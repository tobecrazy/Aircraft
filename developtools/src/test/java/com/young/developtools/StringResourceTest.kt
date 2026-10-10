package com.young.developtools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

/**
 * Module counterpart of the app's StringResourceTest, scoped to
 * developtools/src/main. Guards the 4-locale parity and fails on strings that
 * lost their last reference (shrinkResources relies on real references).
 */
class StringResourceTest {

    @Test
    fun testAllStringsInDefaultStringsXmlAreTranslatedInAllLocaleDirs() {
        val moduleDir = findModuleRoot()
        val resDir = File(moduleDir, "src/main/res")

        val defaultStrings = loadStringsFromXml(File(resDir, "values/strings.xml"))

        resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values") }!!
            .filter { it.name != "values" }
            .forEach { localeDir ->
                val stringsFile = File(localeDir, "strings.xml")
                if (stringsFile.exists()) {
                    val localeStrings = loadStringsFromXml(stringsFile)
                    val missing = defaultStrings.keys - localeStrings.keys
                    assertTrue(
                        "Strings missing in ${localeDir.name}/strings.xml: $missing",
                        missing.isEmpty()
                    )
                }
            }
    }

    @Test
    fun testNoUnusedStringsInDefaultStringsXml() {
        val moduleDir = findModuleRoot()
        val resDir = File(moduleDir, "src/main/res")
        val javaDir = File(moduleDir, "src/main/java")
        val valuesDir = File(resDir, "values")

        val definedStrings = loadStringsFromXml(File(valuesDir, "strings.xml")).keys
        val usedStrings = mutableSetOf<String>()

        val manifest = File(moduleDir, "src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val content = manifest.readText()
            definedStrings.forEach { key ->
                if (content.contains("@string/$key")) {
                    usedStrings.add(key)
                }
            }
        }

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

        val unused = definedStrings - usedStrings

        assertTrue(
            "Unused strings found: $unused",
            unused.isEmpty()
        )
    }

    private fun findModuleRoot(): File {
        var current: File? = File(".").absoluteFile
        while (current != null && !File(current, "consumer-rules.pro").exists()) {
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
