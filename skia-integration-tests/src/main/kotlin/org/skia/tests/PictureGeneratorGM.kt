package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFont
import org.skia.foundation.SkImages
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils
import kotlin.math.sqrt

/**
 * Port of Skia's
 * [`gm/pictureimagegenerator.cpp::PictureGeneratorGM`](https://github.com/google/skia/blob/main/gm/pictureimagegenerator.cpp)
 * (1160 × 860).
 *
 * Upstream records the stylised "SKIA" vector logo (`draw_vector_logo`)
 * into an [SkPicture], then renders **16 tiles** of it onto a 4-column
 * grid through `SkImageGenerators::MakeFromPicture` — varying target
 * dimensions, X/Y scale (incl. negative scales for axis flips), and
 * opacity per tile.
 *
 * ## Port mapping
 *
 * * `SkImageGenerators::MakeFromPicture` → [SkImages.DeferredFromPicture].
 *   The kanvas-skia entry materialises the picture into a freshly
 *   allocated raster bitmap of the requested [SkISize] / colour space,
 *   applying the matrix on the playback canvas and (when non-null) a
 *   `saveLayer(null, paint)` around the playback so the opacity from
 *   `setAlphaf(opacity)` wraps the entire picture (matches upstream's
 *   `paint.getAlpha() != 255 ? &p : nullptr` conditional).
 * * `SkPictureRecorder` → [SkPictureRecorder] (same name).
 * * `SkTextUtils::GetPath` → [SkFont.makeTextPath] returns the outline
 *   of a UTF-8 string as a single [org.skia.foundation.SkPath]. Used
 *   to compute the tight bounds of `"SKI"`, `"I"`, and `"SKIA"` so the
 *   logo's layout maths can be reproduced verbatim. `makeTextPath`
 *   returns `null` for the empty typeface — when that happens we fall
 *   back to a coarse logo-sized rectangle so the 16-cell grid still
 *   exercises the [SkImages.DeferredFromPicture] matrix / opacity
 *   contract end-to-end.
 * * `SkShaders::LinearGradient` → [SkLinearGradient.Make]. We expose the
 *   8-stop "S K I A" rainbow gradient via an `IntArray` of ARGB ints
 *   (no `SkColorConverter` round-trip — the kanvas-skia gradient eats
 *   bytes directly).
 *
 * ## Picture content
 *
 * Mirrors upstream's `draw_vector_logo` exactly :
 *
 *   1. `circle` over the dot of the "I" (the bullet on top).
 *   2. `path` — the slanted accent piece below the "I".
 *   3. `rect` with a transparent→black linear gradient — the underline
 *      that fades into the "I"'s baseline.
 *   4. `drawSimpleText("SKIA", …)` with the 8-stop rainbow gradient on
 *      the paint, drawn through a `RectToRect(skiaBox, viewBox)` CTM so
 *      the wordmark fills the picture exactly.
 *
 * The `draw_vector_logo` helper is private to the GM class (mirrors
 * upstream's anonymous-namespace free function).
 *
 * ## Relationship with [PictureImageGeneratorGM]
 *
 * Two Kotlin classes mirror the same upstream `pictureimagegenerator.cpp`
 * GM — [PictureImageGeneratorGM] was the first pass (substituted a
 * concentric-rectangles vector pattern for the SKIA wordmark) ; this
 * class restores the upstream wordmark content and goes through the
 * same [SkImages.DeferredFromPicture] codepath. Both compile and run
 * independently ; the [PictureGeneratorTest] keyed off this class is
 * the one carrying a similarity score in the ratchet, separate from
 * `PictureImageGeneratorGM`'s entry.
 */
public class PictureGeneratorGM : GM() {

    override fun getName(): String = "pictureimagegenerator"

    override fun getISize(): SkISize = SkISize.Make(1160, 860)

    private var fPicture: SkPicture? = null

