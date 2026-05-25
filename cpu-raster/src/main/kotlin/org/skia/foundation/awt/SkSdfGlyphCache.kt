package org.skia.foundation.awt

import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkMatrix
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextBlob
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Backend-neutral signed-distance-field glyph cache used by the DFText GMs.
 *
 * This is intentionally a small raster implementation: it builds an A8 glyph
 * image from the portable outline path, stores a signed-distance-derived
 * coverage ramp, and samples it through the existing image shader path. That
 * gives perspective/scale handling without pulling in Ganesh atlas code or a
 * GPU shader compiler.
 */
public object SkSdfGlyphCache {
    private const val PAD: Int = 4
    private const val SPREAD: Float = 3f
    private const val MAX_ENTRIES: Int = 1024

    private data class Key(
        val typefaceId: Int,
        val sizeBits: Int,
        val scaleXBits: Int,
        val skewXBits: Int,
        val edging: SkFont.Edging,
        val glyphId: Int,
    )

    public data class Glyph(
        val image: SkImage,
        val originX: Int,
        val originY: Int,
    )

    private val cache: LinkedHashMap<Key, Glyph> =
        object : LinkedHashMap<Key, Glyph>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Glyph>?): Boolean =
                size > MAX_ENTRIES
        }

    public fun getOrRasterize(font: SkFont, glyphId: Int): Glyph? {
        val key = Key(
            System.identityHashCode(font.typeface),
            font.size.toRawBits(),
            font.scaleX.toRawBits(),
            font.skewX.toRawBits(),
            font.edging,
            glyphId,
        )
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) return cached
        }

        val mask = SkGlyphCache.getOrRasterize(font, glyphId) { font.getPath(glyphId) } ?: return null
        if (mask.width == 0 || mask.height == 0) return null
        val glyph = buildSdfGlyph(mask)

        return synchronized(cache) {
            cache[key] ?: glyph.also { cache[key] = it }
        }
    }

    public fun drawTextBlob(canvas: SkCanvas, blob: SkTextBlob, x: Float, y: Float, paint: SkPaint) {
        for (run in blob.runs) {
            when (run) {
                is SkTextBlob.Run.HorizontalSpread -> {
                    var advance = 0f
                    for (gid in run.glyphIds) {
                        drawGlyph(canvas, run.font, gid, x + run.x + advance, y + run.y, paint)
                        advance += run.font.getWidth(gid)
                    }
                }
                is SkTextBlob.Run.HorizontalPositions -> {
                    for (i in run.glyphIds.indices) {
                        drawGlyph(canvas, run.font, run.glyphIds[i], x + run.xs[i], y + run.constY, paint)
                    }
                }
                is SkTextBlob.Run.FullPositions -> {
                    var p = 0
                    for (gid in run.glyphIds) {
                        drawGlyph(canvas, run.font, gid, x + run.positions[p], y + run.positions[p + 1], paint)
                        p += 2
                    }
                }
                is SkTextBlob.Run.RSXformPositions -> {
                    for (i in run.glyphIds.indices) {
                        val glyph = getOrRasterize(run.font, run.glyphIds[i]) ?: continue
                        val xf = run.xforms[i]
                        val m = SkMatrix(
                            sx = xf.fSCos, kx = -xf.fSSin, tx = xf.fTx + x,
                            ky = xf.fSSin, sy = xf.fSCos, ty = xf.fTy + y,
                        )
                        canvas.save()
                        canvas.concat(m)
                        drawGlyphImage(canvas, glyph, 0f, 0f, paint)
                        canvas.restore()
                    }
                }
            }
        }
    }

    public fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private fun drawGlyph(canvas: SkCanvas, font: SkFont, glyphId: Int, x: Float, y: Float, paint: SkPaint) {
        val glyph = getOrRasterize(font, glyphId) ?: return
        drawGlyphImage(canvas, glyph, x, y, paint)
    }

    private fun drawGlyphImage(canvas: SkCanvas, glyph: Glyph, x: Float, y: Float, paint: SkPaint) {
        canvas.drawImage(
            glyph.image,
            x + glyph.originX,
            y + glyph.originY,
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNone),
            paint,
        )
    }

    private fun buildSdfGlyph(mask: SkGlyphCache.GlyphMask): Glyph {
        val w = mask.width + PAD * 2
        val h = mask.height + PAD * 2
        val inside = BooleanArray(w * h)
        for (y in 0 until mask.height) {
            val srcRow = y * mask.width
            val dstRow = (y + PAD) * w + PAD
            for (x in 0 until mask.width) {
                inside[dstRow + x] = (mask.alpha[srcRow + x].toInt() and 0xFF) >= 128
            }
        }

        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val signedDistance = signedDistanceAt(inside, w, h, x, y)
                val coverage = ((signedDistance / SPREAD + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[idx] = SkColorSetARGB(coverage, 0, 0, 0)
            }
        }

        return Glyph(
            image = SkImage(w, h, pixels, SkColorType.kAlpha_8),
            originX = mask.originX - PAD,
            originY = mask.originY - PAD,
        )
    }

    private fun signedDistanceAt(inside: BooleanArray, w: Int, h: Int, x: Int, y: Int): Float {
        val targetInside = inside[y * w + x]
        var best = Int.MAX_VALUE
        val radius = ceil(SPREAD + PAD).toInt().coerceAtLeast(1)
        val minY = (y - radius).coerceAtLeast(0)
        val maxY = (y + radius).coerceAtMost(h - 1)
        val minX = (x - radius).coerceAtLeast(0)
        val maxX = (x + radius).coerceAtMost(w - 1)
        for (yy in minY..maxY) {
            for (xx in minX..maxX) {
                if (inside[yy * w + xx] == targetInside) continue
                val dx = xx - x
                val dy = yy - y
                val d2 = dx * dx + dy * dy
                if (d2 < best) best = d2
            }
        }
        if (best == Int.MAX_VALUE) {
            best = radius * radius
        }
        val d = sqrt(best.toFloat())
        return if (targetInside) d else -d
    }
}
