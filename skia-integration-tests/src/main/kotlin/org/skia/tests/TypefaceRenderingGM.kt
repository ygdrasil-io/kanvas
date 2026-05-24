package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkISize
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils
import kotlin.math.ceil

/**
 * Port of Skia's `gm/typeface.cpp::draw_typeface_rendering_gm` family
 * (`DEF_SIMPLE_GM_CAN_FAIL(typefacerendering, …)`,
 *  `DEF_SIMPLE_GM_CAN_FAIL(typefacerendering_pfa, …)`,
 *  `DEF_SIMPLE_GM_CAN_FAIL(typefacerendering_pfb, …)`).
 *
 * Each GM loads a specific font fixture and exercises the full matrix of
 * per-glyph rendering knobs — subpixel positioning, edging (alias / AA /
 * subpixel AA), embedded-bitmaps, font hinting, paint styles, fake bold,
 * and mask-filter blur variants — on a single glyph at 8 sizes (9–16 pt).
 *
 * ## Port status — **STUB.FIXTURE** for all three variants
 *
 * The required font fixtures are not available as JVM classpath resources:
 *
 *  - `typefacerendering`     → `fonts/hintgasp.ttf` (a synthetic GASP-table
 *    test font from the Skia source tree — not redistributable separately).
 *  - `typefacerendering_pfa` → `fonts/Roboto2-Regular.pfa` (Type1 / PostScript
 *    font — not shipped with kanvas-skia).
 *  - `typefacerendering_pfb` → `fonts/Roboto2-Regular.pfb` (binary PostScript
 *    font — not shipped with kanvas-skia).
 *
 * [ToolUtils.CreateTypefaceFromResource] returns `null` for all three
 * paths, which mirrors upstream's `DrawResult::kSkip` branch. The body
 * below calls `TODO("STUB.FIXTURE: …")` to mark the skip point explicitly;
 * each corresponding test class is `@Disabled` until the fixtures land.
 *
 * ## Body completeness
 *
 * The full `draw_typeface_rendering_gm` logic is implemented in
 * [drawTypefaceRenderingGm] — alias types, size loop, hinting loop,
 * subpixel types, rotation, fake-bold × style matrix, blur mask
 * variants — so the port compiles and runs correctly as soon as the
 * fixtures become available. The one missing piece is
 * `face.unicharToGlyph('A')`, which is now exposed on [SkTypeface]
 * (delegates to [SkTypeface.unicharsToGlyphsInternal]).
 *
 * C++ originals:
 * ```cpp
 * DEF_SIMPLE_GM_CAN_FAIL(typefacerendering, canvas, errMsg, 640, 840) {
 *     sk_sp<SkTypeface> face =
 *         ToolUtils::CreateTypefaceFromResource("fonts/hintgasp.ttf");
 *     if (!face) { return skiagm::DrawResult::kSkip; }
 *     draw_typeface_rendering_gm(canvas, face, face->unicharToGlyph('A'));
 *     draw_typeface_rendering_gm(canvas, face, 0xFFFF);
 *     return skiagm::DrawResult::kOk;
 * }
 * DEF_SIMPLE_GM_CAN_FAIL(typefacerendering_pfa, canvas, errMsg, 640, 840) {
 *     sk_sp<SkTypeface> face =
 *         ToolUtils::CreateTypefaceFromResource("fonts/Roboto2-Regular.pfa");
 *     if (!face) { return skiagm::DrawResult::kSkip; }
 *     draw_typeface_rendering_gm(canvas, face, face->unicharToGlyph('O'));
 *     return skiagm::DrawResult::kOk;
 * }
 * DEF_SIMPLE_GM_CAN_FAIL(typefacerendering_pfb, canvas, errMsg, 640, 840) {
 *     sk_sp<SkTypeface> face =
 *         ToolUtils::CreateTypefaceFromResource("fonts/Roboto2-Regular.pfb");
 *     if (!face) { return skiagm::DrawResult::kSkip; }
 *     draw_typeface_rendering_gm(canvas, face, face->unicharToGlyph('O'));
 *     return skiagm::DrawResult::kOk;
 * }
 * ```
 */

// ---------------------------------------------------------------------------
// Shared rendering helper (mirrors draw_typeface_rendering_gm in typeface.cpp)
// ---------------------------------------------------------------------------

