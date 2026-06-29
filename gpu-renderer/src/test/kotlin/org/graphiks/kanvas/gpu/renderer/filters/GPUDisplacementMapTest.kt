package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUDisplacementMapTest {

    @Test
    fun `accept with valid plan returns accepted result`() {
        val filter = GPUDisplacementMap()
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
        val filter = GPUDisplacementMap()
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
        assertTrue(result.diagnostics.any { it.code == "elision.identity_pass" })
    }

    @Test
    fun `accept with missing displacement texture returns refused`() {
        val filter = GPUDisplacementMap()
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
            it.code == "unsupported.filter.displacement_missing_texture"
        })
        assertTrue(result.diagnostics.all { it.terminal })
    }

    @Test
    fun `execute R equals X G equals Y scale 10 10 displaces by R 10 G 5`() {
        val filter = GPUDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 10f,
            scaleY = 10f,
            tileMode = GPUTileMode.Clamp,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            color(x * 60, 128, 128)
        }
        val displacementPixels = IntArray(width * height) {
            color(10, 5, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        for (i in sourcePixels.indices) {
            assertEquals(sourcePixels[i], output[i], "pixel $i mismatch with small displacement")
        }
    }

    @Test
    fun `execute scaleX 100 scaleY 100 with clamp produces all edge clamped`() {
        val filter = GPUDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 100f,
            scaleY = 100f,
            tileMode = GPUTileMode.Clamp,
        )

        val sourcePixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            color((x * 63).coerceAtMost(255), (y * 63).coerceAtMost(255), (i * 16).coerceAtMost(255))
        }
        val displacementPixels = IntArray(width * height) {
            color(128, 64, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        val expectedEdgeColor = sourcePixels[3 * width + 3]
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(expectedEdgeColor, output[y * width + x], "pixel ($x,$y) not clamped to edge")
            }
        }
    }

    @Test
    fun `execute with repeat tile mode wraps coordinates`() {
        val filter = GPUDisplacementMap()
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
            color(x * 80, y * 80, 128)
        }
        val displacementPixels = IntArray(width * height) {
            color(64, 128, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        val expected00 = sourcePixels[0 * width + 2]
        assertEquals(expected00, output[0])
    }

    @Test
    fun `execute with mirror tile mode zero displacement identity`() {
        val filter = GPUDisplacementMap()
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
            color(x * 60, 128, (i * 10).coerceAtMost(255))
        }
        val displacementPixels = IntArray(width * height) {
            color(0, 0, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        for (i in sourcePixels.indices) {
            assertEquals(sourcePixels[i], output[i], "mirror identity pixel $i")
        }
    }

    @Test
    fun `execute channel A equals X A equals Y symmetric displacement`() {
        val filter = GPUDisplacementMap()
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
            color(x * 60, y * 60, 128)
        }
        // A=128, R=0, G=0, B=0 — alpha channel drives symmetric X/Y displacement
        val displacementPixels = IntArray(width * height) {
            ((128 and 0xFF) shl 24) or ((0 and 0xFF) shl 16) or ((0 and 0xFF) shl 8) or (0 and 0xFF)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        val expectedEdgeColor = sourcePixels[3 * width + 3]
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(expectedEdgeColor, output[y * width + x], "symmetric pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `execute scaleX not equal scaleY anisotropic X-only displacement`() {
        val filter = GPUDisplacementMap()
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
            color(x * 60, y * 60, 128)
        }
        val displacementPixels = IntArray(width * height) {
            color(128, 64, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        for (y in 0 until height) {
            val expectedColor = sourcePixels[y * width + 3]
            for (x in 0 until width) {
                assertEquals(expectedColor, output[y * width + x], "aniso X-only pixel ($x,$y)")
            }
        }
    }

    @Test
    fun `execute with decal tile mode returns transparent where out of bounds`() {
        val filter = GPUDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 100f,
            scaleY = 100f,
            tileMode = GPUTileMode.Decal,
        )

        val sourcePixels = IntArray(width * height) {
            color(100, 200, 50)
        }
        val displacementPixels = IntArray(width * height) {
            color(128, 128, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        for (i in output.indices) {
            assertEquals(0, output[i], "decal pixel $i should be transparent")
        }
    }

    @Test
    fun `execute channel B X G Y with scaleX 20 scaleY 10 cross channel`() {
        val filter = GPUDisplacementMap()
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
            color((i % width) * 60, (i / width) * 60, 128)
        }
        val displacementPixels = IntArray(width * height) {
            color(0, 64, 128)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
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
        val filter = GPUDisplacementMap()
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
    fun `accept with unsupported tile mode produces refusal diagnostic`() {
        val filter = GPUDisplacementMap()
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
        val result = filter.accept(plan, supportedTileModes = setOf(GPUTileMode.Clamp, GPUTileMode.Repeat))
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.code == "unsupported.filter.displacement_tile_mode_unsupported" })
    }

    @Test
    fun `execute nearest integer rounding for displacement sampling`() {
        val filter = GPUDisplacementMap()
        val width = 4
        val height = 4
        val plan = GPUDisplacementMapPlan(
            channelX = GPUColorChannel.R,
            channelY = GPUColorChannel.G,
            scaleX = 25f,
            scaleY = 25f,
            tileMode = GPUTileMode.Clamp,
        )

        val color0 = color(255, 0, 0)
        val color1 = color(0, 255, 0)
        val color2 = color(0, 0, 255)
        val color3 = color(128, 128, 128)
        val sourcePixels = IntArray(width * height) {
            when (it / width) {
                0 -> color0
                1 -> color1
                2 -> color2
                else -> color3
            }
        }
        val displacementPixels = IntArray(width * height) {
            color(51, 255, 0)
        }

        val output = filter.execute(sourcePixels, displacementPixels, width, height, plan)

        assertEquals(width * height, output.size)
        for (i in output.indices) {
            assertEquals(color3, output[i], "nearest clamp pixel $i")
        }
    }

    private fun color(r: Int, g: Int, b: Int): Int =
        ((255 and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
