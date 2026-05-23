package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(all_variants_8888,
 * …, 4 * SCALE + 30, 2 * SCALE + 10)` (`SCALE = 128`).
 *
 * Draws an `8 × 2` grid (over a checker background) of `SCALE × SCALE`
 * discs, exhausting the cross-product :
 *
 *  - colour-space : `sRGB` × 2 copies (upstream loops over `[sRGB,
 *    nullptr]` ; `:kanvas-skia`'s [SkImageInfo] always carries a
 *    non-null colour space, so the two columns collapse to the same
 *    sRGB pipeline — see "drift vs upstream" note below).
 *  - alpha type : `kPremul` × `kUnpremul`.
 *  - colour type : `kRGBA_8888` × `kBGRA_8888`.
 *
 * Each cell holds a `SCALE × SCALE` bitmap rendered pixel-by-pixel via
 * `make_pixel(x, y, alphaType)`. Upstream packs that as a raw `uint32_t`
 * `alpha << 24 | component` and stores it through
 * `SkPixmap::writable_addr32` — the *same* bit pattern but interpreted
 * via different byte orderings, which surfaces the alpha-type ×
 * colour-type cross :
 *
 *  - `kPremul`   × `kRGBA_8888` : opaque white disc (alpha == 0xFF, RGB == 0xFF)
 *  - `kPremul`   × `kBGRA_8888` : opaque white disc (identical bytes)
 *  - `kUnpremul` × `kRGBA_8888` : opaque **red** disc
 *    (bytes `[R=0xFF, G=0, B=0, A=alpha]` per the RGBA byte order)
 *  - `kUnpremul` × `kBGRA_8888` : opaque **blue** disc
 *    (bytes `[B=0xFF, G=0, R=0, A=alpha]` per the BGRA byte order)
 *
 * `:kanvas-skia` stores both 8888 colour types as a Pascal-Argb (ARGB
 * Int) backing — see [SkBitmap.pixelsBGRA8888]'s KDoc — so we
 * synthesise the *logical* `SkColor` matching upstream's byte
 * interpretation per (alphaType, colorType) cell and write through
 * [SkBitmap.setPixel].
 *
 * **Drift vs upstream** : the right-half (originally the `nullptr`
 * colour-space branch) renders the same pixels as the left half. The
 * cross-test similarity is therefore loose-floor relative to the
 * `Skia/DM` reference, which has subtle gamut-clipping differences in
 * the right half.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(all_variants_8888, canvas, 4*SCALE+30, 2*SCALE+10) {
 *     ToolUtils::draw_checkerboard(canvas, SK_ColorLTGRAY, SK_ColorWHITE, 8);
 *     sk_sp<SkColorSpace> colorSpaces[] { SkColorSpace::MakeSRGB(), nullptr };
 *     for (const sk_sp<SkColorSpace>& cs : colorSpaces) {
 *         canvas->save();
 *         for (auto at : {kPremul_SkAlphaType, kUnpremul_SkAlphaType}) {
 *             canvas->save();
 *             for (auto ct : {kRGBA_8888_SkColorType, kBGRA_8888_SkColorType}) {
 *                 SkBitmap bm; make_color_test_bitmap_variant(ct, at, cs, &bm);
 *                 canvas->drawImage(bm.asImage(), 0, 0);
 *                 canvas->translate(SCALE + 10, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, SCALE + 10);
 *         }
 *         canvas->restore();
 *         canvas->translate(2 * (SCALE + 10), 0);
 *     }
 * }
 * ```
 */
public class AllVariants8888GM : GM() {

    override fun getName(): String = "all_variants_8888"

    override fun getISize(): SkISize = SkISize.Make(4 * SCALE + 30, 2 * SCALE + 10)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        ToolUtils.draw_checkerboard(c, SK_ColorLTGRAY, SK_ColorWHITE, 8)

