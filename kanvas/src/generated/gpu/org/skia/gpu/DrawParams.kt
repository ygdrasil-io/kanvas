package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DrawParams {
 * public:
 *     DrawParams(const Transform& transform,
 *                const Geometry& geometry,
 *                const Clip& clip,
 *                DrawOrder drawOrder,
 *                const StrokeStyle* stroke)
 *             : fTransform(transform)
 *             , fGeometry(geometry)
 *             , fDrawBounds(clip.drawBounds())
 *             , fTransformedShapeBounds(clip.transformedShapeBounds())
 *             , fScissor(clip.scissor())
 *             , fOrder(drawOrder)
 *             , fStroke(stroke ? std::optional<StrokeStyle>(*stroke) : std::nullopt) {}
 *
 *     const Transform& transform() const { return fTransform; }
 *     const Geometry&  geometry()  const { return fGeometry;  }
 *     DrawOrder        order()     const { return fOrder;     }
 *
 *     // The subset of a Clip's state that is preserved in a DrawList, whereas the other properties
 *     // of a clip get consumed into the paint's key and uniform data.
 *     Rect drawBounds() const { return fDrawBounds; }
 *     Rect transformedShapeBounds() const { return fTransformedShapeBounds; }
 *     const SkIRect& scissor() const { return fScissor; }
 *
 *     // Optional stroke parameters if the geometry is stroked instead of filled
 *     bool isStroke() const { return fStroke.has_value(); }
 *     const StrokeStyle& strokeStyle() const {
 *         SkASSERT(this->isStroke());
 *         return *fStroke;
 *     }
 *
 * private:
 *     const Transform& fTransform; // Lifetime of the transform must be held longer than the geometry
 *
 *     Geometry  fGeometry;
 *     Rect      fDrawBounds;
 *     Rect      fTransformedShapeBounds;
 *     SkIRect   fScissor;
 *     DrawOrder fOrder;
 *
 *     std::optional<StrokeStyle> fStroke; // Not present implies fill
 * }
 * ```
 */
public data class DrawParams public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Transform& fTransform
   * ```
   */
  private val fTransform: Transform,
  /**
   * C++ original:
   * ```cpp
   * Geometry  fGeometry
   * ```
   */
  private var fGeometry: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect      fDrawBounds
   * ```
   */
  private var fDrawBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect      fTransformedShapeBounds
   * ```
   */
  private var fTransformedShapeBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect   fScissor
   * ```
   */
  private var fScissor: Int,
  /**
   * C++ original:
   * ```cpp
   * DrawOrder fOrder
   * ```
   */
  private var fOrder: Int,
  /**
   * C++ original:
   * ```cpp
   * std::optional<StrokeStyle> fStroke
   * ```
   */
  private var fStroke: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const Transform& transform() const { return fTransform; }
   * ```
   */
  public fun transform(): Transform {
    TODO("Implement transform")
  }

  /**
   * C++ original:
   * ```cpp
   * const Geometry&  geometry()  const { return fGeometry;  }
   * ```
   */
  public fun geometry(): Int {
    TODO("Implement geometry")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawOrder        order()     const { return fOrder;     }
   * ```
   */
  public fun order(): Int {
    TODO("Implement order")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect drawBounds() const { return fDrawBounds; }
   * ```
   */
  public fun drawBounds(): Int {
    TODO("Implement drawBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect transformedShapeBounds() const { return fTransformedShapeBounds; }
   * ```
   */
  public fun transformedShapeBounds(): Int {
    TODO("Implement transformedShapeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& scissor() const { return fScissor; }
   * ```
   */
  public fun scissor(): Int {
    TODO("Implement scissor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isStroke() const { return fStroke.has_value(); }
   * ```
   */
  public fun isStroke(): Boolean {
    TODO("Implement isStroke")
  }

  /**
   * C++ original:
   * ```cpp
   * const StrokeStyle& strokeStyle() const {
   *         SkASSERT(this->isStroke());
   *         return *fStroke;
   *     }
   * ```
   */
  public fun strokeStyle(): StrokeStyle {
    TODO("Implement strokeStyle")
  }
}
