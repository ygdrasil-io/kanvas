package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnimCodecPlayerExifGM : public skiagm::GM {
 *     const char* fPath;
 *     SkISize fSize = SkISize::MakeEmpty();
 *     std::unique_ptr<SkAnimCodecPlayer> fPlayer;
 *     std::vector<SkCodec::FrameInfo> fFrameInfos;
 *
 *     void init() {
 *         if (!fPlayer) {
 *             auto data = GetResourceAsData(fPath);
 *             if (!data) return;
 *
 *             auto codec = SkCodec::MakeFromData(std::move(data));
 *             fFrameInfos = codec->getFrameInfo();
 *             fPlayer = std::make_unique<SkAnimCodecPlayer>(std::move(codec));
 *             if (!fPlayer) return;
 *
 *             // We'll draw one of each frame, so make it big enough to hold them all
 *             // in a grid. The grid will be roughly square, with "factor" frames per
 *             // row and up to "factor" rows.
 *             const size_t count = fFrameInfos.size();
 *             const float root = sqrt((float) count);
 *             const int factor = sk_float_ceil2int(root);
 *
 *             auto imageSize = fPlayer->dimensions();
 *             fSize.fWidth  = imageSize.fWidth  * factor;
 *             fSize.fHeight = imageSize.fHeight * sk_float_ceil2int((float) count / (float) factor);
 *         }
 *     }
 *
 *     SkString getName() const override {
 *         return SkStringPrintf("AnimCodecPlayerExif_%s", strrchr(fPath, '/') + 1);
 *     }
 *
 *     SkISize getISize() override {
 *         this->init();
 *         return fSize;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->init();
 *         if (!fPlayer) return;
 *
 *         const float root = sqrt((float) fFrameInfos.size());
 *         const int factor = sk_float_ceil2int(root);
 *         auto dimensions = fPlayer->dimensions();
 *
 *         uint32_t duration = 0;
 *         for (int frame = 0; duration < fPlayer->duration(); frame++) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *             const int xTranslate = (frame % factor) * dimensions.width();
 *             const int yTranslate = (frame / factor) * dimensions.height();
 *             canvas->translate(SkIntToScalar(xTranslate), SkIntToScalar(yTranslate));
 *
 *
 *             auto image = fPlayer->getFrame();
 *             canvas->drawImage(image, 0, 0);
 *             duration += fFrameInfos[frame].fDuration;
 *             fPlayer->seek(duration);
 *         }
 *     }
 * public:
 *     AnimCodecPlayerExifGM(const char* path)
 *         : fPath(path)
 *     {}
 *
 *     ~AnimCodecPlayerExifGM() override = default;
 * }
 * ```
 */
public open class AnimCodecPlayerExifGM public constructor(
  path: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const char* fPath
   * ```
   */
  private val fPath: String? = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SkISize fSize
   * ```
   */
  private var fSize: SkISize = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAnimCodecPlayer> fPlayer
   * ```
   */
  private var fPlayer: Int = TODO("Initialize fPlayer")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkCodec::FrameInfo> fFrameInfos
   * ```
   */
  private var fFrameInfos: Int = TODO("Initialize fFrameInfos")

  /**
   * C++ original:
   * ```cpp
   * void init() {
   *         if (!fPlayer) {
   *             auto data = GetResourceAsData(fPath);
   *             if (!data) return;
   *
   *             auto codec = SkCodec::MakeFromData(std::move(data));
   *             fFrameInfos = codec->getFrameInfo();
   *             fPlayer = std::make_unique<SkAnimCodecPlayer>(std::move(codec));
   *             if (!fPlayer) return;
   *
   *             // We'll draw one of each frame, so make it big enough to hold them all
   *             // in a grid. The grid will be roughly square, with "factor" frames per
   *             // row and up to "factor" rows.
   *             const size_t count = fFrameInfos.size();
   *             const float root = sqrt((float) count);
   *             const int factor = sk_float_ceil2int(root);
   *
   *             auto imageSize = fPlayer->dimensions();
   *             fSize.fWidth  = imageSize.fWidth  * factor;
   *             fSize.fHeight = imageSize.fHeight * sk_float_ceil2int((float) count / (float) factor);
   *         }
   *     }
   * ```
   */
  private fun `init`() {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("AnimCodecPlayerExif_%s", strrchr(fPath, '/') + 1);
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         this->init();
   *         return fSize;
   *     }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->init();
   *         if (!fPlayer) return;
   *
   *         const float root = sqrt((float) fFrameInfos.size());
   *         const int factor = sk_float_ceil2int(root);
   *         auto dimensions = fPlayer->dimensions();
   *
   *         uint32_t duration = 0;
   *         for (int frame = 0; duration < fPlayer->duration(); frame++) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *             const int xTranslate = (frame % factor) * dimensions.width();
   *             const int yTranslate = (frame / factor) * dimensions.height();
   *             canvas->translate(SkIntToScalar(xTranslate), SkIntToScalar(yTranslate));
   *
   *
   *             auto image = fPlayer->getFrame();
   *             canvas->drawImage(image, 0, 0);
   *             duration += fFrameInfos[frame].fDuration;
   *             fPlayer->seek(duration);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
