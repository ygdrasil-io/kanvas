package core

import com.kanvas.core.Point

/**
 * GlyphRunList represents a collection of GlyphRun objects.
 * Inspired by Skia's sktext::GlyphRunList.
 *
 * This class holds multiple GlyphRun objects that can be rendered together,
 * which is useful for complex text layout and performance optimization.
 */
class GlyphRunList {
    private val glyphRuns: MutableList<GlyphRun> = mutableListOf()
    private var origin: Point = Point(0f, 0f)
    private var sourceBounds: SimpleRect = SimpleRect.EMPTY

    /**
     * Create an empty GlyphRunList
     */
    constructor()

    /**
     * Create a GlyphRunList with a single GlyphRun
     */
    constructor(glyphRun: GlyphRun) {
        addGlyphRun(glyphRun)
        updateBounds()
    }

    /**
     * Create a GlyphRunList with multiple GlyphRuns
     */
    constructor(glyphRuns: List<GlyphRun>) {
        this.glyphRuns.addAll(glyphRuns)
        updateBounds()
    }

    /**
     * Add a GlyphRun to this list
     */
    fun addGlyphRun(glyphRun: GlyphRun) {
        glyphRuns.add(glyphRun)
        updateBounds()
    }

    /**
     * Get the number of GlyphRuns in this list
     */
    fun runCount(): Int = glyphRuns.size

    /**
     * Get the total number of glyphs across all GlyphRuns
     */
    fun totalGlyphCount(): Int {
        return glyphRuns.sumOf { it.size() }
    }

    /**
     * Get the GlyphRun at the specified index
     */
    fun getGlyphRun(index: Int): GlyphRun {
        return glyphRuns[index]
    }

    /**
     * Get all GlyphRuns in this list
     */
    fun getAllGlyphRuns(): List<GlyphRun> {
        return glyphRuns.toList()
    }

    /**
     * Set the origin point for this GlyphRunList
     */
    fun setOrigin(origin: Point) {
        this.origin = origin
    }

    /**
     * Get the origin point
     */
    fun getOrigin(): Point = origin

    /**
     * Get the source bounds of all glyphs in this list
     */
    fun getSourceBounds(): SimpleRect = sourceBounds

    /**
     * Get the source bounds with origin applied
     */
    fun getSourceBoundsWithOrigin(): SimpleRect {
        return SimpleRect(
            sourceBounds.left + origin.x,
            sourceBounds.top + origin.y,
            sourceBounds.right + origin.x,
            sourceBounds.bottom + origin.y
        )
    }

    /**
     * Check if this GlyphRunList has any RSXForm (rotated/scaled glyphs)
     * Corresponds to sktext::GlyphRunList::hasRSXForm() in Skia
     */
    fun hasRSXForm(): Boolean {
        for (glyphRun in glyphRuns) {
            if (glyphRun.hasRSXForm()) {
                return true
            }
        }
        return false
    }

    /**
     * Update the bounds based on all GlyphRuns
     */
    private fun updateBounds() {
        if (glyphRuns.isEmpty()) {
            sourceBounds = SimpleRect.EMPTY
            return
        }

        // Start with the bounds of the first GlyphRun
        var bounds = glyphRuns[0].getBounds()

        // Expand to include all other GlyphRuns
        for (i in 1 until glyphRuns.size) {
            val glyphRunBounds = glyphRuns[i].getBounds()
            bounds = bounds.join(glyphRunBounds)
        }

        sourceBounds = bounds
    }

    /**
     * Check if this GlyphRunList is empty
     */
    fun isEmpty(): Boolean = glyphRuns.isEmpty()

    /**
     * Get an iterator over the GlyphRuns
     */
    operator fun iterator(): Iterator<GlyphRun> = glyphRuns.iterator()

    /**
     * Create a GlyphRunList from text using a font
     */
    companion object {
        fun createFromText(text: String, font: Font, x: Float = 0f, y: Float = 0f): GlyphRunList {
            val glyphRun = font.createGlyphRun(text, x, y)
            return GlyphRunList(glyphRun)
        }

        fun createFromMultipleTexts(texts: List<String>, font: Font, positions: List<Point>): GlyphRunList {
            val glyphRuns = mutableListOf<GlyphRun>()
            for (i in texts.indices) {
                val glyphRun = font.createGlyphRun(texts[i], positions[i].x, positions[i].y)
                glyphRuns.add(glyphRun)
            }
            return GlyphRunList(glyphRuns)
        }
    }
}