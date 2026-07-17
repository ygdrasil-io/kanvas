package org.graphiks.kanvas.gpu.renderer.payloads

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import kotlin.uuid.Uuid

class GPUColorGlyphPayloadGathererTest {
    @Test
    fun `color glyph semantic payload owns immutable atlas layers geometry indices uniforms and dumps`() {
        val atlasA8Bytes = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val firstColor = floatArrayOf(1f, 0f, 0f, 1f)
        val secondColor = floatArrayOf(0f, 0f, 0.5f, 0.5f)
        val layers = mutableListOf(
            layer(
                11u,
                GPUPixelBounds(0, 0, 1, 2),
                firstColor,
                deviceBounds = GPUPixelBounds(0, 0, 2, 2),
            ),
            layer(
                12u,
                GPUPixelBounds(1, 0, 2, 2),
                secondColor,
                deviceBounds = GPUPixelBounds(2, 2, 4, 4),
            ),
        )
        val vertices = validVertices()
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)
        val uniformBytes = validUniformBytes(layers)

        val semantic = gather(
            atlasA8Bytes = atlasA8Bytes,
            layers = layers,
            vertexData = vertices,
            indexData = indices,
            uniformBytes = uniformBytes,
        )
        val stableHash = semantic.canonicalHash
        val stableDump = semantic.stableDumpLines()

        atlasA8Bytes.fill(0)
        firstColor.fill(0f)
        secondColor.fill(1f)
        layers.clear()
        vertices.fill(-1f)
        indices.fill(-1)
        uniformBytes.fill(0)