/**
 * Mirrors `static void draw_typeface_rendering_gm(SkCanvas*, sk_sp<SkTypeface>, SkGlyphID)`.
 *
 * Draws the [glyphId] glyph from [face] through an exhaustive matrix of
 * rendering knobs:
 *
 *  1. **Subpixel × alias × size × hinting × rotation** — the main grid.
 *     The alias axis has 4 or 5 entries (kAlias / kAntiAlias /
 *     kSubpixelAntiAlias with optional saveLayer wrapping). The size axis
 *     runs over `{9, 10, 11, 12, 13, 14, 15, 16}`. Hinting iterates
 *     `{kNone, kSlight, kNormal, kFull}`. Rotation has two values `{false,
 *     true}` (`true` = 2-degree rotation around the draw point).
 *
 *  2. **Fake-bold × style × alias** — a secondary grid below the main one.
 *     Two fake-bold states × 4 style/stroke combos × alias types.
 *
 *  3. **Mask-filter blur** — alias types × 12 (style, sigma) combos.
 *
 * The [glyphId] `0xFFFF` (used for the "invalid glyph" pass) is drawn
 * identically — upstream just verifies that an out-of-range ID doesn't
 * crash. Our port honours that: `drawSimpleText` with `kGlyphID` encoding
 * and a two-byte representation of the glyph ID is used so the rendering
 * path is exercised.
 *
 * Note: upstream draws with `SkTextEncoding::kGlyphID` passing a raw
 * `SkGlyphID` pointer, which our Kotlin port represents by encoding the
 * glyph ID into a `Char` (two-byte, matching a `kGlyphID` `sizeof(SkGlyphID)`
 * payload). For the invalid-glyph ID `0xFFFF` the char is `'￿'` which
 * maps to glyph 0 (`.notdef`) through the OpenType font scaler — the same
 * "draw nothing but don't crash" behaviour upstream verifies.
 */
