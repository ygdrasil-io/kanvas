package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

class GPUBlendOracleTest {
    @Test
    fun `all 29 blend modes produce finite premultiplied results`() {
        val sources = listOf(
            BlendPremulColor(0f, 0f, 0f, 0f),
            BlendPremulColor(.18f, .07f, .31f, .55f),
            BlendPremulColor(.9f, .2f, .5f, 1f),
            BlendPremulColor(.3f, .7f, .1f, .25f),
            BlendPremulColor(1f, 1f, 1f, 1f),
        )
        val destinations = listOf(
            BlendPremulColor(0f, 0f, 0f, 0f),
            BlendPremulColor(.08f, .24f, .16f, .4f),
            BlendPremulColor(.2f, .6f, .35f, 1f),
            BlendPremulColor(1f, 1f, 1f, 1f),
        )

        GPUBlendMode.entries.forEach { mode ->
            sources.forEach { source ->
                destinations.forEach { destination ->
                    val full = GPUBlendOracle.blendAtFullCoverage(mode, source, destination)
                    assertColorFinite(full, "$mode full source=$source destination=$destination")

                    listOf(0f, .25f, .5f, 1f).forEach { coverage ->
                        val scalar = GPUBlendOracle.blend(mode, source, destination, coverage)
                        assertColorFinite(scalar, "$mode scalar=$coverage source=$source destination=$destination")
                    }

                    val lcdCoverage = floatArrayOf(.15f, .55f, .9f)
                    val lcd = GPUBlendOracle.blendLcd(mode, source, destination, lcdCoverage)
                    assertColorFinite(lcd, "$mode lcd source=$source destination=$destination")
                }
            }
        }
    }

    private fun assertColorFinite(color: BlendPremulColor, label: String) {
        assertTrue(color.toArray().all(Float::isFinite), "$label must be finite, got $color")
    }
}
