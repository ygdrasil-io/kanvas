package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.TypefaceID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextArtifactsSurfaceTest {
    @Test
    fun `gpu text artifact surface is composed from dumpable value objects`() {
        val layoutID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655440200"))
        val runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655440201"))
        val runReference = GPUTextLayoutRunReference(
            layoutResultID = layoutID,
            glyphRunID = runID,
            runIndex = 0,
        )
        val runDescriptor = GPUGlyphRunDescriptor(
            runID = runID,
            layoutResultID = layoutID,
            typefaceID = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440202")),
            glyphIDs = listOf(42),
            advances = listOf(11.5f),
            textRangeStart = 0,
            textRangeEnd = 1,
            script = "Latn",
            bidiLevel = 0,
        )
        val artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440203"))
        val generation = GPUTextArtifactGeneration(7)
        val key = GPUTextArtifactKey(
            artifactID = artifactID,
            generation = generation,
            contentFingerprint = "latin-run-001",
        )
        val uploadRange = GPUTextUploadRange(
            offset = 64,
            size = 128,
            label = "sdf-page-0",
        )
        val uploadPlan = GPUTextUploadPlan(
            artifactKey = key,
            ranges = listOf(uploadRange),
            byteSize = 192,
        )
        val glyphUploadPlan = GlyphUploadPlan(
            artifactKey = key,
            uploadPlan = uploadPlan,
            glyphIDs = listOf(42U),
        )
        val outlinePlan = OutlineGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            windingRule = "non-zero",
        )
        val colorPlan = ColorGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            layerCount = 2,
        )
        val bitmapPlan = BitmapGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            colorFormat = "rgba8888",
        )
        val svgPlan = SVGGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            documentCount = 1,
        )
        val atlas = GlyphAtlasArtifact(
            artifactKey = key,
            width = 256,
            height = 256,
            format = "r8",
        )
        val sdfAtlas = SDFGlyphAtlasArtifact(
            atlas = atlas,
            distanceRange = 4.0f,
        )
        val diagnostic = GPUTextArtifactDiagnostic(
            code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
            message = "Glyph 42 is unavailable in the planned atlas.",
            artifactKey = key,
        )
        val routeDiagnostics = GPUTextRouteDiagnostics(
            diagnostics = listOf(diagnostic),
            refusalRequired = true,
        )
        val bundle = TextGPUArtifactBundle(
            artifactKey = key,
            uploadPlans = listOf(uploadPlan),
            glyphUploadPlans = listOf(glyphUploadPlan),
            outlineGlyphPlans = listOf(outlinePlan),
            colorGlyphPlans = listOf(colorPlan),
            bitmapGlyphPlans = listOf(bitmapPlan),
            svgGlyphPlans = listOf(svgPlan),
            atlases = listOf(atlas),
            sdfAtlases = listOf(sdfAtlas),
            diagnostics = routeDiagnostics,
        )

        assertEquals(artifactID, key.artifactID)
        assertEquals("550e8400-e29b-41d4-a716-446655440200", runReference.layoutResultID.value.toHexDashString())
        assertEquals("550e8400-e29b-41d4-a716-446655440202", runDescriptor.typefaceID?.value?.toHexDashString())
        assertEquals(192, bundle.uploadPlans.single().byteSize)
        assertEquals(42U, bundle.glyphUploadPlans.single().glyphIDs.single())
        assertEquals("r8", bundle.atlases.single().format)
        assertTrue(bundle.diagnostics.refusalRequired)
        assertFalse(bundle.diagnostics.isEmpty)
    }
}
