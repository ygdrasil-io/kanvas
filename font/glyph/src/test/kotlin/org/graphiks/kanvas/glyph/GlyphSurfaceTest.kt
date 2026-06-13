package org.graphiks.kanvas.glyph

import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the pure Kotlin glyph package exposes the planned public surface.
 */
class GlyphSurfaceTest {
    /**
     * References every top-level glyph pipeline type so the module fails to compile until the
     * surface exists.
     */
    @Test
    fun exposesGlyphPipelineSurface() {
        val names = listOf(
            GPUGlyphRunDescriptor::class.simpleName,
            GlyphStrikeKey::class.simpleName,
            GlyphRepresentation::class.simpleName,
            GlyphArtifactPlanner::class.simpleName,
            GlyphArtifactPlan::class.simpleName,
            OutlineGlyphRepresentation::class.simpleName,
            GlyphMaskGenerator::class.simpleName,
            A8GlyphMask::class.simpleName,
            SDFGlyphGenerator::class.simpleName,
            SDFGlyphMask::class.simpleName,
            GlyphAtlasPacker::class.simpleName,
            GlyphAtlasArtifactBuilder::class.simpleName,
            SDFGlyphAtlasArtifactBuilder::class.simpleName,
            GlyphCache::class.simpleName,
            GlyphCacheBudget::class.simpleName,
            GlyphRouteDiagnostic::class.simpleName,
        )

        assertEquals(
            listOf(
                "GPUGlyphRunDescriptor",
                "GlyphStrikeKey",
                "GlyphRepresentation",
                "GlyphArtifactPlanner",
                "GlyphArtifactPlan",
                "OutlineGlyphRepresentation",
                "GlyphMaskGenerator",
                "A8GlyphMask",
                "SDFGlyphGenerator",
                "SDFGlyphMask",
                "GlyphAtlasPacker",
                "GlyphAtlasArtifactBuilder",
                "SDFGlyphAtlasArtifactBuilder",
                "GlyphCache",
                "GlyphCacheBudget",
                "GlyphRouteDiagnostic",
            ),
            names,
        )
    }
}
