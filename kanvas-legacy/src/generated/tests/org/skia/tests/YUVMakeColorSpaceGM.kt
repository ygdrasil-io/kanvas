package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class YUVMakeColorSpaceGM : public GM {
 * public:
 *     YUVMakeColorSpaceGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("yuv_make_color_space"); }
 *
 *     SkISize getISize() override {
 *         int numCols = 4; // (transparent, opaque) x (untagged, tagged)
 *         int numRows = 5; // original, YUV, subset, makeNonTextureImage, readPixels
 *         return SkISize::Make(numCols * (kTileWidthHeight + kPad) + kPad,
 *                              numRows * (kTileWidthHeight + kPad) + kPad);
 *     }
 *
 *     void createBitmaps() {
 *         SkPoint origin = { kTileWidthHeight/2.0f, kTileWidthHeight/2.0f };
 *         float outerRadius = kTileWidthHeight/2.0f - 20.0f;
 *         float innerRadius = 20.0f;
 *
 *         {
 *             // transparent
 *             SkTDArray<SkRect> circles;
 *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 5, &circles);
 *             fOriginalBMs[0] = make_bitmap(kN32_SkColorType, path, circles, false, false);
 *         }
 *
 *         {
 *             // opaque
 *             SkTDArray<SkRect> circles;
 *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 7, &circles);
 *             fOriginalBMs[1] = make_bitmap(kN32_SkColorType, path, circles, true, false);
 *         }
 *
 *         fTargetColorSpace = SkColorSpace::MakeSRGB()->makeColorSpin();
 *     }
 *
 *     bool createImages(GrDirectContext* context, Recorder* recorder) {
 *         for (bool opaque : { false, true }) {
 *             PlaneData planes;
 *             extract_planes(fOriginalBMs[opaque],
 *                            kJPEG_SkYUVColorSpace,
 *                            kTopLeft_SkEncodedOrigin,
 *                            &planes);
 *
 *             SkBitmap resultBMs[4];
 *
 *             create_YUV(planes, kAYUV_YUVFormat, resultBMs, opaque);
 *
 *             YUVAPlanarConfig planarConfig(kAYUV_YUVFormat, opaque, kTopLeft_SkEncodedOrigin);
 *
 *             auto yuvaPixmaps = planarConfig.makeYUVAPixmaps(fOriginalBMs[opaque].dimensions(),
 *                                                             kJPEG_Full_SkYUVColorSpace,
 *                                                             resultBMs,
 *                                                             std::size(resultBMs));
 *
 *             int i = 0;
 *             for (sk_sp<SkColorSpace> cs : {sk_sp<SkColorSpace>(nullptr),
 *                                            SkColorSpace::MakeSRGB()}) {
 *                 auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(
 *                         yuvaPixmaps, skgpu::Mipmapped::kNo, std::move(cs));
 * #if defined(SK_GRAPHITE)
 *                 if (recorder) {
 *                     fImages[opaque][i++] = lazyYUV->refImage(
 *                             recorder, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
 *                 } else
 * #endif
 *                 {
 *                     fImages[opaque][i++] = lazyYUV->refImage(
 *                             context, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
 *                 }
 *             }
 *         }
 *
 * #if defined(SK_GANESH)
 *         // Some backends (e.g., Vulkan) require all work be completed for backend textures before
 *         // they are deleted. Since we don't know when we'll next have access to a direct context,
 *         // flush all the work now.
 *         if (context) {
 *             context->flush();
 *             context->submit(GrSyncCpu::kYes);
 *         }
 * #endif
 *         return true;
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 *         GrDirectContext* dContext = nullptr;
 * #if defined(SK_GANESH)
 *         dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext && dContext->abandoned()) {
 *             *errorMsg = "Abandoned GrDirectContext cannot create YUV images";
 *             return DrawResult::kSkip;
 *         }
 * #endif
 *         auto recorder = canvas->recorder();
 *         if (!recorder && !dContext) {
 *             *errorMsg = "GPU context required to create YUV images";
 *             return DrawResult::kSkip;
 *         }
 *
 *         this->createBitmaps();
 *         if (!this->createImages(dContext, recorder)) {
 *             *errorMsg = "Failed to create YUV images";
 *             return DrawResult::kFail;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override {
 *         fImages[0][0] = fImages[0][1] = fImages[1][0] = fImages[1][1] = nullptr;
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* msg) override {
 *         SkASSERT(fImages[0][0] && fImages[0][1] && fImages[1][0] && fImages[1][1]);
 *
 *         auto recorder = canvas->baseRecorder();
 *         if (!recorder) {
 *             *msg = "YUV ColorSpace image creation requires a GPU context.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         int x = kPad;
 *         for (int tagged : { 0, 1 }) {
 *             for (int opaque : { 0, 1 }) {
 *                 int y = kPad;
 *
 *                 auto raster = fOriginalBMs[opaque].asImage()->makeColorSpace(
 *                         nullptr, fTargetColorSpace, {});
 *                 canvas->drawImage(raster, x, y);
 *                 y += kTileWidthHeight + kPad;
 *
 *                 if (fImages[opaque][tagged]) {
 *                     sk_sp<SkImage> yuv = fImages[opaque][tagged]->makeColorSpace(
 *                             recorder, fTargetColorSpace, {/*fMipmapped=*/false});
 *
 *                     SkASSERT(yuv);
 *                     SkASSERT(SkColorSpace::Equals(yuv->colorSpace(), fTargetColorSpace.get()));
 *                     canvas->drawImage(yuv, x, y);
 *                     y += kTileWidthHeight + kPad;
 *
 *                     SkIRect bounds = SkIRect::MakeWH(kTileWidthHeight / 2, kTileWidthHeight / 2);
 *                     sk_sp<SkImage> subset;
 * #if defined(SK_GRAPHITE)
 *                     if (auto gr = skgpu::graphite::AsGraphiteRecorder(recorder)) {
 *                         subset = SkImages::SubsetTextureFrom(gr, yuv.get(), bounds);
 *                     }
 * #endif
 * #if defined(SK_GANESH)
 *                     if (auto gRecorder = AsGaneshRecorder(recorder)) {
 *                         subset = SkImages::SubsetTextureFrom(
 *                                 gRecorder->directContext(), yuv.get(), bounds);
 *                     }
 * #endif
 *                     SkASSERT(subset);
 *                     canvas->drawImage(subset, x, y);
 *                     y += kTileWidthHeight + kPad;
 *
 *                     // Graphite doesn't support makeNonTextureImage() so skip this
 *                     if (!recorder) {
 *                         auto nonTexture = yuv->makeNonTextureImage();
 *                         SkASSERT(nonTexture);
 *                         canvas->drawImage(nonTexture, x, y);
 *                     }
 *                     y += kTileWidthHeight + kPad;
 *
 *                     SkBitmap readBack;
 *                     readBack.allocPixels(yuv->imageInfo());
 * #if defined(SK_GRAPHITE)
 *                     if (recorder->type() == SkRecorder::Type::kGraphite) {
 *                         SkAssertResult(
 *                                 as_IB(yuv)->readPixelsGraphite(recorder, readBack.pixmap(), 0, 0));
 *                     } else
 * #endif
 *                     {
 * #if defined(SK_GANESH)
 *                         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *                         SkAssertResult(yuv->readPixels(dContext, readBack.pixmap(), 0, 0));
 * #else
 *                         SkASSERT(false);
 * #endif
 *                     }
 *                     canvas->drawImage(readBack.asImage(), x, y);
 *                 }
 *                 x += kTileWidthHeight + kPad;
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     SkBitmap fOriginalBMs[2];
 *     sk_sp<SkImage> fImages[2][2];
 *     sk_sp<SkColorSpace> fTargetColorSpace;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class YUVMakeColorSpaceGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fOriginalBMs[2]
   * ```
   */
  private var fOriginalBMs: Array<SkBitmap> = TODO("Initialize fOriginalBMs")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImages[2][2]
   * ```
   */
  private var fImages: Array<SkSp<SkImage>> = TODO("Initialize fImages")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fTargetColorSpace
   * ```
   */
  private var fTargetColorSpace: SkSp<SkColorSpace> = TODO("Initialize fTargetColorSpace")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("yuv_make_color_space"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         int numCols = 4; // (transparent, opaque) x (untagged, tagged)
   *         int numRows = 5; // original, YUV, subset, makeNonTextureImage, readPixels
   *         return SkISize::Make(numCols * (kTileWidthHeight + kPad) + kPad,
   *                              numRows * (kTileWidthHeight + kPad) + kPad);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void createBitmaps() {
   *         SkPoint origin = { kTileWidthHeight/2.0f, kTileWidthHeight/2.0f };
   *         float outerRadius = kTileWidthHeight/2.0f - 20.0f;
   *         float innerRadius = 20.0f;
   *
   *         {
   *             // transparent
   *             SkTDArray<SkRect> circles;
   *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 5, &circles);
   *             fOriginalBMs[0] = make_bitmap(kN32_SkColorType, path, circles, false, false);
   *         }
   *
   *         {
   *             // opaque
   *             SkTDArray<SkRect> circles;
   *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 7, &circles);
   *             fOriginalBMs[1] = make_bitmap(kN32_SkColorType, path, circles, true, false);
   *         }
   *
   *         fTargetColorSpace = SkColorSpace::MakeSRGB()->makeColorSpin();
   *     }
   * ```
   */
  protected fun createBitmaps() {
    TODO("Implement createBitmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool createImages(GrDirectContext* context, Recorder* recorder) {
   *         for (bool opaque : { false, true }) {
   *             PlaneData planes;
   *             extract_planes(fOriginalBMs[opaque],
   *                            kJPEG_SkYUVColorSpace,
   *                            kTopLeft_SkEncodedOrigin,
   *                            &planes);
   *
   *             SkBitmap resultBMs[4];
   *
   *             create_YUV(planes, kAYUV_YUVFormat, resultBMs, opaque);
   *
   *             YUVAPlanarConfig planarConfig(kAYUV_YUVFormat, opaque, kTopLeft_SkEncodedOrigin);
   *
   *             auto yuvaPixmaps = planarConfig.makeYUVAPixmaps(fOriginalBMs[opaque].dimensions(),
   *                                                             kJPEG_Full_SkYUVColorSpace,
   *                                                             resultBMs,
   *                                                             std::size(resultBMs));
   *
   *             int i = 0;
   *             for (sk_sp<SkColorSpace> cs : {sk_sp<SkColorSpace>(nullptr),
   *                                            SkColorSpace::MakeSRGB()}) {
   *                 auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(
   *                         yuvaPixmaps, skgpu::Mipmapped::kNo, std::move(cs));
   * #if defined(SK_GRAPHITE)
   *                 if (recorder) {
   *                     fImages[opaque][i++] = lazyYUV->refImage(
   *                             recorder, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
   *                 } else
   * #endif
   *                 {
   *                     fImages[opaque][i++] = lazyYUV->refImage(
   *                             context, sk_gpu_test::LazyYUVImage::Type::kFromTextures);
   *                 }
   *             }
   *         }
   *
   * #if defined(SK_GANESH)
   *         // Some backends (e.g., Vulkan) require all work be completed for backend textures before
   *         // they are deleted. Since we don't know when we'll next have access to a direct context,
   *         // flush all the work now.
   *         if (context) {
   *             context->flush();
   *             context->submit(GrSyncCpu::kYes);
   *         }
   * #endif
   *         return true;
   *     }
   * ```
   */
  protected fun createImages(context: GrDirectContext?, recorder: Recorder?): Boolean {
    TODO("Implement createImages")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   *         GrDirectContext* dContext = nullptr;
   * #if defined(SK_GANESH)
   *         dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext && dContext->abandoned()) {
   *             *errorMsg = "Abandoned GrDirectContext cannot create YUV images";
   *             return DrawResult::kSkip;
   *         }
   * #endif
   *         auto recorder = canvas->recorder();
   *         if (!recorder && !dContext) {
   *             *errorMsg = "GPU context required to create YUV images";
   *             return DrawResult::kSkip;
   *         }
   *
   *         this->createBitmaps();
   *         if (!this->createImages(dContext, recorder)) {
   *             *errorMsg = "Failed to create YUV images";
   *             return DrawResult::kFail;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onGpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override {
   *         fImages[0][0] = fImages[0][1] = fImages[1][0] = fImages[1][1] = nullptr;
   *     }
   * ```
   */
  protected override fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* msg) override {
   *         SkASSERT(fImages[0][0] && fImages[0][1] && fImages[1][0] && fImages[1][1]);
   *
   *         auto recorder = canvas->baseRecorder();
   *         if (!recorder) {
   *             *msg = "YUV ColorSpace image creation requires a GPU context.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         int x = kPad;
   *         for (int tagged : { 0, 1 }) {
   *             for (int opaque : { 0, 1 }) {
   *                 int y = kPad;
   *
   *                 auto raster = fOriginalBMs[opaque].asImage()->makeColorSpace(
   *                         nullptr, fTargetColorSpace, {});
   *                 canvas->drawImage(raster, x, y);
   *                 y += kTileWidthHeight + kPad;
   *
   *                 if (fImages[opaque][tagged]) {
   *                     sk_sp<SkImage> yuv = fImages[opaque][tagged]->makeColorSpace(
   *                             recorder, fTargetColorSpace, {/*fMipmapped=*/false});
   *
   *                     SkASSERT(yuv);
   *                     SkASSERT(SkColorSpace::Equals(yuv->colorSpace(), fTargetColorSpace.get()));
   *                     canvas->drawImage(yuv, x, y);
   *                     y += kTileWidthHeight + kPad;
   *
   *                     SkIRect bounds = SkIRect::MakeWH(kTileWidthHeight / 2, kTileWidthHeight / 2);
   *                     sk_sp<SkImage> subset;
   * #if defined(SK_GRAPHITE)
   *                     if (auto gr = skgpu::graphite::AsGraphiteRecorder(recorder)) {
   *                         subset = SkImages::SubsetTextureFrom(gr, yuv.get(), bounds);
   *                     }
   * #endif
   * #if defined(SK_GANESH)
   *                     if (auto gRecorder = AsGaneshRecorder(recorder)) {
   *                         subset = SkImages::SubsetTextureFrom(
   *                                 gRecorder->directContext(), yuv.get(), bounds);
   *                     }
   * #endif
   *                     SkASSERT(subset);
   *                     canvas->drawImage(subset, x, y);
   *                     y += kTileWidthHeight + kPad;
   *
   *                     // Graphite doesn't support makeNonTextureImage() so skip this
   *                     if (!recorder) {
   *                         auto nonTexture = yuv->makeNonTextureImage();
   *                         SkASSERT(nonTexture);
   *                         canvas->drawImage(nonTexture, x, y);
   *                     }
   *                     y += kTileWidthHeight + kPad;
   *
   *                     SkBitmap readBack;
   *                     readBack.allocPixels(yuv->imageInfo());
   * #if defined(SK_GRAPHITE)
   *                     if (recorder->type() == SkRecorder::Type::kGraphite) {
   *                         SkAssertResult(
   *                                 as_IB(yuv)->readPixelsGraphite(recorder, readBack.pixmap(), 0, 0));
   *                     } else
   * #endif
   *                     {
   * #if defined(SK_GANESH)
   *                         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *                         SkAssertResult(yuv->readPixels(dContext, readBack.pixmap(), 0, 0));
   * #else
   *                         SkASSERT(false);
   * #endif
   *                     }
   *                     canvas->drawImage(readBack.asImage(), x, y);
   *                 }
   *                 x += kTileWidthHeight + kPad;
   *             }
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, msg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
