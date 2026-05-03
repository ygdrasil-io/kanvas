package org.skia.core

import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoBlitterChoose : SkNoncopyable {
 * public:
 *     SkAutoBlitterChoose() {}
 *     SkAutoBlitterChoose(const skcpu::Draw& draw,
 *                         const SkMatrix* ctm,
 *                         const SkPaint& paint,
 *                         const SkRect& devBounds,
 *                         SkDrawCoverage drawCoverage = SkDrawCoverage::kNo) {
 *         this->choose(draw, ctm, paint, devBounds, drawCoverage);
 *     }
 *
 *     SkBlitter*  operator->() { return fBlitter; }
 *     SkBlitter*  get() const { return fBlitter; }
 *
 *     SkBlitter* choose(const skcpu::Draw& draw,
 *                       const SkMatrix* ctm,
 *                       const SkPaint& paint,
 *                       const SkRect& devBounds,
 *                       SkDrawCoverage drawCoverage = SkDrawCoverage::kNo) {
 *         SkASSERT(!fBlitter);
 *         fBlitter = draw.fBlitterChooser(draw.fDst,
 *                                         ctm ? *ctm : *draw.fCTM,
 *                                         paint,
 *                                         &fAlloc,
 *                                         drawCoverage,
 *                                         draw.fRC->clipShader(),
 *                                         SkSurfacePropsCopyOrDefault(draw.fProps),
 *                                         devBounds);
 *         return fBlitter;
 *     }
 *
 * private:
 *     // Owned by fAlloc, which will handle the delete.
 *     SkBlitter* fBlitter = nullptr;
 *
 *     SkBlitterSizedArena fAlloc;
 * }
 * ```
 */
public open class SkAutoBlitterChoose public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter* fBlitter = nullptr
   * ```
   */
  private var fBlitter: SkBlitter? = TODO("Initialize fBlitter")

  /**
   * C++ original:
   * ```cpp
   * SkBlitterSizedArena fAlloc
   * ```
   */
  private var fAlloc: SkBlitterSizedArena = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SkAutoBlitterChoose() {}
   * ```
   */
  public constructor(
    draw: Draw,
    ctm: SkMatrix?,
    paint: SkPaint,
    devBounds: SkRect,
    drawCoverage: SkDrawCoverage = TODO(),
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlitter*  operator->() { return fBlitter; }
   * ```
   */
  public fun `get`(): SkBlitter {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlitter*  get() const { return fBlitter; }
   * ```
   */
  public fun choose(
    draw: Draw,
    ctm: SkMatrix?,
    paint: SkPaint,
    devBounds: SkRect,
    drawCoverage: SkDrawCoverage = TODO(),
  ): SkBlitter {
    TODO("Implement choose")
  }
}
