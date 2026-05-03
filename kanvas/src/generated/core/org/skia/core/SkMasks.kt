package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SkMasks {
 * public:
 *     // Contains all of the information for a single mask
 *     struct MaskInfo {
 *         uint32_t mask;
 *         uint32_t shift;  // To the left
 *         uint32_t size;   // Of mask width
 *     };
 *
 *     constexpr SkMasks(const MaskInfo red,
 *                       const MaskInfo green,
 *                       const MaskInfo blue,
 *                       const MaskInfo alpha)
 *             : fRed(red), fGreen(green), fBlue(blue), fAlpha(alpha) {}
 *
 *     // Input bit masks format
 *     struct InputMasks {
 *         uint32_t red;
 *         uint32_t green;
 *         uint32_t blue;
 *         uint32_t alpha;
 *     };
 *
 *     // Create the masks object
 *     static SkMasks* CreateMasks(InputMasks masks, int bytesPerPixel);
 *
 *     // Get a color component
 *     uint8_t getRed(uint32_t pixel) const;
 *     uint8_t getGreen(uint32_t pixel) const;
 *     uint8_t getBlue(uint32_t pixel) const;
 *     uint8_t getAlpha(uint32_t pixel) const;
 *
 *     // Getter for the alpha mask
 *     // The alpha mask may be used in other decoding modes
 *     uint32_t getAlphaMask() const { return fAlpha.mask; }
 *
 * private:
 *     const MaskInfo fRed;
 *     const MaskInfo fGreen;
 *     const MaskInfo fBlue;
 *     const MaskInfo fAlpha;
 * }
 * ```
 */
public data class SkMasks public constructor(
  /**
   * C++ original:
   * ```cpp
   * const MaskInfo fRed
   * ```
   */
  private val fRed: MaskInfo,
  /**
   * C++ original:
   * ```cpp
   * const MaskInfo fGreen
   * ```
   */
  private val fGreen: MaskInfo,
  /**
   * C++ original:
   * ```cpp
   * const MaskInfo fBlue
   * ```
   */
  private val fBlue: MaskInfo,
  /**
   * C++ original:
   * ```cpp
   * const MaskInfo fAlpha
   * ```
   */
  private val fAlpha: MaskInfo,
) {
  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMasks::getRed(uint32_t pixel) const {
   *     return get_comp(pixel, fRed.mask, fRed.shift, fRed.size);
   * }
   * ```
   */
  public fun getRed(pixel: UInt): Int {
    TODO("Implement getRed")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMasks::getGreen(uint32_t pixel) const {
   *     return get_comp(pixel, fGreen.mask, fGreen.shift, fGreen.size);
   * }
   * ```
   */
  public fun getGreen(pixel: UInt): Int {
    TODO("Implement getGreen")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMasks::getBlue(uint32_t pixel) const {
   *     return get_comp(pixel, fBlue.mask, fBlue.shift, fBlue.size);
   * }
   * ```
   */
  public fun getBlue(pixel: UInt): Int {
    TODO("Implement getBlue")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMasks::getAlpha(uint32_t pixel) const {
   *     return get_comp(pixel, fAlpha.mask, fAlpha.shift, fAlpha.size);
   * }
   * ```
   */
  public fun getAlpha(pixel: UInt): Int {
    TODO("Implement getAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getAlphaMask() const { return fAlpha.mask; }
   * ```
   */
  public fun getAlphaMask(): Int {
    TODO("Implement getAlphaMask")
  }

  public data class MaskInfo public constructor(
    public var mask: Int,
    public var shift: Int,
    public var size: Int,
  )

  public data class InputMasks public constructor(
    public var red: Int,
    public var green: Int,
    public var blue: Int,
    public var alpha: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkMasks* SkMasks::CreateMasks(InputMasks masks, int bytesPerPixel) {
     *     SkASSERT(0 < bytesPerPixel && bytesPerPixel <= 4);
     *
     *     // Trim the input masks to match bytesPerPixel.
     *     if (bytesPerPixel < 4) {
     *         int bitsPerPixel = 8*bytesPerPixel;
     *         masks.red   &= (1 << bitsPerPixel) - 1;
     *         masks.green &= (1 << bitsPerPixel) - 1;
     *         masks.blue  &= (1 << bitsPerPixel) - 1;
     *         masks.alpha &= (1 << bitsPerPixel) - 1;
     *     }
     *
     *     // Check that masks do not overlap.
     *     if (((masks.red   & masks.green) |
     *          (masks.red   & masks.blue ) |
     *          (masks.red   & masks.alpha) |
     *          (masks.green & masks.blue ) |
     *          (masks.green & masks.alpha) |
     *          (masks.blue  & masks.alpha) ) != 0) {
     *         return nullptr;
     *     }
     *
     *     return new SkMasks(process_mask(masks.red  ),
     *                        process_mask(masks.green),
     *                        process_mask(masks.blue ),
     *                        process_mask(masks.alpha));
     * }
     * ```
     */
    public fun createMasks(masks: InputMasks, bytesPerPixel: Int): SkMasks {
      TODO("Implement createMasks")
    }
  }
}
