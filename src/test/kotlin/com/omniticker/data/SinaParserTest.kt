package com.omniticker.data

import com.omniticker.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinaParserTest {

    /** Live-captured 2026-09-11 response (names UTF-8; k,30/31 = date,time). */
    private val stockLine =
        "var hq_str_sh600519=\"贵州茅台,1285.150,1285.130,1275.160,1286.150,1263.010," +
            "1275.160,1276.000,3480142,4430841445.000,945," +
            "1275.160,100,1275.130,100,1275.120,400,1275.100,100,1275.050,1400," +
            "1276.000,100,1276.130,200,1276.150,100,1276.230,100,1276.460," +
            "2026-09-11,15:34:59,00,D|1500|1912740.00\";"

    private val indexLine =
        "var hq_str_sh000001=\"上证指数,3910.9234,3934.4036,3888.1106,3912.3247,3852.0322," +
            "0,0,579123145,958186336970," +
            "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0," +
            "2026-09-11,15:43:32,00\";"

    @Test
    fun parsesStockQuote() {
        val quotes = SinaParser.parse(stockLine + "\n")
        assertNotNull(quotes)
        assertEquals(1, quotes!!.size)
        val q = quotes[0]

        assertEquals("600519", q.code)
        assertEquals("贵州茅台", q.name)
        assertEquals(Market.SH, q.market)
        assertEquals(1275.160, q.price, 1e-9)
        assertEquals(1285.130, q.prevClose, 1e-9)
        assertEquals(-9.970, q.change, 1e-9)
        // Sina has no direct change% field; we derive it: -9.97/1285.13 ≈ -0.7758
        assertEquals(-0.7758, q.changePercent, 1e-3)
        assertEquals(1286.150, q.high, 1e-9)
        assertEquals(1263.010, q.low, 1e-9)
        assertEquals(1285.150, q.open, 1e-9)
        assertEquals(3480142L / 100, q.volume)   // 股 -> 手
        assertEquals(4430841445.0, q.amount, 1e-9)
        assertEquals("sina", q.source)

        val levels = q.bidAsk
        assertEquals(10, levels!!.size)
        assertEquals(1275.160, levels[0].price, 1e-9)   // bid1
        assertEquals(100L, levels[0].volume)
        assertEquals(1275.050, levels[4].price, 1e-9)   // bid5
        assertEquals(1276.000, levels[5].price, 1e-9)   // ask1
        assertEquals(1276.460, levels[9].price, 1e-9)   // ask5 (no volume field in this format)
    }

    @Test
    fun parsesIndexQuote() {
        val quotes = SinaParser.parse(indexLine)
        assertNotNull(quotes)
        val q = quotes!!.first()
        assertEquals("000001", q.code)
        assertEquals(Market.SH, q.market)
        assertEquals(3888.1106, q.price, 1e-9)
        assertEquals(3934.4036, q.prevClose, 1e-9)
        assertEquals(-46.29299999999967, q.change, 1e-6)
        // index level rows are all zero -> no order book
        assertTrue(q.bidAsk.isNullOrEmpty())
    }

    @Test
    fun rejectsJunk() {
        assertNull(SinaParser.parse(""))
        assertNull(SinaParser.parse("not-a-line"))
        assertNull(SinaParser.parse("var hq_str_sh600519=\"\";"))
    }
}