package com.wolve.app.data

import com.wolve.app.analytics.BEHAVIORS
import com.wolve.app.analytics.JournalInsights
import com.wolve.app.analytics.SleepScore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup as a ZIP of Excel workbooks (readable anywhere) plus a JSON file used for lossless restore. */
object BackupZip {
    private const val JSON_NAME = "wolve-backup.json"
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private fun at(ts: Long): String = stamp.format(Instant.ofEpochMilli(ts))

    private fun tables(store: Store): List<Pair<String, List<List<Any>>>> {
        val nights = store.loadNights()
        val workouts = store.loadWorkouts()
        val strains = store.loadDayStrains()
        val journal = store.loadJournal()

        val cycles = ArrayList<List<Any>>()
        cycles.add(listOf("Date", "Recovery %", "HRV (ms)", "Resting HR (bpm)", "Respiratory rate", "Day strain", "Sleep performance %"))
        for (n in nights) {
            val d = JournalInsights.dateOf(n.wakeTs).toString()
            cycles.add(listOf(d, n.recovery, round1(n.hrv), round1(n.rhr), round1(n.resp), round1(strains[d] ?: 0.0), n.performance))
        }

        val sleeps = ArrayList<List<Any>>()
        sleeps.add(listOf("Sleep onset", "Wake", "Asleep (min)", "Light (min)", "Deep (min)", "REM (min)", "Awake (min)", "Efficiency %", "Latency (min)", "Wake-ups", "Sleep score"))
        for (n in nights) {
            sleeps.add(listOf(at(n.onsetTs), at(n.wakeTs), n.totalMin, n.lightMin, n.deepMin, n.remMin, n.awakeMin, (n.efficiency * 100).toInt(), n.latencyMin, n.awakenings, SleepScore.of(n).total))
        }

        val work = ArrayList<List<Any>>()
        work.add(listOf("Start", "End", "Sport", "Duration (min)", "Avg HR", "Max HR", "Calories", "Strain", "Zone 1 (s)", "Zone 2 (s)", "Zone 3 (s)", "Zone 4 (s)", "Zone 5 (s)"))
        for (w in workouts) {
            val z = w.zoneSec
            work.add(listOf(at(w.startTs), at(w.endTs), w.sport, (w.endTs - w.startTs) / 60000L, w.avgHr, w.maxHr, w.kcal, round1(w.strain),
                z.getOrElse(1) { 0 }, z.getOrElse(2) { 0 }, z.getOrElse(3) { 0 }, z.getOrElse(4) { 0 }, z.getOrElse(5) { 0 }))
        }

        val jr = ArrayList<List<Any>>()
        jr.add(listOf("Date", "Habit", "Logged"))
        for (d in journal.keys.sorted()) {
            for (id in journal.getValue(d)) jr.add(listOf(d, BEHAVIORS.firstOrNull { it.id == id }?.label ?: id, "Yes"))
        }

        val bp = ArrayList<List<Any>>()
        bp.add(listOf("Time", "Systolic (mmHg)", "Diastolic (mmHg)", "Margin (mmHg)", "Heart rate", "HRV (ms)"))
        for (r in store.loadReadings()) bp.add(listOf(at(r.tsMs), r.sys.toInt(), r.dia.toInt(), r.margin.toInt(), r.hr.toInt(), r.rmssd.toInt()))

        return listOf(
            Pair("physiological_cycles", cycles), Pair("sleeps", sleeps), Pair("workouts", work),
            Pair("journal_entries", jr), Pair("blood_pressure", bp),
        )
    }

    fun build(store: Store): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
            for ((name, rows) in tables(store)) put("$name.xlsx", xlsx(name.replace('_', ' '), rows))
            put(JSON_NAME, store.exportAll().toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    /** Plain CSV files in the same layout (days, sleeps, workouts, journal) for spreadsheets and other apps. */
    fun buildCsv(store: Store): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, rows) in tables(store)) {
                z.putNextEntry(ZipEntry("$name.csv"))
                val text = rows.joinToString("\n") { row -> row.joinToString(",") { v -> "\"" + v.toString().replace("\"", "\"\"") + "\"" } }
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Accepts a Wolve ZIP or a bare backup JSON. */
    fun restore(bytes: ByteArray, store: Store): Boolean {
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (e.name == JSON_NAME) return store.importAll(String(zin.readBytes(), Charsets.UTF_8))
                    e = zin.nextEntry
                }
            }
        } catch (ex: Exception) {
            // not a zip, fall through to JSON
        }
        return store.importAll(String(bytes, Charsets.UTF_8))
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun col(i: Int): String = ('A' + i).toString()

    /** Minimal valid .xlsx: one sheet, numbers as numbers, text as inline strings. */
    private fun xlsx(sheet: String, rows: List<List<Any>>): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"").append(r + 1).append("\">")
            row.forEachIndexed { c, v ->
                val ref = col(c) + (r + 1)
                if (v is Int || v is Long || v is Double) {
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(v.toString()).append("</v></c>")
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>").append(esc(v.toString())).append("</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            put("[Content_Types].xml", head +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            put("_rels/.rels", head +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", head +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + esc(sheet) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", head +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
        return out.toByteArray()
    }
}
