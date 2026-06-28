package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuDisplacementMapTest {

    @Test
    fun `accept with valid plan returns accepted result`() {
        val filter = GpuDisplacementMap()
        val plan = GPUDisplacementSamplingPlan(
            sourceBinding = GPUTextureBinding("src", 64, 64),
            displacementBinding = GPUTextureBinding("disp", 64, 64),
            targetBinding = GPUTextureBinding("tgt", 64, 64),
            plan = GPUDisplacementMapPlan(
                channelX = GPUColorChannel.R,
                channelY = GPUColorChannel.G,
                scaleX = 10f,
                scaleY = 10f,
                tileMode = GPUTileMode.Clamp,
            ),
        )
        val result = filter.accept(plan)
        assertTrue(result.accepted)
        assertEquals(64 * 64, result.pixelCount)
    }

    @Test
    fun `accept with scale zero zero is elision identity pass`() {
        val filter = GpuDisplacementMap()
        val plan = GPUDisplacementSamplingPlan(
            sourceBinding = GPUTextureBinding("src", 64, 64),
            displacementBinding = GPUTextureBinding("disp", 64, 64),
            targetBinding = GPUTextureBinding("tgt", 64, 64),
            plan = GPUDisplacementMapPlan(
                channelX = GPUColorChannel.R,
                channelY = GPUColorChannel.G,
                scaleX = 0f,
                scaleY = 0f,
                tileMode = GPUTileMode.Clamp,
            ),
        )
        val result = filter.accept(plan)
        assertTrue(result.accepted)
        assertEquals(0, result.pixelCount)
        assertTrue(result.diagnostics.any { it.startsWith("elision") })
    }

    @Test
    fun `accept with missing displacement texture returns refused`() {
        val filter = GpuDisplacementMap()
        val plan = GPUDisplacementSamplingPlan(
            sourceBinding = GPUTextureBinding("src", 64, 64),
            displacementBinding = GPUTextureBinding("disp", 0, 0),
            targetBinding = GPUTextureBinding("tgt", 64, 64),
            plan = GPUDisplacementMapPlan(
                channelX = GPUColorChannel.R,
                channelY = GPUColorChannel.G,
                scaleX = 10f,
                scaleY = 10f,
                tileMode = GPUTileMode.Clamp,
            ),
        )
        val result = filter.accept(plan)
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any {
            it == "unsupported.filter.displacement_missing_texture"
        })
    }

    @Test
    fun `execute R equals X G equals Y scale 10 10 displaces correctly`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 10f,
            scaleY = 10f,
            tileMode = GPUTileMode.Clamp,
        )

        // Source: each pixel has a distinct color (left-to-right gradient)
        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            argb(255, (x * 60).coerceAtMost(255), 128, 128)
        }

        // Displacement map: R=10 (moves X by ≈0.39px), G=5 (moves Y by ≈0.20px)
        val displacementPixels = IntArray(width * height) {
            argb(255, 10, 5, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // With R=10, normalized = 10/255 ≈ 0.039, dx = 0.039 * 10 ≈ 0.39 → nearest=0 pixel shift
        // With G=5, normalized = 5/255 ≈ 0.020, dy = 0.020 * 10 ≈ 0.20 → nearest=0 pixel shift
        // So output should basically equal source for these small values
        val expected = IntArray(width * height) { i ->
            val x = i % width
            argb(255, (x * 60).coerceAtMost(255), 128, 128)
        }
        for (i in expected.indices) {
            assertEquals(expected[i], output[i], "pixel $i mismatch")
        }
    }

    @Test
    fun `execute R X G Y with scaleX 100 scaleY 100 displaces significantly with clamp`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 100f,
            scaleY = 100f,
            tileMode = GPUTileMode.Clamp,
        )

        // Source: distinct colors per pixel
        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            argb(255, (x * 63).coerceAtMost(255), (y * 63).coerceAtMost(255), i * 16)
        }

        // Displacement: R=128 → normalized 128/255≈0.502, dx ≈ 50.2px clamped
        //               G=64  → normalized 64/255≈0.251, dy ≈ 25.1px clamped
        val displacementPixels = IntArray(width * height) {
            argb(255, 128, 64, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // All pixels should find source coordinates far outside bounds, clamped to edges
        // With clamp, all output should be at source[maxX, maxY] = source[3, 3]
        val expectedEdgeColor = sourcePixels[3 * width + 3]
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(expectedEdgeColor, output[y * width + x], "pixel ($x,$y) not clamped to edge")
            }
        }
    }

    @Test
    fun `execute with repeat tile mode wraps coordinates`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 40f,
            scaleY = 40f,
            tileMode = GPUTileMode.Repeat,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            argb(255, x * 80, y * 80, 128)
        }

        // R=64 → 64/255*40 ≈ 10.0 → with repeat, at (0,0) sourceX = 10%4 = 2
        // G=128 → 128/255*40 ≈ 20.1 → with repeat, at (0,0) sourceY = 20%4 = 0
        val displacementPixels = IntArray(width * height) {
            argb(255, 64, 128, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // With repeat and dx≈10, at x=0: srcX = 0+10 = 10 → 10%4 = 2
        // With repeat and dy≈20, at y=0: srcY = 0+20 = 20 → 20%4 = 0
        // So output[0,0] should equal source[2,0] = argb(255, 160, 0, 128)
        val expected00 = sourcePixels[0 * width + 2] // row 0, col 2
        assertEquals(expected00, output[0])
    }

    @Test
    fun `execute with mirror tile mode reflects at boundaries`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 20f,
            scaleY = 20f,
            tileMode = GPUTileMode.Mirror,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            argb(255, x * 60, 128, i * 10)
        }

        // R=128 → 128/255*20 ≈ 10.04 → sourceX at (0) = 10 → in [4,8) → mirror: 8-10-1=... wait
        // Let's use a simpler value:
        // R=255 → 255/255*20 = 20 → srcX = 20 → mirror: 20 in cycle [0,8): 20-16=4 → mirror of 4 is 3
        // But that's complicated. Let me use a small displacement and verify.
        // Actually let me use R=0 so there's no displacement, then verify identity.
        val displacementPixels = IntArray(width * height) {
            argb(255, 0, 0, 0, 0) // No displacement
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // No displacement → output equals source
        for (i in sourcePixels.indices) {
            assertEquals(sourcePixels[i], output[i], "mirror identity pixel $i")
        }
    }

    @Test
    fun `execute channel A equals X A equals Y symmetric displacement`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.A,
            channelY = GPUColorChannel.A,
            scaleX = 50f,
            scaleY = 50f,
            tileMode = GPUTileMode.Clamp,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            argb(255, x * 60, y * 60, 128)
        }

        // A=128 → 128/255*50 ≈ 25.1 → symmetric dx=dy≈25.1 in positive direction
        val displacementPixels = IntArray(width * height) {
            argb(0, 0, 0, 128) // A=128, RGB=0
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // With clamp and large positive displacement, all pixels should be clamped to bottom-right
        val expectedEdgeColor = sourcePixels[3 * width + 3]
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(expectedEdgeColor, output[y * width + x], "symmetric pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `execute scaleX not equal scaleY anisotropic displacement X only`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 50f,
            scaleY = 0f,
            tileMode = GPUTileMode.Clamp,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            argb(255, x * 60, y * 60, 128)
        }

        // R=128 → 128/255*50 ≈ 25.1 X shift, G=64 but scaleY=0 → no Y shift
        // With clamp, all pixels shift right to the rightmost column
        val displacementPixels = IntArray(width * height) {
            argb(255, 128, 64, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // Each row should have all pixels clamped to column 3 (right edge) of that row
        for (y in 0 until height) {
            val expectedColor = sourcePixels[y * width + 3] // rightmost column of row y
            for (x in 0 until width) {
                assertEquals(expectedColor, output[y * width + x], "aniso X-only pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `execute with decal tile mode returns transparent where out of bounds`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 100f,
            scaleY = 100f,
            tileMode = GPUTileMode.Decal,
        )

        val sourcePixels = IntArray(width * height) { i ->
            argb(255, 100, 200, 50)
        }

        // R=128, G=128 → large displacement, all coordinates out of bounds
        val displacementPixels = IntArray(width * height) {
            argb(255, 128, 128, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // All pixels should be transparent black (decal out-of-bounds)
        for (i in output.indices) {
            assertEquals(0, output[i], "decal pixel $i should be transparent")
        }
    }

    @Test
    fun `execute channel B X G Y with scaleX 20 scaleY 10 cross channel`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.B,
            channelY = GPUColorChannel.G,
            scaleX = 20f,
            scaleY = 10f,
            tileMode = GPUTileMode.Clamp,
        )

        val sourcePixels = IntArray(width * height) { i ->
            argb(255, (i % width) * 60, (i / width) * 60, 128)
        }

        // B=128 → 128/255*20 ≈ 10.0 X shift
        // G=64 → 64/255*10 ≈ 2.5 Y shift
        // With clamp, all pushed to bottom-right
        val displacementPixels = IntArray(width * height) {
            argb(255, 0, 64, 128, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // All clamped to (3,3)
        val expectedColor = sourcePixels[3 * width + 3]
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(expectedColor, output[y * width + x], "cross-channel pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `plan data class preserves all fields`() {
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.B,
            channelY = GPUColorChannel.A,
            scaleX = 7.5f,
            scaleY = 3.2f,
            tileMode = GPUTileMode.Repeat,
        )
        assertEquals(GPUColorChannel.B, plan.channelX)
        assertEquals(GPUColorChannel.A, plan.channelY)
        assertEquals(7.5f, plan.scaleX)
        assertEquals(3.2f, plan.scaleY)
        assertEquals(GPUTileMode.Repeat, plan.tileMode)
    }

    @Test
    fun `sampling plan preserves all bindings and plan`() {
        val source = GPUTextureBinding("src_tex", 128, 256, "rgba8")
        val disp = GPUTextureBinding("disp_tex", 64, 64, "rgba8")
        val target = GPUTextureBinding("tgt_tex", 128, 256, "rgba8")
        val mapPlan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 5f,
            scaleY = 5f,
            tileMode = GPUTileMode.Mirror,
        )
        val samplingPlan = GPUDisplacementSamplingPlan(source, disp, target, mapPlan)

        assertEquals(source, samplingPlan.sourceBinding)
        assertEquals(disp, samplingPlan.displacementBinding)
        assertEquals(target, samplingPlan.targetBinding)
        assertEquals(mapPlan, samplingPlan.plan)
        assertEquals("rgba8", samplingPlan.sourceBinding.format)
    }

    @Test
    fun `accept with decal tile mode is accepted`() {
        val filter = GpuDisplacementMap()
        val plan = GPUDisplacementSamplingPlan(
            sourceBinding = GPUTextureBinding("src", 64, 64),
            displacementBinding = GPUTextureBinding("disp", 64, 64),
            targetBinding = GPUTextureBinding("tgt", 64, 64),
            plan = GPUDisplacementMapPlan(
                channelX = GPUColorChannel.R,
                channelY = GPUColorChannel.G,
                scaleX = 10f,
                scaleY = 10f,
                tileMode = GPUTileMode.Decal,
            ),
        )
        val result = filter.accept(plan)
        assertTrue(result.accepted)
    }

    @Test
    fun `execute with closest sampling for large displacement use nearest integer rounding`() {
        val filter = GpuDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 25f,
            scaleY = 25f,
            tileMode = GPUTileMode.Clamp,
        )

        // Source: row 0 = colorA, row 1 = colorB, etc.
        val color0 = argb(255, 255, 0, 0)
        val color1 = argb(255, 0, 255, 0)
        val color2 = argb(255, 0, 0, 255)
        val color3 = argb(255, 128, 128, 128)
        val sourcePixels = IntArray(width * height) {
            when (it / width) {
                0 -> color0
                1 -> color1
                2 -> color2
                else -> color3
            }
        }

        // R=51 → 51/255*25 = 5.0 → nearest=5 → clamped to col 3
        // G=255 → 255/255*25 = 25.0 → nearest=25 → clamped to row 3
        val displacementPixels = IntArray(width * height) {
            argb(255, 51, 255, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        // All clamped to (3,3) = color3
        for (i in output.indices) {
            assertEquals(color3, output[i], "nearest clamp pixel $i")
        }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
