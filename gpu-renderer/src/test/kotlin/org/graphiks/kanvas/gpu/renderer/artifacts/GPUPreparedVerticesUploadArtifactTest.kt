package org.graphiks.kanvas.gpu.renderer.artifacts

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesArtifactInput
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

class GPUPreparedVerticesUploadArtifactTest {
    @Test
    fun `artifact snapshots vertex and index bytes`() {
        val vertices = validVertexBytes()
        val indices = validUint16Indices()
        val expectedVertices = vertices.copyOf()
        val expectedIndices = indices.copyOf()
        val artifact = preparedArtifact(vertices, indices)

        vertices.fill(99)
        indices.fill(88)

        assertContentEquals(expectedVertices, artifact.vertexBytesForUpload())
        assertContentEquals(expectedIndices, artifact.indexBytesForUpload())
    }

    @Test
    fun `artifact upload accessors return isolated copies`() {
        val artifact = preparedArtifact(validVertexBytes(), validUint16Indices())
        val vertexCopy = artifact.vertexBytesForUpload()
        val indexCopy = requireNotNull(artifact.indexBytesForUpload())

        vertexCopy.fill(99)
        indexCopy.fill(88)

        assertContentEquals(validVertexBytes(), artifact.vertexBytesForUpload())
        assertContentEquals(validUint16Indices(), artifact.indexBytesForUpload())
    }

    @Test
    fun `artifact identity excludes provenance and includes exact bytes`() {
        val first = preparedArtifact(validVertexBytes(), null, provenance = "command:one")
        val same = preparedArtifact(validVertexBytes(), null, provenance = "command:two")
        val changed = preparedArtifact(validVertexBytes().also { it[it.lastIndex] = 99 }, null)

        assertEquals(first.key, same.key)
        assertNotEquals(first.key, changed.key)
    }

    @Test
    fun `artifact key covers every structural axis`() {
        val baseline = preparedArtifact(validVertexBytes(), validUint16Indices())
        val colorLayout = GPUVertexLayoutPlan(
            attributes = listOf("position", "color"),
            strideBytes = 12,
            offsets = linkedMapOf("position" to 0, "color" to 8),
            shaderLocations = linkedMapOf("position" to 0, "color" to 1),
        )

        val variants = listOf(
            preparedArtifact(validVertexBytes(), validUint16Indices(), topology = GPUVertexMode.TriangleStrip),
            preparedArtifact(validVertexBytes().also { it[0] = 99 }, validUint16Indices()),
            preparedArtifact(validVertexBytes(), byteArrayOf(1, 0, 2, 0)),
            preparedArtifact(validVertexBytes(8, 4), validUint16Indices(), vertexCount = 4),
            preparedArtifact(validVertexBytes(), byteArrayOf(0, 0, 1, 0, 2, 0), indexCount = 3),
            preparedArtifact(ByteArray(24), ByteArray(8), indexCount = 2, indexFormat = "uint32"),
            preparedArtifact(validVertexBytes(12), validUint16Indices(), layout = colorLayout),
            preparedArtifact(
                validVertexBytes(),
                validUint16Indices(),
                canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
            ),
        )

        variants.forEach { variant -> assertNotEquals(baseline.key, variant.key) }
    }

    @Test
    fun `artifact key canonicalizes layout map order and snapshots layout collections`() {
        val attributes = mutableListOf("position", "color")
        val offsets = linkedMapOf("position" to 0, "color" to 8)
        val locations = linkedMapOf("position" to 0, "color" to 1)
        val first = preparedArtifact(
            vertices = validVertexBytes(12),
            indices = null,
            layout = GPUVertexLayoutPlan(attributes, 12, offsets, locations),
        )
        val same = preparedArtifact(
            vertices = validVertexBytes(12),
            indices = null,
            layout = GPUVertexLayoutPlan(
                attributes = listOf("position", "color"),
                strideBytes = 12,
                offsets = linkedMapOf("color" to 8, "position" to 0),
                shaderLocations = linkedMapOf("color" to 1, "position" to 0),
            ),
        )

        attributes += "texcoord"
        offsets["position"] = 4
        locations["position"] = 2

        assertEquals(first.key, same.key)
        assertEquals(listOf("position", "color"), first.layout.attributes)
        assertEquals(mapOf("position" to 0, "color" to 8), first.layout.offsets)
        assertEquals(mapOf("position" to 0, "color" to 1), first.layout.shaderLocations)
    }

