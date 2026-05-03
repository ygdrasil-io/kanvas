package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkShader

/**
 * C++ original:
 * ```cpp
 * class Clip {
 * public:
 *     Clip() = default;
 *     Clip(const Rect& drawBounds,
 *          const Rect& shapeBounds,
 *          const SkIRect& scissor,
 *          const NonMSAAClip& nonMSAAClip,
 *          const SkShader* shader)
 *             : fDrawBounds(drawBounds)
 *             , fTransformedShapeBounds(shapeBounds)
 *             , fScissor(scissor)
 *             , fNonMSAAClip(nonMSAAClip)
 *             , fShader(shader) {}
 *
 *     // Tight bounds of the draw, including any padding/outset for stroking and expansion due to
 *     // inverse fill and intersected with the scissor.
 *     const Rect& drawBounds() const { return fDrawBounds; }
 *
 *     // The scissor rectangle obtained by restricting the bounds of the clip stack that affects the
 *     // draw to the device bounds. The scissor must contain drawBounds() and must already be
 *     // intersected with the device bounds.
 *     const SkIRect& scissor() const { return fScissor; }
 *
 *     // Unclipped bounds of the shape in device space, including any padding/outset for stroking but
 *     // ignoring the fill rule. This is not restricted by the scissor (or the target device's
 *     // physical bounds).
 *     //
 *     // For a regular fill, drawBounds() is the intersection of this rectangle and scissor().
 *     //
 *     // For an inverse fill, this is the bounding box of the interesting portion of any coverage
 *     // mask. If it doesn't intersect the scissor, the draw fully covers the scissor; regardless the
 *     // drawBounds() are equal to the scissor.
 *     const Rect& transformedShapeBounds() const { return fTransformedShapeBounds; }
 *
 *     // If set, the shape's bounds and/or an atlas mask are further used to clip the draw.
 *     // NOTE: This cannot impact `drawBounds()` as pixels outside of the non-msaa clip may still be
 *     // shaded and blended with a coverage value of 0, which could lead to undefined behavior on the
 *     // GPU if operations were ordered assuming tighter bounds.
 *     const NonMSAAClip& nonMSAAClip() const { return fNonMSAAClip; }
 *
 *     // If set, the clip shader's output alpha is further used to clip the draw.
 *     const SkShader* shader() const { return fShader; }
 *
 *     bool isClippedOut() const { return fDrawBounds.isEmptyNegativeOrNaN(); }
 *
 *     bool needsCoverage() const { return SkToBool(fShader) || !fNonMSAAClip.isEmpty(); }
 *
 *     void outsetBoundsForAA() {
 *         // We use 1px to handle both subpixel/hairline approaches and the standard 1/2px outset
 *         // for shapes that cover multiple pixels.
 *         fTransformedShapeBounds.outset(1.f);
 *         // This is a no-op for inverse fills (where fDrawBounds was already equal to fScissor),
 *         // and equivalent to fDrawBounds = fTransformedShapeBounds.makeIntersect(fScissor) with
 *         // the outset shape bounds.
 *         fDrawBounds.outset(1.f).intersect(fScissor);
 *     }
 *
 * private:
 *     // DrawList assumes the DrawBounds are correct for a given shape, transform, and style. They
 *     // are provided to the DrawList to avoid re-calculating the same bounds.
 *     Rect              fDrawBounds;
 *     Rect              fTransformedShapeBounds;
 *     SkIRect           fScissor;
 *     NonMSAAClip       fNonMSAAClip;
 *     const SkShader*   fShader;
 * }
 * ```
 */
public data class Clip public constructor(
  /**
   * C++ original:
   * ```cpp
   * Rect              fDrawBounds
   * ```
   */
  private var fDrawBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect              fTransformedShapeBounds
   * ```
   */
  private var fTransformedShapeBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect           fScissor
   * ```
   */
  private var fScissor: Int,
  /**
   * C++ original:
   * ```cpp
   * NonMSAAClip       fNonMSAAClip
   * ```
   */
  private var fNonMSAAClip: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkShader*   fShader
   * ```
   */
  private val fShader: SkShader?,
) {
  /**
   * C++ original:
   * ```cpp
   * const Rect& drawBounds() const { return fDrawBounds; }
   * ```
   */
  public fun drawBounds(): Int {
    TODO("Implement drawBounds")
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
   * const Rect& transformedShapeBounds() const { return fTransformedShapeBounds; }
   * ```
   */
  public fun transformedShapeBounds(): Int {
    TODO("Implement transformedShapeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const NonMSAAClip& nonMSAAClip() const { return fNonMSAAClip; }
   * ```
   */
  public fun nonMSAAClip(): Int {
    TODO("Implement nonMSAAClip")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkShader* shader() const { return fShader; }
   * ```
   */
  public fun shader(): SkShader {
    TODO("Implement shader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClippedOut() const { return fDrawBounds.isEmptyNegativeOrNaN(); }
   * ```
   */
  public fun isClippedOut(): Boolean {
    TODO("Implement isClippedOut")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needsCoverage() const { return SkToBool(fShader) || !fNonMSAAClip.isEmpty(); }
   * ```
   */
  public fun needsCoverage(): Boolean {
    TODO("Implement needsCoverage")
  }

  /**
   * C++ original:
   * ```cpp
   * void outsetBoundsForAA() {
   *         // We use 1px to handle both subpixel/hairline approaches and the standard 1/2px outset
   *         // for shapes that cover multiple pixels.
   *         fTransformedShapeBounds.outset(1.f);
   *         // This is a no-op for inverse fills (where fDrawBounds was already equal to fScissor),
   *         // and equivalent to fDrawBounds = fTransformedShapeBounds.makeIntersect(fScissor) with
   *         // the outset shape bounds.
   *         fDrawBounds.outset(1.f).intersect(fScissor);
   *     }
   * ```
   */
  public fun outsetBoundsForAA() {
    TODO("Implement outsetBoundsForAA")
  }
}
