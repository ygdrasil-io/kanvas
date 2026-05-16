package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.math.SkPoint
import org.skia.math.SkRect
import java.nio.ByteBuffer

/**
 * D2.2 verification suite for [SkRuntimeShader] — the [SkShader]
 * binding wrapping a registered [SkRuntimeImpl] of shader kind.
 *
 * **Behaviour under test** :
 *  - End-to-end : register a stub impl that returns a constant
 *    colour, build the shader via
 *    [SkRuntimeEffect.makeShader], install on a paint, draw a
 *    rect, verify the bitmap pixels match the constant.
 *  - The `coords` parameter is the local-space sample point
 *    (after deviceToLocal mapping) — verifiable by registering
 *    an impl that returns the coord as a colour.
 *  - `null` or empty children pass through.
 *  - Shader-child path : an impl that delegates to its single
 *    shader child gets the child's colour at the sampled point.
 *  - `makeShader` returns `null` when called on a non-shader
 *    effect (e.g. a color filter), or with a wrong children
 *    count.
 */
class SkRuntimeShaderTest {

    @AfterEach fun cleanup() { SkRuntimeEffectDispatch.clearForTest() }

    private fun bitmap(w: Int = 16, h: Int = 16, bg: Int = SK_ColorWHITE): SkBitmap =
        SkBitmap(w, h).also { it.eraseColor(bg) }

