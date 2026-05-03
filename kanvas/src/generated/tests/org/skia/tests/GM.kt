package org.skia.tests

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkUnichar
import org.skia.gpu.ContextOptions
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.tools.SkMetaData

public typealias ClipCubicGMINHERITED = GM

public typealias AAXfermodesGMINHERITED = GM

public typealias AddArcGMINHERITED = GM

public typealias StrokeCircleGMINHERITED = GM

public typealias FillCircleGMINHERITED = GM

public typealias AlphaGradientsGMINHERITED = GM

public typealias AnalyticGradientShaderGMINHERITED = GM

public typealias AndroidBlendModesGMINHERITED = GM

public typealias AnimatedImageBlursINHERITED = GM

public typealias AnisotropicGMINHERITED = GM

public typealias AnisoMipsGMINHERITED = GM

public typealias ArcOfZorroGMINHERITED = GM

public typealias ArithmodeGMINHERITED = GM

public typealias ArithmodeBlenderGMINHERITED = GM

public typealias BadPaintGMINHERITED = GM

public typealias BC1TransparencyGMINHERITED = GM

public typealias BeziersGMINHERITED = GM

public typealias BigBlursGMINHERITED = GM

public typealias BigTextGMINHERITED = GM

public typealias BigTileImageFilterGMINHERITED = GM

public typealias FilterGMINHERITED = GM

public typealias TestExtractAlphaGMINHERITED = GM

public typealias BitmapImageGMINHERITED = GM

public typealias DrawBitmapRect2INHERITED = GM

public typealias DrawBitmapRect3INHERITED = GM

public typealias DrawBitmapRect4INHERITED = GM

public typealias BitmapRectRoundingINHERITED = GM

public typealias BitmapPremulGMINHERITED = GM

public typealias BitmapShaderGMINHERITED = GM

public typealias SrcRectConstraintGMINHERITED = GM

public typealias BlurCirclesGMINHERITED = GM

public typealias BlurCircles2GMINHERITED = GM

public typealias BlurIgnoreXformGMINHERITED = GM

public typealias BlurQuickRejectGMINHERITED = GM

public typealias BlurredClippedCircleGMINHERITED = GM

public typealias BmpFilterQualityRepeatINHERITED = GM

public typealias ClipErrorGMINHERITED = GM

public typealias ClipStrokeRectGMINHERITED = GM

public typealias CircularClipsGMINHERITED = GM

public typealias ClippedBitmapShadersGMINHERITED = GM

public typealias ColorEmojiGMINHERITED = GM

public typealias ColorEmojiBlendModesGMINHERITED = GM

public typealias ColorFilterAlpha8INHERITED = GM

public typealias ColorMatrixGMINHERITED = GM

public typealias ComplexClipGMINHERITED = GM

public typealias ComplexClip2GMINHERITED = GM

public typealias ColrV1GMINHERITED = GM

public typealias ComplexClip3GMINHERITED = GM

public typealias ComplexClip4GMINHERITED = GM

public typealias ComplexClipBlurTiledGMINHERITED = GM

public typealias ComposeShaderGMINHERITED = GM

public typealias ComposeShaderAlphaGMINHERITED = GM

public typealias ComposeShaderBitmapGMINHERITED = GM

public typealias CompressedTexturesGMINHERITED = GM

public typealias ConvexLineOnlyPathsGMINHERITED = GM

public typealias ConicPathsGMINHERITED = GM

public typealias ConvexPolyClipINHERITED = GM

public typealias CroppedRectsGMINHERITED = GM

public typealias ClippedCubic2GMINHERITED = GM

public typealias DashCircleGMINHERITED = GM

public typealias TrimGMINHERITED = GM

public typealias DFTextBlobPerspGMINHERITED = GM

public typealias DFTextGMINHERITED = GM

public typealias DisplacementMapGMINHERITED = GM

public typealias DrawAtlasColorsGMINHERITED = GM

public typealias DrawAtlasGMINHERITED = GM

public typealias DrawBitmapRectGMINHERITED = GM

public typealias DrawRegionGMINHERITED = GM

public typealias DrawMiniBitmapRectGMINHERITED = GM

public typealias DrawRegionModesGMINHERITED = GM

