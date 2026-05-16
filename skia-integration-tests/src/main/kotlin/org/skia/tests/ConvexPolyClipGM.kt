package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/convexpolyclip.cpp::ConvexPolyClip` (870 × 540 —
 * the non-bench layout doubles the base 435-wide column to host both
 * the no-saveLayer and saveLayer columns side by side).
 *
 * Tests convex polygon / rect clip paths. A textured image is drawn
 * under the canvas (faded with `alpha=0x15`), then a 5×2 grid of clip
 * cells :
 *  - 5 rows : triangle, hexagon, scaled hexagon, axis-aligned rect,
 *    rotated rect (each as `SkPath` or `SkRect`).
 *  - Per row, two passes (image-clipped and text-clipped) and two AA
 *    states (bw / aa). Both no-saveLayer and saveLayer columns are
 *    rendered.
 *
 * The textured image is built via [makeImg] — four concentric radial
 * gradients overlaid with `drawSimpleText("Skia")` — exactly as
 * upstream does.
 */
public class ConvexPolyClipGM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    private val clips = mutableListOf<Clip>()
    private var img: SkImage? = null

    override fun getName(): String = "convex_poly_clip"
    override fun getISize(): SkISize = SkISize.Make(870, 540)

    override fun onOnceBeforeDraw() {
        clips.add(
            Clip().apply {
                setPath(
                    SkPath.Polygon(
                        arrayOf(
                            5f to 5f,
                            100f to 20f,
                            15f to 100f,
                        ),
                        isClosed = false,
                    ),
                )
            },
        )

        // Hexagon.
        val hexagonBuilder = SkPathBuilder()
        val kRadius = 45f
        val center = SkPoint(kRadius, kRadius)
        for (i in 0 until 6) {
            val angle = 2 * PI.toFloat() * i / 6
            val px = cos(angle.toDouble()).toFloat() * kRadius
            val py = sin(angle.toDouble()).toFloat() * kRadius
            val sx = center.fX + px
            val sy = center.fY + py
            if (i == 0) hexagonBuilder.moveTo(sx, sy) else hexagonBuilder.lineTo(sx, sy)
        }
        val hexagon = hexagonBuilder.snapshot()
        clips.add(Clip().apply { setPath(hexagon) })

        // Scaled hexagon (1.1 × 0.4 about its centre).
        val scaleM = SkMatrix.MakeScale(1.1f, 0.4f, kRadius, kRadius)
        clips.add(Clip().apply { setPath(hexagonBuilder.detach().makeTransform(scaleM)) })

        // Plain rect.
        clips.add(Clip().apply { setRect(SkRect.MakeXYWH(8.3f, 11.6f, 78.2f, 72.6f)) })

        // Rotated rect (23° about centre).
        val rect = SkRect.MakeLTRB(10f, 12f, 80f, 86f)
        val rotM = SkMatrix.MakeRotate(23f, rect.centerX(), rect.centerY())
        clips.add(Clip().apply { setPath(SkPath.Rect(rect).makeTransform(rotM)) })

        img = makeImg(100, 100)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = img ?: return

        val bgPaint = SkPaint().apply { alpha = 0x15 }
        // Faded full-canvas background = the same texture stretched.
        c.drawImageRect(
            image,
            SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()),
            SkRect.MakeWH(size().width.toFloat(), size().height.toFloat()),
            paint = bgPaint,
        )

        val kTxt = "Clip Me!"
        val font = ToolUtils.DefaultPortableFont(23f)
        val textW = font.measureText(kTxt)
        val txtPaint = SkPaint().apply { color = SK_ColorDKGRAY }
        val kMargin = 10f

        var startX = 0f
        // testLayers loop : doLayer ∈ {0, 1} → two columns.
        for (doLayer in 0..1) {
            var y = 0f
            for (clip in clips) {
                var x = startX
                // Image-clipped pass.
                for (aa in 0..1) {
                    if (doLayer == 1) {
                        val bounds = clip.getBounds().copy()
                        bounds.outset(2f, 2f)
                        bounds.offset(x, y)
                        c.saveLayer(bounds, null)
                    } else {
                        c.save()
                    }
                    c.translate(x, y)
                    clip.setOnCanvas(c, SkClipOp.kIntersect, aa == 1)
                    c.drawImage(image, 0f, 0f)
                    c.restore()
                    x += image.width + kMargin
                }
                // Text-clipped pass.
                for (aa in 0..1) {
                    val outlinePaint = SkPaint().apply {
                        isAntiAlias = true
                        color = 0x50505050
                        style = SkPaint.Style.kStroke_Style
                        strokeWidth = 0f
                    }
                    if (doLayer == 1) {
                        val bounds = clip.getBounds().copy()
                        bounds.outset(2f, 2f)
                        bounds.offset(x, y)
                        c.saveLayer(bounds, null)
                    } else {
                        c.save()
                    }
                    c.translate(x, y)
                    c.drawPath(clip.asClosedPath(), outlinePaint)
                    clip.setOnCanvas(c, SkClipOp.kIntersect, aa == 1)
                    c.scale(1f, 1.8f)
                    c.drawString(kTxt, 0f, 1.5f * font.size, font, txtPaint)
                    c.restore()
                    x += textW + 2 * kMargin
                }
                y += image.height + kMargin
            }
            startX += 2 * image.width + kotlin.math.ceil(2 * textW).toInt() + 6 * kMargin
        }
    }

    private fun makeImg(w: Int, h: Int): SkImage {
        val surf = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val sc = surf.canvas

        val wF = w.toFloat()
        val hF = h.toFloat()
        val pt = SkPoint(wF / 2f, hF / 2f)
        val radius = 3f * maxOf(wF, hF)

        val colors = intArrayOf(
            SK_ColorDKGRAY,
            ToolUtils.colorTo565(0xFF222255.toInt()),
            ToolUtils.colorTo565(0xFF331133.toInt()),
            ToolUtils.colorTo565(0xFF884422.toInt()),
            ToolUtils.colorTo565(0xFF000022.toInt()),
            SK_ColorWHITE,
            ToolUtils.colorTo565(0xFFAABBCC.toInt()),
        )
        val pos = floatArrayOf(
            0f,
            1f / 6f,
            2f / 6f,
            3f / 6f,
            4f / 6f,
            5f / 6f,
            1f,
        )

        var rect = SkRect.MakeWH(wF, hF)
        var mat = SkMatrix.Identity
        val paint = SkPaint()
        for (i in 0 until 4) {
            paint.shader = SkRadialGradient.Make(pt, radius, colors, pos, SkTileMode.kRepeat, mat)
            sc.drawRect(rect, paint)
            rect = SkRect.MakeLTRB(
                rect.left + wF / 8f,
                rect.top + hF / 8f,
                rect.right - wF / 8f,
                rect.bottom - hF / 8f,
            )
            mat = mat.preTranslate(6f * wF, 6f * hF).postScale(1f / 3f, 1f / 3f)
        }

        val font = ToolUtils.DefaultPortableFont(wF / 2.2f)
        paint.shader = null
        paint.color = SK_ColorLTGRAY
        val txt = "Skia"
        val texPos = SkPoint(wF / 17f, hF / 2f + font.size / 2.5f)
        sc.drawString(txt, texPos.fX, texPos.fY, font, paint)
        paint.color = SK_ColorBLACK
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 1f
        sc.drawString(txt, texPos.fX, texPos.fY, font, paint)

        return surf.makeImageSnapshot()
    }

    private class Clip {
        enum class Type { kNone, kPath, kRect }

        var type: Type = Type.kNone
        var clipPath: SkPath = SkPathBuilder().detach()
        var clipRect: SkRect = SkRect.MakeLTRB(0f, 0f, 0f, 0f)

        fun setPath(p: SkPath) { type = Type.kPath; clipPath = p }
        fun setRect(r: SkRect) { type = Type.kRect; clipRect = r }

        fun setOnCanvas(canvas: SkCanvas, op: SkClipOp, aa: Boolean) {
            when (type) {
                Type.kPath -> canvas.clipPath(clipPath, op, aa)
                Type.kRect -> canvas.clipRect(clipRect, op, aa)
                Type.kNone -> Unit
            }
        }

        fun asClosedPath(): SkPath = when (type) {
            Type.kPath -> {
                // Append an explicit close verb. We don't have
                // SkPathBuilder(path).close() but addPath + close achieves
                // the same.
                SkPathBuilder().addPath(clipPath).close().detach()
            }
            Type.kRect -> SkPath.Rect(clipRect)
            Type.kNone -> SkPathBuilder().detach()
        }

        fun getBounds(): SkRect = when (type) {
            Type.kPath -> clipPath.computeBounds()
            Type.kRect -> clipRect
            Type.kNone -> SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        }
    }
}
