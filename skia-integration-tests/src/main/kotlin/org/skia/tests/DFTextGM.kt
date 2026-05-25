package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.withRestore
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTextBlobBuilder
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/dftext.cpp::DFTextGM`](https://github.com/google/skia/blob/main/gm/dftext.cpp)
 * — 1024 × 768, originally **GPU-only** (the reference
 * `original-888/dftext.png` was captured from a Ganesh sink with the
 * distance-field-text shader path).
 *
 * Exercises text rendering through an offscreen surface whose
 * [SkSurfaceProps] carries
 * [SkSurfaceProps.kUseDeviceIndependentFonts_Flag] — the legacy
 * "distance-field text" opt-in that on GPU backends rasterises glyphs
 * via a signed-distance-field shader instead of the per-size glyph
 * atlas. Six geometric variations are exercised through the offscreen
 * canvas :
 *
 *   1. **Scaling up** — four glyph sizes × four CTM scale factors,
 *      stacked vertically along the left edge so each draw covers
 *      progressively larger ink (the DF path is supposed to keep
 *      edges sharp regardless of magnification).
 *   2. **Rotation** — five copies of the same multi-size stack,
 *      rotated by 0/5/10/15/20 degrees around a per-stack pivot ; the
 *      reference proves the DF path doesn't break under non-axis-
 *      aligned CTMs.
 *   3. **Scaling down** — the same four-size stack but with
 *      `1/scale` factors (smaller-than-1-px geometry) to verify the
 *      DF path doesn't collapse to a single mip-level when
 *      minification dominates.
 *   4. **Positioned glyphs** — a single `SkTextBlob` built from
 *      `SkFont.getPos` glyph origins, drawn through `drawTextBlob`
 *      under a 2× CTM. Exercises the text-blob → DF-glyph atlas
 *      lookup path.
 *   5. **Gamma-corrected blending** — two coloured-rectangle
 *      backgrounds (one light-grey, one dark-grey) with the same
 *      8-colour glyph palette drawn over each ; upstream uses this
 *      to check that DF text picks up the surface's gamma profile.
 *   6. **Skew + perspective** — three blocks combining `kAntiAlias`
 *      and `kSubpixelAntiAlias` edging modes with sub-pixel skews
 *      and a 3×3 perspective matrix ; the DF shader is supposed to
 *      stay artifact-free under all three.
 *
 * **Colour-emoji block (`fEmojiSample`) is intentionally omitted** :
 * `:cpu-raster` exposes no upstream-equivalent `ToolUtils::EmojiSample()`
 * (the COLRv1 / Sbix / CBDT colour-bitmap pipeline lands with
 * `STUB.EMOJI_TABLES`, see [ToolUtils.PlanetTypeface]). Upstream
 * itself guards the emoji draw with `if (fEmojiSample.typeface)`, so
 * dropping it here mirrors the host-side null fallback exactly.
 *
 * ## Port status
 *
 * Body fully ported against the live `:kanvas-skia` text-render path
 * (AA path-fill via [SkCanvas.drawSimpleText] / [SkCanvas.drawTextBlob]).
 * A minimal portable raster SDF cache/sampler exists for the narrower
 * [DFTextBlobPerspGM] text-blob case, but this broader GM still routes
 * its `drawSimpleText` sections through regular coverage-AA and keeps the
 * upstream optional emoji block omitted.
 *
 * The associated [DFTextTest] is therefore `@Disabled("STUB.DF_TEXT_FULL_GM")`
 * — see the test file for the detailed gap description.
 */
public class DFTextGM : GM() {

    init {
        setBGColor(0xFFFFFFFF.toInt())
    }

    override fun getName(): String = "dftext"
    override fun getISize(): SkISize = SkISize.Make(1024, 768)

    override fun onDraw(canvas: SkCanvas?) {
        val inputCanvas = canvas ?: return

        val textSizes = floatArrayOf(9.0f, 9.0f * 2.0f, 9.0f * 5.0f, 9.0f * 2.0f * 5.0f)
        val scales = floatArrayOf(2.0f * 5.0f, 5.0f, 2.0f, 1.0f)

        // Set up offscreen rendering — upstream forces the surface to
        // carry `kUseDeviceIndependentFonts_Flag` on top of the input
        // canvas's existing props. `:kanvas-skia` has no DF text shader
        // (raster-only), so the flag round-trips through
        // [SkSurfaceProps] but doesn't change rendering behaviour ;
        // the surface still uses the regular AA coverage path.
        val size = getISize()
        val info = SkImageInfo.MakeN32(
            size.width, size.height,
            org.skia.foundation.SkAlphaType.kPremul,
        )
        val inputProps: SkSurfaceProps = inputCanvas.surfaceProps()
        val props = SkSurfaceProps(
            flags = SkSurfaceProps.kUseDeviceIndependentFonts_Flag or inputProps.flags,
            pixelGeometry = inputProps.pixelGeometry,
        )
        val surface: SkSurface = SkSurface.MakeRaster(info, props)
        val c: SkCanvas = surface.canvas

        // Init the offscreen canvas with the input canvas's matrix —
        // upstream forwards the local-to-device 3×3 explicitly so the
        // offscreen draws land at the same device pixels as if the
        // caller had drawn directly.
        val inputMatrix: SkMatrix = inputCanvas.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity
        c.setMatrix(inputMatrix)
        // Apply a global scale to test glyph positioning under CTM.
        c.scale(1.05f, 1.05f)
        c.clear(0xffffffff.toInt())

        val paint = SkPaint().apply { isAntiAlias = true }

        val font = SkFont(
            ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Normal()),
        ).apply {
            isSubpixel = true
        }

        val text = "Hamburgefons"
        val textLen = text.length

        // ─── 1. Check scaling up ──────────────────────────────────────
        var x = 0f
        var y = 78f
        for (i in textSizes.indices) {
            c.withRestore {
                c.translate(x, y)
                c.scale(scales[i], scales[i])
                font.size = textSizes[i]
                c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
            }
            // Upstream advances y by `font.getMetrics(nullptr) * scales[i]` —
            // `getMetrics` returns the recommended line spacing
            // (ascent + descent + leading, all positive) so y grows
            // by one scaled line per step.
            y += font.getMetrics(SkFontMetrics()) * scales[i]
        }

        // ─── 2. Check rotation ────────────────────────────────────────
        for (i in 0 until 5) {
            val rotX = 10f
            var rotY = y

            c.withRestore {
                c.translate((10 + i * 200).toFloat(), -80f)
                c.rotate((i * 5).toFloat(), rotX, rotY)
                var ps = 6
                while (ps <= 32) {
                    font.size = ps.toFloat()
                    c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, rotX, rotY, font, paint)
                    rotY += font.getMetrics(SkFontMetrics())
                    ps += 3
                }
            }
        }

        // ─── 3. Check scaling down ────────────────────────────────────
        font.edging = SkFont.Edging.kSubpixelAntiAlias
        x = 680f
        y = 20f
        val arraySize = textSizes.size
        for (i in 0 until arraySize) {
            c.withRestore {
                c.translate(x, y)
                val scaleFactor = 1f / scales[arraySize - i - 1]
                c.scale(scaleFactor, scaleFactor)
                font.size = textSizes[i]
                c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
            }
            // Same `font.getMetrics(nullptr) * scaleFactor` advance —
            // recomputed here because each loop iteration overrides
            // `font.size` via `textSizes[i]`.
            val scaleFactor = 1f / scales[arraySize - i - 1]
            y += font.getMetrics(SkFontMetrics()) * scaleFactor
        }

        // ─── 4. Check pos text ────────────────────────────────────────
        c.withRestore {
            c.scale(2.0f, 2.0f)

            // Resolve glyph IDs + per-glyph positions through SkFont
            // (upstream uses `textToGlyphs` + `getPos`). Anchor at
            // (340, 75) — these are intentionally pre-scaled coords
            // chosen to land the run inside the offscreen surface
            // after the surrounding 2× CTM.
            font.size = textSizes[0]
            val glyphs = font.textToGlyphs(text, SkTextEncoding.kUTF8)
            val pos: Array<SkPoint> = font.getPos(glyphs, SkPoint(340f, 75f))

            // Build a positioned-glyph blob via the per-glyph
            // `allocRunPos` recipe — Skia's
            // `SkTextBlob::MakeFromPosGlyphs` collapses onto exactly
            // this in the upstream builder code.
            val builder = SkTextBlobBuilder()
            val alloc = builder.allocRunPos(font, glyphs.size)
            for (g in glyphs.indices) {
                alloc.glyphs[g] = glyphs[g]
                alloc.pos[2 * g] = pos[g].fX
                alloc.pos[2 * g + 1] = pos[g].fY
            }
            val blob = builder.make()
            if (blob != null) {
                c.drawTextBlob(blob, 0f, 0f, paint)
            }
        }

        // ─── 5. Check gamma-corrected blending ───────────────────────
        val fg = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFF000000.toInt(),
        )

        paint.color = 0xFFF7F3F7.toInt()
        var rect = org.graphiks.math.SkRect.MakeLTRB(670f, 215f, 820f, 397f)
        c.drawRect(rect, paint)

        x = 680f
        y = 235f
        font.size = 19f
        for (color in fg) {
            paint.color = color
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, paint)
            y += font.getMetrics(SkFontMetrics())
        }

        paint.color = 0xFF181C18.toInt()
        rect = org.graphiks.math.SkRect.MakeLTRB(820f, 215f, 970f, 397f)
        c.drawRect(rect, paint)

        x = 830f
        y = 235f
        font.size = 19f
        for (color in fg) {
            paint.color = color
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, paint)
            y += font.getMetrics(SkFontMetrics())
        }

        // ─── 6. Check skew ────────────────────────────────────────────
        c.withRestore {
            font.edging = SkFont.Edging.kAntiAlias
            c.skew(0.0f, 0.151515f)
            font.size = 32f
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 745f, 70f, font, paint)
        }
        c.withRestore {
            font.edging = SkFont.Edging.kSubpixelAntiAlias
            c.skew(0.5f, 0.0f)
            font.size = 32f
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 580f, 125f, font, paint)
        }

        // ─── 7. Check perspective ────────────────────────────────────
        c.withRestore {
            font.edging = SkFont.Edging.kAntiAlias
            val persp = SkMatrix.MakeAll(
                0.9839f,     0f,          0f,
                0.2246f,     0.6829f,     0f,
                0.0002352f, -0.0003844f,  1f,
            )
            c.concat(persp)
            c.translate(1100f, -295f)
            font.size = 37.5f
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
        }
        c.withRestore {
            font.isSubpixel = false
            font.edging = SkFont.Edging.kAlias
            val persp = SkMatrix.MakeAll(
                0.9839f,     0f,          0f,
                0.2246f,     0.6829f,     0f,
                0.0002352f, -0.0003844f,  1f,
            )
            c.concat(persp)
            c.translate(1075f, -245f)
            c.scale(375f, 375f)
            font.size = 0.1f
            c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
        }

        // ─── 8. Colour-emoji block ──────────────────────────────────
        // Intentionally omitted — `:cpu-raster` has no
        // `ToolUtils::EmojiSample()` equivalent (the COLRv1 / Sbix /
        // CBDT colour-bitmap pipeline lands with `STUB.EMOJI_TABLES`).
        // Upstream itself guards this block with
        // `if (fEmojiSample.typeface)`, so the host-side null fallback
        // is the matching behaviour here.

        // Render offscreen buffer — upstream resets the input canvas's
        // matrix to identity before blitting because the offscreen
        // matrix was prepended from the input matrix above.
        inputCanvas.withRestore {
            inputCanvas.resetMatrix()
            inputCanvas.drawImage(surface.makeImageSnapshot(), 0f, 0f)
        }
    }
}
