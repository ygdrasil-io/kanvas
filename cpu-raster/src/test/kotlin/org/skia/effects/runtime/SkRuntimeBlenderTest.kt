package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import java.nio.ByteBuffer

/**
 * D2.2 verification suite for [SkRuntimeBlender].
 *
 * Same skeleton as the [SkRuntimeColorFilter] / [SkRuntimeShader]
 * suites — verify the binding's null guards, parameter forwarding,
 * and end-to-end pixel parity through `paint.blender`.
 */
class SkRuntimeBlenderTest {

    @AfterEach fun cleanup() { SkRuntimeEffectDispatch.clearForTest() }

    private val averageSksl = """
        half4 main(vec4 src, vec4 dst) { return (src + dst) * 0.5; }
    """.trimIndent()

    private fun bitmap(w: Int = 8, h: Int = 8, bg: Int = SK_ColorBLUE): SkBitmap =
        SkBitmap(w, h).also { it.eraseColor(bg) }

    @Test
    fun `makeBlender returns null for a shader-shaped SkSL`() {
        val sksl = "half4 main(vec2 p) { return vec4(0); }"
        SkRuntimeEffectDispatch.register(sksl) { stubAverage() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        assertNull(effect.makeBlender(null), "shader effect can't make a blender")
    }

    @Test
    fun `RuntimeBlender forwards (src, dst) for an averaging impl`() {
        SkRuntimeEffectDispatch.register(averageSksl) { stubAverage() }
        val effect = SkRuntimeEffect.MakeForBlender(averageSksl).effect!!
        val blender = effect.makeBlender(null)!!
        val out = blender.blend(SkColor4f(1f, 0f, 0f, 1f), SkColor4f(0f, 0f, 1f, 1f))
        assertEquals(0.5f, out.fR, 1e-5f)
        assertEquals(0f, out.fG, 1e-5f)
        assertEquals(0.5f, out.fB, 1e-5f)
        assertEquals(1f, out.fA, 1e-5f)
    }

    @Test
    fun `paint blender = average-RuntimeBlender produces midpoint pixels`() {
        SkRuntimeEffectDispatch.register(averageSksl) { stubAverage() }
        val effect = SkRuntimeEffect.MakeForBlender(averageSksl).effect!!
        val blender = effect.makeBlender(null)!!

        // Draw red on blue with the averaging blender → ≈ half-purple.
        val bm = bitmap(8, 8, bg = SkColorSetARGB(0xFF, 0, 0, 0xFF))  // blue
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(8f, 8f),
            SkPaint(SK_ColorRED).apply { this.blender = blender },
        )
        val mid = bm.getPixel(4, 4)
        val r = (mid ushr 16) and 0xFF
        val g = (mid ushr 8) and 0xFF
        val b = mid and 0xFF
        // Average((1,0,0,1), (0,0,1,1)) = (0.5, 0, 0.5, 1) → (128, 0, 128, 0xFF).
        assertTrue(kotlin.math.abs(r - 128) <= 2, "R should be ≈ 128 ; got $r")
        assertEquals(0, g, "G should be 0")
        assertTrue(kotlin.math.abs(b - 128) <= 2, "B should be ≈ 128 ; got $b")
    }

    @Test
    fun `builtin invert runtime blender uses destination color on CPU`() {
        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect!!
        val blender = effect.makeBlender(null)!!

        val out = blender.blend(
            src = SkColor4f(1f, 0f, 0f, 0.5f),
            dst = SkColor4f(0.25f, 0.5f, 0.75f, 0.4f),
        )

        assertEquals(0.75f, out.fR, 1e-5f)
        assertEquals(0.5f, out.fG, 1e-5f)
        assertEquals(0.25f, out.fB, 1e-5f)
        assertEquals(1f, out.fA, 1e-5f)
    }

    private fun stubAverage() = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = 0
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val s = srcColor!!
            val d = dstColor!!
            return SkColor4f(
                (s.fR + d.fR) * 0.5f,
                (s.fG + d.fG) * 0.5f,
                (s.fB + d.fB) * 0.5f,
                (s.fA + d.fA) * 0.5f,
            )
        }
    }
}
