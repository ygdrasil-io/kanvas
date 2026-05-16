package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class ImageGenSrc : public Src {
 * public:
 *     enum Mode {
 *         kCodec_Mode,    // Use CodecImageGenerator
 *         kPlatform_Mode, // Uses CG or WIC
 *     };
 *     ImageGenSrc(Path, Mode, SkAlphaType, bool);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 *     bool serial() const override { return fRunSerially; }
 * private:
 *     Path        fPath;
 *     Mode        fMode;
 *     SkAlphaType fDstAlphaType;
 *     bool        fIsGpu;
 *     bool        fRunSerially;
 * }
 * ```
 */
public open class ImageGenSrc public constructor(
  path: Path,
  mode: Mode,
  alphaType: SkAlphaType,
  isGpu: Boolean,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Path        fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * Mode        fMode
   * ```
   */
  private var fMode: Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType fDstAlphaType
   * ```
   */
  private var fDstAlphaType: Int = TODO("Initialize fDstAlphaType")

  /**
   * C++ original:
   * ```cpp
   * bool        fIsGpu
   * ```
   */
  private var fIsGpu: Boolean = TODO("Initialize fIsGpu")

  /**
   * C++ original:
   * ```cpp
   * bool        fRunSerially
   * ```
   */
  private var fRunSerially: Boolean = TODO("Initialize fRunSerially")

  /**
   * C++ original:
   * ```cpp
   * Result ImageGenSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     if (kRGB_565_SkColorType == canvas->imageInfo().colorType()) {
   *         return Result::Skip("Uninteresting to test image generator to 565.");
   *     }
   *
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     if (!encoded) {
   *         return Result::Fatal("Couldn't read %s.", fPath.c_str());
   *     }
   *
   * #if defined(SK_BUILD_FOR_WIN)
   *     // Initialize COM in order to test with WIC.
   *     SkAutoCoInitialize com;
   *     if (!com.succeeded()) {
   *         return Result::Fatal("Could not initialize COM.");
   *     }
   * #endif
   *
   *     std::unique_ptr<SkImageGenerator> gen(nullptr);
   *     switch (fMode) {
   *         case kCodec_Mode:
   *             gen = SkCodecImageGenerator::MakeFromEncodedCodec(encoded);
   *             if (!gen) {
   *                 return Result::Fatal("Could not create codec image generator.");
   *             }
   *             break;
   *         case kPlatform_Mode: {
   * #if defined(SK_BUILD_FOR_MAC) || defined(SK_BUILD_FOR_IOS)
   *             gen = SkImageGeneratorCG::MakeFromEncodedCG(encoded);
   * #elif defined(SK_BUILD_FOR_WIN)
   *             gen = SkImageGeneratorWIC::MakeFromEncodedWIC(encoded);
   * #elif defined(SK_ENABLE_NDK_IMAGES)
   *             gen = SkImageGeneratorNDK::MakeFromEncodedNDK(encoded);
   * #endif
   *             if (!gen) {
   *                 return Result::Fatal("Could not create platform image generator.");
   *             }
   *             break;
   *         }
   *         default:
   *             SkASSERT(false);
   *             return Result::Fatal("Invalid image generator mode");
   *     }
   *
   *     // Test deferred decoding path on GPU
   *     if (fIsGpu) {
   *         sk_sp<SkImage> image(SkImages::DeferredFromGenerator(std::move(gen)));
   *         if (!image) {
   *             return Result::Fatal("Could not create image from codec image generator.");
   *         }
   *         canvas->drawImage(image, 0, 0);
   *         return Result::Ok();
   *     }
   *
   *     // Test various color and alpha types on CPU
   *     SkImageInfo decodeInfo = gen->getInfo().makeAlphaType(fDstAlphaType);
   *
   *     int bpp = decodeInfo.bytesPerPixel();
   *     size_t rowBytes = decodeInfo.width() * bpp;
   *     SkAutoMalloc pixels(decodeInfo.height() * rowBytes);
   *     if (!gen->getPixels(decodeInfo, pixels.get(), rowBytes)) {
   *         Result::Status status = Result::Status::Fatal;
   * #if defined(SK_BUILD_FOR_WIN)
   *         if (kPlatform_Mode == fMode) {
   *             // Do not issue a fatal error for WIC flakiness.
   *             status = Result::Status::Skip;
   *         }
   * #endif
   *         return Result(
   *                 status,
   *                 SkStringPrintf("Image generator could not getPixels() for %s\n", fPath.c_str()));
   *     }
   *
   *     set_bitmap_color_space(&decodeInfo);
   *     draw_to_canvas(canvas, decodeInfo, pixels.get(), rowBytes,
   *                    CodecSrc::kGetFromCanvas_DstColorType);
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
   * SkISize ImageGenSrc::size() const {
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     std::unique_ptr<SkCodec> codec(SkCodec::MakeFromData(encoded));
   *     if (nullptr == codec) {
   *         return {0, 0};
   *     }
   *     return codec->getInfo().dimensions();
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name ImageGenSrc::name() const {
   *     return SkOSPath::Basename(fPath.c_str());
   * }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ImageGenSrc::veto(SinkFlags flags) const {
   *     if (fIsGpu) {
   *         // MSAA runs tend to run out of memory and tests the same code paths as regular gpu configs.
   *         return flags.type != SinkFlags::kGPU || flags.approach != SinkFlags::kDirect ||
   *                flags.multisampled == SinkFlags::kMultisampled;
   *     }
   *
   *     return flags.type != SinkFlags::kRaster || flags.approach != SinkFlags::kDirect;
   * }
   * ```
   */
  public override fun veto(flags: SinkFlags): Boolean {
    TODO("Implement veto")
  }

  /**
   * C++ original:
   * ```cpp
   * bool serial() const override { return fRunSerially; }
   * ```
   */
  public override fun serial(): Boolean {
    TODO("Implement serial")
  }

  public enum class Mode {
    kCodec_Mode,
    kPlatform_Mode,
  }
}
