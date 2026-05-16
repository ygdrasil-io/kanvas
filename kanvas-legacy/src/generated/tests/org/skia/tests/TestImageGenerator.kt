package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.codec.Options
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPMColor

/**
 * C++ original:
 * ```cpp
 * class TestImageGenerator : public SkImageGenerator {
 * public:
 *     enum TestType {
 *         kFailGetPixels_TestType,
 *         kSucceedGetPixels_TestType,
 *         kLast_TestType = kSucceedGetPixels_TestType
 *     };
 *     static int Width() { return 10; }
 *     static int Height() { return 10; }
 *     // value choosen so that there is no loss when converting to to RGB565 and back
 *     static SkColor   Color() { return ToolUtils::color_to_565(0xffaabbcc); }
 *     static SkPMColor PMColor() { return SkPreMultiplyColor(Color()); }
 *
 *     TestImageGenerator(TestType type, skiatest::Reporter* reporter,
 *                        SkColorType colorType = kN32_SkColorType)
 *     : SkImageGenerator(GetMyInfo(colorType)), fType(type), fReporter(reporter) {
 *         SkASSERT((fType <= kLast_TestType) && (fType >= 0));
 *     }
 *     ~TestImageGenerator() override {}
 *
 * protected:
 *     static SkImageInfo GetMyInfo(SkColorType colorType) {
 *         return SkImageInfo::Make(TestImageGenerator::Width(), TestImageGenerator::Height(),
 *                                  colorType, kOpaque_SkAlphaType);
 *     }
 *
 *     bool onGetPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
 *                      const Options& options) override {
 *         REPORTER_ASSERT(fReporter, pixels != nullptr);
 *         REPORTER_ASSERT(fReporter, rowBytes >= info.minRowBytes());
 *         if (fType != kSucceedGetPixels_TestType) {
 *             return false;
 *         }
 *         if (info.colorType() != kN32_SkColorType && info.colorType() != getInfo().colorType()) {
 *             return false;
 *         }
 *         char* bytePtr = static_cast<char*>(pixels);
 *         switch (info.colorType()) {
 *             case kN32_SkColorType:
 *                 for (int y = 0; y < info.height(); ++y) {
 *                     SkOpts::memset32((uint32_t*)bytePtr,
 *                                 TestImageGenerator::PMColor(), info.width());
 *                     bytePtr += rowBytes;
 *                 }
 *                 break;
 *             case kRGB_565_SkColorType:
 *                 for (int y = 0; y < info.height(); ++y) {
 *                     SkOpts::memset16((uint16_t*)bytePtr,
 *                         SkPixel32ToPixel16(TestImageGenerator::PMColor()), info.width());
 *                     bytePtr += rowBytes;
 *                 }
 *                 break;
 *             default:
 *                 return false;
 *         }
 *         return true;
 *     }
 *
 * private:
 *     const TestType fType;
 *     skiatest::Reporter* const fReporter;
 * }
 * ```
 */
public open class TestImageGenerator public constructor(
  type: TestType,
  reporter: Reporter?,
  colorType: SkColorType = TODO(),
) : SkImageGenerator(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const TestType fType
   * ```
   */
  private val fType: TestType = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* const fReporter
   * ```
   */
  private val fReporter: Reporter? = TODO("Initialize fReporter")

  /**
   * C++ original:
   * ```cpp
   * bool onGetPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
   *                      const Options& options) override {
   *         REPORTER_ASSERT(fReporter, pixels != nullptr);
   *         REPORTER_ASSERT(fReporter, rowBytes >= info.minRowBytes());
   *         if (fType != kSucceedGetPixels_TestType) {
   *             return false;
   *         }
   *         if (info.colorType() != kN32_SkColorType && info.colorType() != getInfo().colorType()) {
   *             return false;
   *         }
   *         char* bytePtr = static_cast<char*>(pixels);
   *         switch (info.colorType()) {
   *             case kN32_SkColorType:
   *                 for (int y = 0; y < info.height(); ++y) {
   *                     SkOpts::memset32((uint32_t*)bytePtr,
   *                                 TestImageGenerator::PMColor(), info.width());
   *                     bytePtr += rowBytes;
   *                 }
   *                 break;
   *             case kRGB_565_SkColorType:
   *                 for (int y = 0; y < info.height(); ++y) {
   *                     SkOpts::memset16((uint16_t*)bytePtr,
   *                         SkPixel32ToPixel16(TestImageGenerator::PMColor()), info.width());
   *                     bytePtr += rowBytes;
   *                 }
   *                 break;
   *             default:
   *                 return false;
   *         }
   *         return true;
   *     }
   * ```
   */
  protected override fun onGetPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    options: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }

  public enum class TestType {
    kFailGetPixels_TestType,
    kSucceedGetPixels_TestType,
    kLast_TestType,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static int Width() { return 10; }
     * ```
     */
    public fun width(): Int {
      TODO("Implement width")
    }

    /**
     * C++ original:
     * ```cpp
     * static int Height() { return 10; }
     * ```
     */
    public fun height(): Int {
      TODO("Implement height")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkColor   Color() { return ToolUtils::color_to_565(0xffaabbcc); }
     * ```
     */
    public fun color(): SkColor {
      TODO("Implement color")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPMColor PMColor() { return SkPreMultiplyColor(Color()); }
     * ```
     */
    public fun pMColor(): SkPMColor {
      TODO("Implement pMColor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo GetMyInfo(SkColorType colorType) {
     *         return SkImageInfo::Make(TestImageGenerator::Width(), TestImageGenerator::Height(),
     *                                  colorType, kOpaque_SkAlphaType);
     *     }
     * ```
     */
    protected fun getMyInfo(colorType: SkColorType): SkImageInfo {
      TODO("Implement getMyInfo")
    }
  }
}
