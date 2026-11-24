package com.hayat.core.ui

import com.hayat.core.model.DateFormatPref
import com.hayat.core.model.DigitStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

class FormatterTest {
    private val bn = HayatFormatter(Locale.forLanguageTag("bn"), use24h = false, dateStyle = DateFormatPref.MEDIUM, digitStyle = DigitStyle.AUTO)
    private val en = HayatFormatter(Locale.ENGLISH, use24h = false, dateStyle = DateFormatPref.MEDIUM, digitStyle = DigitStyle.AUTO)

    @Test fun `bengali time uses day period words and bengali digits`() {
        assertEquals("সকাল ১১:৪৬", bn.time(LocalTime.of(11, 46)))
        assertEquals("ভোর ৫:০৫", bn.time(LocalTime.of(5, 5)))
        assertEquals("দুপুর ১:১৫", bn.time(LocalTime.of(13, 15)))
        assertEquals("বিকাল ৪:০০", bn.time(LocalTime.of(16, 0)))
        assertEquals("সন্ধ্যা ৬:৩০", bn.time(LocalTime.of(18, 30)))
        assertEquals("রাত ৯:০৫", bn.time(LocalTime.of(21, 5)))
        assertEquals("11:46 AM", en.time(LocalTime.of(11, 46)))
    }

    @Test fun `units are localized`() {
        assertEquals("২৫০ মিলি", bn.ml(250))
        assertEquals("250 ml", en.ml(250))
        assertEquals("৪৫ মিনিট", bn.minutes(45))
        assertEquals("45 min", en.minutes(45))
    }

    @Test fun `hijri date has the month name`() {
        assertEquals("14 Jumada II 1448", en.hijri(14, 6, 1448))
        assertEquals("১৪ জমাদিউস সানি ১৪৪৮", bn.hijri(14, 6, 1448))
    }

    @Test fun `countdown and relative time`() {
        assertEquals("in 4h 21m", en.countdown(261))
        assertEquals("৪ ঘণ্টা ২১ মিনিট পর", bn.countdown(261))
        assertEquals("in 9m", en.countdown(9))
        assertEquals("2 min ago", en.ago(2))
        assertEquals("২ মিনিট আগে", bn.ago(2))
    }

    @Test fun `chart weekday labels are unambiguous`() {
        val labels = listOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY).map { en.weekdayShort(it) }
        assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"), labels)
        assertEquals(7, labels.toSet().size)
    }
}
