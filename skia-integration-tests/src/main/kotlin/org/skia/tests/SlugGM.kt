package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTextSlug
import org.skia.foundation.SkTypeface
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/slug.cpp::SlugGM`](https://github.com/google/skia/blob/main/gm/slug.cpp)
 * — exercises the chromium-only `sktext::gpu::Slug` text-blob
 * preprocessing primitive : build an [SkTextBlob] from a portable
 * serif typeface, convert it to a [SkTextSlug], then `drawTextBlob`
 * and `drawSlug` side-by-side at six increasing CTM scales (1.0,
 * 1.5, 2.0, 2.5, 3.0, 3.5) with a 5° rotation per row. Each row
 * compares the live `drawTextBlob` path (left column) with the
 * pre-compiled slug replay path (right column).
 *
 * ## Port status — **INTRACTABLE for the raster-only `:kanvas-skia`**
 *
 * Upstream `gm/slug.cpp` lives entirely under
 * `#if defined(SK_GANESH) || defined(SK_GRAPHITE)`. The Slug
 * primitive's *raison d'être* is to **pre-compile** a glyph run
 * into a GPU-renderable atlas-residency snapshot, so that on a
 * subsequent `canvas->drawSlug` the GPU backend can skip the
 * shaping / atlas-upload work and stamp the cached glyph quads
 * straight into the render pass. There is no raster equivalent —
 * the CPU rasteriser already pays the glyph-cache cost per draw,
 * so a "pre-compiled" snapshot is just the source blob.
 *
 * `:kanvas-skia` ships a minimal [SkTextSlug] (blob + paint pair)
 * + [SkCanvas.drawSlug] surface so this GM (and any downstream
 * code that captures slugs across `SkNWayCanvas` / `SkPictureRecorder`
 * sinks) compiles ; the replay path delegates to
 * [SkTextSlug.replay] which re-issues the captured `drawTextBlob`.
 * That makes the GM's left + right columns **identical** on the
 * raster backend — by design.
 *
 * The associated [SlugTest] is `@Disabled` with the `STUB.SLUG`
 * marker. The reference `original-888/slug.png` was rendered by a
 * Ganesh / Graphite sink with `fSupportBilerpFromGlyphAtlas =
 * true` (bilinear-filtered glyph sampling out of the GPU atlas) —
 * a feature the raster pipeline doesn't model, so a pixel-level
 * diff would fail for backend-inherent reasons.
 *
 * ## Upstream parameter
 *
 * Mirrors upstream's `SlugGM(const char* txt)` ctor — the only
 * registered instance is `DEF_GM(return new SlugGM("hamburgefons");)`,
 * so we default the no-arg ctor to that string.
 */
public class SlugGM(
    private val text: String,
) : GM() {

    /** No-arg ctor — matches upstream's only `DEF_GM` registration. */
    public constructor() : this(K_DEFAULT_TEXT)

    private var typeface: SkTypeface = SkTypeface.MakeEmpty()
    private var glyphs: IntArray = IntArray(0)

    override fun onOnceBeforeDraw() {
        // Mirrors upstream's:
        //   fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
        //   SkFont font(fTypeface);
        //   int glyphCount = font.countText(fText, txtLen, SkTextEncoding::kUTF8);
        //   fGlyphs.append(glyphCount);
        //   font.textToGlyphs(fText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
        //
        // `SkFontStyle()` defaults to upright / normal weight / normal width
        // — exactly what `SkFontStyle.Normal()` produces.
        typeface = ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Normal())
        val sizingFont = SkFont(typeface)
        glyphs = sizingFont.textToGlyphs(text, SkTextEncoding.kUTF8)
    }

    override fun getName(): String = "slug"

    override fun getISize(): SkISize = SkISize.Make(1000, 480)

    /**
     * Mirrors upstream's `SlugGM::makeBlob()` — a single
     * `allocRun` (uniform x-advance from origin (0, 0)) with the
     * pre-shaped glyph IDs from [onOnceBeforeDraw].
     */
    private fun makeBlob(): SkTextBlob? {
        val builder = SkTextBlobBuilder()
        val font = SkFont(typeface).apply {
            isSubpixel = true
            edging = SkFont.Edging.kAntiAlias
            size = 16f
        }
        val rec = builder.allocRun(font, glyphs.size, x = 0f, y = 0f)
        for (i in glyphs.indices) rec.glyphs[i] = glyphs[i]
        return builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = makeBlob() ?: return

        val paint = SkPaint().apply { isAntiAlias = true }

        // Upstream: canvas->clipIRect(SkIRect::MakeSize(getISize()).makeInset(40, 50));
        // :kanvas-skia exposes clipRect(SkRect) — semantically identical for
        // integer-aligned bounds (boundary lands on pixel edges; no AA
        // sub-pixel coverage needed). Matches the GraphiteReplayGM
        // translation precedent.
        val size = getISize()
        c.clipRect(
            SkRect.MakeLTRB(
                40f,
                50f,
                (size.width - 40).toFloat(),
                (size.height - 50).toFloat(),
            ),
        )

        c.scale(1.3f, 1.3f)

        // Upstream: sktext::gpu::Slug::ConvertBlob(canvas, *blob, {10, 10}, p)
        // Maps to the SkTextSlug ctor : (blob, paint, origin). The `canvas`
        // arg upstream is the *capture context* — used only to inherit the
        // backend's text-pipeline settings (bilerp-from-glyph-atlas, MSAA,
        // colour space, …). On the raster backend that captures nothing
        // beyond the blob + paint + origin.
        //
        // Implementation note — :kanvas-skia's SkTextSlug.replay() emits
        // `drawTextBlob(blob, dx, dy, paint)` where `(dx, dy) = target - origin`
        // (re-positionable replay). To match upstream's `slug->draw(canvas, p)`
        // semantics (which bakes the (10, 10) origin into the draw), we
        // capture with `origin = (0, 0)` and replay at `(10, 10)` — the
        // arithmetic collapses to `drawTextBlob(blob, 10, 10, paint)`,
        // identical to the left-column blob draw.
        val slug: SkTextSlug = SkTextSlug(blob, paint, SkPoint(0f, 0f))

        // Sub-pixel nudge mirrors upstream's `canvas->translate(0.5, 0.5)`.
        c.translate(0.5f, 0.5f)
        c.translate(30f, 30f)

        // Row 1 (scale = 1.0, no per-row rotate) — drawTextBlob on the left.
        c.drawTextBlob(blob, 10f, 10f, paint)
        c.translate(370f, 0f)

        // Slug replay on the right.
        c.drawSlug(slug, SkPoint(10f, 10f))

        // Rows 2..6 — scale ∈ {1.5, 2.0, 2.5, 3.0, 3.5}, each row pre-rotated
        // 5° and pre-scaled. Left column re-renders from the blob ; right
        // column replays from the slug. Upstream loop :
        //   for (float scale = 1.5; scale < 4; scale += 0.5) { … }
        var scale = 1.5f
        while (scale < 4f) {
            c.translate(-370f, 20f * scale)

            c.save()
            c.scale(scale, scale)
            c.rotate(5f)
            c.drawTextBlob(blob, 10f, 10f, paint)
            c.restore()

            c.translate(370f, 0f)

            c.save()
            c.scale(scale, scale)
            c.rotate(5f)
            c.drawSlug(slug, SkPoint(10f, 10f))
            c.restore()

            scale += 0.5f
        }
    }

    private companion object {
        /** Upstream's only `DEF_GM` registration is `SlugGM("hamburgefons")`. */
        const val K_DEFAULT_TEXT: String = "hamburgefons"
    }
}
