package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.codec.SkEncodedOrigin
import org.skia.core.SkYUVAInfo
import org.skia.core.SkYUVAPixmaps
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkYUVColorSpace
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class YUVAPlanarConfig {
 * public:
 *     YUVAPlanarConfig(YUVFormat format, bool opaque, SkEncodedOrigin origin) : fOrigin(origin) {
 *         switch (format) {
 *             case kP016_YUVFormat:
 *             case kP010_YUVFormat:
 *             case kP016F_YUVFormat:
 *             case kNV12_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_UV;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_UV_A;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 }
 *                 break;
 *             case kY416_YUVFormat:
 *             case kY410_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kUYV;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k444;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kUYVA;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k444;
 *                 }
 *                 break;
 *             case kAYUV_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kYUV;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k444;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kYUVA;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k444;
 *                 }
 *                 break;
 *             case kNV21_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_VU;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_VU_A;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 }
 *                 break;
 *             case kI420_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_U_V;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_U_V_A;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 }
 *                 break;
 *             case kYV12_YUVFormat:
 *                 if (opaque) {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_V_U;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 } else {
 *                     fPlaneConfig = SkYUVAInfo::PlaneConfig::kY_V_U_A;
 *                     fSubsampling = SkYUVAInfo::Subsampling::k420;
 *                 }
 *                 break;
 *         }
 *     }
 *
 *     int numPlanes() const { return SkYUVAInfo::NumPlanes(fPlaneConfig); }
 *
 *     SkYUVAPixmaps makeYUVAPixmaps(SkISize dimensions,
 *                                   SkYUVColorSpace yuvColorSpace,
 *                                   const SkBitmap bitmaps[],
 *                                   int numBitmaps) const;
 *
 * private:
 *     SkYUVAInfo::PlaneConfig fPlaneConfig;
 *     SkYUVAInfo::Subsampling fSubsampling;
 *     SkEncodedOrigin         fOrigin;
 * }
 * ```
 */
public data class YUVAPlanarConfig public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo::PlaneConfig fPlaneConfig
   * ```
   */
  private var fPlaneConfig: SkYUVAInfo.PlaneConfig,
  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo::Subsampling fSubsampling
   * ```
   */
  private var fSubsampling: SkYUVAInfo.Subsampling,
  /**
   * C++ original:
   * ```cpp
   * SkEncodedOrigin         fOrigin
   * ```
   */
  private var fOrigin: SkEncodedOrigin,
) {
  /**
   * C++ original:
   * ```cpp
   * int numPlanes() const { return SkYUVAInfo::NumPlanes(fPlaneConfig); }
   * ```
   */
  public fun numPlanes(): Int {
    TODO("Implement numPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps YUVAPlanarConfig::makeYUVAPixmaps(SkISize dimensions,
   *                                                 SkYUVColorSpace yuvColorSpace,
   *                                                 const SkBitmap bitmaps[],
   *                                                 int numBitmaps) const {
   *     SkYUVAInfo info(dimensions, fPlaneConfig, fSubsampling, yuvColorSpace, fOrigin);
   *     SkPixmap pmaps[SkYUVAInfo::kMaxPlanes];
   *     int n = info.numPlanes();
   *     if (numBitmaps < n) {
   *         return {};
   *     }
   *     for (int i = 0; i < n; ++i) {
   *         pmaps[i] = bitmaps[i].pixmap();
   *     }
   *     return SkYUVAPixmaps::FromExternalPixmaps(info, pmaps);
   * }
   * ```
   */
  public fun makeYUVAPixmaps(
    dimensions: SkISize,
    yuvColorSpace: SkYUVColorSpace,
    bitmaps: Array<SkBitmap>,
    numBitmaps: Int,
  ): SkYUVAPixmaps {
    TODO("Implement makeYUVAPixmaps")
  }
}
