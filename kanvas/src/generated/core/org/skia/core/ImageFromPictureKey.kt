package org.skia.core

import kotlin.UInt
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * struct ImageFromPictureKey : public SkResourceCache::Key {
 * public:
 *     ImageFromPictureKey(SkColorSpace* colorSpace, SkColorType colorType,
 *                         uint32_t pictureID, const SkRect& subset,
 *                         SkSize scale, const SkSurfaceProps& surfaceProps)
 *         : fColorSpaceXYZHash(colorSpace->toXYZD50Hash())
 *         , fColorSpaceTransferFnHash(colorSpace->transferFnHash())
 *         , fColorType(static_cast<uint32_t>(colorType))
 *         , fSubset(subset)
 *         , fScale(scale)
 *         , fSurfaceProps(surfaceProps)
 *     {
 *         static const size_t keySize = sizeof(fColorSpaceXYZHash) +
 *                                       sizeof(fColorSpaceTransferFnHash) +
 *                                       sizeof(fColorType) +
 *                                       sizeof(fSubset) +
 *                                       sizeof(fScale) +
 *                                       sizeof(fSurfaceProps);
 *         // This better be packed.
 *         SkASSERT(sizeof(uint32_t) * (&fEndOfStruct - &fColorSpaceXYZHash) == keySize);
 *         this->init(&gImageFromPictureKeyNamespaceLabel,
 *                    SkPicturePriv::MakeSharedID(pictureID),
 *                    keySize);
 *     }
 *
 * private:
 *     uint32_t       fColorSpaceXYZHash;
 *     uint32_t       fColorSpaceTransferFnHash;
 *     uint32_t       fColorType;
 *     SkRect         fSubset;
 *     SkSize         fScale;
 *     SkSurfaceProps fSurfaceProps;
 *
 *     SkDEBUGCODE(uint32_t fEndOfStruct;)
 * }
 * ```
 */
public open class ImageFromPictureKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t       fColorSpaceXYZHash
   * ```
   */
  private var fColorSpaceXYZHash: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t       fColorSpaceTransferFnHash
   * ```
   */
  private var fColorSpaceTransferFnHash: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t       fColorType
   * ```
   */
  private var fColorType: UInt,
  /**
   * C++ original:
   * ```cpp
   * SkRect         fSubset
   * ```
   */
  private var fSubset: SkRect,
  /**
   * C++ original:
   * ```cpp
   * SkSize         fScale
   * ```
   */
  private var fScale: SkSize,
  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps fSurfaceProps
   * ```
   */
  private var fSurfaceProps: SkSurfaceProps,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ImageFromPictureKey(SkColorSpace* colorSpace, SkColorType colorType,
   *                         uint32_t pictureID, const SkRect& subset,
   *                         SkSize scale, const SkSurfaceProps& surfaceProps)
   *         : fColorSpaceXYZHash(colorSpace->toXYZD50Hash())
   *         , fColorSpaceTransferFnHash(colorSpace->transferFnHash())
   *         , fColorType(static_cast<uint32_t>(colorType))
   *         , fSubset(subset)
   *         , fScale(scale)
   *         , fSurfaceProps(surfaceProps)
   *     {
   *         static const size_t keySize = sizeof(fColorSpaceXYZHash) +
   *                                       sizeof(fColorSpaceTransferFnHash) +
   *                                       sizeof(fColorType) +
   *                                       sizeof(fSubset) +
   *                                       sizeof(fScale) +
   *                                       sizeof(fSurfaceProps);
   *         // This better be packed.
   *         SkASSERT(sizeof(uint32_t) * (&fEndOfStruct - &fColorSpaceXYZHash) == keySize);
   *         this->init(&gImageFromPictureKeyNamespaceLabel,
   *                    SkPicturePriv::MakeSharedID(pictureID),
   *                    keySize);
   *     }
   * ```
   */
  public constructor(
    colorSpace: SkColorSpace?,
    colorType: SkColorType,
    pictureID: UInt,
    subset: SkRect,
    scale: SkSize,
    surfaceProps: SkSurfaceProps,
  ) : this() {
    TODO("Implement constructor")
  }
}
