package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStrokeRec {
 * public:
 *     enum InitStyle {
 *         kHairline_InitStyle,
 *         kFill_InitStyle
 *     };
 *     explicit SkStrokeRec(InitStyle style);
 *     SkStrokeRec(const SkPaint&, SkPaint::Style, SkScalar resScale = 1);
 *     explicit SkStrokeRec(const SkPaint&, SkScalar resScale = 1);
 *
 *     enum Style {
 *         kHairline_Style,
 *         kFill_Style,
 *         kStroke_Style,
 *         kStrokeAndFill_Style
 *     };
 *
 *     static constexpr int kStyleCount = kStrokeAndFill_Style + 1;
 *
 *     Style getStyle() const;
 *     SkScalar getWidth() const { return fWidth; }
 *     SkScalar getMiter() const { return fMiterLimit; }
 *     SkPaint::Cap getCap() const { return (SkPaint::Cap)fCap; }
 *     SkPaint::Join getJoin() const { return (SkPaint::Join)fJoin; }
 *
 *     bool isHairlineStyle() const {
 *         return kHairline_Style == this->getStyle();
 *     }
 *
 *     bool isFillStyle() const {
 *         return kFill_Style == this->getStyle();
 *     }
 *
 *     void setFillStyle();
 *     void setHairlineStyle();
 *     /**
 *      *  Specify the strokewidth, and optionally if you want stroke + fill.
 *      *  Note, if width==0, then this request is taken to mean:
 *      *      strokeAndFill==true -> new style will be Fill
 *      *      strokeAndFill==false -> new style will be Hairline
 *      */
 *     void setStrokeStyle(SkScalar width, bool strokeAndFill = false);
 *
 *     void setStrokeParams(SkPaint::Cap cap, SkPaint::Join join, SkScalar miterLimit) {
 *         fCap = cap;
 *         fJoin = join;
 *         fMiterLimit = miterLimit;
 *     }
 *
 *     SkScalar getResScale() const {
 *         return fResScale;
 *     }
 *
 *     void setResScale(SkScalar rs) {
 *         SkASSERT(rs > 0 && std::isfinite(rs));
 *         fResScale = rs;
 *     }
 *
 *     /**
 *      *  Returns true if this specifes any thick stroking, i.e. applyToPath()
 *      *  will return true.
 *      */
 *     bool needToApply() const {
 *         Style style = this->getStyle();
 *         return (kStroke_Style == style) || (kStrokeAndFill_Style == style);
 *     }
 *
 *     /**
 *      *  Apply these stroke parameters to the src path, returning the result
 *      *  in dst.
 *      *
 *      *  If there was no change (i.e. style == hairline or fill) this returns
 *      *  false and dst is unchanged. Otherwise returns true and the result is
 *      *  stored in dst.
 *      *
 *      *  src and dst may be the same path.
 *      */
 *     bool applyToPath(SkPathBuilder* dst, const SkPath& src) const;
 *
 *     /**
 *      *  Apply these stroke parameters to a paint.
 *      */
 *     void applyToPaint(SkPaint* paint) const;
 *
 *     /**
 *      * Gives a conservative value for the outset that should applied to a
 *      * geometries bounds to account for any inflation due to applying this
 *      * strokeRec to the geometry.
 *      */
 *     SkScalar getInflationRadius() const;
 *
 *     /**
 *      * Equivalent to:
 *      *   SkStrokeRec rec(paint, style);
 *      *   rec.getInflationRadius();
 *      * This does not account for other effects on the paint (i.e. path
 *      * effect).
 *      */
 *     static SkScalar GetInflationRadius(const SkPaint&, SkPaint::Style);
 *
 *     static SkScalar GetInflationRadius(SkPaint::Join, SkScalar miterLimit, SkPaint::Cap,
 *                                        SkScalar strokeWidth);
 *
 *     /**
 *      * Compare if two SkStrokeRecs have an equal effect on a path.
 *      * Equal SkStrokeRecs produce equal paths. Equality of produced
 *      * paths does not take the ResScale parameter into account.
 *      */
 *     bool hasEqualEffect(const SkStrokeRec& other) const {
 *         if (!this->needToApply()) {
 *             return this->getStyle() == other.getStyle();
 *         }
 *         return fWidth == other.fWidth &&
 *                (fJoin != SkPaint::kMiter_Join || fMiterLimit == other.fMiterLimit) &&
 *                fCap == other.fCap &&
 *                fJoin == other.fJoin &&
 *                fStrokeAndFill == other.fStrokeAndFill;
 *     }
 *
 * private:
 *     void init(const SkPaint&, SkPaint::Style, SkScalar resScale);
 *
 *     SkScalar        fResScale;
 *     SkScalar        fWidth;
 *     SkScalar        fMiterLimit;
 *     // The following three members are packed together into a single u32.
 *     // This is to avoid unnecessary padding and ensure binary equality for
 *     // hashing (because the padded areas might contain garbage values).
 *     //
 *     // fCap and fJoin are larger than needed to avoid having to initialize
 *     // any pad values
 *     uint32_t        fCap : 16;             // SkPaint::Cap
 *     uint32_t        fJoin : 15;            // SkPaint::Join
 *     uint32_t        fStrokeAndFill : 1;    // bool
 * }
 * ```
 */
public data class SkStrokeRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kStyleCount = kStrokeAndFill_Style + 1
   * ```
   */
  private var fResScale: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar        fResScale
   * ```
   */
  private var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar        fWidth
   * ```
   */
  private var fMiterLimit: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar        fMiterLimit
   * ```
   */
  private var fCap: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t        fCap : 16
   * ```
   */
  private var fJoin: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t        fJoin : 15
   * ```
   */
  private var fStrokeAndFill: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * SkStrokeRec::Style SkStrokeRec::getStyle() const {
   *     if (fWidth < 0) {
   *         return kFill_Style;
   *     } else if (0 == fWidth) {
   *         return kHairline_Style;
   *     } else {
   *         return fStrokeAndFill ? kStrokeAndFill_Style : kStroke_Style;
   *     }
   * }
   * ```
   */
  public fun getStyle(): Style {
    TODO("Implement getStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getWidth() const { return fWidth; }
   * ```
   */
  public fun getWidth(): Int {
    TODO("Implement getWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getMiter() const { return fMiterLimit; }
   * ```
   */
  public fun getMiter(): Int {
    TODO("Implement getMiter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Cap getCap() const { return (SkPaint::Cap)fCap; }
   * ```
   */
  public fun getCap(): Int {
    TODO("Implement getCap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join getJoin() const { return (SkPaint::Join)fJoin; }
   * ```
   */
  public fun getJoin(): Int {
    TODO("Implement getJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHairlineStyle() const {
   *         return kHairline_Style == this->getStyle();
   *     }
   * ```
   */
  public fun isHairlineStyle(): Boolean {
    TODO("Implement isHairlineStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFillStyle() const {
   *         return kFill_Style == this->getStyle();
   *     }
   * ```
   */
  public fun isFillStyle(): Boolean {
    TODO("Implement isFillStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrokeRec::setFillStyle() {
   *     fWidth = kStrokeRec_FillStyleWidth;
   *     fStrokeAndFill = false;
   * }
   * ```
   */
  public fun setFillStyle() {
    TODO("Implement setFillStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrokeRec::setHairlineStyle() {
   *     fWidth = 0;
   *     fStrokeAndFill = false;
   * }
   * ```
   */
  public fun setHairlineStyle() {
    TODO("Implement setHairlineStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrokeRec::setStrokeStyle(SkScalar width, bool strokeAndFill) {
   *     if (strokeAndFill && (0 == width)) {
   *         // hairline+fill == fill
   *         this->setFillStyle();
   *     } else {
   *         fWidth = width;
   *         fStrokeAndFill = strokeAndFill;
   *     }
   * }
   * ```
   */
  public fun setStrokeStyle(width: SkScalar, strokeAndFill: Boolean = false) {
    TODO("Implement setStrokeStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStrokeParams(SkPaint::Cap cap, SkPaint::Join join, SkScalar miterLimit) {
   *         fCap = cap;
   *         fJoin = join;
   *         fMiterLimit = miterLimit;
   *     }
   * ```
   */
  public fun setStrokeParams(
    cap: SkPaint.Cap,
    join: SkPaint.Join,
    miterLimit: SkScalar,
  ) {
    TODO("Implement setStrokeParams")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getResScale() const {
   *         return fResScale;
   *     }
   * ```
   */
  public fun getResScale(): Int {
    TODO("Implement getResScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void setResScale(SkScalar rs) {
   *         SkASSERT(rs > 0 && std::isfinite(rs));
   *         fResScale = rs;
   *     }
   * ```
   */
  public fun setResScale(rs: SkScalar) {
    TODO("Implement setResScale")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needToApply() const {
   *         Style style = this->getStyle();
   *         return (kStroke_Style == style) || (kStrokeAndFill_Style == style);
   *     }
   * ```
   */
  public fun needToApply(): Boolean {
    TODO("Implement needToApply")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrokeRec::applyToPath(SkPathBuilder* dst, const SkPath& src) const {
   *     if (fWidth <= 0) {  // hairline or fill
   *         return false;
   *     }
   *
   *     SkStroke stroker;
   *     stroker.setCap((SkPaint::Cap)fCap);
   *     stroker.setJoin((SkPaint::Join)fJoin);
   *     stroker.setMiterLimit(fMiterLimit);
   *     stroker.setWidth(fWidth);
   *     stroker.setDoFill(fStrokeAndFill);
   * #ifdef SK_DEBUG
   *     stroker.setResScale(gDebugStrokerErrorSet ? gDebugStrokerError : fResScale);
   * #else
   *     stroker.setResScale(fResScale);
   * #endif
   *     stroker.strokePath(src, dst);
   *     return true;
   * }
   * ```
   */
  public fun applyToPath(dst: SkPathBuilder?, src: SkPath): Boolean {
    TODO("Implement applyToPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrokeRec::applyToPaint(SkPaint* paint) const {
   *     if (fWidth < 0) {  // fill
   *         paint->setStyle(SkPaint::kFill_Style);
   *         return;
   *     }
   *
   *     paint->setStyle(fStrokeAndFill ? SkPaint::kStrokeAndFill_Style : SkPaint::kStroke_Style);
   *     paint->setStrokeWidth(fWidth);
   *     paint->setStrokeMiter(fMiterLimit);
   *     paint->setStrokeCap((SkPaint::Cap)fCap);
   *     paint->setStrokeJoin((SkPaint::Join)fJoin);
   * }
   * ```
   */
  public fun applyToPaint(paint: SkPaint?) {
    TODO("Implement applyToPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkStrokeRec::getInflationRadius() const {
   *     return GetInflationRadius((SkPaint::Join)fJoin, fMiterLimit, (SkPaint::Cap)fCap, fWidth);
   * }
   * ```
   */
  public fun getInflationRadius(): Int {
    TODO("Implement getInflationRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasEqualEffect(const SkStrokeRec& other) const {
   *         if (!this->needToApply()) {
   *             return this->getStyle() == other.getStyle();
   *         }
   *         return fWidth == other.fWidth &&
   *                (fJoin != SkPaint::kMiter_Join || fMiterLimit == other.fMiterLimit) &&
   *                fCap == other.fCap &&
   *                fJoin == other.fJoin &&
   *                fStrokeAndFill == other.fStrokeAndFill;
   *     }
   * ```
   */
  public fun hasEqualEffect(other: SkStrokeRec): Boolean {
    TODO("Implement hasEqualEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrokeRec::init(const SkPaint& paint, SkPaint::Style style, SkScalar resScale) {
   *     fResScale = resScale;
   *
   *     switch (style) {
   *         case SkPaint::kFill_Style:
   *             fWidth = kStrokeRec_FillStyleWidth;
   *             fStrokeAndFill = false;
   *             break;
   *         case SkPaint::kStroke_Style:
   *             fWidth = paint.getStrokeWidth();
   *             fStrokeAndFill = false;
   *             break;
   *         case SkPaint::kStrokeAndFill_Style:
   *             if (0 == paint.getStrokeWidth()) {
   *                 // hairline+fill == fill
   *                 fWidth = kStrokeRec_FillStyleWidth;
   *                 fStrokeAndFill = false;
   *             } else {
   *                 fWidth = paint.getStrokeWidth();
   *                 fStrokeAndFill = true;
   *             }
   *             break;
   *         default:
   *             SkDEBUGFAIL("unknown paint style");
   *             // fall back on just fill
   *             fWidth = kStrokeRec_FillStyleWidth;
   *             fStrokeAndFill = false;
   *             break;
   *     }
   *
   *     // copy these from the paint, regardless of our "style"
   *     fMiterLimit = paint.getStrokeMiter();
   *     fCap        = paint.getStrokeCap();
   *     fJoin       = paint.getStrokeJoin();
   * }
   * ```
   */
  private fun `init`(
    paint: SkPaint,
    style: SkPaint.Style,
    resScale: SkScalar,
  ) {
    TODO("Implement init")
  }

  public enum class InitStyle {
    kHairline_InitStyle,
    kFill_InitStyle,
  }

  public enum class Style {
    kHairline_Style,
    kFill_Style,
    kStroke_Style,
    kStrokeAndFill_Style,
  }

  public companion object {
    public val kStyleCount: Int = TODO("Initialize kStyleCount")

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkStrokeRec::GetInflationRadius(const SkPaint& paint, SkPaint::Style style) {
     *     SkScalar width = SkPaint::kFill_Style == style ? -SK_Scalar1 : paint.getStrokeWidth();
     *     return GetInflationRadius(paint.getStrokeJoin(), paint.getStrokeMiter(), paint.getStrokeCap(),
     *                               width);
     *
     * }
     * ```
     */
    public fun getInflationRadius(paint: SkPaint, style: SkPaint.Style): Int {
      TODO("Implement getInflationRadius")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkStrokeRec::GetInflationRadius(SkPaint::Join join, SkScalar miterLimit, SkPaint::Cap cap,
     *                                          SkScalar strokeWidth) {
     *     if (strokeWidth < 0) {  // fill
     *         return 0;
     *     } else if (0 == strokeWidth) {
     *         // FIXME: We need a "matrixScale" parameter here in order to properly handle hairlines.
     *         // Their with is determined in device space, unlike other strokes.
     *         // skbug.com/40039419
     *         return SK_Scalar1;
     *     }
     *
     *     // since we're stroked, outset the rect by the radius (and join type, caps)
     *     SkScalar multiplier = SK_Scalar1;
     *     if (SkPaint::kMiter_Join == join) {
     *         multiplier = std::max(multiplier, miterLimit);
     *     }
     *     if (SkPaint::kSquare_Cap == cap) {
     *         multiplier = std::max(multiplier, SK_ScalarSqrt2);
     *     }
     *     return strokeWidth/2 * multiplier;
     * }
     * ```
     */
    public fun getInflationRadius(
      join: SkPaint.Join,
      miterLimit: SkScalar,
      cap: SkPaint.Cap,
      strokeWidth: SkScalar,
    ): Int {
      TODO("Implement getInflationRadius")
    }
  }
}
