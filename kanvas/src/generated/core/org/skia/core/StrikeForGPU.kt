package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class StrikeForGPU : public SkRefCnt {
 * public:
 *     virtual void lock() = 0;
 *     virtual void unlock() = 0;
 *
 *     // Generate a digest for a given packed glyph ID as drawn using the give action type.
 *     virtual SkGlyphDigest digestFor(skglyph::ActionType, SkPackedGlyphID) = 0;
 *
 *     // Prepare the glyph to draw an image, and return if the image exists.
 *     virtual bool prepareForImage(SkGlyph*) = 0;
 *
 *     // Prepare the glyph to draw a path, and return if the path exists.
 *     virtual bool prepareForPath(SkGlyph*) = 0;
 *
 *     // Prepare the glyph to draw a drawable, and return if the drawable exists.
 *     virtual bool prepareForDrawable(SkGlyph*) = 0;
 *
 *
 *     virtual const SkDescriptor& getDescriptor() const = 0;
 *
 *     virtual const SkGlyphPositionRoundingSpec& roundingSpec() const = 0;
 *
 *     // Return a strike promise.
 *     virtual SkStrikePromise strikePromise() = 0;
 * }
 * ```
 */
public abstract class StrikeForGPU : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void lock() = 0
   * ```
   */
  public abstract fun lock()

  /**
   * C++ original:
   * ```cpp
   * virtual void unlock() = 0
   * ```
   */
  public abstract fun unlock()

  /**
   * C++ original:
   * ```cpp
   * virtual SkGlyphDigest digestFor(skglyph::ActionType, SkPackedGlyphID) = 0
   * ```
   */
  public abstract fun digestFor(param0: ActionType, param1: SkPackedGlyphID): SkGlyphDigest

  /**
   * C++ original:
   * ```cpp
   * virtual bool prepareForImage(SkGlyph*) = 0
   * ```
   */
  public abstract fun prepareForImage(param0: SkGlyph?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool prepareForPath(SkGlyph*) = 0
   * ```
   */
  public abstract fun prepareForPath(param0: SkGlyph?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool prepareForDrawable(SkGlyph*) = 0
   * ```
   */
  public abstract fun prepareForDrawable(param0: SkGlyph?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual const SkDescriptor& getDescriptor() const = 0
   * ```
   */
  public abstract fun getDescriptor(): SkDescriptor

  /**
   * C++ original:
   * ```cpp
   * virtual const SkGlyphPositionRoundingSpec& roundingSpec() const = 0
   * ```
   */
  public abstract fun roundingSpec(): SkGlyphPositionRoundingSpec

  /**
   * C++ original:
   * ```cpp
   * virtual SkStrikePromise strikePromise() = 0
   * ```
   */
  public abstract fun strikePromise(): SkStrikePromise
}
