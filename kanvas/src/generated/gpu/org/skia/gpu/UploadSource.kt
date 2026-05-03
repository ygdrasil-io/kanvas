package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkTextureCompressionType
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class UploadSource {
 * public:
 *     static UploadSource Make(const Caps*,
 *                              const TextureProxy& textureProxy,
 *                              const SkColorInfo& srcColorInfo,
 *                              const SkColorInfo& dstColorInfo,
 *                              SkSpan<const MipLevel> levels,
 *                              const SkIRect& dstRect);
 *     static UploadSource MakeCompressed(const Caps*,
 *                                        const TextureProxy& textureProxy,
 *                                        const void* data,
 *                                        size_t dataSize);
 *
 *     UploadSource(UploadSource&&);
 *     UploadSource& operator=(UploadSource&&);
 *     ~UploadSource();
 *
 *     bool isValid() const { return !fLevels.empty(); }
 *
 *     SkSpan<const MipLevel> levels() const { return fLevels; }
 *     bool canUploadOnHost() const { return fCanUploadOnHost; }
 *     bool isRGB888Format() const { return fIsRGB888Format; }
 *     SkTextureCompressionType compression() const { return fCompression; }
 *     size_t bytesPerPixel() const { return fBytesPerPixel; }
 *
 * private:
 *     static UploadSource Invalid() { return {}; }
 *
 *     UploadSource();
 *
 *     skia_private::STArray<16, MipLevel> fLevels;
 *
 *     // Whether the texture supports uploads directly from host memory.
 *     bool fCanUploadOnHost = false;
 *     // Whether the texture is RGB888, which is typically emulated by RGBA8888.
 *     bool fIsRGB888Format = false;
 *     // Compression type, if any.
 *     SkTextureCompressionType fCompression;
 *     // Bytes per pixel or block (if compressed)
 *     size_t fBytesPerPixel = 0;
 * }
 * ```
 */
public data class UploadSource public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<16, MipLevel> fLevels
   * ```
   */
  private var fLevels: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fCanUploadOnHost = false
   * ```
   */
  private var fCanUploadOnHost: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fIsRGB888Format = false
   * ```
   */
  private var fIsRGB888Format: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkTextureCompressionType fCompression
   * ```
   */
  private var fCompression: SkTextureCompressionType,
  /**
   * C++ original:
   * ```cpp
   * size_t fBytesPerPixel
   * ```
   */
  private var fBytesPerPixel: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * UploadSource& UploadSource::operator=(UploadSource&&)
   * ```
   */
  public fun assign(param0: UploadSource) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return !fLevels.empty(); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const MipLevel> levels() const { return fLevels; }
   * ```
   */
  public fun levels(): Int {
    TODO("Implement levels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canUploadOnHost() const { return fCanUploadOnHost; }
   * ```
   */
  public fun canUploadOnHost(): Boolean {
    TODO("Implement canUploadOnHost")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRGB888Format() const { return fIsRGB888Format; }
   * ```
   */
  public fun isRGB888Format(): Boolean {
    TODO("Implement isRGB888Format")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTextureCompressionType compression() const { return fCompression; }
   * ```
   */
  public fun compression(): SkTextureCompressionType {
    TODO("Implement compression")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesPerPixel() const { return fBytesPerPixel; }
   * ```
   */
  public fun bytesPerPixel(): Int {
    TODO("Implement bytesPerPixel")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * UploadSource UploadSource::Make(const Caps* caps,
     *                                 const TextureProxy& textureProxy,
     *                                 const SkColorInfo& srcColorInfo,
     *                                 const SkColorInfo& dstColorInfo,
     *                                 SkSpan<const MipLevel> levels,
     *                                 const SkIRect& dstRect) {
     *     const TextureInfo& texInfo = textureProxy.textureInfo();
     *
     *     SkASSERT(caps->isTexturable(texInfo));
     *     SkASSERT(caps->areColorTypeAndTextureInfoCompatible(dstColorInfo.colorType(), texInfo));
     *
     *     unsigned int mipLevelCount = levels.size();
     *     // The assumption is either that we have no mipmaps, or that our rect is the entire texture
     *     SkASSERT(mipLevelCount == 1 || dstRect == SkIRect::MakeSize(textureProxy.dimensions()));
     *
     *     // We assume that if the texture has mip levels, we either upload to all the levels or just the
     *     // first.
     * #ifdef SK_DEBUG
     *     unsigned int numExpectedLevels = 1;
     *     if (texInfo.mipmapped() == Mipmapped::kYes) {
     *         numExpectedLevels = SkMipmap::ComputeLevelCount(textureProxy.dimensions()) + 1;
     *     }
     *     SkASSERT(mipLevelCount == 1 || mipLevelCount == numExpectedLevels);
     * #endif
     *
     *     if (dstRect.isEmpty()) {
     *         return Invalid();
     *     }
     *
     *     UploadSource source;
     *     for (unsigned int i = 0; i < mipLevelCount; ++i) {
     *         // We do not allow any gaps in the mip data
     *         if (!levels[i].fPixels) {
     *             return Invalid();
     *         }
     *         source.fLevels.push_back(levels[i]);
     *     }
     *
     *     SkColorType supportedColorType;
     *     bool isRGB888Format;
     *     std::tie(supportedColorType, isRGB888Format) = caps->supportedWritePixelsColorType(
     *             dstColorInfo.colorType(), texInfo, srcColorInfo.colorType());
     *     if (supportedColorType == kUnknown_SkColorType) {
     *         return Invalid();
     *     }
     *
     *     SkASSERT(!source.isRGB888Format() || (supportedColorType == kRGB_888x_SkColorType &&
     *                                           dstColorInfo.colorType() == kRGB_888x_SkColorType));
     *
     *     constexpr size_t kRGB888Bytes = 3;
     *
     *     source.fIsRGB888Format = isRGB888Format;
     *     source.fBytesPerPixel =
     *             isRGB888Format ? kRGB888Bytes : SkColorTypeBytesPerPixel(supportedColorType);
     *     source.fCanUploadOnHost =
     *             textureProxy.isInstantiated() ? textureProxy.texture()->canUploadOnHost(source) : false;
     *
     *     return source;
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      textureProxy: TextureProxy,
      srcColorInfo: SkColorInfo,
      dstColorInfo: SkColorInfo,
      levels: SkSpan<MipLevel>,
      dstRect: SkIRect,
    ): UploadSource {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * UploadSource UploadSource::MakeCompressed(const Caps* caps,
     *                                           const TextureProxy& textureProxy,
     *                                           const void* data,
     *                                           size_t dataSize) {
     *     if (!data) {
     *         return Invalid();  // no data to upload
     *     }
     *
     *     const TextureInfo& texInfo = textureProxy.textureInfo();
     *     SkASSERT(caps->isTexturable(texInfo));
     *
     *     SkTextureCompressionType compression =
     *             TextureFormatCompressionType(TextureInfoPriv::ViewFormat(texInfo));
     *     if (compression == SkTextureCompressionType::kNone) {
     *         return Invalid();
     *     }
     *
     *     // Create a transfer buffer and fill with data.
     *     const SkISize dimensions = textureProxy.dimensions();
     *     skia_private::STArray<16, size_t> srcMipOffsets;
     *     SkDEBUGCODE(size_t computedSize =) SkCompressedDataSize(
     *             compression, dimensions, &srcMipOffsets, texInfo.mipmapped() == Mipmapped::kYes);
     *     SkASSERT(computedSize == dataSize);
     *
     *     const unsigned int mipLevelCount = srcMipOffsets.size();
     *
     *     UploadSource source;
     *     source.fLevels.resize(mipLevelCount);
     *     for (unsigned int i = 0; i < mipLevelCount; ++i) {
     *         source.fLevels[i].fPixels = SkTAddOffset<const void>(data, srcMipOffsets[i]);
     *         source.fLevels[i].fRowBytes = 0;  // Tightly packed
     *     }
     *
     *     source.fCompression = compression;
     *     source.fBytesPerPixel = SkCompressedBlockSize(compression);
     *     source.fCanUploadOnHost =
     *             textureProxy.isInstantiated() ? textureProxy.texture()->canUploadOnHost(source) : false;
     *
     *     return source;
     * }
     * ```
     */
    public fun makeCompressed(
      caps: Caps?,
      textureProxy: TextureProxy,
      `data`: Unit?,
      dataSize: ULong,
    ): UploadSource {
      TODO("Implement makeCompressed")
    }

    /**
     * C++ original:
     * ```cpp
     * static UploadSource Invalid() { return {}; }
     * ```
     */
    private fun invalid(): UploadSource {
      TODO("Implement invalid")
    }
  }
}
