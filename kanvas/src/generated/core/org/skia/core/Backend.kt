package org.skia.core

import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Backend : public SkRefCnt {
 * public:
 *     ~Backend() override;
 *
 *     // For creating offscreen intermediate renderable images
 *     virtual sk_sp<SkDevice> makeDevice(SkISize size,
 *                                        sk_sp<SkColorSpace>,
 *                                        const SkSurfaceProps* props=nullptr) const = 0;
 *
 *     // For input images to be processed by image filters
 *     virtual sk_sp<SkSpecialImage> makeImage(const SkIRect& subset, sk_sp<SkImage> image) const = 0;
 *
 *     // For internal data to be accessed by filter implementations
 *     virtual sk_sp<SkImage> getCachedBitmap(const SkBitmap& data) const = 0;
 *
 *     // TODO: Once all Backends provide a blur engine, maybe just have Backend extend it.
 *     virtual const SkBlurEngine* getBlurEngine() const = 0;
 *
 *     // Properties controlling the pixel data for offscreen surfaces rendered to during filtering.
 *     const SkSurfaceProps& surfaceProps() const { return fSurfaceProps; }
 *     SkColorType colorType() const { return fColorType; }
 *
 *     SkImageFilterCache* cache() const { return fCache.get(); }
 *
 * protected:
 *     Backend(sk_sp<SkImageFilterCache> cache,
 *             const SkSurfaceProps& surfaceProps,
 *             const SkColorType colorType);
 *
 * private:
 *     sk_sp<SkImageFilterCache> fCache;
 *     SkSurfaceProps fSurfaceProps;
 *     SkColorType fColorType;
 * }
 * ```
 */
public abstract class Backend public constructor(
  cache: SkSp<SkImageFilterCache>,
  surfaceProps: SkSurfaceProps,
  colorType: SkColorType,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilterCache> fCache
   * ```
   */
  private var fCache: SkSp<SkImageFilterCache> = TODO("Initialize fCache")

  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps fSurfaceProps
   * ```
   */
  private var fSurfaceProps: SkSurfaceProps = TODO("Initialize fSurfaceProps")

  /**
   * C++ original:
   * ```cpp
   * SkColorType fColorType
   * ```
   */
  private var fColorType: SkColorType = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkDevice> makeDevice(SkISize size,
   *                                        sk_sp<SkColorSpace>,
   *                                        const SkSurfaceProps* props=nullptr) const = 0
   * ```
   */
  public abstract fun makeDevice(
    size: SkISize,
    param1: SkSp<SkColorSpace>,
    props: SkSurfaceProps? = TODO(),
  ): SkSp<SkDevice>

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkSpecialImage> makeImage(const SkIRect& subset, sk_sp<SkImage> image) const = 0
   * ```
   */
  public abstract fun makeImage(subset: SkIRect, image: SkSp<SkImage>): SkSp<SkSpecialImage>

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> getCachedBitmap(const SkBitmap& data) const = 0
   * ```
   */
  public abstract fun getCachedBitmap(`data`: SkBitmap): SkSp<SkImage>

  /**
   * C++ original:
   * ```cpp
   * virtual const SkBlurEngine* getBlurEngine() const = 0
   * ```
   */
  public abstract fun getBlurEngine(): SkBlurEngine

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps& surfaceProps() const { return fSurfaceProps; }
   * ```
   */
  public fun surfaceProps(): SkSurfaceProps {
    TODO("Implement surfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fColorType; }
   * ```
   */
  public fun colorType(): SkColorType {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageFilterCache* cache() const { return fCache.get(); }
   * ```
   */
  public fun cache(): SkImageFilterCache {
    TODO("Implement cache")
  }
}