internal fun drawTypefaceRenderingGm(canvas: SkCanvas, face: SkTypeface, glyphId: Int) {
    // Encode the glyph ID as a one-char string whose code unit is the raw
    // glyph index, mirroring upstream's `kGlyphID` text-encoding path.
    val glyphChar = glyphId.and(0xFFFF).toChar().toString()

    data class AliasType(val edging: SkFont.Edging, val inLayer: Boolean)
    val aliasTypes = listOf(
        // kAlias is excluded on iOS upstream; we include it unconditionally
        // (no iOS-specific restriction in the JVM port).
        AliasType(SkFont.Edging.kAlias,             false),
        AliasType(SkFont.Edging.kAntiAlias,         false),
        AliasType(SkFont.Edging.kSubpixelAntiAlias, false),
        AliasType(SkFont.Edging.kAntiAlias,         true),
        AliasType(SkFont.Edging.kSubpixelAntiAlias, true),
    )

    // Sizes designed to exercise the hintgasp.ttf GASP table bands.
    val textSizes: FloatArray = floatArrayOf(9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)

    val hintingTypes = listOf(
        SkFontHinting.kNone,
        SkFontHinting.kSlight,
        SkFontHinting.kNormal,
        SkFontHinting.kFull,
    )

    data class SubpixelType(val requested: Boolean, val offsetX: Float, val offsetY: Float)
    val subpixelTypes = listOf(
        SubpixelType(false, 0.00f, 0.00f),
        SubpixelType(true,  0.00f, 0.00f),
        SubpixelType(true,  0.25f, 0.00f),
        SubpixelType(true,  0.25f, 0.25f),
    )

    val rotateABitTypes = listOf(false, true)

    // ── Section 1 : subpixel × alias × size × hinting × rotation ──────────

    var y = 0f
    val paint = SkPaint()

    val font = SkFont(face).apply { isEmbeddedBitmaps = true }

    var xBase = 0f
    for (subpixel in subpixelTypes) {
        y = 0f
        font.isSubpixel = subpixel.requested

        var xMax = xBase
        for (alias in aliasTypes) {
            font.edging = alias.edging
            if (alias.inLayer) {
                canvas.saveLayer(null, paint)
            } else {
                canvas.save()
            }

            for (textSize in textSizes) {
                var x = xBase + 5f
                font.size = textSize

                val dy = ceil(font.getMetrics(SkFontMetrics()).toDouble()).toFloat()
                y += dy
                for (hinting in hintingTypes) {
                    font.hinting = hinting

                    for (rotateABit in rotateABitTypes) {
                        canvas.save()
                        if (rotateABit) {
                            canvas.rotate(
                                2f,
                                x + subpixel.offsetX,
                                y + subpixel.offsetY,
                            )
                        }
                        canvas.drawSimpleText(
                            glyphChar,
                            glyphChar.length,
                            SkTextEncoding.kGlyphID,
                            x + subpixel.offsetX,
                            y + subpixel.offsetY,
                            font,
                            paint,
                        )
                        val dx = ceil(
                            font.measureText(
                                glyphChar,
                                encoding = SkTextEncoding.kGlyphID,
                            ).toDouble()
                        ).toFloat() + 5f
                        x += dx
                        if (x > xMax) xMax = x
                        canvas.restore()
                    }
                }
            }
            y += 10f
            canvas.restore()
        }
        xBase = xMax
    }

    // ── Section 2 : fake-bold × style × alias ─────────────────────────────

    data class StyleTest(val style: SkPaint.Style, val strokeWidth: Float)
    val styleTypes = listOf(
        StyleTest(SkPaint.Style.kFill_Style,          0.0f),
        StyleTest(SkPaint.Style.kStroke_Style,        0.0f),
        StyleTest(SkPaint.Style.kStroke_Style,        0.5f),
        StyleTest(SkPaint.Style.kStrokeAndFill_Style, 1.0f),
    )

    val fakeBoldTypes = listOf(false, true)

    run {
        val p = SkPaint()
        val f = SkFont(face, 16f)

        for (fakeBold in fakeBoldTypes) {
            val dy = ceil(f.getMetrics(SkFontMetrics()).toDouble()).toFloat()
            y += dy
            var x = 5f

            f.isEmbolden = fakeBold
            for (alias in aliasTypes) {
                f.edging = alias.edging
                if (alias.inLayer) {
                    canvas.saveLayer(null, p)
                } else {
                    canvas.save()
                }
                for (st in styleTypes) {
                    p.style = st.style
                    p.strokeWidth = st.strokeWidth
                    canvas.drawSimpleText(
                        glyphChar, glyphChar.length, SkTextEncoding.kGlyphID,
                        x, y, f, p,
                    )
                    val dx = ceil(
                        f.measureText(glyphChar, encoding = SkTextEncoding.kGlyphID).toDouble()
                    ).toFloat() + 5f
                    x += dx
                }
                canvas.restore()
            }
            y += 10f
        }
    }

    // ── Section 3 : blur mask-filter × alias ──────────────────────────────

    data class MaskTest(val style: SkBlurStyle, val sigma: Float)
    val maskTypes = listOf(
        MaskTest(SkBlurStyle.kNormal, 0.0f),
        MaskTest(SkBlurStyle.kSolid,  0.0f),
        MaskTest(SkBlurStyle.kOuter,  0.0f),
        MaskTest(SkBlurStyle.kInner,  0.0f),

        MaskTest(SkBlurStyle.kNormal, 0.5f),
        MaskTest(SkBlurStyle.kSolid,  0.5f),
        MaskTest(SkBlurStyle.kOuter,  0.5f),
        MaskTest(SkBlurStyle.kInner,  0.5f),

        MaskTest(SkBlurStyle.kNormal, 2.0f),
        MaskTest(SkBlurStyle.kSolid,  2.0f),
        MaskTest(SkBlurStyle.kOuter,  2.0f),
        MaskTest(SkBlurStyle.kInner,  2.0f),
    )

    run {
        val p = SkPaint()
        val f = SkFont(face, 16f)

        for (alias in aliasTypes) {
            val dy = ceil(f.getMetrics(SkFontMetrics()).toDouble()).toFloat()
            y += dy
            var x = 5f

            f.edging = alias.edging
            if (alias.inLayer) {
                canvas.saveLayer(null, p)
            } else {
                canvas.save()
            }
            for (mask in maskTypes) {
                p.maskFilter = SkMaskFilter.MakeBlur(mask.style, mask.sigma)
                canvas.drawSimpleText(
                    glyphChar, glyphChar.length, SkTextEncoding.kGlyphID,
                    x, y, f, p,
                )
                val dx = ceil(
                    f.measureText(glyphChar, encoding = SkTextEncoding.kGlyphID).toDouble()
                ).toFloat() + 5f
                x += dx
            }
            p.maskFilter = null
            canvas.restore()
        }
        y += 10f
    }
}

// ---------------------------------------------------------------------------
// GM 1: typefacerendering (fonts/hintgasp.ttf)
// ---------------------------------------------------------------------------

