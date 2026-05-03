package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class SkottieSrc final : public Src {
 * public:
 *     explicit SkottieSrc(Path path);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 *
 * private:
 *     // Generates a kTileCount x kTileCount filmstrip with evenly distributed frames.
 *     inline static constexpr int      kTileCount = 5;
 *
 *     // Fit kTileCount x kTileCount frames to a 1000x1000 film strip.
 *     inline static constexpr SkScalar kTargetSize = 1000;
 *     inline static constexpr SkScalar kTileSize = kTargetSize / kTileCount;
 *
 *     Path                      fPath;
 * }
 * ```
 */
public class SkottieSrc public constructor(
  path: Path,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int      kTileCount = 5
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * Result SkottieSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     auto predecode = skresources::ImageDecodeStrategy::kPreDecode;
   *     // DM should have already registered the codecs necessary for DataURIResourceProviderProxy
   *     // to decode images.
   *     auto resource_provider = skresources::DataURIResourceProviderProxy::Make(
   *             skresources::FileResourceProvider::Make(SkOSPath::Dirname(fPath.c_str()), predecode),
   *             predecode,
   *             ToolUtils::TestFontMgr());
   *
   *     static constexpr char kInterceptPrefix[] = "__";
   *     auto precomp_interceptor =
   *             sk_make_sp<skottie_utils::ExternalAnimationPrecompInterceptor>(resource_provider,
   *                                                                            kInterceptPrefix);
   *     uint32_t flags = 0;
   *     if (FLAGS_useLottieGlyphPaths) {
   *         flags |= skottie::Animation::Builder::kPreferEmbeddedFonts;
   *     }
   *
   *     auto animation = skottie::Animation::Builder(flags)
   *         .setFontManager(ToolUtils::TestFontMgr())
   *         .setResourceProvider(std::move(resource_provider))
   *         .setPrecompInterceptor(std::move(precomp_interceptor))
   *         .setTextShapingFactory(SkShapers::BestAvailable())
   *         .makeFromFile(fPath.c_str());
   *     if (!animation) {
   *         return Result::Fatal("Unable to parse file: %s", fPath.c_str());
   *     }
   *
   *     canvas->drawColor(SK_ColorWHITE);
   *
   *     const auto t_rate = 1.0f / (kTileCount * kTileCount - 1);
   *
   *     // Draw the frames in a shuffled order to exercise non-linear
   *     // frame progression. The film strip will still be in order left-to-right,
   *     // top-down, just not drawn in that order.
   *     static constexpr int frameOrder[] = { 4, 0, 3, 1, 2 };
   *     static_assert(std::size(frameOrder) == kTileCount, "");
   *
   *     for (int i = 0; i < kTileCount; ++i) {
   *         const SkScalar y = frameOrder[i] * kTileSize;
   *
   *         for (int j = 0; j < kTileCount; ++j) {
   *             const SkScalar x = frameOrder[j] * kTileSize;
   *             SkRect dest = SkRect::MakeXYWH(x, y, kTileSize, kTileSize);
   *
   *             const auto t = t_rate * (frameOrder[i] * kTileCount + frameOrder[j]);
   *             {
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->clipRect(dest, true);
   *                 canvas->concat(SkMatrix::RectToRectOrIdentity(SkRect::MakeSize(animation->size()),
   *                                                               dest, SkMatrix::kCenter_ScaleToFit));
   *                 animation->seek(t);
   *                 animation->render(canvas);
   *             }
   *         }
   *     }
   *
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(canvas: SkCanvas?, param1: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize SkottieSrc::size() const {
   *     return SkISize::Make(kTargetSize, kTargetSize);
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name SkottieSrc::name() const { return SkOSPath::Basename(fPath.c_str()); }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkottieSrc::veto(SinkFlags flags) const {
   *     // No need to test to non-(raster||gpu||vector) or indirect backends.
   *     bool type_ok = flags.type == SinkFlags::kRaster
   *                 || flags.type == SinkFlags::kGPU
   *                 || flags.type == SinkFlags::kVector;
   *
   *     return !type_ok || flags.approach != SinkFlags::kDirect;
   * }
   * ```
   */
  public override fun veto(flags: SinkFlags): Boolean {
    TODO("Implement veto")
  }

  public companion object {
    private val kTileCount: Int = TODO("Initialize kTileCount")

    private val kTargetSize: Int = TODO("Initialize kTargetSize")

    private val kTileSize: Int = TODO("Initialize kTileSize")
  }
}
