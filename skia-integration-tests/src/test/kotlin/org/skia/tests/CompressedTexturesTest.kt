package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * `compressed_textures` / `compressed_textures_npot` /
 * `compressed_textures_nmof` cross-test. All three variants share the
 * same compressed-payload + raster-fallback plumbing, so they fail at
 * the same `STUB.COMPRESSED_TEXTURES` site (`SkCompressedDataSize`).
 *
 * Body is fully ported against the live
 * [org.skia.foundation.SkImages.RasterFromCompressedTextureData] /
 * [org.skia.foundation.SkCompressedDataUtils] surface — drop the
 * `@Disabled` once the BC1 / ETC2 decode path lands.
 */
class CompressedTexturesTest {

    @Test
    fun `CompressedTexturesGM matches reference`() {
        val gm = CompressedTexturesGM(CompressedTexturesGM.Type.kNormal)
        TestUtils.runGmTest(gm)
    }

    @Disabled("Leave NPOT variant gated until ETC2 path is implemented.")
    @Test
    fun `CompressedTexturesGM npot matches reference`() {
        val gm = CompressedTexturesGM(CompressedTexturesGM.Type.kNonPowerOfTwo)
        TestUtils.runGmTest(gm)
    }

    @Disabled("Leave NMOF variant gated until ETC2 path is implemented.")
    @Test
    fun `CompressedTexturesGM nmof matches reference`() {
        val gm = CompressedTexturesGM(CompressedTexturesGM.Type.kNonMultipleOfFour)
        TestUtils.runGmTest(gm)
    }
}
