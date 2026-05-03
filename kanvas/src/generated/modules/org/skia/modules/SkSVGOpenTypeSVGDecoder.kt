package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.core.SkOpenTypeSVGDecoder
import org.skia.foundation.SkColor
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SkSVGOpenTypeSVGDecoder : public SkOpenTypeSVGDecoder {
 * public:
 *     static std::unique_ptr<SkOpenTypeSVGDecoder> Make(const uint8_t* svg, size_t svgLength);
 *     size_t approximateSize() override;
 *     bool render(SkCanvas&, int upem, SkGlyphID glyphId,
 *                 SkColor foregroundColor, SkSpan<SkColor> palette) override;
 *     ~SkSVGOpenTypeSVGDecoder() override;
 * private:
 *     SkSVGOpenTypeSVGDecoder(sk_sp<SkSVGDOM> skSvg, size_t approximateSize);
 *     sk_sp<SkSVGDOM> fSkSvg;
 *     size_t fApproximateSize;
 * }
 * ```
 */
public open class SkSVGOpenTypeSVGDecoder public constructor(
  skSvg: SkSp<SkSVGDOM>,
  approximateSize: ULong,
) : SkOpenTypeSVGDecoder() {
  /**
   * C++ original:
   * ```cpp
   * SkSVGOpenTypeSVGDecoder(sk_sp<SkSVGDOM> skSvg, size_t approximateSize)
   * ```
   */
  private var skSp: SkSVGOpenTypeSVGDecoder = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSVGDOM> fSkSvg
   * ```
   */
  private var fSkSvg: Int = TODO("Initialize fSkSvg")

  /**
   * C++ original:
   * ```cpp
   * size_t fApproximateSize
   * ```
   */
  private var fApproximateSize: ULong = TODO("Initialize fApproximateSize")

  /**
   * C++ original:
   * ```cpp
   * size_t SkSVGOpenTypeSVGDecoder::approximateSize() {
   *     // TODO
   *     return fApproximateSize;
   * }
   * ```
   */
  public override fun approximateSize(): ULong {
    TODO("Implement approximateSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGOpenTypeSVGDecoder::render(SkCanvas& canvas, int upem, SkGlyphID glyphId,
   *                                      SkColor foregroundColor, SkSpan<SkColor> palette) {
   *     SkSize emSize = SkSize::Make(SkScalar(upem), SkScalar(upem));
   *     fSkSvg->setContainerSize(emSize);
   *
   *     SkSVGPresentationContext pctx;
   *     pctx.fInherited.fColor.set(foregroundColor);
   *
   *     THashMap<SkString, SkSVGColorType> namedColors;
   *     if (!palette.empty()) {
   *         for (auto&& [i, color] : SkMakeEnumerate(palette)) {
   *             constexpr const size_t colorStringLen = sizeof("color") - 1;
   *             char colorIdString[colorStringLen + kSkStrAppendU32_MaxSize + 1] = "color";
   *             *SkStrAppendU32(colorIdString + colorStringLen, i) = 0;
   *
   *             namedColors.set(SkString(colorIdString), color);
   *         }
   *         pctx.fNamedColors = &namedColors;
   *     }
   *
   *     constexpr const size_t glyphStringLen = sizeof("glyph") - 1;
   *     char glyphIdString[glyphStringLen + kSkStrAppendU32_MaxSize + 1] = "glyph";
   *     *SkStrAppendU32(glyphIdString + glyphStringLen, glyphId) = 0;
   *
   *     fSkSvg->renderNode(&canvas, pctx, glyphIdString);
   *     return true;
   * }
   * ```
   */
  public override fun render(
    canvas: SkCanvas,
    upem: Int,
    glyphId: SkGlyphID,
    foregroundColor: SkColor,
    palette: SkSpan<SkColor>,
  ): Boolean {
    TODO("Implement render")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkOpenTypeSVGDecoder> SkSVGOpenTypeSVGDecoder::Make(const uint8_t* svg,
     *                                                                     size_t svgLength) {
     *     std::unique_ptr<SkStreamAsset> stream = SkMemoryStream::MakeDirect(svg, svgLength);
     *     if (!stream) {
     *         return nullptr;
     *     }
     *     SkSVGDOM::Builder builder;
     *     builder.setResourceProvider(DataResourceProvider::Make());
     *     // We shouldn't need to set this builder's font manager or shaping utils because hopefully
     *     // the SVG we are decoding doesn't itself have <text> tags.
     *     sk_sp<SkSVGDOM> skSvg = builder.make(*stream);
     *     if (!skSvg) {
     *         return nullptr;
     *     }
     *     return std::unique_ptr<SkOpenTypeSVGDecoder>(
     *         new SkSVGOpenTypeSVGDecoder(std::move(skSvg), svgLength));
     * }
     * ```
     */
    public fun make(svg: UByte?, svgLength: ULong): SkSVGOpenTypeSVGDecoder? {
      TODO("Implement make")
    }
  }
}
