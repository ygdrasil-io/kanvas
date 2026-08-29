package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle
import org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle

class GpuEvidenceCatalogOracleTest {
    @Test
    fun `radial stroke oracle combines winding clip butt coverage and linear light gradient`() {
        val pixels = oracle("clip-path-triangle-radial-stroke")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(137, 0, 225, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(250, 0, 59, 255))
        assertPixel(pixels, 64, 64, 18, 18, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `translated radial stroke oracle preserves device-space coverage and gradient`() {
        val pixels = oracle("clip-path-translated-triangle-radial-stroke")

        assertPixel(pixels, 64, 64, 9, 7, intArrayOf(137, 0, 225, 255))
        assertPixel(pixels, 64, 64, 17, 15, intArrayOf(250, 0, 59, 255))
        assertPixel(pixels, 64, 64, 20, 18, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `local radial matrix stroke oracle rebases shader sampling without changing coverage`() {
        val pixels = oracle("clip-path-local-radial-matrix-stroke")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(141, 0, 223, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(245, 0, 85, 255))
        assertPixel(pixels, 64, 64, 18, 16, intArrayOf(227, 0, 133, 255))
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `local radial matrix square stroke preserves cap extension`() {
        val pixels = oracle("clip-path-local-radial-matrix-square-stroke")
        val background = intArrayOf(13, 20, 33, 255).toList()

        assertNotEquals(background, pixel(pixels, 64, 64, 7, 7).toList())
        assertNotEquals(background, pixel(pixels, 64, 64, 15, 15).toList())
        assertNotEquals(background, pixel(pixels, 64, 64, 18, 16).toList())
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle radial square stroke oracle preserves rotated device coverage`() {
        val pixels = oracle("clip-path-right-angle-radial-square-stroke")

        assertPixel(pixels, 64, 64, 23, 8, intArrayOf(157, 0, 213, 255))
        assertPixel(pixels, 64, 64, 22, 9, intArrayOf(174, 0, 200, 255))
        assertPixel(pixels, 64, 64, 20, 12, intArrayOf(210, 0, 161, 255))
        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `sweep stroke oracle combines angular sampling with winding clip`() {
        val pixels = oracle("clip-path-sweep-square-stroke")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(99, 0, 240, 255))
        assertPixel(pixels, 64, 64, 18, 16, intArrayOf(251, 0, 50, 255))
        assertPixel(pixels, 64, 64, 20, 12, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `local sweep matrix oracle rebases angular sampling without changing coverage`() {
        val pixels = oracle("clip-path-local-sweep-matrix-stroke")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(161, 0, 210, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(113, 0, 236, 255))
        assertPixel(pixels, 64, 64, 18, 16, intArrayOf(26, 0, 254, 255))
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `even odd sweep stroke oracle preserves the clipped hole`() {
        val pixels = oracle("clip-path-sweep-square-stroke-even-odd-hole")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(244, 0, 87, 255))
        assertPixel(pixels, 64, 64, 29, 5, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `inverse even odd sweep stroke oracle paints only the hole overlap`() {
        val pixels = oracle("clip-path-sweep-square-stroke-inverse-even-odd-hole")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `even odd difference sweep stroke oracle paints only the complement of the shell`() {
        val pixels = oracle("clip-path-sweep-square-stroke-even-odd-difference-hole")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `inverse winding difference sweep stroke leaves the triangle interior`() {
        val pixels = oracle("clip-path-sweep-square-stroke-inverse-winding-difference")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `winding difference sweep stroke paints only the exterior`() {
        val pixels = oracle("clip-path-sweep-square-stroke-winding-difference")

        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(13, 20, 33, 255))
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 18, 18).toList(),
            "Winding Difference exterior sweep stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 25, 25, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `scaled translated inverse winding sweep stroke follows transformed device geometry`() {
        val pixels = oracle("clip-path-sweep-square-stroke-scaled-translated-inverse-winding")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 15, 15, intArrayOf(13, 20, 33, 255))
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 19, 14).toList(),
            "transformed stroke exterior witness must be painted",
        )
    }

    @Test
    fun `butt sweep stroke stops at the segment endpoints through the EvenOdd hole`() {
        val pixels = oracle("clip-path-sweep-butt-stroke-even-odd-hole")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `scaled translated inverse even odd difference sweep keeps the transformed shell`() {
        val pixels = oracle("clip-path-sweep-square-stroke-scaled-translated-inverse-even-odd-difference-hole")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 8, 7).toList(),
            "transformed shell stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 12, 11, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 19, 14, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `inverse winding butt sweep paints the exterior without cap extension`() {
        val pixels = oracle("clip-path-sweep-butt-stroke-inverse-winding")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(13, 20, 33, 255))
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 18, 18).toList(),
            "inverse-Winding exterior stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle sweep oracle keeps the rotated stroke outside the winding clip`() {
        val pixels = oracle("clip-path-sweep-square-stroke-right-angle-winding")

        assertPixel(pixels, 64, 64, 24, 8, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 23, 12, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `winding butt sweep paints the interior without endpoint extension`() {
        val pixels = oracle("clip-path-sweep-butt-stroke-winding")

        assertPixel(pixels, 64, 64, 7, 7, intArrayOf(165, 0, 207, 255))
        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(99, 0, 240, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `winding linear gradient square stroke combines clip coverage and clamp sampling`() {
        val pixels = oracle("clip-path-linear-gradient-square-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 7, 7).toList(),
            "linear-gradient stroke interior witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 16, 15).toList(),
            "linear-gradient stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `winding linear gradient butt stroke combines clip coverage and clamp sampling`() {
        val pixels = oracle("clip-path-linear-gradient-butt-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 7, 7).toList(),
            "linear-gradient butt stroke interior witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 16, 15).toList(),
            "linear-gradient butt stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 4, 7, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `scaled translated winding linear gradient butt stroke follows device geometry`() {
        val pixels = oracle("clip-path-linear-gradient-scaled-translated-butt-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 8, 7).toList(),
            "transformed linear-gradient butt stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 12, 11, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 21, 14, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `winding difference linear gradient butt stroke paints the exterior`() {
        val pixels = oracle("clip-path-linear-gradient-butt-stroke-winding-difference")

        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(13, 20, 33, 255))
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 18, 18).toList(),
            "Difference exterior stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `even odd hole linear gradient butt stroke preserves the hole`() {
        val pixels = oracle("clip-path-linear-gradient-butt-stroke-even-odd-hole")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 7, 7).toList(),
            "EvenOdd shell stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `inverse winding linear gradient butt stroke paints the exterior`() {
        val pixels = oracle("clip-path-linear-gradient-butt-stroke-inverse-winding")

        assertPixel(pixels, 64, 64, 16, 15, intArrayOf(13, 20, 33, 255))
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 18, 18).toList(),
            "inverse-Winding exterior stroke witness must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle winding linear gradient butt stroke follows rotated geometry`() {
        val pixels = oracle("clip-path-linear-gradient-right-angle-butt-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 23, 8).toList(),
            "right-angle linear-gradient stroke witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 20, 12).toList(),
            "right-angle linear-gradient midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle winding linear gradient square stroke follows rotated geometry`() {
        val pixels = oracle("clip-path-linear-gradient-right-angle-square-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 23, 8).toList(),
            "right-angle linear-gradient square stroke witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 20, 12).toList(),
            "right-angle linear-gradient square midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `half turn winding solid stroke follows rotated geometry`() {
        val pixels = oracle("clip-path-half-turn-butt-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 20, 9).toList(),
            "half-turn solid stroke witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 12, 6).toList(),
            "half-turn endpoint witness must be painted",
        )
        assertPixel(pixels, 64, 64, 16, 16, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `diagonal winding square stroke preserves cap extension and clip`() {
        val pixels = oracle("clip-path-diagonal-square-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 8, 10).toList(),
            "diagonal square-cap endpoint witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 15, 15).toList(),
            "diagonal square stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `translated radial square stroke follows device transform and clip`() {
        val pixels = oracle("clip-path-translated-radial-square-stroke-winding")

        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 10, 10).toList(),
            "translated radial square stroke witness must be painted",
        )
        assertNotEquals(
            intArrayOf(13, 20, 33, 255).toList(),
            pixel(pixels, 64, 64, 12, 12).toList(),
            "translated radial square stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 25, 25, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `scaled translated diagonal butt stroke follows device transform and clip`() {
        val pixels = oracle("clip-path-scaled-translated-diagonal-butt-stroke-winding")
        val background = intArrayOf(13, 20, 33, 255).toList()

        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 9, 8).toList(),
            "scaled translated stroke start witness must be painted",
        )
        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 15, 12).toList(),
            "scaled translated stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 22, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle diagonal butt stroke preserves rotated device coverage`() {
        val pixels = oracle("clip-path-right-angle-diagonal-butt-stroke-winding")
        val background = intArrayOf(13, 20, 33, 255).toList()

        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 23, 9).toList(),
            "right-angle stroke start witness must be painted",
        )
        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 19, 18).toList(),
            "right-angle stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 10, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `right angle diagonal square stroke preserves rotated cap extension`() {
        val pixels = oracle("clip-path-right-angle-diagonal-square-stroke-winding")
        val background = intArrayOf(13, 20, 33, 255).toList()

        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 24, 8).toList(),
            "right-angle square-cap endpoint witness must be painted",
        )
        assertNotEquals(
            background,
            pixel(pixels, 64, 64, 19, 18).toList(),
            "right-angle square stroke midpoint must be painted",
        )
        assertPixel(pixels, 64, 64, 10, 20, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `scissored round cap stroke preserves the integral device bounds`() {
        val pixels = oracle("scissored-round-cap-stroke", 32, 32)

        assertPixel(pixels, 32, 32, 6, 15, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 16, 17, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 18, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 5, 19, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `scaled translated horizontal hairline covers one device row`() {
        val pixels = oracle("scaled-translated-horizontal-hairline", 32, 32)

        assertPixel(pixels, 32, 32, 10, 18, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 29, 18, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 30, 18, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 10, 17, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `scaled horizontal hairline preserves the direct device row`() {
        val pixels = oracle("scaled-horizontal-hairline", 32, 32)

        assertPixel(pixels, 32, 32, 8, 16, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 27, 16, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 28, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 8, 15, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `horizontal hairline covers the exact integral device row`() {
        val pixels = oracle("horizontal-hairline", 32, 32)

        assertPixel(pixels, 32, 32, 4, 16, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 27, 16, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 28, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 4, 15, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `vertical butt miter stroke covers only the segment body`() {
        val pixels = oracle("vertical-butt-miter-stroke", 32, 32)

        assertPixel(pixels, 32, 32, 14, 4, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 17, 27, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 13, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 16, 28, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `translated scissored round cap stroke rebases device coverage`() {
        val pixels = oracle("translated-scissored-round-cap-stroke", 32, 32)

        assertPixel(pixels, 32, 32, 9, 17, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 19, 19, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 21, 18, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 8, 21, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `scissored diagonal butt stroke preserves integral device bounds`() {
        val pixels = oracle("scissored-diagonal-butt-stroke", 32, 32)

        assertPixel(pixels, 32, 32, 8, 10, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 14, 15, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 32, 32, 20, 14, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 32, 32, 10, 19, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `bounded bitmap oracle preserves literal nearest texels at its integer destination`() {
        val pixels = oracle("bounded-rgba8-nearest-bitmap")

        assertPixel(pixels, 64, 64, 11, 15, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 12, 16, intArrayOf(17, 34, 51, 255))
        assertPixel(pixels, 64, 64, 13, 16, intArrayOf(221, 204, 187, 255))
        assertPixel(pixels, 64, 64, 14, 16, intArrayOf(119, 136, 153, 255))
        assertPixel(pixels, 64, 64, 12, 17, intArrayOf(68, 85, 102, 255))
        assertPixel(pixels, 64, 64, 13, 17, intArrayOf(16, 32, 48, 255))
        assertPixel(pixels, 64, 64, 14, 17, intArrayOf(170, 187, 204, 255))
        assertPixel(pixels, 64, 64, 15, 18, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `translucent overlap oracle matches literal premultiplied src-over pixels`() {
        val pixels = oracle("translucent-card-overlap")

        assertPixel(pixels, 64, 64, 2, 2, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 12, 12, intArrayOf(46, 94, 142, 255))
        assertPixel(pixels, 64, 64, 50, 50, intArrayOf(93, 48, 33, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(98, 81, 105, 255))
    }

    @Test
    fun `scissor oracle leaves clipped pixels untouched and paints literal intersection`() {
        val pixels = oracle("scissor-overlay")

        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(31, 115, 209, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(242, 135, 46, 255))
    }

    @Test
    fun `restore to count oracle preserves parent child and post restore sentinels`() {
        val pixels = oracle("canvas-state-restore-to-count")

        assertPixel(pixels, 64, 64, 30, 9, intArrayOf(31, 115, 209, 255))
        assertPixel(pixels, 64, 64, 15, 21, intArrayOf(242, 135, 46, 255))
        assertPixel(pixels, 64, 64, 21, 21, intArrayOf(31, 115, 209, 255))
        assertPixel(pixels, 64, 64, 45, 9, intArrayOf(255, 255, 255, 255))
        assertPixel(pixels, 64, 64, 7, 9, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `stroke oracle paints only the four literal coverage bands`() {
        val pixels = oracle("stroke-rect-outline")
        val background = intArrayOf(13, 20, 33, 255)
        val stroke = intArrayOf(242, 135, 46, 255)

        assertPixel(pixels, 64, 64, 12, 12, background)
        assertPixel(pixels, 64, 64, 30, 30, background)
        assertPixel(pixels, 64, 64, 14, 14, stroke)
        assertPixel(pixels, 64, 64, 14, 46, stroke)
        assertPixel(pixels, 64, 64, 14, 30, stroke)
        assertPixel(pixels, 64, 64, 46, 30, stroke)
    }

    @Test
    fun `gradient oracles preserve literal clamp endpoints and transparent exterior`() {
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 7, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 8, 16, intArrayOf(255, 56, 56, 255))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 32, 16, intArrayOf(189, 90, 192, 255))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 55, 16, intArrayOf(56, 112, 255, 255))

        assertPixel(oracle("radial-swatch"), 64, 64, 7, 8, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("radial-swatch"), 64, 64, 32, 32, intArrayOf(255, 232, 72, 255))
        assertPixel(oracle("radial-swatch"), 64, 64, 44, 32, intArrayOf(188, 176, 149, 255))

        assertPixel(oracle("sweep-disk"), 64, 64, 7, 8, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("sweep-disk"), 64, 64, 48, 32, intArrayOf(255, 64, 64, 255))
        assertPixel(oracle("sweep-disk"), 64, 64, 32, 48, intArrayOf(226, 122, 146, 255))
    }

    @Test
    fun `sweep stroke oracle samples pixel-center angles across four disjoint bands`() {
        val pixels = oracle("sweep-gradient-two-stop-stroke-rect")

        assertPixel(pixels, 64, 64, 30, 15, intArrayOf(148, 101, 224, 255))
        assertPixel(pixels, 64, 64, 30, 48, intArrayOf(223, 76, 149, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 5, 15, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `three stop sweep stroke oracle samples each gradient interval across four disjoint bands`() {
        val pixels = oracle("sweep-gradient-three-stop-stroke-rect")

        assertPixel(pixels, 64, 64, 30, 15, intArrayOf(56, 181, 198, 255))
        assertPixel(pixels, 64, 64, 30, 48, intArrayOf(184, 170, 97, 255))
        assertPixel(pixels, 64, 64, 55, 30, intArrayOf(56, 117, 252, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 5, 15, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `translated two stop linear stroke oracle rebases its axis across four device bands`() {
        val pixels = oracle("linear-gradient-two-stop-translated-stroke-rect")

        assertPixel(pixels, 64, 64, 30, 18, intArrayOf(202, 85, 179, 255))
        assertPixel(pixels, 64, 64, 30, 51, intArrayOf(202, 85, 179, 255))
        assertPixel(pixels, 64, 64, 31, 33, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 7, 17, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `translated three stop linear stroke oracle rebases its axis across four device bands`() {
        val pixels = oracle("linear-gradient-three-stop-translated-stroke-rect")

        assertPixel(pixels, 64, 64, 8, 17, intArrayOf(255, 56, 56, 255))
        assertPixel(pixels, 64, 64, 59, 17, intArrayOf(56, 112, 255, 255))
        assertPixel(pixels, 64, 64, 31, 33, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 7, 17, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `uniform scaled two stop linear stroke oracle scales coverage and rebases its axis`() {
        val pixels = oracle("linear-gradient-two-stop-uniform-scaled-stroke-rect")

        assertPixel(pixels, 64, 64, 16, 18, intArrayOf(255, 56, 56, 255))
        assertPixel(pixels, 64, 64, 59, 18, intArrayOf(56, 112, 255, 255))
        assertPixel(pixels, 64, 64, 30, 36, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 15, 18, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `uniform scaled two stop linear stroke oracle rejects a degenerate scaled axis`() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle(
                List(4) { SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Rect(0, 0, 1, 1) },
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
                2,
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Point(2.0, 4.0),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Stop(255, 56, 56),
                SurfaceSrgbScaledTwoStopLinearGradientStrokeCpuOracle.Stop(56, 112, 255),
            )
        }
    }

    @Test
    fun `uniform scaled three stop linear stroke oracle scales coverage and retains both intervals`() {
        val pixels = oracle("linear-gradient-three-stop-uniform-scaled-stroke-rect")

        assertPixel(pixels, 64, 64, 16, 18, intArrayOf(255, 56, 56, 255))
        assertPixel(pixels, 64, 64, 59, 18, intArrayOf(56, 112, 255, 255))
        val midpointOffset = (18 * 64 + 37) * 4
        val midpoint = pixels.copyOfRange(midpointOffset, midpointOffset + 4).map { it.toInt() and 0xff }
        assertNotEquals(intArrayOf(255, 56, 56, 255).toList(), midpoint)
        assertNotEquals(intArrayOf(56, 112, 255, 255).toList(), midpoint)
        assertPixel(pixels, 64, 64, 30, 36, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 15, 18, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `uniform scaled three stop linear stroke oracle rejects a degenerate scaled axis`() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle(
                List(4) { SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Rect(0, 0, 1, 1) },
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(8.0, 16.0),
                2,
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Point(2.0, 4.0),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(255, 56, 56),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(56, 220, 120),
                SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle.Stop(56, 112, 255),
            )
        }
    }

    @Test
    fun `three stop radial stroke oracle samples pixel-center distance across four disjoint bands`() {
        val pixels = oracle("radial-gradient-three-stop-stroke-rect")

        assertPixel(pixels, 64, 64, 30, 15, intArrayOf(56, 181, 197, 255))
        assertPixel(pixels, 64, 64, 30, 48, intArrayOf(56, 189, 186, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 64, 64, 5, 15, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `wave two oracles preserve hand-derived gradient affine and clip pixels`() {
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 48, 32, intArrayOf(255, 64, 64, 255))
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 32, 48, intArrayOf(236, 107, 126, 255))
        // Pixel centres make (16,17) cross the sloped left edge: its top-left
        // corner maps outside, while its centre maps to local x = 8.125.
        assertPixel(oracle("affine-solid-rect"), 64, 64, 15, 15, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 15, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 16, 16, intArrayOf(242, 135, 46, 255))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 16, 17, intArrayOf(242, 135, 46, 255))
        // The right edge is half-open: at this row, the pixel centre maps to
        // local x = 40.125, so it remains clear even though its corner is in.
        assertPixel(oracle("affine-solid-rect"), 64, 64, 48, 17, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 19, 12, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 20, 12, intArrayOf(54, 83, 191, 255))
    }

    @Test
    fun `rrect and drrect oracles preserve literal device coverage and fill counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val scaledRRect = oracle("scaled-solid-rrect")
        val drrect = oracle("solid-drrect-hole")

        assertPixel(scaledRRect, 64, 64, 15, 16, background)
        assertPixel(scaledRRect, 64, 64, 16, 16, background)
        assertPixel(scaledRRect, 64, 64, 24, 16, orange)
        assertPixel(scaledRRect, 64, 64, 32, 32, orange)
        assertPixel(scaledRRect, 64, 64, 47, 47, background)
        assertEquals(996, fillPixelCount(scaledRRect, orange))

        assertPixel(drrect, 64, 64, 8, 8, background)
        assertPixel(drrect, 64, 64, 12, 12, blue)
        assertPixel(drrect, 64, 64, 20, 20, blue)
        assertPixel(drrect, 64, 64, 32, 32, background)
        assertPixel(drrect, 64, 64, 44, 32, blue)
        assertPixel(drrect, 64, 64, 55, 55, background)
        assertEquals(1692, fillPixelCount(drrect, blue))
    }

    @Test
    fun `advanced rrect oracles preserve independent corner coverage and fill counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val asymmetric = oracle("asymmetric-solid-rrect")
        val ellipse = oracle("ellipse-solid-rrect")
        val drrect = oracle("asymmetric-solid-drrect-hole")

        assertPixel(asymmetric, 64, 64, 10, 8, background)
        assertPixel(asymmetric, 64, 64, 11, 8, orange)
        assertPixel(asymmetric, 64, 64, 50, 8, orange)
        assertPixel(asymmetric, 64, 64, 51, 8, background)
        assertPixel(asymmetric, 64, 64, 10, 55, background)
        assertPixel(asymmetric, 64, 64, 11, 55, orange)
        assertPixel(asymmetric, 64, 64, 49, 55, orange)
        assertPixel(asymmetric, 64, 64, 50, 55, background)
        assertEquals(2265, fillPixelCount(asymmetric, orange))

        assertPixel(ellipse, 64, 64, 25, 20, background)
        assertPixel(ellipse, 64, 64, 26, 20, blue)
        assertPixel(ellipse, 64, 64, 37, 20, blue)
        assertPixel(ellipse, 64, 64, 38, 20, background)
        assertPixel(ellipse, 64, 64, 12, 32, blue)
        assertPixel(ellipse, 64, 64, 52, 32, background)
        assertEquals(764, fillPixelCount(ellipse, blue))

        assertPixel(drrect, 64, 64, 20, 20, blue)
        assertPixel(drrect, 64, 64, 21, 20, background)
        assertPixel(drrect, 64, 64, 41, 20, background)
        assertPixel(drrect, 64, 64, 42, 20, blue)
        assertPixel(drrect, 64, 64, 20, 43, blue)
        assertPixel(drrect, 64, 64, 21, 43, background)
        assertPixel(drrect, 64, 64, 41, 43, background)
        assertPixel(drrect, 64, 64, 42, 43, blue)
        assertEquals(1889, fillPixelCount(drrect, blue))
    }

    @Test
    fun `hard clip rrect oracles preserve clip membership and ordered band counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val solid = oracle("clip-rrect-solid")
        val ellipse = oracle("clip-rrect-ellipse")
        val bands = oracle("clip-rrect-two-bands")

        assertPixel(solid, 64, 64, 8, 8, background)
        assertPixel(solid, 64, 64, 13, 8, blue)
        assertPixel(solid, 64, 64, 56, 32, background)
        assertEquals(2256, fillPixelCount(solid, blue))

        assertPixel(ellipse, 64, 64, 25, 20, background)
        assertPixel(ellipse, 64, 64, 26, 20, orange)
        assertPixel(ellipse, 64, 64, 38, 20, background)
        assertEquals(764, fillPixelCount(ellipse, orange))

        assertPixel(bands, 64, 64, 31, 32, blue)
        assertPixel(bands, 64, 64, 32, 32, orange)
        assertEquals(1128, fillPixelCount(bands, blue))
        assertEquals(1128, fillPixelCount(bands, orange))
    }

    @Test
    fun `hard clip path oracles preserve literal winding notch and ordered band counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val triangle = oracle("clip-path-triangle-solid")
        val difference = oracle("clip-path-triangle-difference-solid")
        val concave = oracle("clip-path-concave-solid")
        val bands = oracle("clip-path-triangle-two-bands")

        assertPixel(triangle, 64, 64, 8, 8, orange)
        assertPixel(triangle, 64, 64, 55, 8, background)
        assertPixel(triangle, 64, 64, 31, 31, orange)
        assertPixel(triangle, 64, 64, 32, 31, background)
        assertEquals(1128, fillPixelCount(triangle, orange))

        assertPixel(difference, 64, 64, 12, 12, background)
        assertPixel(difference, 64, 64, 60, 60, orange)
        assertEquals(2968, fillPixelCount(difference, orange))

        assertPixel(concave, 64, 64, 10, 10, blue)
        assertPixel(concave, 64, 64, 40, 30, background)
        assertPixel(concave, 64, 64, 40, 44, blue)
        assertEquals(1920, fillPixelCount(concave, blue))

        assertPixel(bands, 64, 64, 31, 31, blue)
        assertPixel(bands, 64, 64, 32, 8, orange)
        assertPixel(bands, 64, 64, 32, 31, background)
        assertEquals(852, fillPixelCount(bands, blue))
        assertEquals(276, fillPixelCount(bands, orange))
    }

    @Test
    fun `hard clip direct triangle oracles preserve device clip translation and paint order`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val solid = oracle("clip-path-triangle-direct-triangle-solid")
        val translated = oracle("clip-path-translated-triangle-direct-triangle-solid")
        val ordered = oracle("clip-path-triangle-direct-triangle-order")

        assertPixel(solid, 64, 64, 12, 12, orange)
        assertPixel(solid, 64, 64, 50, 14, background)
        assertEquals(1059, fillPixelCount(solid, orange))

        assertPixel(translated, 64, 64, 14, 12, blue)
        assertPixel(translated, 64, 64, 8, 12, background)
        assertEquals(1059, fillPixelCount(translated, blue))

        assertPixel(ordered, 64, 64, 12, 12, blue)
        assertPixel(ordered, 64, 64, 24, 12, orange)
        assertPixel(ordered, 64, 64, 50, 14, background)
        assertEquals(465, fillPixelCount(ordered, blue))
        assertEquals(630, fillPixelCount(ordered, orange))
    }

    @Test
    fun `hard clip direct triangle clamp gradient oracles preserve device geometry and counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val identity = oracle("clip-path-triangle-direct-triangle-linear-gradient")
        val translated = oracle("clip-path-translated-triangle-direct-triangle-linear-gradient")
        val scaled = oracle("clip-path-uniform-scaled-triangle-direct-triangle-linear-gradient")

        listOf(20 to 1, 21 to 2, 22 to 3).forEach { (y, channel) ->
            assertInteriorPixel(identity, 20, y, intArrayOf(channel, channel, channel, 255))
        }
        assertPixel(identity, 64, 64, 20, 18, intArrayOf(0, 0, 0, 255))
        assertPixel(identity, 64, 64, 20, 23, intArrayOf(4, 4, 4, 255))
        assertPixel(identity, 64, 64, 50, 14, background)
        assertEquals(1059, paintedPixelCount(identity, background))

        listOf(20 to 1, 21 to 2, 22 to 3).forEach { (y, channel) ->
            assertInteriorPixel(translated, 22, y, intArrayOf(channel, channel, channel, 255))
        }
        assertPixel(translated, 64, 64, 22, 18, intArrayOf(0, 0, 0, 255))
        assertPixel(translated, 64, 64, 22, 23, intArrayOf(4, 4, 4, 255))
        assertPixel(translated, 64, 64, 8, 12, background)
        assertEquals(1059, paintedPixelCount(translated, background))

        listOf(19 to 1, 20 to 2, 21 to 3).forEach { (y, channel) ->
            assertInteriorPixel(scaled, 22, y, intArrayOf(channel, channel, channel, 255))
        }
        assertPixel(scaled, 64, 64, 22, 17, intArrayOf(0, 0, 0, 255))
        assertPixel(scaled, 64, 64, 22, 22, intArrayOf(4, 4, 4, 255))
        assertPixel(scaled, 64, 64, 13, 11, background)
        assertEquals(592, paintedPixelCount(scaled, background))
    }

    @Test
    fun `path fill oracles preserve literal winding and inverse coverage`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val green = intArrayOf(56, 220, 120, 255)
        val triangle = oracle("solid-triangle-path")
        val concave = oracle("solid-concave-path")
        val evenOdd = oracle("even-odd-path-hole")
        val windingHole = oracle("winding-path-hole")
        val inverseWinding = oracle("inverse-winding-triangle-path")
        val inverseEvenOdd = oracle("inverse-even-odd-path-hole")
        val bowTie = oracle("even-odd-bow-tie-path")

        assertPixel(triangle, 64, 64, 8, 8, orange)
        assertPixel(triangle, 64, 64, 55, 8, background)
        assertPixel(triangle, 64, 64, 31, 31, orange)
        assertPixel(triangle, 64, 64, 32, 31, background)
        assertEquals(1128, fillPixelCount(triangle, orange))

        assertPixel(concave, 64, 64, 10, 10, blue)
        assertPixel(concave, 64, 64, 40, 30, background)
        assertPixel(concave, 64, 64, 40, 44, blue)
        assertEquals(1920, fillPixelCount(concave, blue))

        assertPixel(evenOdd, 64, 64, 10, 10, green)
        assertPixel(evenOdd, 64, 64, 22, 20, background)
        assertPixel(evenOdd, 64, 64, 30, 30, background)
        assertPixel(evenOdd, 64, 64, 44, 30, green)
        assertEquals(1776, fillPixelCount(evenOdd, green))

        assertPixel(windingHole, 64, 64, 10, 10, blue)
        assertPixel(windingHole, 64, 64, 30, 30, background)
        assertPixel(windingHole, 64, 64, 44, 30, blue)
        assertEquals(1776, fillPixelCount(windingHole, blue))

        assertPixel(inverseWinding, 64, 64, 4, 4, orange)
        assertPixel(inverseWinding, 64, 64, 8, 8, background)
        assertPixel(inverseWinding, 64, 64, 31, 31, background)
        assertPixel(inverseWinding, 64, 64, 55, 8, orange)
        assertEquals(2968, fillPixelCount(inverseWinding, orange))

        assertPixel(inverseEvenOdd, 64, 64, 4, 4, green)
        assertPixel(inverseEvenOdd, 64, 64, 10, 10, background)
        assertPixel(inverseEvenOdd, 64, 64, 30, 30, green)
        assertPixel(inverseEvenOdd, 64, 64, 44, 30, background)
        assertEquals(2320, fillPixelCount(inverseEvenOdd, green))

        assertPixel(bowTie, 64, 64, 16, 16, green)
        assertPixel(bowTie, 64, 64, 32, 32, background)
        assertPixel(bowTie, 64, 64, 16, 48, green)

    }

    @Test
    fun `closure and transform path oracles preserve hand-derived device triangles`() {
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val green = intArrayOf(56, 220, 120, 255)

        listOf(
            Triple("implicit-closure-triangle-path", orange, 1128 to listOf(8, 8, 55, 8, 31, 31, 32, 31)),
            Triple("translated-triangle-path", blue, 1128 to listOf(12, 13, 60, 13, 35, 36, 50, 40)),
            Triple("uniform-scaled-triangle-path", green, 1128 to listOf(12, 12, 60, 12, 35, 35, 50, 40)),
        ).forEach { (id, color, expectation) ->
            val (count, samples) = expectation
            val pixels = oracle(id)
            assertPixel(pixels, 64, 64, samples[0], samples[1], color)
            assertPixel(pixels, 64, 64, samples[2], samples[3], intArrayOf(13, 20, 33, 255))
            assertPixel(pixels, 64, 64, samples[4], samples[5], color)
            assertPixel(pixels, 64, 64, samples[6], samples[7], intArrayOf(13, 20, 33, 255))
            assertEquals(count, fillPixelCount(pixels, color), id)
        }
        val scaled = oracle("uniform-scaled-triangle-path")
        assertPixel(scaled, 64, 64, 59, 12, intArrayOf(13, 20, 33, 255))
    }

    @Test
    fun `inverse translated rrect oracles preserve complement witnesses and counts`() {
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val background = intArrayOf(0, 0, 0, 0)
        listOf(
            Triple("clip-path-inverse-axis-x-translated-solid-rrect", orange, Triple(40 to 30, 20 to 20, 784)),
            Triple("clip-path-inverse-axis-y-translated-asymmetric-solid-rrect", blue, Triple(40 to 32, 20 to 20, 835)),
            Triple("clip-path-inverse-negative-x-translated-ellipse-solid-rrect", orange, Triple(33 to 37, 20 to 32, 413)),
            Triple("clip-path-inverse-negative-y-translated-solid-rrect", orange, Triple(45 to 27, 24 to 20, 789)),
        ).forEach { (id, color, expectation) ->
            val pixels = oracle(id)
            assertPixel(pixels, 64, 64, expectation.first.first, expectation.first.second, color)
            assertPixel(pixels, 64, 64, expectation.second.first, expectation.second.second, background)
            assertEquals(expectation.third, fillPixelCount(pixels, color), id)
        }
    }

    private fun oracle(id: String, width: Int = 64, height: Int = 64): ByteArray = assertNotNull(
        GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id }?.oracle,
    ).render(width, height)

    private fun paintedPixelCount(pixels: ByteArray, background: IntArray): Int =
        pixels.asList().chunked(4).count { pixel ->
            pixel.map { it.toInt() and 0xff } != background.toList()
        }

    private fun assertPixel(pixels: ByteArray, width: Int, height: Int, x: Int, y: Int, expected: IntArray) {
        require(x in 0 until width && y in 0 until height)
        val offset = (y * width + x) * 4
        assertEquals(4, expected.size)
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }

    private fun pixel(pixels: ByteArray, width: Int, height: Int, x: Int, y: Int): IntArray {
        require(x in 0 until width && y in 0 until height)
        val offset = (y * width + x) * 4
        return IntArray(4) { pixels[offset + it].toInt() and 0xff }
    }

    private fun assertInteriorPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        assertPixel(pixels, 64, 64, x, y, expected)
        assertNotEquals(intArrayOf(0, 0, 0, 255).toList(), expected.toList(), "interior pixel must differ from start")
        assertNotEquals(intArrayOf(4, 4, 4, 255).toList(), expected.toList(), "interior pixel must differ from end")
    }

    private fun fillPixelCount(pixels: ByteArray, color: IntArray): Int =
        pixels.asList().chunked(4).count { pixel -> pixel.map { it.toInt() and 0xff } == color.toList() }
}
