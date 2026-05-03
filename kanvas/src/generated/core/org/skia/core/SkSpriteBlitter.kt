package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect
import org.skia.memory.SkArenaAlloc

public typealias SkSpriteBlitterMemcpyINHERITED = SkSpriteBlitter

public typealias SkRasterPipelineSpriteBlitterINHERITED = SkSpriteBlitter

public typealias SpriteD32S32INHERITED = SkSpriteBlitter

/**
 * C++ original:
 * ```cpp
 * class SkSpriteBlitter : public SkBlitter {
 * public:
 *     SkSpriteBlitter(const SkPixmap& source);
 *
 *     virtual bool setup(const SkPixmap& dst, int left, int top, const SkPaint&);
 *
 *     // blitH, blitAntiH, blitV and blitMask should not be called on an SkSpriteBlitter.
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitMask(const SkMask&, const SkIRect& clip) override;
 *
 *     // A SkSpriteBlitter must implement blitRect.
 *     void blitRect(int x, int y, int width, int height) override = 0;
 *
 *     static SkSpriteBlitter* ChooseL32(const SkPixmap& source, const SkPaint&, SkArenaAlloc*);
 *
 * protected:
 *     SkPixmap        fDst;
 *     const SkPixmap  fSource;
 *     int             fLeft, fTop;
 *     const SkPaint*  fPaint;
 *
 * private:
 *     using INHERITED = SkBlitter;
 * }
 * ```
 */
public abstract class SkSpriteBlitter public constructor(
  source: SkPixmap,
) : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkPixmap        fDst
   * ```
   */
  protected var fDst: SkPixmap = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * const SkPixmap  fSource
   * ```
   */
  protected val fSource: SkPixmap = TODO("Initialize fSource")

  /**
   * C++ original:
   * ```cpp
   * int             fLeft
   * ```
   */
  protected var fLeft: Int = TODO("Initialize fLeft")

  /**
   * C++ original:
   * ```cpp
   * int             fLeft, fTop
   * ```
   */
  protected var fTop: Int = TODO("Initialize fTop")

  /**
   * C++ original:
   * ```cpp
   * const SkPaint*  fPaint
   * ```
   */
  protected val fPaint: SkPaint? = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * bool SkSpriteBlitter::setup(const SkPixmap& dst, int left, int top, const SkPaint& paint) {
   *     fDst = dst;
   *     fLeft = left;
   *     fTop = top;
   *     fPaint = &paint;
   *     return true;
   * }
   * ```
   */
  public open fun setup(
    dst: SkPixmap,
    left: Int,
    top: Int,
    paint: SkPaint,
  ): Boolean {
    TODO("Implement setup")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpriteBlitter::blitH(int x, int y, int width) {
   *     SkDEBUGFAIL("how did we get here?");
   *
   *     // Fallback to blitRect.
   *     this->blitRect(x, y, width, 1);
   * }
   * ```
   */
  public override fun blitH(
    x: Int,
    y: Int,
    width: Int,
  ) {
    TODO("Implement blitH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpriteBlitter::blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) {
   *     SkDEBUGFAIL("how did we get here?");
   *
   *     // No fallback strategy.
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpriteBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     SkDEBUGFAIL("how did we get here?");
   *
   *     // Fall back to superclass if the code gets here in release mode.
   *     INHERITED::blitV(x, y, height, alpha);
   * }
   * ```
   */
  public override fun blitV(
    x: Int,
    y: Int,
    height: Int,
    alpha: SkAlpha,
  ) {
    TODO("Implement blitV")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpriteBlitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkDEBUGFAIL("how did we get here?");
   *
   *     // Fall back to superclass if the code gets here in release mode.
   *     INHERITED::blitMask(mask, clip);
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitRect(int x, int y, int width, int height) override = 0
   * ```
   */
  public abstract override fun blitRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkSpriteBlitter* SkSpriteBlitter::ChooseL32(const SkPixmap& source, const SkPaint& paint,
     *                                             SkArenaAlloc* allocator) {
     *     SkASSERT(allocator != nullptr);
     *
     *     if (paint.getColorFilter() != nullptr) {
     *         return nullptr;
     *     }
     *     if (paint.getMaskFilter() != nullptr) {
     *         return nullptr;
     *     }
     *     if (source.colorType() == kN32_SkColorType && paint.isSrcOver()) {
     *         // this can handle alpha, but not xfermode
     *         return allocator->make<Sprite_D32_S32>(source, paint.getAlpha());
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun chooseL32(
      source: SkPixmap,
      paint: SkPaint,
      allocator: SkArenaAlloc?,
    ): SkSpriteBlitter {
      TODO("Implement chooseL32")
    }
  }
}
