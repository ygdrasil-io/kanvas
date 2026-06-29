package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactReference
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextCommandHandoffTest {
    @Test
    fun `draw text run command carries only dumpable text artifact facts`() {
        val diagnostic = GPUTextDiagnostic(
            code = GPUTextDiagnosticCodes.ARTIFACT_UNREGISTERED,
            layoutId = "layout-42",
            message = "Glyph atlas artifact is not registered.",
            terminal = true,
        )
        val artifactRef = GPUTextArtifactRef(
            artifactType = "GlyphAtlasArtifact",
            artifactId = "artifact-9",
            artifactKeyHash = "glyph-atlas-sha256",
            generationToken = "atlas-generation-3",
            routeHint = "AtlasMaskSample",
        )
        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(42),
            textLayoutResultId = "layout-42",
            glyphRunId = "run-7",
            glyphRunDescriptorRefs = listOf("run-7"),
            artifactRefs = listOf(artifactRef),
            artifactKeyHashes = listOf("glyph-atlas-sha256"),
            atlasGenerationTokens = listOf("atlas-generation-3"),
            uploadDependencyFacts = listOf("upload-before-sample"),
            routeDiagnostics = listOf(diagnostic),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 128f, 64f)),
            layer = GPULayerFacts.root(
                target = GPUTargetFacts(
                    width = 128,
                    height = 64,
                    colorFormat = "rgba8unorm",
                ),
            ),
            material = GPUMaterialDescriptor.SolidColor(
                r = 0f,
                g = 0f,
                b = 0f,
                a = 1f,
            ),
            bounds = GPUBounds(0f, 0f, 128f, 64f),
            ordering = GPUOrderingFacts(
                paintOrder = 4,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = GPUCommandSource(adapter = "unit-test", operation = "drawTextRun"),
        )

        assertEquals(GPUDrawKind.DrawTextRun, command.drawKind)
        assertEquals("unit-test:drawTextRun#42", command.diagnosticName)
        assertEquals("layout-42", command.textLayoutResultId)
        assertEquals("run-7", command.glyphRunId)
        assertEquals(listOf(artifactRef), command.artifactRefs)
        assertEquals("GlyphAtlasArtifact", command.artifactRefs.single().artifactType)
        assertEquals(listOf("glyph-atlas-sha256"), command.artifactKeyHashes)
        assertEquals(GPUTextDiagnosticCodes.ARTIFACT_UNREGISTERED, command.routeDiagnostics.single().code)
        assertFalse(command.ordering.dependsOnDestination)
    }

    @Test
    fun `text diagnostic codes use stable renderer refusal names`() {
        val expectedCodes = listOf(
            "unsupported.text.payload_nondumpable",
            "unsupported.text.sk_type_leaked",
            "unsupported.text.artifact_unregistered",
            "unsupported.text.artifact_key_nondeterministic",
            "unsupported.text.artifact_generation_stale",
            "unsupported.text.artifact_budget_exceeded",
            "unsupported.text.upload_plan_missing",
            "unsupported.text.upload_budget_exceeded",
            "unsupported.text.upload_failed",
            "unsupported.text.atlas_descriptor_unaccepted",
            "unsupported.text.atlas_page_unavailable",
            "unsupported.text.atlas_entry_missing",
            "unsupported.text.atlas_generation_stale",
            "unsupported.text.a8_atlas_route_unavailable",
            "unsupported.text.sdf_route_unavailable",
            "unsupported.text.sdf_params_missing",
            "unsupported.text.sdf_transform_unsupported",
            "unsupported.text.outline_route_unavailable",
            "unsupported.text.color_plan_unsupported",
            "unsupported.text.color_composite_unsupported",
            "unsupported.text.color_font.format_unavailable",
            "unsupported.text.color_font.layer_count_exceeded",
            "unsupported.text.bitmap_route_unsupported",
            "unsupported.text.svg_plan_unsupported",
            "dependency.text.emoji_color_glyph_unavailable",
            "unsupported.text.lcd_future_research",
            "unsupported.text.instance_buffer_budget_exceeded",
            "unsupported.text.binding_layout_unavailable",
            "unsupported.text.destination_read_unaccepted",
            "unsupported.text.clip_route_unaccepted",
            "unsupported.text.cpu_rendered_texture_forbidden",
            "unsupported.text.subpixel_pixel_geometry",
            "unsupported.text.subpixel_target_format",
            "unsupported.text.fallback_exhausted",
        )

        assertEquals(expectedCodes, GPUTextDiagnosticCodes.all)
    }

    @Test
    fun `text representation gate matrix keeps unsupported glyph representations refused`() {
        val gates = GPUTextRepresentationGateMatrix.byRepresentation()

        assertEquals(GPUTextDiagnosticCodes.A8_ATLAS_ROUTE_UNAVAILABLE, gates.getValue("A8MaskAtlas").diagnosticCode)
        assertEquals(GPUTextDiagnosticCodes.SDF_ROUTE_UNAVAILABLE, gates.getValue("SDFMaskAtlas").diagnosticCode)
        assertEquals(GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED, gates.getValue("COLRColorGlyph").diagnosticCode)
        assertEquals(GPUTextDiagnosticCodes.BITMAP_ROUTE_UNSUPPORTED, gates.getValue("BitmapGlyph").diagnosticCode)
        assertEquals(GPUTextDiagnosticCodes.SVG_PLAN_UNSUPPORTED, gates.getValue("SVGGlyph").diagnosticCode)
        assertEquals(
            GPUTextDiagnosticCodes.EMOJI_COLOR_GLYPH_UNAVAILABLE,
            gates.getValue("EmojiColorGlyph").diagnosticCode,
        )
        assertEquals(GPUTextDiagnosticCodes.LCD_FUTURE_RESEARCH, gates.getValue("LCDMask").diagnosticCode)
        assertEquals(GPUTextDiagnosticCodes.CPU_RENDERED_TEXTURE_FORBIDDEN, gates.getValue("CPURenderedTextTexture").diagnosticCode)
        assertTrue(gates.getValue("COLRColorGlyph").promoted)
        assertFalse(gates.minus("COLRColorGlyph").values.any { gate -> gate.promoted })
    }

    @Test
    fun `text representation gate dump keeps legacy gates and non claims visible`() {
        val dumpLines = GPUTextRepresentationGateMatrix.dumpLines()

        assertEquals(
            listOf(
                "A8MaskAtlas|unsupported.text.a8_atlas_route_unavailable|dftext|not-promoted",
                "SDFMaskAtlas|unsupported.text.sdf_route_unavailable|dftext|not-promoted",
                "COLRColorGlyph|unsupported.text.color_plan_unsupported|coloremoji_blendmodes|promoted",
                "BitmapGlyph|unsupported.text.bitmap_route_unsupported|scaledemoji_rendering|not-promoted",
                "SVGGlyph|unsupported.text.svg_plan_unsupported|scaledemoji_rendering|not-promoted",
                "EmojiColorGlyph|dependency.text.emoji_color_glyph_unavailable|scaledemoji_rendering,coloremoji_blendmodes|not-promoted",
                "LCDMask|unsupported.text.lcd_future_research|dftext|not-promoted",
                "CPURenderedTextTexture|unsupported.text.cpu_rendered_texture_forbidden|dftext,scaledemoji_rendering,coloremoji_blendmodes|not-promoted",
            ),
            dumpLines,
        )
    }

    @Test
    fun `font gpu artifact reference maps to renderer draw text run artifact facts`() {
        val fontReference = GPUTextArtifactReference(
            artifactName = "GlyphAtlasArtifact",
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655449001")),
            generation = GPUTextArtifactGeneration(3),
            contentFingerprint = "glyph-atlas-sha256",
            sourceLabel = "TextGPUArtifactBundle.atlases",
        )

        val rendererReference = fontReference.toRendererTextArtifactRef(routeHint = "AtlasMaskSample")

        assertEquals("GlyphAtlasArtifact", rendererReference.artifactType)
        assertEquals("550e8400-e29b-41d4-a716-446655449001", rendererReference.artifactId)
        assertEquals("glyph-atlas-sha256", rendererReference.artifactKeyHash)
        assertEquals("3", rendererReference.generationToken)
        assertEquals("AtlasMaskSample", rendererReference.routeHint)
    }

    @Test
    fun `draw text run payload type surface does not leak skia objects`() {
        val forbiddenFieldTypes = NormalizedDrawCommand.DrawTextRun::class.java.declaredFields
            .map { field -> field.type.name }
            .filter { typeName -> typeName.contains("org.skia.") || typeName.substringAfterLast('.').startsWith("Sk") }

        assertEquals(emptyList(), forbiddenFieldTypes)
    }
}
