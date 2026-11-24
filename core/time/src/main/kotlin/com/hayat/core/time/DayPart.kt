package com.hayat.core.time

/** Part of the day used for greetings: 05–12 morning, 12–17 afternoon, 17–21 evening, 21–05 night. */
enum class DayPart {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

/** Hijri month names (Umm al-Qura numbering 1–12). */
object HijriMonths {
    val english = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah")
    val bengali = listOf("মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি", "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ")

    fun name(month: Int, bengali: Boolean): String = (if (bengali) this.bengali else english).getOrElse(month - 1) { month.toString() }
}
