package org.skia.gpu

import kotlin.Int
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkPath
import org.skia.math.SkPathFillType
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class VelloScene final {
 * public:
 *     VelloScene();
 *
 *     void reset();
 *
 *     void solidFill(const SkPath&,
 *                    const SkColor4f&,
 *                    const SkPathFillType,
 *                    const Transform& transform);
 *
 *     void solidStroke(const SkPath&,
 *                      const SkColor4f&,
 *                      const SkStrokeRec&,
 *                      const Transform& transform);
 *
 *     void pushClipLayer(const SkPath& shape, const Transform& transform);
 *     void popClipLayer();
 *
 *     void append(const VelloScene& other);
 *
 * private:
 *     friend class VelloRenderer;
 *
 *     // Disallow copy
 *     VelloScene(const VelloScene&) = delete;
 *     VelloScene& operator=(const VelloScene&) = delete;
 *
 *     ::rust::Box<::vello_cpp::Encoding> fEncoding;
 *     SkDEBUGCODE(int fLayers = 0;)
 * }
 * ```
 */
public abstract class VelloScene public constructor() {
  /**
   * C++ original:
   * ```cpp
   * ::rust::Box<::vello_cpp::Encoding> fEncoding
   * ```
   */
  private var fEncoding: Int = TODO("Initialize fEncoding")

  /**
   * C++ original:
   * ```cpp
   * VelloScene()
   * ```
   */
  public constructor(param0: VelloScene) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset()
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void solidFill(const SkPath&,
   *                    const SkColor4f&,
   *                    const SkPathFillType,
   *                    const Transform& transform)
   * ```
   */
  public fun solidFill(
    param0: SkPath,
    param1: SkColor4f,
    param2: SkPathFillType,
    transform: Transform,
  ) {
    TODO("Implement solidFill")
  }

  /**
   * C++ original:
   * ```cpp
   * void solidStroke(const SkPath&,
   *                      const SkColor4f&,
   *                      const SkStrokeRec&,
   *                      const Transform& transform)
   * ```
   */
  public fun solidStroke(
    param0: SkPath,
    param1: SkColor4f,
    param2: SkStrokeRec,
    transform: Transform,
  ) {
    TODO("Implement solidStroke")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushClipLayer(const SkPath& shape, const Transform& transform)
   * ```
   */
  public fun pushClipLayer(shape: SkPath, transform: Transform) {
    TODO("Implement pushClipLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void popClipLayer()
   * ```
   */
  public fun popClipLayer() {
    TODO("Implement popClipLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void append(const VelloScene& other)
   * ```
   */
  public fun append(other: VelloScene) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * VelloScene& operator=(const VelloScene&) = delete
   * ```
   */
  private fun assign(param0: VelloScene) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(int fLayers = 0;)
   * ```
   */
  private abstract fun skDEBUGCODE(param0: Int): Int
}
