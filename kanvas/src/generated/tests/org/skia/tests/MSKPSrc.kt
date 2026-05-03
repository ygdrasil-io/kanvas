package org.skia.tests

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class MSKPSrc : public Src {
 * public:
 *     explicit MSKPSrc(Path path);
 *
 *     int pageCount() const override;
 *     Result draw(SkCanvas* c, GraphiteTestContext*) const override;
 *     Result draw(int, SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     SkISize size(int) const override;
 *     Name name() const override;
 *
 * private:
 *     Path fPath;
 *     mutable skia_private::TArray<SkDocumentPage> fPages;
 * }
 * ```
 */
public open class MSKPSrc public constructor(
  path: Path,
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
   * mutable skia_private::TArray<SkDocumentPage> fPages
   * ```
   */
  private var fPages: Int = TODO("Initialize fPages")

  /**
   * C++ original:
   * ```cpp
   * int MSKPSrc::pageCount() const { return fPages.size(); }
   * ```
   */
  public override fun pageCount(): Int {
    TODO("Implement pageCount")
  }

  /**
   * C++ original:
   * ```cpp
   * Result MSKPSrc::draw(SkCanvas* c, GraphiteTestContext* testContext) const {
   *     return this->draw(FLAGS_mskpFrame, c, testContext);
   * }
   * ```
   */
  public override fun draw(c: SkCanvas?, testContext: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * Result MSKPSrc::draw(int i, SkCanvas* canvas, GraphiteTestContext*) const {
   *     if (this->pageCount() == 0) {
   *         return Result::Fatal("Unable to parse MultiPictureDocument file: %s", fPath.c_str());
   *     }
   *     if (i >= fPages.size() || i < 0) {
   *         return Result::Fatal("MultiPictureDocument page number out of range: %d", i);
   *     }
   *     SkPicture* page = fPages[i].fPicture.get();
   *     if (!page) {
   *         std::unique_ptr<SkStreamAsset> stream = SkStream::MakeFromFile(fPath.c_str());
   *         if (!stream) {
   *             return Result::Fatal("Unable to open file: %s", fPath.c_str());
   *         }
   *         if (!SkMultiPictureDocument::Read(stream.get(), &fPages[0], fPages.size())) {
   *             return Result::Fatal("SkMultiPictureDocument reader failed on page %d: %s", i,
   *                                  fPath.c_str());
   *         }
   *         page = fPages[i].fPicture.get();
   *     }
   *     canvas->drawPicture(page);
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(
    i: Int,
    canvas: SkCanvas?,
    param2: GraphiteTestContext?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize MSKPSrc::size() const { return this->size(FLAGS_mskpFrame); }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize MSKPSrc::size(int i) const {
   *     return i >= 0 && i < fPages.size() ? fPages[i].fSize.toCeil() : SkISize{0, 0};
   * }
   * ```
   */
  public override fun size(i: Int): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name MSKPSrc::name() const { return SkOSPath::Basename(fPath.c_str()); }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }
}
