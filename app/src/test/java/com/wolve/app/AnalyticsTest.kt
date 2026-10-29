package com.wolve.app

import com.wolve.app.analytics.Baselines
import com.wolve.app.analytics.BloodPressure
import com.wolve.app.analytics.BpCategory
import com.wolve.app.analytics.Ecg
import com.wolve.app.analytics.Hrv
import com.wolve.app.analytics.Recovery
import com.wolve.app.analytics.Coach
import com.wolve.app.analytics.JournalInsights
import com.wolve.app.analytics.Nutrition
import com.wolve.app.analytics.Resp
import com.wolve.app.analytics.SleepScore
import com.wolve.app.analytics.Weekly
import com.wolve.app.analytics.Workout
import com.wolve.app.analytics.SleepNight
import com.wolve.app.analytics.StressMath
import com.wolve.app.analytics.Zones
import com.wolve.app.analytics.SampleNight
import com.wolve.app.analytics.StrainMath
import com.wolve.app.data.BpCalPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

class AnalyticsTest {
    @Test fun rmssdOfConstantIntervalsIsZero() {
        assertEquals(0.0, Hrv.rmssd(List(30) { 800.0 }), 1e-9)
    }

    @Test fun cleanDropsOutliers() {
        val rr = List(20) { 800.0 } + listOf(250.0, 2500.0, 1500.0)
        assertEquals(20, Hrv.clean(rr).size)
    }

    @Test fun categoriesFollowGuidelineThresholds() {
        assertEquals(BpCategory.NORMAL, BloodPressure.categorize(118.0, 76.0))
        assertEquals(BpCategory.ELEVATED, BloodPressure.categorize(125.0, 78.0))
        assertEquals(BpCategory.STAGE1, BloodPressure.categorize(135.0, 78.0))
        assertEquals(BpCategory.STAGE2, BloodPressure.categorize(150.0, 95.0))
        assertEquals(BpCategory.CRISIS, BloodPressure.categorize(190.0, 100.0))
    }

    @Test fun higherHeartRateRaisesUncalibratedEstimate() {
        val lo = BloodPressure.estimate(60.0, 40.0, emptyList())
        val hi = BloodPressure.estimate(95.0, 40.0, emptyList())
        assertTrue(hi.sys > lo.sys && hi.dia > lo.dia)
    }

    @Test fun calibrationShiftsEstimateTowardCuff() {
        val cal = listOf(BpCalPoint(0, 135.0, 85.0, 70.0, 40.0))
        val e = BloodPressure.estimate(70.0, 40.0, cal)
        assertEquals(135.0, e.sys, 1.0)
        assertTrue(e.calibrated)
    }

    @Test fun detectsRPeaksInSyntheticEcg() {
        val fs = 130.0
        val n = (60 * fs).toInt()
        val x = DoubleArray(n)
        val beats = ArrayList<Double>()
        var b = 0.5
        while (b < 60.0) { beats.add(b); b += 0.85 }
        fun g(d: Double, s: Double, a: Double) = a * exp(-0.5 * (d / s) * (d / s))
        for (i in 0 until n) {
            val t = i / fs
            var v = 300.0 * sin(2 * Math.PI * 0.25 * t)
            for (bt in beats) v += g(t - bt, 0.012, 1100.0) + g(t - bt - 0.25, 0.04, 280.0)
            x[i] = v
        }
        val peaks = Ecg.detectRPeaks(x, fs)
        assertTrue(abs(peaks.size - beats.size) <= 1)
    }

    @Test fun respiratoryRateIsRecoveredFromSinusArrhythmia() {
        val rnd = Random(3)
        for (brpm in listOf(10, 14, 18)) {
            val f = brpm / 60.0
            val t = ArrayList<Double>()
            val rr = ArrayList<Double>()
            var tm = 0.0
            while (tm < 300_000.0) {
                val r = 900.0 + 45.0 * sin(2 * Math.PI * f * tm / 1000.0) + rnd.nextGaussian() * 12.0
                t.add(tm)
                rr.add(r)
                tm += r
            }
            val est = Resp.estimate(t.toDoubleArray(), rr.toDoubleArray())
            assertTrue("expected about $brpm got $est", abs(est - brpm) <= 1.0)
        }
    }

    @Test fun sampleNightIsScoredWithPlausibleArchitecture() {
        val n = SampleNight.build()
        assertNotNull(n)
        n!!
        assertTrue(n.totalMin in 400..600)
        assertTrue(n.deepMin > 0 && n.remMin > 0 && n.lightMin > 0)
        assertEquals(n.totalMin, n.deepMin + n.remMin + n.lightMin)
        assertTrue(n.deepMin <= (n.totalMin * 0.30).toInt() + 5)
        assertEquals(n.stages.length * 5, n.totalMin + n.awakeMin)
        assertTrue(abs(n.rhr - 54.0) < 3.0)
    }

    @Test fun recoveryRisesWithHrvAndFallsWithRestingHr() {
        val b = Baselines(10, ln(50.0), 0.02, 55.0, 4.0, 14.0, 0.4, 10)
        val base = Recovery.score(b, 50.0, 55.0, 14.0, 90)
        assertTrue(Recovery.score(b, 80.0, 55.0, 14.0, 90) > base)
        assertTrue(Recovery.score(b, 50.0, 62.0, 14.0, 90) < base)
        assertTrue(Recovery.score(b, 50.0, 55.0, 14.0, 50) < base)
    }

    @Test fun baselinesNeedFourNightsToCalibrate() {
        var b = Baselines()
        for (i in 1..3) b = Recovery.update(b, 50.0, 55.0, 14.0)
        assertTrue(Recovery.isCalibrating(b))
        b = Recovery.update(b, 50.0, 55.0, 14.0)
        assertTrue(!Recovery.isCalibrating(b))
    }

