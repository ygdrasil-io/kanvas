package org.skia.modules

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class GeometryNode : public Node {
 * public:
 *     void clip(SkCanvas*, bool antiAlias) const;
 *     void draw(SkCanvas*, const SkPaint&) const;
 *
 *     bool contains(const SkPoint&) const;
 *
 *     SkPath asPath() const;
 *
 * protected:
 *     GeometryNode();
 *
 *     virtual void onClip(SkCanvas*, bool antiAlias) const = 0;
 *
 *     virtual void onDraw(SkCanvas*, const SkPaint&) const = 0;
 *
 *     virtual bool onContains(const SkPoint&) const = 0;
 *
 *     virtual SkPath onAsPath() const = 0;
 *
 * private:
 *     friend class Draw; // wants to know the cached bounds.
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class GeometryNode public constructor() : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void GeometryNode::clip(SkCanvas* canvas, bool aa) const {
   *     SkASSERT(!this->hasInval());
   *     this->onClip(canvas, aa);
   * }
   * ```
   */
  public fun clip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement clip")
  }

  /**
   * C++ original:
   * ```cpp
   * void GeometryNode::draw(SkCanvas* canvas, const SkPaint& paint) const {
   *     SkASSERT(!this->hasInval());
   *     this->onDraw(canvas, paint);
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GeometryNode::contains(const SkPoint& p) const {
   *     SkASSERT(!this->hasInval());
   *     return this->bounds().contains(p.x(), p.y()) ? this->onContains(p) : false;
   * }
   * ```
   */
  public fun contains(p: SkPoint): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath GeometryNode::asPath() const {
   *     SkASSERT(!this->hasInval());
   *     return this->onAsPath();
   * }
   * ```
   */
  public fun asPath(): SkPath {
    TODO("Implement asPath")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onClip(SkCanvas*, bool antiAlias) const = 0
   * ```
   */
  protected abstract fun onClip(param0: SkCanvas?, antiAlias: Boolean)

  /**
   * C++ original:
   * ```cpp
   * virtual void onDraw(SkCanvas*, const SkPaint&) const = 0
   * ```
   */
  protected abstract fun onDraw(param0: SkCanvas?, param1: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * virtual bool onContains(const SkPoint&) const = 0
   * ```
   */
  protected abstract fun onContains(param0: SkPoint): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual SkPath onAsPath() const = 0
   * ```
   */
  protected abstract fun onAsPath(): SkPath
}
