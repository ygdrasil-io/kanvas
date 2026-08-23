package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.toColorSpaceOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IccProfileTest {
    @Test
    fun `parses and retains an immutable ICC byte snapshot`() {
        val source = IccProfileWriter.writeMatrixTrc(ColorProfiles.displayP3())
        val profile = IccProfile.parse(source).getOrThrow()
        val expected = profile.bytes

        source[0] = 0
        val returned = profile.bytes
        returned[1] = 0

        assertEquals(ColorSpace.DISPLAY_P3, profile.colorProfile.toColorSpaceOrNull())
        assertContentEquals(expected, profile.bytes)
        assertEquals(expected.size, profile.size)
        assertTrue(profile.tagCount > 0)
        assertTrue(profile.hasTrc)
        assertTrue(profile.hasToXyzD50)
    }

    @Test
    fun `reports parser failures without a partial ICC profile`() {
        val result = IccProfile.parse(ByteArray(0))
        val failure = assertNotNull(result.failureOrNull())

        assertEquals("icc.header.size", failure.code)
        assertNull(result.profileOrNull())
    }

    @Test
    fun `serializes a supported Matrix TRC profile without a Skia facade`() {
        val profile = IccProfile.fromMatrixTrc(ColorProfiles.displayP3())

        assertEquals(ColorSpace.DISPLAY_P3, profile.colorProfile.toColorSpaceOrNull())
        assertTrue(profile.bytes.isNotEmpty())
    }
}