    @Test
    fun `artifact rejects a noncanonical shader location without comparing it to stride`() {
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                vertices = validVertexBytes(),
                indices = null,
                layout = positionLayout().copy(shaderLocations = mapOf("position" to 8)),
            )
        }
    }

    @Test
    fun `artifact canonicalization identity is versioned key-visible and coherent`() {
        val direct = preparedArtifact(validVertexBytes(), validUint16Indices())
        val fan = preparedArtifact(
            validVertexBytes(),
            validUint16Indices(),
            canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
        )

        assertEquals(GPUPreparedVerticesCanonicalizationIdentity.IdentityV1, direct.canonicalizationIdentity)
        assertEquals("identity-v1", direct.canonicalizationIdentity.stableIdentity)
        assertEquals(
            GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
            fan.canonicalizationIdentity,
        )
        assertEquals("triangle-fan-to-triangle-list-v1", fan.canonicalizationIdentity.stableIdentity)
        assertNotEquals(direct.key, fan.key)
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(),
                null,
                canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(),
                validUint16Indices(),
                topology = GPUVertexMode.TriangleStrip,
                canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
            )
        }
    }

    @Test
    fun `artifact rejects malformed structure before key publication`() {
        assertFailsWith<IllegalArgumentException> { preparedArtifact(validVertexBytes(), null, provenance = "") }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(validVertexBytes(), null, topology = GPUVertexMode.TriangleFan)
        }
        assertFailsWith<IllegalArgumentException> { preparedArtifact(validVertexBytes(), null, vertexCount = 0) }
        assertFailsWith<IllegalArgumentException> { preparedArtifact(ByteArray(23), null) }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(validVertexBytes(12), null, layout = positionLayout(strideBytes = 12))
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(12),
                null,
                layout = GPUVertexLayoutPlan(
                    listOf("position", "color"),
                    12,
                    mapOf("position" to 0, "color" to 4),
                    mapOf("position" to 0, "color" to 1),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(),
                null,
                layout = GPUVertexLayoutPlan(listOf("color"), 8, mapOf("color" to 0), mapOf("color" to 0)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(),
                null,
                layout = GPUVertexLayoutPlan(
                    listOf("position"),
                    8,
                    mapOf("position" to 0, "color" to 4),
                    mapOf("position" to 0),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(
                validVertexBytes(12),
                null,
                layout = GPUVertexLayoutPlan(
                    listOf("position", "color"),
                    12,
                    mapOf("position" to 0, "color" to 8),
                    mapOf("position" to 0, "color" to 0),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(validVertexBytes(), null, indexCount = 2, indexFormat = "uint16")
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(validVertexBytes(), validUint16Indices(), indexFormat = "uint8")
        }
        assertFailsWith<IllegalArgumentException> {
            preparedArtifact(validVertexBytes(), validUint16Indices(), indexCount = 3)
        }
    }

    @Test
    fun `artifact input carries raw source geometry before packing`() {
        val input = GPUPreparedVerticesArtifactInput(
            topology = GPUVertexMode.Triangles,
            positions = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            colorsRgba8 = byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
            texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            indices = intArrayOf(0, 1, 2),
            provenance = "unit-test",
        )

        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), input.positions)
        assertContentEquals(byteArrayOf(255.toByte(), 0, 0, 255.toByte()), input.colorsRgba8)
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), input.texCoords)
        assertContentEquals(intArrayOf(0, 1, 2), input.indices)
    }
}

private fun preparedArtifact(
    vertices: ByteArray,
    indices: ByteArray?,
    provenance: String = "unit-test",
    topology: GPUVertexMode = GPUVertexMode.Triangles,
    layout: GPUVertexLayoutPlan = positionLayout(),
    vertexCount: Int = 3,
    indexCount: Int? = indices?.size?.div(2),
    indexFormat: String? = indices?.let { "uint16" },
    canonicalizationIdentity: GPUPreparedVerticesCanonicalizationIdentity =
        GPUPreparedVerticesCanonicalizationIdentity.IdentityV1,
): GPUPreparedVerticesUploadArtifact =
    GPUPreparedVerticesUploadArtifact(
        topology = topology,
        layout = layout,
        vertexBytes = vertices,
        indexBytes = indices,
        vertexCount = vertexCount,
        indexCount = indexCount,
        indexFormat = indexFormat,
        provenance = provenance,
        canonicalizationIdentity = canonicalizationIdentity,
    )

private fun positionLayout(strideBytes: Int = 8): GPUVertexLayoutPlan =
    GPUVertexLayoutPlan(
        attributes = listOf("position"),
        strideBytes = strideBytes,
        offsets = mapOf("position" to 0),
        shaderLocations = mapOf("position" to 0),
    )

private fun validVertexBytes(strideBytes: Int = 8, vertexCount: Int = 3): ByteArray =
    ByteArray(strideBytes * vertexCount) { index -> index.toByte() }

private fun validUint16Indices(): ByteArray = byteArrayOf(0, 0, 1, 0)
