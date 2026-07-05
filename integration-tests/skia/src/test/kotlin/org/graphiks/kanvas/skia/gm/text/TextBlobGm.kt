package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/textblob.cpp::TextBlob` (640 × 480).
 * Stress-tests TextBlob with 6 blobs in a 3×2 grid spanning positioning
 * variants. Each blob's bounding rect is stroked in blue.
 * @see https://github.com/google/skia/blob/main/gm/textblob.cpp
 */
class TextBlobGm : SkiaGm {
    override val name = "textblob"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 90.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    private val kFontSize = 16f
    private val kText = "hamburgefons"

    private val kDefaultPos = 0
    private val kScalarPos = 1
    private val kPointPos = 2

    private data class BlobCfg(val count: Int, val pos: Int, val scale: Float)

    private val blobConfigs: Array<Array<Array<BlobCfg>>> = arrayOf(
        arrayOf(
            arrayOf(BlobCfg(1024, kDefaultPos, 1f), BlobCfg(0, kDefaultPos, 0f), BlobCfg(0, kDefaultPos, 0f)),
            arrayOf(BlobCfg(1024, kScalarPos, 1f), BlobCfg(0, kScalarPos, 0f), BlobCfg(0, kScalarPos, 0f)),
            arrayOf(BlobCfg(1024, kPointPos, 1f), BlobCfg(0, kPointPos, 0f), BlobCfg(0, kPointPos, 0f)),
        ),
        arrayOf(
            arrayOf(BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kDefaultPos, 1f)),
            arrayOf(BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kScalarPos, 1f)),
            arrayOf(BlobCfg(4, kPointPos, 1f), BlobCfg(4, kPointPos, 1f), BlobCfg(4, kPointPos, 1f)),
        ),
        arrayOf(
            arrayOf(BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kScalarPos, 1f)),
            arrayOf(BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kPointPos, 1f)),
            arrayOf(BlobCfg(4, kPointPos, 1f), BlobCfg(4, kPointPos, 1f), BlobCfg(4, kDefaultPos, 1f)),
        ),
        arrayOf(
            arrayOf(BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kPointPos, 1f)),
            arrayOf(BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kPointPos, 1f), BlobCfg(4, kDefaultPos, 1f)),
            arrayOf(BlobCfg(4, kPointPos, 1f), BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kScalarPos, 1f)),
        ),
        arrayOf(
            arrayOf(BlobCfg(4, kDefaultPos, 0.75f), BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kScalarPos, 1.25f)),
            arrayOf(BlobCfg(4, kScalarPos, 0.75f), BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kPointPos, 1.25f)),
            arrayOf(BlobCfg(4, kPointPos, 0.75f), BlobCfg(4, kPointPos, 1f), BlobCfg(4, kDefaultPos, 1.25f)),
        ),
        arrayOf(
            arrayOf(BlobCfg(4, kDefaultPos, 1f), BlobCfg(4, kScalarPos, 0.75f), BlobCfg(4, kPointPos, 1.25f)),
            arrayOf(BlobCfg(4, kScalarPos, 1f), BlobCfg(4, kPointPos, 0.75f), BlobCfg(4, kDefaultPos, 1.25f)),
            arrayOf(BlobCfg(4, kPointPos, 1f), BlobCfg(4, kDefaultPos, 0.75f), BlobCfg(4, kScalarPos, 1.25f)),
        ),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val glyphIds = getGlyphIds()

        for (b in blobConfigs.indices) {
            val blob = makeBlob(b, glyphIds)
            val paint = Paint(antiAlias = true)
            val offsetX = (10 + 300 * (b % 2)).toFloat()
            val offsetY = (20 + 150 * (b / 2)).toFloat()

            canvas.drawTextBlob(blob, offsetX, offsetY, paint)

            val boxPaint = Paint(color = Color.BLUE, style = PaintStyle.STROKE, antiAlias = false)
            val box = computeBlobBounds(blob)
            canvas.drawRect(
                Rect(box.left + offsetX, box.top + offsetY, box.right + offsetX, box.bottom + offsetY),
                boxPaint,
            )
        }
    }

    private fun getGlyphIds(): List<UShort> {
        val ids = mutableListOf<UShort>()
        for (cp in kText.codePoints()) {
            ids.add(typeface.glyphIdForCodepoint(cp).toUShort())
        }
        return ids
    }

    private fun makeBlob(blobIndex: Int, glyphIds: List<UShort>): TextBlob {
        val allRuns = mutableListOf<KanvasGlyphRun>()
        var currentGlyph = 0

        val rows = blobConfigs[blobIndex]
        for (l in rows.indices) {
            val row = rows[l]
            for (c in row.indices) {
                val cfg = row[c]
                var count = cfg.count
                if (count > glyphIds.size - currentGlyph) {
                    count = glyphIds.size - currentGlyph
                }
                if (count == 0) break

                val fontSize = kFontSize * cfg.scale
                val advanceX = fontSize * 0.85f
                val advanceY = fontSize * 1.5f
                val offsetX = currentGlyph * advanceX + c * advanceX
                val offsetY = advanceY * l

                when (cfg.pos) {
                    kDefaultPos -> {
                        val runGlyphs = mutableListOf<UShort>()
                        val runPositions = mutableListOf<Point>()
                        for (i in 0 until count) {
                            runGlyphs.add(glyphIds[currentGlyph + i])
                            runPositions.add(Point(offsetX + i * advanceX, offsetY))
                        }
                        allRuns.add(KanvasGlyphRun(runGlyphs, runPositions))
                    }
                    kScalarPos -> {
                        val runGlyphs = mutableListOf<UShort>()
                        val runPositions = mutableListOf<Point>()
                        for (i in 0 until count) {
                            runGlyphs.add(glyphIds[currentGlyph + i])
                            runPositions.add(Point(offsetX + i * advanceX, offsetY))
                        }
                        allRuns.add(KanvasGlyphRun(runGlyphs, runPositions))
                    }
                    kPointPos -> {
                        val runGlyphs = mutableListOf<UShort>()
                        val runPositions = mutableListOf<Point>()
                        for (i in 0 until count) {
                            runGlyphs.add(glyphIds[currentGlyph + i])
                            runPositions.add(
                                Point(
                                    offsetX + i * advanceX,
                                    offsetY + i * (advanceY / count.toFloat()),
                                ),
                            )
                        }
                        allRuns.add(KanvasGlyphRun(runGlyphs, runPositions))
                    }
                }
                currentGlyph += count
            }
        }
        return TextBlob(glyphRuns = allRuns, typeface = typeface, fontSize = kFontSize)
    }

    private fun computeBlobBounds(blob: TextBlob): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var hasGlyphs = false
        for (run in blob.glyphRuns) {
            for (pos in run.positions) {
                minX = minOf(minX, pos.x)
                minY = minOf(minY, pos.y)
                maxX = maxOf(maxX, pos.x)
                maxY = maxOf(maxY, pos.y)
                hasGlyphs = true
            }
        }
        if (!hasGlyphs) return Rect.EMPTY
        val h = blob.fontSize * 1.2f
        return Rect(minX, minY - h, maxX + blob.fontSize * 0.5f, maxY)
    }
}
