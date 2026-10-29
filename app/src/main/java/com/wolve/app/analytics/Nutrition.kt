package com.wolve.app.analytics

import kotlin.math.roundToInt

data class Meal(val name: String, val kcal: Int, val proteinG: Int, val carbG: Int, val fatG: Int)

data class MealPlan(
    val bmr: Int,
    val tdee: Int,
    val proteinG: Int,
    val carbG: Int,
    val fatG: Int,
    val waterMl: Int,
    val meals: List<Meal>,
)

/** Energy and macro targets from age, weight, height, sex and today's strain (Mifflin-St Jeor). */
object Nutrition {
    fun plan(age: Int, weightKg: Double, heightCm: Double, male: Boolean, strain: Double): MealPlan {
        val bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + (if (male) 5.0 else -161.0)
        val tdee = bmr * (1.35 + 0.025 * strain.coerceIn(0.0, 21.0))
        val protein = 2.0 * weightKg
        val fat = tdee * 0.25 / 9.0
        val carbs = ((tdee - protein * 4.0 - fat * 9.0) / 4.0).coerceAtLeast(0.0)
        val water = 35.0 * weightKg + 25.0 * strain.coerceIn(0.0, 21.0)
        fun meal(name: String, share: Double) = Meal(
            name, (tdee * share).roundToInt(), (protein * share).roundToInt(), (carbs * share).roundToInt(), (fat * share).roundToInt(),
        )
        return MealPlan(
            bmr.roundToInt(), tdee.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt(), water.roundToInt(),
            listOf(meal("Breakfast", 0.30), meal("Lunch", 0.40), meal("Dinner", 0.30)),
        )
    }
}
