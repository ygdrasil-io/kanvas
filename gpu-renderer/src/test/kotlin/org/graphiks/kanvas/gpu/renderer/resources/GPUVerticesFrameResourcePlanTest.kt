package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesStagingLayout
import org.graphiks.kanvas.gpu.renderer.vertices.GPUIndexBufferPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexBufferPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

class GPUVerticesFrameResourcePlanTest {
    @Test
    fun `vertex plan carries copy-dst vertex usage flags alignment and exact byte counts`() {
        val plan = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 6),
            deviceGeneration = 5,
            bufferGeneration = 3,
        )

        assertEquals(48L, plan.vertexBuffer.byteCount)
        assertEquals(4, plan.vertexBuffer.alignment)
        assertTrue(plan.vertexBuffer.usageFlags.containsAll(listOf("copy_dst", "vertex")))
        assertEquals("upload-before-draw", plan.vertexBuffer.uploadRequirement)
        assertEquals(5L, plan.vertexBuffer.deviceGeneration)
        assertEquals(3L, plan.vertexBuffer.bufferGeneration)
        assertNull(plan.indexBuffer)
        assertEquals(48L, plan.totalByteCount)
    }

    @Test
    fun `indexed artifact plans uint16 indices with doubled byte count and index format`() {
        val plan = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16"),
            deviceGeneration = 5,
        )

        val index = assertNotNull(plan.indexBuffer)
        assertEquals("uint16", index.indexFormat)
        assertEquals(6, index.count)
        assertEquals(12L, index.byteCount)
        assertEquals(4, index.alignment)
        assertTrue(index.usageFlags.containsAll(listOf("copy_dst", "index")))
        assertEquals(60L, plan.totalByteCount)
        assertEquals(0L..47L, plan.vertexByteRange)
        assertEquals(48L..59L, assertNotNull(plan.indexByteRange))
    }

    @Test
    fun `indexed artifact plans uint32 indices with quadrupled byte count and index format`() {
        val plan = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint32"),
            deviceGeneration = 5,
        )

        val index = assertNotNull(plan.indexBuffer)
        assertEquals("uint32", index.indexFormat)
        assertEquals(6, index.count)
        assertEquals(24L, index.byteCount)
        assertTrue(index.usageFlags.containsAll(listOf("copy_dst", "index")))
        assertEquals(72L, plan.totalByteCount)
        assertEquals(48L..71L, assertNotNull(plan.indexByteRange))
    }

    @Test
    fun `staging layout honors alignment and never overlaps multiple artifacts`() {
        val first = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 1, indexed = true, indexFormat = "uint16"),
            deviceGeneration = 5,
        )
        val second = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 6),
            deviceGeneration = 5,
        )
        val third = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 9, indexed = true, indexFormat = "uint32"),
            deviceGeneration = 5,
        )

        val layout = buildVerticesStagingLayout(listOf(first, second, third))

        val ranges = layout.ranges.map { range ->
            Triple(range.artifactKey, range.bufferKind, range.offsetBytes)
        }
        assertEquals(
            listOf(
                Triple(first.artifactKey, "vertex", 0L),
                Triple(first.artifactKey, "index", 8L),
                Triple(second.artifactKey, "vertex", 12L),
                Triple(third.artifactKey, "vertex", 60L),
                Triple(third.artifactKey, "index", 132L),
            ),
            ranges,
        )
        assertTrue(layout.ranges.all { it.offsetBytes % 4L == 0L })
        layout.ranges.zipWithNext().forEach { (left, right) ->
            assertTrue(left.offsetBytes + left.byteCount <= right.offsetBytes)
        }
        assertEquals(168L, layout.totalBytes)
    }

    @Test
    fun `same artifact key yields an identical dedup plan`() {
        val firstArtifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16")
        val secondArtifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16")

        assertEquals(firstArtifact.key, secondArtifact.key)
        val firstPlan = buildVerticesFrameResourcePlan(firstArtifact, deviceGeneration = 5)
        val secondPlan = buildVerticesFrameResourcePlan(secondArtifact, deviceGeneration = 5)
        assertEquals(firstPlan, secondPlan)
        assertEquals(firstPlan.hashCode(), secondPlan.hashCode())
        assertEquals(
            buildVerticesStagingLayout(listOf(firstPlan)),
            buildVerticesStagingLayout(listOf(secondPlan)),
        )
    }

    @Test
    fun `different artifacts yield different artifact keys and plans`() {
        val firstArtifact = artifact(vertexCount = 3)
        val secondArtifact = artifact(vertexCount = 6)

        assertNotEquals(firstArtifact.key, secondArtifact.key)
        val firstPlan = buildVerticesFrameResourcePlan(firstArtifact, deviceGeneration = 5)
        val secondPlan = buildVerticesFrameResourcePlan(secondArtifact, deviceGeneration = 5)
        assertNotEquals(firstPlan, secondPlan)
        assertNotEquals(firstPlan.artifactKey, secondPlan.artifactKey)
    }

    @Test
    fun `ownerScope defaults to PayloadOwnedCompletion`() {
        val plan = buildVerticesFrameResourcePlan(artifact(vertexCount = 3), deviceGeneration = 5)

        assertEquals("PayloadOwnedCompletion", plan.ownerScope)
        assertEquals("PayloadOwnedCompletion", plan.vertexBuffer.ownerScope)
        assertNull(plan.indexBuffer)
    }

    @Test
    fun `uploadBeforeUseToken is present and deterministic per artifact`() {
        val plan = buildVerticesFrameResourcePlan(artifact(vertexCount = 6), deviceGeneration = 5)

        assertEquals("prepared-vertices.upload-before-consumer:${plan.artifactKey}", plan.uploadBeforeUseToken)
        assertEquals(
            plan.uploadBeforeUseToken,
            buildVerticesFrameResourcePlan(artifact(vertexCount = 6), deviceGeneration = 5).uploadBeforeUseToken,
        )
    }

    @Test
    fun `plan retains invalidation generation facts for vertex and index buffers`() {
        val plan = buildVerticesFrameResourcePlan(
            artifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16"),
            deviceGeneration = 5,
            bufferGeneration = 3,
        )

        assertEquals(
            listOf(
                "device-generation:5",
                "buffer-generation:3",
                "index-device-generation:5",
                "index-buffer-generation:3",
            ),
            plan.invalidationFacts,
        )
    }

    @Test
    fun `invalid plan inputs are rejected via require`() {
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "  ",
                vertexBuffer = vertexPlan(48L),
                indexBuffer = null,
                uploadBeforeUseToken = "token",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "artifact-key",
                vertexBuffer = vertexPlan(48L),
                indexBuffer = null,
                uploadBeforeUseToken = "  ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "artifact-key",
                vertexBuffer = vertexPlan(48L),
                indexBuffer = null,
                uploadBeforeUseToken = "token",
                ownerScope = "  ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "artifact-key",
                vertexBuffer = vertexPlan(-48L),
                indexBuffer = null,
                uploadBeforeUseToken = "token",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "artifact-key",
                vertexBuffer = vertexPlan(48L),
                indexBuffer = GPUIndexBufferPlan(
                    indexFormat = "uint64",
                    count = 6,
                    validationLabel = "test",
                    byteCount = 48L,
                ),
                uploadBeforeUseToken = "token",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUVerticesFrameResourcePlan(
                artifactKey = "artifact-key",
                vertexBuffer = vertexPlan(48L),
                indexBuffer = GPUIndexBufferPlan(
                    indexFormat = "uint16",
                    count = 6,
                    validationLabel = "test",
                    byteCount = -6L,
                ),
                uploadBeforeUseToken = "token",
            )
        }
    }

    @Test
    fun `staging layout rejects overflowing byte accounting`() {
        val huge = GPUVerticesFrameResourcePlan(
            artifactKey = "huge",
            vertexBuffer = vertexPlan(Long.MAX_VALUE),
            indexBuffer = null,
            uploadBeforeUseToken = "token",
        )

        assertFailsWith<IllegalArgumentException> {
            buildVerticesStagingLayout(listOf(huge, huge))
        }
    }

    private fun vertexPlan(byteCount: Long): GPUVertexBufferPlan = GPUVertexBufferPlan(
        byteCount = byteCount,
        layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = false),
        uploadRequirement = "upload-before-draw",
    )

    private fun artifact(
        vertexCount: Int,
        indexed: Boolean = false,
        indexFormat: String = "uint16",
    ): GPUPreparedVerticesUploadArtifact = GPUPreparedVerticesUploadArtifact(
        topology = GPUVertexMode.Triangles,
        layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = false),
        vertexBytes = ByteArray(vertexCount * 8),
        indexBytes = if (indexed) {
            ByteArray(vertexCount * (if (indexFormat == "uint16") 2 else 4))
        } else {
            null
        },
        vertexCount = vertexCount,
        indexCount = if (indexed) vertexCount else null,
        indexFormat = if (indexed) indexFormat else null,
        provenance = "test",
        canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.IdentityV1,
    )
}
