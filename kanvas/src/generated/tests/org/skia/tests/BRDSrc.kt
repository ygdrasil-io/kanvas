package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class BRDSrc : public Src {
 * public:
 *     enum Mode {
 *         // Decode the entire image as one region.
 *         kFullImage_Mode,
 *         // Splits the image into multiple regions using a divisor and decodes the regions
 *         // separately.  Also, this test adds a border of a few pixels to each of the regions
 *         // that it is decoding.  This tests the behavior when a client asks for a region that
 *         // does not fully fit in the image.
 *         kDivisor_Mode,
 *     };
 *
 *     BRDSrc(Path, Mode, CodecSrc::DstColorType, uint32_t);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 * private:
 *     Path                                     fPath;
 *     Mode                                     fMode;
 *     CodecSrc::DstColorType                   fDstColorType;
 *     uint32_t                                 fSampleSize;
 * }
 * ```
 */
public open class BRDSrc public constructor(
  path: Path,
  mode: Mode,
  dstColorType: CodecSrc.DstColorType,
  sampleSize: UInt,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Path                                     fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * Mode                                     fMode
   * ```
   */
  private var fMode: Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * CodecSrc::DstColorType                   fDstColorType
   * ```
   */
  private var fDstColorType: CodecSrc.DstColorType = TODO("Initialize fDstColorType")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                                 fSampleSize
   * ```
   */
  private var fSampleSize: Int = TODO("Initialize fSampleSize")

  /**
   * C++ original:
   * ```cpp
   * Result BRDSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     SkColorType colorType = canvas->imageInfo().colorType();
   *     if (kRGB_565_SkColorType == colorType &&
   *         CodecSrc::kGetFromCanvas_DstColorType != fDstColorType)
   *     {
   *         return Result::Skip("Testing non-565 to 565 is uninteresting.");
   *     }
   *     switch (fDstColorType) {
   *         case CodecSrc::kGetFromCanvas_DstColorType:
   *             break;
   *         case CodecSrc::kGrayscale_Always_DstColorType:
   *             colorType = kGray_8_SkColorType;
   *             break;
   *         default:
   *             SkASSERT(false);
   *             break;
   *     }
   *
   *     auto brd = create_brd(fPath);
   *     if (nullptr == brd) {
   *         return Result::Skip("Could not create brd for %s.", fPath.c_str());
   *     }
   *
   *     auto recommendedCT = brd->computeOutputColorType(colorType);
   *     if (kRGB_565_SkColorType == colorType && recommendedCT != colorType) {
   *         return Result::Skip("Skip decoding non-opaque to 565.");
   *     }
   *     colorType = recommendedCT;
   *
   *     auto colorSpace = brd->computeOutputColorSpace(colorType, nullptr);
   *
   *     const uint32_t width = brd->width();
   *     const uint32_t height = brd->height();
   *     // Visually inspecting very small output images is not necessary.
   *     if ((width / fSampleSize <= 10 || height / fSampleSize <= 10) && 1 != fSampleSize) {
   *         return Result::Skip("Scaling very small images is uninteresting.");
   *     }
   *     switch (fMode) {
   *         case kFullImage_Mode: {
   *             SkBitmap bitmap;
   *             if (!brd->decodeRegion(&bitmap, nullptr, SkIRect::MakeXYWH(0, 0, width, height),
   *                     fSampleSize, colorType, false, colorSpace)) {
   *                 return Result::Fatal("Cannot decode (full) region.");
   *             }
   *             alpha8_to_gray8(&bitmap);
   *
   *             canvas->drawImage(bitmap.asImage(), 0, 0);
   *             return Result::Ok();
   *         }
   *         case kDivisor_Mode: {
   *             const uint32_t divisor = 2;
   *             if (width < divisor || height < divisor) {
   *                 return Result::Skip("Divisor is larger than image dimension.");
   *             }
   *
   *             // Use a border to test subsets that extend outside the image.
   *             // We will not allow the border to be larger than the image dimensions.  Allowing
   *             // these large borders causes off by one errors that indicate a problem with the
   *             // test suite, not a problem with the implementation.
   *             const uint32_t maxBorder = std::min(width, height) / (fSampleSize * divisor);
   *             const uint32_t scaledBorder = std::min(5u, maxBorder);
   *             const uint32_t unscaledBorder = scaledBorder * fSampleSize;
   *
   *             // We may need to clear the canvas to avoid uninitialized memory.
   *             // Assume we are scaling a 780x780 image with sampleSize = 8.
   *             // The output image should be 97x97.
   *             // Each subset will be 390x390.
   *             // Each scaled subset be 48x48.
   *             // Four scaled subsets will only fill a 96x96 image.
   *             // The bottom row and last column will not be touched.
   *             // This is an unfortunate result of our rounding rules when scaling.
   *             // Maybe we need to consider testing scaled subsets without trying to
   *             // combine them to match the full scaled image?  Or maybe this is the
   *             // best we can do?
   *             canvas->clear(0);
   *
   *             for (uint32_t x = 0; x < divisor; x++) {
   *                 for (uint32_t y = 0; y < divisor; y++) {
   *                     // Calculate the subset dimensions
   *                     uint32_t subsetWidth = width / divisor;
   *                     uint32_t subsetHeight = height / divisor;
   *                     const int left = x * subsetWidth;
   *                     const int top = y * subsetHeight;
   *
   *                     // Increase the size of the last subset in each row or column, when the
   *                     // divisor does not divide evenly into the image dimensions
   *                     subsetWidth += (x + 1 == divisor) ? (width % divisor) : 0;
   *                     subsetHeight += (y + 1 == divisor) ? (height % divisor) : 0;
   *
   *                     // Increase the size of the subset in order to have a border on each side
   *                     const int decodeLeft = left - unscaledBorder;
   *                     const int decodeTop = top - unscaledBorder;
   *                     const uint32_t decodeWidth = subsetWidth + unscaledBorder * 2;
   *                     const uint32_t decodeHeight = subsetHeight + unscaledBorder * 2;
   *                     SkBitmap bitmap;
   *                     if (!brd->decodeRegion(&bitmap, nullptr, SkIRect::MakeXYWH(decodeLeft,
   *                             decodeTop, decodeWidth, decodeHeight), fSampleSize, colorType, false,
   *                             colorSpace)) {
   *                         return Result::Fatal("Cannot decode region.");
   *                     }
   *
   *                     alpha8_to_gray8(&bitmap);
   *                     canvas->drawImageRect(bitmap.asImage().get(),
   *                             SkRect::MakeXYWH((SkScalar) scaledBorder, (SkScalar) scaledBorder,
   *                                     (SkScalar) (subsetWidth / fSampleSize),
   *                                     (SkScalar) (subsetHeight / fSampleSize)),
   *                             SkRect::MakeXYWH((SkScalar) (left / fSampleSize),
   *                                     (SkScalar) (top / fSampleSize),
   *                                     (SkScalar) (subsetWidth / fSampleSize),
   *                                     (SkScalar) (subsetHeight / fSampleSize)),
   *                             SkSamplingOptions(), nullptr,
   *                             SkCanvas::kStrict_SrcRectConstraint);
   *                 }
   *             }
   *             return Result::Ok();
   *         }
   *         default:
   *             SkASSERT(false);
   *             return Result::Fatal("Error: Should not be reached.");
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
   * SkISize BRDSrc::size() const {
   *     auto brd = create_brd(fPath);
   *     if (brd) {
   *         return {std::max(1, brd->width() / (int)fSampleSize),
   *                 std::max(1, brd->height() / (int)fSampleSize)};
   *     }
   *     return {0, 0};
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name BRDSrc::name() const {
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
   * bool BRDSrc::veto(SinkFlags flags) const {
   *     // No need to test to non-raster or indirect backends.
   *     return flags.type != SinkFlags::kRaster
   *         || flags.approach != SinkFlags::kDirect;
   * }
   * ```
   */
  public override fun veto(flags: SinkFlags): Boolean {
    TODO("Implement veto")
  }

  public enum class Mode {
    kFullImage_Mode,
    kDivisor_Mode,
  }
}