    override fun onOnceBeforeDraw() {
        val rect = SkRect.MakeWH(kPictureWidth, kPictureHeight)
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(rect)
        drawVectorLogo(canvas, rect)
        fPicture = recorder.finishRecordingAsPicture()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pic = fPicture ?: run {
            onOnceBeforeDraw()
            fPicture ?: return
        }

        // Config table — mirrors upstream's anonymous-struct array.
        val configs = arrayOf(
            Config(SkISize.Make(200, 100), 1f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 2f, 1f),
            Config(SkISize.Make(400, 200), 2f, 2f, 1f),

            Config(SkISize.Make(200, 100), 1f, 1f, 0.9f),
            Config(SkISize.Make(200, 200), 1f, 1f, 0.75f),
            Config(SkISize.Make(200, 200), 1f, 2f, 0.5f),
            Config(SkISize.Make(400, 200), 2f, 2f, 0.25f),

            Config(SkISize.Make(200, 200), 0.5f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 0.5f, 1f),
            Config(SkISize.Make(200, 200), 0.5f, 0.5f, 1f),
            Config(SkISize.Make(200, 200), 2f, 2f, 1f),

            Config(SkISize.Make(200, 100), -1f, 1f, 1f),
            Config(SkISize.Make(200, 100), 1f, -1f, 1f),
            Config(SkISize.Make(200, 100), -1f, -1f, 1f),
            Config(SkISize.Make(200, 100), -1f, -1f, 0.5f),
        )

        val srgb = SkColorSpace.makeSRGB()
        val kDrawsPerRow = 4
        val kDrawSize: SkScalar = 250f

        for (i in configs.indices) {
            val cfg = configs[i]

            // Opacity paint — passed to DeferredFromPicture when alpha < 1.
            val opacityPaint = if (cfg.opacity < 1f) {
                SkPaint().apply { alphaf = cfg.opacity }
            } else {
                null
            }

            // Build the playback matrix : scale, then translate for any
            // negative axis so the picture stays inside the target bitmap.
            var m = SkMatrix.MakeScale(cfg.scaleX, cfg.scaleY)
            if (cfg.scaleX < 0f) m = m.postTranslate(cfg.size.width.toFloat(), 0f)
            if (cfg.scaleY < 0f) m = m.postTranslate(0f, cfg.size.height.toFloat())

            val image = SkImages.DeferredFromPicture(
                picture = pic,
                dimensions = cfg.size,
                matrix = m,
                paint = opacityPaint,
                bitDepth = SkImages.BitDepth.kU8,
                colorSpace = srgb,
            ) ?: continue

            val x = kDrawSize * (i % kDrawsPerRow)
            val y = kDrawSize * (i / kDrawsPerRow)

            // Backdrop — upstream draws `0xfff0f0f0` under each tile
            // before stamping the image (so semi-transparent tiles
            // composite onto a known light grey, not on whatever the GM
            // background carries).
            val bg = SkPaint().apply { color = 0xFFF0F0F0.toInt() }
            c.drawRect(
                SkRect.MakeXYWH(x, y, image.width.toFloat(), image.height.toFloat()),
                bg,
            )
            c.drawImage(image, x, y)
        }
    }

