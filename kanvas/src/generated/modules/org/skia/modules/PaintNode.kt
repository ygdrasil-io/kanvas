package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPaint
import undefined.AntiAlias

/**
 * C++ original:
 * ```cpp
 * class PaintNode : public Node {
 * public:
 *     SkPaint makePaint() const;
 *
 *     SG_ATTRIBUTE(AntiAlias  , bool          , fAntiAlias  )
 *     SG_ATTRIBUTE(Opacity    , SkScalar      , fOpacity    )
 *     SG_ATTRIBUTE(BlendMode  , SkBlendMode   , fBlendMode  )
 *     SG_ATTRIBUTE(StrokeWidth, SkScalar      , fStrokeWidth)
 *     SG_ATTRIBUTE(StrokeMiter, SkScalar      , fStrokeMiter)
 *     SG_ATTRIBUTE(Style      , SkPaint::Style, fStyle      )
 *     SG_ATTRIBUTE(StrokeJoin , SkPaint::Join , fStrokeJoin )
 *     SG_ATTRIBUTE(StrokeCap  , SkPaint::Cap  , fStrokeCap  )
 *
 * protected:
 *     PaintNode();
 *
 *     virtual void onApplyToPaint(SkPaint*) const = 0;
 *
 * private:
 *     SkScalar       fOpacity     = 1,
 *                    fStrokeWidth = 1,
 *                    fStrokeMiter = 4;
 *     bool           fAntiAlias   = false;
 *     SkBlendMode    fBlendMode   = SkBlendMode::kSrcOver;
 *     SkPaint::Style fStyle       = SkPaint::kFill_Style;
 *     SkPaint::Join  fStrokeJoin  = SkPaint::kMiter_Join;
 *     SkPaint::Cap   fStrokeCap   = SkPaint::kButt_Cap;
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class PaintNode public constructor() : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar       fOpacity
   * ```
   */
  private var fOpacity: Int = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * SkScalar       fOpacity     = 1,
   *                    fStrokeWidth
   * ```
   */
  private var fStrokeWidth: Int = TODO("Initialize fStrokeWidth")

  /**
   * C++ original:
   * ```cpp
   * SkScalar       fOpacity     = 1,
   *                    fStrokeWidth = 1,
   *                    fStrokeMiter
   * ```
   */
  private var fStrokeMiter: Int = TODO("Initialize fStrokeMiter")

  /**
   * C++ original:
   * ```cpp
   * bool           fAntiAlias   = false
   * ```
   */
  private var fAntiAlias: Boolean = TODO("Initialize fAntiAlias")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode    fBlendMode
   * ```
   */
  private var fBlendMode: Int = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Style fStyle
   * ```
   */
  private var fStyle: Int = TODO("Initialize fStyle")

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join  fStrokeJoin
   * ```
   */
  private var fStrokeJoin: Int = TODO("Initialize fStrokeJoin")

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Cap   fStrokeCap
   * ```
   */
  private var fStrokeCap: Int = TODO("Initialize fStrokeCap")

  /**
   * C++ original:
   * ```cpp
   * SkPaint PaintNode::makePaint() const {
   *     SkASSERT(!this->hasInval());
   *
   *     SkPaint paint;
   *
   *     paint.setAntiAlias(fAntiAlias);
   *     paint.setBlendMode(fBlendMode);
   *     paint.setStyle(fStyle);
   *     paint.setStrokeWidth(fStrokeWidth);
   *     paint.setStrokeMiter(fStrokeMiter);
   *     paint.setStrokeJoin(fStrokeJoin);
   *     paint.setStrokeCap(fStrokeCap);
   *
   *     this->onApplyToPaint(&paint);
   *
   *     // Compose opacity on top of the subclass value.
   *     paint.setAlpha(SkScalarRoundToInt(paint.getAlpha() * SkTPin<SkScalar>(fOpacity, 0, 1)));
   *
   *     return paint;
   * }
   * ```
   */
  public fun makePaint(): Int {
    TODO("Implement makePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(AntiAlias  , bool          , fAntiAlias  )
   * ```
   */
  public fun sgATTRIBUTE(param0: AntiAlias, param1: Boolean): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onApplyToPaint(SkPaint*) const = 0
   * ```
   */
  protected abstract fun onApplyToPaint(param0: SkPaint?)
}

public typealias ShaderPaintINHERITED = PaintNode
