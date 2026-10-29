package com.wolve.app.data

import android.content.Context
import com.wolve.app.analytics.Baselines
import com.wolve.app.analytics.SleepBlock
import com.wolve.app.analytics.SleepNight
import com.wolve.app.analytics.Workout
import org.json.JSONArray
import org.json.JSONObject

data class FoundDevice(val name: String, val address: String, val rssi: Int)

data class HrPoint(val tsMs: Long, val bpm: Int)

data class StressPoint(val ts: Long, val score: Double)

data class AlarmCfg(val enabled: Boolean = false, val hour: Int = 7, val minute: Int = 0, val smart: Boolean = true)

/** A cuff reading paired with the heart features measured at the same time. */
data class BpCalPoint(
    val tsMs: Long,
    val sys: Double,
    val dia: Double,
    val hr: Double,
    val rmssd: Double,
)

data class BpReading(
    val tsMs: Long,
    val sys: Double,
    val dia: Double,
    val margin: Double,
    val hr: Double,
    val rmssd: Double,
    val quality: Double,
    val calibrated: Boolean,
    val category: String,
)

/** On-device store. The app has no INTERNET permission, so nothing leaves the phone. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("wolve", Context.MODE_PRIVATE)

    // ---- profile ----
    var age: Int
        get() = p.getInt("age", 30)
        set(v) { p.edit().putInt("age", v).apply() }

    var male: Boolean
        get() = p.getBoolean("male", true)
        set(v) { p.edit().putBoolean("male", v).apply() }

    var restHr: Double
        get() = p.getFloat("rest_hr", 60f).toDouble()
        set(v) { p.edit().putFloat("rest_hr", v.toFloat()).apply() }

    var weightKg: Double
        get() = p.getFloat("weight", 75f).toDouble()
        set(v) { p.edit().putFloat("weight", v.toFloat()).apply() }

    var heightCm: Double
        get() = p.getFloat("height", 170f).toDouble()
        set(v) { p.edit().putFloat("height", v.toFloat()).apply() }

    var zoneAlerts: Boolean
        get() = p.getBoolean("zone_alerts", false)
        set(v) { p.edit().putBoolean("zone_alerts", v).apply() }

    var autoWorkout: Boolean
        get() = p.getBoolean("auto_workout", true)
        set(v) { p.edit().putBoolean("auto_workout", v).apply() }

    var goalMinutes: Int
        get() = p.getInt("goal_min", 150)
        set(v) { p.edit().putInt("goal_min", v).apply() }

    var goalSessions: Int
        get() = p.getInt("goal_sessions", 4)
        set(v) { p.edit().putInt("goal_sessions", v).apply() }

    var aiProvider: String
        get() = p.getString("ai_provider", "gemini") ?: "gemini"
        set(v) { p.edit().putString("ai_provider", v).apply() }

    var aiModel: String
        get() = p.getString("ai_model", "") ?: ""
        set(v) { p.edit().putString("ai_model", v).apply() }

    /** API keys stay in this private file and are never included in backups. */
    fun aiKey(provider: String): String = p.getString("ai_key_$provider", "") ?: ""

    fun setAiKey(provider: String, key: String) {
        p.edit().putString("ai_key_$provider", key.trim()).apply()
    }

    fun gi(k: String, d: Int): Int = p.getInt(k, d)
    fun si(k: String, v: Int) { p.edit().putInt(k, v).apply() }
    fun gb(k: String, d: Boolean): Boolean = p.getBoolean(k, d)
    fun sb(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }
    fun gf(k: String, d: Float): Float = p.getFloat(k, d)
    fun sf(k: String, v: Float) { p.edit().putFloat(k, v).apply() }
    fun gs(k: String, d: String): String = p.getString(k, d) ?: d
    fun ss(k: String, v: String) { p.edit().putString(k, v).apply() }

    /** 0 male, 1 female, 2 non-binary. Formulas use [male]; non-binary uses the male coefficients. */
    var sex: Int
        get() = p.getInt("sex", if (p.getBoolean("male", true)) 0 else 1)
        set(v) {
            p.edit().putInt("sex", v).putBoolean("male", v != 1).apply()
        }

    var lastDevice: String
        get() = p.getString("last_device", "") ?: ""
        set(v) { p.edit().putString("last_device", v).apply() }

    // ---- strain (one running day) ----
    fun loadStrain(): Pair<String, Double> =
        Pair(p.getString("strain_day", "") ?: "", p.getFloat("strain_load", 0f).toDouble())

    fun saveStrain(day: String, load: Double) {
        p.edit().putString("strain_day", day).putFloat("strain_load", load.toFloat()).apply()
    }

    // ---- blood pressure ----
    fun loadCal(): List<BpCalPoint> = readArr("cal") {
        BpCalPoint(it.getLong("ts"), it.getDouble("sys"), it.getDouble("dia"), it.getDouble("hr"), it.getDouble("rmssd"))
    }

    fun saveCal(list: List<BpCalPoint>) = writeArr("cal", list) {
        JSONObject().put("ts", it.tsMs).put("sys", it.sys).put("dia", it.dia).put("hr", it.hr).put("rmssd", it.rmssd)
    }

    fun loadReadings(): List<BpReading> = readArr("readings") {
        BpReading(
            it.getLong("ts"), it.getDouble("sys"), it.getDouble("dia"), it.getDouble("margin"),
            it.getDouble("hr"), it.getDouble("rmssd"), it.getDouble("q"),
            it.getBoolean("cal"), it.getString("cat"),
        )
    }

    fun saveReadings(list: List<BpReading>) = writeArr("readings", list) {
        JSONObject().put("ts", it.tsMs).put("sys", it.sys).put("dia", it.dia).put("margin", it.margin)
            .put("hr", it.hr).put("rmssd", it.rmssd).put("q", it.quality)
            .put("cal", it.calibrated).put("cat", it.category)
    }

    // ---- sleep ----
    fun loadNights(): List<SleepNight> = readArr("nights") {
        SleepNight(
            startTs = it.getLong("st"), endTs = it.getLong("en"), onsetTs = it.getLong("on"), wakeTs = it.getLong("wk"),
            totalMin = it.getInt("tot"), awakeMin = it.getInt("awk"), lightMin = it.getInt("lt"),
            deepMin = it.getInt("dp"), remMin = it.getInt("rem"), efficiency = it.getDouble("eff"),
            latencyMin = it.getInt("lat"), awakenings = it.getInt("aw"), rhr = it.getDouble("rhr"),
            hrv = it.getDouble("hrv"), resp = it.getDouble("resp"), coverage = it.getDouble("cov"),
            performance = it.getInt("perf"), recovery = it.getInt("rec"), calibrating = it.getBoolean("cal"),
            stages = it.getString("stg"),
        )
    }

    fun saveNights(list: List<SleepNight>) = writeArr("nights", list) {
        JSONObject().put("st", it.startTs).put("en", it.endTs).put("on", it.onsetTs).put("wk", it.wakeTs)
            .put("tot", it.totalMin).put("awk", it.awakeMin).put("lt", it.lightMin).put("dp", it.deepMin)
            .put("rem", it.remMin).put("eff", it.efficiency).put("lat", it.latencyMin).put("aw", it.awakenings)
            .put("rhr", it.rhr).put("hrv", it.hrv).put("resp", it.resp).put("cov", it.coverage)
            .put("perf", it.performance).put("rec", it.recovery).put("cal", it.calibrating).put("stg", it.stages)
    }

    fun loadBaselines(): Baselines {
        val s = p.getString("baselines", null) ?: return Baselines()
        return try {
            val o = JSONObject(s)
            Baselines(
                o.getInt("n"), o.getDouble("lh"), o.getDouble("lhv"), o.getDouble("rhr"), o.getDouble("rhv"),
                o.getDouble("rp"), o.getDouble("rpv"), o.getInt("rpn"),
            )
        } catch (e: Exception) {
            Baselines()
        }
    }

    fun saveBaselines(b: Baselines) {
        val o = JSONObject().put("n", b.n).put("lh", b.lnHrv).put("lhv", b.lnHrvVar).put("rhr", b.rhr)
            .put("rhv", b.rhrVar).put("rp", b.resp).put("rpv", b.respVar).put("rpn", b.respN)
        p.edit().putString("baselines", o.toString()).apply()
    }

    /** An unfinished tracking session survives process death. */
    fun loadSleepSession(): Pair<Long, List<SleepBlock>> {
        val st = p.getLong("sleep_start", 0L)
        if (st <= 0L) return Pair(0L, emptyList())
        return Pair(st, readArr("sleep_blocks") {
            SleepBlock(it.getLong("ts"), it.getDouble("hr"), it.getDouble("rm"), it.getDouble("sd"), it.getDouble("rp"), it.getBoolean("v"))
        })
    }

    fun saveSleepSession(start: Long, blocks: List<SleepBlock>) {
        val a = JSONArray()
        blocks.forEach {
            a.put(JSONObject().put("ts", it.ts).put("hr", it.hr).put("rm", it.rmssd).put("sd", it.hrStd).put("rp", it.resp).put("v", it.valid))
        }
        p.edit().putLong("sleep_start", start).putString("sleep_blocks", a.toString()).apply()
    }

    fun clearSleepSession() {
        p.edit().remove("sleep_start").remove("sleep_blocks").apply()
    }


    // ---- alarm ----
    fun loadAlarm(): AlarmCfg = AlarmCfg(
        p.getBoolean("alarm_on", false), p.getInt("alarm_h", 7), p.getInt("alarm_m", 0), p.getBoolean("alarm_smart", true),
    )

    fun saveAlarm(c: AlarmCfg) {
        p.edit().putBoolean("alarm_on", c.enabled).putInt("alarm_h", c.hour).putInt("alarm_m", c.minute)
            .putBoolean("alarm_smart", c.smart).apply()
    }

    // ---- workouts ----
    fun loadWorkouts(): List<Workout> = readArr("workouts") {
        val z = it.getJSONArray("z")
        Workout(
            startTs = it.getLong("st"), endTs = it.getLong("en"), sport = it.getString("sp"),
            avgHr = it.getInt("avg"), maxHr = it.getInt("max"), kcal = it.getInt("kc"), strain = it.getDouble("str"),
            zoneSec = (0 until z.length()).map { i -> z.getInt(i) },
        )
    }

    fun saveWorkouts(list: List<Workout>) = writeArr("workouts", list) {
        JSONObject().put("st", it.startTs).put("en", it.endTs).put("sp", it.sport).put("avg", it.avgHr)
            .put("max", it.maxHr).put("kc", it.kcal).put("str", it.strain).put("z", JSONArray(it.zoneSec))
    }

    // ---- journal: date -> behaviour ids ----
    fun loadJournal(): Map<String, Set<String>> {
        val s = p.getString("journal", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = HashMap<String, Set<String>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = (0 until a.length()).map { a.getString(it) }.toSet()
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveJournal(m: Map<String, Set<String>>) {
        val o = JSONObject()
        m.keys.sorted().takeLast(120).forEach { k -> o.put(k, JSONArray(m.getValue(k).toList())) }
        p.edit().putString("journal", o.toString()).apply()
    }

    // ---- day strain history ----
    fun loadDayStrains(): Map<String, Double> {
        val s = p.getString("day_strain", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = HashMap<String, Double>()
            for (k in o.keys()) out[k] = o.getDouble(k)
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveDayStrain(day: String, value: Double) {
        val m = HashMap(loadDayStrains())
        m[day] = value
        val o = JSONObject()
        m.keys.sorted().takeLast(90).forEach { k -> o.put(k, m.getValue(k)) }
        p.edit().putString("day_strain", o.toString()).apply()
    }

    // ---- stress ----
    fun loadStress(): List<StressPoint> = readArr("stress") { StressPoint(it.getLong("ts"), it.getDouble("s")) }

    fun saveStress(list: List<StressPoint>) = writeArr("stress", list) {
        JSONObject().put("ts", it.ts).put("s", it.score)
    }

    /** Daytime HRV baseline as (mean of ln RMSSD, variance, windows seen). */
    fun loadStressBase(): Triple<Double, Double, Int> =
        Triple(p.getFloat("sb_m", 0f).toDouble(), p.getFloat("sb_v", 0f).toDouble(), p.getInt("sb_n", 0))

    fun saveStressBase(m: Double, v: Double, n: Int) {
        p.edit().putFloat("sb_m", m.toFloat()).putFloat("sb_v", v.toFloat()).putInt("sb_n", n).apply()
    }


    // ---- hydration (date -> ml) and naps ----
    fun loadHydration(): Map<String, Int> {
        val s = p.getString("hydration", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = HashMap<String, Int>()
            for (k in o.keys()) out[k] = o.getInt(k)
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveHydration(m: Map<String, Int>) {
        val o = JSONObject()
        m.keys.sorted().takeLast(90).forEach { k -> o.put(k, m.getValue(k)) }
        p.edit().putString("hydration", o.toString()).apply()
    }

    /** A nap is (start time, minutes). */
    fun loadNaps(): List<Pair<Long, Int>> = readArr("naps") { Pair(it.getLong("ts"), it.getInt("min")) }

    fun saveNaps(list: List<Pair<Long, Int>>) = writeArr("naps", list.takeLast(120)) {
        JSONObject().put("ts", it.first).put("min", it.second)
    }

    // ---- backup ----
    private val bundleKeys = listOf("nights", "workouts", "journal", "day_strain", "readings", "cal", "baselines", "stress", "hydration", "naps")

    fun exportAll(): String {
        val o = JSONObject()
        o.put("app", "wolve").put("version", 2)
        for (k in bundleKeys) {
            val s = p.getString(k, null)
            if (s != null) o.put(k, s)
        }
        o.put("age", age).put("male", male).put("weight", weightKg).put("rest_hr", restHr).put("height", heightCm)
        return o.toString()
    }

    fun importAll(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            if (o.optString("app") != "wolve") return false
            val e = p.edit()
            for (k in bundleKeys) {
                if (!o.has(k)) continue
                val s = o.getString(k)
                if (k == "journal" || k == "day_strain" || k == "baselines" || k == "hydration") JSONObject(s) else JSONArray(s)
                e.putString(k, s)
            }
            if (o.has("age")) e.putInt("age", o.getInt("age"))
            if (o.has("male")) e.putBoolean("male", o.getBoolean("male"))
            if (o.has("weight")) e.putFloat("weight", o.getDouble("weight").toFloat())
            if (o.has("height")) e.putFloat("height", o.getDouble("height").toFloat())
            if (o.has("rest_hr")) e.putFloat("rest_hr", o.getDouble("rest_hr").toFloat())
            e.apply()
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun clearAll() {
        p.edit().clear().apply()
    }

    private fun <T> readArr(key: String, f: (JSONObject) -> T): List<T> {
        val s = p.getString(key, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { f(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun <T> writeArr(key: String, list: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray()
        list.forEach { a.put(f(it)) }
        p.edit().putString(key, a.toString()).apply()
    }
}
