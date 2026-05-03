package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class ColorCodecSrc : public Src {
 * public:
 *     ColorCodecSrc(Path, bool decode_to_dst);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 * private:
 *     Path fPath;
 *     bool fDecodeToDst;
 * }
 * ```
 */
public open class ColorCodecSrc public constructor(
  path: Path,
  decodeToDst: Boolean,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Path fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * bool fDecodeToDst
   * ```
   */
  private var fDecodeToDst: Boolean = TODO("Initialize fDecodeToDst")

  /**
   * C++ original:
   * ```cpp
   * Result ColorCodecSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     if (!encoded) {
   *         return Result::Fatal("Couldn't read %s.", fPath.c_str());
   *     }
   *
   *     std::unique_ptr<SkCodec> codec(SkCodec::MakeFromData(encoded));
   *     if (nullptr == codec) {
   *         return Result::Fatal("Couldn't create codec for %s.", fPath.c_str());
   *     }
   *
   *     SkImageInfo info = codec->getInfo();
   *     if (SkEncodedOriginSwapsWidthHeight(codec->getOrigin())) {
   *         info = SkPixmapUtils::SwapWidthHeight(info);
   *     }
   *     if (fDecodeToDst) {
   *         SkImageInfo canvasInfo = canvas->imageInfo();
   *         if (!canvasInfo.colorSpace()) {
   *             // This will skip color conversion, and the resulting images will
   *             // look different from images they are compared against in Gold, but
   *             // that doesn't mean they are wrong. We have a test verifying that
   *             // passing a null SkColorSpace skips conversion, so skip this
   *             // misleading test.
   *             return Result::Skip("Skipping decoding without color transform.");
   *         }
   *         info = canvasInfo.makeDimensions(info.dimensions());
   *     }
   *
   *     auto [image, result] = codec->getImage(info);
   *     switch (result) {
   *         case SkCodec::kSuccess:
   *         case SkCodec::kErrorInInput:
   *         case SkCodec::kIncompleteInput:
   *             canvas->drawImage(image, 0,0);
   *             return Result::Ok();
   *         case SkCodec::kInvalidConversion:
   *             // TODO(mtklein): why are there formats we can't decode to?
   *             return Result::Skip("SkCodec can't decode to this format.");
   *         default:
   *             return Result::Fatal("Couldn't getPixels %s. Error code %d", fPath.c_str(), result);
   *     }
   * }
   * ```
   */
  public override fun draw(canvas: SkCanvas?, param1: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize ColorCodecSrc::size() const {
   *     sk_sp<SkData> encoded(SkData::MakeFromFileName(fPath.c_str()));
   *     std::unique_ptr<SkCodec> codec(SkCodec::MakeFromData(encoded));
   *     if (nullptr == codec) {
   *         return {0, 0};
   *     }
   *     return {codec->getInfo().width(), codec->getInfo().height()};
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name ColorCodecSrc::name() const {
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
   * bool ColorCodecSrc::veto(SinkFlags flags) const {
   *     // Test to direct raster backends (8888 and 565).
   *     return flags.type != SinkFlags::kRaster || flags.approach != SinkFlags::kDirect;
   * }
   * ```
   */
  public override fun veto(flags: SinkFlags): Boolean {
    TODO("Implement veto")
  }
}
