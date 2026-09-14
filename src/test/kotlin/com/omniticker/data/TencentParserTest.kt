package com.omniticker.data

import com.omniticker.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentParserTest {

    /**
     * Verified field layout (0-based, '~' separated). GBK decoding happens in
     * the caller; here the text is already decoded to a Kotlin String.
     */
    private fun line(): String {
        val fields = mutableListOf<String>()
        fun set(index: Int, value: String) {
            while (fields.size <= index) fields.add("")
            fields[index] = value
        }
        set(1, "贵州茅台")
        set(2, "600519")
        set(3, "1702.50")     // price
        set(4, "1679.80")     // prevClose
        set(5, "1685.00")     // open
        set(9, "1702.50"); set(10, "300")   // bid1
        set(11, "1702.49"); set(12, "200")  // bid2
        set(13, "1702.48"); set(14, "100")
        set(15, "1702.47"); set(16, "50")
        set(17, "1702.46"); set(18, "30")
        set(19, "1702.51"); set(20, "400")  // ask1
        set(21, "1702.52"); set(22, "300")
        set(23, "1702.53"); set(24, "200")
        set(25, "1702.54"); set(26, "100")
        set(27, "1702.55"); set(28, "50")
        set(30, "20260912150000")
        set(31, "22.70")      // change
        set(32, "1.35")       // change%
        set(33, "1710.00")    // high
        set(34, "1688.00")    // low
        set(36, "26300")      // volume 手
        set(37, "445084")     // amount 万元
        set(38, "0.28")       // turnover
        set(39, "26.50")      // PE
        set(43, "1.31")       // amplitude
        set(44, "15940.54")   // float cap 亿
        set(45, "2139000.00") // total cap 亿
        set(46, "6.34")       // PB
        set(47, "1847.78")    // limit up
        set(48, "1511.82")    // limit down
        set(49, "1.12")       // volume ratio
        return "v_sh600519=\"" + fields.joinToString("~") + "\";"
    }

    @Test
    fun parsesFullQuote() {
        val quotes = TencentParser.parse(line() + "\n")
        assertTrue(quotes != null && quotes.size == 1)
        val q = quotes!!.first()

        assertEquals("600519", q.code)
        assertEquals("贵州茅台", q.name)
        assertEquals(Market.SH, q.market)
        assertEquals(1702.50, q.price, 1e-9)
        assertEquals(1679.80, q.prevClose, 1e-9)
        assertEquals(1685.00, q.open, 1e-9)
        assertEquals(22.70, q.change, 1e-9)
        assertEquals(1.35, q.changePercent, 1e-9)
        assertEquals(1710.00, q.high, 1e-9)
        assertEquals(1688.00, q.low, 1e-9)
        assertEquals(26300L, q.volume)
        assertEquals(445084.0 * 10_000.0, q.amount, 1e-9)  // 万元 -> 元
        assertEquals(0.28, q.turnoverRate!!, 1e-9)
        assertEquals(1.31, q.amplitude!!, 1e-9)
        assertEquals(26.50, q.pe!!, 1e-9)
        assertEquals(6.34, q.pb!!, 1e-9)
        assertEquals(2139000.00 * 1e8, q.totalMarketCap!!, 1e-9)
        assertEquals(1847.78, q.limitUp!!, 1e-9)
        assertEquals(1511.82, q.limitDown!!, 1e-9)
        assertEquals("tencent", q.source)

        val ba = q.bidAsk
        assertEquals(10, ba!!.size)
        assertEquals(1702.50, ba[0].price, 1e-9)
        assertEquals(300L, ba[0].volume)
        assertEquals(1702.46, ba[4].price, 1e-9)
        assertEquals(1702.51, ba[5].price, 1e-9)
        assertEquals(1702.55, ba[9].price, 1e-9)
    }

    @Test
    fun handlesJunkAndEmpty() {
        assertNull(TencentParser.parse(""))
        assertNull(TencentParser.parse("not-a-valid-line"))
        // Referer failure bodies arrive as empty payloads, not garbage
        assertNull(TencentParser.parse("v_sh000001=\"\";"))
    }

    @Test
    fun parsesSearchHits() {
        // Live-verified smartbox payload: v_hint lines, '~' separated, \uXXXX names
        val body = "v_hint=\"sh~600519~\\u8d35\\u5dde\\u8305\\u53f0~gzmt~GP-A\";\n" +
            "v_hint=\"sz~000858~\\u4e94\\u7cae\\u6db2~wly~GP-A\";\n"
        val hits = TencentParser.parseSearch(body, 5)
        assertEquals(2, hits!!.size)
        val mt = hits[0]
        assertEquals("600519", mt.code)
        assertEquals("贵州茅台", mt.name)
        assertEquals("gzmt", mt.pinyin)
        assertEquals("wly", hits[1].pinyin)
        assertEquals(Market.SZ, hits[1].market)
    }

    @Test
    fun ignoresEmptySearch() {
        assertNull(TencentParser.parseSearch(""))
        assertNull(TencentParser.parseSearch("v_hint=\"\";"))
    }
}