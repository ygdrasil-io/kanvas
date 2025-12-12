package com.kanvas.core

import com.kanvas.core.SkTextBlob.GlyphPositioning.*

/**
 * SkTextBlob represents a collection of text runs that can be drawn efficiently.
 * It contains glyphs, positions, and font information for optimized text rendering.
 */
class SkTextBlob(private val runs: List<RunRecord>, val uniqueID: UInt = generateUniqueID()) {

    /**
     * Returns the bounds of this text blob.
     *
     * @return The bounding rectangle of all glyphs in this blob
     */
    fun bounds(): SkRect {
        val bounds = SkRect.makeEmpty()
        for (run in runs) {
            bounds.join(run.bounds())
        }
        return bounds
    }

    /**
     * Returns the number of glyph runs in this blob.
     *
     * @return The number of runs
     */
    fun numRuns(): Int = runs.size

    /**
     * Returns the run at the specified index.
     *
     * @param index The index of the run
     * @return The run record at the specified index
     */
    fun getRun(index: Int): RunRecord {
        require(index >= 0 && index < runs.size) { "Index out of bounds" }
        return runs[index]
    }

    /**
     * Returns all runs in this blob.
     *
     * @return List of all run records
     */
    fun getRuns(): List<RunRecord> = runs.toList()

    /**
     * Returns true if this blob has any runs with RSXform (rotation/scale) transformations.
     *
     * @return true if RSXform runs are present, false otherwise
     */
    fun hasRSXform(): Boolean {
        return runs.any { it.positioning() == kRSXform_GlyphPositioning }
    }

    /**
     * Returns the unique ID of this text blob.
     *
     * @return The unique ID
     */
    fun uniqueID(): UInt = uniqueID

    /**
     * Iterates through all runs in this blob.
     *
     * @param callback The callback to call for each run
     */
    fun forEachRun(callback: (RunRecord) -> Unit) {
        for (run in runs) {
            callback(run)
        }
    }