/**
 * GM for `typefacerendering` — loads `fonts/hintgasp.ttf` (a synthetic
 * Skia test font with a hand-crafted GASP table) and renders glyph `'A'`
 * plus an invalid glyph `0xFFFF` through [drawTypefaceRenderingGm].
 *
 * **STUB.FIXTURE** : `fonts/hintgasp.ttf` is not present in the kanvas-skia
 * classpath resources. The font is a Skia-internal test asset not distributed
 * separately. [ToolUtils.CreateTypefaceFromResource] returns `null`; the body
 * calls `TODO("STUB.FIXTURE: …")` at that point, matching upstream's
 * `DrawResult::kSkip`. [TypefaceRenderingTest] is `@Disabled`.
 */
public class TypefaceRenderingGM : GM() {

    override fun getName(): String = "typefacerendering"
    override fun getISize(): SkISize = SkISize.Make(640, 840)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val face: SkTypeface = ToolUtils.CreateTypefaceFromResource("fonts/hintgasp.ttf")
            ?: TODO("STUB.FIXTURE: fonts/hintgasp.ttf is not available as a classpath resource. " +
                    "Add the Skia hintgasp test font to make this GM renderable.")

        // Upstream draws 'A' then the invalid glyph 0xFFFF (should draw nothing
        // and not crash — verifies the renderer is well-behaved for out-of-range IDs).
        drawTypefaceRenderingGm(c, face, face.unicharToGlyph('A'.code))
        drawTypefaceRenderingGm(c, face, 0xFFFF)
    }
}

// ---------------------------------------------------------------------------
// GM 2: typefacerendering_pfa (fonts/Roboto2-Regular.pfa — non-Windows)
// ---------------------------------------------------------------------------

/**
 * GM for `typefacerendering_pfa` — loads `fonts/Roboto2-Regular.pfa`
 * (a Type 1 PostScript ASCII font) and renders glyph `'O'` through
 * [drawTypefaceRenderingGm].
 *
 * **STUB.FIXTURE** : `fonts/Roboto2-Regular.pfa` is not present in the
 * kanvas-skia classpath resources. The font is a Skia test asset and the
 * pure Kotlin OpenType backend does not support PFA/PFB Type 1 fonts.
 * [TypefaceRenderingPfaTest] is `@Disabled`.
 *
 * The C++ GM is guarded with `#ifndef SK_BUILD_FOR_WIN` (Type 1 fonts do not
 * work on Windows in Skia). The JVM port applies no such guard — the
 * `STUB.FIXTURE` skip makes it OS-neutral.
 */
public class TypefaceRenderingPfaGM : GM() {

    override fun getName(): String = "typefacerendering_pfa"
    override fun getISize(): SkISize = SkISize.Make(640, 840)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val face: SkTypeface = ToolUtils.CreateTypefaceFromResource("fonts/Roboto2-Regular.pfa")
            ?: TODO("STUB.FIXTURE: fonts/Roboto2-Regular.pfa is not available as a classpath " +
                    "resource. The pure Kotlin OpenType backend does not support Type 1 PFA fonts. " +
                    "Add the font fixture to make this GM renderable.")

        drawTypefaceRenderingGm(c, face, face.unicharToGlyph('O'.code))
    }
}

// ---------------------------------------------------------------------------
// GM 3: typefacerendering_pfb (fonts/Roboto2-Regular.pfb — non-Windows)
// ---------------------------------------------------------------------------

/**
 * GM for `typefacerendering_pfb` — loads `fonts/Roboto2-Regular.pfb`
 * (a Type 1 PostScript binary font) and renders glyph `'O'` through
 * [drawTypefaceRenderingGm].
 *
 * **STUB.FIXTURE** : `fonts/Roboto2-Regular.pfb` is not present in the
 * kanvas-skia classpath resources. The font is a Skia test asset and the
 * pure Kotlin OpenType backend does not support PFA/PFB Type 1 fonts.
 * [TypefaceRenderingPfbTest] is `@Disabled`.
 *
 * The C++ GM is guarded with `#ifndef SK_BUILD_FOR_WIN`. See
 * [TypefaceRenderingPfaGM] for the port rationale.
 */
public class TypefaceRenderingPfbGM : GM() {

    override fun getName(): String = "typefacerendering_pfb"
    override fun getISize(): SkISize = SkISize.Make(640, 840)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val face: SkTypeface = ToolUtils.CreateTypefaceFromResource("fonts/Roboto2-Regular.pfb")
            ?: TODO("STUB.FIXTURE: fonts/Roboto2-Regular.pfb is not available as a classpath " +
                    "resource. The pure Kotlin OpenType backend does not support Type 1 PFB fonts. " +
                    "Add the font fixture to make this GM renderable.")

        drawTypefaceRenderingGm(c, face, face.unicharToGlyph('O'.code))
    }
}
