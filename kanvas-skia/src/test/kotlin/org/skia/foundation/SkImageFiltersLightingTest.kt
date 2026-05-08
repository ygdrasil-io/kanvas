package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * C1.7 verification suite — lighting (6 variants : 3 diffuse + 3
 * specular).
 *
 * Strategy : the algorithm has many parameters (light position,
 * surface normal, shininess, cone cutoff, …) that combine into one
 * Phong evaluation. We don't try to hit upstream Skia's bytes
 * exactly — instead we use **invariants** that constrain the
 * physics of the model :
 *  - Flat surface (constant α input) ⇒ normal `(0, 0, 1)` ⇒ output
 *    depends only on light z-component.
 *  - Pointing the light straight down (L = (0, 0, 1)) ⇒
 *    `N · L = 1`, output saturates to `kd · lightColor`.
 *  - Light pointing sideways (L = (1, 0, 0)) ⇒ `N · L = 0` for a
 *    flat surface ⇒ output is black.
 *  - Specular with shininess >> 1 ⇒ output narrows compared to
 *    diffuse with the same parameters at the same angle.
 *  - Spot light outside the cutoff cone ⇒ output black ;
 *    inside ⇒ non-zero.
 *  - Output bbox = input bbox.
 */
class SkImageFiltersLightingTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 fully-opaque image (α = 0xFF everywhere) — flat surface. */
    private val flatOpaque: SkImage = SkImage(4, 4, IntArray(16) { 0xFF000000.toInt() })

    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    // ─── DistantLitDiffuse ────────────────────────────────────────

    @Test
    fun `DistantLitDiffuse with light straight down on flat surface saturates`() {
        // L = (0, 0, 1), N = (0, 0, 1), kd = 1, lightColor = white →
        // out = 1 * 1 * white = (255, 255, 255), alpha = 255.
        val filter = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f,
            kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        // Centre pixel (avoid edge sobel artifacts).
        val px = result.image.peekPixel(2, 2)
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        assertTrue(r >= 250, "R=$r should saturate (~255)")
        assertTrue(g >= 250, "G=$g should saturate")
        assertTrue(b >= 250, "B=$b should saturate")
    }

    @Test
    fun `DistantLitDiffuse with sideways light on flat surface is black`() {
        // L = (1, 0, 0) ⇒ N · L = 0 ⇒ out = 0.
        val filter = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(1f, 0f, 0f),
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f,
            kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Centre pixel, away from edges where Sobel might wobble.
        assertEquals(0, result.image.peekPixel(2, 2))
    }

    @Test
    fun `DistantLitDiffuse half kd halves the output`() {
        val full = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(), surfaceScale = 1f,
            kd = 1f, input = SkImageFilters.Image(flatOpaque),
        )
        val half = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(), surfaceScale = 1f,
            kd = 0.5f, input = SkImageFilters.Image(flatOpaque),
        )
        val rFull = (full.filterImage(anyDriver, identity).image.peekPixel(2, 2) ushr 16) and 0xFF
        val rHalf = (half.filterImage(anyDriver, identity).image.peekPixel(2, 2) ushr 16) and 0xFF
        // Allow ±2 byte for rounding ; rHalf should be ~rFull / 2.
        assertTrue(kotlin.math.abs(rHalf - rFull / 2) <= 2, "R: full=$rFull, half=$rHalf")
    }

    // ─── PointLitDiffuse ─────────────────────────────────────────

    @Test
    fun `PointLitDiffuse with light directly above centre is brightest at centre`() {
        // Light at (2, 2, 10) above a 4×4 flat surface → the brightest
        // pixel should be near (2, 2) — straight down means N · L = 1.
        val filter = SkImageFilters.PointLitDiffuse(
            location = floatArrayOf(2f, 2f, 10f),
            lightColor = 0xFFFFFFFF.toInt(), surfaceScale = 1f,
            kd = 1f, input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        val centreR = (result.image.peekPixel(2, 2) ushr 16) and 0xFF
        val cornerR = (result.image.peekPixel(0, 0) ushr 16) and 0xFF
        // At centre the light is closer to "straight down" ; at corner
        // it's slanted, so N · L is smaller. centre > corner.
        assertTrue(centreR > cornerR, "centre=$centreR should be brighter than corner=$cornerR")
    }

    // ─── SpotLitDiffuse ──────────────────────────────────────────

    @Test
    fun `SpotLitDiffuse outside cone is black`() {
        // Spot at (0, 0, 10) aimed at (0, 0, 0) (i.e. straight down at
        // origin), with a tight cone (cutoff = 0.05 rad ~ 3°).
        // Pixel (3, 3) is far from the spot axis → out of cone → black.
        val filter = SkImageFilters.SpotLitDiffuse(
            location = floatArrayOf(0f, 0f, 10f),
            target = floatArrayOf(0f, 0f, 0f),
            falloffExponent = 1f,
            cutoffAngle = 0.05f,
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f, kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0, result.image.peekPixel(3, 3))
    }

    @Test
    fun `SpotLitDiffuse inside cone is non-zero`() {
        // Wide cone : 60 degrees cutoff. Same spot at (0, 0, 10). Centre
        // pixel (2, 2) → angle = atan(sqrt(2² + 2²) / 10) ≈ 16° < 60°,
        // inside cone.
        val filter = SkImageFilters.SpotLitDiffuse(
            location = floatArrayOf(0f, 0f, 10f),
            target = floatArrayOf(0f, 0f, 0f),
            falloffExponent = 1f,
            cutoffAngle = 1.05f, // ~60°
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f, kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(2, 2)
        val r = (px ushr 16) and 0xFF
        assertTrue(r > 0, "centre R=$r should be non-zero (inside cone)")
    }

    // ─── DistantLitSpecular ──────────────────────────────────────

    @Test
    fun `DistantLitSpecular straight down on flat surface peaks`() {
        // L = (0, 0, 1) → H = normalize((0, 0, 1) + (0, 0, 1)) = (0, 0, 1).
        // N · H = 1 → out = ks · 1^shininess · light = ks · light.
        val filter = SkImageFilters.DistantLitSpecular(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f,
            ks = 1f, shininess = 8f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(2, 2)
        val r = (px ushr 16) and 0xFF
        assertTrue(r >= 250, "specular R=$r should saturate at peak")
    }

    @Test
    fun `DistantLitSpecular with high shininess narrows the highlight`() {
        // Compare two specular lights at a slightly off-axis angle :
        // L = (0.5, 0, 0.866) (~30° off vertical). Higher shininess
        // narrows N · H exponent → output drops faster.
        val low = SkImageFilters.DistantLitSpecular(
            direction = floatArrayOf(0.5f, 0f, 0.866f),
            lightColor = 0xFFFFFFFF.toInt(), surfaceScale = 1f,
            ks = 1f, shininess = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val high = SkImageFilters.DistantLitSpecular(
            direction = floatArrayOf(0.5f, 0f, 0.866f),
            lightColor = 0xFFFFFFFF.toInt(), surfaceScale = 1f,
            ks = 1f, shininess = 32f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val rLow = (low.filterImage(anyDriver, identity).image.peekPixel(2, 2) ushr 16) and 0xFF
        val rHigh = (high.filterImage(anyDriver, identity).image.peekPixel(2, 2) ushr 16) and 0xFF
        assertTrue(rHigh < rLow, "high shininess R=$rHigh should be < low shininess R=$rLow")
    }

    // ─── PointLitSpecular & SpotLitSpecular ───────────────────────

    @Test
    fun `PointLitSpecular produces non-zero output`() {
        val filter = SkImageFilters.PointLitSpecular(
            location = floatArrayOf(2f, 2f, 10f),
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f, ks = 1f, shininess = 4f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        val r = (result.image.peekPixel(2, 2) ushr 16) and 0xFF
        assertTrue(r > 0, "PointLitSpecular at centre should be non-zero, got R=$r")
    }

    @Test
    fun `SpotLitSpecular outside cone is black`() {
        val filter = SkImageFilters.SpotLitSpecular(
            location = floatArrayOf(0f, 0f, 10f),
            target = floatArrayOf(0f, 0f, 0f),
            falloffExponent = 1f, cutoffAngle = 0.05f,
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f, ks = 1f, shininess = 4f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0, result.image.peekPixel(3, 3))
    }

    // ─── Output bbox ─────────────────────────────────────────────

    @Test
    fun `Lighting output bbox matches input bbox`() {
        val filter = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(),
            surfaceScale = 1f, kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `Lighting output alpha matches max RGB`() {
        // Phong + Skia convention : `out_a = max(out_r, out_g, out_b)`.
        val filter = SkImageFilters.DistantLitDiffuse(
            direction = floatArrayOf(0f, 0f, 1f),
            lightColor = 0xFFFFFFFF.toInt(), // white, all channels equal
            surfaceScale = 1f, kd = 1f,
            input = SkImageFilters.Image(flatOpaque),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(2, 2)
        val a = (px ushr 24) and 0xFF
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        assertEquals(maxOf(r, g, b), a, "alpha should equal max(R, G, B)")
    }
}
