package com.wolve.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.wolve.app.data.FoundDevice
import com.wolve.app.data.Graph
import com.wolve.app.data.Live
import java.util.UUID

/**
 * BLE client for straps that expose the standard Heart Rate Service (0x180D) with R-R intervals.
 * Polar H10-class devices also stream raw ECG (PMD service). WHOOP straps expose the standard service only while
 * "Heart Rate Broadcast" is switched on in the WHOOP app.
 */
@SuppressLint("MissingPermission")
class BleClient(private val ctx: Context) {
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var wantConnected = false
    private var lastAddress = ""
    private var connectedAt = 0L
    private var packets = 0
    private val retries = HashMap<UUID, Int>()

    private val ops = ArrayDeque<() -> Boolean>()
    private var busy = false

    companion object {
        val HR_SVC: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEAS: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATT_SVC: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATT_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val PMD_SVC: UUID = UUID.fromString("fb005c80-02e7-f387-1cad-8acd2d8df0c8")
        val PMD_CTRL: UUID = UUID.fromString("fb005c81-02e7-f387-1cad-8acd2d8df0c8")
        val PMD_DATA: UUID = UUID.fromString("fb005c82-02e7-f387-1cad-8acd2d8df0c8")

        /** Start ECG: 130 Hz, 14-bit. */
        val START_ECG = byteArrayOf(0x02, 0x00, 0x00, 0x01, 0x82.toByte(), 0x00, 0x01, 0x01, 0x0E, 0x00)

        const val WHOOP_HINT = "No heart data yet. On a WHOOP strap, open the WHOOP app > Device settings > turn ON Heart Rate Broadcast, " +
            "then close the WHOOP app completely (it holds the strap's only connection) and tap Connect again."
    }

    private fun short(u: UUID): String {
        val s = u.toString()
        return if (s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8) else s.substring(0, 8)
    }

