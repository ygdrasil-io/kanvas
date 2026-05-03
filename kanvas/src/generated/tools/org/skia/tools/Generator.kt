package org.skia.tools

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.codec.Options
import org.skia.core.SkYUVAPixmapInfo
import org.skia.core.SkYUVAPixmaps
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class Generator : public SkImageGenerator {
 * public:
 *     Generator(SkYUVAPixmaps pixmaps, sk_sp<SkColorSpace> cs)
 *             : SkImageGenerator(SkImageInfo::Make(pixmaps.yuvaInfo().dimensions(),
 *                                                  kN32_SkColorType,
 *                                                  kPremul_SkAlphaType,
 *                                                  std::move(cs)))
 *             , fPixmaps(std::move(pixmaps)) {}
 *
 * protected:
 *     bool onGetPixels(const SkImageInfo& info,
 *                      void* pixels,
 *                      size_t rowBytes,
 *                      const Options&) override {
 *         if (kUnknown_SkColorType == fFlattened.colorType()) {
 *             fFlattened.allocPixels(info);
 *             SkASSERT(info == this->getInfo());
 *
 *             float mtx[20];
 *             SkColorMatrix_YUV2RGB(fPixmaps.yuvaInfo().yuvColorSpace(), mtx);
 *             SkYUVAInfo::YUVALocations yuvaLocations = fPixmaps.toYUVALocations();
 *             SkASSERT(SkYUVAInfo::YUVALocation::AreValidLocations(yuvaLocations));
 *
 *             SkMatrix om = fPixmaps.yuvaInfo().inverseOriginMatrix();
 *             float normX = 1.f/info.width();
 *             float normY = 1.f/info.height();
 *             if (SkEncodedOriginSwapsWidthHeight(fPixmaps.yuvaInfo().origin())) {
 *                 using std::swap;
 *                 swap(normX, normY);
 *             }
 *             for (int y = 0; y < info.height(); ++y) {
 *                 for (int x = 0; x < info.width(); ++x) {
 *                     SkPoint xy1 {(x + 0.5f),
 *                                  (y + 0.5f)};
 *                     xy1 = om.mapPoint(xy1);
 *                     xy1.fX *= normX;
 *                     xy1.fY *= normY;
 *
 *                     uint8_t yuva[4] = {0, 0, 0, 255};
 *
 *                     for (auto c : {SkYUVAInfo::YUVAChannels::kY,
 *                                    SkYUVAInfo::YUVAChannels::kU,
 *                                    SkYUVAInfo::YUVAChannels::kV}) {
 *                         const auto& pmap = fPixmaps.plane(yuvaLocations[c].fPlane);
 *                         yuva[c] = look_up(xy1, pmap, yuvaLocations[c].fChannel);
 *                     }
 *                     auto [aPlane, aChan] = yuvaLocations[SkYUVAInfo::YUVAChannels::kA];
 *                     if (aPlane >= 0) {
 *                         const auto& pmap = fPixmaps.plane(aPlane);
 *                         yuva[3] = look_up(xy1, pmap, aChan);
 *                     }
 *
 *                     // Making premul here.
 *                     *fFlattened.getAddr32(x, y) = convert_yuva_to_rgba(mtx, yuva);
 *                 }
 *             }
 *         }
 *
 *         return fFlattened.readPixels(info, pixels, rowBytes, 0, 0);
 *     }
 *
 *     bool onQueryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes& types,
 *                          SkYUVAPixmapInfo* info) const override {
 *         *info = fPixmaps.pixmapsInfo();
 *         return info->isValid();
 *     }
 *
 *     bool onGetYUVAPlanes(const SkYUVAPixmaps& pixmaps) override {
 *         SkASSERT(pixmaps.yuvaInfo() == fPixmaps.yuvaInfo());
 *         for (int i = 0; i < pixmaps.numPlanes(); ++i) {
 *             SkASSERT(fPixmaps.plane(i).colorType() == pixmaps.plane(i).colorType());
 *             SkASSERT(fPixmaps.plane(i).dimensions() == pixmaps.plane(i).dimensions());
 *             SkASSERT(fPixmaps.plane(i).rowBytes() == pixmaps.plane(i).rowBytes());
 *             fPixmaps.plane(i).readPixels(pixmaps.plane(i));
 *         }
 *         return true;
 *     }
 *
 * private:
 *     SkYUVAPixmaps fPixmaps;
 *     SkBitmap      fFlattened;
 * }
 * ```
 */
public open class Generator public constructor(
  pixmaps: SkYUVAPixmaps,
  cs: SkSp<SkColorSpace>,
) : SkImageGenerator(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps fPixmaps
   * ```
   */
  private var fPixmaps: SkYUVAPixmaps = TODO("Initialize fPixmaps")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap      fFlattened
   * ```
   */
  private var fFlattened: SkBitmap = TODO("Initialize fFlattened")

  /**
   * C++ original:
   * ```cpp
   * bool onGetPixels(const SkImageInfo& info,
   *                      void* pixels,
   *                      size_t rowBytes,
   *                      const Options&) override {
   *         if (kUnknown_SkColorType == fFlattened.colorType()) {
   *             fFlattened.allocPixels(info);
   *             SkASSERT(info == this->getInfo());
   *
   *             float mtx[20];
   *             SkColorMatrix_YUV2RGB(fPixmaps.yuvaInfo().yuvColorSpace(), mtx);
   *             SkYUVAInfo::YUVALocations yuvaLocations = fPixmaps.toYUVALocations();
   *             SkASSERT(SkYUVAInfo::YUVALocation::AreValidLocations(yuvaLocations));
   *
   *             SkMatrix om = fPixmaps.yuvaInfo().inverseOriginMatrix();
   *             float normX = 1.f/info.width();
   *             float normY = 1.f/info.height();
   *             if (SkEncodedOriginSwapsWidthHeight(fPixmaps.yuvaInfo().origin())) {
   *                 using std::swap;
   *                 swap(normX, normY);
   *             }
   *             for (int y = 0; y < info.height(); ++y) {
   *                 for (int x = 0; x < info.width(); ++x) {
   *                     SkPoint xy1 {(x + 0.5f),
   *                                  (y + 0.5f)};
   *                     xy1 = om.mapPoint(xy1);
   *                     xy1.fX *= normX;
   *                     xy1.fY *= normY;
   *
   *                     uint8_t yuva[4] = {0, 0, 0, 255};
   *
   *                     for (auto c : {SkYUVAInfo::YUVAChannels::kY,
   *                                    SkYUVAInfo::YUVAChannels::kU,
   *                                    SkYUVAInfo::YUVAChannels::kV}) {
   *                         const auto& pmap = fPixmaps.plane(yuvaLocations[c].fPlane);
   *                         yuva[c] = look_up(xy1, pmap, yuvaLocations[c].fChannel);
   *                     }
   *                     auto [aPlane, aChan] = yuvaLocations[SkYUVAInfo::YUVAChannels::kA];
   *                     if (aPlane >= 0) {
   *                         const auto& pmap = fPixmaps.plane(aPlane);
   *                         yuva[3] = look_up(xy1, pmap, aChan);
   *                     }
   *
   *                     // Making premul here.
   *                     *fFlattened.getAddr32(x, y) = convert_yuva_to_rgba(mtx, yuva);
   *                 }
   *             }
   *         }
   *
   *         return fFlattened.readPixels(info, pixels, rowBytes, 0, 0);
   *     }
   * ```
   */
  protected override fun onGetPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    param3: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onQueryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes& types,
   *                          SkYUVAPixmapInfo* info) const override {
   *         *info = fPixmaps.pixmapsInfo();
   *         return info->isValid();
   *     }
   * ```
   */
  protected override fun onQueryYUVAInfo(types: SkYUVAPixmapInfo.SupportedDataTypes, info: SkYUVAPixmapInfo?): Boolean {
    TODO("Implement onQueryYUVAInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGetYUVAPlanes(const SkYUVAPixmaps& pixmaps) override {
   *         SkASSERT(pixmaps.yuvaInfo() == fPixmaps.yuvaInfo());
   *         for (int i = 0; i < pixmaps.numPlanes(); ++i) {
   *             SkASSERT(fPixmaps.plane(i).colorType() == pixmaps.plane(i).colorType());
   *             SkASSERT(fPixmaps.plane(i).dimensions() == pixmaps.plane(i).dimensions());
   *             SkASSERT(fPixmaps.plane(i).rowBytes() == pixmaps.plane(i).rowBytes());
   *             fPixmaps.plane(i).readPixels(pixmaps.plane(i));
   *         }
   *         return true;
   *     }
   * ```
   */
  protected override fun onGetYUVAPlanes(pixmaps: SkYUVAPixmaps): Boolean {
    TODO("Implement onGetYUVAPlanes")
  }
}
