package com.young.developtools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random

class IdCardParserTest {

    private val validId = "11010519491231002X"

    /** ISO 7064 MOD 11-2, written independently of the production lookup table. */
    private fun iso7064CheckChar(prefix17: String): Char {
        val weights = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
        var sum = 0
        prefix17.forEachIndexed { index, char -> sum += (char - '0') * weights[index] }
        val check = (12 - sum % 11) % 11
        return if (check == 10) 'X' else check.digitToChar()
    }

    private fun frontLines(
        idLine: String = "公民身份号码 $validId",
        extra: List<OcrLine> = emptyList(),
    ): List<OcrLine> = listOf(
        OcrLine("姓名 张三", top = 0, bottom = 20, right = 600),
        OcrLine("性别 女 民族 汉", top = 30, bottom = 50, right = 600),
        OcrLine("出生 1949年12月31日", top = 60, bottom = 80, right = 600),
        OcrLine("住址 北京市朝阳区某某路1号", top = 90, bottom = 110, right = 600),
        OcrLine(idLine, top = 120, bottom = 140, right = 600),
    ) + extra

    @Test
    fun `checksum accepts a known good number`() {
        assertTrue(isIdNumberValid(validId))
        assertFalse(isIdNumberValid("110105194912310021"))
        assertFalse(isIdNumberValid("11010519491231"))
        assertFalse(isIdNumberValid(""))
    }

    @Test
    fun `checksum matches ISO 7064 across generated prefixes`() {
        val random = Random(20261116L)
        repeat(5_000) {
            val area = 110000 + random.nextInt(90_000)
            val year = 1950 + random.nextInt(61)
            val month = 1 + random.nextInt(12)
            val day = 1 + random.nextInt(28)
            val sequence = random.nextInt(1_000)
            val prefix = String.format(
                Locale.US, "%06d%04d%02d%02d%03d", area, year, month, day, sequence
            )
            val id = prefix + iso7064CheckChar(prefix)
            assertTrue("$id should pass", isIdNumberValid(id))

            val wrongCheck = if (iso7064CheckChar(prefix) == '0') '1' else '0'
            assertFalse("$prefix$wrongCheck should fail", isIdNumberValid("$prefix$wrongCheck"))
        }
    }

    @Test
    fun `checksum-valid number with an impossible birth date is rejected`() {
        val badMonthPrefix = "11010519901301002"
        assertFalse(isIdNumberValid(badMonthPrefix + iso7064CheckChar(badMonthPrefix)))

        val badDayPrefix = "11010519900100002"
        assertFalse(isIdNumberValid(badDayPrefix + iso7064CheckChar(badDayPrefix)))

        val badYearPrefix = "11010518991231002"
        assertFalse(isIdNumberValid(badYearPrefix + iso7064CheckChar(badYearPrefix)))

        // 1990 is not a leap year, so 02-31 must fail even with a correct checksum.
        val badDayInMonthPrefix = "11010519900231002"
        assertFalse(isIdNumberValid(badDayInMonthPrefix + iso7064CheckChar(badDayInMonthPrefix)))

        // 2000 is a leap year; 02-29 is a real date and must pass.
        val leapDayPrefix = "11010520000229002"
        assertTrue(isIdNumberValid(leapDayPrefix + iso7064CheckChar(leapDayPrefix)))
    }

    @Test
    fun `checksum rejects malformed input`() {
        assertFalse(isIdNumberValid("11010519491231002Y"))
        assertFalse(isIdNumberValid("1101051949123100AX"))
        assertFalse(isIdNumberValid("11010519491231002X0"))
        assertTrue(isIdNumberValid("11010519491231002x"))
    }

    @Test
    fun `birth date comes from the number`() {
        assertEquals("1949-12-31", birthDateFromIdNumber(validId))
        assertNull(birthDateFromIdNumber("123"))
        assertEquals("女", genderFromIdNumber(validId))
    }

    @Test
    fun `front side parses every field`() {
        val front = parseIdCardPortrait(frontLines())
        assertEquals(validId, front?.idNumber)
        assertEquals("张三", front?.name)
        assertEquals("女", front?.gender)
        assertEquals("汉", front?.ethnicity)
        assertEquals("1949-12-31", front?.birth)
        assertEquals("北京市朝阳区某某路1号", front?.address)
    }

    @Test
    fun `letter confusions inside the number are corrected`() {
        val front = parseIdCardPortrait(frontLines(idLine = "公民身份号码 11O1O519491231OO2X"))
        assertEquals(validId, front?.idNumber)
    }

    @Test
    fun `invalid checksum yields no front result`() {
        assertNull(parseIdCardPortrait(frontLines(idLine = "公民身份号码 110105194912310021")))
    }

    @Test
    fun `portrait column text never leaks into the address`() {
        val portrait = OcrLine("张三", top = 95, bottom = 115, left = 820, right = 1000)
        val front = parseIdCardPortrait(frontLines(extra = listOf(portrait)))
        assertEquals("北京市朝阳区某某路1号", front?.address)
    }

    @Test
    fun `address stops at the id number label`() {
        val lines = frontLines().toMutableList()
        lines.add(lines.size - 1, OcrLine("朝阳区", top = 112, bottom = 130, right = 600))
        val front = parseIdCardPortrait(lines)
        assertEquals("北京市朝阳区某某路1号朝阳区", front?.address)
    }

    @Test
    fun `back side parses issuer and validity`() {
        val back = parseIdCardEmblem(
            listOf(
                OcrLine("签发机关 北京市公安局朝阳分局", top = 0, bottom = 20),
                OcrLine("有效期限 2013.05.01-2033.05.01", top = 30, bottom = 50),
            )
        )
        assertEquals("北京市公安局朝阳分局", back?.issuer)
        assertEquals("2013.05.01", back?.validFrom)
        assertEquals("2033.05.01", back?.validTo)
    }

    @Test
    fun `validity tolerates a misread dash and long-term cards`() {
        val dash = parseIdCardEmblem(
            listOf(
                OcrLine("签发机关 上海市公安局", top = 0, bottom = 20),
                OcrLine("有效期限 2015.06.01一2025.06.01", top = 30, bottom = 50),
            )
        )
        assertEquals("2015.06.01", dash?.validFrom)
        assertEquals("2025.06.01", dash?.validTo)

        val longTerm = parseIdCardEmblem(
            listOf(
                OcrLine("签发机关 上海市公安局", top = 0, bottom = 20),
                OcrLine("有效期限 2015.06.01-长期", top = 30, bottom = 50),
            )
        )
        assertEquals("长期", longTerm?.validTo)
    }

    @Test
    fun `back side without readable fields yields null`() {
        assertNull(parseIdCardEmblem(listOf(OcrLine("随便一些文字"))))
    }
}