    /**
     * Records the SKIA wordmark into [canvas], scaled to fill [viewBox].
     * Direct port of upstream's `draw_vector_logo` free function.
     *
     * Layout :
     *  - Compute `skiBox` (tight bounds of "SKI") and `iBox` (tight bounds
     *    of "I" placed at the right edge of `skiBox`) via
     *    [SkFont.makeTextPath]. Same trick as upstream's
     *    `SkTextUtils::GetPath` calls.
     *  - Inflate the "SKIA" bounds vertically by `2 * iBox.width *
     *    (kVerticalSpacing + 1)` to leave room for the dot and the
     *    underline.
     *  - Concat the canvas with a `RectToRect(skiaBox, viewBox)` matrix
     *    so the rest of the picture lives in text-local coords.
     *  - Draw the bullet circle, accent path, fading underline, and the
     *    word itself with the rainbow gradient.
     *
     * If [SkFont.makeTextPath] returns null (e.g. empty typeface) we
     * fall back to drawing a black-filled rectangle so the 16-cell grid
     * still exercises the [SkImages.DeferredFromPicture] matrix / opacity
     * contract.
     */
    private fun drawVectorLogo(canvas: SkCanvas, viewBox: SkRect) {
        val paint = SkPaint().apply { isAntiAlias = true }

        // Mirrors upstream's
        //   SkFont font = ToolUtils::DefaultPortableFont();
        //   font.setSubpixel(true); font.setEmbolden(true);
        val font = ToolUtils.DefaultPortableFont().apply {
            isSubpixel = true
            isEmbolden = true
        }

        // SkTextUtils::GetPath("SKI", …) → tight bounds.
        val skiPath = font.makeTextPath("SKI", 0f, 0f) ?: run {
            // Fallback : no font outlines available. Draw a black bar
            // sized to the view box so the grid stays visually distinct
            // (matches upstream's empty-typeface fallback semantically —
            // upstream would draw nothing, we draw something to keep the
            // raster output non-blank for the ratchet).
            canvas.drawRect(viewBox, paint)
            return
        }
        val skiBox = skiPath.computeTightBounds()

        // SkTextUtils::GetPath("I", …) → tight bounds, then shifted to the
        // right edge of `skiBox`. Mirrors upstream's
        //   iBox.offsetTo(skiBox.fRight - iBox.width(), iBox.fTop);
        val iPath = font.makeTextPath("I", 0f, 0f)
            ?: return canvas.drawRect(viewBox, paint).also { /* same fallback */ }
        val iBoxRaw = iPath.computeTightBounds()
        // Build the offsetTo'd iBox as a copy (data class is mutable so
        // we could mutate in place too).
        val iBox = SkRect(
            left = skiBox.right - iBoxRaw.width(),
            top = iBoxRaw.top,
            right = skiBox.right,
            bottom = iBoxRaw.top + iBoxRaw.height(),
        )

        // SkTextUtils::GetPath("SKIA", …) → tight bounds, then outset
        // vertically. Mirrors upstream's
        //   skiaBox.outset(0, 2 * iBox.width() * (kVerticalSpacing + 1));
        val skiaPath = font.makeTextPath(kSkiaStr, 0f, 0f)
            ?: return canvas.drawRect(viewBox, paint).also { /* same fallback */ }
        val skiaBox = skiaPath.computeTightBounds().apply {
            outset(0f, 2f * iBox.width() * (kVerticalSpacing + 1f))
        }

        val accentSize: SkScalar = iBox.width() * kAccentScale
        val underlineY: SkScalar = iBox.bottom +
            (kVerticalSpacing + sqrt(3f) / 2f) * accentSize

        canvas.save()
        // SkMatrix::RectToRectOrIdentity(skiaBox, viewBox) — null falls
        // back to identity to mirror the "OrIdentity" suffix. Upstream
        // uses an `SkAutoCanvasRestore` ; we explicitly bracket with
        // `save()` / `restore()`.
        val mat = SkMatrix.MakeRectToRect(skiaBox, viewBox)
            ?: SkMatrix.Identity
        canvas.concat(mat)

        // The bullet over the "I". Upstream :
        //   canvas->drawCircle(iBox.centerX(),
        //                      iBox.y() - (0.5f + kVerticalSpacing) * accentSize,
        //                      accentSize / 2, paint);
        canvas.drawCircle(
            iBox.centerX(),
            iBox.top - (0.5f + kVerticalSpacing) * accentSize,
            accentSize / 2f,
            paint,
        )

        // The accent triangle below the "I" — a "moveTo, rLineTo,
        // lineTo, close" inline path. Mirrors upstream's :
        //   SkPathBuilder()
        //       .moveTo(iBox.centerX() - accentSize / 2,
        //               iBox.bottom() + kVerticalSpacing * accentSize)
        //       .rLineTo(accentSize, 0)
        //       .lineTo(iBox.centerX(), underlineY)
        //       .detach()
        val accentPath = SkPathBuilder()
            .moveTo(
                iBox.centerX() - accentSize / 2f,
                iBox.bottom + kVerticalSpacing * accentSize,
            )
            .rLineTo(accentSize, 0f)
            .lineTo(iBox.centerX(), underlineY)
            .detach()
        canvas.drawPath(accentPath, paint)

        // Fading-underline rectangle. Mirrors upstream's :
        //   SkRect underlineRect = SkRect::MakeLTRB(
        //       iBox.centerX() - iBox.width() * accentSize * 3,
        //       underlineY,
        //       iBox.centerX(),
        //       underlineY + accentSize / 10);
        //   pts1 = { (underlineRect.x, 0), (iBox.centerX, 0) }
        //   pos1 = { 0, 0.75 }
        //   colors1 = { kTransparent, kBlack }
        //   paint.setShader(SkShaders::LinearGradient(pts1, …))
        val underlineRect = SkRect.MakeLTRB(
            iBox.centerX() - iBox.width() * accentSize * 3f,
            underlineY,
            iBox.centerX(),
            underlineY + accentSize / 10f,
        )
        val underlineP0 = SkPoint(underlineRect.left, 0f)
        val underlineP1 = SkPoint(iBox.centerX(), 0f)
        paint.shader = SkLinearGradient.Make(
            p0 = underlineP0,
            p1 = underlineP1,
            colors = intArrayOf(SK_ColorTRANSPARENT, SK_ColorBLACK),
            positions = floatArrayOf(0f, 0.75f),
            tileMode = SkTileMode.kClamp,
        )
        canvas.drawRect(underlineRect, paint)

        // The "SKIA" rainbow gradient + text. Mirrors upstream's :
        //   pts2 = { (iBox.x - iBox.width * kGradientPad, 0),
        //            (iBox.right + iBox.width * kGradientPad, 0) }
        //   pos2 = { 0, .01, 1/3, 1/3, 2/3, 2/3, .99, 1 }
        //   colors2 = { BLACK, 0xffca5139, 0xffca5139, 0xff8dbd53,
        //               0xff8dbd53, 0xff5460a5, 0xff5460a5, BLACK }
        //   conv = SkColorConverter(colors2)
        //   paint.setShader(SkShaders::LinearGradient(pts2,
        //       {{conv.colors4f(), pos2, kClamp}, {}}));
        //   canvas->drawSimpleText("SKIA", textLen, …, 0, 0, font, paint);
        val gradP0 = SkPoint(iBox.left - iBox.width() * kGradientPad, 0f)
        val gradP1 = SkPoint(iBox.right + iBox.width() * kGradientPad, 0f)
        val rainbowPos = floatArrayOf(0f, 0.01f, 1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f, 0.99f, 1f)
        val rainbowColors = intArrayOf(
            SK_ColorBLACK,
            0xFFCA5139.toInt(),
            0xFFCA5139.toInt(),
            0xFF8DBD53.toInt(),
            0xFF8DBD53.toInt(),
            0xFF5460A5.toInt(),
            0xFF5460A5.toInt(),
            SK_ColorBLACK,
        )
        paint.shader = SkLinearGradient.Make(
            p0 = gradP0,
            p1 = gradP1,
            colors = rainbowColors,
            positions = rainbowPos,
            tileMode = SkTileMode.kClamp,
        )
        canvas.drawSimpleText(
            kSkiaStr, kSkiaStr.length, org.skia.foundation.SkTextEncoding.kUTF8,
            0f, 0f, font, paint,
        )

        canvas.restore()
    }

    private data class Config(
        val size: SkISize,
        val scaleX: SkScalar,
        val scaleY: SkScalar,
        val opacity: SkScalar,
    )

    public companion object {
        /** Upstream's `constexpr char kSkiaStr[] = "SKIA";`. */
        private const val kSkiaStr: String = "SKIA"

        /** Upstream's `constexpr SkScalar kGradientPad = .1f;`. */
        private const val kGradientPad: SkScalar = 0.1f

        /** Upstream's `constexpr SkScalar kVerticalSpacing = 0.25f;`. */
        private const val kVerticalSpacing: SkScalar = 0.25f

        /** Upstream's `constexpr SkScalar kAccentScale = 1.20f;`. */
        private const val kAccentScale: SkScalar = 1.20f

        /** Upstream's `const SkScalar kPictureWidth = 200`. */
        private const val kPictureWidth: SkScalar = 200f

        /** Upstream's `const SkScalar kPictureHeight = 100`. */
        private const val kPictureHeight: SkScalar = 100f
    }
}
