package org.skia.core

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API SkGraphics {
 * public:
 *     /**
 *      *  Call this at process initialization time if your environment does not
 *      *  permit static global initializers that execute code.
 *      *  Init() is thread-safe and idempotent.
 *      */
 *     static void Init();
 *
 *     /**
 *      *  Return the max number of bytes that should be used by the font cache.
 *      *  If the cache needs to allocate more, it will purge previous entries.
 *      *  This max can be changed by calling SetFontCacheLimit().
 *      */
 *     static size_t GetFontCacheLimit();
 *
 *     /**
 *      *  Specify the max number of bytes that should be used by the font cache.
 *      *  If the cache needs to allocate more, it will purge previous entries.
 *      *
 *      *  This function returns the previous setting, as if GetFontCacheLimit()
 *      *  had be called before the new limit was set.
 *      */
 *     static size_t SetFontCacheLimit(size_t bytes);
 *
 *     /**
 *      *  Return the number of bytes currently used by the font cache.
 *      */
 *     static size_t GetFontCacheUsed();
 *
 *     /**
 *      *  Return the number of entries in the font cache.
 *      *  A cache "entry" is associated with each typeface + pointSize + matrix.
 *      */
 *     static int GetFontCacheCountUsed();
 *
 *     /**
 *      *  Return the current limit to the number of entries in the font cache.
 *      *  A cache "entry" is associated with each typeface + pointSize + matrix.
 *      */
 *     static int GetFontCacheCountLimit();
 *
 *     /**
 *      *  Set the limit to the number of entries in the font cache, and return
 *      *  the previous value. If this new value is lower than the previous,
 *      *  it will automatically try to purge entries to meet the new limit.
 *      */
 *     static int SetFontCacheCountLimit(int count);
 *
 *     /**
 *      *  Return the current limit to the number of entries in the typeface cache.
 *      *  A cache "entry" is associated with each typeface.
 *      */
 *     static int GetTypefaceCacheCountLimit();
 *
 *     /**
 *      *  Set the limit to the number of entries in the typeface cache, and return
 *      *  the previous value. Changes to this only take effect the next time
 *      *  each cache object is modified.
 *      */
 *     static int SetTypefaceCacheCountLimit(int count);
 *
 *     /**
 *      *  For debugging purposes, this will attempt to purge the font cache. It
 *      *  does not change the limit, but will cause subsequent font measures and
 *      *  draws to be recreated, since they will no longer be in the cache.
 *      */
 *     static void PurgeFontCache();
 *
 *     /**
 *      *  If the strike cache is above the cache limit, attempt to purge strikes
 *      *  with pinners. This should be called after clients release locks on
 *      *  pinned strikes.
 *      */
 *     static void PurgePinnedFontCache();
 *
 *     /**
 *      *  This function returns the memory used for temporary images and other resources.
 *      */
 *     static size_t GetResourceCacheTotalBytesUsed();
 *
 *     /**
 *      *  These functions get/set the memory usage limit for the resource cache, used for temporary
 *      *  bitmaps and other resources. Entries are purged from the cache when the memory useage
 *      *  exceeds this limit.
 *      */
 *     static size_t GetResourceCacheTotalByteLimit();
 *     static size_t SetResourceCacheTotalByteLimit(size_t newLimit);
 *
 *     /**
 *      *  For debugging purposes, this will attempt to purge the resource cache. It
 *      *  does not change the limit.
 *      */
 *     static void PurgeResourceCache();
 *
 *     /**
 *      *  When the cachable entry is very lage (e.g. a large scaled bitmap), adding it to the cache
 *      *  can cause most/all of the existing entries to be purged. To avoid the, the client can set
 *      *  a limit for a single allocation. If a cacheable entry would have been cached, but its size
 *      *  exceeds this limit, then we do not attempt to cache it at all.
 *      *
 *      *  Zero is the default value, meaning we always attempt to cache entries.
 *      */
 *     static size_t GetResourceCacheSingleAllocationByteLimit();
 *     static size_t SetResourceCacheSingleAllocationByteLimit(size_t newLimit);
 *
 *     /**
 *      *  Dumps memory usage of caches using the SkTraceMemoryDump interface. See SkTraceMemoryDump
 *      *  for usage of this method.
 *      */
 *     static void DumpMemoryStatistics(SkTraceMemoryDump* dump);
 *
 *     /**
 *      *  Free as much globally cached memory as possible. This will purge all private caches in Skia,
 *      *  including font and image caches.
 *      *
 *      *  If there are caches associated with GPU context, those will not be affected by this call.
 *      */
 *     static void PurgeAllCaches();
 *
 * #if defined(SK_DISABLE_LEGACY_NONCONST_ENCODED_IMAGE_DATA)
 *     using ImageGeneratorFromEncodedDataFactory =
 *             std::unique_ptr<SkImageGenerator> (*)(sk_sp<const SkData>);
 * #else
 *     using ImageGeneratorFromEncodedDataFactory =
 *             std::unique_ptr<SkImageGenerator> (*)(sk_sp<SkData>);
 * #endif
 *
 *     /**
 *      *  To instantiate images from encoded data, first looks at this runtime function-ptr. If it
 *      *  exists, it is called to create an SkImageGenerator from SkData. If there is no function-ptr
 *      *  or there is, but it returns NULL, then skia will call its internal default implementation.
 *      *
 *      *  Returns the previous factory (which could be NULL).
 *      */
 *     static ImageGeneratorFromEncodedDataFactory
 *                     SetImageGeneratorFromEncodedDataFactory(ImageGeneratorFromEncodedDataFactory);
 *
 *     /**
 *      *  To draw OpenType SVG data, Skia will look at this runtime function pointer. If this function
 *      *  pointer is set, the SkTypeface implementations which support OpenType SVG will call this
 *      *  function to create an SkOpenTypeSVGDecoder to decode the OpenType SVG and draw it as needed.
 *      *  If this function is not set, the SkTypeface implementations will generally not support
 *      *  OpenType SVG and attempt to use other glyph representations if available.
 *      */
 *     using OpenTypeSVGDecoderFactory =
 *             std::unique_ptr<SkOpenTypeSVGDecoder> (*)(const uint8_t* svg, size_t length);
 *     static OpenTypeSVGDecoderFactory SetOpenTypeSVGDecoderFactory(OpenTypeSVGDecoderFactory);
 *     static OpenTypeSVGDecoderFactory GetOpenTypeSVGDecoderFactory();
 * }
 * ```
 */
public open class SkGraphics {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::Init() {
     *     // SkGraphics::Init() must be thread-safe and idempotent.
     *     SkCpu::CacheRuntimeFeatures();
     *     SkOpts::Init();
     *     SkOpts::Init_BitmapProcState();
     *     SkOpts::Init_BlitMask();
     *     SkOpts::Init_BlitRow();
     *     SkOpts::Init_Memset();
     *     SkOpts::Init_Swizzler();
     * }
     * ```
     */
    public fun `init`() {
      TODO("Implement init")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::GetFontCacheLimit() {
     *     return SkStrikeCache::GlobalStrikeCache()->getCacheSizeLimit();
     * }
     * ```
     */
    public fun getFontCacheLimit(): ULong {
      TODO("Implement getFontCacheLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::SetFontCacheLimit(size_t bytes) {
     *     return SkStrikeCache::GlobalStrikeCache()->setCacheSizeLimit(bytes);
     * }
     * ```
     */
    public fun setFontCacheLimit(bytes: ULong): ULong {
      TODO("Implement setFontCacheLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::GetFontCacheUsed() {
     *     return SkStrikeCache::GlobalStrikeCache()->getTotalMemoryUsed();
     * }
     * ```
     */
    public fun getFontCacheUsed(): ULong {
      TODO("Implement getFontCacheUsed")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkGraphics::GetFontCacheCountUsed() {
     *     return SkStrikeCache::GlobalStrikeCache()->getCacheCountUsed();
     * }
     * ```
     */
    public fun getFontCacheCountUsed(): Int {
      TODO("Implement getFontCacheCountUsed")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkGraphics::GetFontCacheCountLimit() {
     *     return SkStrikeCache::GlobalStrikeCache()->getCacheCountLimit();
     * }
     * ```
     */
    public fun getFontCacheCountLimit(): Int {
      TODO("Implement getFontCacheCountLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkGraphics::SetFontCacheCountLimit(int count) {
     *     return SkStrikeCache::GlobalStrikeCache()->setCacheCountLimit(count);
     * }
     * ```
     */
    public fun setFontCacheCountLimit(count: Int): Int {
      TODO("Implement setFontCacheCountLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkGraphics::GetTypefaceCacheCountLimit() {
     *     return gTypefaceCacheCountLimit;
     * }
     * ```
     */
    public fun getTypefaceCacheCountLimit(): Int {
      TODO("Implement getTypefaceCacheCountLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkGraphics::SetTypefaceCacheCountLimit(int count) {
     *     const int prev = gTypefaceCacheCountLimit;
     *     gTypefaceCacheCountLimit = count;
     *     return prev;
     * }
     * ```
     */
    public fun setTypefaceCacheCountLimit(count: Int): Int {
      TODO("Implement setTypefaceCacheCountLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::PurgeFontCache() {
     *     SkStrikeCache::GlobalStrikeCache()->purgeAll();
     *     SkTypefaceCache::PurgeAll();
     * }
     * ```
     */
    public fun purgeFontCache() {
      TODO("Implement purgeFontCache")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::PurgePinnedFontCache() {
     *     SkStrikeCache::GlobalStrikeCache()->purgePinned();
     * }
     * ```
     */
    public fun purgePinnedFontCache() {
      TODO("Implement purgePinnedFontCache")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::GetResourceCacheTotalBytesUsed() { return SkResourceCache::GetTotalBytesUsed(); }
     * ```
     */
    public fun getResourceCacheTotalBytesUsed(): ULong {
      TODO("Implement getResourceCacheTotalBytesUsed")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::GetResourceCacheTotalByteLimit() { return SkResourceCache::GetTotalByteLimit(); }
     * ```
     */
    public fun getResourceCacheTotalByteLimit(): ULong {
      TODO("Implement getResourceCacheTotalByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::SetResourceCacheTotalByteLimit(size_t newLimit) {
     *     return SkResourceCache::SetTotalByteLimit(newLimit);
     * }
     * ```
     */
    public fun setResourceCacheTotalByteLimit(newLimit: ULong): ULong {
      TODO("Implement setResourceCacheTotalByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::PurgeResourceCache() {
     *     SkImageFilter_Base::PurgeCache();
     *     return SkResourceCache::PurgeAll();
     * }
     * ```
     */
    public fun purgeResourceCache() {
      TODO("Implement purgeResourceCache")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::GetResourceCacheSingleAllocationByteLimit() {
     *     return SkResourceCache::GetSingleAllocationByteLimit();
     * }
     * ```
     */
    public fun getResourceCacheSingleAllocationByteLimit(): ULong {
      TODO("Implement getResourceCacheSingleAllocationByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkGraphics::SetResourceCacheSingleAllocationByteLimit(size_t newLimit) {
     *     return SkResourceCache::SetSingleAllocationByteLimit(newLimit);
     * }
     * ```
     */
    public fun setResourceCacheSingleAllocationByteLimit(newLimit: ULong): ULong {
      TODO("Implement setResourceCacheSingleAllocationByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::DumpMemoryStatistics(SkTraceMemoryDump* dump) {
     *   SkResourceCache::DumpMemoryStatistics(dump);
     *   SkStrikeCache::DumpMemoryStatistics(dump);
     * }
     * ```
     */
    public fun dumpMemoryStatistics(dump: SkTraceMemoryDump?) {
      TODO("Implement dumpMemoryStatistics")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGraphics::PurgeAllCaches() {
     *     SkGraphics::PurgeFontCache();
     *     SkGraphics::PurgeResourceCache();
     *     SkImageFilter_Base::PurgeCache();
     * }
     * ```
     */
    public fun purgeAllCaches() {
      TODO("Implement purgeAllCaches")
    }

    /**
     * C++ original:
     * ```cpp
     * SkGraphics::ImageGeneratorFromEncodedDataFactory
     * SkGraphics::SetImageGeneratorFromEncodedDataFactory(ImageGeneratorFromEncodedDataFactory factory)
     * {
     *     ImageGeneratorFromEncodedDataFactory prev = gFactory;
     *     gFactory = factory;
     *     return prev;
     * }
     * ```
     */
    public fun setImageGeneratorFromEncodedDataFactory(factory: SkGraphicsImageGeneratorFromEncodedDataFactory): SkGraphicsImageGeneratorFromEncodedDataFactory {
      TODO("Implement setImageGeneratorFromEncodedDataFactory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkGraphics::OpenTypeSVGDecoderFactory
     * SkGraphics::SetOpenTypeSVGDecoderFactory(OpenTypeSVGDecoderFactory svgDecoderFactory) {
     *     OpenTypeSVGDecoderFactory old(gSVGDecoderFactory);
     *     gSVGDecoderFactory = svgDecoderFactory;
     *     return old;
     * }
     * ```
     */
    public fun setOpenTypeSVGDecoderFactory(svgDecoderFactory: SkGraphicsOpenTypeSVGDecoderFactory): SkGraphicsOpenTypeSVGDecoderFactory {
      TODO("Implement setOpenTypeSVGDecoderFactory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkGraphics::OpenTypeSVGDecoderFactory SkGraphics::GetOpenTypeSVGDecoderFactory() {
     *     return gSVGDecoderFactory;
     * }
     * ```
     */
    public fun getOpenTypeSVGDecoderFactory(): SkGraphicsOpenTypeSVGDecoderFactory {
      TODO("Implement getOpenTypeSVGDecoderFactory")
    }
  }
}
