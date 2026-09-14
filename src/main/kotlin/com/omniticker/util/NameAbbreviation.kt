package com.omniticker.util

import java.nio.charset.Charset

/**
 * First-pinyin-letter abbreviation (UPPERCASE, no separators):
 * "贵州茅台" -> "GZMT", "招商银行" -> "ZSYX".
 * Uses the classic GB2312/GBK zone-mapping table (no dictionary needed),
 * which covers all first-level simplified Chinese characters.
 */
object NameAbbreviation {

    /** Abbreviate a stock/index name: initial of each Han char; ASCII letters uppercased. */
    fun abbrev(name: String): String {
        if (name.isBlank()) return name
        val sb = StringBuilder(name.length + 4)
        for (i in 0 until name.length) {
            appendInitial(sb, name[i])
        }
        return sb.toString()
    }

    private fun appendInitial(sb: StringBuilder, ch: Char) {
        val c = ch.toInt()
        if (c >= 'a'.toInt() && c <= 'z'.toInt()) {
            sb.append((c - 32).toChar())  // lowercase ascii -> uppercase
            return
        }
        if (c >= 'A'.toInt() && c <= 'Z'.toInt()) {
            sb.append(ch)
            return
        }
        if (c < 0x80) {
            sb.append(ch) // digits / punctuation / space
            return
        }
        val bb = Charset.forName("GBK").encode(ch.toString())
        val bytes = ByteArray(bb.remaining())
        bb.get(bytes)
        if (bytes.size != 2) {
            sb.append(ch)
            return
        }
        val code = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        // CJK first-level band (B0A1..F7FE)
        if (code < TABLE[0]) {
            sb.append(ch)
            return
        }
        for (i in TABLE.size - 1 downTo 0) {
            if (TABLE[i] <= code) {
                sb.append(LETTERS[i])  // already uppercase
                break
            }
        }
    }

    /** Zone start codes of the first-level band, one per initial letter. */
    private val TABLE = arrayOf(
        0xB0A1, 0xB0C4, 0xB2C7, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE, 0xBBF7, 0xBFA6,
        0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBF9, 0xCDD2,
        0xCEF4, 0xD1B9, 0xD4D1,
    )

    /** Letters for TABLE entries; note 'I', 'U', 'V' are not used in pinyin initials. */
    private val LETTERS = arrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
        'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W',
        'X', 'Y', 'Z',
    )
}