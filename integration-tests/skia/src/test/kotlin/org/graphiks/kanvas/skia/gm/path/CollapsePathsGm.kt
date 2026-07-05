package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/collapsepaths.cpp::collapsepaths` (500 × 600).
 *
 * 10 hand-crafted "near-collapse" paths — thin almost-degenerate
 * triangles with cubic-precision coordinates that previously caused
 * the rasteriser's edge-flattening to drop edges entirely (visible
 * as missing pixel rows). Each `test_collapseN` draws one such path
 * with `kEvenOdd` (or default winding for the inverted-V case 10)
 * and tests that the analytic AA preserves the shape's silhouette.
 * @see https://github.com/google/skia/blob/main/gm/collapsepaths.cpp
 */
class CollapsePathsGm : SkiaGm {
    override val name = "collapsepaths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 83.4
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, style = PaintStyle.FILL)
        testCollapse1(canvas, paint)
        testCollapse2(canvas, paint)
        testCollapse3(canvas, paint)
        testCollapse4(canvas, paint)
        testCollapse5(canvas, paint)
        testCollapse6(canvas, paint)
        testCollapse7(canvas, paint)
        testCollapse8(canvas, paint)
        testCollapse9(canvas, paint)
        testCollapse10(canvas, paint)
    }

    private fun testCollapse1(canvas: GmCanvas, p: Paint) {
        canvas.translate(0f, 0f)
        canvas.drawPath(
            Path { moveTo(652.830078125f, 673.9365234375f); lineTo(479.50152587890625f, 213.412628173828125f); lineTo(511.840545654296875f, 209.1551055908203125f); lineTo(528.14959716796875f, 208.6212158203125f); moveTo(370.50653076171875f, 73.684051513671875f); lineTo(525.02093505859375f, 208.6413726806640625f); lineTo(478.403564453125f, 213.5998992919921875f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse2(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(492.781982421875f, 508.7139892578125f); lineTo(361.946746826171875f, 161.0923004150390625f); lineTo(386.357513427734375f, 157.8785552978515625f); lineTo(398.668212890625f, 157.475555419921875f); moveTo(279.673004150390625f, 55.619640350341796875f); lineTo(396.30657958984375f, 157.4907684326171875f); lineTo(361.117950439453125f, 161.2336578369140625f) },
            p,
        )
    }

    private fun testCollapse3(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(31.9730987548828125f, 69.4149169921875f); lineTo(36.630767822265625f, 67.66190338134765625f); lineTo(51.1498870849609375f, 64.2765045166015625f); moveTo(52.94580078125f, 64.05560302734375f); lineTo(38.9994354248046875f, 66.8980712890625f); lineTo(32.229583740234375f, 69.31696319580078125f); lineTo(12.99810791015625f, 22.4723663330078125f) },
            p,
        )
    }

    private fun testCollapse4(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(122.66265869140625f, 77.81488800048828125f); lineTo(161.983642578125f, 128.557952880859375f); lineTo(22.599969863891601562f, 76.61859893798828125f); lineTo(18.03154754638671875f, 76.055633544921875f); lineTo(15.40312957763671875f, 75.7647247314453125f); lineTo(18.572841644287109375f, 75.2251129150390625f); lineTo(20.895002365112304688f, 73.7937774658203125f) },
            p,
        )
    }

    private fun testCollapse5(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(52.659847259521484375f, 782.0546875f); lineTo(136.6915130615234375f, 690.18011474609375f); lineTo(392.147796630859375f, 554.6090087890625f); lineTo(516.51470947265625f, 534.44134521484375f); moveTo(154.6182708740234375f, 188.230926513671875f); lineTo(430.242095947265625f, 546.76605224609375f); lineTo(373.1005859375f, 559.0906982421875f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse6(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(13.314494132995605469f, 197.7343902587890625f); lineTo(34.56102752685546875f, 174.5048675537109375f); lineTo(99.15048980712890625f, 140.22711181640625f); lineTo(130.595367431640625f, 135.1279296875f); moveTo(39.09362030029296875f, 47.59223175048828125f); lineTo(108.7822418212890625f, 138.244110107421875f); lineTo(94.33460235595703125f, 141.360260009765625f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse7(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(13.737141609191894531f, 204.0111541748046875f); lineTo(35.658111572265625f, 180.04425048828125f); lineTo(102.2978668212890625f, 144.67840576171875f); lineTo(134.74090576171875f, 139.4173583984375f); moveTo(40.33458709716796875f, 49.10297393798828125f); lineTo(112.2353668212890625f, 142.6324462890625f); lineTo(97.32910919189453125f, 145.8475189208984375f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse8(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(11.75f, 174.50f); lineTo(30.50f, 154.00f); lineTo(87.50f, 123.75f); lineTo(115.25f, 119.25f); moveTo(34.50f, 42.00f); lineTo(96.00f, 122.00f); lineTo(83.25f, 124.75f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse9(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(13.25f, 197.75f); lineTo(34.75f, 174.75f); lineTo(99.0364f, 140.364f); lineTo(99.25f, 140.25f); lineTo(100.167f, 140.096f); lineTo(130.50f, 135.00f); moveTo(39.25f, 47.50f); lineTo(100.167f, 140.096f); lineTo(99.0364f, 140.364f); lineTo(94.25f, 141.50f) }.also { it.fillType = FillType.EVEN_ODD },
            p,
        )
    }

    private fun testCollapse10(canvas: GmCanvas, p: Paint) {
        canvas.drawPath(
            Path { moveTo(5.5f, 36.0f); lineTo(47.5f, 5.0f); lineTo(90.0f, 36.0f); lineTo(88.5f, 36.0f); lineTo(47.5f, 6.0f); lineTo(7.0f, 36.0f) },
            p,
        )
    }
}