        // Upstream walks {sRGB, nullptr} ; we only model sRGB. The
        // duplicated outer iteration keeps the geometry identical to
        // upstream — same 4-column footprint.
        val colorSpaces = listOf(SkColorSpace.makeSRGB(), SkColorSpace.makeSRGB())
        for (colorSpace in colorSpaces) {
            val outerSave = c.save()
            try {
                for (alphaType in listOf(SkAlphaType.kPremul, SkAlphaType.kUnpremul)) {
                    val rowSave = c.save()
                    try {
                        for (colorType in listOf(SkColorType.kRGBA_8888, SkColorType.kBGRA_8888)) {
                            val bm = makeColorTestBitmapVariant(colorType, alphaType, colorSpace)
                            c.drawImage(bm.asImage(), 0f, 0f)
                            c.translate((SCALE + 10).toFloat(), 0f)
                        }
                    } finally {
                        c.restoreToCount(rowSave)
                    }
                    c.translate(0f, (SCALE + 10).toFloat())
                }
            } finally {
                c.restoreToCount(outerSave)
            }
            c.translate((2 * (SCALE + 10)).toFloat(), 0f)
        }
    }

    /**
     * Mirrors upstream's `make_color_test_bitmap_variant(ct, at, cs, &bm)`
     * (`gm/all_bitmap_configs.cpp:222`).
     *
     * Allocates an `SCALE × SCALE` bitmap of the given (colorType,
     * alphaType, colorSpace) and fills it pixel-by-pixel. Each pixel
     * encodes the upstream `make_pixel(x, y, alphaType)` value :
     *
     *  - **premul** : alpha = 0xFF inside a centred disc of radius
     *    `SCALE/2`, else 0x00 ; RGB premultiplied (= alpha). Resolves to
     *    opaque white / transparent black regardless of colour type.
     *  - **unpremul** : alpha = 0xFF (inside) or 0x00 (outside), RGB =
     *    `(0xFF, 0, 0)` for `kRGBA_8888` (red) or `(0, 0, 0xFF)` for
     *    `kBGRA_8888` (blue) — encodes the byte-order difference visible
     *    in the upstream raw-pixel write.
     */
    private fun makeColorTestBitmapVariant(
        colorType: SkColorType,
        alphaType: SkAlphaType,
        colorSpace: SkColorSpace,
    ): SkBitmap {
        require(colorType == SkColorType.kRGBA_8888 || colorType == SkColorType.kBGRA_8888) {
            "all_variants_8888: unexpected colorType $colorType"
        }
        require(alphaType == SkAlphaType.kPremul || alphaType == SkAlphaType.kUnpremul) {
            "all_variants_8888: unexpected alphaType $alphaType"
        }

        val info = SkImageInfo.Make(SCALE, SCALE, colorType, alphaType, colorSpace)
        val bm = SkBitmap.allocPixels(info)

        val r = SCALE / 2f
        for (y in 0 until SCALE) {
            for (x in 0 until SCALE) {
                val dx = x - r
                val dy = y - r
                val inside = (dx * dx + dy * dy) < (r * r)
                val alpha = if (inside) 0xFF else 0x00
                bm.setPixel(x, y, logicalPixel(alpha, alphaType, colorType))
            }
        }
        return bm
    }

    /**
     * Translate upstream's `make_pixel` packed `uint32_t = alpha<<24 |
     * component` into a logical [SkColor] (ARGB) under the (alphaType,
     * colorType) cell. The packed bit pattern is identical across cells
     * upstream ; what changes is how the bytes are interpreted by the
     * destination colour type.
     */
    private fun logicalPixel(
        alpha: Int,
        alphaType: SkAlphaType,
        colorType: SkColorType,
    ): SkColor {
        // Component byte (lowest-address byte of the 32-bit pixel) :
        //  - premul  → equals alpha (inside disc = 0xFF, outside = 0x00).
        //  - unpremul → always 0xFF.
        val componentByte = when (alphaType) {
            SkAlphaType.kPremul -> alpha
            SkAlphaType.kUnpremul -> 0xFF
            else -> error("unreachable")
        }
        return when (colorType) {
            // kRGBA_8888 reads bytes `[R, G, B, A]` from the 32-bit word
            // (little-endian host) → R = componentByte, G = 0, B = 0.
            SkColorType.kRGBA_8888 -> SkColorSetARGB(alpha, componentByte, 0, 0)
            // kBGRA_8888 reads bytes `[B, G, R, A]` → B = componentByte,
            // G = 0, R = 0.
            SkColorType.kBGRA_8888 -> SkColorSetARGB(alpha, 0, 0, componentByte)
            else -> error("unreachable")
        }
    }

    private companion object {
        private const val SCALE: Int = 128
    }
}
