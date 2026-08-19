package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.preparedColorGlyphBlendPlan
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import kotlin.uuid.Uuid

class GPUPreparedColorGlyphMixedFrameTest {
    @Test
    fun `COLRv0 keeps layer color and applies paint alpha once`() {
        val semantic = preparedColorGlyph(
            paintAlpha = 0.5f,
            layers = listOf(
                layer(
                    glyphId = 21,
                    paletteIndex = 3,
                    premultipliedRgba = floatArrayOf(0.6f, 0.3f, 0.15f, 0.75f),
                    colorLayerIndex = 0,
                ),
            ),
        )

        assertFloatArrayEquals(
            floatArrayOf(0.3f, 0.15f, 0.075f, 0.375f),
            semantic.uniformLayerColors().single(),
        )
    }

    @Test
    fun `COLRv0 retains palette foreground and layer order`() {
        val semantic = preparedColorGlyph(
            paintAlpha = 1f,
            layers = listOf(
                layer(
                    glyphId = 21,
                    paletteIndex = 7,
                    premultipliedRgba = floatArrayOf(0.2f, 0.1f, 0f, 0.25f),
                    colorLayerIndex = 0,
                ),
                layer(
                    glyphId = 22,
                    paletteIndex = 0xffff,
                    premultipliedRgba = floatArrayOf(0.1f, 0.2f, 0.3f, 0.5f),
                    colorLayerIndex = 1,
                    useForeground = true,
                ),
            ),
        )

        assertEquals(listOf(21u, 22u), semantic.layers.map { it.layerGlyphID })
        assertEquals(listOf(7, 0xffff), semantic.layers.map { it.paletteIndex })
        assertEquals(listOf(false, true), semantic.layers.map { it.useForeground })
        assertFloatArrayEquals(
            floatArrayOf(0.2f, 0.1f, 0f, 0.25f),
            semantic.uniformLayerColors()[0],
        )
        assertFloatArrayEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.5f),
            semantic.uniformLayerColors()[1],
        )
    }

    @Test
    fun `A8 and COLRv0 same frame reaches the sealed native boundary`() {
        val fixture = preparedTextNativePreflightFixture(includeColorGlyph = true)
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture),
        )

        assertEquals(
            "test.prepared-surface.boundary",
            refused.diagnostic.code.value,
            refused.diagnostic.toString(),
        )
        assertEquals(1, probe.materializerInvocations)
    }

    private fun preparedColorGlyph(
        paintAlpha: Float,
        layers: List<GPUColorGlyphLayerPayloadInput>,
    ): GPUDrawSemanticPayload.ColorGlyph {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val atlas = page.toPreparedR8UploadArtifact()
        val material = solidGreenMaterial(paintAlpha)
        val blend = preparedColorGlyphBlendPlan()
        return GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(
            GPUPreparedColorGlyphPayloadInput(
                commandIdValue = 31,
                planArtifactKey = PLAN_KEY,
                atlasArtifactKey = ATLAS_KEY,
                atlas = atlas,
                instances = layers.mapIndexed { index, layer ->
                    GPUTextA8Instance.create(
                        glyphId = layer.layerGlyphID.toInt(),
                        sourceGlyphIndex = GPUTextSourceGlyphIndex(index),
                        deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                        uvRect = GPUTextFloatRect(
                            layer.atlasBounds.left / atlas.width.toFloat(),
                            layer.atlasBounds.top / atlas.height.toFloat(),
                            layer.atlasBounds.right / atlas.width.toFloat(),
                            layer.atlasBounds.bottom / atlas.height.toFloat(),
                        ),
                        pageIndex = 0,
                        colorLayerIndex = requireNotNull(layer.colorLayerIndex),
                    )
                },
                layers = layers,
                material = material,
                targetBounds = TARGET,
                scissorBounds = TARGET,
                clipIdentity = "clip:none",
                blendPlanIdentity = blend.canonicalIdentity(),
                capabilitySnapshotHash = "capability:task11",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
    }

    private fun layer(
        glyphId: Int,
        paletteIndex: Int,
        premultipliedRgba: FloatArray,
        colorLayerIndex: Int,
        useForeground: Boolean = false,
    ): GPUColorGlyphLayerPayloadInput {
        val atlasBounds = GPUPixelBounds(
            colorLayerIndex * 2,
            0,
            colorLayerIndex * 2 + 2,
            2,
        )
        return GPUColorGlyphLayerPayloadInput(
            planArtifactKey = PLAN_KEY,
            layerGlyphID = glyphId.toUInt(),
            paletteIndex = paletteIndex,
            atlasBounds = atlasBounds,
            deviceBounds = GPUPixelBounds(1 + colorLayerIndex, 1, 5 + colorLayerIndex, 5),
            premultipliedRgba = premultipliedRgba,
            useForeground = useForeground,
            foregroundResolved = true,
            placementProof = GPUColorGlyphAtlasPlacementProofInput(
                atlasArtifactKey = ATLAS_KEY,
                strikeGlyphId = glyphId,
                strikeSize = 16f,
                strikeSubpixelX = 0,
                strikeSubpixelY = 0,
                atlasBounds = atlasBounds,
            ),
            colorLayerIndex = colorLayerIndex,
        )
    }

    private fun solidGreenMaterial(paintAlpha: Float): GPUPreparedMaterialProgram {
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 1f),
            paintAlpha = paintAlpha,
            context = GPUMaterialLoweringContext(
                capabilityClass = "task11",
                targetFormatClass = "rgba8unorm",
                dictionaryVersion = "material-dictionary:task11:v1",
                runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
            ),
        )
        return assertIs<GPUPreparedMaterialProgramResult.Ready>(result).program
    }

    private fun GPUDrawSemanticPayload.ColorGlyph.uniformLayerColors(): List<FloatArray> {
        val buffer = ByteBuffer.wrap(uniformBytes.map(Int::toByte).toByteArray())
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(16)
        return List(layers.size) {
            FloatArray(4) { buffer.float }
        }
    }

    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index], absoluteTolerance = 0.000001f)
        }
    }

    private companion object {
        val TARGET = GPUPixelBounds(0, 0, 16, 16)
        val GENERATION = GPUTextArtifactGeneration(
            GPUPreparedTextPreflightFixture.GENERATION.toInt(),
        )
        val PLAN_KEY = GPUTextArtifactKey(
            GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-4466554400a1")),
            GENERATION,
            "task11-plan",
        )
        val ATLAS_KEY = GPUTextArtifactKey(
            GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-4466554400a2")),
            GENERATION,
            "task11-atlas",
        )
    }
}
