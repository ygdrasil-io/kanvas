package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlobBuilder
import org.skia.tools.ToolUtils
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE

/**
 * M3 -- verify that `paint.maskFilter` (Gaussian blur) is honoured on the
 * GPU backend when text is drawn via [SkCanvas.drawString].
 *
 * Audit context : the J4 closing PR noted that `drawString` and
 * `drawTextBlob` decompose to per-glyph `drawPath` calls in
 * [SkCanvas.drawString] / [SkCanvas.drawTextBlob] (see SkCanvas.kt:2071
 * + 2127-2178). On the GPU side, `SkWebGpuDevice.drawPath` routes
 * `paint.maskFilter is SkBlurMaskFilter` through
 * `drawPathWithBlurMaskFilterIfApplicable` (SkWebGpuDevice.kt:8485)
 * which renders the path into an offscreen mask + runs the separable
 * Gaussian blur cascade. The K2 PR (#612) ratchet'd the same routing on
 * `drawImageRect` but did NOT cover the text path explicitly.
 *
 * What this test asserts : drawing `"AAA"` with a kNormal blur at
 * sigma = 4 produces a soft halo extending outside the crisp glyph
 * footprint. We compare two renders :
 *
 *  - "crisp" : `drawString("AAA", ..., paint = solid black, no maskFilter)`
 *  - "blurred" : same call, `paint.maskFilter = Blur(kNormal, sigma=4)`
 *
 * Invariants the GPU must satisfy :
 *  1. Both renders produce non-trivial pixel coverage (sanity ; tests
 *     that the GPU isn't silently no-op'ing the blurred case).
 *  2. The blurred render has STRICTLY MORE non-white pixels than the
 *     crisp render (the blur spreads coverage outside the original
 *     glyph silhouette).
 *  3. The blurred render's pixels include grey halo (not just pure
 *     black) -- if maskFilter were dropped, we'd see only the crisp
 *     black glyph and the pixel set would be identical to the crisp
 *     render.
 *
 * If maskFilter were silently dropped on the GPU text path (the J4
 * gap that motivated this slice), invariant (2) would fail : the two
 * renders would produce identical non-white-pixel counts.
 */
class TextMaskFilterWebGpuTest {

