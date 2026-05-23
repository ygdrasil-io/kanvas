package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

// ─── clip_shader ─────────────────────────────────────────────────────────────

/**
 * Port of `DEF_SIMPLE_GM(clip_shader, canvas, 840, 650)` in
 * `gm/complexclip.cpp`.
 *
 * Loads `images/yellow_rose.png`, wraps it in a shader, and demonstrates
 * four `clipShader` usages laid out in a 2×2 grid:
 *  - TL: original image (no clip-shader)
 *  - TR: intersect clip-shader → draw red rect
 *  - BL: difference clip-shader → draw green rect
 *  - BR: two nested intersect clip-shaders (full + 1/5-scaled) → draw image
 *
 * Reference image: `clip_shader.png`, 840 × 650.
 */
public class ClipShaderSimpleGM : GM() {

    override fun getName(): String = "clip_shader"
    override fun getISize(): SkISize = SkISize.Make(840, 650)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val img = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        val sh = img.makeShader(SkSamplingOptions())

        val r = SkRect.MakeIWH(img.width, img.height)
        val p = SkPaint()

        c.translate(10f, 10f)

        // TL: original image (no clipShader)
        c.drawImage(img, 0f, 0f)

        // TR: intersect with image shader → red rect
        c.save()
        c.translate(img.width + 10f, 0f)
        c.clipShader(sh, SkClipOp.kIntersect)
        p.color = SK_ColorRED
        c.drawRect(r, p)
        c.restore()

        // BL: difference with image shader → green rect
        c.save()
        c.translate(0f, img.height + 10f)
        c.clipShader(sh, SkClipOp.kDifference)
        p.color = SK_ColorGREEN
        c.drawRect(r, p)
        c.restore()

        // BR: intersect with image shader, then intersect with 1/5-scaled tiled shader → draw image
        c.save()
        c.translate(img.width + 10f, img.height + 10f)
        c.clipShader(sh, SkClipOp.kIntersect)
        c.save()
        val lm = SkMatrix.MakeScale(1.0f / 5, 1.0f / 5)
        c.clipShader(img.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions(), lm))
        c.drawImage(img, 0f, 0f)
        c.restore()
        c.restore()
    }
}

// ─── clip_shader_layer ───────────────────────────────────────────────────────

/**
 * Port of `DEF_SIMPLE_GM(clip_shader_layer, canvas, 430, 320)` in
 * `gm/complexclip.cpp`.
 *
 * Clips a rect, applies a clipShader, then opens a saveLayer over that
 * rect, fills it with red, and restores — demonstrating that the layer
 * restore applies the clip-shader mask to the layer output.
 *
 * Reference image: `clip_shader_layer.png`, 430 × 320.
 */
public class ClipShaderLayerGM : GM() {

    override fun getName(): String = "clip_shader_layer"
    override fun getISize(): SkISize = SkISize.Make(430, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val img = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        val sh = img.makeShader(SkSamplingOptions())

        val r = SkRect.MakeIWH(img.width, img.height)

        c.translate(10f, 10f)
        // clip to image bounds, then apply clip-shader
        c.clipRect(r)
        c.clipShader(sh)
        // open a layer, fill red, restore (layer output gets masked by clipShader)
        c.saveLayer(r, null)
        c.drawColor(0xFFFF0000.toInt())
        c.restore()
    }
}

// ─── clip_shader_nested ──────────────────────────────────────────────────────

/**
 * Port of `DEF_SIMPLE_GM(clip_shader_nested, canvas, 256, 256)` in
 * `gm/complexclip.cpp`.
 *
 * Demonstrates nested clip-shader compositions:
 *  - TL (128×128): black rect through two nested radial-gradient clip-shaders
 *    (outer at natural scale; inner after 2× scale)
 *  - BL (64×64):   small red rect — no clipping
 *  - TR (64×64):   green rect through a radial-gradient clipShader + RRect clip
 *  - BR (64×64):   blue rect through a radial-gradient clipShader + star-path clip
 *
 * Reference image: `clip_shader_nested.png`, 256 × 256.
 */
public class ClipShaderNestedGM : GM() {

    override fun getName(): String = "clip_shader_nested"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val w = 64f
        val h = 64f

        // SkColorConverter{SK_ColorBLACK, SkColorSetARGB(128,128,128,128)} → two-stop gradient
        val gradColors = intArrayOf(SK_ColorBLACK, SkColorSetARGB(128, 128, 128, 128))
        val gradCenter = SkPoint.Make(0.5f * w, 0.5f * h)
        val gradRadius = 0.1f * w

        // Helper to make the same radial gradient (used in all four cells)
        fun makeGrad(): org.skia.foundation.SkShader =
            SkRadialGradient.Make(
                center   = gradCenter,
                radius   = gradRadius,
                colors   = gradColors,
                positions = null,
                tileMode = SkTileMode.kRepeat,
            )

