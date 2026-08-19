package org.graphiks.kanvas.gpu.renderer.vertices

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity

class GPUPreparedVerticesPackerTest {
    @Test
    fun `triangle lists require a positive element count divisible by three`() {
        val unindexed = pack(
            positions = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
        ).ready()
        val indexed = pack(
            positions = squarePositions(),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        ).ready()

        assertEquals(GPUVertexMode.Triangles, unindexed.artifact.topology)
        assertNull(unindexed.artifact.indexBytesForUpload())
        assertEquals(3, unindexed.artifact.vertexCount)
        assertContentEquals(
            intArrayOf(0, 1, 2, 0, 2, 3),
            decodeIndices(indexed.artifact.indexBytesForUpload(), indexed.artifact.indexFormat),
        )

        listOf(
            pack(positions = squarePositions()),
            pack(positions = squarePositions(), indices = intArrayOf(0, 1, 2, 3)),
            pack(positions = floatArrayOf(0f, 0f), indices = intArrayOf()),
        ).forEach { result ->
            val refusal = result.refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.Topology, refusal.code)
            assertEquals("triangle_count_not_multiple_of_three", refusal.facts["reason"])
        }
    }

    @Test
    fun `triangle strips require at least three source elements and remain strips`() {
        val unindexed = pack(
            topology = GPUVertexMode.TriangleStrip,
            positions = squarePositions(),
        ).ready()
        val indexed = pack(
            topology = GPUVertexMode.TriangleStrip,
            positions = squarePositions(),
            indices = intArrayOf(3, 2, 1, 0),
        ).ready()

        assertEquals(GPUVertexMode.TriangleStrip, unindexed.artifact.topology)
        assertNull(unindexed.artifact.indexBytesForUpload())
        assertEquals(GPUVertexMode.TriangleStrip, indexed.artifact.topology)
        assertContentEquals(
            intArrayOf(3, 2, 1, 0),
            decodeIndices(indexed.artifact.indexBytesForUpload(), indexed.artifact.indexFormat),
        )

        listOf(
            pack(
                topology = GPUVertexMode.TriangleStrip,
                positions = floatArrayOf(0f, 0f, 1f, 0f),
            ),
            pack(
                topology = GPUVertexMode.TriangleStrip,
                positions = squarePositions(),
                indices = intArrayOf(0, 1),
            ),
        ).forEach { result ->
            val refusal = result.refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.Topology, refusal.code)
            assertEquals("strip_requires_three_elements", refusal.facts["reason"])
        }
    }

    @Test
    fun `indexed fan becomes deterministic triangle-list indices with source provenance and winding`() {
        val result = pack(
            topology = GPUVertexMode.TriangleFan,
            positions = squarePositions(),
            indices = intArrayOf(2, 3, 0, 1),
            provenance = "draw:fan:indexed",
        ).ready()

        assertEquals(GPUVertexMode.Triangles, result.artifact.topology)
        assertEquals("draw:fan:indexed", result.artifact.provenance)
        assertContentEquals(
            intArrayOf(2, 3, 0, 2, 0, 1),
            decodeIndices(result.artifact.indexBytesForUpload(), result.artifact.indexFormat),
        )
    }

    @Test
    fun `unindexed fan gets deterministic implicit triangle-list indices`() {
        val result = pack(
            topology = GPUVertexMode.TriangleFan,
            positions = squarePositions(),
        ).ready()

        assertEquals(GPUVertexMode.Triangles, result.artifact.topology)
        assertEquals("uint16", result.artifact.indexFormat)
        assertContentEquals(
            intArrayOf(0, 1, 2, 0, 2, 3),
            decodeIndices(result.artifact.indexBytesForUpload(), result.artifact.indexFormat),
        )

        val refusal = pack(
            topology = GPUVertexMode.TriangleFan,
            positions = floatArrayOf(0f, 0f, 1f, 0f),
        ).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.Topology, refusal.code)
        assertEquals("fan_requires_three_elements", refusal.facts["reason"])
    }

    @Test
    fun `fan expansion identity keeps equivalent canonical bytes in distinct artifact keys`() {
        val fan = pack(
            topology = GPUVertexMode.TriangleFan,
            positions = squarePositions(),
        ).ready().artifact
        val direct = pack(
            topology = GPUVertexMode.Triangles,
            positions = squarePositions(),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        ).ready().artifact

        assertContentEquals(direct.vertexBytesForUpload(), fan.vertexBytesForUpload())
        assertContentEquals(direct.indexBytesForUpload(), fan.indexBytesForUpload())
        assertEquals(GPUPreparedVerticesCanonicalizationIdentity.IdentityV1, direct.canonicalizationIdentity)
        assertEquals(
            GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
            fan.canonicalizationIdentity,
        )
        assertNotEquals(direct.key, fan.key)
    }

    @Test
    fun `positions must contain a positive even float count`() {
        val empty = pack(positions = floatArrayOf()).refused()
        val odd = pack(positions = floatArrayOf(0f, 0f, 1f)).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.PositionCount, empty.code)
        assertEquals("positions_empty", empty.facts["reason"])
        assertEquals(GPUPreparedVerticesRefusalCodes.PositionCount, odd.code)
        assertEquals("positions_not_float32x2", odd.facts["reason"])
    }

    @Test
    fun `color bytes and UV floats exactly match the vertex count`() {
        val shortColors = pack(colorsRgba8 = ByteArray(11)).refused()
        val longColors = pack(colorsRgba8 = ByteArray(13)).refused()
        val shortUvs = pack(texCoords = FloatArray(5)).refused()
        val longUvs = pack(texCoords = FloatArray(7)).refused()

        listOf(shortColors, longColors).forEach { refusal ->
            assertEquals(GPUPreparedVerticesRefusalCodes.AttributeCount, refusal.code)
            assertEquals("color", refusal.facts["attribute"])
            assertEquals("12", refusal.facts["expected"])
        }
        listOf(shortUvs, longUvs).forEach { refusal ->
            assertEquals(GPUPreparedVerticesRefusalCodes.AttributeCount, refusal.code)
            assertEquals("texcoord", refusal.facts["attribute"])
            assertEquals("6", refusal.facts["expected"])
        }
    }

    @Test
    fun `positions and UVs must be finite`() {
        val positionCases = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        positionCases.forEach { value ->
            val refusal = pack(
                positions = floatArrayOf(value, 0f, 1f, 0f, 0f, 1f),
            ).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.NonFinite, refusal.code)
            assertEquals("position", refusal.facts["attribute"])
            assertEquals("0", refusal.facts["componentIndex"])
        }

        val uvCases = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        uvCases.forEach { value ->
            val refusal = pack(
                texCoords = floatArrayOf(0f, value, 1f, 0f, 0f, 1f),
            ).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.NonFinite, refusal.code)
            assertEquals("texcoord", refusal.facts["attribute"])
            assertEquals("1", refusal.facts["componentIndex"])
        }
    }

    @Test
    fun `RGBA8 colors use alpha UNORM and round-half-up canonical premultiplication`() {
        val result = pack(
            colorsRgba8 = byteArrayOf(
                1, 3, 254.toByte(), 128.toByte(),
                255.toByte(), 128.toByte(), 64, 255.toByte(),
                255.toByte(), 128.toByte(), 64, 0,
            ),
        ).ready()
        val bytes = result.artifact.vertexBytesForUpload()

        assertContentEquals(
            byteArrayOf(1, 2, 127, 128.toByte()),
            bytes.copyOfRange(8, 12),
        )
        assertContentEquals(
            byteArrayOf(255.toByte(), 128.toByte(), 64, 255.toByte()),
            bytes.copyOfRange(20, 24),
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0),
            bytes.copyOfRange(32, 36),
        )
    }

    @Test
    fun `index packing selects uint16 at 65535 and uint32 above it`() {
        val uint16 = pack(
            positions = FloatArray(65_536 * 2),
            indices = intArrayOf(0, 1, 65_535),
        ).ready()
        val uint32 = pack(
            positions = FloatArray(65_537 * 2),
            indices = intArrayOf(0, 1, 65_536),
            supportsUint32Index = true,
        ).ready()

        assertEquals("uint16", uint16.artifact.indexFormat)
        assertEquals("uint32", uint32.artifact.indexFormat)
        assertEquals(6, requireNotNull(uint16.artifact.indexBytesForUpload()).size)
        assertEquals(12, requireNotNull(uint32.artifact.indexBytesForUpload()).size)
    }

    @Test
    fun `uint32 index requirement refuses when the capability is absent`() {
        val refusal = pack(
            positions = FloatArray(65_537 * 2),
            indices = intArrayOf(0, 1, 65_536),
            supportsUint32Index = false,
        ).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.IndexFormat, refusal.code)
        assertEquals(
            mapOf(
                "maxIndex" to "65536",
                "provenance" to "unit-test",
                "reason" to "uint32_capability_unavailable",
                "requiredFormat" to "uint32",
                "supportsUint32Index" to "false",
                "topology" to "Triangles",
            ),
            refusal.facts,
        )
    }

    @Test
    fun `packer emits the four closed layouts with exact stride offsets and locations`() {
        val position = pack().ready().artifact.layout
        val color = pack(colorsRgba8 = opaqueColors(3)).ready().artifact.layout
        val uv = pack(texCoords = triangleUvs()).ready().artifact.layout
        val colorUv = pack(
            colorsRgba8 = opaqueColors(3),
            texCoords = triangleUvs(),
        ).ready().artifact.layout

        assertEquals(
            GPUVertexLayoutPlan(
                attributes = listOf("position"),
                strideBytes = 8,
                offsets = mapOf("position" to 0),
                shaderLocations = mapOf("position" to 0),
            ),
            position,
        )
        assertEquals(
            GPUVertexLayoutPlan(
                attributes = listOf("position", "color"),
                strideBytes = 12,
                offsets = mapOf("position" to 0, "color" to 8),
                shaderLocations = mapOf("position" to 0, "color" to 1),
            ),
            color,
        )
        assertEquals(
            GPUVertexLayoutPlan(
                attributes = listOf("position", "texcoord"),
                strideBytes = 16,
                offsets = mapOf("position" to 0, "texcoord" to 8),
                shaderLocations = mapOf("position" to 0, "texcoord" to 2),
            ),
            uv,
        )
        assertEquals(
            GPUVertexLayoutPlan(
                attributes = listOf("position", "color", "texcoord"),
                strideBytes = 20,
                offsets = mapOf("position" to 0, "color" to 8, "texcoord" to 12),
                shaderLocations = mapOf("position" to 0, "color" to 1, "texcoord" to 2),
            ),
            colorUv,
        )
    }

    @Test
    fun `vertex floats and uint16 and uint32 indices are little endian`() {
        val vertexResult = pack(
            positions = floatArrayOf(1f, -2f, 0f, 0f, 0f, 0f),
        ).ready()
        val uint16Result = pack(
            positions = FloatArray(259 * 2),
            indices = intArrayOf(0, 258, 1),
        ).ready()
        val uint32Result = pack(
            positions = FloatArray(65_537 * 2),
            indices = intArrayOf(0, 65_536, 1),
            supportsUint32Index = true,
        ).ready()

        assertContentEquals(
            byteArrayOf(0, 0, 128.toByte(), 63, 0, 0, 0, 192.toByte()),
            vertexResult.artifact.vertexBytesForUpload().copyOfRange(0, 8),
        )
        assertContentEquals(
            byteArrayOf(0, 0, 2, 1, 1, 0),
            requireNotNull(uint16Result.artifact.indexBytesForUpload()),
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0),
            requireNotNull(uint32Result.artifact.indexBytesForUpload()),
        )
    }

    @Test
    fun `color plus UV vertices match the complete sentinel interleaving`() {
        val result = pack(
            positions = floatArrayOf(1f, 2f, -1f, -2f, 0f, 0f),
            colorsRgba8 = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                100, 50, 200.toByte(), 128.toByte(),
                1, 3, 254.toByte(), 128.toByte(),
            ),
            texCoords = floatArrayOf(3f, 4f, -3f, -4f, 0.5f, -0.5f),
        ).ready()

        assertContentEquals(
            byteArrayOf(
                0, 0, 128.toByte(), 63, 0, 0, 0, 64,
                255.toByte(), 0, 0, 255.toByte(),
                0, 0, 64, 64, 0, 0, 128.toByte(), 64,
                0, 0, 128.toByte(), 191.toByte(), 0, 0, 0, 192.toByte(),
                50, 25, 100, 128.toByte(),
                0, 0, 64, 192.toByte(), 0, 0, 128.toByte(), 192.toByte(),
                0, 0, 0, 0, 0, 0, 0, 0,
                1, 2, 127, 128.toByte(),
                0, 0, 0, 63, 0, 0, 0, 191.toByte(),
            ),
            result.artifact.vertexBytesForUpload(),
        )
    }

    @Test
    fun `source bounds conservatively enclose all local positions`() {
        val result = pack(
            positions = floatArrayOf(3f, -2f, -4f, 5f, 1f, 0f),
        ).ready()

        assertEquals(
            GPUPreparedVerticesFloatBounds(left = -4f, top = -2f, right = 3f, bottom = 5f),
            result.sourceBounds,
        )
    }

    @Test
    fun `negative and out-of-range source indices refuse with deterministic facts`() {
        val negative = pack(indices = intArrayOf(0, -1, 2)).refused()
        val outOfRange = pack(indices = intArrayOf(0, 1, 3)).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.IndexOutOfRange, negative.code)
        assertEquals("-1", negative.facts["index"])
        assertEquals("1", negative.facts["indexPosition"])
        assertEquals(
            mapOf(
                "index" to "3",
                "indexPosition" to "2",
                "provenance" to "unit-test",
                "reason" to "index_out_of_range",
                "topology" to "Triangles",
                "vertexCount" to "3",
            ),
            outOfRange.facts,
        )
        assertEquals(outOfRange.facts.keys.sorted(), outOfRange.facts.keys.toList())
        assertEquals(
            outOfRange.facts,
            pack(indices = intArrayOf(0, 1, 3)).refused().facts,
        )
    }

    @Test
    fun `each configured packing budget refuses before an artifact is produced`() {
        val vertexCount = pack(limits = defaultLimits().copy(maxVertices = 2)).refused()
        val indexCount = pack(
            indices = intArrayOf(0, 1, 2),
            limits = defaultLimits().copy(maxIndices = 2),
        ).refused()
        val vertexBytes = pack(limits = defaultLimits().copy(maxVertexBytes = 23)).refused()
        val indexBytes = pack(
            indices = intArrayOf(0, 1, 2),
            limits = defaultLimits().copy(maxIndexBytes = 5),
        ).refused()
        val fanExpansion = pack(
            topology = GPUVertexMode.TriangleFan,
            positions = squarePositions(),
            limits = defaultLimits().copy(maxFanExpandedIndices = 5),
        ).refused()

        listOf(vertexCount, indexCount, vertexBytes, indexBytes, fanExpansion).forEach { refusal ->
            assertEquals(GPUPreparedVerticesRefusalCodes.Budget, refusal.code)
            assertEquals("budget_exceeded", refusal.facts["reason"])
            assertTrue(requireNotNull(refusal.facts["actual"]).toLong() > requireNotNull(refusal.facts["limit"]).toLong())
        }
        assertEquals("maxVertices", vertexCount.facts["budget"])
        assertEquals("maxIndices", indexCount.facts["budget"])
        assertEquals("maxVertexBytes", vertexBytes.facts["budget"])
        assertEquals("maxIndexBytes", indexBytes.facts["budget"])
        assertEquals("maxFanExpandedIndices", fanExpansion.facts["budget"])
    }

    @Test
    fun `invalid limits and checked allocation arithmetic refuse overflow`() {
        val invalidLimits = pack(
            limits = defaultLimits().copy(maxVertexBytes = -1),
        ).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, invalidLimits.code)
        assertEquals("invalid_limit", invalidLimits.facts["reason"])
        assertEquals("maxVertexBytes", invalidLimits.facts["budget"])
        assertEquals(40L, GPUPreparedVerticesPacker.checkedAllocationByteCount(2, 20))
        assertEquals(
            (Int.MAX_VALUE - 8).toLong(),
            GPUPreparedVerticesPacker.checkedAllocationByteCount(Int.MAX_VALUE - 8, 1),
        )
        assertNull(GPUPreparedVerticesPacker.checkedAllocationByteCount(Int.MAX_VALUE - 7, 1))
        assertNull(GPUPreparedVerticesPacker.checkedAllocationByteCount(Int.MAX_VALUE, 20))
        assertNull(GPUPreparedVerticesPacker.checkedAllocationByteCount(-1, 20))
    }

    @Test
    fun `shape and budget preflight precedes mutable content inspection`() {
        val vertexBudget = pack(
            positions = floatArrayOf(Float.NaN, 0f, 1f, 0f, 0f, 1f),
            limits = defaultLimits().copy(maxVertexBytes = 23),
        ).refused()
        val indexBudget = pack(
            indices = intArrayOf(0, 1, 3),
            limits = defaultLimits().copy(maxIndices = 2),
        ).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, vertexBudget.code)
        assertEquals("maxVertexBytes", vertexBudget.facts["budget"])
        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, indexBudget.code)
        assertEquals("maxIndices", indexBudget.facts["budget"])
    }

    @Test
    fun `minimum index byte budget precedes source index range inspection`() {
        val refusal = pack(
            indices = intArrayOf(0, 1, 3),
            limits = defaultLimits().copy(maxIndexBytes = 5),
        ).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, refusal.code)
        assertEquals("budget_exceeded", refusal.facts["reason"])
        assertEquals("maxIndexBytes", refusal.facts["budget"])
        assertEquals("6", refusal.facts["actual"])
        assertEquals("5", refusal.facts["limit"])
    }

    @Test
    fun `unsupported topology and blank provenance return canonical refusals`() {
        val topology = pack(topology = GPUVertexMode.Unsupported("Lines")).refused()
        val provenance = pack(provenance = " ").refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.Topology, topology.code)
        assertEquals("unsupported_topology", topology.facts["reason"])
        assertEquals(GPUPreparedVerticesRefusalCodes.AttributeLayout, provenance.code)
        assertEquals("provenance_blank", provenance.facts["reason"])
    }

    @Test
    fun `all packer refusals come from the canonical refusal authority`() {
        val refusals = listOf(
            pack(positions = floatArrayOf()).refused(),
            pack(colorsRgba8 = ByteArray(1)).refused(),
            pack(positions = floatArrayOf(Float.NaN, 0f, 1f, 0f, 0f, 1f)).refused(),
            pack(indices = intArrayOf(0, 1, 3)).refused(),
            pack(topology = GPUVertexMode.Unsupported("Lines")).refused(),
            pack(limits = defaultLimits().copy(maxVertices = 2)).refused(),
        )

        assertTrue(refusals.all { refusal -> refusal.code in GPUPreparedVerticesRefusalCodes.ALL })
    }

    @Test
    fun `mutating every source array after packing cannot change the artifact or bounds`() {
        val positions = squarePositions()
        val colors = opaqueColors(4)
        val uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val indices = intArrayOf(2, 3, 0, 1)
        val input = GPUPreparedVerticesArtifactInput(
            topology = GPUVertexMode.TriangleFan,
            positions = positions,
            colorsRgba8 = colors,
            texCoords = uvs,
            indices = indices,
            provenance = "mutation-fixture",
        )
        val result = GPUPreparedVerticesPacker.pack(input, defaultLimits(), supportsUint32Index = true).ready()
        val expectedKey = result.artifact.key
        val expectedVertexBytes = result.artifact.vertexBytesForUpload()
        val expectedIndexBytes = requireNotNull(result.artifact.indexBytesForUpload())
        val expectedBounds = result.sourceBounds

        positions.fill(99f)
        colors.fill(88)
        uvs.fill(77f)
        indices.fill(66)

        assertEquals(expectedKey, result.artifact.key)
        assertContentEquals(expectedVertexBytes, result.artifact.vertexBytesForUpload())
        assertContentEquals(expectedIndexBytes, result.artifact.indexBytesForUpload())
        assertEquals(expectedBounds, result.sourceBounds)
        assertContentEquals(
            intArrayOf(2, 3, 0, 2, 0, 1),
            decodeIndices(result.artifact.indexBytesForUpload(), result.artifact.indexFormat),
        )
    }
}