    /**
     * Creates a builder for constructing text blobs.
     *
     * @return A new text blob builder
     */
    companion object {
        private var nextUniqueID: UInt = 1u

        private fun generateUniqueID(): UInt {
            val id = nextUniqueID
            nextUniqueID++
            if (nextUniqueID == 0u) nextUniqueID = 1u // Skip 0
            return id
        }

        /**
         * Creates an empty text blob.
         *
         * @return A new empty text blob
         */
        fun make(): SkTextBlob {
            return SkTextBlob(emptyList())
        }

        /**
         * Creates a text blob from a single run.
         *
         * @param run The run record
         * @return A new text blob with the specified run
         */
        fun makeFromRun(run: RunRecord): SkTextBlob {
            return SkTextBlob(listOf(run))
        }

        /**
         * Creates a text blob from multiple runs.
         *
         * @param runs List of run records
         * @return A new text blob with the specified runs
         */
        fun makeFromRuns(runs: List<RunRecord>): SkTextBlob {
            return SkTextBlob(runs)
        }

        /**
         * Creates a text blob builder.
         *
         * @return A new text blob builder
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Represents a single run of text with consistent font and positioning.
     */
    data class RunRecord(
        val glyphs: List<SkGlyphID>,
        val positions: List<SkScalar>,
        val offset: SkPoint,
        val font: SkFont,
        val positioning: GlyphPositioning,
        val text: String? = null,
        val clusters: IntArray? = null
    ) {

        init {
            require(glyphs.isNotEmpty()) { "Glyph list cannot be empty" }
            require(positions.size == glyphs.size * ScalarsPerGlyph(positioning)) { 
                "Position count must match glyph count for positioning mode"
            }
            if (text != null) {
                require(clusters != null && clusters.size == glyphs.size) { 
                    "Clusters must be provided when text is provided"
                }
            }
        }

        /**
         * Returns the number of glyphs in this run.
         *
         * @return The glyph count
         */
        fun glyphCount(): Int = glyphs.size

        /**
         * Returns the font used for this run.
         *
         * @return The font
         */
        fun font(): SkFont = font

        /**
         * Returns the offset of this run.
         *
         * @return The offset point
         */
        fun offset(): SkPoint = offset

        /**
         * Returns the positioning mode of this run.
         *
         * @return The glyph positioning mode
         */
        fun positioning(): GlyphPositioning = positioning

        /**
         * Returns the glyph at the specified index.
         *
         * @param index The index of the glyph
         * @return The glyph ID at the specified index
         */
        fun getGlyph(index: Int): SkGlyphID {
            require(index >= 0 && index < glyphs.size) { "Index out of bounds" }
            return glyphs[index]
        }

        /**
         * Returns all glyphs in this run.
         *
         * @return List of glyph IDs
         */
        fun getGlyphs(): List<SkGlyphID> = glyphs.toList()

        /**
         * Returns the position of the glyph at the specified index.
         *
         * @param index The index of the glyph
         * @return The position(s) of the glyph
         */
        fun getPosition(index: Int): List<SkScalar> {
            require(index >= 0 && index < glyphs.size) { "Index out of bounds" }
            val start = index * ScalarsPerGlyph(positioning)
            val end = start + ScalarsPerGlyph(positioning)
            return positions.subList(start, end)
        }

        /**
         * Returns all positions in this run.
         *
         * @return List of all positions
         */
        fun getPositions(): List<SkScalar> = positions.toList()

        /**
         * Returns the text associated with this run (if any).
         *
         * @return The text string or null if not available
         */
        fun text(): String? = text

        /**
         * Returns the cluster information for this run (if any).
         *
         * @return The cluster array or null if not available
         */
        fun clusters(): IntArray? = clusters

        /**
         * Returns true if this run has extended text information.
         *
         * @return true if text and clusters are available, false otherwise
         */
        fun isExtended(): Boolean = text != null && clusters != null

        /**
         * Returns the bounds of this run.
         *
         * @return The bounding rectangle of all glyphs in this run
         */
        fun bounds(): SkRect {
            val bounds = SkRect.makeEmpty()
            for (i in glyphs.indices) {
                val glyphBounds = font.getBounds(glyphs[i])
                val positions = getPosition(i)
                val x = if (positions.isNotEmpty()) positions[0].toFloat() else 0f
                val y = if (positions.size > 1) positions[1].toFloat() else 0f
                
                val glyphRect = SkRect.makeXYWH(
                    offset.x + x,
                    offset.y + y,
                    glyphBounds.width().toFloat(),
                    glyphBounds.height().toFloat()
                )
                bounds.join(glyphRect)
            }
            return bounds
        }

        /**
         * Returns the number of scalars per glyph for the positioning mode.
         *
         * @return The number of scalars per glyph
         */
        fun ScalarsPerGlyph(positioning: GlyphPositioning): Int {
            return when (positioning) {
                kNormal_GlyphPositioning -> 2 // x, y
                kRSXform_GlyphPositioning -> 4 // x, y, scaleX, skewX
                else -> 2
            }
        }
    }

    /**
     * Builder for constructing text blobs.
     */
    class Builder {
        private val runs: MutableList<RunRecord> = mutableListOf()

        /**
         * Adds a run to the text blob.
         *
         * @param run The run record to add
         * @return This builder for chaining
         */
        fun addRun(run: RunRecord): Builder {
            runs.add(run)
            return this
        }

        /**
         * Adds a run with normal positioning.
         *
         * @param glyphs List of glyph IDs
         * @param positions List of positions (x, y pairs)
         * @param offset The offset of the run
         * @param font The font to use
         * @return This builder for chaining
         */
        fun addRunNormal(glyphs: List<SkGlyphID>, positions: List<SkScalar>, offset: SkPoint, font: SkFont): Builder {
            require(positions.size == glyphs.size * 2) { "Positions must be x,y pairs" }
            val run = RunRecord(glyphs, positions, offset, font, kNormal_GlyphPositioning)
            runs.add(run)
            return this
        }

        /**
         * Adds a run with RSXform positioning.
         *
         * @param glyphs List of glyph IDs
         * @param positions List of positions (x, y, scaleX, skewX quadruples)
         * @param offset The offset of the run
         * @param font The font to use
         * @return This builder for chaining
         */
        fun addRunRSXform(glyphs: List<SkGlyphID>, positions: List<SkScalar>, offset: SkPoint, font: SkFont): Builder {
            require(positions.size == glyphs.size * 4) { "Positions must be x,y,scaleX,skewX quadruples" }
            val run = RunRecord(glyphs, positions, offset, font, kRSXform_GlyphPositioning)
            runs.add(run)
            return this
        }

        /**
         * Adds an extended run with text and cluster information.
         *
         * @param glyphs List of glyph IDs
         * @param positions List of positions
         * @param offset The offset of the run
         * @param font The font to use
         * @param text The original text
         * @param clusters The cluster information
         * @param positioning The positioning mode
         * @return This builder for chaining
         */
        fun addRunExtended(glyphs: List<SkGlyphID>, positions: List<SkScalar>, offset: SkPoint, font: SkFont, text: String, clusters: IntArray, positioning: GlyphPositioning = kNormal_GlyphPositioning): Builder {
            require(clusters.size == glyphs.size) { "Clusters must match glyph count" }
            require(positions.size == glyphs.size * ScalarsPerGlyph(positioning)) { 
                "Position count must match glyph count for positioning mode"
            }
            val run = RunRecord(glyphs, positions, offset, font, positioning, text, clusters)
            runs.add(run)
            return this
        }

        /**
         * Builds the text blob.
         *
         * @return A new text blob with the added runs
         */
        fun build(): SkTextBlob {
            return SkTextBlob(runs.toList())
        }
    }

    /**
     * Glyph positioning modes.
     */
    enum class GlyphPositioning {
        kNormal_GlyphPositioning,
        kRSXform_GlyphPositioning
    }

    companion object {
        /**
         * Returns the number of scalars per glyph for the positioning mode.
         *
         * @param positioning The positioning mode
         * @return The number of scalars per glyph
         */
        fun ScalarsPerGlyph(positioning: GlyphPositioning): Int {
            return when (positioning) {
                kNormal_GlyphPositioning -> 2 // x, y
                kRSXform_GlyphPositioning -> 4 // x, y, scaleX, skewX
            }
        }
    }
}

/**
 * Extension functions for SkTextBlob.
 */
fun SkTextBlob.iterateRuns(): Sequence<SkTextBlob.RunRecord> {
    return runs.asSequence()
}

/**
 * Creates a simple text blob from a string and font.
 *
 * @param text The text to convert to a blob
 * @param font The font to use
 * @param x The x-coordinate of the text
 * @param y The y-coordinate of the text
 * @return A new text blob with the specified text
 */
fun SkTextBlob.Companion.makeFromString(text: String, font: SkFont, x: Float, y: Float): SkTextBlob {
    // This is a simplified version - in a real implementation, this would use
    // proper text shaping and glyph positioning
    val glyphs = text.map { char -> SkGlyphID(char.code) }
    val positions = glyphs.flatMap { glyph -> listOf(SkScalar(0f), SkScalar(0f)) }
    
    val builder = SkTextBlob.builder()
    builder.addRunNormal(glyphs, positions, SkPoint(x, y), font)
    return builder.build()
}