        val p = SkPaint()

        // TL: large black rect affected by two nested gradient clip-shaders
        c.save()
        c.clipShader(makeGrad())
        c.scale(2f, 2f)
        c.clipShader(makeGrad())
        c.drawRect(SkRect.MakeWH(w, h), p)
        c.restore()

        // BL: small red rect, no clipping
        c.translate(0f, 2f * h)
        c.save()
        p.color = SK_ColorRED
        c.drawRect(SkRect.MakeWH(w, h), p)
        c.restore()

        // TR: small green rect, clip-shader + RRect clip
        c.translate(2f * w, -2f * h)
        c.save()
        c.clipShader(makeGrad())
        c.clipRRect(SkRRect.MakeRectXY(SkRect.MakeWH(w, h), 10f, 10f), doAntiAlias = true)
        p.color = SK_ColorGREEN
        c.drawRect(SkRect.MakeWH(w, h), p)
        c.restore()

        // BR: small blue rect, clip-shader + star-path clip
        c.translate(0f, 2f * h)
        c.save()
        c.clipShader(makeGrad())
        // 12-point star path centred at (w/2, h/2)
        val starPath: SkPath = SkPathBuilder()
            .moveTo(0.0f, -33.3333f)
            .lineTo(9.62f, -16.6667f)
            .lineTo(28.867f, -16.6667f)
            .lineTo(19.24f, 0.0f)
            .lineTo(28.867f, 16.6667f)
            .lineTo(9.62f, 16.6667f)
            .lineTo(0.0f, 33.3333f)
            .lineTo(-9.62f, 16.6667f)
            .lineTo(-28.867f, 16.6667f)
            .lineTo(-19.24f, 0.0f)
            .lineTo(-28.867f, -16.6667f)
            .lineTo(-9.62f, -16.6667f)
            .close()
            .detach()
        c.translate(w / 2, h / 2)
        c.clipPath(starPath)
        p.color = SK_ColorBLUE
        c.translate(-w / 2, -h / 2)
        c.drawRect(SkRect.MakeWH(w, h), p)
        c.restore()
    }
}

// ─── clip_shader_difference ──────────────────────────────────────────────────

/**
 * Port of `DEF_SIMPLE_GM(clip_shader_difference, canvas, 512, 512)` in
 * `gm/complexclip.cpp`.
 *
 * Demonstrates `kDifference` clip-shader on four shapes arranged in a 2×2 grid:
 *  - TL: rectangle
 *  - TR: round-rectangle
 *  - BL: diamond + square path
 *  - BR: "Hello" text repeated 4 times
 *
 * Background is grey; the shader is a 64×64-tiled version of the rose image.
 * Where the shader's alpha is high the difference clip cuts the draw away,
 * leaving grey showing through; outside the shader the draw is unaffected.
 *
 * Reference image: `clip_shader_difference.png`, 512 × 512.
 */
public class ClipShaderDifferenceGM : GM() {

    override fun getName(): String = "clip_shader_difference"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val image = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        c.clear(0xFF888888.toInt())

        val rect = SkRect.MakeWH(256f, 256f)
        // Scale image down to 64×64 tile
        val local = SkMatrix.RectToRectOrIdentity(
            SkRect.MakeIWH(image.width, image.height),
            SkRect.MakeWH(64f, 64f),
        )
        val shader = image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions(), local)

        val paint = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        // TL: rectangle
        c.save()
        c.translate(0f, 0f)
        c.clipShader(shader, SkClipOp.kDifference)
        c.drawRect(rect, paint)
        c.restore()

        // TR: round-rectangle
        c.save()
        c.translate(256f, 0f)
        c.clipShader(shader, SkClipOp.kDifference)
        c.drawRRect(SkRRect.MakeRectXY(rect, 64f, 64f), paint)
        c.restore()

        // BL: diamond + square path
        c.save()
        c.translate(0f, 256f)
        c.clipShader(shader, SkClipOp.kDifference)
        val path: SkPath = SkPathBuilder()
            .moveTo(0f, 128f)
            .lineTo(128f, 256f)
            .lineTo(256f, 128f)
            .lineTo(128f, 0f)
            .let { pb ->
                val d = 64f * 1.41421356f // SK_ScalarSqrt2 ≈ √2
                pb
                    .moveTo(128f - d, 128f - d)
                    .lineTo(128f - d, 128f + d)
                    .lineTo(128f + d, 128f + d)
                    .lineTo(128f + d, 128f - d)
            }
            .detach()
        c.drawPath(path, paint)
        c.restore()

        // BR: text "Hello" repeated 4 times
        c.save()
        c.translate(256f, 256f)
        c.clipShader(shader, SkClipOp.kDifference)
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 64f)
        for (y in 0 until 4) {
            c.drawString("Hello", 32f, y * 64f, font, paint)
        }
        c.restore()
    }
}
