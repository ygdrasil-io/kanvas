package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class AndroidCodecSrc : public Src {
 * public:
 *     AndroidCodecSrc(Path, CodecSrc::DstColorType, SkAlphaType, int sampleSize);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 *     bool serial() const override { return fRunSerially; }
 * private:
 *     Path                    fPath;
 *     CodecSrc::DstColorType  fDstColorType;
 *     SkAlphaType             fDstAlphaType;
 *     int                     fSampleSize;
 *     bool                    fRunSerially;
 * }
 * ```
 */
public open class AndroidCodecSrc public constructor(
  path: Path,
  dstColorType: CodecSrc.DstColorType,
  dstAlphaType: SkAlphaType,
  sampleSize: Int,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Path                    fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * CodecSrc::DstColorType  fDstColorType
   * ```
   */
  private var fDstColorType: CodecSrc.DstColorType = TODO("Initialize fDstColorType")

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType             fDstAlphaType
   * ```
   */
  private var fDstAlphaType: Int = TODO("Initialize fDstAlphaType")

  /**
   * C++ original:
   * ```cpp
   * int                     fSampleSize
   * ```
   */
  private var fSampleSize: Int = TODO("Initialize fSampleSize")

  /**
   * C++ original:
   * ```cpp
   * bool                    fRunSerially
   * ```
   */
  private var fRunSerially: Boolean = TODO("Initialize fRunSerially")

  /**
   * C++ original:
   * ```cpp
   * Result AndroidCodecSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     if (!encoded) {
   *         return Result::Fatal("Couldn't read %s.", fPath.c_str());
   *     }
   *     std::unique_ptr<SkAndroidCodec> codec(SkAndroidCodec::MakeFromData(encoded));
   *     if (nullptr == codec) {
   *         return Result::Fatal("Couldn't create android codec for %s.", fPath.c_str());
   *     }
   *
   *     SkImageInfo decodeInfo = codec->getInfo();
   *     if (!get_decode_info(&decodeInfo, canvas->imageInfo().colorType(), fDstColorType,
   *                          fDstAlphaType)) {
   *         return Result::Skip("Skipping uninteresting test.");
   *     }
   *
   *     // Scale the image if it is desired.
   *     SkISize size = codec->getSampledDimensions(fSampleSize);
   *
   *     // Visually inspecting very small output images is not necessary.  We will
   *     // cover these cases in unit testing.
   *     if ((size.width() <= 10 || size.height() <= 10) && 1 != fSampleSize) {
   *         return Result::Skip("Scaling very small images is uninteresting.");
   *     }
   *     decodeInfo = decodeInfo.makeDimensions(size);
   *
   *     int bpp = decodeInfo.bytesPerPixel();
   *     size_t rowBytes = size.width() * bpp;
   *     SkAutoMalloc pixels(size.height() * rowBytes);
   *
   *     SkBitmap bitmap;
   *     SkImageInfo bitmapInfo = decodeInfo;
   *     set_bitmap_color_space(&bitmapInfo);
   *     if (kRGBA_8888_SkColorType == decodeInfo.colorType() ||
   *             kBGRA_8888_SkColorType == decodeInfo.colorType()) {
   *         bitmapInfo = bitmapInfo.makeColorType(kN32_SkColorType);
   *     }
   *
   *     // Create options for the codec.
   *     SkAndroidCodec::AndroidOptions options;
   *     options.fSampleSize = fSampleSize;
   *
   *     switch (codec->getAndroidPixels(decodeInfo, pixels.get(), rowBytes, &options)) {
   *         case SkCodec::kSuccess:
   *         case SkCodec::kErrorInInput:
   *         case SkCodec::kIncompleteInput:
   *             break;
   *         default:
   *             return Result::Fatal("Couldn't getPixels %s.", fPath.c_str());
   *     }
   *     draw_to_canvas(canvas, bitmapInfo, pixels.get(), rowBytes, fDstColorType);
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
   * SkISize AndroidCodecSrc::size() const {
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     std::unique_ptr<SkAndroidCodec> codec(SkAndroidCodec::MakeFromData(encoded));
   *     if (nullptr == codec) {
   *         return {0, 0};
   *     }
   *     return codec->getSampledDimensions(fSampleSize);
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name AndroidCodecSrc::name() const {
   *     // We will replicate the names used by CodecSrc so that images can
   *     // be compared in Gold.
   *     if (1 == fSampleSize) {
   *         return SkOSPath::Basename(fPath.c_str());
   *     }
   *     return get_scaled_name(fPath, 1.0f / (float) fSampleSize);
   * }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AndroidCodecSrc::veto(SinkFlags flags) const {
   *     // No need to test decoding to non-raster or indirect backend.
   *     return flags.type != SinkFlags::kRaster
   *         || flags.approach != SinkFlags::kDirect;
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
}
