package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlanner

/** Statistics from text A8 atlas execution. */
data class TextA8AtlasStats(
    val atlasWidth: Int,
    val atlasHeight: Int,
    val glyphCount: Int,
    val uploadSizeBytes: Long,
    val accepted: Boolean,
    val diagnostic: String? = null,
)

/** Plans and executes A8 glyph atlas uploads. */
class TextA8AtlasExecutor {
    /** Executes atlas preparation for the given key and dimensions. */
    fun execute(atlasKey: String, width: Int, height: Int): TextA8AtlasStats {
        val planner = GlyphAtlasUploadPlanner()
        val plan = planner.plan(emptyList())
        return when (plan) {
            is GlyphAtlasUploadPlan.Accepted -> TextA8AtlasStats(
                atlasWidth = plan.atlasWidth,
                atlasHeight = plan.atlasHeight,
                glyphCount = plan.placements.size,
                uploadSizeBytes = plan.atlasBytes.size.toLong(),
                accepted = true,
            )
            is GlyphAtlasUploadPlan.Refused -> TextA8AtlasStats(
                atlasWidth = 0,
                atlasHeight = 0,
                glyphCount = 0,
                uploadSizeBytes = 0L,
                accepted = false,
                diagnostic = plan.reason,
            )
        }
    }

    companion object {
        const val nonClaimLine: String =
            "nonclaim:no-gpu-atlas-pages no-backend-texture-allocation no-cpu-bitmap-upload"
    }
}
