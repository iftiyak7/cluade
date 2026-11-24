package com.hayat.core.ui

import androidx.annotation.StringRes

/** Localized titles for bundled template items ("tpl.<key>") and template names. */
object TemplateStrings {
    @StringRes fun item(key: String): Int? = when (key.removePrefix("tpl.")) {
        "wake_up" -> R.string.tpl_wake_up
        "exercise" -> R.string.tpl_exercise
        "breakfast" -> R.string.tpl_breakfast
        "deep_work" -> R.string.tpl_deep_work
        "lunch" -> R.string.tpl_lunch
        "walk" -> R.string.tpl_walk
        "dinner" -> R.string.tpl_dinner
        "reading" -> R.string.tpl_reading
        "wind_down" -> R.string.tpl_wind_down
        "classes" -> R.string.tpl_classes
        "rest" -> R.string.tpl_rest
        "self_study" -> R.string.tpl_self_study
        "revision" -> R.string.tpl_revision
        "sleep_prep" -> R.string.tpl_sleep_prep
        "commute" -> R.string.tpl_commute
        "work_morning" -> R.string.tpl_work_morning
        "work_afternoon" -> R.string.tpl_work_afternoon
        "family_time" -> R.string.tpl_family_time
        "planning" -> R.string.tpl_planning
        "email" -> R.string.tpl_email
        "client_work" -> R.string.tpl_client_work
        "learning" -> R.string.tpl_learning
        "fajr" -> R.string.tpl_fajr
        "morning_adhkar" -> R.string.tpl_morning_adhkar
        "quran" -> R.string.tpl_quran
        "dhuhr" -> R.string.tpl_dhuhr
        "asr" -> R.string.tpl_asr
        "maghrib" -> R.string.tpl_maghrib
        "evening_adhkar" -> R.string.tpl_evening_adhkar
        "isha" -> R.string.tpl_isha
        "suhoor" -> R.string.tpl_suhoor
        "iftar_prep" -> R.string.tpl_iftar_prep
        "iftar" -> R.string.tpl_iftar
        "taraweeh" -> R.string.tpl_taraweeh
        "practice_test" -> R.string.tpl_practice_test
        "break" -> R.string.tpl_break
        "weak_topics" -> R.string.tpl_weak_topics
        "light_review" -> R.string.tpl_light_review
        "warm_up" -> R.string.tpl_warm_up
        "workout" -> R.string.tpl_workout
        "stretching" -> R.string.tpl_stretching
        "protein_breakfast" -> R.string.tpl_protein_breakfast
        "screens_off" -> R.string.tpl_screens_off
        "lights_out" -> R.string.tpl_lights_out
        "review_day" -> R.string.tpl_review_day
        else -> null
    }

    @StringRes fun name(systemKey: String?): Int? = when (systemKey) {
        "balanced" -> R.string.tplname_balanced
        "student" -> R.string.tplname_student
        "office" -> R.string.tplname_office
        "freelancer" -> R.string.tplname_freelancer
        "muslim" -> R.string.tplname_muslim
        "ramadan" -> R.string.tplname_ramadan
        "exam" -> R.string.tplname_exam
        "fitness" -> R.string.tplname_fitness
        "sleep" -> R.string.tplname_sleep
        "productivity" -> R.string.tplname_productivity
        else -> null
    }

    @StringRes fun description(systemKey: String?): Int? = when (systemKey) {
        "balanced" -> R.string.tpldesc_balanced
        "student" -> R.string.tpldesc_student
        "office" -> R.string.tpldesc_office
        "freelancer" -> R.string.tpldesc_freelancer
        "muslim" -> R.string.tpldesc_muslim
        "ramadan" -> R.string.tpldesc_ramadan
        "exam" -> R.string.tpldesc_exam
        "fitness" -> R.string.tpldesc_fitness
        "sleep" -> R.string.tpldesc_sleep
        "productivity" -> R.string.tpldesc_productivity
        else -> null
    }
}