public typealias DRRectGMINHERITED = GM

public typealias DstReadShuffleINHERITED = GM

public typealias EmbossGMINHERITED = GM

public typealias EncodeGMINHERITED = GM

public typealias EncodeJpegAlphaOptsGMINHERITED = GM

public typealias EncodeColorTypesGMINHERITED = GM

public typealias EncodeSRGBGMINHERITED = GM

public typealias EncodePlatformGMINHERITED = GM

public typealias FillrectGradientGMINHERITED = GM

public typealias FillTypeGMINHERITED = GM

public typealias FillTypePerspGMINHERITED = GM

public typealias FilterBugGMINHERITED = GM

public typealias ImageFilterFastBoundGMINHERITED = GM

public typealias FlippityGMINHERITED = GM

public typealias FontCacheGMINHERITED = GM

public typealias FontRegenGMINHERITED = GM

public typealias BadAppleGMINHERITED = GM

public typealias FontScalerGMINHERITED = GM

public typealias GammaTextGMINHERITED = GM

public typealias GammaShaderTextGMINHERITED = GM

public typealias GiantBitmapGMINHERITED = GM

public typealias GradientsGMINHERITED = GM

public typealias RadialGradient2GMINHERITED = GM

public typealias RadialGradient3GMINHERITED = GM

public typealias RadialGradient4GMINHERITED = GM

public typealias LinearGradientGMINHERITED = GM

public typealias DegenerateGradientGMINHERITED = GM

public typealias GradientsNoTextureGMINHERITED = GM

public typealias GradientsManyColorsGMINHERITED = GM

public typealias HairlinesGMINHERITED = GM

public typealias HairModesGMINHERITED = GM

public typealias HardstopGradientShaderGMINHERITED = GM

public typealias HardstopGradientsManyGMINHERITED = GM

public typealias HighContrastFilterGMINHERITED = GM

public typealias ImageGMINHERITED = GM

public typealias ScalePixelsGMINHERITED = GM

public typealias ImagePictGMINHERITED = GM

public typealias ImageCacheratorGMINHERITED = GM

public typealias ImageShaderGMINHERITED = GM

public typealias ImageBlurRepeatModeGMINHERITED = GM

public typealias ImageBlurClampModeGMINHERITED = GM

public typealias ImageBlurTiledGMINHERITED = GM

public typealias ImageFiltersBaseGMINHERITED = GM

public typealias ImageFiltersTextBaseGMINHERITED = GM

public typealias ImageFiltersClippedGMINHERITED = GM

public typealias ImageFiltersCroppedGMINHERITED = GM

public typealias ImageFiltersGraphGMINHERITED = GM

public typealias ImageFiltersScaledGMINHERITED = GM

public typealias ImageFiltersStrokedGMINHERITED = GM

public typealias ImageFiltersTransformedGMINHERITED = GM

public typealias ImageMakeWithFilterGMINHERITED = GM

public typealias ImageSourceGMINHERITED = GM

public typealias ColorCubeGMINHERITED = GM

public typealias LatticeGMINHERITED = GM

public typealias LatticeGM2INHERITED = GM

public typealias LcdBlendGMINHERITED = GM

public typealias LcdOverlapGMINHERITED = GM

public typealias ImageLightingGMINHERITED = GM

public typealias LumaFilterGMINHERITED = GM

public typealias ManyCirclesGMINHERITED = GM

public typealias ManyRRectsGMINHERITED = GM

public typealias MatrixConvolutionGMINHERITED = GM

public typealias MixerCFGMINHERITED = GM

public typealias MixedTextBlobsGMINHERITED = GM

public typealias ModeColorFilterGMINHERITED = GM

public typealias MorphologyGMINHERITED = GM

public typealias NestedGMINHERITED = GM

public typealias NinePatchStretchGMINHERITED = GM

public typealias NonClosedPathsGMINHERITED = GM

public typealias OffsetImageFilterGMINHERITED = GM

public typealias SimpleOffsetImageFilterGMINHERITED = GM

public typealias OvalGMINHERITED = GM

public typealias FontPaletteGMINHERITED = GM

public typealias ContourStartGMINHERITED = GM

public typealias PathEffectGMINHERITED = GM

