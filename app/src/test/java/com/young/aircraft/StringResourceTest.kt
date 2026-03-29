package com.young.aircraft

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

class StringResourceTest {

    @Test
    fun testAllStringsInDefaultStringsXmlAreTranslatedInValuesZh() {
        val projectDir = findProjectRoot()
        val resDir = File(projectDir, "app/src/main/res")
        val valuesDir = File(resDir, "values")
        val valuesZhDir = File(resDir, "values-zh")

        val defaultStrings = loadStringsFromXml(File(valuesDir, "strings.xml"))
        val zhStrings = loadStringsFromXml(File(valuesZhDir, "strings.xml"))

        val missingInZh = defaultStrings.keys - zhStrings.keys
        assertTrue(
            "Strings missing in values-zh/strings.xml: $missingInZh",
            missingInZh.isEmpty()
        )
    }

    @Test
    fun testNoUnusedStringsInDefaultStringsXml() {
        val projectDir = findProjectRoot()
        val resDir = File(projectDir, "app/src/main/res")
        val javaDir = File(projectDir, "app/src/main/java")
        val layoutDir = File(resDir, "layout")
        val valuesDir = File(resDir, "values")

        val definedStrings = loadStringsFromXml(File(valuesDir, "strings.xml")).keys
        val usedStrings = mutableSetOf<String>()

        // Scan layouts
        layoutDir.listFiles()?.forEach { file ->
            if (file.extension == "xml") {
                val content = file.readText()
                definedStrings.forEach { key ->
                    if (content.contains("@string/$key")) {
                        usedStrings.add(key)
                    }
                }
            }
        }

        // Scan Manifest
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val content = manifest.readText()
            definedStrings.forEach { key ->
                if (content.contains("@string/$key")) {
                    usedStrings.add(key)
                }
            }
        }

        // Scan Java/Kotlin code
        javaDir.walkTopDown().forEach { file ->
            if (file.extension == "kt" || file.extension == "java") {
                val content = file.readText()
                definedStrings.forEach { key ->
                    if (content.contains("R.string.$key")) {
                        usedStrings.add(key)
                    }
                }
            }
        }
        
        // Whitelist known strings used by Firebase or system
        val whitelist = setOf(
            "google_app_id",
            "google_api_key",
            "google_storage_bucket",
            "gcm_defaultSenderId",
            "project_id",
            "google_crash_reporting_api_key",
            "com.google.firebase.crashlytics.mapping_file_id",
            "com.google.firebase.crashlytics.version_control_info"
        )
        
        val unused = definedStrings - usedStrings - whitelist
        
        assertTrue(
            "Unused strings found: $unused",
            unused.isEmpty()
        )
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").absoluteFile
        while (current != null && !File(current, "settings.gradle").exists()) {
            current = current.parentFile
        }
        return current ?: File(".")
    }

    private fun loadStringsFromXml(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(file)
        val strings = mutableMapOf<String, String>()
        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            val nameNode = node.attributes.getNamedItem("name")
            if (nameNode != null) {
                val name = nameNode.nodeValue
                strings[name] = node.textContent
            }
        }
        return strings
    }
}