    /** Stub impl that returns the same constant for every pixel. */
    private fun constantColorImpl(c: SkColor4f) = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = 0
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = c
    }

    private val identityShaderSksl = """
        half4 main(vec2 p) { return vec4(0); }
    """.trimIndent()

    @Test
    fun `makeShader returns null for a colorFilter-shaped SkSL`() {
        val sksl = "half4 main(vec4 c) { return c; }"
        SkRuntimeEffectDispatch.register(sksl) { constantColorImpl(SkColor4f.kRed) }
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect!!
        // makeShader must reject — the effect doesn't allowShader.
        assertNull(effect.makeShader(null), "color-filter effect can't make a shader")
    }

    @Test
    fun `makeShader returns null on children-count mismatch`() {
        val sksl = """
            uniform shader child;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { constantColorImpl(SkColor4f.kBlue) }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        // Effect declares 1 child, but we pass 0.
        assertNull(effect.makeShader(null, emptyArray()))
        assertNull(effect.makeShader(null, arrayOf<SkShader?>(null, null)))
    }

    @Test
    fun `makeShader produces a non-null SkShader for a shader-shaped SkSL`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) {
            constantColorImpl(SkColor4f.kRed)
        }
        val effect = SkRuntimeEffect.MakeForShader(identityShaderSksl).effect!!
        val shader = effect.makeShader(null)
        assertNotNull(shader)
        assertTrue(shader is SkRuntimeShader)
    }

    // ─── End-to-end pixel parity ──────────────────────────────────────

    @Test
    fun `drawRect with a constant-color RuntimeShader fills with the constant`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) {
            // Mid-grey, opaque.
            constantColorImpl(SkColor4f(0.5f, 0.5f, 0.5f, 1f))
        }
        val effect = SkRuntimeEffect.MakeForShader(identityShaderSksl).effect!!
        val shader = effect.makeShader(null)!!
        val bm = bitmap()
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(16f, 16f),
            SkPaint().apply { this.shader = shader },
        )
        // Expect mid-grey pixels (≈ 0xFF808080).
        val mid = bm.getPixel(8, 8)
        val r = (mid ushr 16) and 0xFF
        val g = (mid ushr 8) and 0xFF
        val b = mid and 0xFF
        val a = mid ushr 24
        assertEquals(0xFF, a, "alpha should be 1.0 → 0xFF")
        for (chan in listOf(r, g, b)) {
            assertTrue(kotlin.math.abs(chan - 128) <= 2,
                "channel should round to ≈ 128 ; got $chan")
        }
    }

    @Test
    fun `RuntimeShader receives the local-space sample coord`() {
        // Impl that encodes `coords.x / 16` into the red channel and
        // `coords.y / 16` into the green channel. Drawing a 16×16 rect
        // at identity CTM should produce a gradient where pixel (x, y)
        // has R ≈ x*16 + 8, G ≈ y*16 + 8.
        var sawCoords: SkPoint? = null
        val coordImpl = object : SkRuntimeImpl {
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
                sawCoords = coords
                val u = (coords?.fX ?: 0f) / 16f
                val v = (coords?.fY ?: 0f) / 16f
                return SkColor4f(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f), 0f, 1f)
            }
        }
        SkRuntimeEffectDispatch.register(identityShaderSksl) { coordImpl }
        val effect = SkRuntimeEffect.MakeForShader(identityShaderSksl).effect!!
        val shader = effect.makeShader(null)!!
        val bm = bitmap(16, 16)
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(16f, 16f),
            SkPaint().apply { this.shader = shader },
        )
        // Pixel (0, 0) sees coord (0.5, 0.5) → R ≈ 8, G ≈ 8.
        val tl = bm.getPixel(0, 0)
        val tlR = (tl ushr 16) and 0xFF
        val tlG = (tl ushr 8) and 0xFF
        assertTrue(kotlin.math.abs(tlR - 8) <= 2, "(0,0) R should be ≈ 8 ; got $tlR")
        assertTrue(kotlin.math.abs(tlG - 8) <= 2, "(0,0) G should be ≈ 8 ; got $tlG")
        // Pixel (15, 15) sees coord (15.5, 15.5) → R ≈ 247, G ≈ 247.
        val br = bm.getPixel(15, 15)
        val brR = (br ushr 16) and 0xFF
        val brG = (br ushr 8) and 0xFF
        assertTrue(kotlin.math.abs(brR - 247) <= 2, "(15,15) R should be ≈ 247 ; got $brR")
        assertTrue(kotlin.math.abs(brG - 247) <= 2, "(15,15) G should be ≈ 247 ; got $brG")
        assertNotNull(sawCoords, "impl should have been called")
    }

    // ─── Child-shader pass-through ────────────────────────────────────

    @Test
    fun `RuntimeShader with a non-null shader child invokes the child's sampleAtLocal`() {
        // Stub impl that delegates to its single shader child :
        //   `vec4 main(vec2 p) { return child.eval(p); }`
        val sksl = """
            uniform shader child;
            half4 main(vec2 p) {
                return child.eval(p);
            }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = listOf(
                    SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0),
                )
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    val resolver = children[0] as ChildResolver.Shader
                    return resolver.sample(coords!!)
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!

        // The child : a stub-impl shader that returns a constant.
        SkRuntimeEffectDispatch.register(identityShaderSksl) {
            constantColorImpl(SkColor4f.kRed)
        }
        val childEffect = SkRuntimeEffect.MakeForShader(identityShaderSksl).effect!!
        val childShader = childEffect.makeShader(null)!!

        val shader = effect.makeShader(null, arrayOf<SkShader?>(childShader))!!
        val bm = bitmap()
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(16f, 16f),
            SkPaint().apply { this.shader = shader },
        )
        // Outer impl delegates to child → child returns red.
        val mid = bm.getPixel(8, 8)
        assertEquals(0xFF, mid ushr 24)
        assertEquals(0xFF, (mid ushr 16) and 0xFF, "R should be 0xFF (child returns red)")
        assertEquals(0x00, (mid ushr 8) and 0xFF, "G should be 0x00")
        assertEquals(0x00, mid and 0xFF, "B should be 0x00")
    }

    @Test
    fun `RuntimeShader with a null shader child slot resolves to transparent`() {
        // Same outer impl as above ; pass null for the child.
        val sksl = """
            uniform shader child;
            half4 main(vec2 p) { return child.eval(p); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = listOf(
                    SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0),
                )
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    val resolver = children[0] as ChildResolver.Shader
                    return resolver.sample(coords!!)
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val shader = effect.makeShader(null, arrayOf<SkShader?>(null))!!

        val bm = bitmap(16, 16, bg = 0xFFFF8000.toInt()) // orange bg
        SkCanvas(bm).drawRect(
            SkRect.MakeWH(16f, 16f),
            SkPaint().apply { this.shader = shader },
        )
        // Null child resolver returns transparent → outer impl
        // returns transparent → kSrcOver blend leaves orange bg
        // unchanged.
        assertEquals(0xFFFF8000.toInt(), bm.getPixel(8, 8),
            "null child → transparent → bg preserved")
    }

    // ─── Uniforms ────────────────────────────────────────────────────

    @Test
    fun `RuntimeShader passes uniform bytes through to the impl`() {
        val sksl = """
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        var sawBytes: ByteArray? = null
        SkRuntimeEffectDispatch.register(sksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
                    SkRuntimeEffect.Uniform(
                        name = "tint", offset = 0, type = SkRuntimeEffect.Uniform.Type.kFloat4,
                        count = 1, flags = 0,
                    ),
                )
                override val children: List<SkRuntimeEffect.Child> = emptyList()
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    if (sawBytes == null) {
                        // Capture the first 16 bytes (vec4 = 16 bytes).
                        val buf = ByteArray(16)
                        uniforms.position(0)
                        uniforms.get(buf, 0, 16)
                        sawBytes = buf
                    }
                    return SkColor4f.kBlack
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val data = SkData.MakeWithCopy(byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        ))
        val shader = effect.makeShader(data)!!
        val bm = bitmap(2, 2)
        SkCanvas(bm).drawRect(SkRect.MakeWH(2f, 2f), SkPaint().apply { this.shader = shader })

        assertNotNull(sawBytes, "impl should have been invoked")
        assertEquals(16, sawBytes!!.size)
        for (i in 0 until 16) {
            assertEquals((i + 1).toByte(), sawBytes!![i],
                "uniform byte $i should be ${i + 1}, got ${sawBytes!![i]}")
        }
    }
}
