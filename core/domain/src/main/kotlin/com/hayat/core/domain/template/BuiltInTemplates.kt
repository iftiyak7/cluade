package com.hayat.core.domain.template

import com.hayat.core.model.DateRule
import com.hayat.core.model.RoutineKind
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.TemplateItem
import com.hayat.core.model.WeekdaySet
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Bundled routine templates. Item titles are stable keys ("tpl.*") that the UI localizes; when a
 * template is applied, the localized titles are written into the user's own routine, which is
 * then fully editable.
 */
object BuiltInTemplates {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun item(key: String, h: Int, m: Int, duration: Int) = TemplateItem("tpl.$key", t(h, m), duration)

    val all: List<RoutineTemplate> = listOf(
        RoutineTemplate("builtin.balanced", "balanced", "balanced", "", RoutineKind.GENERAL, DateRule.Daily(), listOf(
            item("wake_up", 6, 0, 15), item("exercise", 6, 30, 30), item("breakfast", 7, 15, 30),
            item("deep_work", 9, 0, 120), item("lunch", 13, 0, 45), item("walk", 17, 30, 30),
            item("dinner", 20, 0, 45), item("reading", 21, 0, 30), item("wind_down", 22, 0, 30),
        )),
        RoutineTemplate("builtin.student", "student", "student", "", RoutineKind.STUDY, DateRule.Weekly(WeekdaySet.BD_WORKWEEK), listOf(
            item("wake_up", 6, 30, 15), item("breakfast", 7, 0, 30), item("classes", 8, 0, 300),
            item("lunch", 13, 30, 45), item("rest", 14, 15, 45), item("self_study", 16, 0, 120),
            item("revision", 20, 30, 60), item("sleep_prep", 22, 30, 30),
        )),
        RoutineTemplate("builtin.office", "office", "office", "", RoutineKind.WORKDAY, DateRule.Weekly(WeekdaySet.BD_WORKWEEK), listOf(
            item("wake_up", 6, 30, 15), item("breakfast", 7, 15, 30), item("commute", 8, 0, 60),
            item("work_morning", 9, 0, 240), item("lunch", 13, 0, 60), item("work_afternoon", 14, 0, 180),
            item("commute", 17, 0, 60), item("family_time", 19, 0, 90), item("wind_down", 22, 0, 30),
        )),
        RoutineTemplate("builtin.freelancer", "freelancer", "freelancer", "", RoutineKind.WORKDAY, DateRule.Daily(), listOf(
            item("wake_up", 7, 0, 15), item("planning", 8, 0, 30), item("deep_work", 8, 30, 150),
            item("email", 11, 0, 30), item("lunch", 13, 0, 60), item("client_work", 14, 0, 180),
            item("exercise", 17, 30, 45), item("learning", 20, 30, 60),
        )),
        RoutineTemplate("builtin.muslim", "muslim", "muslim", "", RoutineKind.GENERAL, DateRule.Daily(), listOf(
            item("fajr", 5, 0, 20), item("morning_adhkar", 5, 20, 15), item("quran", 5, 35, 20),
            item("dhuhr", 13, 15, 20), item("asr", 16, 30, 15), item("maghrib", 17, 45, 15),
            item("evening_adhkar", 18, 0, 15), item("isha", 19, 30, 20),
        )),
        RoutineTemplate("builtin.ramadan", "ramadan", "ramadan", "", RoutineKind.RAMADAN, DateRule.Daily(), listOf(
            item("suhoor", 3, 45, 30), item("fajr", 4, 30, 20), item("quran", 5, 0, 30), item("rest", 5, 30, 90),
            item("work_morning", 9, 0, 180), item("dhuhr", 13, 0, 20), item("rest", 14, 0, 60),
            item("iftar_prep", 17, 0, 45), item("iftar", 17, 50, 30), item("taraweeh", 20, 0, 90),
        )),
        RoutineTemplate("builtin.exam", "exam", "exam", "", RoutineKind.STUDY, DateRule.Daily(), listOf(
            item("wake_up", 6, 0, 15), item("revision", 6, 30, 90), item("breakfast", 8, 0, 30),
            item("practice_test", 9, 0, 120), item("break", 11, 0, 30), item("weak_topics", 11, 30, 90),
            item("lunch", 13, 0, 60), item("revision", 15, 0, 120), item("light_review", 21, 0, 45),
        )),
        RoutineTemplate("builtin.fitness", "fitness", "fitness", "", RoutineKind.FITNESS,
            DateRule.Weekly(WeekdaySet.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), listOf(
            item("warm_up", 6, 30, 10), item("workout", 6, 40, 45), item("stretching", 7, 25, 10), item("protein_breakfast", 7, 45, 30),
        )),
        RoutineTemplate("builtin.sleep", "sleep", "sleep", "", RoutineKind.GENERAL, DateRule.Daily(), listOf(
            item("screens_off", 21, 30, 15), item("reading", 21, 45, 30), item("wind_down", 22, 15, 15), item("lights_out", 22, 30, 5),
        )),
        RoutineTemplate("builtin.productivity", "productivity", "productivity", "", RoutineKind.GENERAL, DateRule.Weekly(WeekdaySet.BD_WORKWEEK), listOf(
            item("planning", 7, 30, 15), item("deep_work", 8, 0, 90), item("break", 9, 30, 15), item("deep_work", 9, 45, 90),
            item("email", 11, 15, 30), item("review_day", 17, 30, 15),
        )),
    )

    fun byId(id: String): RoutineTemplate? = all.firstOrNull { it.id == id }
}
