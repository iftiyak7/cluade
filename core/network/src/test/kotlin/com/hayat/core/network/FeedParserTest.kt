package com.hayat.core.network

import com.hayat.core.model.AnnouncementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FeedParserTest {
    @Test fun `parses the server's v1 format and skips malformed items`() {
        val body = """
            {"version":1,"generated_at":"2026-11-24T06:00:00Z","items":[
              {"id":1,"kind":"both","title_en":"Hi","body_en":"B","title_bn":"হাই","body_bn":"ব","cta_label_en":"Open","cta_url":"hayat://water",
               "lang":"all","min_version":2,"priority":50,"starts_at":"2026-11-24T04:00:00Z","ends_at":"2026-11-30T04:00:00+06:00","updated_at":"2026-11-24T05:00:00Z","future_field":true},
              {"id":2,"kind":"popup","title_en":"x","starts_at":"2026-11-24T04:00:00Z"},
              {"id":3,"kind":"banner","title_en":"x","starts_at":"not a date"}
            ]}
        """.trimIndent()
        val items = FeedParser.parse(body)!!
        assertEquals(1, items.size)
        val a = items.single()
        assertEquals(AnnouncementKind.BOTH, a.kind)
        assertEquals("হাই", a.titleBn)
        assertEquals(Instant.parse("2026-11-29T22:00:00Z"), a.endsAt)
        assertEquals(2, a.minVersion)
    }

    @Test fun `rejects unknown versions and non-JSON`() {
        assertNull(FeedParser.parse("""{"version":2,"items":[]}"""))
        assertNull(FeedParser.parse("<html>captive portal</html>"))
        assertEquals(0, FeedParser.parse("""{"version":1,"items":[]}""")!!.size)
    }

    @Test fun `caps the number of items`() {
        val items = (1..80).joinToString(",") { """{"id":$it,"kind":"banner","title_en":"t","starts_at":"2026-11-24T04:00:00Z"}""" }
        assertEquals(FeedParser.MAX_ITEMS, FeedParser.parse("""{"version":1,"items":[$items]}""")!!.size)
    }
}
