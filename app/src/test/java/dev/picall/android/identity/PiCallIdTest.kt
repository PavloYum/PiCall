package dev.picall.android.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PiCallIdTest {
    @Test
    fun normalizesId() {
        assertEquals("PC-2ABC-9XYZ", PiCallId.parseOrNull("pc2abc9xyz")?.value)
    }

    @Test
    fun rejectsAmbiguousCharacters() {
        assertNull(PiCallId.parseOrNull("PC-1ILO-2ABC"))
    }

    @Test
    fun rejectsWrongLength() {
        assertNull(PiCallId.parseOrNull("PC-ABC-1234"))
    }
}

