package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.TypefaceID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUTextArtifactsSurfaceTest {
    @Test
    fun `gpu text artifact surface is composed from dumpable value objects`() {
        val layoutID = GPUTextLayoutResultID("paragraph-1/layout-0")
        val runID = GPUGlyphRunID("paragraph-1/run-0")
        val runReference = GPUTextLayoutRunReference(
            layoutResultID = layoutID,
            glyphRunID = runID,
            runIndex = 0,
        )
        val runDescriptor = GPUGlyphRunDescriptor(
            runID = runID,
            layoutResultID = layoutID,
            typefaceID = TypefaceID("face-a"),
            glyphIDs = listOf(42),
            advances = listOf(11.5f),
            textRangeStart = 0,
            textRangeEnd = 1,
            script = "Latn",
            bidiLevel = 0,
        )
        val artifactID = GPUTextArtifactID("face-a/size-18/subpixel-0")
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
        assertEquals("paragraph-1/layout-0", runReference.layoutResultID.value)
        assertEquals("face-a", runDescriptor.typefaceID?.value)
        assertEquals(192, bundle.uploadPlans.single().byteSize)
        assertEquals(42U, bundle.glyphUploadPlans.single().glyphIDs.single())
        assertEquals("r8", bundle.atlases.single().format)
        assertTrue(bundle.diagnostics.refusalRequired)
        assertFalse(bundle.diagnostics.isEmpty)
    }
}
