package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * R-final.9 — sanity tests for the HDR PQ colorspace tag factory
 * [SkColorSpace.MakePqHdr].
 */
class SkColorSpacePqHdrTest {

    @Test
    fun `MakePqHdr is singleton`() {
        assertSame(SkColorSpace.MakePqHdr(), SkColorSpace.MakePqHdr())
    }

    @Test
    fun `MakePqHdr carries the PQ transfer function sentinel`() {
        val cs = SkColorSpace.MakePqHdr()
        // PQ is encoded with negative g (-5 sentinel) — see
        // SkNamedTransferFn.kPQ. The retag must round-trip the sentinel
        // verbatim.
        assertEquals(SkNamedTransferFn.kPQ.g, cs.transferFn.g)
        assertEquals(SkNamedTransferFn.kPQ.a, cs.transferFn.a)
    }

    @Test
    fun `MakePqHdr differs from sRGB`() {
        assertNotEquals(SkColorSpace.makeSRGB().hash(), SkColorSpace.MakePqHdr().hash())
    }
}
