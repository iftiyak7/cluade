package com.hayat.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class LocalizationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test @Config(qualifiers = "bn")
    fun `bengali resources are packaged across modules`() {
        assertEquals("হায়াত", context.getString(R.string.app_name))
        assertEquals("সংরক্ষণ", context.getString(com.hayat.core.ui.R.string.action_save))
        assertEquals("ফজর", context.getString(com.hayat.core.ui.R.string.prayer_fajr))
        assertEquals("পানি", context.getString(com.hayat.feature.water.R.string.water_title))
    }

    @Test @Config(qualifiers = "en")
    fun `english is the default`() {
        assertEquals("Hayat", context.getString(R.string.app_name))
        assertEquals("Save", context.getString(com.hayat.core.ui.R.string.action_save))
    }
}