private fun pack(
    topology: GPUVertexMode = GPUVertexMode.Triangles,
    positions: FloatArray = trianglePositions(),
    colorsRgba8: ByteArray? = null,
    texCoords: FloatArray? = null,
    indices: IntArray? = null,
    provenance: String = "unit-test",
    limits: GPUPreparedVerticesPackingLimits = defaultLimits(),
    supportsUint32Index: Boolean = true,
): GPUPreparedVerticesPackingResult =
    GPUPreparedVerticesPacker.pack(
        input = GPUPreparedVerticesArtifactInput(
            topology = topology,
            positions = positions,
            colorsRgba8 = colorsRgba8,
            texCoords = texCoords,
            indices = indices,
            provenance = provenance,
        ),
        limits = limits,
        supportsUint32Index = supportsUint32Index,
    )

private fun GPUPreparedVerticesPackingResult.ready(): GPUPreparedVerticesPackingResult.Ready =
    assertIs(this)

private fun GPUPreparedVerticesPackingResult.refused(): GPUPreparedVerticesPackingResult.Refused =
    assertIs(this)

private fun decodeIndices(bytes: ByteArray?, format: String?): IntArray {
    val snapshot = requireNotNull(bytes)
    val buffer = ByteBuffer.wrap(snapshot).order(ByteOrder.LITTLE_ENDIAN)
    return when (format) {
        "uint16" -> IntArray(snapshot.size / 2) { buffer.short.toInt() and 0xffff }
        "uint32" -> IntArray(snapshot.size / 4) { buffer.int }
        else -> error("Unexpected index format $format")
    }
}

private fun trianglePositions(): FloatArray =
    floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)

private fun squarePositions(): FloatArray =
    floatArrayOf(0f, 0f, 4f, 0f, 4f, 4f, 0f, 4f)

private fun triangleUvs(): FloatArray =
    floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)

private fun opaqueColors(vertexCount: Int): ByteArray =
    ByteArray(vertexCount * 4) { index -> if (index % 4 == 3) 255.toByte() else (index + 1).toByte() }

private fun defaultLimits(): GPUPreparedVerticesPackingLimits =
    GPUPreparedVerticesPackingLimits(
        maxVertices = 100_000,
        maxIndices = 300_000,
        maxVertexBytes = 4_000_000,
        maxIndexBytes = 1_200_000,
        maxFanExpandedIndices = 300_000,
    )
