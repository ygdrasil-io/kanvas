package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkAutoDescriptor
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkTypefaceID

/**
 * C++ original:
 * ```cpp
 * class SkStrikeClient {
 * public:
 *     // This enum is used in histogram reporting in chromium. Please don't re-order the list of
 *     // entries, and consider it to be append-only.
 *     enum CacheMissType : uint32_t {
 *         // Hard failures where no fallback could be found.
 *         kFontMetrics = 0,
 *         kGlyphMetrics = 1,
 *         kGlyphImage = 2,
 *         kGlyphPath = 3,
 *
 *         // (DEPRECATED) The original glyph could not be found and a fallback was used.
 *         kGlyphMetricsFallback = 4,
 *         kGlyphPathFallback    = 5,
 *
 *         kGlyphDrawable = 6,
 *         kLast = kGlyphDrawable
 *     };
 *
 *     // An interface to delete handles that may be pinned by the remote server.
 *     class DiscardableHandleManager : public SkRefCnt {
 *     public:
 *         ~DiscardableHandleManager() override = default;
 *
 *         // Returns true if the handle was unlocked and can be safely deleted. Once
 *         // successful, subsequent attempts to delete the same handle are invalid.
 *         virtual bool deleteHandle(SkDiscardableHandleId) = 0;
 *
 *         virtual void assertHandleValid(SkDiscardableHandleId) {}
 *
 *         virtual void notifyCacheMiss(CacheMissType type, int fontSize) = 0;
 *
 *         struct ReadFailureData {
 *             size_t memorySize;
 *             size_t bytesRead;
 *             uint64_t typefaceSize;
 *             uint64_t strikeCount;
 *             uint64_t glyphImagesCount;
 *             uint64_t glyphPathsCount;
 *         };
 *         virtual void notifyReadFailure(const ReadFailureData& data) {}
 *     };
 *
 *     SK_SPI explicit SkStrikeClient(sk_sp<DiscardableHandleManager>,
 *                                    bool isLogging = true,
 *                                    SkStrikeCache* strikeCache = nullptr);
 *     SK_SPI ~SkStrikeClient();
 *
 *     // Deserializes the strike data from a SkStrikeServer. All messages generated
 *     // from a server when serializing the ops must be deserialized before the op
 *     // is rasterized.
 *     // Returns false if the data is invalid.
 *     SK_SPI bool readStrikeData(const volatile void* memory, size_t memorySize);
 *
 *     // Given a descriptor re-write the Rec mapping the typefaceID from the renderer to the
 *     // corresponding typefaceID on the GPU.
 *     SK_SPI bool translateTypefaceID(SkAutoDescriptor* descriptor) const;
 *
 *     // Testing helpers
 *     sk_sp<SkTypeface> retrieveTypefaceUsingServerIDForTest(SkTypefaceID) const;
 *
 *     // Given a buffer, unflatten into a slug making sure to do the typefaceID translation from
 *     // renderer to GPU. Returns nullptr if there was a problem.
 *     sk_sp<sktext::gpu::Slug> deserializeSlugForTest(const void* data, size_t size) const;
 *
 * private:
 *     std::unique_ptr<SkStrikeClientImpl> fImpl;
 * }
 * ```
 */
public data class SkStrikeClient public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStrikeClientImpl> fImpl
   * ```
   */
  private var fImpl: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkStrikeClient::readStrikeData(const volatile void* memory, size_t memorySize) {
   *     return fImpl->readStrikeData(memory, memorySize);
   * }
   * ```
   */
  public fun readStrikeData(memory: Unit?, memorySize: ULong): Int {
    TODO("Implement readStrikeData")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrikeClient::translateTypefaceID(SkAutoDescriptor* descriptor) const {
   *     return fImpl->translateTypefaceID(descriptor);
   * }
   * ```
   */
  public fun translateTypefaceID(descriptor: SkAutoDescriptor?): Int {
    TODO("Implement translateTypefaceID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkStrikeClient::retrieveTypefaceUsingServerIDForTest(
   *         SkTypefaceID typefaceID) const {
   *     return fImpl->retrieveTypefaceUsingServerID(typefaceID);
   * }
   * ```
   */
  public fun retrieveTypefaceUsingServerIDForTest(typefaceID: SkTypefaceID): Int {
    TODO("Implement retrieveTypefaceUsingServerIDForTest")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sktext::gpu::Slug> SkStrikeClient::deserializeSlugForTest(const void* data,
   *                                                                 size_t size) const {
   *     return sktext::gpu::Slug::Deserialize(data, size, this);
   * }
   * ```
   */
  public fun deserializeSlugForTest(`data`: Unit?, size: ULong): Int {
    TODO("Implement deserializeSlugForTest")
  }

  public abstract class DiscardableHandleManager : SkRefCnt() {
    public abstract fun deleteHandle(param0: SkDiscardableHandleId): Boolean

    public open fun assertHandleValid(param0: SkDiscardableHandleId) {
      TODO("Implement assertHandleValid")
    }

    public abstract fun notifyCacheMiss(type: undefined.CacheMissType, fontSize: Int)

    public open fun notifyReadFailure(`data`: DiscardableHandleManager.ReadFailureData) {
      TODO("Implement notifyReadFailure")
    }

    public data class ReadFailureData public constructor(
      public var memorySize: ULong,
      public var bytesRead: ULong,
      public var typefaceSize: ULong,
      public var strikeCount: ULong,
      public var glyphImagesCount: ULong,
      public var glyphPathsCount: ULong,
    )
  }

  public enum class CacheMissType {
    kFontMetrics,
    kGlyphMetrics,
    kGlyphImage,
    kGlyphPath,
    kGlyphMetricsFallback,
    kGlyphPathFallback,
    kGlyphDrawable,
    kLast,
  }
}
