package kz.tcloud.dcinv.domain

/**
 * QR identifier format: `DCQR-XXXXXXXX` where the 8-char suffix is drawn from a
 * Crockford-style alphabet (no I, O, 0, 1). See ToR §4.2.1.
 *
 * The prefix check lets the app reject unrelated QR codes without an API call.
 */
object QrFormat {
    const val PREFIX = "DCQR-"

    private val PATTERN = Regex("^DCQR-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$")

    /** Strict match against the full format. */
    fun isValid(value: String): Boolean = PATTERN.matches(value)

    /** Cheap prefix check — enough to decide whether to hit the backend. */
    fun hasPrefix(value: String): Boolean = value.startsWith(PREFIX)
}
