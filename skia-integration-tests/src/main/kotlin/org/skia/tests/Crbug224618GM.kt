package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.math.SK_ColorYELLOW
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalarMod
import org.skia.math.SkV3
import org.skia.math.SkV4
import org.skia.core.SrcRectConstraint

/**
 * Port of Skia's `gm/crbug_224618.cpp::CrBug224618GM`.
 *
 * Renders a 6-faced cube projected through a CSS-style perspective
 * matrix, alternating between a solid-fill quad and a textured
 * `drawImageRect` quad on each face. Used upstream to validate that
 * the GPU `FillRectOp` and `TextureOp` paths agree on perspective
 * clipping ; on raster the GM is a smoke test for the perspective-
 * aware rasteriser (`SkBitmapDevice.drawPath` + perspective
 * `SkBitmapShader.shadeRow`).
 *
 * Animation : in viewer the FOV cycles via [onAnimate]. We freeze the
 * time at `0` so the rendered frame matches the reference PNG
 * (`viewportWidth = kMinVW = 300`, hence `radius = 150`).
 */
public class Crbug224618GM : GM() {

    private companion object {
        const val K_MAX_VW: Int = 800
        const val K_MIN_VW: Int = 300
    }

    private var fTime: Float = 0f
    private var fCubeImage: SkImage? = null

    override fun getName(): String = "crbug_224618"

    override fun getISize(): SkISize = SkISize.Make(K_MAX_VW, K_MAX_VW)

    override fun onOnceBeforeDraw() {
        // Pre-render a 400 × 400 mirrored radial gradient to use as the
        // cube-face texture. Two-stop gradient (transparent ↔ semi-white)
        // mirroring under tile mode produces a checkerboard-ish pattern.
        val surface: SkSurface = SkSurface.MakeRaster(
            SkImageInfo.MakeN32Premul(400, 400),
        )
        val gradient = SkRadialGradient.Make(
            center = SkPoint(200f, 200f),
            radius = 25f,
            colors = intArrayOf(
                SK_ColorTRANSPARENT,
                SkColorSetARGB(128, 255, 255, 255),
            ),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )
        val bgPaint = SkPaint().apply {
            shader = gradient
        }
        surface.canvas.drawPaint(bgPaint)
        fCubeImage = surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val cubeImage = fCubeImage ?: return

        val viewportWidth = SkScalarMod(fTime, 10f) / 10f * (K_MAX_VW - K_MIN_VW) + K_MIN_VW
        val radius = viewportWidth / 2f

        // CSS-style perspective projection matrix.
        // [include/core/SkM44.h] row-major scalar ctor: m0/4/8/12 is row 0.
        val proj = SkM44(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, -1f / radius, 1f,
        )
        val zoom = SkM44.translate(0f, 0f, radius)
        val postZoom = SkM44.translate(0f, 0f, -radius - 1f)
        val rotateHorizontal = SkM44.rotate(SkV3(0f, 1f, 0f), 2.356194490192345f)

        val axisAngles = arrayOf(
            SkV4(0f, 1f, 0f, -90f),  // rotateY(-90deg)
            SkV4(1f, 0f, 0f,   0f),  // <none>
            SkV4(0f, 1f, 0f,  90f),  // rotateY(90deg)
            SkV4(0f, 1f, 0f, 180f),  // rotateY(180deg)
            SkV4(1f, 0f, 0f, -90f),  // rotateX(-90deg)
            SkV4(1f, 0f, 0f,  90f),  // rotateX(90deg)
        )
        val faceColors = intArrayOf(
            SK_ColorRED,
            SK_ColorGREEN,
            SK_ColorBLUE,
            SK_ColorYELLOW,
            SkColorSetARGB(0xFF, 0xFF, 0xA5, 0x00),  // CSS orange
            SkColorSetARGB(0xFF, 0x80, 0x00, 0x80),  // CSS purple
        )

        for (i in 0 until 6) {
            val a = axisAngles[i]
            val model = SkM44.rotate(SkV3(a.x, a.y, a.z), Math.toRadians(a.w.toDouble()).toFloat())

            // Build : Translate(r, r) · proj · zoom · rotateHorizontal · model · postZoom · Translate(-r, -r)
            val composed = SkM44.translate(radius, radius)
                .preConcat(proj)
                .preConcat(zoom)
                .preConcat(rotateHorizontal)
                .preConcat(model)
                .preConcat(postZoom)
                .preConcat(SkM44.translate(-radius, -radius))

            c.save()
            c.concat(composed)

            val fillPaint = SkPaint().apply {
                isAntiAlias = true
                color = faceColors[i]
            }
            c.drawRect(SkRect.MakeWH(viewportWidth, viewportWidth), fillPaint)

            c.drawImageRect(
                cubeImage,
                SkRect.MakeWH(cubeImage.width.toFloat(), cubeImage.height.toFloat()),
                SkRect.MakeWH(viewportWidth, viewportWidth),
                SkSamplingOptions(SkFilterMode.kLinear),
                fillPaint,
                SrcRectConstraint.kFast,
            )

            c.restore()
        }
    }
}
