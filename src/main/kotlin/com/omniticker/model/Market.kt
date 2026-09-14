package com.omniticker.model

/**
 * A-share exchange for a 6-digit code.
 *
 * Derived from the code prefix (so storage only needs the plain 6-digit code):
 * - SH: 600/601/603/605/688 (STAR)/689
 * - SZ: 000/001/002/003/300/301 (ChiNext)
 * - BJ: 4xx/8xx/92x (Beijing Stock Exchange)
 */
enum class Market(val prefix: String) {
    SH("sh"),
    SZ("sz"),
    BJ("bj");

    companion object {
        /** Derive the exchange from a 6-digit code. Defaults to SH for unknown prefixes. */
        fun derive(code: String): Market {
            val c = code.take(3)
            return when {
                c in SH_PREFIXES -> SH
                c in SZ_PREFIXES -> SZ
                c in BJ_PREFIXES -> BJ
                else -> SH
            }
        }

        private val SH_PREFIXES = setOf("600", "601", "603", "605", "688", "689")
        private val SZ_PREFIXES = setOf("000", "001", "002", "003", "300", "301")
        private val BJ_PREFIXES = setOf("400", "420", "430", "830", "831", "832", "833", "834", "835",
            "836", "837", "838", "839", "870", "871", "872", "873", "920")
    }
}

/** Tencent-style market prefix, e.g. "sh600519". */
fun Market.tencentCode(code: String): String = prefix + code