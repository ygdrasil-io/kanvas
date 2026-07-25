package org.graphiks.kanvas.gpu.renderer.payloads

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

/** Verifies the closed, handle-free semantic contract for prepared sampled images. */
class GPUPreparedImagePayloadTest {
    @Test
    fun `content and sampling affect semantic identity but not structural pipeline key`() {
        val first = payload(bytes = byteArrayOf(1, 2, 3, 4), sampling = GPUPreparedImageSampling.Nearest)
        val changedContent = payload(bytes = byteArrayOf(4, 3, 2, 1), sampling = GPUPreparedImageSampling.Nearest)
        val changedSampling = payload(bytes = byteArrayOf(1, 2, 3, 4), sampling = GPUPreparedImageSampling.Linear)

        assertNotEquals(first.artifact.contentHash, changedContent.artifact.contentHash)
        assertNotEquals(first.canonicalHash, changedContent.canonicalHash)
        assertEquals(first.pipelineKey, changedContent.pipelineKey)
        assertNotEquals(first.canonicalHash, changedSampling.canonicalHash)
        assertEquals(first.pipelineKey, changedSampling.pipelineKey)
    }

    @Test
    fun `quad preserves all four transformed positions and uvs with fixed indices`() {
        val geometry = GPUPreparedImageGeometry(
            geometryClass = GPUPreparedImageGeometryClass.Quad,
            vertices = listOf(
                GPUPreparedImageVertex(4f, 2f, 0f, 0f),
                GPUPreparedImageVertex(10f, 5f, 1f, 0f),
                GPUPreparedImageVertex(7f, 14f, 1f, 1f),
                GPUPreparedImageVertex(1f, 11f, 0f, 1f),
            ),
            indices = listOf(0, 1, 2, 0, 2, 3),
        )

        assertEquals(listOf(4f, 2f, 10f, 5f, 7f, 14f, 1f, 11f), geometry.vertices.flatMap { listOf(it.x, it.y) })
        assertEquals(listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), geometry.vertices.flatMap { listOf(it.u, it.v) })
        assertEquals(listOf(0, 1, 2, 0, 2, 3), geometry.indices)
    }

    @Test
    fun `copies and validates finite premultiplied colors`() {
        val color = mutableListOf(0.25f, 0.5f, 0.75f, 1f)
        val semantic = payload(tint = color)
        color[0] = 0f

        assertEquals(0.25f, semantic.tintPremultipliedRgba[0])
        assertTrue(semantic.hasCanonicalHashIntegrity())
        assertContains(semantic.stableDumpLine(), "tint=${0.25f.toRawBits()}")
        assertFailsWith<IllegalArgumentException> { payload(tint = listOf(1f, 0f, 0f, 0.5f)) }
        assertFailsWith<IllegalArgumentException> { payload(tint = listOf(Float.NaN, 0f, 0f, 1f)) }
    }

    @Test
    fun `semantic dump contains artifact identity and never image pixels`() {
        val semantic = payload(bytes = byteArrayOf(8, 7, 6, 5))
        val dump = semantic.stableDumpLine()

        assertContains(dump, semantic.artifact.key.value)
        assertContains(dump, semantic.artifact.contentHash)
        assertContains(dump, "artifactGeneration=2")
        assertContains(dump, "artifactLayout=4,4,1")
        assertContains(dump, "vertex0=0,0,0,0")
        assertContains(dump, "tint=${1f.toRawBits()}")
        assertContains(dump, "sampling=Nearest")
        assertTrue(!dump.contains("8, 7, 6, 5"))
    }

    @Test
    fun `geometry rejects noncanonical indices`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedImageGeometry(
                GPUPreparedImageGeometryClass.Rect,
                List(4) { GPUPreparedImageVertex(it.toFloat(), 0f, 0f, 0f) },
                listOf(0, 1, 3, 0, 3, 2),
            )
        }
    }

    private fun payload(
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        sampling: GPUPreparedImageSampling = GPUPreparedImageSampling.Nearest,
        tint: List<Float> = listOf(1f, 1f, 1f, 1f),
    ): GPUDrawSemanticPayload.SampledImage = GPUPreparedImagePayloadGatherer().gatherSemantic(
        GPUPreparedImagePayloadInput(
            payloadRef = GPUDrawPayloadRef(commandIdValue = 41, renderStepIdentity = "image.draw.texture_upload"),
            artifact = artifact(bytes),
            geometry = GPUPreparedImageGeometry(
                GPUPreparedImageGeometryClass.Rect,
                listOf(
                    GPUPreparedImageVertex(0f, 0f, 0f, 0f),
                    GPUPreparedImageVertex(4f, 0f, 1f, 0f),
                    GPUPreparedImageVertex(4f, 3f, 1f, 1f),
                    GPUPreparedImageVertex(0f, 3f, 0f, 1f),
                ),
                listOf(0, 1, 2, 0, 2, 3),
            ),
            sampling = sampling,
            tintPremultipliedRgba = tint,
            atlasColorPremultipliedRgba = null,
            atlasSourceBlend = null,
            targetBounds = GPUPixelBounds(0, 0, 16, 16),
            scissorBounds = GPUPixelBounds(0, 0, 16, 16),
            blendPlanIdentity = "src-over",
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )

    private fun artifact(bytes: ByteArray) = (GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
            sourceId = "test-image",
            width = 1,
            height = 1,
            sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
            alphaType = AlphaType.PREMUL,
            sourceRowBytes = 4,
            profile = GPUPreparedImageProfile.Srgb,
            orientation = GPUPreparedImageOrientation.AppliedIdentity,
            provenance = GPUPreparedImageProvenance.CallerPixels,
            sourceGeneration = 2,
            pixelBytes = bytes,
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact
}
