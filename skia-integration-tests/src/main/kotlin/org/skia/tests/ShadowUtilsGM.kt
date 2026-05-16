package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorCYAN
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorLTGRAY
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.utils.SkShadowUtils
import kotlin.math.max

/**
 * Port of Skia's `gm/shadowutils.cpp` (`shadow_utils`,
 * `shadow_utils_occl`, `shadow_utils_gray` GMs — 800 × 960).
 *
 * Lays out a grid of round-rect / nine-patch RRect / rect / circle /
 * cubic / oval paths under two transforms (identity, a rotated +
 * non-uniform scale) and two flag combos
 * (`kNone_ShadowFlag`, `kTransparentOccluder_ShadowFlag`), drawing
 * `SkShadowUtils.DrawShadow` followed by the path itself in a
 * mode-dependent fill / stroke.
 *
 * The three GM variants only differ in how the shadow colours and the
 * occluder fill are picked :
 *  - [Mode.DebugColorNoOccluders] : red ambient + blue spot, then a
 *    green / cyan zero-width stroke outline ;
 *  - [Mode.DebugColorOccluders] : same red+blue shadows, then a light-
 *    grey opaque (or 50% alpha for `kTransparentOccluder_ShadowFlag`)
 *    fill on top ;
 *  - [Mode.Grayscale] : a single pair of black ambient (10%) + black
 *    spot (25%) shadows, white fill on top.
 *
 * After the convex paths a second pass renders two concave paths
 * (12-pointed star + cubic dumbbell) — `SkShadowUtils.DrawShadow`
 * falls back to its blur path here. A small black circle marks the
 * light position in device space.
 *
 * C++ source : see `gm/shadowutils.cpp`. References:
 * `shadow_utils.png`, `shadow_utils_occl.png`, `shadow_utils_gray.png`.
 */
public class ShadowUtilsGM(private val mode: Mode) : GM() {

    public enum class Mode { DebugColorNoOccluders, DebugColorOccluders, Grayscale }

    override fun getName(): String = when (mode) {
        Mode.DebugColorNoOccluders -> "shadow_utils"
        Mode.DebugColorOccluders -> "shadow_utils_occl"
        Mode.Grayscale -> "shadow_utils_gray"
    }