    @Test fun strainGrowsWithIntensityAndStaysBelowScaleMax() {
        val easy = StrainMath.trimp(60.0, 0.4, true)
        val hard = StrainMath.trimp(60.0, 0.8, true)
        assertTrue(hard > easy * 2)
        assertTrue(StrainMath.scale(1_000_000.0) < 21.0001)
        assertEquals(0.0, StrainMath.scale(0.0), 1e-9)
    }

    @Test fun zonesFollowPercentOfMaxHeartRate() {
        assertEquals(0, Zones.zoneOf(90, 190.0))
        assertEquals(1, Zones.zoneOf(105, 190.0))
        assertEquals(3, Zones.zoneOf(140, 190.0))
        assertEquals(5, Zones.zoneOf(180, 190.0))
    }

    @Test fun caloriesRiseWithHeartRateAndNeverGoNegative() {
        assertTrue(Zones.kcalPerMin(150, 30, 75.0, true) > Zones.kcalPerMin(110, 30, 75.0, true))
        assertEquals(0.0, Zones.kcalPerMin(40, 30, 75.0, true), 1e-9)
    }

    @Test fun lowerHrvMeansHigherStress() {
        val base = ln(50.0)
        assertTrue(StressMath.score(30.0, base, 0.04) > StressMath.score(50.0, base, 0.04))
        assertTrue(StressMath.score(80.0, base, 0.04) < StressMath.score(50.0, base, 0.04))
        assertTrue(StressMath.score(1.0, base, 0.04) <= 3.0)
    }

    @Test fun coachTargetFollowsRecovery() {
        assertEquals(14, Coach.strainTarget(80).low)
        assertEquals(10, Coach.strainTarget(50).low)
        assertEquals(4, Coach.strainTarget(10).low)
        assertEquals(22 * 60, Coach.bedtimeMin(7 * 60, 525, 15))
        assertEquals(23 * 60 + 15, Coach.bedtimeMin(7 * 60, 450, 15))
    }

    private fun night(wakeTs: Long, rec: Int) = SleepNight(
        startTs = wakeTs - 8 * 3600_000L, endTs = wakeTs, onsetTs = wakeTs - 7 * 3600_000L, wakeTs = wakeTs,
        totalMin = 420, awakeMin = 20, lightMin = 250, deepMin = 80, remMin = 90, efficiency = 0.9, latencyMin = 10,
        awakenings = 1, rhr = 55.0, hrv = 50.0, resp = 14.0, coverage = 1.0, performance = 90, recovery = rec,
        calibrating = false, stages = "",
    )

    @Test fun journalEffectComparesNextMorningRecovery() {
        val journal = HashMap<String, Set<String>>()
        val nights = ArrayList<SleepNight>()
        val start = java.time.LocalDate.of(2026, 3, 1)
        for (i in 0 until 12) {
            val d = start.plusDays(i.toLong())
            val alcohol = i % 2 == 0
            journal[d.toString()] = if (alcohol) setOf("alcohol") else emptySet()
            val wake = d.plusDays(1).atTime(7, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            nights.add(night(wake, if (alcohol) 40 else 70))
        }
        val e = JournalInsights.effects(journal, nights).first { it.id == "alcohol" }
        assertEquals(-30.0, e.delta, 1e-6)
    }

    @Test fun sleepScoreRewardsDurationAndPenalisesDisturbances() {
        val good = night(0L, 70).copy(totalMin = 480, efficiency = 0.95, awakenings = 1, awakeMin = 10, latencyMin = 10)
        val bad = good.copy(totalMin = 300, efficiency = 0.70, awakenings = 6, awakeMin = 60, latencyMin = 45)
        val g = SleepScore.of(good)
        assertEquals(100, g.total)
        assertTrue(SleepScore.of(bad).total < 50)
        assertEquals(g.total, g.duration + g.efficiency + g.disturbance)
    }

    @Test fun nutritionScalesWithWeightAndStrain() {
        val a = Nutrition.plan(37, 69.0, 170.0, true, 8.0)
        assertEquals(10 * 69 + 6.25 * 170 - 5 * 37 + 5, a.bmr.toDouble(), 1.0)
        assertEquals(138, a.proteinG)
        assertTrue(Nutrition.plan(37, 69.0, 170.0, true, 16.0).tdee > a.tdee)
        assertEquals(a.tdee, a.meals.sumOf { it.kcal }, 3)
    }

    @Test fun weeklySummaryCountsActiveMinutesAndWorkouts() {
        val end = java.time.LocalDate.of(2026, 3, 10)
        fun ts(d: java.time.LocalDate) = d.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val w1 = Workout(ts(end), ts(end) + 45 * 60_000L, "Running", 150, 175, 500, 12.0, listOf(0, 0, 0, 0, 0, 0))
        val w2 = Workout(ts(end.minusDays(2)), ts(end.minusDays(2)) + 30 * 60_000L, "Cycling", 140, 165, 300, 9.0, listOf(0, 0, 0, 0, 0, 0))
        val old = Workout(ts(end.minusDays(20)), ts(end.minusDays(20)) + 60 * 60_000L, "Yoga", 100, 120, 150, 4.0, listOf(0, 0, 0, 0, 0, 0))
        val s = Weekly.summarize(end, listOf(w1, w2, old), emptyList(), mapOf(end.toString() to 12.0))
        assertEquals(75, s.activeMin)
        assertEquals(2, s.sessions)
        assertEquals(800, s.kcal)
        assertEquals(12.0, s.avgStrain, 1e-9)
    }
}