public typealias CTMPathEffectGMINHERITED = GM

public typealias PathFillGMINHERITED = GM

public typealias PathInverseFillGMINHERITED = GM

public typealias PathInteriorGMINHERITED = GM

public typealias PathMaskCacheINHERITED = GM

public typealias PathOpsInverseGMINHERITED = GM

public typealias PerlinNoiseGMINHERITED = GM

public typealias PerspImagesINHERITED = GM

public typealias PerspShadersGMINHERITED = GM

public typealias PictureGMINHERITED = GM

public typealias PictureCullRectGMINHERITED = GM

public typealias PictureImageFilterGMINHERITED = GM

public typealias PictureGeneratorGMINHERITED = GM

public typealias PictureShaderCacheGMINHERITED = GM

public typealias PictureShaderGMINHERITED = GM

public typealias PictureShaderTileGMINHERITED = GM

public typealias PointsGMINHERITED = GM

public typealias Poly2PolyGMINHERITED = GM

public typealias PolygonOffsetGMINHERITED = GM

public typealias PolygonsGMINHERITED = GM

public typealias QuadPathGMINHERITED = GM

public typealias QuadClosePathGMINHERITED = GM

public typealias ReadPixelsGMINHERITED = GM

public typealias ReadPixelsCodecGMINHERITED = GM

public typealias ReadPixelsPictureGMINHERITED = GM

public typealias ResizeGMINHERITED = GM

public typealias RoundRectGMINHERITED = GM

public typealias RRectGMINHERITED = GM

public typealias SamplerStressGMINHERITED = GM

public typealias ScaledEmojiGMINHERITED = GM

public typealias ScaledEmojiPosGMINHERITED = GM

public typealias ScaledEmojiPerspectiveGMINHERITED = GM

public typealias ScaledEmojiRenderingGMINHERITED = GM

public typealias ScaledStrokesGMINHERITED = GM

public typealias ShaderPathGMINHERITED = GM

public typealias ShaderText3GMINHERITED = GM

public typealias ShapesGMINHERITED = GM

public typealias ShowMipLevels3INHERITED = GM

public typealias SimpleClipGMINHERITED = GM

public typealias SimpleRectGMINHERITED = GM

public typealias SlugGMINHERITED = GM

public typealias SmallPathsGMINHERITED = GM

public typealias SpriteBitmapGMINHERITED = GM

public typealias StringArtGMINHERITED = GM

public typealias StLouisArchGMINHERITED = GM

public typealias StrokedLinesGMINHERITED = GM

public typealias StrokeRectGMINHERITED = GM

public typealias StrokeRectAnisotropicGMINHERITED = GM

public typealias StrokesGMINHERITED = GM

public typealias ZeroLenStrokesGMINHERITED = GM

public typealias TeenyStrokesGMINHERITED = GM

public typealias Strokes2GMINHERITED = GM

public typealias Strokes3GMINHERITED = GM

public typealias Strokes4GMINHERITED = GM

public typealias Strokes5GMINHERITED = GM

public typealias SurfacePropsGMINHERITED = GM

public typealias NewSurfaceGMINHERITED = GM

public typealias TableColorFilterGMINHERITED = GM

public typealias TestGradientGMINHERITED = GM

public typealias TallStretchedBitmapsGMINHERITED = GM

public typealias TextBlobGMINHERITED = GM

public typealias TextBlobBlockReorderingINHERITED = GM

public typealias TextBlobColorTransINHERITED = GM

public typealias TextBlobGeometryChangeINHERITED = GM

public typealias TextBlobMixedSizesINHERITED = GM

public typealias TextBlobRandomFontINHERITED = GM

public typealias TextBlobShaderGMINHERITED = GM

public typealias TextBlobTransformsINHERITED = GM

public typealias TextBlobUseAfterGpuFreeINHERITED = GM

public typealias ThinRectsGMINHERITED = GM

public typealias ThinStrokedRectsGMINHERITED = GM

public typealias TiledScaledBitmapGMINHERITED = GM

public typealias TileImageFilterGMINHERITED = GM

public typealias TilingGMINHERITED = GM

public typealias ScaledTilingGMINHERITED = GM

public typealias TypefaceStylesGMINHERITED = GM

