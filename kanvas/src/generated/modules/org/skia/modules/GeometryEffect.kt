package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

public typealias PuckerBloatEffectINHERITED = GeometryEffect

/**
 * C++ original:
 * ```cpp
 * class GeometryEffect : public GeometryNode {
 * protected:
 *     explicit GeometryEffect(sk_sp<GeometryNode>);
 *     ~GeometryEffect() override;
 *
 *     void onClip(SkCanvas*, bool antiAlias) const final;
 *     void onDraw(SkCanvas*, const SkPaint&) const final;
 *     bool onContains(const SkPoint&)        const final;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) final;
 *     SkPath onAsPath() const final;
 *
 *     virtual SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) = 0;
 *
 * private:
 *     const sk_sp<GeometryNode> fChild;
 *     SkPath                    fPath; // transformed child cache.
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public abstract class GeometryEffect public constructor(
  child: SkSp<GeometryNode>,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<GeometryNode> fChild
   * ```
   */
  private val fChild: Int = TODO("Initialize fChild")

  /**
   * C++ original:
   * ```cpp
   * SkPath                    fPath
   * ```
   */
  private var fPath: Int = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * void GeometryEffect::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipPath(fPath, SkClipOp::kIntersect, antiAlias);
   * }
   * ```
   */
  protected override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void GeometryEffect::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawPath(fPath, paint);
   * }
   * ```
   */
  protected fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GeometryEffect::onContains(const SkPoint& p) const {
   *     return fPath.contains(p.x(), p.y());
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect GeometryEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     fChild->revalidate(ic, ctm);
   *
   *     fPath = this->onRevalidateEffect(fChild, ctm);
   *
   *     return fPath.computeTightBounds();
   * }
   * ```
   */
  protected fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath GeometryEffect::onAsPath() const {
   *     return fPath;
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) = 0
   * ```
   */
  protected abstract fun onRevalidateEffect(param0: SkSp<GeometryNode>, param1: SkMatrix): Int
}

public typealias TrimEffectINHERITED = GeometryEffect

public typealias GeometryTransformINHERITED = GeometryEffect

public typealias FillTypeOverrideINHERITED = GeometryEffect

public typealias DashEffectINHERITED = GeometryEffect

public typealias RoundEffectINHERITED = GeometryEffect

public typealias OffsetEffectINHERITED = GeometryEffect