        val colorGlyph = assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantic)
        assertEquals("ColorGlyph", colorGlyph.canonicalType)
        assertEquals(GPUColorGlyphAtlasFormat.R8Unorm, colorGlyph.atlasFormat)
        assertEquals(listOf(0x10, 0x20, 0x30, 0x40), colorGlyph.atlasA8Bytes)
        assertEquals(2, colorGlyph.atlasWidth)
        assertEquals(2, colorGlyph.atlasHeight)
        assertEquals(7L, colorGlyph.atlasGeneration)
        assertEquals(
            "f4e3f0b04771c047e227c9ecaba65d3fe2fd0e1eee0a7552b956d1a7c535a7cf",
            colorGlyph.atlasBytesSha256,
        )
        assertEquals(planArtifactKey(), colorGlyph.planArtifactKey)
        assertEquals(atlasArtifactKey(), colorGlyph.atlasArtifactKey)
        assertEquals(TARGET_BOUNDS, colorGlyph.targetBounds)
        assertEquals(SCISSOR_BOUNDS, colorGlyph.scissorBounds)
        assertEquals(listOf(11u, 12u), colorGlyph.layers.map { it.layerGlyphID })
        assertEquals(
            listOf(
                GPUPixelBounds(0, 0, 1, 2),
                GPUPixelBounds(1, 0, 2, 2),
            ),
            colorGlyph.layers.map { it.atlasBounds },
        )
        assertEquals(
            listOf(
                GPUPixelBounds(0, 0, 2, 2),
                GPUPixelBounds(2, 2, 4, 4),
            ),
            colorGlyph.layers.map { it.deviceBounds },
        )
        assertEquals(
            listOf(listOf(1f, 0f, 0f, 1f), listOf(0f, 0f, 0.5f, 0.5f)),
            colorGlyph.layers.map { it.premultipliedRgba },
        )
        assertEquals(validVertices().toList(), colorGlyph.vertexData)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), colorGlyph.indexData)
        assertEquals(784, colorGlyph.uniformBytes.size)
        assertEquals(stableHash, colorGlyph.canonicalHash)
        assertEquals(stableDump, colorGlyph.stableDumpLines())
        assertTrue(stableDump.any { "deviceBounds=0,0,2,2" in it })
        assertTrue(colorGlyph.hasCanonicalHashIntegrity())
        assertNotEquals(colorGlyph.canonicalHash, colorGlyph.payloadRef.uniformBlock!!.fingerprint.value)
    }

    @Test
    fun `color glyph canonical integrity covers uniform slot block metadata bytes and fingerprint`() {
        val base = gather()
        val slot = requireNotNull(base.payloadRef.uniformSlot)
        val block = requireNotNull(base.payloadRef.uniformBlock)
        val changedBytes = block.bytes.toMutableList().also { it[0] = it[0] xor 1 }
        val changedFields = block.fields.toMutableList().also {
            it[0] = it[0].copy(valueClass = "substituted")
        }
        val mutations = listOf(
            base.payloadRef.copy(uniformSlot = slot.copy(slotId = GPUPayloadSlotID("color-glyph:41:other"))),
            base.payloadRef.copy(uniformSlot = slot.copy(byteOffset = 16L)),
            base.payloadRef.copy(uniformSlot = slot.copy(fingerprint = GPUPayloadFingerprint("1".repeat(64)))),
            base.payloadRef.copy(uniformBlock = block.copy(fingerprint = GPUPayloadFingerprint("2".repeat(64)))),
            base.payloadRef.copy(uniformBlock = block.copy(packingPlanHash = "substituted-layout")),
            base.payloadRef.copy(uniformBlock = block.copy(byteSize = block.byteSize + 16L)),
            base.payloadRef.copy(uniformBlock = block.copy(zeroedPadding = !block.zeroedPadding)),
            base.payloadRef.copy(uniformBlock = block.copy(scope = "color-glyph:other")),
            base.payloadRef.copy(uniformBlock = block.copy(bytes = changedBytes)),
            base.payloadRef.copy(uniformBlock = block.copy(fields = changedFields)),
        )

        mutations.forEach { mutatedRef ->
            assertFalse(base.copyForIntegrityTest(payloadRef = mutatedRef).hasCanonicalHashIntegrity())
        }
    }

    @Test
    fun `color glyph device bounds participate in canonical hash and dump`() {
        val original = gather()
        val movedLayers = validLayers().mapIndexed { index, layer ->
            if (index == 0) layer.copy(deviceBounds = GPUPixelBounds(0, 1, 2, 3)) else layer
        }
        val moved = gather(layers = movedLayers, uniformBytes = validUniformBytes(movedLayers))

        assertNotEquals(original.canonicalHash, moved.canonicalHash)
        assertNotEquals(original.stableDumpLines(), moved.stableDumpLines())
        assertTrue(moved.stableDumpLines().any { "deviceBounds=0,1,2,3" in it })
    }

    @Test
    fun `color glyph gatherer refuses malformed atlas layers geometry uniforms generation and bounds`() {
        val validLayers = validLayers()
        val refusalCases = listOf<Pair<String, () -> Unit>>(
            "missing atlas bytes" to { gather(atlasA8Bytes = byteArrayOf()) },
            "atlas byte size mismatch" to { gather(atlasA8Bytes = byteArrayOf(1, 2, 3)) },
            "unsupported atlas format" to { gather(atlasFormat = "rgba8unorm") },
            "no layer" to { gather(layers = emptyList()) },
            "too many layers" to { gather(layers = List(17) { validLayers.first() }) },
            "plan identity mismatch" to {
                gather(layers = listOf(layer(11u, planKey = planArtifactKey(generation = 6))))
            },
            "placement atlas identity mismatch" to {
                gather(layers = listOf(layer(11u, placementAtlasKey = atlasArtifactKey(generation = 6))))
            },
            "placement glyph identity mismatch" to {
                gather(layers = listOf(layer(11u, placementGlyphId = 12)))
            },
            "atlas generation mismatch" to {
                gather(atlasKey = atlasArtifactKey(generation = 6))
            },
            "blank plan artifact fingerprint" to {
                gather(planKey = planArtifactKey(fingerprint = ""))
            },
            "unsafe atlas artifact fingerprint" to {
                gather(atlasKey = atlasArtifactKey(fingerprint = "atlas\ncontent"))
            },
            "plan and atlas identity alias" to {
                val aliased = planArtifactKey()
                gather(planKey = aliased, atlasKey = aliased)
            },
            "layer outside atlas" to {
                gather(layers = listOf(layer(11u, bounds = GPUPixelBounds(1, 1, 3, 2))))
            },
            "empty device bounds" to {
                gather(layers = listOf(layer(11u, deviceBounds = GPUPixelBounds(1, 1, 1, 2))))
            },
            "device bounds outside target" to {
                gather(layers = listOf(layer(11u, deviceBounds = GPUPixelBounds(3, 3, 5, 4))))
            },
            "unresolved foreground" to {
                gather(layers = listOf(layer(11u, useForeground = true, foregroundResolved = false)))
            },
            "non finite color" to {
                gather(layers = listOf(layer(11u, color = floatArrayOf(Float.NaN, 0f, 0f, 1f))))
            },
            "non premultiplied color" to {
                gather(layers = listOf(layer(11u, color = floatArrayOf(1f, 0f, 0f, 0.5f))))
            },
            "malformed indexed vertices" to { gather(vertexData = floatArrayOf(0f, 0f, 0f)) },
            "uv below normalized range" to {
                gather(vertexData = validVertices().also { it[2] = -0.1f })
            },
            "uv above normalized range" to {
                gather(vertexData = validVertices().also { it[3] = 1.1f })
            },
            "non canonical quad topology" to {
                gather(indexData = intArrayOf(0, 2, 1, 0, 3, 2))
            },
            "index outside vertex range" to { gather(indexData = intArrayOf(0, 1, 4)) },
            "negative index" to { gather(indexData = intArrayOf(0, -1, 2)) },
            "uniform ABI size mismatch" to { gather(uniformBytes = ByteArray(783)) },
            "uniform layer facts mismatch" to {
                gather(uniformBytes = validUniformBytes(validLayers, declaredLayerCount = 1))
            },
            "uniform device bounds mismatch" to {
                val bytes = validUniformBytes(validLayers()).also {
                    ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putFloat(528, 3f)
                }
                gather(uniformBytes = bytes)
            },
            "scissor outside target" to {
                gather(scissorBounds = GPUPixelBounds(0, 0, 5, 4))
            },
        )

        refusalCases.forEach { (name, action) ->
            assertFailsWith<IllegalArgumentException>(name) { action() }
        }
    }

    private fun gather(
        atlasA8Bytes: ByteArray = byteArrayOf(0x10, 0x20, 0x30, 0x40),
        atlasFormat: String = "r8unorm",
        planKey: GPUTextArtifactKey = planArtifactKey(),
        atlasKey: GPUTextArtifactKey = atlasArtifactKey(),
        layers: List<GPUColorGlyphLayerPayloadInput> = validLayers(),
        vertexData: FloatArray = validVertices(),
        indexData: IntArray = intArrayOf(0, 1, 2, 0, 2, 3),
        uniformBytes: ByteArray = validUniformBytes(layers),
        scissorBounds: GPUPixelBounds = SCISSOR_BOUNDS,
    ): GPUDrawSemanticPayload.ColorGlyph = GPUColorGlyphPayloadGatherer().gatherSemantic(
        commandIdValue = 41,
        renderStepIdentity = COLOR_GLYPH_RENDER_STEP_IDENTITY,
        planArtifactKey = planKey,
        atlasArtifactKey = atlasKey,
        atlasA8Bytes = atlasA8Bytes,
        atlasWidth = 2,
        atlasHeight = 2,
        atlasFormat = atlasFormat,
        atlasGeneration = 7L,
        layers = layers,
        vertexData = vertexData,
        indexData = indexData,
        uniformBytes = uniformBytes,
        targetBounds = TARGET_BOUNDS,
        scissorBounds = scissorBounds,
    )

    private fun GPUDrawSemanticPayload.ColorGlyph.copyForIntegrityTest(
        payloadRef: GPUDrawPayloadRef = this.payloadRef,
    ) = GPUDrawSemanticPayload.ColorGlyph(
        payloadRef = payloadRef,
        planArtifactKey = planArtifactKey,
        atlasArtifactKey = atlasArtifactKey,
        atlasA8Bytes = atlasA8Bytes,
        atlasWidth = atlasWidth,
        atlasHeight = atlasHeight,
        atlasFormat = atlasFormat,
        atlasGeneration = atlasGeneration,
        layers = layers,
        vertexData = vertexData,
        indexData = indexData,
        uniformBytes = uniformBytes,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        canonicalHash = canonicalHash,
    )

    private fun validLayers(): List<GPUColorGlyphLayerPayloadInput> = listOf(
        layer(
            11u,
            GPUPixelBounds(0, 0, 1, 2),
            floatArrayOf(1f, 0f, 0f, 1f),
            deviceBounds = GPUPixelBounds(0, 0, 2, 2),
        ),
        layer(
            12u,
            GPUPixelBounds(1, 0, 2, 2),
            floatArrayOf(0f, 0f, 0.5f, 0.5f),
            deviceBounds = GPUPixelBounds(2, 2, 4, 4),
        ),
    )

    private fun layer(
        glyphId: UInt,
        bounds: GPUPixelBounds = GPUPixelBounds(0, 0, 1, 2),
        color: FloatArray = floatArrayOf(1f, 0f, 0f, 1f),
        planKey: GPUTextArtifactKey = planArtifactKey(),
        useForeground: Boolean = false,
        foregroundResolved: Boolean = true,
        placementAtlasKey: GPUTextArtifactKey = atlasArtifactKey(),
        placementGlyphId: Int = glyphId.toInt(),
        deviceBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 2, 2),
    ): GPUColorGlyphLayerPayloadInput = GPUColorGlyphLayerPayloadInput(
        planArtifactKey = planKey,
        layerGlyphID = glyphId,
        paletteIndex = 0,
        atlasBounds = bounds,
        deviceBounds = deviceBounds,
        premultipliedRgba = color,
        useForeground = useForeground,
        foregroundResolved = foregroundResolved,
        placementProof = GPUColorGlyphAtlasPlacementProofInput(
            atlasArtifactKey = placementAtlasKey,
            strikeGlyphId = placementGlyphId,
            strikeSize = 48f,
            strikeSubpixelX = 0,
            strikeSubpixelY = 0,
            atlasBounds = bounds,
        ),
    )

    private fun planArtifactKey(
        generation: Int = 7,
        fingerprint: String = "color-glyph-plan",
    ) = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440041")),
        generation = GPUTextArtifactGeneration(generation),
        contentFingerprint = fingerprint,
    )

    private fun atlasArtifactKey(
        generation: Int = 7,
        fingerprint: String = "color-glyph-atlas",
    ) = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440042")),
        generation = GPUTextArtifactGeneration(generation),
        contentFingerprint = fingerprint,
    )

    private fun validVertices(): FloatArray = floatArrayOf(
        0f, 0f, 0f, 0f,
        4f, 0f, 1f, 0f,
        4f, 4f, 1f, 1f,
        0f, 4f, 0f, 1f,
    )

    private fun validUniformBytes(
        layers: List<GPUColorGlyphLayerPayloadInput>,
        declaredLayerCount: Int = layers.size,
    ): ByteArray = ByteBuffer.allocate(784).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(TARGET_BOUNDS.width.toFloat())
        putFloat(TARGET_BOUNDS.height.toFloat())
        putInt(declaredLayerCount)
        putInt(0)
        repeat(16) { index ->
            val color = layers.getOrNull(index)?.premultipliedRgba ?: floatArrayOf(0f, 0f, 0f, 0f)
            color.forEach(::putFloat)
        }
        repeat(16) { index ->
            val bounds = layers.getOrNull(index)?.atlasBounds ?: GPUPixelBounds(0, 0, 0, 0)
            putFloat(bounds.left / 2f)
            putFloat(bounds.top / 2f)
            putFloat(bounds.width / 2f)
            putFloat(bounds.height / 2f)
        }
        repeat(16) { index ->
            val bounds = layers.getOrNull(index)?.deviceBounds ?: GPUPixelBounds(0, 0, 0, 0)
            putFloat(bounds.left.toFloat())
            putFloat(bounds.top.toFloat())
            putFloat(bounds.width.toFloat())
            putFloat(bounds.height.toFloat())
        }
    }.array()

    private companion object {
        val TARGET_BOUNDS = GPUPixelBounds(0, 0, 4, 4)
        val SCISSOR_BOUNDS = GPUPixelBounds(0, 0, 4, 4)
    }
}