    // ---------------------------------------------------------------- scanning

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device, result.rssi, result.device.name ?: result.scanRecord?.deviceName)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Live.status.value = "Scan failed (code $errorCode)"
            Live.log("Scan failed: $errorCode")
        }
    }

    private fun addDevice(d: BluetoothDevice, rssi: Int, name: String?) {
        val addr = d.address
        val list = Live.devices.value
        val shown = name ?: "Unnamed device"
        if (list.none { it.address == addr }) Live.devices.value = list + FoundDevice(shown, addr, rssi)
    }

    /** [all] = true lists every nearby Bluetooth LE device (useful to find a WHOOP strap by its name). */
    fun startScan(all: Boolean = false) {
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            Live.status.value = "Turn Bluetooth on to scan"
            return
        }
        if (scanning) return
        Live.devices.value = emptyList()
        // devices already paired with this phone (a WHOOP strap often shows up here)
        try {
            adapter.bondedDevices?.forEach { d -> if (d.type != BluetoothDevice.DEVICE_TYPE_CLASSIC) addDevice(d, 0, d.name) }
        } catch (e: Exception) {
        }
        val filters = if (all) emptyList() else listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SVC)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCb)
        scanning = true
        Live.status.value = if (all) "Scanning all Bluetooth devices..." else "Scanning for heart-rate devices..."
        Live.log(if (all) "Scan started (all devices)" else "Scan started (heart-rate service 0x180D)")
        main.postDelayed({ stopScan() }, 12_000)
    }

    fun stopScan() {
        if (!scanning) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCb)
        } catch (e: Exception) {
        }
        scanning = false
        if (!Live.connected.value) Live.status.value = "Not connected"
    }

    // ---------------------------------------------------------------- connect

    fun connect(address: String) {
        stopScan()
        val dev = (try { adapter?.getRemoteDevice(address) } catch (e: IllegalArgumentException) { null }) ?: return
        disconnect()
        Live.resetStream()
        wantConnected = true
        lastAddress = address
        retries.clear()
        packets = 0
        Live.battery.value = -1
        Live.deviceName.value = dev.name ?: address
        Live.status.value = "Connecting..."
        Live.log("Connecting to ${dev.name ?: "?"} $address")
        gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        Graph.store.lastDevice = address
    }

    /** User-initiated: stops automatic reconnection. */
    fun disconnect() {
        wantConnected = false
        main.removeCallbacks(watchdog)
        main.removeCallbacks(batteryPoll)
        synchronized(this) {
            ops.clear()
            busy = false
        }
        gatt?.disconnect()
    }

    private fun scheduleReconnect() {
        if (!wantConnected || lastAddress.isEmpty()) return
        Live.status.value = "Connection lost, reconnecting..."
        Live.log("Reconnecting in 4 s")
        main.postDelayed({
            if (wantConnected && gatt == null) {
                val dev = try { adapter?.getRemoteDevice(lastAddress) } catch (e: IllegalArgumentException) { null }
                if (dev != null) gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
            }
        }, 4000)
    }

    /** Fires 15 s after services are ready: tells the user why no heart data arrived. */
    private val watchdog = Runnable {
        if (wantConnected && Live.connected.value && Live.lastHrAt.value < connectedAt) {
            Live.status.value = "Connected, but no heart data"
            Live.log("Watchdog: no heart-rate packet after 15 s (packets=$packets)")
            Live.log(WHOOP_HINT)
        }
    }

    private val batteryPoll = object : Runnable {
        override fun run() {
            val g = gatt
            val c = g?.getService(BATT_SVC)?.getCharacteristic(BATT_LEVEL)
            if (g != null && c != null && Live.connected.value) {
                enqueue { g.readCharacteristic(c) }
                runNext()
            }
            main.postDelayed(this, 300_000L)
        }
    }

    // ---------------------------------------------------------------- GATT operation queue

    @Synchronized
    private fun enqueue(op: () -> Boolean) {
        ops.addLast(op)
    }

    @Synchronized
    private fun runNext() {
        if (busy) return
        val op = ops.removeFirstOrNull() ?: return
        busy = true
        val ok = try { op() } catch (e: Exception) { false }
        if (!ok) {
            Live.log("GATT operation refused by the phone")
            busy = false
            runNext()
        }
    }

    @Synchronized
    private fun opDone() {
        busy = false
        runNext()
    }

    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic, indicate: Boolean): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(CCCD) ?: return false
        @Suppress("DEPRECATION")
        d.value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        return g.writeDescriptor(d)
    }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray): Boolean {
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = v
        return g.writeCharacteristic(c)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Live.log("Connection state=$newState status=$status")
            if (gatt != null && gatt !== g) {
                // callback from a superseded connection
                if (newState == BluetoothProfile.STATE_DISCONNECTED) g.close()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Live.status.value = "Connected, discovering services..."
                main.postDelayed({ if (gatt === g) g.discoverServices() }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Live.connected.value = false
                Live.ecgAvailable.value = false
                Live.status.value = "Disconnected"
                main.removeCallbacks(watchdog)
                synchronized(this@BleClient) {
                    ops.clear()
                    busy = false
                }
                g.close()
                if (gatt === g) gatt = null
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val names = g.services.map { short(it.uuid) }
            Live.log("Services: ${names.joinToString(", ")}")
            val hr = g.getService(HR_SVC)?.getCharacteristic(HR_MEAS)
            if (hr == null) {
                Live.status.value = "No standard heart-rate service on this device"
                Live.log("Heart Rate Service 0x180D not found. For WHOOP: enable Heart Rate Broadcast in the WHOOP app, close it, then reconnect.")
                g.disconnect()
                return
            }
            Live.log("Heart rate characteristic found, properties=${hr.properties}")
            enqueue { enableNotify(g, hr, false) }
            val batt = g.getService(BATT_SVC)?.getCharacteristic(BATT_LEVEL)
            if (batt != null) enqueue { g.readCharacteristic(batt) }
            val pmd = g.getService(PMD_SVC)
            val ctrl = pmd?.getCharacteristic(PMD_CTRL)
            val data = pmd?.getCharacteristic(PMD_DATA)
            if (ctrl != null && data != null) {
                enqueue { enableNotify(g, ctrl, true) }
                enqueue { enableNotify(g, data, false) }
                enqueue { write(g, ctrl, START_ECG) }
            }
            connectedAt = System.currentTimeMillis()
            Live.connected.value = true
            Live.status.value = "Connected, waiting for heart rate..."
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 15_000L)
            main.removeCallbacks(batteryPoll)
            main.postDelayed(batteryPoll, 300_000L)
            runNext()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            val u = d.characteristic.uuid
            Live.log("Notifications ${short(u)}: status $status")
            if (status != BluetoothGatt.GATT_SUCCESS && gatt === g) {
                val n = retries[u] ?: 0
                if (n < 3) {
                    retries[u] = n + 1
                    // status 5 / 15 = the strap wants an encrypted (bonded) link first
                    if (status == 5 || status == 15) {
                        try { g.device.createBond() } catch (e: Exception) { }
                        Live.log("Strap asked for pairing. Accept the Bluetooth pairing request if one appears.")
                    }
                    @Suppress("DEPRECATION")
                    val indicate = d.value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true
                    main.postDelayed({
                        if (gatt === g) {
                            enqueue { enableNotify(g, d.characteristic, indicate) }
                            runNext()
                        }
                    }, 2500)
                } else {
                    Live.status.value = "Could not enable heart-rate notifications (status $status)"
                }
            }
            opDone()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            Live.log("Write ${short(c.uuid)}: status $status")
            opDone()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                if (status == BluetoothGatt.GATT_SUCCESS) c.value?.let { handleRead(c.uuid, it) }
                opDone()
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleRead(c.uuid, value)
            opDone()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) c.value?.let { handleChanged(c.uuid, it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleChanged(c.uuid, value)
        }
    }

    private fun handleRead(uuid: UUID, v: ByteArray) {
        if (uuid == BATT_LEVEL && v.isNotEmpty()) {
            Live.battery.value = v[0].toInt() and 0xFF
            Live.log("Battery ${Live.battery.value}%")
        }
    }

    private fun handleChanged(uuid: UUID, v: ByteArray) {
        when (uuid) {
            HR_MEAS -> {
                packets++
                if (packets == 1) {
                    Live.log("First heart-rate packet: " + v.joinToString(" ") { String.format("%02X", it) })
                    Live.status.value = "Connected: streaming heart rate"
                }
                parseHr(v)
            }
            PMD_DATA -> parseEcg(v)
        }
    }

    /** Heart Rate Measurement characteristic (Bluetooth SIG GATT specification). */
    private fun parseHr(v: ByteArray) {
        if (v.isEmpty()) return
        val flags = v[0].toInt() and 0xFF
        var i = 1
        val bpm: Int
        if ((flags and 0x01) == 0) {
            if (v.size < 2) return
            bpm = v[i].toInt() and 0xFF
            i += 1
        } else {
            if (v.size < 3) return
            bpm = (v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8)
            i += 2
        }
        if ((flags and 0x08) != 0) i += 2 // energy expended
        if ((flags and 0x10) != 0) {
            while (i + 1 < v.size) {
                val raw = (v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8)
                Live.pushRr(raw * 1000.0 / 1024.0)
                i += 2
            }
        }
        Live.pushHr(bpm)
    }

    /** PMD ECG frame: type(1) timestamp(8) frameType(1) then 3-byte signed samples in microvolts. */
    private fun parseEcg(v: ByteArray) {
        if (v.size <= 10 || v[0].toInt() != 0 || v[9].toInt() != 0) return
        val n = (v.size - 10) / 3
        val out = IntArray(n)
        for (k in 0 until n) {
            val o = 10 + k * 3
            var s = (v[o].toInt() and 0xFF) or ((v[o + 1].toInt() and 0xFF) shl 8) or ((v[o + 2].toInt() and 0xFF) shl 16)
            if ((s and 0x800000) != 0) s -= (1 shl 24)
            out[k] = s
        }
        Live.pushEcg(out)
    }
}
