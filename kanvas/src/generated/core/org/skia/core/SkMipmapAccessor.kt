package org.skia.core

import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmap
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkMipmapAccessor : ::SkNoncopyable {
 * public:
 *     // Returns null on failure
 *     static SkMipmapAccessor* Make(SkArenaAlloc*, const SkImage*, const SkMatrix& inv, SkMipmapMode);
 *
 *     std::pair<SkPixmap, SkMatrix> level() const {
 *         SkASSERT(fUpper.addr() != nullptr);
 *         return std::make_pair(fUpper, fUpperInv);
 *     }
 *
 *     std::pair<SkPixmap, SkMatrix> lowerLevel() const {
 *         SkASSERT(fLower.addr() != nullptr);
 *         return std::make_pair(fLower, fLowerInv);
 *     }
 *
 *     // 0....1. Will be 0 if there is no lowerLevel
 *     float lowerWeight() const { return fLowerWeight; }
 *
 * private:
 *     SkPixmap     fUpper,
 *                  fLower; // only valid for mip_linear
 *     float        fLowerWeight;   // lower * weight + upper * (1 - weight)
 *     SkMatrix     fUpperInv,
 *                  fLowerInv;
 *
 *     // these manage lifetime for the buffers
 *     SkBitmap              fBaseStorage;
 *     sk_sp<const SkMipmap> fCurrMip;
 *
 * public:
 *     // Don't call publicly -- this is only public for SkArenaAlloc to access it inside Make()
 *     SkMipmapAccessor(const SkImage_Base*, const SkMatrix& inv, SkMipmapMode requestedMode);
 * }
 * ```
 */
public open class SkMipmapAccessor public constructor(
  image: SkImageBase?,
  inv: SkMatrix,
  requestedMode: SkMipmapMode,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkPixmap     fUpper
   * ```
   */
  private var fUpper: SkPixmap = TODO("Initialize fUpper")

  /**
   * C++ original:
   * ```cpp
   * SkPixmap     fUpper,
   *                  fLower
   * ```
   */
  private var fLower: SkPixmap = TODO("Initialize fLower")

  /**
   * C++ original:
   * ```cpp
   * float        fLowerWeight
   * ```
   */
  private var fLowerWeight: Float = TODO("Initialize fLowerWeight")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix     fUpperInv
   * ```
   */
  private var fUpperInv: SkMatrix = TODO("Initialize fUpperInv")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix     fUpperInv,
   *                  fLowerInv
   * ```
   */
  private var fLowerInv: SkMatrix = TODO("Initialize fLowerInv")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap              fBaseStorage
   * ```
   */
  private var fBaseStorage: SkBitmap = TODO("Initialize fBaseStorage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkMipmap> fCurrMip
   * ```
   */
  private val fCurrMip: SkSp<SkMipmap> = TODO("Initialize fCurrMip")

  /**
   * C++ original:
   * ```cpp
   * std::pair<SkPixmap, SkMatrix> level() const {
   *         SkASSERT(fUpper.addr() != nullptr);
   *         return std::make_pair(fUpper, fUpperInv);
   *     }
   * ```
   */
  public fun level(): Int {
    TODO("Implement level")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<SkPixmap, SkMatrix> lowerLevel() const {
   *         SkASSERT(fLower.addr() != nullptr);
   *         return std::make_pair(fLower, fLowerInv);
   *     }
   * ```
   */
  public fun lowerLevel(): Int {
    TODO("Implement lowerLevel")
  }

  /**
   * C++ original:
   * ```cpp
   * float lowerWeight() const { return fLowerWeight; }
   * ```
   */
  public fun lowerWeight(): Float {
    TODO("Implement lowerWeight")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkMipmapAccessor* SkMipmapAccessor::Make(SkArenaAlloc* alloc, const SkImage* image,
     *                                          const SkMatrix& inv, SkMipmapMode mipmap) {
     *     auto* access = alloc->make<SkMipmapAccessor>(as_IB(image), inv, mipmap);
     *     // return null if we failed to get the level (so the caller won't try to use it)
     *     return access->fUpper.addr() ? access : nullptr;
     * }
     * ```
     */
    public fun make(
      alloc: SkArenaAlloc?,
      image: SkImage?,
      inv: SkMatrix,
      mipmap: SkMipmapMode,
    ): SkMipmapAccessor {
      TODO("Implement make")
    }
  }
}
