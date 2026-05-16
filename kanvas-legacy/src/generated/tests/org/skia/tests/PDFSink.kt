package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PDFSink : public Sink {
 * public:
 *     PDFSink(bool pdfa, SkScalar rasterDpi) : fPDFA(pdfa), fRasterDpi(rasterDpi) {}
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "pdf"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
 *
 *     bool fPDFA;
 *     SkScalar fRasterDpi;
 * }
 * ```
 */
public open class PDFSink public constructor(
  pdfa: Boolean,
  rasterDpi: SkScalar,
) : Sink() {
  /**
   * C++ original:
   * ```cpp
   * bool fPDFA
   * ```
   */
  public var fPDFA: Boolean = TODO("Initialize fPDFA")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fRasterDpi
   * ```
   */
  public var fRasterDpi: Int = TODO("Initialize fRasterDpi")

  /**
   * C++ original:
   * ```cpp
   * Result PDFSink::draw(const Src& src, SkBitmap*, SkWStream* dst, SkString*) const {
   *     SkPDF::Metadata metadata;
   *     metadata.fTitle = src.name();
   *     metadata.fSubject = "rendering correctness test";
   *     metadata.fCreator = "Skia/DM";
   *     metadata.fProducer = "Skia/PDF HEAD"; // Set producer to avoid SK_MILESTONE churn.
   *     metadata.fRasterDPI = fRasterDpi;
   *     metadata.fPDFA = fPDFA;
   *     metadata.jpegDecoder = SkPDF::JPEG::Decode;
   *     metadata.jpegEncoder = SkPDF::JPEG::Encode;
   * #if SK_PDF_TEST_EXECUTOR
   *     std::unique_ptr<SkExecutor> executor = SkExecutor::MakeFIFOThreadPool();
   *     metadata.fExecutor = executor.get();
   * #endif
   *     auto doc = SkPDF::MakeDocument(dst, metadata);
   *     if (!doc) {
   *         return Result::Fatal("SkPDF::MakeDocument() returned nullptr");
   *     }
   *     return draw_skdocument(src, doc.get(), dst);
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
   * const char* fileExtension() const override { return "pdf"; }
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
