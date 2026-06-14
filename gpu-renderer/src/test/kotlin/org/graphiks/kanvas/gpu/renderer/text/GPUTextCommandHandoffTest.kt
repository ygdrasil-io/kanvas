package org.graphiks.kanvas.gpu.renderer.text

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
            "unsupported.text.bitmap_route_unsupported",
            "unsupported.text.svg_plan_unsupported",
            "unsupported.text.lcd_future_research",
            "unsupported.text.instance_buffer_budget_exceeded",
            "unsupported.text.binding_layout_unavailable",
            "unsupported.text.destination_read_unaccepted",
            "unsupported.text.clip_route_unaccepted",
            "unsupported.text.cpu_rendered_texture_forbidden",
        )

        assertEquals(expectedCodes, GPUTextDiagnosticCodes.all)
    }

    @Test
    fun `draw text run payload type surface does not leak skia objects`() {
        val forbiddenFieldTypes = NormalizedDrawCommand.DrawTextRun::class.java.declaredFields
            .map { field -> field.type.name }
            .filter { typeName -> typeName.contains("org.skia.") || typeName.substringAfterLast('.').startsWith("Sk") }

        assertEquals(emptyList(), forbiddenFieldTypes)
    }
}
