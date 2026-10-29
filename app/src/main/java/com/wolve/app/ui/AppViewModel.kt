package com.wolve.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wolve.app.analytics.BloodPressure
import com.wolve.app.analytics.Nutrition
import com.wolve.app.analytics.Ecg
import com.wolve.app.analytics.Hrv
import com.wolve.app.analytics.Recovery
import com.wolve.app.analytics.SampleNight
import com.wolve.app.analytics.SleepEngine
import com.wolve.app.analytics.SleepNight
import com.wolve.app.analytics.Workout
import com.wolve.app.ble.Demo
import com.wolve.app.data.AlarmCfg
import com.wolve.app.data.BpCalPoint
import com.wolve.app.data.BpReading
import com.wolve.app.data.Graph
import com.wolve.app.data.Live
import com.wolve.app.data.SleepRecorder
import com.wolve.app.data.StrainTracker
import com.wolve.app.data.StressTracker
import com.wolve.app.data.WorkoutRecorder
import com.wolve.app.data.BackupZip
import com.wolve.app.service.AiClient
import com.wolve.app.service.AlarmScheduler
import java.time.LocalDate
import com.wolve.app.service.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Metrics(
    val rmssd: Double = 0.0,
    val sdnn: Double = 0.0,
    val pnn50: Double = 0.0,
    val meanHr: Double = 0.0,
    val beats: Int = 0,
)

enum class Phase { IDLE, MEASURING, DONE, ERROR }

data class BpUi(
    val phase: Phase = Phase.IDLE,
    val progress: Float = 0f,
    val latest: BpReading? = null,
    val source: String = "",
    val message: String = "",
    val calMessage: String = "",
    val history: List<BpReading> = emptyList(),
    val calCount: Int = 0,
)

data class SleepUi(
    val active: Boolean = false,
    val startTs: Long = 0L,
    val elapsedMin: Int = 0,
    val last: SleepNight? = null,
    val nights: List<SleepNight> = emptyList(),
    val preview: SleepNight? = null,
    val message: String = "",
    val baselineNights: Int = 0,
)

data class Profile(val age: Int = 30, val male: Boolean = true, val weightKg: Double = 75.0, val heightCm: Double = 170.0)

data class ChatMsg(val fromUser: Boolean, val text: String)

data class Goals(val minutes: Int = 150, val sessions: Int = 4)

