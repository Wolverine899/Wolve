package com.wolve.app.data

import com.wolve.app.service.AutoWorkout
import com.wolve.app.service.ZoneNotifier
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide live signal hub. BLE client and demo generator write here; the UI reads. */
object Live {
    const val ECG_FS = 130.0
    private const val VIEW_SAMPLES = 1040 // 8 s of ECG for display

    val status = MutableStateFlow("Not connected")
    val connected = MutableStateFlow(false)
    val ecgAvailable = MutableStateFlow(false)
    val hr = MutableStateFlow(0)
    val hrSeries = MutableStateFlow<List<HrPoint>>(emptyList())
    val devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val ecgView = MutableStateFlow(FloatArray(0))
    val battery = MutableStateFlow(-1)
    val lastHrAt = MutableStateFlow(0L)
    val deviceName = MutableStateFlow("")
    val diag = MutableStateFlow<List<String>>(emptyList())

    /** Connection log shown in Diagnostics and shared for bug reports. */
    fun log(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        diag.value = (diag.value + "$t  $msg").takeLast(250)
    }

    private val lock = Any()
    private val rr = ArrayList<Double>()
    private val view = ArrayList<Float>()
    private var measuring = false
    private val mRr = ArrayList<Double>()
    private val mEcg = ArrayList<Double>()

    fun pushHr(bpm: Int) {
        if (bpm <= 0 || bpm > 240) return
        val now = System.currentTimeMillis()
        hr.value = bpm
        lastHrAt.value = now
        hrSeries.value = (hrSeries.value + HrPoint(now, bpm)).takeLast(300)
        StrainTracker.onHr(now, bpm)
        SleepRecorder.onHr(now, bpm)
        WorkoutRecorder.onHr(now, bpm)
        StressTracker.onHr(now, bpm)
        ZoneNotifier.onHr(now, bpm)
        AutoWorkout.onHr(now, bpm)
    }

    fun pushRr(ms: Double) {
        synchronized(lock) {
            rr.add(ms)
            if (rr.size > 600) rr.removeAt(0)
            if (measuring) mRr.add(ms)
        }
        val now = System.currentTimeMillis()
        SleepRecorder.onRr(now, ms)
        StressTracker.onRr(now, ms)
    }

    fun pushEcg(samples: IntArray) {
        synchronized(lock) {
            for (s in samples) {
                view.add(s.toFloat())
                if (measuring) mEcg.add(s.toDouble())
            }
            val over = view.size - VIEW_SAMPLES
            if (over > 0) view.subList(0, over).clear()
            ecgView.value = view.toFloatArray()
        }
        ecgAvailable.value = true
    }

    fun recentRr(n: Int): List<Double> = synchronized(lock) { ArrayList(rr.takeLast(n)) }

    fun startMeasure() {
        synchronized(lock) {
            mRr.clear()
            mEcg.clear()
            measuring = true
        }
    }

    fun cancelMeasure() {
        synchronized(lock) { measuring = false }
    }

    fun finishMeasure(): Pair<List<Double>, DoubleArray> = synchronized(lock) {
        measuring = false
        Pair(ArrayList(mRr), mEcg.toDoubleArray())
    }

    fun resetStream() {
        synchronized(lock) {
            rr.clear()
            view.clear()
        }
        hr.value = 0
        hrSeries.value = emptyList()
        ecgView.value = FloatArray(0)
        ecgAvailable.value = false
    }
}
