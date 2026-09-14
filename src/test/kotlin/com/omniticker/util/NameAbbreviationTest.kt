package com.omniticker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameAbbreviationTest {

    @Test
    fun abbreviatesStockNamesUppercaseNoSpaces() {
        assertEquals("GZMT", NameAbbreviation.abbrev("贵州茅台"))
        assertEquals("WLY", NameAbbreviation.abbrev("五粮液"))
        assertEquals("PA", NameAbbreviation.abbrev("平安"))
        assertEquals("NDSD", NameAbbreviation.abbrev("宁德时代"))
        // 多音字：GB2312 区位表将"行"归入 xíng -> X（银行读 háng，列车读 xíng）
        assertEquals("ZSYX", NameAbbreviation.abbrev("招商银行"))
    }

    @Test
    fun uppercasesAsciiAndKeepsNumbers() {
        assertEquals("AB12C", NameAbbreviation.abbrev("ab12c"))
        assertEquals("ABC", NameAbbreviation.abbrev("aBc"))
        assertEquals("", NameAbbreviation.abbrev(""))
    }

    @Test
    fun mixesHanAndAscii() {
        assertEquals("SH600519", NameAbbreviation.abbrev("sh600519"))
        assertEquals("LYDS", NameAbbreviation.abbrev("联一大盛"))
    }
}