package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Short
import org.skia.core.SkIPoint16

/**
 * C++ original:
 * ```cpp
 * class Rectanizer {
 * public:
 *     Rectanizer(int width, int height) : fWidth(width), fHeight(height) {
 *         SkASSERT(width >= 0);
 *         SkASSERT(height >= 0);
 *     }
 *
 *     virtual ~Rectanizer() {}
 *
 *     virtual void reset() = 0;
 *
 *     int width() const { return fWidth; }
 *     int height() const { return fHeight; }
 *
 *     // Attempt to add a rect. Return true on success; false on failure. If
 *     // successful the position in the atlas is returned in 'loc'.
 *     virtual bool addRect(int width, int height, SkIPoint16* loc) = 0;
 *     virtual float percentFull() const = 0;
 *
 *     bool addPaddedRect(int width, int height, int16_t padding, SkIPoint16* loc) {
 *         if (this->addRect(width + 2*padding, height + 2*padding, loc)) {
 *             loc->fX += padding;
 *             loc->fY += padding;
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /**
 *      *  Our factory, which returns the subclass du jour
 *      */
 *     static Rectanizer* Factory(int width, int height);
 *
 * private:
 *     const int fWidth;
 *     const int fHeight;
 * }
 * ```
 */
public abstract class Rectanizer public constructor(
  width: Int,
  height: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const int fWidth
   * ```
   */
  private val fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * const int fHeight
   * ```
   */
  private val fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * virtual void reset() = 0
   * ```
   */
  public abstract fun reset()

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool addRect(int width, int height, SkIPoint16* loc) = 0
   * ```
   */
  public abstract fun addRect(
    width: Int,
    height: Int,
    loc: SkIPoint16?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual float percentFull() const = 0
   * ```
   */
  public abstract fun percentFull(): Float

  /**
   * C++ original:
   * ```cpp
   * bool addPaddedRect(int width, int height, int16_t padding, SkIPoint16* loc) {
   *         if (this->addRect(width + 2*padding, height + 2*padding, loc)) {
   *             loc->fX += padding;
   *             loc->fY += padding;
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun addPaddedRect(
    width: Int,
    height: Int,
    padding: Short,
    loc: SkIPoint16?,
  ): Boolean {
    TODO("Implement addPaddedRect")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * Rectanizer* Rectanizer::Factory(int width, int height) {
     *     return new RectanizerSkyline(width, height);
     * }
     * ```
     */
    public fun factory(width: Int, height: Int): Rectanizer {
      TODO("Implement factory")
    }
  }
}
