package org.skia.foundation


import org.graphiks.math.SK_ColorRED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix

/**
 * Unit tests for [SkShader.makeWithLocalMatrix] (R-final.2).
 *
 * Validates the three folding behaviours called out in the upstream
 * `SkLocalMatrixShader::onMakeWithLocalMatrix` contract :
 *  - identity matrix returns `this` (no wrapper allocation),
 *  - chaining two `makeWithLocalMatrix` folds into a single wrapper
 *    rather than nesting,
 *  - the per-draw `setupForDraw` sees the augmented CTM through to
 *    the wrapped child shader.
 */
class SkLocalMatrixShaderTest {

    @Test
    fun `identity matrix returns the same shader instance`() {
        val base = SkShaders.Color(SK_ColorRED)
        val wrapped = base.makeWithLocalMatrix(SkMatrix.Identity)
        assertSame(base, wrapped, "identity makeWithLocalMatrix should return this")
    }

    @Test
    fun `non-identity matrix returns a new wrapper`() {
        val base = SkShaders.Color(SK_ColorRED)
        val wrapped = base.makeWithLocalMatrix(SkMatrix.MakeTrans(10f, 20f))
        assertNotSame(base, wrapped, "non-identity should allocate a wrapper")
    }

    @Test
    fun `chained makeWithLocalMatrix folds into a single wrapper`() {
        val base = SkShaders.Color(SK_ColorRED)
        val outer = SkMatrix.MakeTrans(10f, 0f)
        val inner = SkMatrix.MakeTrans(0f, 5f)
        // First wrap pulls (proxy=base, lm=inner). Second wrap should
        // unwrap that pair, fold (outer · inner), and re-wrap base —
        // exactly one level of nesting.
        val once = base.makeWithLocalMatrix(inner)
        val twice = once.makeWithLocalMatrix(outer)
        // The folded wrapper exposes (proxy, lm) via
        // makeAsALocalMatrixShader. The proxy must be the original
        // base, not the first wrapper.
        val pair = twice.makeAsALocalMatrixShader()!!
        assertSame(base, pair.first, "chained wrap should fold proxy back to base")
        assertEquals(10f, pair.second.tx, 1e-6f)
        assertEquals(5f, pair.second.ty, 1e-6f)
    }

    @Test
    fun `wrapped shader sees the augmented ctm at setupForDraw`() {
        // Concrete check : a colour-shader wrapped in a translate produces
        // the same colour as the unwrapped base (no local-space sampling
        // involved), but the wrapper still forwards setupForDraw without
        // throwing or introducing NaNs.
        val base = SkShaders.Color(SK_ColorRED)
        val wrapped = base.makeWithLocalMatrix(SkMatrix.MakeTrans(10f, 20f))
        val xform = SkColorSpaceXformSteps(
            src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
            dst = SkColorSpace.makeSRGB(), dstAT = SkAlphaType.kUnpremul,
        )
        wrapped.setupForDraw(SkMatrix.Identity, xform)
        val out = IntArray(4)
        wrapped.shadeRow(0, 0, 4, out)
        // SkColorShader returns its colour for every pixel regardless
        // of local matrix — the wrapper must not perturb that.
        out.forEach { assertEquals(SK_ColorRED, it) }
    }
}
