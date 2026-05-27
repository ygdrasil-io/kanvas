package org.skia.effects.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * D2.5 smoke tests for [SkRuntimeImageFilters.RuntimeShader].
 *
 * Registers a stub "fade-to-blue" runtime shader effect (single
 * `uniform shader child` slot, output = `child.eval(coord) +
 * SkColor4f(0, 0, 0.4, 0)`), wires it through
 * [SkRuntimeImageFilters.RuntimeShader], and verifies the per-pixel
 * output of [SkImageFilter.filterImage].
 */
class SkRuntimeImageFilterTest {

    /** Stub SkSL — adds 0.4 to the blue channel of the input shader. */
    private val FADE_TO_BLUE_SKSL: String = """
        uniform shader child;
        half4 main(float2 coord) {
            half4 c = child.eval(coord);
            return half4(c.r, c.g, c.b + 0.4, c.a);
        }
    """

    /** Hand-port of FADE_TO_BLUE_SKSL — registered before each test. */
    private object FadeToBlueImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, index = 0),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val coord = coords ?: return SkColor4f.kBlack
            val childRes = children[0] as ChildResolver.Shader
            val c = childRes.sample(coord)
            return SkColor4f(c.fR, c.fG, c.fB + 0.4f, c.fA)
        }
    }

    @BeforeEach
    fun registerStub() {
        SkRuntimeEffectDispatch.registerForTestOverride(FADE_TO_BLUE_SKSL) { FadeToBlueImpl }
    }

    @Test
    fun `RuntimeShader image filter wires effect through filterImage`() {
        // Build a 4×4 red source image.
        val w = 4
        val h = 4
        val pixels = IntArray(w * h) { 0xFFFF0000.toInt() }  // opaque red
        val src = SkImage(w, h, pixels)

        // Compile + bind the stub effect.
        val effect = SkRuntimeEffect.MakeForShader(FADE_TO_BLUE_SKSL).effect
            ?: error("Failed to compile fade-to-blue effect")
        val builder = SkRuntimeEffectBuilder(effect)

        // Build the image filter ; child binds to the layer source.
        val filter = SkRuntimeImageFilters.RuntimeShader(
            builder = builder,
            sampleRadius = 0f,
            childShaderName = "child",
            input = null,
        )

        // Apply to the source.
        val result = filter.filterImage(src, SkMatrix.Identity)
        assertEquals(w, result.image.width)
        assertEquals(h, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)

        // Each pixel : red (1, 0, 0, 1) + fade-to-blue (0, 0, 0.4, 0)
        // = (1, 0, 0.4, 1). After 8-bit pack : R = 255, G = 0,
        // B = 102 (0.4 * 255), A = 255.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = result.image.peekPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel shr 24) and 0xFF
                assertEquals(255, r, "R at ($x, $y)")
                assertEquals(0, g, "G at ($x, $y)")
                assertEquals(102, b, "B at ($x, $y) (0.4 * 255 ≈ 102)")
                assertEquals(255, a, "A at ($x, $y)")
            }
        }
    }

    @Test
    fun `multi-child variant accepts named child bindings`() {
        // Same stub effect, accessed via the multi-child overload.
        val effect = SkRuntimeEffect.MakeForShader(FADE_TO_BLUE_SKSL).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        val filter = SkRuntimeImageFilters.RuntimeShader(
            builder = builder,
            childShaderNames = arrayOf("child"),
            inputs = arrayOf<SkImageFilter?>(null),
        )

        val src = SkImage(2, 2, IntArray(4) { 0xFF000000.toInt() })  // black
        val result = filter.filterImage(src, SkMatrix.Identity)

        // Black + (0, 0, 0.4, 0) = (0, 0, 0.4, 1). B = 102.
        val pixel = result.image.peekPixel(0, 0)
        assertEquals(0, (pixel shr 16) and 0xFF, "R")
        assertEquals(0, (pixel shr 8) and 0xFF, "G")
        assertEquals(102, pixel and 0xFF, "B")
    }

    @Test
    fun `mismatched childShaderNames and inputs throws`() {
        val effect = SkRuntimeEffect.MakeForShader(FADE_TO_BLUE_SKSL).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        try {
            SkRuntimeImageFilters.RuntimeShader(
                builder = builder,
                childShaderNames = arrayOf("child", "extra"),
                inputs = arrayOf<SkImageFilter?>(null),
            )
            assertNotNull(null, "should have thrown")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
    }
}
