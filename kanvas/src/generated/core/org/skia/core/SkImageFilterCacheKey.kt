package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkIRect
import org.skia.math.SkMatrix

public typealias CacheImplKey = SkImageFilterCacheKey

/**
 * C++ original:
 * ```cpp
 * struct SkImageFilterCacheKey {
 *     SkImageFilterCacheKey(const uint32_t uniqueID, const SkMatrix& matrix,
 *         const SkIRect& clipBounds, uint32_t srcGenID, const SkIRect& srcSubset)
 *         : fUniqueID(uniqueID)
 *         , fMatrix(matrix)
 *         , fClipBounds(clipBounds)
 *         , fSrcGenID(srcGenID)
 *         , fSrcSubset(srcSubset) {
 *         // Assert that Key is tightly-packed, since it is hashed.
 *         static_assert(sizeof(SkImageFilterCacheKey) == sizeof(uint32_t) + sizeof(SkMatrix) +
 *                                      sizeof(SkIRect) + sizeof(uint32_t) + 4 * sizeof(int32_t),
 *                                      "image_filter_key_tight_packing");
 *         fMatrix.getType();  // force initialization of type, so hashes match
 *         SkASSERT(fMatrix.isFinite());   // otherwise we can't rely on == self when comparing keys
 *     }
 *
 *     uint32_t fUniqueID;
 *     SkMatrix fMatrix;
 *     SkIRect fClipBounds;
 *     uint32_t fSrcGenID;
 *     SkIRect fSrcSubset;
 *
 *     bool operator==(const SkImageFilterCacheKey& other) const {
 *         return fUniqueID == other.fUniqueID &&
 *                fMatrix == other.fMatrix &&
 *                fClipBounds == other.fClipBounds &&
 *                fSrcGenID == other.fSrcGenID &&
 *                fSrcSubset == other.fSrcSubset;
 *     }
 * }
 * ```
 */
public data class SkImageFilterCacheKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  public var fUniqueID: Int,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fMatrix
   * ```
   */
  public var fMatrix: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fClipBounds
   * ```
   */
  public var fClipBounds: SkIRect,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fSrcGenID
   * ```
   */
  public var fSrcGenID: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fSrcSubset
   * ```
   */
  public var fSrcSubset: SkIRect,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkImageFilterCacheKey& other) const {
   *         return fUniqueID == other.fUniqueID &&
   *                fMatrix == other.fMatrix &&
   *                fClipBounds == other.fClipBounds &&
   *                fSrcGenID == other.fSrcGenID &&
   *                fSrcSubset == other.fSrcSubset;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
