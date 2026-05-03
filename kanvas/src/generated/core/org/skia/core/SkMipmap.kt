package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkSize
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * class SkMipmap : public SkCachedData {
 * public:
 *     ~SkMipmap() override;
 *     // Allocate and fill-in a mipmap. If computeContents is false, we just allocated
 *     // and compute the sizes/rowbytes, but leave the pixel-data uninitialized.
 *     static SkMipmap* Build(const SkPixmap& src, SkDiscardableFactoryProc,
 *                            bool computeContents = true);
 *
 *     static SkMipmap* Build(const SkBitmap& src, SkDiscardableFactoryProc);
 *
 *     // Determines how many levels a SkMipmap will have without creating that mipmap.
 *     // This does not include the base mipmap level that the user provided when
 *     // creating the SkMipmap.
 *     static int ComputeLevelCount(int baseWidth, int baseHeight);
 *     static int ComputeLevelCount(SkISize s) { return ComputeLevelCount(s.width(), s.height()); }
 *
 *     // Determines the size of a given mipmap level.
 *     // |level| is an index into the generated mipmap levels. It does not include
 *     // the base level. So index 0 represents mipmap level 1.
 *     static SkISize ComputeLevelSize(int baseWidth, int baseHeight, int level);
 *     static SkISize ComputeLevelSize(SkISize s, int level) {
 *         return ComputeLevelSize(s.width(), s.height(), level);
 *     }
 *
 *     // Computes the fractional level based on the scaling in X and Y.
 *     static float ComputeLevel(SkSize scaleSize);
 *
 *     // We use a block of (possibly discardable) memory to hold an array of Level structs, followed
 *     // by the pixel data for each level. On 32-bit platforms, Level would naturally be 4 byte
 *     // aligned, so the pixel data could end up with 4 byte alignment. If the pixel data is F16,
 *     // it must be 8 byte aligned. To ensure this, keep the Level struct 8 byte aligned as well.
 *     struct alignas(8) Level {
 *         SkPixmap    fPixmap;
 *         SkSize      fScale; // < 1.0
 *     };
 *
 *     bool extractLevel(SkSize scale, Level*) const;
 *
 *     // countLevels returns the number of mipmap levels generated (which does not
 *     // include the base mipmap level).
 *     int countLevels() const;
 *
 *     // |index| is an index into the generated mipmap levels. It does not include
 *     // the base level. So index 0 represents mipmap level 1.
 *     bool getLevel(int index, Level*) const;
 *
 *     bool validForRootLevel(const SkImageInfo&) const;
 *
 *     static std::unique_ptr<SkMipmapDownSampler> MakeDownSampler(const SkPixmap&);
 *
 * protected:
 *     void onDataChange(void* oldData, void* newData) override {
 *         fLevels = (Level*)newData; // could be nullptr
 *     }
 *
 * private:
 *     sk_sp<SkColorSpace> fCS;
 *     Level*              fLevels;    // managed by the baseclass, may be null due to onDataChanged.
 *     int                 fCount;
 *
 *     SkMipmap(void* malloc, size_t size);
 *     SkMipmap(size_t size, SkDiscardableMemory* dm);
 *
 *     static size_t AllocLevelsSize(int levelCount, size_t pixelSize);
 * }
 * ```
 */
public open class SkMipmap public constructor(
  malloc: Unit?,
  size: ULong,
) : SkCachedData(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fCS
   * ```
   */
  private var fCS: SkSp<SkColorSpace> = TODO("Initialize fCS")

  /**
   * C++ original:
   * ```cpp
   * Level*              fLevels
   * ```
   */
  private var fLevels: Level? = TODO("Initialize fLevels")

  /**
   * C++ original:
   * ```cpp
   * int                 fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * SkMipmap::SkMipmap(void* malloc, size_t size) : SkCachedData(malloc, size) {}
   * ```
   */
  public constructor(size: ULong, dm: SkDiscardableMemory?) : super(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMipmap::extractLevel(SkSize scaleSize, Level* levelPtr) const {
   *     if (nullptr == fLevels) {
   *         return false;
   *     }
   *
   *     float L = ComputeLevel(scaleSize);
   *     int level = sk_float_round2int(L);
   *     if (level <= 0) {
   *         return false;
   *     }
   *
   *     if (level > fCount) {
   *         level = fCount;
   *     }
   *     if (levelPtr) {
   *         *levelPtr = fLevels[level - 1];
   *         // need to augment with our colorspace
   *         levelPtr->fPixmap.setColorSpace(fCS);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun extractLevel(scale: SkSize, levelPtr: Level?): Boolean {
    TODO("Implement extractLevel")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkMipmap::countLevels() const {
   *     return fCount;
   * }
   * ```
   */
  public fun countLevels(): Int {
    TODO("Implement countLevels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMipmap::getLevel(int index, Level* levelPtr) const {
   *     if (nullptr == fLevels) {
   *         return false;
   *     }
   *     if (index < 0) {
   *         return false;
   *     }
   *     if (index > fCount - 1) {
   *         return false;
   *     }
   *     if (levelPtr) {
   *         *levelPtr = fLevels[index];
   *         // need to augment with our colorspace
   *         levelPtr->fPixmap.setColorSpace(fCS);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun getLevel(index: Int, levelPtr: Level?): Boolean {
    TODO("Implement getLevel")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMipmap::validForRootLevel(const SkImageInfo& root) const {
   *     if (nullptr == fLevels) {
   *         return false;
   *     }
   *
   *     const SkISize dimension = root.dimensions();
   *     if (dimension.width() <= 1 && dimension.height() <= 1) {
   *         return false;
   *     }
   *
   *     if (fLevels[0].fPixmap. width() != std::max(1, dimension. width() >> 1) ||
   *         fLevels[0].fPixmap.height() != std::max(1, dimension.height() >> 1)) {
   *         return false;
   *     }
   *
   *     for (int i = 0; i < this->countLevels(); ++i) {
   *         if (fLevels[i].fPixmap.colorType() != root.colorType() ||
   *             fLevels[i].fPixmap.alphaType() != root.alphaType()) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun validForRootLevel(root: SkImageInfo): Boolean {
    TODO("Implement validForRootLevel")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDataChange(void* oldData, void* newData) override {
   *         fLevels = (Level*)newData; // could be nullptr
   *     }
   * ```
   */
  protected override fun onDataChange(oldData: Unit?, newData: Unit?) {
    TODO("Implement onDataChange")
  }

  public data class Level public constructor(
    public var fPixmap: SkPixmap,
    public var fScale: SkSize,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkMipmap* SkMipmap::Build(const SkPixmap& src, SkDiscardableFactoryProc fact,
     *                           bool computeContents) {
     *     if (src.width() <= 1 && src.height() <= 1) {
     *         return nullptr;
     *     }
     *
     *     const SkColorType ct = src.colorType();
     *     const SkAlphaType at = src.alphaType();
     *
     *     // whip through our loop to compute the exact size needed
     *     size_t size = 0;
     *     int countLevels = ComputeLevelCount(src.width(), src.height());
     *     for (int currentMipLevel = countLevels; currentMipLevel >= 0; currentMipLevel--) {
     *         SkISize mipSize = ComputeLevelSize(src.width(), src.height(), currentMipLevel);
     *         size += SkColorTypeMinRowBytes(ct, mipSize.fWidth) * mipSize.fHeight;
     *     }
     *
     *     size_t storageSize = SkMipmap::AllocLevelsSize(countLevels, size);
     *     if (0 == storageSize) {
     *         return nullptr;
     *     }
     *
     *     SkMipmap* mipmap;
     *     if (fact) {
     *         SkDiscardableMemory* dm = fact(storageSize);
     *         if (nullptr == dm) {
     *             return nullptr;
     *         }
     *         mipmap = new SkMipmap(storageSize, dm);
     *     } else {
     *         void* tmp = sk_malloc_canfail(storageSize);
     *         if (!tmp) {
     *             return nullptr;
     *         }
     *
     *         mipmap = new SkMipmap(tmp, storageSize);
     *     }
     *
     *     // init
     *     mipmap->fCS = sk_ref_sp(src.info().colorSpace());
     *     mipmap->fCount = countLevels;
     *     mipmap->fLevels = (Level*)mipmap->writable_data();
     *     SkASSERT(mipmap->fLevels);
     *
     *     Level* levels = mipmap->fLevels;
     *     uint8_t*    baseAddr = (uint8_t*)&levels[countLevels];
     *     uint8_t*    addr = baseAddr;
     *     int         width = src.width();
     *     int         height = src.height();
     *     uint32_t    rowBytes;
     *     SkPixmap    srcPM(src);
     *
     *     // Depending on architecture and other factors, the pixel data alignment may need to be as
     *     // large as 8 (for F16 pixels). See the comment on SkMipmap::Level.
     *     SkASSERT(SkIsAlign8((uintptr_t)addr));
     *
     *     std::unique_ptr<SkMipmapDownSampler> downsampler;
     *     if (computeContents) {
     *         downsampler = MakeDownSampler(src);
     *         if (!downsampler) {
     *             delete mipmap;
     *             return nullptr;
     *         }
     *     }
     *
     *     for (int i = 0; i < countLevels; ++i) {
     *         width = std::max(1, width >> 1);
     *         height = std::max(1, height >> 1);
     *         rowBytes = SkToU32(SkColorTypeMinRowBytes(ct, width));
     *
     *         // We make the Info w/o any colorspace, since that storage is not under our control, and
     *         // will not be deleted in a controlled fashion. When the caller is given the pixmap for
     *         // a given level, we augment this pixmap with fCS (which we do manage).
     *         new (&levels[i].fPixmap) SkPixmap(SkImageInfo::Make(width, height, ct, at), addr, rowBytes);
     *         levels[i].fScale  = SkSize::Make(SkIntToScalar(width)  / src.width(),
     *                                          SkIntToScalar(height) / src.height());
     *
     *         const SkPixmap& dstPM = levels[i].fPixmap;
     *         if (downsampler) {
     *             downsampler->buildLevel(dstPM, srcPM);
     *         }
     *         srcPM = dstPM;
     *         addr += height * rowBytes;
     *     }
     *     SkASSERT(addr == baseAddr + size);
     *
     *     SkASSERT(mipmap->fLevels);
     *     return mipmap;
     * }
     * ```
     */
    public fun build(
      src: SkPixmap,
      fact: SkDiscardableFactoryProc,
      computeContents: Boolean = true,
    ): SkMipmap {
      TODO("Implement build")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMipmap* SkMipmap::Build(const SkBitmap& src, SkDiscardableFactoryProc fact) {
     *     SkPixmap srcPixmap;
     *     if (!src.peekPixels(&srcPixmap)) {
     *         return nullptr;
     *     }
     *     return Build(srcPixmap, fact);
     * }
     * ```
     */
    public fun build(src: SkBitmap, fact: SkDiscardableFactoryProc): SkMipmap {
      TODO("Implement build")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkMipmap::ComputeLevelCount(int baseWidth, int baseHeight) {
     *     if (baseWidth < 1 || baseHeight < 1) {
     *         return 0;
     *     }
     *
     *     // OpenGL's spec requires that each mipmap level have height/width equal to
     *     // max(1, floor(original_height / 2^i)
     *     // (or original_width) where i is the mipmap level.
     *     // Continue scaling down until both axes are size 1.
     *
     *     const int largestAxis = std::max(baseWidth, baseHeight);
     *     if (largestAxis < 2) {
     *         // SkMipmap::Build requires a minimum size of 2.
     *         return 0;
     *     }
     *     const int leadingZeros = SkCLZ(static_cast<uint32_t>(largestAxis));
     *     // If the value 00011010 has 3 leading 0s then it has 5 significant bits
     *     // (the bits which are not leading zeros)
     *     const int significantBits = (sizeof(uint32_t) * 8) - leadingZeros;
     *     // This is making the assumption that the size of a byte is 8 bits
     *     // and that sizeof(uint32_t)'s implementation-defined behavior is 4.
     *     int mipLevelCount = significantBits;
     *
     *     // SkMipmap does not include the base mip level.
     *     // For example, it contains levels 1-x instead of 0-x.
     *     // This is because the image used to create SkMipmap is the base level.
     *     // So subtract 1 from the mip level count.
     *     if (mipLevelCount > 0) {
     *         --mipLevelCount;
     *     }
     *
     *     return mipLevelCount;
     * }
     * ```
     */
    public fun computeLevelCount(baseWidth: Int, baseHeight: Int): Int {
      TODO("Implement computeLevelCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static int ComputeLevelCount(SkISize s) { return ComputeLevelCount(s.width(), s.height()); }
     * ```
     */
    public fun computeLevelCount(s: SkISize): Int {
      TODO("Implement computeLevelCount")
    }

    /**
     * C++ original:
     * ```cpp
     * SkISize SkMipmap::ComputeLevelSize(int baseWidth, int baseHeight, int level) {
     *     if (baseWidth < 1 || baseHeight < 1) {
     *         return SkISize::Make(0, 0);
     *     }
     *
     *     int maxLevelCount = ComputeLevelCount(baseWidth, baseHeight);
     *     if (level >= maxLevelCount || level < 0) {
     *         return SkISize::Make(0, 0);
     *     }
     *     // OpenGL's spec requires that each mipmap level have height/width equal to
     *     // max(1, floor(original_height / 2^i)
     *     // (or original_width) where i is the mipmap level.
     *
     *     // SkMipmap does not include the base mip level.
     *     // For example, it contains levels 1-x instead of 0-x.
     *     // This is because the image used to create SkMipmap is the base level.
     *     // So subtract 1 from the mip level to get the index stored by SkMipmap.
     *     int width = std::max(1, baseWidth >> (level + 1));
     *     int height = std::max(1, baseHeight >> (level + 1));
     *
     *     return SkISize::Make(width, height);
     * }
     * ```
     */
    public fun computeLevelSize(
      baseWidth: Int,
      baseHeight: Int,
      level: Int,
    ): SkISize {
      TODO("Implement computeLevelSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkISize ComputeLevelSize(SkISize s, int level) {
     *         return ComputeLevelSize(s.width(), s.height(), level);
     *     }
     * ```
     */
    public fun computeLevelSize(s: SkISize, level: Int): SkISize {
      TODO("Implement computeLevelSize")
    }

    /**
     * C++ original:
     * ```cpp
     * float SkMipmap::ComputeLevel(SkSize scaleSize) {
     *     SkASSERT(scaleSize.width() >= 0 && scaleSize.height() >= 0);
     *
     * #ifndef SK_SUPPORT_LEGACY_ANISOTROPIC_MIPMAP_SCALE
     *     // Use the smallest scale to match the GPU impl.
     *     const float scale = std::min(scaleSize.width(), scaleSize.height());
     * #else
     *     // Ideally we'd pick the smaller scale, to match Ganesh.  But ignoring one of the
     *     // scales can produce some atrocious results, so for now we use the geometric mean.
     *     // (https://bugs.chromium.org/p/skia/issues/detail?id=4863)
     *     const float scale = std::sqrt(scaleSize.width() * scaleSize.height());
     * #endif
     *
     *     if (scale >= SK_Scalar1 || scale <= 0 || !SkIsFinite(scale)) {
     *         return -1;
     *     }
     *
     *     // The -0.5 bias here is to emulate GPU's sharpen mipmap option.
     *     float L = std::max(-SkScalarLog2(scale) - 0.5f, 0.f);
     *     if (!SkIsFinite(L)) {
     *         return -1;
     *     }
     *     return L;
     * }
     * ```
     */
    public fun computeLevel(scaleSize: SkSize): Float {
      TODO("Implement computeLevel")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkMipmapDownSampler> SkMipmap::MakeDownSampler(const SkPixmap& root) {
     *     FilterProc* proc_1_2 = nullptr;
     *     FilterProc* proc_1_3 = nullptr;
     *     FilterProc* proc_2_1 = nullptr;
     *     FilterProc* proc_2_2 = nullptr;
     *     FilterProc* proc_2_3 = nullptr;
     *     FilterProc* proc_3_1 = nullptr;
     *     FilterProc* proc_3_2 = nullptr;
     *     FilterProc* proc_3_3 = nullptr;
     *
     *     switch (root.colorType()) {
     *         case kRGBA_8888_SkColorType:
     *         case kBGRA_8888_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_8888>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_8888>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_8888>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_8888>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_8888>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_8888>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_8888>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_8888>;
     *             break;
     *         case kRGB_565_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_565>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_565>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_565>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_565>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_565>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_565>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_565>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_565>;
     *             break;
     *         case kARGB_4444_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_4444>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_4444>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_4444>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_4444>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_4444>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_4444>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_4444>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_4444>;
     *             break;
     *         case kAlpha_8_SkColorType:
     *         case kGray_8_SkColorType:
     *         case kR8_unorm_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_8>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_8>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_8>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_8>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_8>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_8>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_8>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_8>;
     *             break;
     *         case kRGBA_F16Norm_SkColorType:
     *         case kRGBA_F16_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_RGBA_F16>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_RGBA_F16>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_RGBA_F16>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_RGBA_F16>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_RGBA_F16>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_RGBA_F16>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_RGBA_F16>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_RGBA_F16>;
     *             break;
     *         case kR8G8_unorm_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_88>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_88>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_88>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_88>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_88>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_88>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_88>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_88>;
     *             break;
     *         case kR16G16_unorm_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_1616>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_1616>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_1616>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_1616>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_1616>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_1616>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_1616>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_1616>;
     *             break;
     *         case kA16_unorm_SkColorType:
     *         case kR16_unorm_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_16>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_16>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_16>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_16>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_16>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_16>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_16>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_16>;
     *             break;
     *         case kRGBA_1010102_SkColorType:
     *         case kBGRA_1010102_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_1010102>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_1010102>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_1010102>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_1010102>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_1010102>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_1010102>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_1010102>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_1010102>;
     *             break;
     *         case kA16_float_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_Alpha_F16>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_Alpha_F16>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_Alpha_F16>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_Alpha_F16>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_Alpha_F16>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_Alpha_F16>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_Alpha_F16>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_Alpha_F16>;
     *             break;
     *         case kR16G16_float_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_F16F16>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_F16F16>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_F16F16>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_F16F16>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_F16F16>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_F16F16>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_F16F16>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_F16F16>;
     *             break;
     *         case kR16G16B16A16_unorm_SkColorType:
     *             proc_1_2 = downsample_1_2<ColorTypeFilter_16161616>;
     *             proc_1_3 = downsample_1_3<ColorTypeFilter_16161616>;
     *             proc_2_1 = downsample_2_1<ColorTypeFilter_16161616>;
     *             proc_2_2 = downsample_2_2<ColorTypeFilter_16161616>;
     *             proc_2_3 = downsample_2_3<ColorTypeFilter_16161616>;
     *             proc_3_1 = downsample_3_1<ColorTypeFilter_16161616>;
     *             proc_3_2 = downsample_3_2<ColorTypeFilter_16161616>;
     *             proc_3_3 = downsample_3_3<ColorTypeFilter_16161616>;
     *             break;
     *
     *         case kUnknown_SkColorType:
     *         case kRGB_888x_SkColorType:     // TODO: use 8888?
     *         case kRGB_101010x_SkColorType:  // TODO: use 1010102?
     *         case kBGR_101010x_SkColorType:  // TODO: use 1010102?
     *         case kBGR_101010x_XR_SkColorType:  // TODO: use 1010102?
     *         case kRGB_F16F16F16x_SkColorType:  // TODO: use F16?
     *         case kBGRA_10101010_XR_SkColorType:
     *         case kRGBA_10x6_SkColorType:
     *         case kRGBA_F32_SkColorType:
     *             return nullptr;
     *
     *         case kSRGBA_8888_SkColorType:  // TODO: needs careful handling
     *             return nullptr;
     *     }
     *
     *     auto sampler = std::make_unique<HQDownSampler>();
     *     sampler->proc_1_2 = proc_1_2;
     *     sampler->proc_1_3 = proc_1_3;
     *     sampler->proc_2_1 = proc_2_1;
     *     sampler->proc_2_2 = proc_2_2;
     *     sampler->proc_2_3 = proc_2_3;
     *     sampler->proc_3_1 = proc_3_1;
     *     sampler->proc_3_2 = proc_3_2;
     *     sampler->proc_3_3 = proc_3_3;
     *     return sampler;
     * }
     * ```
     */
    public fun makeDownSampler(root: SkPixmap): Int {
      TODO("Implement makeDownSampler")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkMipmap::AllocLevelsSize(int levelCount, size_t pixelSize) {
     *     if (levelCount < 0) {
     *         return 0;
     *     }
     *     int64_t size = sk_64_mul(levelCount + 1, sizeof(Level)) + pixelSize;
     *     if (!SkTFitsIn<int32_t>(size)) {
     *         return 0;
     *     }
     *     return SkTo<int32_t>(size);
     * }
     * ```
     */
    private fun allocLevelsSize(levelCount: Int, pixelSize: ULong): ULong {
      TODO("Implement allocLevelsSize")
    }
  }
}
