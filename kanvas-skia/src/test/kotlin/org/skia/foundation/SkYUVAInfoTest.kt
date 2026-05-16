package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedOrigin
import org.graphiks.math.SkISize

/**
 * R3.11 verification suite for [SkYUVAInfo].
 *
 * Covers `numPlanes` / `planeDimensions` for a representative cross of
 * [SkYUVAInfo.PlaneConfig] × [SkYUVAInfo.Subsampling]. Includes the JPEG-
 * style 4:2:0 chroma layout (the most common YUV format on Android).
 */
class SkYUVAInfoTest {

    // ─── NumPlanes per PlaneConfig ────────────────────────────────────

    @Test
    fun `NumPlanes matches upstream constants`() {
        assertEquals(0, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kUnknown))
        assertEquals(3, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_U_V))
        assertEquals(3, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_V_U))
        assertEquals(2, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_UV))
        assertEquals(2, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_VU))
        assertEquals(1, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kYUV))
        assertEquals(1, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kUYV))
        assertEquals(4, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_U_V_A))
        assertEquals(4, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_V_U_A))
        assertEquals(3, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_UV_A))
        assertEquals(3, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kY_VU_A))
        assertEquals(1, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kYUVA))
        assertEquals(1, SkYUVAInfo.NumPlanes(SkYUVAInfo.PlaneConfig.kUYVA))
    }

    // ─── Plane dimensions ─────────────────────────────────────────────

    @Test
    fun `planeDimensions Y_U_V at k420 gives Y at full, U-V at quarter resolution`() {
        // The JPEG-style "4:2:0" subsampling : the most common YUV
        // layout. Y is full-res, U/V are half-res on each axis.
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(16, 16),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        assertEquals(3, info.numPlanes())
        assertEquals(SkISize.Make(16, 16), info.planeDimensions(0))
        assertEquals(SkISize.Make(8, 8), info.planeDimensions(1))
        assertEquals(SkISize.Make(8, 8), info.planeDimensions(2))
    }

    @Test
    fun `planeDimensions Y_UV at k420 puts Y full, UV at quarter resolution`() {
        // NV12-style : Y on plane 0, UV interleaved on plane 1.
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(20, 20),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        assertEquals(2, info.numPlanes())
        assertEquals(SkISize.Make(20, 20), info.planeDimensions(0))
        assertEquals(SkISize.Make(10, 10), info.planeDimensions(1))
    }

    @Test
    fun `planeDimensions Y_U_V at k444 keeps every plane at full resolution`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(7, 5),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        assertEquals(SkISize.Make(7, 5), info.planeDimensions(0))
        assertEquals(SkISize.Make(7, 5), info.planeDimensions(1))
        assertEquals(SkISize.Make(7, 5), info.planeDimensions(2))
    }

    @Test
    fun `planeDimensions Y_U_V_A at k422 keeps Y-A full and shrinks U-V horizontally`() {
        // 4:2:2 → U/V halved on x only.
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 6),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V_A,
            subsampling = SkYUVAInfo.Subsampling.k422,
        )
        assertEquals(4, info.numPlanes())
        assertEquals(SkISize.Make(8, 6), info.planeDimensions(0)) // Y
        assertEquals(SkISize.Make(4, 6), info.planeDimensions(1)) // U
        assertEquals(SkISize.Make(4, 6), info.planeDimensions(2)) // V
        assertEquals(SkISize.Make(8, 6), info.planeDimensions(3)) // A
    }

    @Test
    fun `planeDimensions rounds up odd dimensions`() {
        // Odd-width source : the U/V plane width is the ceil of W/2.
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(11, 9),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        assertEquals(SkISize.Make(11, 9), info.planeDimensions(0))
        assertEquals(SkISize.Make(6, 5), info.planeDimensions(1))
        assertEquals(SkISize.Make(6, 5), info.planeDimensions(2))
    }

    @Test
    fun `planeDimensions YUVA interleaved is single full-resolution plane`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(10, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kYUVA,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        assertEquals(1, info.numPlanes())
        assertEquals(SkISize.Make(10, 8), info.planeDimensions(0))
    }

    @Test
    fun `planeDimensions respects origin rotation`() {
        // A 90° rotation swaps stored width and height.
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(20, 10),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            origin = SkEncodedOrigin.kRightTop, // rotates 90° CW
        )
        assertEquals(SkISize.Make(10, 20), info.planeDimensions(0))
    }

    // ─── hasAlpha + isValid ───────────────────────────────────────────

    @Test
    fun `hasAlpha is true only for the _A configs`() {
        assertFalse(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kY_U_V))
        assertFalse(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kY_UV))
        assertFalse(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kYUV))
        assertTrue(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kY_U_V_A))
        assertTrue(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kY_UV_A))
        assertTrue(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kYUVA))
        assertTrue(SkYUVAInfo.HasAlpha(SkYUVAInfo.PlaneConfig.kUYVA))
    }

    @Test
    fun `isValid is false for kUnknown plane config`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(4, 4),
            planeConfig = SkYUVAInfo.PlaneConfig.kUnknown,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        assertFalse(info.isValid())
    }

    // ─── numChannelsInPlane ───────────────────────────────────────────

    @Test
    fun `numChannelsInPlane returns the upstream values`() {
        assertEquals(1, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kY_U_V, 0))
        assertEquals(1, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kY_U_V, 2))
        assertEquals(0, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kY_U_V, 3))

        assertEquals(1, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kY_UV, 0))
        assertEquals(2, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kY_UV, 1))

        assertEquals(3, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kYUV, 0))
        assertEquals(4, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kYUVA, 0))
        assertEquals(0, SkYUVAInfo.NumChannelsInPlane(SkYUVAInfo.PlaneConfig.kYUVA, 1))
    }

    // ─── Subsampling factors ──────────────────────────────────────────

    @Test
    fun `SubsamplingFactors mirrors the J colon a colon b notation`() {
        assertEquals(1 to 1, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k444))
        assertEquals(2 to 1, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k422))
        assertEquals(2 to 2, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k420))
        assertEquals(1 to 2, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k440))
        assertEquals(4 to 1, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k411))
        assertEquals(4 to 2, SkYUVAInfo.SubsamplingFactors(SkYUVAInfo.Subsampling.k410))
    }

    @Test
    fun `PlaneSubsamplingFactors marks interleaved YUV with non-444 as invalid`() {
        // Y is in the same plane as UV : non-444 isn't a valid combination.
        assertEquals(
            0 to 0,
            SkYUVAInfo.PlaneSubsamplingFactors(
                SkYUVAInfo.PlaneConfig.kYUV,
                SkYUVAInfo.Subsampling.k420,
                0,
            ),
        )
        // The same configuration with k444 is fine — factor is (1, 1).
        assertEquals(
            1 to 1,
            SkYUVAInfo.PlaneSubsamplingFactors(
                SkYUVAInfo.PlaneConfig.kYUV,
                SkYUVAInfo.Subsampling.k444,
                0,
            ),
        )
    }

    // ─── computeTotalBytes ────────────────────────────────────────────

    @Test
    fun `computeTotalBytes sums planeHeight × rowBytes per plane`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(16, 16),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        // Y = 16x16 @ 16 bytes/row → 256
        // U = 8x8 @ 8 bytes/row → 64
        // V = 8x8 @ 8 bytes/row → 64
        val rowBytes = intArrayOf(16, 8, 8, 0)
        assertEquals(256L + 64 + 64, info.computeTotalBytes(rowBytes))
    }

    // ─── PlaneDimensions companion ────────────────────────────────────

    @Test
    fun `PlaneDimensions companion fills the out array`() {
        val out = Array(SkYUVAInfo.kMaxPlanes) { SkISize.Make(0, 0) }
        val n = SkYUVAInfo.PlaneDimensions(
            SkISize.Make(16, 16),
            SkYUVAInfo.PlaneConfig.kY_UV,
            SkYUVAInfo.Subsampling.k420,
            SkEncodedOrigin.kTopLeft,
            out,
        )
        assertEquals(2, n)
        assertEquals(SkISize.Make(16, 16), out[0])
        assertEquals(SkISize.Make(8, 8), out[1])
    }
}
