package com.young.aircraft

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceTest {

    private val modulePath = System.getProperty("user.dir")?.let {
        if (it.endsWith("app")) it else "$it/app"
    } ?: "app"

    private val resDir = File(modulePath, "src/main/res")
    private val javaDir = File(modulePath, "src/main/java")
    private val manifestFile = File(modulePath, "src/main/AndroidManifest.xml")

    private fun getStrings(localeDir: String = "values"): Set<String> {
        val stringsFile = File(resDir, "$localeDir/strings.xml")
        if (!stringsFile.exists()) return emptySet()

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(stringsFile)
        doc.documentElement.normalize()

        val keys = mutableSetOf<String>()
        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            keys.add(name)
        }
        
        val arrayList = doc.getElementsByTagName("string-array")
        for (i in 0 until arrayList.length) {
            val node = arrayList.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            keys.add(name)
        }
        
        return keys
    }

    @Test
    fun `verify no i18n uncovered strings`() {
        val defaultStrings = getStrings("values")
        val zhStrings = getStrings("values-zh")

        val missingInZh = defaultStrings - zhStrings
        val extraInZh = zhStrings - defaultStrings

        assertTrue(
            "Strings missing in values-zh: $missingInZh",
            missingInZh.isEmpty()
        )
        
        // strict check: zh should not have extra keys either
        assertTrue(
            "Strings in values-zh but not in values: $extraInZh",
            extraInZh.isEmpty()
        )
    }

    @Test
    fun `verify no unused strings`() {
        val definedStrings = getStrings("values")
        // We will scan all files in src/main/java and src/main/res (excluding values/strings.xml itself)
        
        val usedStrings = mutableSetOf<String>()
        
        // Scan Java/Kotlin files
        javaDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val content = file.readText()
            definedStrings.forEach { key ->
                if (content.contains("R.string.$key") || content.contains("R.array.$key")) {
                    usedStrings.add(key)
                }
            }
        }

        // Scan Res files (layout, menu, etc.) but skip the definition files themselves
        resDir.walkTopDown().filter { it.isFile && !it.name.equals("strings.xml") }.forEach { file ->
            val content = file.readText()
            definedStrings.forEach { key ->
                if (content.contains("@string/$key") || content.contains("@array/$key")) {
                    usedStrings.add(key)
                }
            }
        }
        
        // Scan Manifest
        if (manifestFile.exists()) {
             val content = manifestFile.readText()
             definedStrings.forEach { key ->
                if (content.contains("@string/$key")) {
                    usedStrings.add(key)
                }
            }
        }
        
        val unused = definedStrings - usedStrings
        
        assertTrue(
            "Unused strings found: $unused",
            unused.isEmpty()
        )
    }
}
