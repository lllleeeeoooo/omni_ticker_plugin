package com.omniticker.data

import com.omniticker.model.Market
import com.omniticker.model.Quote
import com.omniticker.model.QuoteLevel

/**
 * Pure parser for the Sina hq.sinajs.cn feed.
 *
 * Live-verified layout (2026-09, UTF-8 response, '~' expert the CSV is ','):
 *   var hq_str_sh600519="贵州茅台,1285.150,1285.130,1275.160,1286.150,1263.010,
 *         1275.160,1276.000,3480142,4430841445.000,945,
 *         1275.160,100, 1275.130,100, 1275.120,400, 1275.100,100, 1275.050,1400,
 *         1276.000,100, 1276.130,200, 1276.150,100, 1276.230,100, 1276.460,
 *         2026-09-11,15:34:59,00,D|...";
 *
 * Index map:
 *   0 name, 1 open, 2 prevClose, 3 price, 4 high, 5 low,
 *   6 bid1, 7 ask1, 8 volume(股), 9 amount(元), 10 misc,
 *   11..20  bid1..bid5 (price, volume), 21..29 ask1..ask5 (price, ask5 volume absent),
 *   30 date, 31 time, 32 misc, 33 tick
 *
 * No turnover/PE/PB/cap/limit fields in this format — those stay null and are
 * supplied by Tencent when it is the active source.
 */
object SinaParser {

    private val CODE_REGEX = Regex("hq_str_([a-z]+)(\\d{6})")

    fun parse(text: String): List<Quote>? =
        runCatching {
            val lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("var hq_str_") && it.contains("=") }
                .toList()
            if (lines.isEmpty()) return null
            val quotes = lines.mapNotNull { parseLine(it) }
            if (quotes.isEmpty()) return null
            quotes
        }.getOrNull()

    internal fun parseLine(line: String): Quote? {
        val payload = line.substringAfter('=')
            .trim()
            .removeSuffix(";")      // strip the trailing ';' BEFORE the quotes pair up
            .trim()
            .removeSurrounding("\"")
            .trim()
        val f = payload.split(',')
        // Stocks sport 34 fields; indexes 33 (same core positions). Require the essential tail.
        if (f.size < 33) return null

        val m = CODE_REGEX.find(line) ?: return null
        val code = m.groupValues[2]
        // Market from the line's own prefix (sh000001 = 上证指数 must be SH,
        // even though derive("000001") would guess SZ).
        val market = when (m.groupValues[1]) {
            "sh" -> Market.SH
            "sz" -> Market.SZ
            "bj" -> Market.BJ
            else -> Market.derive(code)
        }
        val name = f[0].trim()
        val price = f[3].toDoubleOrNull() ?: return null
        val prevClose = f[2].toDoubleOrNull() ?: price
        val change = price - prevClose
        val changePercent = if (prevClose != 0.0) change / prevClose * 100.0 else 0.0

        val levels = buildList {
            for (i in 0 until 5) {
                val p = f.getOrNull(11 + 2 * i)?.toDoubleOrNull()
                val v = f.getOrNull(12 + 2 * i)?.toLongOrNull()
                if (p != null && p > 0) add(QuoteLevel(p, v ?: 0))
            }
            for (i in 0 until 4) {
                val p = f.getOrNull(21 + 2 * i)?.toDoubleOrNull()
                val v = f.getOrNull(22 + 2 * i)?.toLongOrNull()
                if (p != null && p > 0) add(QuoteLevel(p, v ?: 0))
            }
            // ask5 exists on stocks (index 29); its volume field is absent in this format.
            val ask5 = f.getOrNull(29)?.toDoubleOrNull()
            if (ask5 != null && ask5 > 0) add(QuoteLevel(ask5, 0))
        }

        return Quote(
            code = code,
            name = name,
            market = market,
            price = price,
            change = change,
            changePercent = changePercent,
            open = f[1].toDoubleOrNull() ?: 0.0,
            high = f[4].toDoubleOrNull() ?: price,
            low = f[5].toDoubleOrNull() ?: price,
            prevClose = prevClose,
            volume = (f[8].toDoubleOrNull() ?: 0.0).toLong() / 100, // 股 -> 手
            amount = f[9].toDoubleOrNull() ?: 0.0,
            bidAsk = levels.takeIf { it.isNotEmpty() && it[0].price > 0 },
            timestamp = System.currentTimeMillis(),
            source = SinaProvider.ID,
        )
    }
}