const val MEASURE_SEC = 60

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Graph.store
    val ble = Graph.ble
    val metrics = MutableStateFlow(Metrics())
    val demoRunning = MutableStateFlow(false)
    val profile = MutableStateFlow(Profile(store.age, store.male, store.weightKg))
    val workouts = MutableStateFlow(store.loadWorkouts().reversed())
    val journal = MutableStateFlow(store.loadJournal())
    val alarm = MutableStateFlow(store.loadAlarm())
    val hydration = MutableStateFlow(store.loadHydration()[LocalDate.now().toString()] ?: 0)
    val naps = MutableStateFlow(store.loadNaps().reversed())
    val goals = MutableStateFlow(Goals(store.goalMinutes, store.goalSessions))
    val chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val aiBusy = MutableStateFlow(false)
    val aiModels = MutableStateFlow<List<String>>(emptyList())
    val bp = MutableStateFlow(
        BpUi(history = store.loadReadings().takeLast(30).reversed(), calCount = store.loadCal().size)
    )
    val sleep: MutableStateFlow<SleepUi>

    private var demoJob: Job? = null
    private var bpJob: Job? = null

    init {
        val nights = store.loadNights()
        sleep = MutableStateFlow(
            SleepUi(
                active = SleepRecorder.active.value,
                startTs = SleepRecorder.startTs.value,
                last = nights.lastOrNull(),
                nights = nights,
                baselineNights = store.loadBaselines().n,
            )
        )
        viewModelScope.launch {
            Graph.workoutsVersion.collect { workouts.value = store.loadWorkouts().reversed() }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                tick()
            }
        }
    }

    private fun tick() {
        val c = Hrv.clean(Live.recentRr(90))
        if (c.size >= 20) {
            metrics.value = Metrics(Hrv.rmssd(c), Hrv.sdnn(c), Hrv.pnn50(c), Hrv.meanHr(c), c.size)
        }
        val s = sleep.value
        val active = SleepRecorder.active.value
        val start = SleepRecorder.startTs.value
        val elapsed = if (active) ((System.currentTimeMillis() - start) / 60000L).toInt() else 0
        if (s.active != active || s.elapsedMin != elapsed || s.startTs != start) {
            sleep.value = s.copy(active = active, startTs = start, elapsedMin = elapsed)
        }
    }

    // ---- connection ----
    fun scan(all: Boolean = false) = ble.startScan(all)

    fun connect(address: String) {
        stopDemo()
        ble.connect(address)
        TrackingService.start(getApplication())
    }

    fun disconnect() {
        ble.disconnect()
        stopDemo()
        if (!SleepRecorder.active.value) TrackingService.stop(getApplication())
    }

    fun startDemo() {
        ble.disconnect()
        Live.resetStream()
        demoJob?.cancel()
        demoJob = Demo.start(viewModelScope)
        demoRunning.value = true
    }

    fun stopDemo() {
        if (demoJob == null) return
        demoJob?.cancel()
        demoJob = null
        demoRunning.value = false
        Live.connected.value = false
        Live.ecgAvailable.value = false
        Live.status.value = "Not connected"
    }

    // ---- sleep ----
    private fun sleepMsg(m: String) {
        sleep.value = sleep.value.copy(message = m)
    }

    fun startSleep() {
        if (!Live.connected.value) {
            sleepMsg("Connect your strap first (More tab), or start the demo.")
            return
        }
        if (Live.recentRr(1).isEmpty()) {
            sleepMsg("No heartbeat intervals received yet. Wait a few seconds, or use a strap that streams R-R intervals.")
            return
        }
        val now = System.currentTimeMillis()
        SleepRecorder.start(now)
        TrackingService.start(getApplication())
        sleep.value = sleep.value.copy(active = true, startTs = now, elapsedMin = 0, message = "", preview = null)
    }

    fun stopSleep() {
        if (!SleepRecorder.active.value) return
        val now = System.currentTimeMillis()
        val session = SleepRecorder.stop(now)
        viewModelScope.launch {
            val night = withContext(Dispatchers.Default) { SleepEngine.analyze(session.second, session.first, now, store.gb("hrv_deep", false)) }
            if (night == null) {
                sleep.value = sleep.value.copy(
                    active = false,
                    message = "Not enough clean data to score this night. Wear the strap snugly and track at least 30 minutes of sleep.",
                )
                return@launch
            }
            val b = store.loadBaselines()
            val scored = night.copy(
                recovery = Recovery.score(b, night.hrv, night.rhr, night.resp, night.performance),
                calibrating = Recovery.isCalibrating(b),
            )
            var msg = ""
            if (scored.totalMin >= 180) {
                store.saveBaselines(Recovery.update(b, night.hrv, night.rhr, night.resp))
                store.restHr = night.rhr
                StrainTracker.configure(store.age, store.male, store.restHr)
            } else {
                msg = "Short sleep (under 3 h): saved, but your baselines were not updated."
            }
            val all = (store.loadNights() + scored).takeLast(60)
            store.saveNights(all)
            sleep.value = sleep.value.copy(
                active = false, last = scored, nights = all, preview = null,
                message = msg, baselineNights = store.loadBaselines().n,
            )
            if (!Live.connected.value) TrackingService.stop(getApplication())
        }
    }

    fun previewSample() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.Default) { SampleNight.build() }
            sleep.value = sleep.value.copy(preview = n, message = "")
        }
    }

    fun closePreview() {
        sleep.value = sleep.value.copy(preview = null)
    }

    // ---- profile ----
    fun saveProfile(age: Int, male: Boolean, weightKg: Double, heightCm: Double): Boolean {
        if (age !in 14..90 || weightKg < 30.0 || weightKg > 250.0 || heightCm < 120.0 || heightCm > 230.0) return false
        store.age = age
        store.male = male
        store.weightKg = weightKg
        store.heightCm = heightCm
        profile.value = Profile(age, male, weightKg, heightCm)
        StrainTracker.configure(age, male, store.restHr)
        return true
    }

    fun setSex(v: Int) {
        store.sex = v
        profile.value = profile.value.copy(male = v != 1)
        StrainTracker.configure(store.age, store.male, store.restHr)
    }

    fun recalibrate() {
        store.saveBaselines(com.wolve.app.analytics.Baselines())
        sleep.value = sleep.value.copy(baselineNights = 0)
    }

    // ---- activities ----
    fun startWorkout(sport: String) {
        if (!Live.connected.value) return
        WorkoutRecorder.start(sport, System.currentTimeMillis(), store.age, store.weightKg, store.male, store.restHr)
        TrackingService.start(getApplication())
    }

    fun stopWorkout(): Workout? {
        val w = WorkoutRecorder.stop(System.currentTimeMillis())
        if (w != null) {
            val all = (store.loadWorkouts() + w).takeLast(200)
            store.saveWorkouts(all)
            workouts.value = all.reversed()
        }
        return w
    }

    fun deleteWorkout(startTs: Long) {
        val all = store.loadWorkouts().filter { it.startTs != startTs }
        store.saveWorkouts(all)
        workouts.value = all.reversed()
    }

    // ---- journal ----
    fun toggleBehavior(id: String) {
        val day = LocalDate.now().toString()
        val m = HashMap(journal.value)
        val set = HashSet(m[day] ?: emptySet())
        if (!set.add(id)) set.remove(id)
        m[day] = set
        store.saveJournal(m)
        journal.value = m
    }

    // ---- alarm ----
    fun setAlarm(c: AlarmCfg) {
        store.saveAlarm(c)
        alarm.value = c
        AlarmScheduler.schedule(getApplication(), c)
    }

    // ---- water, naps, goals, alerts ----
    fun addWater(ml: Int) {
        val day = LocalDate.now().toString()
        val m = HashMap(store.loadHydration())
        val v = ((m[day] ?: 0) + ml).coerceIn(0, 10000)
        m[day] = v
        store.saveHydration(m)
        hydration.value = v
    }

    fun logNap(minutes: Int) {
        val list = store.loadNaps() + Pair(System.currentTimeMillis() - minutes * 60_000L, minutes)
        store.saveNaps(list)
        naps.value = store.loadNaps().reversed()
    }

    fun setGoals(g: Goals) {
        store.goalMinutes = g.minutes
        store.goalSessions = g.sessions
        goals.value = g
    }

    fun markBehavior(id: String) {
        val day = LocalDate.now().toString()
        val m = HashMap(journal.value)
        val set = HashSet(m[day] ?: emptySet())
        set.add(id)
        m[day] = set
        store.saveJournal(m)
        journal.value = m
    }

    fun setZoneAlerts(on: Boolean) {
        store.zoneAlerts = on
    }

    fun setAutoWorkout(on: Boolean) {
        store.autoWorkout = on
    }

    // ---- AI coach ----
    fun aiProvider(): String = store.aiProvider

    fun aiModel(): String = store.aiModel

    fun aiKey(provider: String): String = store.aiKey(provider)

    fun saveAi(provider: String, model: String, key: String) {
        store.aiProvider = provider
        store.aiModel = model.trim()
        store.setAiKey(provider, key)
    }

    fun loadModels(provider: String, key: String) {
        viewModelScope.launch { aiModels.value = AiClient.listModels(provider, key.trim()) }
    }

    fun clearChat() {
        chat.value = emptyList()
    }

    /** A compact summary of your own numbers, sent with each AI request so answers are specific to you. */
    fun contextText(): String {
        val p = profile.value
        val n = sleep.value.last
        val strain = StrainTracker.strain.value
        val plan = Nutrition.plan(p.age, p.weightKg, p.heightCm, p.male, strain)
        val b = StringBuilder()
        b.append("Athlete: age ${p.age}, ${if (p.male) "male" else "female"}, ${p.weightKg.toInt()} kg, ${p.heightCm.toInt()} cm.\n")
        b.append("Today's strain so far: ${"%.1f".format(java.util.Locale.US, strain)} of 21. Water today: ${hydration.value} ml.\n")
        b.append("Energy needs estimate: BMR ${plan.bmr} kcal, about ${plan.tdee} kcal per day, protein ${plan.proteinG} g, carbs ${plan.carbG} g, fat ${plan.fatG} g.\n")
        if (n != null) {
            b.append("Last night: ${n.totalMin / 60} h ${n.totalMin % 60} min asleep, efficiency ${(n.efficiency * 100).toInt()}%, deep ${n.deepMin} min, REM ${n.remMin} min, ")
            b.append("resting HR ${n.rhr.toInt()} bpm, HRV ${n.hrv.toInt()} ms, recovery ${n.recovery}%.\n")
        } else {
            b.append("No tracked sleep yet.\n")
        }
        if (UiPrefs.shareWithCoach) {
            val eff = com.wolve.app.analytics.JournalInsights.effects(journal.value, sleep.value.nights).take(3)
            if (eff.isNotEmpty()) b.append("Habit effects on next-morning recovery: " + eff.joinToString("; ") { "${it.label} ${"%+d".format(java.util.Locale.US, it.delta.toInt())}" } + ".\n")
        }
        val w = workouts.value.take(3)
        if (w.isNotEmpty()) b.append("Recent workouts: " + w.joinToString("; ") { "${it.sport} ${(it.endTs - it.startTs) / 60000L} min, strain ${"%.1f".format(java.util.Locale.US, it.strain)}" } + ".\n")
        return b.toString()
    }

    fun askAi(prompt: String, shown: String = prompt, attach: AiClient.Attach? = null) {
        if (aiBusy.value) return
        val provider = store.aiProvider
        val system = "You are Wolve, a careful fitness and recovery coach inside a phone app. Use the athlete data provided. " +
            "Be concrete and concise, use short sections, and reply in the same language as the user's message. " +
            "You are not a doctor: never diagnose, and tell the user to see a clinician for anything concerning."
        val history = chat.value.takeLast(6).joinToString("\n") { (if (it.fromUser) "User: " else "Coach: ") + it.text }
        val full = "ATHLETE DATA\n" + contextText() + (if (history.isNotEmpty()) "\nCONVERSATION SO FAR\n$history\n" else "") + "\nREQUEST\n" + prompt
        chat.value = chat.value + ChatMsg(true, shown)
        aiBusy.value = true
        viewModelScope.launch {
            val r = AiClient.ask(provider, store.aiModel, store.aiKey(provider), system, full, attach)
            chat.value = chat.value + ChatMsg(false, r.text)
            aiBusy.value = false
        }
    }

    // ---- backup (ZIP with Excel files) ----
    fun exportZip(): ByteArray = BackupZip.build(store)

    fun importBackup(bytes: ByteArray): Boolean {
        try {
            java.io.File(Graph.appContext.filesDir, "pre-import-backup.json").writeText(store.exportAll())
        } catch (e: Exception) {
        }
        val ok = BackupZip.restore(bytes, store)
        if (ok) reloadFromStore()
        return ok
    }

    private fun reloadFromStore() {
        val nights = store.loadNights()
        profile.value = Profile(store.age, store.male, store.weightKg, store.heightCm)
        hydration.value = store.loadHydration()[LocalDate.now().toString()] ?: 0
        naps.value = store.loadNaps().reversed()
        workouts.value = store.loadWorkouts().reversed()
        journal.value = store.loadJournal()
        bp.value = BpUi(history = store.loadReadings().takeLast(30).reversed(), calCount = store.loadCal().size)
        sleep.value = sleep.value.copy(last = nights.lastOrNull(), nights = nights, baselineNights = store.loadBaselines().n)
        StrainTracker.configure(store.age, store.male, store.restHr)
    }

    // ---- blood-pressure estimate ----
    fun startBp() {
        if (!Live.connected.value) {
            bp.value = bp.value.copy(
                phase = Phase.ERROR,
                message = "Connect a heart-rate strap or start the demo first.",
            )
            return
        }
        bpJob?.cancel()
        Live.startMeasure()
        bp.value = bp.value.copy(phase = Phase.MEASURING, progress = 0f, message = "")
        bpJob = viewModelScope.launch {
            for (i in 1..MEASURE_SEC) {
                delay(1000)
                if (!Live.connected.value) {
                    Live.cancelMeasure()
                    bp.value = bp.value.copy(phase = Phase.ERROR, message = "Connection lost during the measurement.")
                    return@launch
                }
                bp.value = bp.value.copy(progress = i / MEASURE_SEC.toFloat())
            }
            finishBp()
        }
    }

    fun cancelBp() {
        bpJob?.cancel()
        Live.cancelMeasure()
        bp.value = bp.value.copy(phase = Phase.IDLE, progress = 0f)
    }

    private suspend fun finishBp() {
        val (rrSvc, ecg) = Live.finishMeasure()
        val (rr, source) = withContext(Dispatchers.Default) {
            if (ecg.size >= (Live.ECG_FS * 40).toInt()) {
                val peaks = Ecg.detectRPeaks(ecg, Live.ECG_FS)
                Pair(peaks.toList().zipWithNext { a, b -> (b - a) * 1000.0 / Live.ECG_FS }, "ECG")
            } else {
                Pair(rrSvc, "heart-rate intervals")
            }
        }
        val clean = Hrv.clean(rr)
        val quality = if (rr.isEmpty()) 0.0 else clean.size.toDouble() / rr.size
        if (clean.size < 40 || quality < 0.75) {
            bp.value = bp.value.copy(
                phase = Phase.ERROR, progress = 0f,
                message = "Signal too short or noisy (${clean.size} clean beats). Wear the strap snugly, stay still, and try again.",
            )
            return
        }
        val hr = Hrv.meanHr(clean)
        val rm = Hrv.rmssd(clean)
        val cal = store.loadCal()
        val e = BloodPressure.estimate(hr, rm, cal)
        val reading = BpReading(System.currentTimeMillis(), e.sys, e.dia, e.margin, hr, rm, quality, e.calibrated, e.category.label)
        val hist = (store.loadReadings() + reading).takeLast(100)
        store.saveReadings(hist)
        bp.value = BpUi(
            phase = Phase.DONE, progress = 1f, latest = reading, source = source,
            history = hist.takeLast(30).reversed(), calCount = cal.size,
        )
    }

    fun calibrate(sys: Int, dia: Int) {
        val l = bp.value.latest
        if (l == null) {
            bp.value = bp.value.copy(calMessage = "Take a measurement first.")
            return
        }
        if (sys !in 70..250 || dia !in 40..150 || sys <= dia) {
            bp.value = bp.value.copy(calMessage = "Check the cuff numbers.")
            return
        }
        val point = BpCalPoint(System.currentTimeMillis(), sys.toDouble(), dia.toDouble(), l.hr, l.rmssd)
        val list = (store.loadCal() + point).takeLast(30)
        store.saveCal(list)
        bp.value = bp.value.copy(
            calCount = list.size,
            calMessage = "Saved. ${list.size} cuff reading(s) now tune your estimates.",
        )
    }

    fun clearData() {
        SleepRecorder.discard()
        store.clearAll()
        StrainTracker.reset()
        StressTracker.clear()
        StrainTracker.configure(30, true, 60.0)
        AlarmScheduler.cancel(getApplication())
        alarm.value = AlarmCfg()
        workouts.value = emptyList()
        journal.value = emptyMap()
        profile.value = Profile()
        hydration.value = 0
        naps.value = emptyList()
        goals.value = Goals()
        chat.value = emptyList()
        bp.value = BpUi()
        sleep.value = SleepUi()
        metrics.value = Metrics()
    }

    override fun onCleared() {
        // Keep the connection alive if a foreground service is tracking; otherwise release it.
        if (!SleepRecorder.active.value && !UiPrefs.keepBackground) ble.disconnect()
        super.onCleared()
    }
}
