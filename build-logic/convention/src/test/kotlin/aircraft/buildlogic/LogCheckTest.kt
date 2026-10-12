package aircraft.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Covers the matching core shared by checkAppLogUsage / checkDevLogUsage. */
class LogCheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun source(relPath: String, content: String) =
        tmp.root.resolve(relPath).also {
            it.parentFile.mkdirs()
            it.writeText(content)
        }

    @Test
    fun `flags direct android util Log import`() {
        val f = source("Foo.kt", "import android.util.Log\n")
        assertEquals(listOf(f), findDirectLogUsages(listOf(f), "/utils/AppLog.kt"))
    }

    @Test
    fun `flags aliased android util Log import`() {
        val f = source("Foo.kt", "import android.util.Log as ALog\n")
        assertEquals(listOf(f), findDirectLogUsages(listOf(f), "/utils/AppLog.kt"))
    }

    @Test
    fun `flags fully qualified Log usage`() {
        val f = source("Foo.kt", "fun x() { android.util.Log.d(TAG, \"x\") }\n")
        assertEquals(listOf(f), findDirectLogUsages(listOf(f), "/utils/AppLog.kt"))
    }

    @Test
    fun `flags bare Log usage`() {
        val f = source("Foo.kt", "fun x() { Log.d(TAG, \"x\") }\n")
        assertEquals(listOf(f), findDirectLogUsages(listOf(f), "/utils/AppLog.kt"))
    }

    @Test
    fun `passes AppLog trailing-lambda call form`() {
        val f = source("Foo.kt", "fun x() { AppLog.d(TAG) { \"hello\" } }\n")
        assertTrue(findDirectLogUsages(listOf(f), "/utils/AppLog.kt").isEmpty())
    }

    @Test
    fun `passes DevLog helper`() {
        val f = source("Foo.kt", "fun x() { DevLog.d(TAG, \"hello\") }\n")
        assertTrue(findDirectLogUsages(listOf(f), "/utils/DevLog.kt").isEmpty())
    }

    @Test
    fun `passes lookalike identifiers`() {
        val f = source("Foo.kt", "Catalog.show()\nDialog.dismiss()\nMyLog.d(TAG)\n")
        assertTrue(findDirectLogUsages(listOf(f), "/utils/AppLog.kt").isEmpty())
    }

    @Test
    fun `allowlisted file is excluded even with violations`() {
        val allowed = source("utils/AppLog.kt", "import android.util.Log\n")
        val other = source("other/Foo.kt", "import android.util.Log\n")
        assertEquals(listOf(other), findDirectLogUsages(listOf(allowed, other), "/utils/AppLog.kt"))
    }

    @Test
    fun `clean file passes`() {
        val f = source("Foo.kt", "package com.example\n\nfun x() = 1\n")
        assertTrue(findDirectLogUsages(listOf(f), "/utils/AppLog.kt").isEmpty())
    }
}
