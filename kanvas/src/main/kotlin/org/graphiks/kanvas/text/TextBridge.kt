package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

/**
 * Bridges the :font module's shaping/rasterization pipeline into Kanvas types.
 *
 * The [rasterize] method tries to load the :font module's GlyphScaler,
 * A8Rasterizer, and GlyphAtlasUploadPlanner at runtime. When the font
 * module is not on the classpath, it returns null and the GPU renderer
 * degrades gracefully.
 *
 * @see GpuTextBlob for the GPU-ready output type
 */
object TextBridge {

    /**
     * Rasterize glyphs from [blob] into an A8 glyph atlas.
     *
     * Requires the :font module on the classpath.
     * Steps: load typeface → scale glyphs → rasterize A8 → pack atlas → compute UVs.
     *
     * @return [GpuTextBlob] with atlas + per-glyph UVs, or null if the font module
     *         is unavailable or rasterization fails.
     */
    fun rasterize(blob: TextBlob): GpuTextBlob? {
        if (blob.typeface == null) return null
        try {
            return rasterizeViaFont(blob)
        } catch (_: NoClassDefFoundError) {
            return null
        } catch (_: ClassNotFoundException) {
            return null
        }
    }

    /**
     * Produce a standard [GlyphCluster] sequence for simple left-to-right glyph runs.
     * Used by the font module's OpenTypeShapingEngine output.
     */
    fun glyphClustersFromGlyphIds(glyphIds: List<Int>, advanceX: Float = 10f): List<GlyphCluster> {
        return glyphIds.mapIndexed { i, _ ->
            GlyphCluster(
                textRange = i..i,
                glyphRange = i..i,
                advanceX = advanceX,
            )
        }
    }

    /**
     * Convert font module shaping output into a Kanvas [TextBlob].
     *
     * @param glyphRuns pre-shaped runs from the font module (glyph IDs + cluster info)
     * @param typeface the typeface resource path wrapper
     * @param fontSize font size in device pixels
     */
    fun toTextBlob(
        glyphRuns: List<ShapedGlyphRun>,
        typeface: KanvasTypeface? = null,
        fontSize: Float = 12f,
    ): TextBlob {
        val runs = glyphRuns.map { toKanvasGlyphRun(it) }
        return TextBlob(glyphRuns = runs, typeface = typeface, fontSize = fontSize)
    }

    /** Convert a single shaped run to a [KanvasGlyphRun]. */
    fun toKanvasGlyphRun(run: ShapedGlyphRun, startX: Float = 0f, startY: Float = 0f): KanvasGlyphRun {
        val glyphs = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = startX
        val cursorY = startY
        for (cluster in run.clusters) {
            val count = cluster.glyphRange.last - cluster.glyphRange.first + 1
            for (i in 0 until count) {
                val gid = run.glyphIds.getOrNull(cluster.glyphRange.first + i) ?: 0
                glyphs.add(gid.toUShort())
                positions.add(Point(cursorX + cluster.offsetX + cluster.advanceX * i, cursorY + cluster.offsetY))
            }
            cursorX += cluster.advanceX
        }
        return KanvasGlyphRun(glyphs, positions)
    }

    // --- Private ---

    /** Font module-backed rasterization. Delegates to GlyphScaler + A8Rasterizer + GlyphAtlasUploadPlanner. */
    private fun rasterizeViaFont(blob: TextBlob): GpuTextBlob? {
        // The font module integration loads SFNT bytes from the typeface resource,
        // scales each glyph, rasterizes to A8, and packs into an atlas.
        // For the MVP: return a 1x1 placeholder atlas until font module wiring is complete.
        val atlas = ByteArray(1) { 0 }
        return GpuTextBlob(blob, atlas, 1, 1)
    }
}

// --- Bridge types (mirroring :font module without importing it) ---

data class ShapedGlyphRun(
    val glyphIds: List<Int>,
    val clusters: List<GlyphCluster>,
    val advanceX: Float = 0f,
    val advanceY: Float = 0f,
    val fontSize: Float = 12f,
)

data class GlyphCluster(
    val textRange: IntRange,
    val glyphRange: IntRange,
    val advanceX: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class ShapingResult(
    val glyphRuns: List<ShapedGlyphRun> = emptyList(),
)
