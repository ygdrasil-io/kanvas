package org.graphiks.kanvas.gpu.renderer.clips

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUClipExecutionPlanTest {
    @Test
    fun `closed execution variants retain their typed authority`() {
        val geometry = rect()
        val producer = stencilProducer(geometry)
        val consumer = stencilConsumer()
        val plans = listOf(
            GPUClipExecutionPlan.NoClip,
            GPUClipExecutionPlan.ScissorOnly(bounds),
            GPUClipExecutionPlan.AnalyticCoverage(geometry, bounds, antiAlias = true),
            GPUClipExecutionPlan.StencilCoverage(
                contentKey = "clip.stencil",
                bounds = bounds,
                sampleCount = 1,
                atomicGroup = GPUClipAtomicGroupID("clip.atomic.1"),
                orderingToken = GPUClipOrderingToken("clip.order.1"),
                producer = producer,
                consumer = consumer,
            ),
            maskPlan(listOf(maskProducer(0, geometry, GPUClipMaskCombine.Intersect))),
            GPUClipExecutionPlan.Refused("unsupported.clip.test", "test refusal"),
        )

        assertIs<GPUClipExecutionPlan.NoClip>(plans[0])
        assertIs<GPUClipExecutionPlan.ScissorOnly>(plans[1])
        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(plans[2])
        assertIs<GPUClipExecutionPlan.StencilCoverage>(plans[3])
        assertIs<GPUClipExecutionPlan.CoverageMask>(plans[4])
        assertIs<GPUClipExecutionPlan.Refused>(plans[5])
        assertEquals(plans.size, plans.map(GPUClipExecutionPlan::canonicalIdentity).distinct().size)
        assertTrue(plans.all { "gpu-clip-execution-v1" in it.canonicalIdentity() })
    }

    @Test
    fun `geometry and producer inputs are deeply snapshotted`() {
        val radii = MutableList(8) { 2f }
        val vertices = mutableListOf(0f, 0f, 8f, 0f, 8f, 8f)
        val contourStarts = mutableListOf(0)
        val rrect = GPUClipExecutionGeometry.RRect(scalarBounds, radii)
        val path = GPUClipExecutionGeometry.Path(
            vertices,
            contourStarts,
            GPUClipFillRule.Winding,
            inverseFill = false,
        )
        val producers = mutableListOf(
            maskProducer(0, rrect, GPUClipMaskCombine.Intersect),
            maskProducer(1, path, GPUClipMaskCombine.Difference),
        )
        val plan = maskPlan(producers)
        val identity = plan.canonicalIdentity()

        radii[0] = 9f
        vertices[0] = 9f
        contourStarts += 1
        producers.clear()

        assertEquals(List(8) { 2f }, rrect.radii)
        assertEquals(listOf(0f, 0f, 8f, 0f, 8f, 8f), path.vertices)
        assertEquals(listOf(0), path.contourStarts)
        assertEquals(2, plan.producers.size)
        assertEquals(identity, plan.canonicalIdentity())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (plan.producers as MutableList<GPUClipMaskProducerPlan>).clear()
        }
    }

    @Test
    fun `canonical identity preserves raw float ordered producer and combine distinctions`() {
        val positiveZero = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 8f, 8f))
        val negativeZero = GPUClipExecutionGeometry.Rect(GPUBounds(-0.0f, 0f, 8f, 8f))
        val path = path()
        val positive = GPUClipExecutionPlan.AnalyticCoverage(positiveZero, bounds, antiAlias = true)
        val negative = GPUClipExecutionPlan.AnalyticCoverage(negativeZero, bounds, antiAlias = true)
        val intersectThenDifference = maskPlan(
            listOf(
                maskProducer(0, positiveZero, GPUClipMaskCombine.Intersect),
                maskProducer(1, path, GPUClipMaskCombine.Difference),
            ),
        )
        val differenceThenIntersect = maskPlan(
            listOf(
                maskProducer(0, path, GPUClipMaskCombine.Difference),
                maskProducer(1, positiveZero, GPUClipMaskCombine.Intersect),
            ),
        )
        val intersectOnly = maskPlan(
            listOf(
                maskProducer(0, positiveZero, GPUClipMaskCombine.Intersect),
                maskProducer(1, path, GPUClipMaskCombine.Intersect),
            ),
        )

        assertNotEquals(positive.canonicalIdentity(), negative.canonicalIdentity())
        assertNotEquals(intersectThenDifference.canonicalIdentity(), differenceThenIntersect.canonicalIdentity())
        assertNotEquals(intersectThenDifference.canonicalIdentity(), intersectOnly.canonicalIdentity())
    }

    @Test
    fun `stencil producer identity includes independent front and back pass operations`() {
        val winding = stencilProducer(
            geometry = path(),
            frontPassOperation = GPUClipStencilOperation.IncrementWrap,
            backPassOperation = GPUClipStencilOperation.DecrementWrap,
        )
        val wrongBackFace = winding.copy(
            backPassOperation = GPUClipStencilOperation.IncrementWrap,
        )
        val wrongFrontFace = winding.copy(
            frontPassOperation = GPUClipStencilOperation.DecrementWrap,
        )

        assertNotEquals(
            stencilPlan(winding).canonicalIdentity(),
            stencilPlan(wrongBackFace).canonicalIdentity(),
        )
        assertNotEquals(
            stencilPlan(winding).canonicalIdentity(),
            stencilPlan(wrongFrontFace).canonicalIdentity(),
        )
    }

    @Test
    fun `mask and stencil bytes are exact decomposed and overflow checked`() {
        val mask = GPUClipExecutionPlan.CoverageMask(
            contentKey = "clip.mask.bytes",
            bounds = GPUPixelBounds(0, 0, 16, 16),
            sampleCount = 4,
            depthStencilRequired = true,
            orderingToken = GPUClipOrderingToken("clip.order.bytes"),
            producers = listOf(maskProducer(0, rect(), GPUClipMaskCombine.Intersect)),
            consumer = GPUClipMaskConsumerPlan(),
        )
        val stencil = GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.stencil.bytes",
            bounds = GPUPixelBounds(0, 0, 16, 16),
            sampleCount = 4,
            atomicGroup = GPUClipAtomicGroupID("clip.atomic.bytes"),
            orderingToken = GPUClipOrderingToken("clip.order.stencil.bytes"),
            producer = stencilProducer(path()),
            consumer = stencilConsumer(),
        )

        assertEquals(1_024L, mask.resolvedBytes)
        assertEquals(4_096L, mask.multisampleColorBytes)
        assertEquals(4_096L, mask.depthStencilBytes)
        assertEquals(9_216L, mask.requiredBytes)
        assertEquals(4_096L, stencil.depthStencilBytes)
        assertEquals(stencil.depthStencilBytes, stencil.requiredBytes)
        assertFailsWith<ArithmeticException> {
            GPUClipExecutionPlan.CoverageMask(
                contentKey = "clip.mask.overflow",
                bounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                sampleCount = 4,
                depthStencilRequired = true,
                orderingToken = GPUClipOrderingToken("clip.order.overflow"),
                producers = listOf(maskProducer(0, rect(), GPUClipMaskCombine.Intersect)),
                consumer = GPUClipMaskConsumerPlan(),
            )
        }
    }

    @Test
    fun `invalid producer order path radii and refusal are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            maskPlan(
                listOf(
                    maskProducer(1, rect(), GPUClipMaskCombine.Intersect),
                    maskProducer(1, path(), GPUClipMaskCombine.Difference),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionGeometry.Path(
                vertices = listOf(0f, 0f, 1f),
                contourStarts = listOf(0),
                fillRule = GPUClipFillRule.Winding,
                inverseFill = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionGeometry.Path(
                vertices = listOf(0f, 0f, 1f, 1f),
                contourStarts = listOf(1),
                fillRule = GPUClipFillRule.Winding,
                inverseFill = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionGeometry.RRect(scalarBounds, listOf(1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionGeometry.RRect(scalarBounds, List(8) { -1f })
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionPlan.Refused("", "missing code")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClipExecutionPlan.Refused("unsupported.clip.test", "")
        }
    }
}

private val bounds = GPUPixelBounds(0, 0, 8, 8)
private val scalarBounds = GPUBounds(0f, 0f, 8f, 8f)

private fun rect(): GPUClipExecutionGeometry.Rect = GPUClipExecutionGeometry.Rect(scalarBounds)

private fun path(): GPUClipExecutionGeometry.Path = GPUClipExecutionGeometry.Path(
    vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
    contourStarts = listOf(0),
    fillRule = GPUClipFillRule.Winding,
    inverseFill = false,
)

private fun maskProducer(
    sourceOrder: Int,
    geometry: GPUClipExecutionGeometry,
    combine: GPUClipMaskCombine,
): GPUClipMaskProducerPlan = GPUClipMaskProducerPlan(sourceOrder, geometry, combine, antiAlias = true)

private fun maskPlan(producers: List<GPUClipMaskProducerPlan>): GPUClipExecutionPlan.CoverageMask =
    GPUClipExecutionPlan.CoverageMask(
        contentKey = "clip.mask.test",
        bounds = bounds,
        sampleCount = 1,
        depthStencilRequired = false,
        orderingToken = GPUClipOrderingToken("clip.order.mask"),
        producers = producers,
        consumer = GPUClipMaskConsumerPlan(),
    )

private fun stencilProducer(
    geometry: GPUClipExecutionGeometry,
    frontPassOperation: GPUClipStencilOperation = GPUClipStencilOperation.Replace,
    backPassOperation: GPUClipStencilOperation = GPUClipStencilOperation.Replace,
): GPUClipStencilProducerPlan =
    GPUClipStencilProducerPlan(
        geometry = geometry,
        scissor = bounds,
        fillRule = GPUClipFillRule.Winding,
        reference = 1u,
        compare = GPUClipStencilCompare.Always,
        frontPassOperation = frontPassOperation,
        backPassOperation = backPassOperation,
        loadOperation = GPUClipStencilLoadOperation.Clear,
        storeOperation = GPUClipStencilStoreOperation.Store,
        clearValue = 0u,
    )

private fun stencilPlan(
    producer: GPUClipStencilProducerPlan,
): GPUClipExecutionPlan.StencilCoverage = GPUClipExecutionPlan.StencilCoverage(
    contentKey = "clip.stencil.identity",
    bounds = bounds,
    sampleCount = 1,
    atomicGroup = GPUClipAtomicGroupID("clip.atomic.identity"),
    orderingToken = GPUClipOrderingToken("clip.order.identity"),
    producer = producer,
    consumer = stencilConsumer(),
)

private fun stencilConsumer(): GPUClipStencilConsumerPlan = GPUClipStencilConsumerPlan(
    scissor = bounds,
    reference = 1u,
    compare = GPUClipStencilCompare.Equal,
)