public typealias VerticesGMINHERITED = GM

public typealias VariedTextGMINHERITED = GM

public typealias XfermodeImageFilterGMINHERITED = GM

public typealias XfermodesGMINHERITED = GM

public typealias Xfermodes2GMINHERITED = GM

public typealias Xfermodes3GMINHERITED = GM

public typealias ImageFromYUVINHERITED = GM

public typealias CompositorGMINHERITED = GM

public typealias WackyYUVFormatsGMINHERITED = GM

public typealias YUVMakeColorSpaceGMINHERITED = GM

public typealias YUVtoRGBSubsetEffectINHERITED = GM

/**
 * C++ original:
 * ```cpp
 * class GM {
 *     public:
 *         using DrawResult = skiagm::DrawResult;
 *         using GraphiteTestContext = skiatest::graphite::GraphiteTestContext;
 *
 *         explicit GM(SkColor backgroundColor = SK_ColorWHITE);
 *         virtual ~GM();
 *
 *         enum Mode {
 *             kGM_Mode,
 *             kSample_Mode,
 *             kBench_Mode,
 *         };
 *
 *         void setMode(Mode mode) { fMode = mode; }
 *         Mode getMode() const { return fMode; }
 *
 *         inline static constexpr char kErrorMsg_DrawSkippedGpuOnly[] =
 *                 "This test is for GPU configs only.";
 *
 *         DrawResult gpuSetup(SkCanvas*, SkString* errorMsg, GraphiteTestContext* = nullptr);
 *         void gpuTeardown();
 *
 *         void onceBeforeDraw() {
 *             if (!fHaveCalledOnceBeforeDraw) {
 *                 fHaveCalledOnceBeforeDraw = true;
 *                 this->onOnceBeforeDraw();
 *             }
 *         }
 *
 *         DrawResult draw(SkCanvas* canvas) {
 *             SkString errorMsg;
 *             return this->draw(canvas, &errorMsg);
 *         }
 *         DrawResult draw(SkCanvas*, SkString* errorMsg);
 *
 *         void drawBackground(SkCanvas*);
 *         DrawResult drawContent(SkCanvas* canvas) {
 *             SkString errorMsg;
 *             return this->drawContent(canvas, &errorMsg);
 *         }
 *         DrawResult drawContent(SkCanvas*, SkString* errorMsg);
 *
 *         virtual SkISize getISize() = 0;
 *
 *         virtual SkString getName() const = 0;
 *
 *         virtual bool runAsBench() const;
 *
 *         SkScalar width() {
 *             return SkIntToScalar(this->getISize().width());
 *         }
 *         SkScalar height() {
 *             return SkIntToScalar(this->getISize().height());
 *         }
 *
 *         SkColor getBGColor() const { return fBGColor; }
 *         void setBGColor(SkColor);
 *
 *         // helper: fill a rect in the specified color based on the GM's getISize bounds.
 *         void drawSizeBounds(SkCanvas*, SkColor);
 *
 *         bool animate(double /*nanos*/);
 *         virtual bool onChar(SkUnichar);
 *
 *         bool getControls(SkMetaData* controls) { return this->onGetControls(controls); }
 *         void setControls(const SkMetaData& controls) { this->onSetControls(controls); }
 *
 *         // Override to modify the default surface properties of the canvas to be used.
 *         // The value may be further modified or ignored if the canvas used cannot support it.
 *         virtual void modifySurfaceProps(SkSurfaceProps*) const {}
 *
 *         virtual void modifyGrContextOptions(GrContextOptions*) {}
 *         virtual void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions*) const {}
 *
 *         // Convenience method to skip Bazel-only GMs from DM.
 *         //
 *         // As of Q3 2023, lovisolo@ is experimenting with reimplementing some DM behaviors as
 *         // smaller, independent Bazel targets. For example, file
 *         // //tools/testrunners/gm/BazelGMTestRunner.cpp provides a main function that can run GMs.
 *         // With this file, one can define multiple small Bazel tests to run groups of related GMs
 *         // with Bazel. However, GMs are only one kind of "source" supported by DM (see class
 *         // GMSrc). DM supports other kinds of sources as well, such as codecs (CodecSrc class) and
 *         // image generators (ImageGenSrc class). One possible strategy to support these sources in
 *         // our Bazel build is to turn them into GMs. For example, instead of using the CodecSrc
 *         // class from Bazel, we could have a GM subclass that takes an image as an input, decodes
 *         // it using a codec, and draws in on a canvas. Given that this overlaps with existing DM
 *         // functionality, we would mark such GMs as Bazel-only.
 *         //
 *         // Another possibility is to slowly replace all existing DM source types with just GMs.
 *         // This would lead to a simpler DM architecture where there is only one source type and
 *         // multiple sinks, as opposed to the current design with multiple sources and sinks.
 *         // Furthermore, it would simplify the migration to Bazel because it would allow us to
 *         // leverage existing work to run GMs with Bazel.
 *         //
 *         // TODO(lovisolo): Delete once it's no longer needed.
 *         virtual bool isBazelOnly() const { return false; }
 *
 *         // Ignored by DM. Returns the set of Gold key/value pairs specific to this GM, such as the
 *         // GM name and corpus. GMs may define additional keys. For example, codec GMs define keys
 *         // for the parameters utilized to initialize the codec.
 *         virtual std::map<std::string, std::string> getGoldKeys() const {
 *             return std::map<std::string, std::string>{
 *                     {"name", getName().c_str()},
 *                     {"source_type", "gm"},
 *             };
 *         }
 *
 *     protected:
 *         // onGpuSetup is called once before any other processing with a direct context.
 *         virtual DrawResult onGpuSetup(SkCanvas*, SkString*, GraphiteTestContext*) {
 *             return DrawResult::kOk;
 *         }
 *         virtual void onGpuTeardown() {}
 *         virtual void onOnceBeforeDraw();
 *         virtual DrawResult onDraw(SkCanvas*, SkString* errorMsg);
 *         virtual void onDraw(SkCanvas*);
 *
 *         virtual bool onAnimate(double /*nanos*/);
 *         virtual bool onGetControls(SkMetaData*);
 *         virtual void onSetControls(const SkMetaData&);
 *
 *         GraphiteTestContext* graphiteTestContext() const { return fGraphiteTestContext; }
 *
 *     private:
 *         Mode fMode;
 *         SkColor    fBGColor;
 *         bool       fHaveCalledOnceBeforeDraw = false;
 *         bool       fGpuSetup = false;
 *         DrawResult fGpuSetupResult = DrawResult::kOk;
 *         GraphiteTestContext* fGraphiteTestContext;
 *     }
 * ```
 */
public abstract class GM public constructor(
  backgroundColor: SkColor = TODO(),
) {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr char kErrorMsg_DrawSkippedGpuOnly[] =
   *                 "This test is for GPU configs only."
   * ```
   */
  private var fMode: Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * Mode fMode
   * ```
   */
  private var fBGColor: Int = TODO("Initialize fBGColor")

  /**
   * C++ original:
   * ```cpp
   * SkColor    fBGColor
   * ```
   */
  private var fHaveCalledOnceBeforeDraw: Boolean = TODO("Initialize fHaveCalledOnceBeforeDraw")

  /**
   * C++ original:
   * ```cpp
   * bool       fHaveCalledOnceBeforeDraw = false
   * ```
   */
  private var fGpuSetup: Boolean = TODO("Initialize fGpuSetup")

  /**
   * C++ original:
   * ```cpp
   * bool       fGpuSetup = false
   * ```
   */
  private var fGpuSetupResult: GMDrawResult = TODO("Initialize fGpuSetupResult")

  /**
   * C++ original:
   * ```cpp
   * DrawResult fGpuSetupResult = DrawResult::kOk
   * ```
   */
  private var fGraphiteTestContext: GMGraphiteTestContext? = TODO("Initialize fGraphiteTestContext")

  /**
   * C++ original:
   * ```cpp
   * void setMode(Mode mode) { fMode = mode; }
   * ```
   */
  public fun setMode(mode: Mode) {
    TODO("Implement setMode")
  }

  /**
   * C++ original:
   * ```cpp
   * Mode getMode() const { return fMode; }
   * ```
   */
  public fun getMode(): Mode {
    TODO("Implement getMode")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult GM::gpuSetup(SkCanvas* canvas,
   *                         SkString* errorMsg,
   *                         GraphiteTestContext* graphiteTestContext) {
   *     TRACE_EVENT1("GM", TRACE_FUNC, "name", TRACE_STR_COPY(this->getName().c_str()));
   *     if (!fGpuSetup) {
   *         // When drawn in viewer, gpuSetup will be called multiple times with the same
   *         // GrContext or graphite::Context.
   *         fGpuSetup = true;
   *         fGpuSetupResult = this->onGpuSetup(canvas, errorMsg, graphiteTestContext);
   *     }
   *     if (fGpuSetupResult == DrawResult::kOk) {
   *         fGraphiteTestContext = graphiteTestContext;
   *     } else {
   *         handle_gm_failure(canvas, fGpuSetupResult, *errorMsg);
   *     }
   *
   *     return fGpuSetupResult;
   * }
   * ```
   */
  public fun gpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    graphiteTestContext: GMGraphiteTestContext? = TODO(),
  ): GMDrawResult {
    TODO("Implement gpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::gpuTeardown() {
   *     this->onGpuTeardown();
   *
   *     // After 'gpuTeardown' a GM can be reused with a different GrContext or graphite::Context. Reset
   *     // the flag so 'onGpuSetup' will be called.
   *     fGpuSetup = false;
   *     fGraphiteTestContext = nullptr;
   * }
   * ```
   */
  public fun gpuTeardown() {
    TODO("Implement gpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * void onceBeforeDraw() {
   *             if (!fHaveCalledOnceBeforeDraw) {
   *                 fHaveCalledOnceBeforeDraw = true;
   *                 this->onOnceBeforeDraw();
   *             }
   *         }
   * ```
   */
  public fun onceBeforeDraw() {
    TODO("Implement onceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult draw(SkCanvas* canvas) {
   *             SkString errorMsg;
   *             return this->draw(canvas, &errorMsg);
   *         }
   * ```
   */
  public fun draw(canvas: SkCanvas?): GMDrawResult {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult GM::draw(SkCanvas* canvas, SkString* errorMsg) {
   *     TRACE_EVENT1("GM", TRACE_FUNC, "name", TRACE_STR_COPY(this->getName().c_str()));
   *     this->drawBackground(canvas);
   *     return this->drawContent(canvas, errorMsg);
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?, errorMsg: String?): GMDrawResult {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::drawBackground(SkCanvas* canvas) {
   *     TRACE_EVENT0("GM", TRACE_FUNC);
   *     this->onceBeforeDraw();
   *     canvas->drawColor(fBGColor, SkBlendMode::kSrc);
   * }
   * ```
   */
  public fun drawBackground(canvas: SkCanvas?) {
    TODO("Implement drawBackground")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult drawContent(SkCanvas* canvas) {
   *             SkString errorMsg;
   *             return this->drawContent(canvas, &errorMsg);
   *         }
   * ```
   */
  public fun drawContent(canvas: SkCanvas?): GMDrawResult {
    TODO("Implement drawContent")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult GM::drawContent(SkCanvas* canvas, SkString* errorMsg) {
   *     TRACE_EVENT0("GM", TRACE_FUNC);
   *     this->onceBeforeDraw();
   *     SkAutoCanvasRestore acr(canvas, true);
   *     DrawResult drawResult = this->onDraw(canvas, errorMsg);
   *     if (DrawResult::kOk != drawResult) {
   *         handle_gm_failure(canvas, drawResult, *errorMsg);
   *     }
   *     return drawResult;
   * }
   * ```
   */
  public fun drawContent(canvas: SkCanvas?, errorMsg: String?): GMDrawResult {
    TODO("Implement drawContent")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkISize getISize() = 0
   * ```
   */
  public abstract fun getISize(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkString getName() const = 0
   * ```
   */
  public abstract fun getName(): Int

  /**
   * C++ original:
   * ```cpp
   * bool GM::runAsBench() const { return false; }
   * ```
   */
  public open fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar width() {
   *             return SkIntToScalar(this->getISize().width());
   *         }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() {
   *             return SkIntToScalar(this->getISize().height());
   *         }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor getBGColor() const { return fBGColor; }
   * ```
   */
  public fun getBGColor(): Int {
    TODO("Implement getBGColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::setBGColor(SkColor color) {
   *     fBGColor = color;
   * }
   * ```
   */
  public fun setBGColor(color: SkColor) {
    TODO("Implement setBGColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::drawSizeBounds(SkCanvas* canvas, SkColor color) {
   *     canvas->drawRect(SkRect::Make(this->getISize()), SkPaint(SkColor4f::FromColor(color)));
   * }
   * ```
   */
  public fun drawSizeBounds(canvas: SkCanvas?, color: SkColor) {
    TODO("Implement drawSizeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GM::animate(double nanos) { return this->onAnimate(nanos); }
   * ```
   */
  public fun animate(nanos: Int): Boolean {
    TODO("Implement animate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GM::onChar(SkUnichar uni) { return false; }
   * ```
   */
  public open fun onChar(uni: SkUnichar): Boolean {
    TODO("Implement onChar")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getControls(SkMetaData* controls) { return this->onGetControls(controls); }
   * ```
   */
  public fun getControls(controls: SkMetaData?): Boolean {
    TODO("Implement getControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void setControls(const SkMetaData& controls) { this->onSetControls(controls); }
   * ```
   */
  public fun setControls(controls: SkMetaData) {
    TODO("Implement setControls")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void modifySurfaceProps(SkSurfaceProps*) const {}
   * ```
   */
  public open fun modifySurfaceProps(param0: SkSurfaceProps?) {
    TODO("Implement modifySurfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void modifyGrContextOptions(GrContextOptions*) {}
   * ```
   */
  public open fun modifyGrContextOptions(param0: GrContextOptions?) {
    TODO("Implement modifyGrContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions*) const {}
   * ```
   */
  public open fun modifyGraphiteContextOptions(param0: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isBazelOnly() const { return false; }
   * ```
   */
  public open fun isBazelOnly(): Boolean {
    TODO("Implement isBazelOnly")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::map<std::string, std::string> getGoldKeys() const {
   *             return std::map<std::string, std::string>{
   *                     {"name", getName().c_str()},
   *                     {"source_type", "gm"},
   *             };
   *         }
   * ```
   */
  public open fun getGoldKeys(): Int {
    TODO("Implement getGoldKeys")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual DrawResult onGpuSetup(SkCanvas*, SkString*, GraphiteTestContext*) {
   *             return DrawResult::kOk;
   *         }
   * ```
   */
  protected open fun onGpuSetup(
    param0: SkCanvas?,
    param1: String?,
    param2: GMGraphiteTestContext?,
  ): GMDrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onGpuTeardown() {}
   * ```
   */
  protected open fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::onOnceBeforeDraw() {}
   * ```
   */
  protected open fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult GM::onDraw(SkCanvas* canvas, SkString* errorMsg) {
   *     this->onDraw(canvas);
   *     return DrawResult::kOk;
   * }
   * ```
   */
  protected open fun onDraw(canvas: SkCanvas?, errorMsg: String?): GMDrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::onDraw(SkCanvas*) { SK_ABORT("Not implemented."); }
   * ```
   */
  protected open fun onDraw(param0: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GM::onAnimate(double /*nanos*/) { return false; }
   * ```
   */
  protected open fun onAnimate(param0: Int): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GM::onGetControls(SkMetaData*) { return false; }
   * ```
   */
  protected open fun onGetControls(param0: SkMetaData?): Boolean {
    TODO("Implement onGetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void GM::onSetControls(const SkMetaData&) {}
   * ```
   */
  protected open fun onSetControls(param0: SkMetaData) {
    TODO("Implement onSetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * GraphiteTestContext* graphiteTestContext() const { return fGraphiteTestContext; }
   * ```
   */
  protected fun graphiteTestContext(): GMGraphiteTestContext {
    TODO("Implement graphiteTestContext")
  }

  public enum class Mode {
    kGM_Mode,
    kSample_Mode,
    kBench_Mode,
  }

  public companion object {
    public val kErrorMsgDrawSkippedGpuOnly: CharArray =
        TODO("Initialize kErrorMsgDrawSkippedGpuOnly")
  }
}
