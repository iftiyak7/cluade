package com.hayat.core.model

import java.time.DayOfWeek

enum class Priority { LOW, MEDIUM, HIGH, URGENT }

/** Set of weekdays stored as a bitmask: Monday = bit 0 … Sunday = bit 6. */
@JvmInline
value class WeekdaySet(val mask: Int) {
    init { require(mask in 0..0x7F) { "invalid weekday mask $mask" } }

    operator fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun with(day: DayOfWeek): WeekdaySet = WeekdaySet(mask or bit(day))
    fun without(day: DayOfWeek): WeekdaySet = WeekdaySet(mask and bit(day).inv() and 0x7F)
    fun toggle(day: DayOfWeek): WeekdaySet = if (day in this) without(day) else with(day)
    val isEmpty: Boolean get() = mask == 0
    val days: List<DayOfWeek> get() = DayOfWeek.entries.filter { it in this }
    val size: Int get() = Integer.bitCount(mask)

    companion object {
        private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
        val NONE = WeekdaySet(0)
        val ALL = WeekdaySet(0x7F)
        fun of(vararg days: DayOfWeek) = WeekdaySet(days.fold(0) { acc, d -> acc or bit(d) })
        fun of(days: Collection<DayOfWeek>) = WeekdaySet(days.fold(0) { acc, d -> acc or bit(d) })
        /** Sunday–Thursday: the standard working week in Bangladesh. */
        val BD_WORKWEEK = of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        /** Monday–Friday. */
        val MON_FRI = of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

data class Category(
    val id: String,
    val name: String,
    /** Non-null for built-in categories; UI maps it to a localized name. */
    val systemKey: String?,
    val color: Long,
    val icon: String,
    val position: Int,
)

data class Tag(val id: String, val name: String)
