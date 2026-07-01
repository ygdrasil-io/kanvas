package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

public data class ConicalGrad2ptData(val colors: IntArray, val positions: FloatArray)

public sealed class ConicalGradients2ptVariantGM(
    private val gmName: String,
    private val makers: List<(Array<SkPoint>, ConicalGrad2ptData, SkTileMode, SkMatrix) -> SkShader?>,
    private val dither: Boolean,
    private val tileMode: SkTileMode = SkTileMode.kClamp,
) : GM() {

    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(840, 815)

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val rect = SkRect.MakeXYWH(0f, 0f, 100f, 100f)

        c.translate(20f, 20f)

        for (i in gConicalGrad2ptData.indices) {
            c.save()
            for (j in makers.indices) {
                val localMatrix = if (i == 3) {
                    SkMatrix.MakeScale(0.5f, 0.5f).postTranslate(25f, 25f)
                } else {
                    SkMatrix.Identity
                }
                val shader = makers[j](pts, gConicalGrad2ptData[i], tileMode, localMatrix)
                if (shader != null) {
                    val paint = SkPaint().apply {
                        isAntiAlias = true
                        this.shader = shader
                        isDither = dither
                    }
                    c.drawRect(rect, paint)
                }
                c.translate(0f, 120f)
            }
            c.restore()
            c.translate(120f, 0f)
        }
    }

    protected companion object {
        @JvmStatic
        protected fun make2ConicalOutside(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 10f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c0 = SkPoint(pts[0].fX + r0, pts[0].fY + r0)
            val c1 = SkPoint(pts[1].fX - r1, pts[1].fY - r1)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalOutsideFlip(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 10f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c0 = SkPoint(pts[0].fX + r0, pts[0].fY + r0)
            val c1 = SkPoint(pts[1].fX - r1, pts[1].fY - r1)
            return SkConicalGradient.Make(c1, r1, c0, r0, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalZeroRadOutside(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = 0f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c0 = SkPoint(pts[0].fX + r0, pts[0].fY + r0)
            val c1 = SkPoint(pts[1].fX - r1, pts[1].fY - r1)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalZeroRadFlipOutside(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = 0f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c0 = SkPoint(pts[0].fX + r0, pts[0].fY + r0)
            val c1 = SkPoint(pts[1].fX - r1, pts[1].fY - r1)
            return SkConicalGradient.Make(c1, r1, c0, r0, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalOutsideStrip(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r = (pts[1].fX - pts[0].fX) / 3f
            val c0 = SkPoint(pts[0].fX, pts[0].fY)
            val c1 = SkPoint(pts[1].fX, pts[1].fY)
            return SkConicalGradient.Make(c0, r, c1, r, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalEdgeX(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 7f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX + r1, c1.fY)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalEdgeY(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 7f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX, c1.fY + r1)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalZeroRadEdgeX(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = 0f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX + r1, c1.fY)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalZeroRadEdgeY(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = 0f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX, c1.fY + r1)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalTouchX(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 7f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX - r1 + r0, c1.fY)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalTouchY(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val r0 = (pts[1].fX - pts[0].fX) / 7f
            val r1 = (pts[1].fX - pts[0].fX) / 3f
            val c1 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            val c0 = SkPoint(c1.fX, c1.fY + r1 - r0)
            return SkConicalGradient.Make(c0, r0, c1, r1, data.colors, data.positions, tm, lm)
        }

        @JvmStatic
        protected fun make2ConicalInsideSmallRad(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
            val c0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
            return SkConicalGradient.Make(
                c0, 0.0000000000000000001f,
                c0, (pts[1].fX - pts[0].fX) / 2f,
                data.colors, data.positions, tm, lm,
            )
        }

        @JvmStatic
        protected val OUTSIDE_MAKERS: List<(Array<SkPoint>, ConicalGrad2ptData, SkTileMode, SkMatrix) -> SkShader?> = listOf(
            ::make2ConicalOutside,
            ::make2ConicalOutsideFlip,
            ::make2ConicalZeroRadOutside,
            ::make2ConicalZeroRadFlipOutside,
            ::make2ConicalOutsideStrip,
        )

        @JvmStatic
        protected val EDGE_MAKERS: List<(Array<SkPoint>, ConicalGrad2ptData, SkTileMode, SkMatrix) -> SkShader?> = listOf(
            ::make2ConicalEdgeX,
            ::make2ConicalEdgeY,
            ::make2ConicalZeroRadEdgeX,
            ::make2ConicalZeroRadEdgeY,
            ::make2ConicalTouchX,
            ::make2ConicalTouchY,
            ::make2ConicalInsideSmallRad,
        )

        private fun midpoint(a: Float, b: Float): Float = (a + b) * 0.5f

        @JvmStatic
        protected val gColorsBasic: IntArray = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
        )

        @JvmStatic
        protected val gColorClamp: IntArray = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        )

        @JvmStatic
        protected val gConicalGrad2ptData: List<ConicalGrad2ptData> = listOf(
            ConicalGrad2ptData(gColorsBasic.copyOfRange(0, 2), floatArrayOf(0f, 1f)),
            ConicalGrad2ptData(gColorsBasic.copyOfRange(0, 2), floatArrayOf(0.25f, 0.75f)),
            ConicalGrad2ptData(gColorsBasic.copyOfRange(0, 5), floatArrayOf(0f, 0.125f, 0.5f, 0.875f, 1f)),
            ConicalGrad2ptData(gColorClamp, floatArrayOf(0f, 0f, 1f, 1f)),
        )
    }
}
