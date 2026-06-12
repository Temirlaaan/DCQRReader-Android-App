package kz.tcloud.dcinv

import kz.tcloud.dcinv.domain.QrFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrFormatTest {

    @Test
    fun `valid code passes`() {
        assertTrue(QrFormat.isValid("DCQR-ABCD2345"))
    }

    @Test
    fun `wrong prefix fails`() {
        assertFalse(QrFormat.isValid("XXQR-ABCD2345"))
    }

    @Test
    fun `excluded letters I O and digits 0 1 are rejected`() {
        assertFalse(QrFormat.isValid("DCQR-IO012345"))
    }

    @Test
    fun `wrong length fails`() {
        assertFalse(QrFormat.isValid("DCQR-ABC"))
        assertFalse(QrFormat.isValid("DCQR-ABCD23456"))
    }

    @Test
    fun `lowercase fails strict match`() {
        assertFalse(QrFormat.isValid("DCQR-abcd2345"))
    }

    @Test
    fun `prefix check is lenient`() {
        assertTrue(QrFormat.hasPrefix("DCQR-anything"))
        assertFalse(QrFormat.hasPrefix("nope"))
    }
}
