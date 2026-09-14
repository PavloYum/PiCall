package dev.picall.android.identity

/** Public participant address. Authentication credentials are stored separately. */
@JvmInline
value class PiCallId private constructor(val value: String) {
    companion object {
        private val canonicalPattern = Regex("^PC-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")

        fun parseOrNull(raw: String): PiCallId? {
            val compact = raw.trim().uppercase().replace("-", "")
            if (!compact.startsWith("PC") || compact.length != 10) return null

            val canonical = "PC-${compact.substring(2, 6)}-${compact.substring(6, 10)}"
            return canonical.takeIf(canonicalPattern::matches)?.let(::PiCallId)
        }
    }
}

