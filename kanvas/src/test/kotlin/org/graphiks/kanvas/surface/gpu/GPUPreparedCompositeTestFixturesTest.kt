package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedCompositeTestFixturesTest {

    @Test
    fun `rgba checker mutation does not affect subsequent access`() {
        val first = GPUPreparedCompositeTestFixtures.rgbaChecker()
        first[0] = 99.toByte()
        val second = GPUPreparedCompositeTestFixtures.rgbaChecker()
        val expected = 255
        assertEquals(expected, second[0].toInt() and 0xFF)
    }

    @Test
    fun `a8 checker mutation does not affect subsequent access`() {
        val first = GPUPreparedCompositeTestFixtures.a8Checker()
        first[0] = 77.toByte()
        val second = GPUPreparedCompositeTestFixtures.a8Checker()
        assertEquals(255, second[0].toInt() and 0xFF)
    }

    @Test
    fun `rgba solid returns correct dimensions`() {
        val rgba = GPUPreparedCompositeTestFixtures.rgbaSolid(3, 5, 100, 150, 200)
        assertEquals(3 * 5 * 4, rgba.size)
        assertEquals(100, rgba[0].toInt() and 0xFF)
        assertEquals(150, rgba[1].toInt() and 0xFF)
        assertEquals(200, rgba[2].toInt() and 0xFF)
        assertEquals(255, rgba[3].toInt() and 0xFF)
    }

    @Test
    fun `rgba gradient has correct first and last pixels`() {
        val rgba = GPUPreparedCompositeTestFixtures.rgbaGradient(4, 4)
        assertEquals(0, rgba[0].toInt() and 0xFF)
        val lastPixelIndex = (3 * 4 + 3) * 4
        assertEquals(255, rgba[lastPixelIndex].toInt() and 0xFF)
    }

    @Test
    fun `oracle nearest sample returns exact pixel`() {
        val rgba = GPUPreparedCompositeTestFixtures.rgbaSolid(4, 4, 10, 20, 30)
        val sample = GPUPreparedFilterCpuOracle.sampleNearestRGBA(rgba, 4, 4, 0.75f, 0.75f)
        assertEquals(10, sample[0])
        assertEquals(20, sample[1])
        assertEquals(30, sample[2])
        assertEquals(255, sample[3])
    }

    @Test
    fun `oracle linear sample interpolates correctly`() {
        val rgba = GPUPreparedCompositeTestFixtures.rgbaGradient(2, 2)
        val sample = GPUPreparedFilterCpuOracle.sampleLinearRGBA(rgba, 2, 2, 0.75f, 0.5f)
        assertTrue(sample[0] in 0..255)
        assertTrue(sample[1] in 0..255)
    }

    @Test
    fun `srgb pipeline oracle produces expected order`() {
        val sample = intArrayOf(255, 0, 0, 255)
        val tint = floatArrayOf(1f, 1f, 1f, 1f)
        val result = GPUPreparedFilterCpuOracle.applySrgbPipelineOrder(sample, tint)
        assertEquals(255, result[0])
        assertEquals(0, result[1])
        assertEquals(0, result[2])
        assertTrue(result[3] in 250..255)
    }

    @Test
    fun `maxChannelDelta computes correctly for identical arrays`() {
        val rgba = GPUPreparedCompositeTestFixtures.rgbaChecker()
        val delta = GPUPreparedFilterCpuOracle.computeMaxChannelDelta(rgba, rgba, 4, 4)
        assertEquals(0, delta)
    }

    @Test
    fun `maxChannelDelta detects differences`() {
        val a = GPUPreparedCompositeTestFixtures.rgbaSolid(2, 2, 0, 0, 0)
        val b = GPUPreparedCompositeTestFixtures.rgbaSolid(2, 2, 5, 0, 0)
        val delta = GPUPreparedFilterCpuOracle.computeMaxChannelDelta(a, b, 2, 2)
        assertTrue(delta > 0)
    }

    @Test
    fun `native probe starts at zero`() {
        val probe = NativeEffectProbe()
        assertEquals(NativeEffectProbe.Counts.ZERO, probe.counts)
        assertTrue(probe.counts.isZero)
    }

    @Test
    fun `native probe separately counts each effect kind`() {
        val probe = NativeEffectProbe()
        probe.recordAllocation()
        probe.recordQueueWrite()
        probe.recordCommandEncoding()
        probe.recordSubmit()
        probe.recordFallback()
        probe.recordRenderPass()
        probe.recordComputePass()
        probe.recordCopyOperation()
        val c = probe.counts
        assertEquals(1, c.allocations)
        assertEquals(1, c.queueWrites)
        assertEquals(1, c.commandEncodings)
        assertEquals(1, c.submits)
        assertEquals(1, c.fallbacks)
        assertEquals(1, c.renderPasses)
        assertEquals(1, c.computePasses)
        assertEquals(1, c.copyOperations)
    }

    @Test
    fun `native probe reset clears all counts`() {
        val probe = NativeEffectProbe()
        probe.recordAllocation()
        probe.recordSubmit()
        probe.reset()
        assertEquals(NativeEffectProbe.Counts.ZERO, probe.counts)
    }

    @Test
    fun `a8 nearest sample returns correct value at center`() {
        val a8 = GPUPreparedCompositeTestFixtures.a8Checker(4, 4)
        val a = GPUPreparedFilterCpuOracle.sampleNearestA8(a8, 4, 4, 0f, 0f)
        assertEquals(255, a)
    }
}
