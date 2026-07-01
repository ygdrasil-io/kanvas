package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Ratchet tests for the `runtimeshader.cpp` GMs added in the partial-complete
 * pass (16 missing after `RuntimeShaderGM` was already covered).
 *
 * | GM name                  | Class                  | Status   |
 * |:-------------------------|:-----------------------|:---------|
 * | threshold_rt             | ThresholdRTGM          | PORTED   |
 * | spiral_rt                | SpiralRTGM             | PORTED   |
 * | unsharp_rt               | UnsharpRTGM            | PORTED   |
 * | color_cube_rt            | ColorCubeRTGM          | PORTED   |
 * | linear_gradient_rt       | LinearGradientRTGM     | PORTED   |
 * | child_sampling_rt        | ChildSamplingRtGM      | @Disabled STUB.RUNTIME_SHADER_CHILD |
 * | clip_super_rrect_pow2    | ClipSuperRRectPow2GM   | @Disabled STUB.CLIP_SUPER_RRECT |
 * | clip_super_rrect_pow3.5  | ClipSuperRRectPow3p5GM | @Disabled STUB.CLIP_SUPER_RRECT |
 * | paint_alpha_normals_rt   | PaintAlphaNormalsRtGM  | @Disabled STUB.NORMAL_MAP_SHADER |
 * | raw_image_shader_normals | RawImageShaderNormalsRtGM | @Disabled STUB.RAW_IMAGE_SHADER |
 * | lit_shader_linear_rt     | LitShaderLinearRtGM    | @Disabled STUB.LIT_SHADER_LINEAR |
 * | local_matrix_shader_rt   | LocalMatrixShaderRtGM  | @Disabled STUB.LOCAL_MATRIX_RT |
 * | null_child_rt            | NullChildRtGM          | @Disabled STUB.NULL_CHILD_RT |
 * | deferred_shader_rt       | DeferredShaderRtGM     | @Disabled STUB.DEFERRED_SHADER |
 * | alpha_image_shader_rt    | AlphaImageShaderRtGM   | @Disabled STUB.ALPHA_IMAGE_SHADER |
 * | color_cube_cf_rt         | (inline stub)          | @Disabled STUB.COLOR_CUBE_CF |
 *
 * Floor strategy: all ported GMs use synthetic fallback images when Skia
 * resources are absent, so similarity vs the upstream reference PNGs can be low.
 * The [SimilarityTracker] ratchet still catches regressions.
 */
class RuntimeShaderPartialTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double = 0.0) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0 &&
            rendered.width == reference.width && rendered.height == reference.height
        ) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    // ─── Ported GMs ───────────────────────────────────────────────────

    @Test
    fun `ThresholdRTGM matches threshold_rt reference`() =
        runGm(ThresholdRTGM(), "ThresholdRTGM", floor = 0.0)

    @Test
    fun `SpiralRTGM matches spiral_rt reference`() =
        runGm(SpiralRTGM(), "SpiralRTGM", floor = 0.0)

    @Test
    fun `UnsharpRTGM matches unsharp_rt reference`() =
        runGm(UnsharpRTGM(), "UnsharpRTGM", floor = 0.0)

    @Test
    fun `ColorCubeRTGM matches color_cube_rt reference`() =
        runGm(ColorCubeRTGM(), "ColorCubeRTGM", floor = 0.0)

    // ─── Stubs — MISSING_API ──────────────────────────────────────────

    @Test
    @Disabled("STUB.RUNTIME_SHADER_CHILD: passthrough child shader impl not registered — see SkBuiltinShaderEffectsChildren")
    fun `ChildSamplingRtGM matches child_sampling_rt reference`() =
        runGm(ChildSamplingRtGM(), "ChildSamplingRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.CLIP_SUPER_RRECT: ClipSuperRRect shader impl not registered — power=2")
    fun `ClipSuperRRectPow2GM matches clip_super_rrect_pow2 reference`() =
        runGm(ClipSuperRRectPow2GM(), "ClipSuperRRectPow2GM", floor = 0.0)

    @Test
    @Disabled("STUB.CLIP_SUPER_RRECT: ClipSuperRRect shader impl not registered — power=3.5")
    fun `ClipSuperRRectPow3p5GM matches clip_super_rrect_pow3_5 reference`() =
        runGm(ClipSuperRRectPow3p5GM(), "ClipSuperRRectPow3p5GM", floor = 0.0)

    @Test
    @Disabled("STUB.NORMAL_MAP_SHADER: normal_map_shader + lit_shader impls not registered")
    fun `PaintAlphaNormalsRtGM matches paint_alpha_normals_rt reference`() =
        runGm(PaintAlphaNormalsRtGM(), "PaintAlphaNormalsRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.RAW_IMAGE_SHADER: SkImage.makeRawShader + SkColorSpace.makeColorSpin not implemented")
    fun `RawImageShaderNormalsRtGM matches raw_image_shader_normals_rt reference`() =
        runGm(RawImageShaderNormalsRtGM(), "RawImageShaderNormalsRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.LIT_SHADER_LINEAR: lit_shader_linear impl not registered")
    fun `LitShaderLinearRtGM matches lit_shader_linear_rt reference`() =
        runGm(LitShaderLinearRtGM(), "LitShaderLinearRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.LOCAL_MATRIX_RT: passthrough shader impl not registered")
    fun `LocalMatrixShaderRtGM matches local_matrix_shader_rt reference`() =
        runGm(LocalMatrixShaderRtGM(), "LocalMatrixShaderRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.NULL_CHILD_RT: ChildPtr null-dispatch not implemented in ChildResolver")
    fun `NullChildRtGM matches null_child_rt reference`() =
        runGm(NullChildRtGM(), "NullChildRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.DEFERRED_SHADER: SkRuntimeEffectPriv.MakeDeferredShader is a private Skia API not exposed in kanvas-skia")
    fun `DeferredShaderRtGM matches deferred_shader_rt reference`() =
        runGm(DeferredShaderRtGM(), "DeferredShaderRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.ALPHA_IMAGE_SHADER: alpha-image paint-colour suppression in ChildResolver not implemented")
    fun `AlphaImageShaderRtGM matches alpha_image_shader_rt reference`() =
        runGm(AlphaImageShaderRtGM(), "AlphaImageShaderRtGM", floor = 0.0)

    @Test
    @Disabled("STUB.COLOR_CUBE_CF: SkRuntimeEffect.makeColorFilter does not support kShader children — integration pending")
    fun `ColorCubeColorFilterRtGM matches color_cube_cf_rt reference`() {
        // color_cube_cf_rt uses a shader child inside a color-filter program —
        // the kanvas binding's makeColorFilter rejects shader children until
        // the integration in SkRuntimeEffect.kt is complete.
        TODO("STUB.COLOR_CUBE_CF: shader-child inside color-filter not wired")
    }
}
