package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import undefined.Typeface

/**
 * C++ original:
 * ```cpp
 * class Text final : public GeometryNode {
 * public:
 *     static sk_sp<Text> Make(sk_sp<SkTypeface> tf, const SkString& text);
 *     ~Text() override;
 *
 *     SG_ATTRIBUTE(Typeface, sk_sp<SkTypeface> , fTypeface)
 *     SG_ATTRIBUTE(Text    , SkString          , fText    )
 *     SG_ATTRIBUTE(Position, SkPoint           , fPosition)
 *     SG_ATTRIBUTE(Size    , SkScalar          , fSize    )
 *     SG_ATTRIBUTE(ScaleX  , SkScalar          , fScaleX  )
 *     SG_ATTRIBUTE(SkewX   , SkScalar          , fSkewX   )
 *     SG_ATTRIBUTE(Align   , SkTextUtils::Align, fAlign   )
 *     SG_ATTRIBUTE(Edging  , SkFont::Edging    , fEdging  )
 *     SG_ATTRIBUTE(Hinting , SkFontHinting     , fHinting )
 *
 *     // TODO: add shaping functionality.
 *
 * protected:
 *     void onClip(SkCanvas*, bool antiAlias) const override;
 *     void onDraw(SkCanvas*, const SkPaint&) const override;
 *     bool onContains(const SkPoint&)        const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *     SkPath onAsPath() const override;
 *
 * private:
 *     Text(sk_sp<SkTypeface>, const SkString&);
 *
 *     SkPoint alignedPosition(SkScalar advance) const;
 *
 *     sk_sp<SkTypeface> fTypeface;
 *     SkString                fText;
 *     SkPoint                 fPosition = SkPoint::Make(0, 0);
 *     SkScalar                fSize     = 12;
 *     SkScalar                fScaleX   = 1;
 *     SkScalar                fSkewX    = 0;
 *     SkTextUtils::Align      fAlign    = SkTextUtils::kLeft_Align;
 *     SkFont::Edging          fEdging   = SkFont::Edging::kAntiAlias;
 *     SkFontHinting           fHinting  = SkFontHinting::kNormal;
 *
 *     sk_sp<SkTextBlob> fBlob; // cached text blob
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public class Text public constructor(
  tf: SkSp<SkTypeface>,
  text: String,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * Text(sk_sp<SkTypeface>, const SkString&)
   * ```
   */
  private var skSp: Text = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fTypeface: Int = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * SkString                fText
   * ```
   */
  private var fText: Int = TODO("Initialize fText")

  /**
   * C++ original:
   * ```cpp
   * SkPoint                 fPosition
   * ```
   */
  private var fPosition: Int = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * SkScalar                fSize
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * SkScalar                fScaleX
   * ```
   */
  private var fScaleX: Int = TODO("Initialize fScaleX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar                fSkewX
   * ```
   */
  private var fSkewX: Int = TODO("Initialize fSkewX")

  /**
   * C++ original:
   * ```cpp
   * SkTextUtils::Align      fAlign
   * ```
   */
  private var fAlign: Int = TODO("Initialize fAlign")

  /**
   * C++ original:
   * ```cpp
   * SkFont::Edging          fEdging
   * ```
   */
  private var fEdging: Int = TODO("Initialize fEdging")

  /**
   * C++ original:
   * ```cpp
   * SkFontHinting           fHinting
   * ```
   */
  private var fHinting: Int = TODO("Initialize fHinting")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fBlob
   * ```
   */
  private var fBlob: Int = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Typeface, sk_sp<SkTypeface> , fTypeface)
   * ```
   */
  public fun sgATTRIBUTE(param0: Typeface, param1: SkSp<SkTypeface>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void Text::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     const auto aligned_pos = this->alignedPosition(this->bounds().width());
   *     canvas->drawTextBlob(fBlob, aligned_pos.x(), aligned_pos.y(), paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Text::onContains(const SkPoint& p) const {
   *     return this->asPath().contains(p.x(), p.y());
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Text::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     // TODO: we could potentially track invals which don't require rebuilding the blob.
   *
   *     SkFont font;
   *     font.setTypeface(fTypeface);
   *     font.setSize(fSize);
   *     font.setScaleX(fScaleX);
   *     font.setSkewX(fSkewX);
   *     font.setEdging(fEdging);
   *     font.setHinting(fHinting);
   *
   *     // N.B.: fAlign is applied externally (in alignedPosition()), because
   *     //  1) SkTextBlob has some trouble computing accurate bounds with alignment.
   *     //  2) SkPaint::Align is slated for deprecation.
   *
   *     fBlob = SkTextBlob::MakeFromText(fText.c_str(), fText.size(), font, SkTextEncoding::kUTF8);
   *     if (!fBlob) {
   *         return SkRect::MakeEmpty();
   *     }
   *
   *     const auto& bounds = fBlob->bounds();
   *     const auto aligned_pos = this->alignedPosition(bounds.width());
   *
   *     return bounds.makeOffset(aligned_pos.x(), aligned_pos.y());
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Text::onAsPath() const {
   *     // TODO
   *     return SkPath();
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint Text::alignedPosition(SkScalar advance) const {
   *     auto aligned = fPosition;
   *
   *     switch (fAlign) {
   *     case SkTextUtils::kLeft_Align:
   *         break;
   *     case SkTextUtils::kCenter_Align:
   *         aligned.offset(-advance / 2, 0);
   *         break;
   *     case SkTextUtils::kRight_Align:
   *         aligned.offset(-advance, 0);
   *         break;
   *     }
   *
   *     return aligned;
   * }
   * ```
   */
  private fun alignedPosition(advance: SkScalar): SkPaint {
    TODO("Implement alignedPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * void Text::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipPath(this->asPath(), antiAlias);
   * }
   * ```
   */
  public override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Text> Text::Make(sk_sp<SkTypeface> tf, const SkString& text) {
     *     return sk_sp<Text>(new Text(std::move(tf), text));
     * }
     * ```
     */
    public fun make(tf: SkSp<SkTypeface>, text: String): Int {
      TODO("Implement make")
    }
  }
}