    override fun getISize(): SkISize = SkISize.Make(kW, kH)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawPaths(c, mode)
    }

    private fun drawPaths(canvas: SkCanvas, mode: Mode) {
        val paths = ArrayList<SkPath>(6)
        paths.add(SkPath.RRect(SkRRect.MakeRectXY(SkRect.MakeWH(50f, 50f), 10f, 10.00002f)))

        val odd = SkRRect()
        odd.setNinePatch(SkRect.MakeWH(50f, 50f), 9f, 13f, 6f, 16f)
        paths.add(SkPath.RRect(odd))

        paths.add(SkPath.Rect(SkRect.MakeWH(50f, 50f)))
        paths.add(SkPath.Circle(25f, 25f, 25f))
        paths.add(SkPathBuilder().cubicTo(100f, 50f, 20f, 100f, 0f, 0f).detach())
        paths.add(SkPath.Oval(SkRect.MakeWH(20f, 60f)))

        val concavePaths = ArrayList<SkPath>(2)

        // 12-pointed star
        val star = SkPathBuilder()
        star.moveTo(0.0f, -33.3333f)
        star.lineTo(9.62f, -16.6667f)
        star.lineTo(28.867f, -16.6667f)
        star.lineTo(19.24f, 0.0f)
        star.lineTo(28.867f, 16.6667f)
        star.lineTo(9.62f, 16.6667f)
        star.lineTo(0.0f, 33.3333f)
        star.lineTo(-9.62f, 16.6667f)
        star.lineTo(-28.867f, 16.6667f)
        star.lineTo(-19.24f, 0.0f)
        star.lineTo(-28.867f, -16.6667f)
        star.lineTo(-9.62f, -16.6667f)
        star.close()
        concavePaths.add(star.detach())

        // dumbbell
        val dumb = SkPathBuilder()
        dumb.moveTo(50f, 0f)
        dumb.cubicTo(100f, 25f, 60f, 50f, 50f, 0f)
        dumb.cubicTo(0f, -25f, 40f, -50f, 50f, 0f)
        concavePaths.add(dumb.detach())

        val kPad = 15f
        val kLightR = 100f
        val kHeight = 50f

        // transform light position relative to canvas
        val lightXY = canvas.getTotalMatrix().mapXY(org.skia.math.SkPoint.Make(250f, 400f))
        val lightPos = SkPoint3(lightXY.fX, lightXY.fY, 500f)

        canvas.translate(3f * kPad, 3f * kPad)
        canvas.save()
        var x = 0f
        var dy = 0f

        val matrices = arrayOf(
            SkMatrix.Identity,
            SkMatrix.MakeRotate(33f, 25f, 25f).postScale(1.2f, 0.8f, 25f, 25f),
        )

        val flagSet = intArrayOf(
            SkShadowUtils.kNone_ShadowFlag,
            SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )

        for (m in matrices) {
            for (flags in flagSet) {
                var pathCounter = 0
                for (path in paths) {
                    val postM = m.mapRect(path.computeBounds())
                    val w = postM.width() + kHeight
                    val dx = w + kPad
                    if (x + dx > kW - 3f * kPad) {
                        canvas.restore()
                        canvas.translate(0f, dy)
                        canvas.save()
                        x = 0f
                        dy = 0f
                    }

                    canvas.save()
                    canvas.concat(m)

                    // flip a couple of paths to test 180° rotation
                    val flipping =
                        flags == SkShadowUtils.kTransparentOccluder_ShadowFlag && (pathCounter % 3 == 0)
                    if (flipping) {
                        canvas.save()
                        canvas.rotate(180f, 25f, 25f)
                    }

                    when (mode) {
                        Mode.DebugColorNoOccluders, Mode.DebugColorOccluders -> {
                            drawShadow(canvas, path, kHeight, SK_ColorRED, lightPos, kLightR, true, flags)
                            drawShadow(canvas, path, kHeight, SK_ColorBLUE, lightPos, kLightR, false, flags)
                        }
                        Mode.Grayscale -> {
                            val ambient = colorSetARGB((0.1f * 255).toInt(), 0, 0, 0)
                            val spot = colorSetARGB((0.25f * 255).toInt(), 0, 0, 0)
                            SkShadowUtils.DrawShadow(
                                canvas, path, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                                ambient, spot, flags,
                            )
                        }
                    }

                    val paint = SkPaint().apply { isAntiAlias = true }
                    when (mode) {
                        Mode.DebugColorNoOccluders -> {
                            paint.color = if (flags == SkShadowUtils.kTransparentOccluder_ShadowFlag)
                                SK_ColorCYAN else SK_ColorGREEN
                            paint.style = SkPaint.Style.kStroke_Style
                            paint.strokeWidth = 0f
                        }
                        Mode.DebugColorOccluders, Mode.Grayscale -> {
                            paint.color = if (mode == Mode.DebugColorOccluders) SK_ColorLTGRAY else SK_ColorWHITE
                            if (flags == SkShadowUtils.kTransparentOccluder_ShadowFlag) {
                                paint.alphaf = 0.5f
                            }
                            paint.style = SkPaint.Style.kFill_Style
                        }
                    }
                    canvas.drawPath(path, paint)
                    if (flipping) canvas.restore()
                    canvas.restore()

                    canvas.translate(dx, 0f)
                    x += dx
                    dy = max(dy, postM.height() + kPad + kHeight)
                    pathCounter++
                }
            }
        }

        // concave paths
        canvas.restore()
        canvas.translate(kPad, dy)
        canvas.save()
        x = kPad
        dy = 0f
        for (m in matrices) {
            for (path in concavePaths) {
                val postM = m.mapRect(path.computeBounds())
                val w = postM.width() + kHeight
                val dx = w + kPad

                canvas.save()
                canvas.concat(m)

                when (mode) {
                    Mode.DebugColorNoOccluders, Mode.DebugColorOccluders -> {
                        drawShadow(canvas, path, kHeight, SK_ColorRED, lightPos, kLightR, true,
                            SkShadowUtils.kNone_ShadowFlag)
                        drawShadow(canvas, path, kHeight, SK_ColorBLUE, lightPos, kLightR, false,
                            SkShadowUtils.kNone_ShadowFlag)
                    }
                    Mode.Grayscale -> {
                        val ambient = colorSetARGB((0.1f * 255).toInt(), 0, 0, 0)
                        val spot = colorSetARGB((0.25f * 255).toInt(), 0, 0, 0)
                        SkShadowUtils.DrawShadow(
                            canvas, path, SkPoint3(0f, 0f, kHeight), lightPos, kLightR,
                            ambient, spot, SkShadowUtils.kNone_ShadowFlag,
                        )
                    }
                }

                val paint = SkPaint().apply { isAntiAlias = true }
                when (mode) {
                    Mode.DebugColorNoOccluders -> {
                        paint.color = SK_ColorGREEN
                        paint.style = SkPaint.Style.kStroke_Style
                        paint.strokeWidth = 0f
                    }
                    Mode.DebugColorOccluders, Mode.Grayscale -> {
                        paint.color = if (mode == Mode.DebugColorOccluders) SK_ColorLTGRAY else SK_ColorWHITE
                        paint.style = SkPaint.Style.kFill_Style
                    }
                }
                canvas.drawPath(path, paint)
                canvas.restore()

                canvas.translate(dx, 0f)
                x += dx
                dy = max(dy, postM.height() + kPad + kHeight)
            }
        }

        // Show where the light is in x,y as a circle (specified in device space).
        val total = canvas.getTotalMatrix()
        val inv = total.invert()
        if (inv != null) {
            canvas.save()
            canvas.concat(inv)
            val paint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            }
            canvas.drawCircle(lightPos.fX, lightPos.fY, kLightR / 10f, paint)
            canvas.restore()
        }
    }

    private fun drawShadow(
        canvas: SkCanvas,
        path: SkPath,
        height: Float,
        color: SkColor,
        lightPos: SkPoint3,
        lightR: Float,
        isAmbient: Boolean,
        flags: Int,
    ) {
        val ambientAlpha = if (isAmbient) 0.5f else 0f
        val spotAlpha = if (isAmbient) 0f else 0.5f
        val baseA = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val ambientColor = colorSetARGB((ambientAlpha * baseA).toInt(), r, g, b)
        val spotColor = colorSetARGB((spotAlpha * baseA).toInt(), r, g, b)
        SkShadowUtils.DrawShadow(
            canvas, path, SkPoint3(0f, 0f, height), lightPos, lightR,
            ambientColor, spotColor, flags,
        )
    }

    private fun colorSetARGB(a: Int, r: Int, g: Int, b: Int): SkColor =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private companion object {
        const val kW: Int = 800
        const val kH: Int = 960
    }
}
