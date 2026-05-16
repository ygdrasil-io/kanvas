package org.skia.gpu

import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkDevice
import org.skia.core.SkNoPixelsDevice
import org.skia.core.SkStrikeDeviceInfo
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkISize
import org.skia.utils.Slug
import undefined.CreateInfo

/**
 * C++ original:
 * ```cpp
 * class GlyphTrackingDevice final : public SkNoPixelsDevice {
 * public:
 *     GlyphTrackingDevice(
 *             const SkISize& dimensions, const SkSurfaceProps& props, SkStrikeServerImpl* server,
 *             sk_sp<SkColorSpace> colorSpace, sktext::gpu::SubRunControl SubRunControl)
 *             : SkNoPixelsDevice(SkIRect::MakeSize(dimensions), props, std::move(colorSpace))
 *             , fStrikeServerImpl(server)
 *             , fSubRunControl(SubRunControl) {
 *         SkASSERT(fStrikeServerImpl != nullptr);
 *     }
 *
 *     sk_sp<SkDevice> createDevice(const CreateInfo& cinfo, const SkPaint*) override {
 *         const SkSurfaceProps surfaceProps =
 *             this->surfaceProps().cloneWithPixelGeometry(cinfo.fPixelGeometry);
 *
 *         return sk_make_sp<GlyphTrackingDevice>(cinfo.fInfo.dimensions(),
 *                                                surfaceProps,
 *                                                fStrikeServerImpl,
 *                                                cinfo.fInfo.refColorSpace(),
 *                                                fSubRunControl);
 *     }
 *
 *     SkStrikeDeviceInfo strikeDeviceInfo() const override {
 *         return {this->surfaceProps(), this->scalerContextFlags(), &fSubRunControl};
 *     }
 *
 * protected:
 *     void onDrawGlyphRunList(SkCanvas*,
 *                             const sktext::GlyphRunList& glyphRunList,
 *                             const SkPaint& paint) override {
 *         SkMatrix drawMatrix = this->localToDevice();
 *         drawMatrix.preTranslate(glyphRunList.origin().x(), glyphRunList.origin().y());
 *
 *         // Just ignore the resulting SubRunContainer. Since we're passing in a null SubRunAllocator
 *         // no SubRuns will be produced.
 *         STSubRunAllocator<sizeof(SubRunContainer), alignof(SubRunContainer)> tempAlloc;
 *         auto container = SubRunContainer::MakeInAlloc(glyphRunList,
 *                                                       drawMatrix,
 *                                                       paint,
 *                                                       this->strikeDeviceInfo(),
 *                                                       fStrikeServerImpl,
 *                                                       &tempAlloc,
 *                                                       SubRunContainer::kStrikeCalculationsOnly,
 *                                                       "Cache Diff");
 *         // Calculations only. No SubRuns.
 *         SkASSERT(container->isEmpty());
 *     }
 *
 *     sk_sp<sktext::gpu::Slug> convertGlyphRunListToSlug(const sktext::GlyphRunList& glyphRunList,
 *                                                        const SkPaint& paint) override {
 *         // Full matrix for placing glyphs.
 *         SkMatrix positionMatrix = this->localToDevice();
 *         positionMatrix.preTranslate(glyphRunList.origin().x(), glyphRunList.origin().y());
 *
 *         // Use the SkStrikeServer's strike cache to generate the Slug.
 *         return sktext::gpu::MakeSlug(this->localToDevice(),
 *                                      glyphRunList,
 *                                      paint,
 *                                      this->strikeDeviceInfo(),
 *                                      fStrikeServerImpl);
 *     }
 *
 * private:
 *     SkStrikeServerImpl* const fStrikeServerImpl;
 *     const sktext::gpu::SubRunControl fSubRunControl;
 * }
 * ```
 */
public class GlyphTrackingDevice public constructor(
  dimensions: SkISize,
  props: SkSurfaceProps,
  server: SkStrikeServerImpl?,
  colorSpace: SkSp<SkColorSpace>,
  subRunControl: SubRunControl,
) : SkNoPixelsDevice(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkStrikeServerImpl* const fStrikeServerImpl
   * ```
   */
  private val fStrikeServerImpl: SkStrikeServerImpl? = TODO("Initialize fStrikeServerImpl")

  /**
   * C++ original:
   * ```cpp
   * const sktext::gpu::SubRunControl fSubRunControl
   * ```
   */
  private val fSubRunControl: SubRunControl = TODO("Initialize fSubRunControl")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> createDevice(const CreateInfo& cinfo, const SkPaint*) override {
   *         const SkSurfaceProps surfaceProps =
   *             this->surfaceProps().cloneWithPixelGeometry(cinfo.fPixelGeometry);
   *
   *         return sk_make_sp<GlyphTrackingDevice>(cinfo.fInfo.dimensions(),
   *                                                surfaceProps,
   *                                                fStrikeServerImpl,
   *                                                cinfo.fInfo.refColorSpace(),
   *                                                fSubRunControl);
   *     }
   * ```
   */
  public override fun createDevice(cinfo: CreateInfo, param1: SkPaint?): SkSp<SkDevice> {
    TODO("Implement createDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikeDeviceInfo strikeDeviceInfo() const override {
   *         return {this->surfaceProps(), this->scalerContextFlags(), &fSubRunControl};
   *     }
   * ```
   */
  public override fun strikeDeviceInfo(): SkStrikeDeviceInfo {
    TODO("Implement strikeDeviceInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawGlyphRunList(SkCanvas*,
   *                             const sktext::GlyphRunList& glyphRunList,
   *                             const SkPaint& paint) override {
   *         SkMatrix drawMatrix = this->localToDevice();
   *         drawMatrix.preTranslate(glyphRunList.origin().x(), glyphRunList.origin().y());
   *
   *         // Just ignore the resulting SubRunContainer. Since we're passing in a null SubRunAllocator
   *         // no SubRuns will be produced.
   *         STSubRunAllocator<sizeof(SubRunContainer), alignof(SubRunContainer)> tempAlloc;
   *         auto container = SubRunContainer::MakeInAlloc(glyphRunList,
   *                                                       drawMatrix,
   *                                                       paint,
   *                                                       this->strikeDeviceInfo(),
   *                                                       fStrikeServerImpl,
   *                                                       &tempAlloc,
   *                                                       SubRunContainer::kStrikeCalculationsOnly,
   *                                                       "Cache Diff");
   *         // Calculations only. No SubRuns.
   *         SkASSERT(container->isEmpty());
   *     }
   * ```
   */
  protected override fun onDrawGlyphRunList(
    param0: SkCanvas?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sktext::gpu::Slug> convertGlyphRunListToSlug(const sktext::GlyphRunList& glyphRunList,
   *                                                        const SkPaint& paint) override {
   *         // Full matrix for placing glyphs.
   *         SkMatrix positionMatrix = this->localToDevice();
   *         positionMatrix.preTranslate(glyphRunList.origin().x(), glyphRunList.origin().y());
   *
   *         // Use the SkStrikeServer's strike cache to generate the Slug.
   *         return sktext::gpu::MakeSlug(this->localToDevice(),
   *                                      glyphRunList,
   *                                      paint,
   *                                      this->strikeDeviceInfo(),
   *                                      fStrikeServerImpl);
   *     }
   * ```
   */
  protected override fun convertGlyphRunListToSlug(glyphRunList: GlyphRunList, paint: SkPaint): SkSp<Slug> {
    TODO("Implement convertGlyphRunListToSlug")
  }
}
