package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/textblob.cpp::TextBlob` (640 × 480, text="hamburgefons").
 *
 * Stress-tests the [SkTextBlobBuilder] entry-point matrix : per-blob,
 * we lay out 9 sub-runs in a 3×3 grid spanning all three positioning
 * variants (`kDefault_Pos` → allocRun, `kScalar_Pos` → allocRunPosH,
 * `kPoint_Pos` → allocRunPos), and 6 such blobs are drawn at
 * 300×150 grid offsets covering uniform / heterogeneous mixes and a
 * trio of font scales (0.75, 1.0, 1.25 × kFontSize). Each blob's
 * conservative bounds rect is then stroked in [SK_ColorBLUE] for a
 * direct visual check that [org.skia.foundation.SkTextBlob.bounds]
 * stayed in lockstep with the actual glyph extents.
 */
public class TextBlobGM : GM() {

    private companion object {
        private const val kFontSize = 16f
        private const val kDefaultPos = 0
        private const val kScalarPos = 1
        private const val kPointPos = 2
        private val kText = "hamburgefons"

        private data class BlobCfg(val count: Int, val pos: Int, val scale: Float)

        // Mirrors textblob.cpp's `blobConfigs[][3][3]` table verbatim.
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
    }

    private var fGlyphs: IntArray = IntArray(0)
    private var fTypeface: SkTypeface = SkTypeface.MakeEmpty()

    override fun getName(): String = "textblob"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        fTypeface = ToolUtils.CreatePortableTypeface("serif", SkFontStyle())
        val font = SkFont(fTypeface)
        val codePoints = kText.codePoints().toArray()
        val n = codePoints.size
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codePoints, n, glyphsShort)
        fGlyphs = IntArray(n) { glyphsShort[it].toInt() and 0xFFFF }
    }

    private fun makeBlob(blobIndex: Int): org.skia.foundation.SkTextBlob? {
        val builder = SkTextBlobBuilder()
        val font = SkFont().apply {
            isSubpixel = true
            edging = SkFont.Edging.kAntiAlias
            typeface = fTypeface
        }

        val rows = blobConfigs[blobIndex]
        for (l in rows.indices) {
            var currentGlyph = 0
            val row = rows[l]
            for (c in row.indices) {
                val cfg = row[c]
                var count = cfg.count
                if (count > fGlyphs.size - currentGlyph) {
                    count = fGlyphs.size - currentGlyph
                }
                if (count == 0) break

                font.size = kFontSize * cfg.scale
                val advanceX = font.size * 0.85f
                val advanceY = font.size * 1.5f

                val offsetX = currentGlyph * advanceX + c * advanceX
                val offsetY = advanceY * l

                when (cfg.pos) {
                    kDefaultPos -> {
                        val buf = builder.allocRun(font, count, offsetX, offsetY)
                        for (i in 0 until count) buf.glyphs[i] = fGlyphs[currentGlyph + i]
                    }
                    kScalarPos -> {
                        val buf = builder.allocRunPosH(font, count, offsetY)
                        for (i in 0 until count) {
                            buf.glyphs[i] = fGlyphs[currentGlyph + i]
                            buf.pos[i] = offsetX + i * advanceX
                        }
                    }
                    kPointPos -> {
                        val buf = builder.allocRunPos(font, count)
                        for (i in 0 until count) {
                            buf.glyphs[i] = fGlyphs[currentGlyph + i]
                            buf.pos[i * 2] = offsetX + i * advanceX
                            buf.pos[i * 2 + 1] = offsetY + i * (advanceY / count)
                        }
                    }
                }
                currentGlyph += count
            }
        }
        return builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        for (b in blobConfigs.indices) {
            val blob = makeBlob(b) ?: continue
            val p = SkPaint().apply { isAntiAlias = true }
            val offsetX = (10 + 300 * (b % 2)).toFloat()
            val offsetY = (20 + 150 * (b / 2)).toFloat()

            c.drawTextBlob(blob, offsetX, offsetY, p)

            val pBox = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                isAntiAlias = false
            }
            val box = blob.bounds().copy()
            box.offset(offsetX, offsetY)
            c.drawRect(box, pBox)
        }
    }
}
