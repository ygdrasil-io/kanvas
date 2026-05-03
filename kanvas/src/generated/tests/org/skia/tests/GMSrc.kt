package org.skia.tests

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSurfaceProps
import org.skia.gpu.ContextOptions
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.tools.GraphiteTestContext
import skiagm.GMFactory

/**
 * C++ original:
 * ```cpp
 * class GMSrc : public Src {
 * public:
 *     explicit GMSrc(skiagm::GMFactory);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     void modifySurfaceProps(SkSurfaceProps*) const override;
 *     void modifyGrContextOptions(GrContextOptions* options) const override;
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions*) const override;
 * #endif
 *
 * private:
 *     skiagm::GMFactory fFactory;
 * }
 * ```
 */
public open class GMSrc public constructor(
  factory: GMFactory,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * skiagm::GMFactory fFactory
   * ```
   */
  private var fFactory: Int = TODO("Initialize fFactory")

  /**
   * C++ original:
   * ```cpp
   * Result GMSrc::draw(SkCanvas* canvas, GraphiteTestContext* testContext) const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     if (gm->isBazelOnly()) {
   *         // We skip Bazel-only GMs because they might overlap with existing DM functionality. See
   *         // comments in the skiagm::GM::isBazelOnly function declaration for context.
   *         return Result(Result::Status::Skip, SkString("Bazel-only GM"));
   *     }
   *     SkString msg;
   *
   *     skiagm::DrawResult gpuSetupResult = gm->gpuSetup(canvas, &msg, testContext);
   *     switch (gpuSetupResult) {
   *         case skiagm::DrawResult::kOk  : break;
   *         case skiagm::DrawResult::kFail: return Result(Result::Status::Fatal, msg);
   *         case skiagm::DrawResult::kSkip: return Result(Result::Status::Skip,  msg);
   *         default: SK_ABORT("");
   *     }
   *
   *     skiagm::DrawResult drawResult = gm->draw(canvas, &msg);
   *     switch (drawResult) {
   *         case skiagm::DrawResult::kOk  : return Result(Result::Status::Ok,    msg);
   *         case skiagm::DrawResult::kFail: return Result(Result::Status::Fatal, msg);
   *         case skiagm::DrawResult::kSkip: return Result(Result::Status::Skip,  msg);
   *         default: SK_ABORT("");
   *     }
   *
   *     // Note: we don't call "gpuTeardown" here because, when testing DDL recording, we want
   *     // the gpu-backed images to live past the lifetime of the GM.
   * }
   * ```
   */
  public override fun draw(canvas: SkCanvas?, testContext: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize GMSrc::size() const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     return gm->getISize();
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name GMSrc::name() const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     return gm->getName();
   * }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * void GMSrc::modifySurfaceProps(SkSurfaceProps* props) const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     gm->modifySurfaceProps(props);
   * }
   * ```
   */
  public override fun modifySurfaceProps(props: SkSurfaceProps?) {
    TODO("Implement modifySurfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * void GMSrc::modifyGrContextOptions(GrContextOptions* options) const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     gm->modifyGrContextOptions(options);
   * }
   * ```
   */
  public override fun modifyGrContextOptions(options: GrContextOptions?) {
    TODO("Implement modifyGrContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void GMSrc::modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const {
   *     std::unique_ptr<skiagm::GM> gm(fFactory());
   *     gm->modifyGraphiteContextOptions(options);
   * }
   * ```
   */
  public override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }
}