    private fun countNonWhiteAndGreyPixels(rgba: ByteArray, w: Int, h: Int): Pair<Int, Int> {
        var nonWhite = 0
        var grey = 0
        for (i in 0 until w * h) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            if (r < 250 || g < 250 || b < 250) {
                nonWhite += 1
                // "Grey" -- any pixel that isn't near-pure-black AND
                // isn't near-pure-white. Halo pixels live exactly here.
                if (r > 20 || g > 20 || b > 20) grey += 1
            }
        }
        return Pair(nonWhite, grey)
    }

    @Test
    fun `drawString honours paint maskFilter blur on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val w = 80
            val h = 40
            val font = SkFont(
                ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold()),
                20f,
            )

            // 1. Crisp render -- solid black "AAA", no maskFilter.
            val crispRgba = SkWebGpuDevice(ctx, w, h, applyColorspaceTransform = false)
                .use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    canvas.drawString("AAA", 8f, 26f, font, SkPaint(SK_ColorBLACK))
                    device.flush()
                }

            // 2. Blurred render -- same draw, with a kNormal blur
            // sigma = 4 (well below the cascade threshold ; single-stage
            // 32-tap kernel covers it directly).
            val blurredRgba = SkWebGpuDevice(ctx, w, h, applyColorspaceTransform = false)
                .use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    val paint = SkPaint(SK_ColorBLACK).apply {
                        isAntiAlias = true
                        maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 4f)
                    }
                    canvas.drawString("AAA", 8f, 26f, font, paint)
                    device.flush()
                }

            val (crispNonWhite, _) = countNonWhiteAndGreyPixels(crispRgba, w, h)
            val (blurredNonWhite, blurredGrey) = countNonWhiteAndGreyPixels(blurredRgba, w, h)

            println(
                "[TextMaskFilterWebGpu] crispNonWhite=$crispNonWhite, " +
                    "blurredNonWhite=$blurredNonWhite, blurredGrey=$blurredGrey",
            )

            // Invariant 1 : both renders produce non-trivial coverage.
            assertTrue(
                crispNonWhite >= 30,
                "Crisp 'AAA' should paint ≥ 30 px (got $crispNonWhite). " +
                    "drawString may be no-op'ing on GPU.",
            )
            assertTrue(
                blurredNonWhite >= 30,
                "Blurred 'AAA' should paint ≥ 30 px (got $blurredNonWhite). " +
                    "drawString may be no-op'ing on GPU.",
            )

            // Invariant 2 (key) : blur spreads coverage. The blurred
            // render MUST cover strictly more pixels than the crisp
            // one. If maskFilter were silently dropped, the two counts
            // would be equal (modulo AA noise).
            //
            // Loose margin (10 %) absorbs the AA hairline drift between
            // the two passes ; a real blur at sigma = 4 spreads `3σ =
            // 12 px` per side, which for "AAA" at 20 pt easily doubles
            // the affected pixel set.
            assertTrue(
                blurredNonWhite > crispNonWhite + (crispNonWhite / 10),
                "Blurred 'AAA' coverage ($blurredNonWhite px) is not " +
                    "meaningfully larger than crisp coverage ($crispNonWhite px). " +
                    "Expected the blur to spread coverage by ≥ 10 % ; this " +
                    "suggests paint.maskFilter was silently dropped on the GPU " +
                    "text path.",
            )

            // Invariant 3 : the blurred render contains halo pixels
            // (pixels that aren't near-pure-black and aren't pure
            // white). A dropped maskFilter would yield only the crisp
            // black glyph + pure white background, so grey count would
            // collapse to (essentially) only the AA edge pixels.
            assertTrue(
                blurredGrey >= 50,
                "Blurred 'AAA' should produce ≥ 50 grey halo pixels " +
                    "(got $blurredGrey). A low grey count suggests the blur " +
                    "was dropped and only the crisp glyph AA edges remain.",
            )
        }
    }

    /**
     * Same invariants as the `drawString` test above, but routed through
     * [SkCanvas.drawTextBlob]. Mirrors the per-glyph translate / drawPath
     * loop in SkCanvas.kt:2127-2178 -- each glyph spins up its own
     * offscreen mask + blur pass on the GPU, so an unhonoured maskFilter
     * here would manifest as the blurred render matching the crisp one
     * pixel-for-pixel.
     */
    @Test
    fun `drawTextBlob honours paint maskFilter blur on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val w = 80
            val h = 40
            val font = SkFont(
                ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold()),
                20f,
            )

            // Build a single-glyph blob "A" placed at the same baseline
            // we used in the drawString test, via the canonical
            // SkTextBlobBuilder.allocRunPos path (the same factory used
            // by StrokeTextGM and friends).
            val blob = SkTextBlobBuilder().run {
                val text = "A"
                val rec = allocRunPos(font, 1)
                val codepoints = text.codePoints().toArray()
                val glyphs = ShortArray(1)
                font.unicharsToGlyphs(codepoints, 1, glyphs)
                rec.glyphs[0] = glyphs[0].toInt() and 0xFFFF
                rec.pos[0] = 8f
                rec.pos[1] = 26f
                make()!!
            }

            // 1. Crisp render -- solid black blob, no maskFilter.
            val crispRgba = SkWebGpuDevice(ctx, w, h, applyColorspaceTransform = false)
                .use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    canvas.drawTextBlob(blob, 0f, 0f, SkPaint(SK_ColorBLACK))
                    device.flush()
                }

            // 2. Blurred render.
            val blurredRgba = SkWebGpuDevice(ctx, w, h, applyColorspaceTransform = false)
                .use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    val paint = SkPaint(SK_ColorBLACK).apply {
                        isAntiAlias = true
                        maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 4f)
                    }
                    canvas.drawTextBlob(blob, 0f, 0f, paint)
                    device.flush()
                }

            val (crispNonWhite, _) = countNonWhiteAndGreyPixels(crispRgba, w, h)
            val (blurredNonWhite, blurredGrey) = countNonWhiteAndGreyPixels(blurredRgba, w, h)

            println(
                "[TextMaskFilterWebGpu-Blob] crispNonWhite=$crispNonWhite, " +
                    "blurredNonWhite=$blurredNonWhite, blurredGrey=$blurredGrey",
            )

            assertTrue(
                crispNonWhite >= 10,
                "Crisp 'A' blob should paint ≥ 10 px (got $crispNonWhite).",
            )
            assertTrue(
                blurredNonWhite > crispNonWhite + (crispNonWhite / 10),
                "Blurred 'A' blob coverage ($blurredNonWhite px) is not " +
                    "meaningfully larger than crisp coverage ($crispNonWhite px). " +
                    "Suggests paint.maskFilter was dropped on the GPU drawTextBlob path.",
            )
            assertTrue(
                blurredGrey >= 30,
                "Blurred 'A' blob should produce ≥ 30 grey halo pixels " +
                    "(got $blurredGrey).",
            )
        }
    }
}
