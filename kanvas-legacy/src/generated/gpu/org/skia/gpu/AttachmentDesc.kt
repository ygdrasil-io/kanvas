package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AttachmentDesc {
 *     TextureFormat fFormat = TextureFormat::kUnsupported;
 *     LoadOp fLoadOp = LoadOp::kDiscard;
 *     StoreOp fStoreOp = StoreOp::kDiscard;
 *     SampleCount fSampleCount = SampleCount::k1;
 *
 *     bool operator==(const AttachmentDesc& other) const {
 *         if (fFormat == TextureFormat::kUnsupported &&
 *             other.fFormat == TextureFormat::kUnsupported) {
 *             return true;
 *         }
 *
 *         return fFormat == other.fFormat &&
 *                fLoadOp == other.fLoadOp &&
 *                fStoreOp == other.fStoreOp &&
 *                fSampleCount == other.fSampleCount;
 *     }
 *     bool operator!=(const AttachmentDesc& other) const { return !(*this == other); }
 *
 *     bool isCompatible(const TextureInfo&) const;
 *
 *     SkString toString() const;
 * }
 * ```
 */
public data class AttachmentDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextureFormat fFormat
   * ```
   */
  public var fFormat: Int,
  /**
   * C++ original:
   * ```cpp
   * LoadOp fLoadOp
   * ```
   */
  public var fLoadOp: Int,
  /**
   * C++ original:
   * ```cpp
   * StoreOp fStoreOp
   * ```
   */
  public var fStoreOp: Int,
  /**
   * C++ original:
   * ```cpp
   * SampleCount fSampleCount
   * ```
   */
  public var fSampleCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const AttachmentDesc& other) const {
   *         if (fFormat == TextureFormat::kUnsupported &&
   *             other.fFormat == TextureFormat::kUnsupported) {
   *             return true;
   *         }
   *
   *         return fFormat == other.fFormat &&
   *                fLoadOp == other.fLoadOp &&
   *                fStoreOp == other.fStoreOp &&
   *                fSampleCount == other.fSampleCount;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const AttachmentDesc& other) const { return !(*this == other); }
   * ```
   */
  public fun isCompatible(texInfo: TextureInfo): Boolean {
    TODO("Implement isCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AttachmentDesc::isCompatible(const TextureInfo& texInfo) const {
   *     return fFormat == TextureInfoPriv::ViewFormat(texInfo) && fSampleCount == texInfo.sampleCount();
   * }
   * ```
   */
  public override fun toString(): Int {
    TODO("Implement toString")
  }
}
