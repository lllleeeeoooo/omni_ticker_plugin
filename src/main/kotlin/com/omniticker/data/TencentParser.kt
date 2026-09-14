package com.omniticker.data

import com.omniticker.model.Market
import com.omniticker.model.Quote
import com.omniticker.model.QuoteLevel
import com.omniticker.model.SearchHit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure parser for the Tencent quote feed (qt.gtimg.cn / web.sqt.gtimg.cn).
 *
 * The response is GBK bytes decoded to text (done by the caller), lines like:
 *   v_sh600519="1~贵州茅台~600519~1275.16~1285.13~1278.00~...";
 * Fields are separated by '~' (index from 0); the layout is stable for years:
 *
 *   1    name (may carry spaces — trim)
 *   2    code
 *   3/4/5   price / prevClose / open
 *   9..18    bid1..bid5 (price, volume alternating)
 *   19..28   ask1..ask5 (price, volume alternating)
 *   30   timestamp yyyyMMddHHmmss
 *   31/32    change / change%  (no symbols: drop leading '+' if present)
 *   33/34    high / low
 *   36/37    volume(手) / amount(万元)
 *   38/39    turnover% / PE
 *   43   amplitude%
 *   44/45    float cap / total cap (亿)
 *   46   PB
 *   47/48    limit up / limit down
 *   49   volume ratio
 */
object TencentParser {
    private val TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(text: String): List<Quote>? =
        runCatching {
            val lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("v_") && it.contains("=") }
                .toList()
            if (lines.isEmpty()) return null
            val quotes = lines.mapNotNull { line -> parseLine(line) }
            if (quotes.isEmpty()) return null
            quotes
        }.getOrNull()

    internal fun parseLine(line: String): Quote? {
        val eq = line.indexOf('=')
        if (eq < 0) return null
        val payload = line.substring(eq + 1)
            .trim()
            .removeSuffix(";")
            .trim()
            .removeSurrounding("\"")
            .trim()
        val f = payload.split('~')
        if (f.size < 31) return null
        val code = f[2].takeIf { it.length == 6 } ?: return null
        val name = f[1].trim()
        val price = f[3].toDoubleOrNull() ?: return null
        val prevClose = f[4].toDoubleOrNull() ?: price
        val change = f[31].clean()?.toDoubleOrNull() ?: (price - prevClose)
        val changePercent = f[32].clean()?.toDoubleOrNull()
            ?: (if (prevClose != 0.0) change / prevClose * 100.0 else 0.0)
        val levels = buildList {
            // bids: 9..18 -> even indices = price, odd = volume
            val bidCount = 5
            for (i in 0 until bidCount) {
                val p = f.getOrNull(9 + 2 * i)?.toDoubleOrNull()
                val v = f.getOrNull(10 + 2 * i)?.toLongOrNull()
                if (p != null) add(QuoteLevel(p, v ?: 0))
            }
            for (i in 0 until bidCount) {
                val p = f.getOrNull(19 + 2 * i)?.toDoubleOrNull()
                val v = f.getOrNull(20 + 2 * i)?.toLongOrNull()
                if (p != null) add(QuoteLevel(p, v ?: 0))
            }
        }
        return Quote(
            code = code,
            name = name,
            market = Market.derive(code),
            price = price,
            change = change,
            changePercent = changePercent,
            open = f[5].toDoubleOrNull() ?: 0.0,
            high = f[33].toDoubleOrNull() ?: price,
            low = f[34].toDoubleOrNull() ?: price,
            prevClose = prevClose,
            volume = f[36].toLongOrNull() ?: 0,
            amount = (f[37].toDoubleOrNull() ?: 0.0) * 10_000.0, // 万元 -> 元
            turnoverRate = f[38].clean()?.toDoubleOrNull(),
            amplitude = f[43].clean()?.toDoubleOrNull(),
            bidAsk = levels.takeIf { it.isNotEmpty() },
            pe = f[39].clean()?.toDoubleOrNull(),
            pb = f[46].clean()?.toDoubleOrNull(),
            totalMarketCap = f[45].clean()?.toDoubleOrNull()?.times(1e8),
            limitUp = f[47].clean()?.toDoubleOrNull(),
            limitDown = f[48].clean()?.toDoubleOrNull(),
            timestamp = parseTimestamp(f[30]),
            source = TencentProvider.ID,
        )
    }

    /**
     * Search suggest response, live-verified format (GBK-decoded by caller):
     *   v_hint="sh~600519~贵州茅台~gzmt~GP-A";
     * one line per hit, '~'-separated, names may arrive as \uXXXX escapes.
     */
    fun parseSearch(text: String, max: Int = 10): List<SearchHit>? =
        runCatching {
            val hits = mutableListOf<SearchHit>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (!line.startsWith("v_hint=")) continue
                val payload = line.substringAfter('=')
                    .trim()
                    .removeSurrounding("\"")
                    .removeSuffix(";")
                    .trim()
                val parts = unescapeUnicode(payload).split('~')
                if (parts.size < 4) continue
                val code = parts[1].takeIf { it.length == 6 } ?: continue
                val name = parts[2].trim()
                val pinyin = parts.getOrNull(3) ?: ""
                val type = parts.getOrNull(4) ?: ""
                hits += SearchHit(code, name, Market.derive(code), pinyin, type = type)
                if (hits.size >= max) break
            }
            hits.takeIf { it.isNotEmpty() }
        }.getOrNull()

    /** Resolve \uXXXX escapes embedded in the smartbox payload. */
    private fun unescapeUnicode(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 5 < s.length && s[i + 1] == 'u') {
                val hex = s.substring(i + 2, i + 6)
                sb.append(hex.toIntOrNull(16)?.toChar() ?: ch)
                i += 6
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseTimestamp(s: String): Long =
        runCatching {
            LocalDateTime.parse(s, TS_FMT).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

    /** Tencent emits things like "--", "0.00", "+1.23". Strip '+' and treat junk as null. */
    private fun String?.clean(): String? {
        if (this == null) return null
        val t = trim().removePrefix("+")
        if (t.isBlank() || t == "--" || t == "-") return null
        return t
    }
}