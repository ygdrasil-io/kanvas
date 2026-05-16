package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class SVGSrc : public Src {
 * public:
 *     explicit SVGSrc(Path path);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 *     bool veto(SinkFlags) const override;
 *
 * private:
 *     Name            fName;
 *     sk_sp<SkSVGDOM> fDom;
 *     SkScalar        fScale;
 *
 *     using INHERITED = Src;
 * }
 * ```
 */
public open class SVGSrc public constructor(
  path: Path,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Name            fName
   * ```
   */
  private var fName: Name = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSVGDOM> fDom
   * ```
   */
  private var fDom: Int = TODO("Initialize fDom")

  /**
   * C++ original:
   * ```cpp
   * SkScalar        fScale
   * ```
   */
  private var fScale: Int = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * Result SVGSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     if (!fDom) {
   *         return Result::Fatal("Unable to parse file: %s", fName.c_str());
   *     }
   *
   *     SkAutoCanvasRestore acr(canvas, true);
   *     canvas->scale(fScale, fScale);
   *     canvas->drawColor(SK_ColorWHITE);
   *     fDom->render(canvas);
   *
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
   * SkISize SVGSrc::size() const {
   *     if (!fDom) {
   *         return {0, 0};
   *     }
   *
   *     return SkSize{fDom->containerSize().width() * fScale, fDom->containerSize().height() * fScale}
   *             .toRound();
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name SVGSrc::name() const { return fName; }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SVGSrc::veto(SinkFlags flags) const {
   *     // No need to test to non-(raster||gpu||vector) or indirect backends.
   *     bool type_ok = flags.type == SinkFlags::kRaster
   *                 || flags.type == SinkFlags::kGPU
   *                 || flags.type == SinkFlags::kVector;
   *
   *     return !type_ok || flags.approach != SinkFlags::kDirect;
   * }
   * ```
   */
  public override fun veto(flags: SinkFlags): Boolean {
    TODO("Implement veto")
  }
}
