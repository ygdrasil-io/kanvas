package org.skia.tests

import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class SVGSink : public Sink {
 * public:
 *     explicit SVGSink(int pageIndex = 0);
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "svg"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
 *
 * private:
 *     int fPageIndex;
 * }
 * ```
 */
public open class SVGSink public constructor(
  pageIndex: Int = TODO(),
) : Sink() {
  /**
   * C++ original:
   * ```cpp
   * int fPageIndex
   * ```
   */
  private var fPageIndex: Int = TODO("Initialize fPageIndex")

  /**
   * C++ original:
   * ```cpp
   * Result SVGSink::draw(const Src& src, SkBitmap*, SkWStream* dst, SkString*) const {
   * #if defined(SK_ENABLE_SVG)
   *     if (src.pageCount() > 1) {
   *         int pageCount = src.pageCount();
   *         if (fPageIndex > pageCount - 1) {
   *             return Result::Fatal("Page index %d too high for document with only %d pages.",
   *                                  fPageIndex, pageCount);
   *         }
   *     }
   *     return src.draw(fPageIndex,
   *                     SkSVGCanvas::Make(SkRect::MakeWH(SkIntToScalar(src.size().width()),
   *                                                      SkIntToScalar(src.size().height())),
   *                                       dst)
   *                             .get(),
   *                     /*GraphiteTestContext=*/nullptr);
   * #else
   *     (void)fPageIndex;
   *     return Result::Fatal("SVG sink is disabled.");
   * #endif // SK_ENABLE_SVG
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    param1: SkBitmap?,
    dst: SkWStream?,
    param3: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override { return "svg"; }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
   * ```
   */
  public override fun flags(): SinkFlags {
    TODO("Implement flags")
  }
}
