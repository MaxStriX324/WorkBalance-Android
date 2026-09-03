package ru.maxstrix.workbalance.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun `semantic versions are compared numerically`() {
        assertTrue(AppUpdateChecker.isNewer("0.6.0", "0.5.1"))
        assertTrue(AppUpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertFalse(AppUpdateChecker.isNewer("0.5.1", "0.5.1"))
        assertFalse(AppUpdateChecker.isNewer("0.4.0", "0.5.1"))
    }

    @Test
    fun `latest release ignores archives drafts and prereleases`() {
        val json = """
            [
              {"tag_name":"archive-v0.2.0","html_url":"archive","body":"old"},
              {"tag_name":"v0.7.0","html_url":"draft","body":"draft","draft":true},
              {"tag_name":"v0.6.0","html_url":"release","body":"new"},
              {"tag_name":"v0.5.1","html_url":"previous","body":"previous"}
            ]
        """.trimIndent()

        val release = AppUpdateChecker.selectLatestRelease(json)

        assertEquals("0.6.0", release?.version)
        assertEquals("release", release?.pageUrl)
        assertEquals("new", release?.notes)
    }
}
