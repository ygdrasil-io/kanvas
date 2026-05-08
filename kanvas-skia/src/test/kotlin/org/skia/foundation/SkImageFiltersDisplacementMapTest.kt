package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * C1.5 verification suite — DisplacementMap.
 *
 * Algorithm under test (per output pixel `(x, y)`) :
 *  1. Read the displacement filter's pixel.
 *  2. Extract the configured X / Y channels as bytes ∈ `[0, 255]`.
 *  3. Convert to floats `c ∈ [0, 1]`, centre at zero (`c - 0.5`),
 *     multiply by `scale` to get an offset `(dx, dy)`.
 *  4. Sample the colour filter at `(x + dx, y + dy)` with nearest-
 *     neighbour ; OOB reads return transparent black.
 *
 * Coverage :
 *  - Mid-grey `(0x80)` displacement → centred channel value ≈ 0
 *    → identity sampling.
 *  - Saturated `(0xFF)` red on the X channel → `+scale/2` shift on
 *    that axis ; saturated `(0x00)` → `-scale/2` shift.
 *  - Per-pixel constant displacement ⇒ uniform offset that we can
 *    spot-check by colour identity.
 */
class SkImageFiltersDisplacementMapTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image with cells colour-coded by `(x, y)` so we can detect shifts. */
    private val rainbowImg: SkImage = SkImage(
        4, 4,
        IntArray(16) { i ->
            val x = i % 4; val y = i / 4
            // (R, G, B) = (x*64, y*64, 0xFF) : column → red, row → green.
            (0xFF shl 24) or ((x * 64).coerceAtMost(255) shl 16) or
                ((y * 64).coerceAtMost(255) shl 8) or 0xFF
        },
    )

    /** Mid-grey displacement (R=G=B=A=0x80). All channels centre to 0. */
    private val midGreyDisplacement: SkImage = SkImage(4, 4, IntArray(16) { 0x80808080.toInt() })

    /** Pure red displacement (R=0xFF, G=0, B=0, A=0xFF). */
    private val redDisplacement: SkImage = SkImage(4, 4, IntArray(16) { 0xFFFF0000.toInt() })

    /** Pure black opaque displacement (R=G=B=0x00, A=0xFF). */
    private val blackDisplacement: SkImage = SkImage(4, 4, IntArray(16) { 0xFF000000.toInt() })

    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    @Test
    fun `mid-grey displacement is an identity (no offset)`() {
        // Every channel = 0x80 → c = 128/255 ≈ 0.502, c - 0.5 ≈ 0.002.
        // With scale=10, the offset is ≈ 0.02 px → rounds to 0.
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kR,
            yChannelSelector = SkColorChannel.kG,
            scale = 10f,
            displacement = SkImageFilters.Image(midGreyDisplacement),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(
                rainbowImg.peekPixel(x, y),
                result.image.peekPixel(x, y),
                "expected identity at ($x, $y)",
            )
        }
    }

    @Test
    fun `saturated-red X displacement shifts by plus half scale`() {
        // R = 0xFF → c = 1.0, c - 0.5 = 0.5. With scale=4, offset = +2.
        // Output pixel (x, y) reads colour at (x + 2, y). For x ∈ {0, 1}
        // the source x is 2 or 3 (in-bounds). For x ∈ {2, 3} the source
        // x is 4 or 5 (OOB) → transparent black.
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kR,
            yChannelSelector = SkColorChannel.kG, // G of red displacement = 0 → -2 shift
            scale = 4f,
            displacement = SkImageFilters.Image(redDisplacement),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)

        // Expected behaviour : output (x, y) = rainbow(x + 2, y - 2)
        // since redDisplacement.G = 0 → c = 0, c - 0.5 = -0.5, scale=4 → -2.
        for (oy in 0 until 4) for (ox in 0 until 4) {
            val sx = ox + 2
            val sy = oy - 2
            val expected = if (sx in 0 until 4 && sy in 0 until 4)
                rainbowImg.peekPixel(sx, sy) else 0
            assertEquals(expected, result.image.peekPixel(ox, oy), "at ($ox, $oy)")
        }
    }

    @Test
    fun `black displacement shifts by minus half scale on both axes`() {
        // R = 0, G = 0 → c = 0, c - 0.5 = -0.5. With scale=2, offset = (-1, -1).
        // Output (x, y) → colour at (x - 1, y - 1).
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kR,
            yChannelSelector = SkColorChannel.kG,
            scale = 2f,
            displacement = SkImageFilters.Image(blackDisplacement),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (oy in 0 until 4) for (ox in 0 until 4) {
            val sx = ox - 1
            val sy = oy - 1
            val expected = if (sx in 0 until 4 && sy in 0 until 4)
                rainbowImg.peekPixel(sx, sy) else 0
            assertEquals(expected, result.image.peekPixel(ox, oy), "at ($ox, $oy)")
        }
    }

    @Test
    fun `zero-scale collapses to identity regardless of displacement`() {
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kR,
            yChannelSelector = SkColorChannel.kG,
            scale = 0f,
            displacement = SkImageFilters.Image(redDisplacement),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(
                rainbowImg.peekPixel(x, y),
                result.image.peekPixel(x, y),
                "expected identity at ($x, $y) with scale=0",
            )
        }
    }

    @Test
    fun `output bbox matches color filter bbox`() {
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kR,
            yChannelSelector = SkColorChannel.kG,
            scale = 5f,
            displacement = SkImageFilters.Image(midGreyDisplacement),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Output dimensions track colour, not displacement (both 4×4 here).
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `alpha channel can drive displacement`() {
        // alphaDisplacement : full 4×4 grid, every pixel α = 0xFF.
        // Using kA on both axes : c = 1.0, c - 0.5 = 0.5, scale=2 → offset (+1, +1).
        val alphaImg = SkImage(4, 4, IntArray(16) { 0xFF000000.toInt() })
        val filter = SkImageFilters.DisplacementMap(
            xChannelSelector = SkColorChannel.kA,
            yChannelSelector = SkColorChannel.kA,
            scale = 2f,
            displacement = SkImageFilters.Image(alphaImg),
            color = SkImageFilters.Image(rainbowImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Output (x, y) = rainbow(x + 1, y + 1).
        for (oy in 0 until 4) for (ox in 0 until 4) {
            val sx = ox + 1; val sy = oy + 1
            val expected = if (sx in 0 until 4 && sy in 0 until 4)
                rainbowImg.peekPixel(sx, sy) else 0
            assertEquals(expected, result.image.peekPixel(ox, oy), "at ($ox, $oy)")
        }
    }
}
