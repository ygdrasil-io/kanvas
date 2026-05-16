package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkClipStack
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class WindowRectanglesGM : public GM {
 * private:
 *     DrawResult coverClipStack(const SkClipStack&, SkCanvas*, SkString* errorMsg);
 *
 *     SkISize getISize() override { return SkISize::Make(kDeviceRect.width(), kDeviceRect.height()); }
 *     SkString getName() const override { return SkString("windowrectangles"); }
 *     DrawResult onDraw(SkCanvas*, SkString* errorMsg) override;
 * }
 * ```
 */
public open class WindowRectanglesGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * DrawResult WindowRectanglesGM::coverClipStack(const SkClipStack& stack, SkCanvas* canvas,
   *                                                 SkString* errorMsg) {
   *     SkPaint paint;
   *     paint.setColor(0xff00aa80);
   *
   *     // Set up the canvas's clip to match our SkClipStack.
   *     SkClipStack::Iter iter(stack, SkClipStack::Iter::kBottom_IterStart);
   *     for (const SkClipStack::Element* element = iter.next(); element; element = iter.next()) {
   *         SkASSERT(!element->isReplaceOp());
   *         SkClipOp op = element->getOp();
   *         bool isAA = element->isAA();
   *         switch (element->getDeviceSpaceType()) {
   *             case SkClipStack::Element::DeviceSpaceType::kShader:
   *                 canvas->clipShader(element->refShader(), op);
   *                 break;
   *             case SkClipStack::Element::DeviceSpaceType::kPath:
   *                 canvas->clipPath(element->getDeviceSpacePath(), op, isAA);
   *                 break;
   *             case SkClipStack::Element::DeviceSpaceType::kRRect:
   *                 canvas->clipRRect(element->getDeviceSpaceRRect(), op, isAA);
   *                 break;
   *             case SkClipStack::Element::DeviceSpaceType::kRect:
   *                 canvas->clipRect(element->getDeviceSpaceRect(), op, isAA);
   *                 break;
   *             case SkClipStack::Element::DeviceSpaceType::kEmpty:
   *                 canvas->clipRect({ 0, 0, 0, 0 }, SkClipOp::kIntersect, false);
   *                 break;
   *         }
   *     }
   *
   *     canvas->drawRect(SkRect::Make(kCoverRect), paint);
   *     return DrawResult::kOk;
   * }
   * ```
   */
  private fun coverClipStack(
    stack: SkClipStack,
    canvas: SkCanvas?,
    errorMsg: String?,
  ): DrawResult {
    TODO("Implement coverClipStack")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kDeviceRect.width(), kDeviceRect.height()); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("windowrectangles"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult WindowRectanglesGM::onDraw(SkCanvas* canvas, SkString* errorMsg) {
   *     ToolUtils::draw_checkerboard(canvas, 0xffffffff, 0xffc6c3c6, 25);
   *     SkClipStack stack;
   *     stack.clipRect(SkRect::MakeXYWH(370.75, 80.25, 149, 100), SkMatrix::I(),
   *                    SkClipOp::kDifference, false);
   *     stack.clipRect(SkRect::MakeXYWH(80.25, 420.75, 150, 100), SkMatrix::I(),
   *                    SkClipOp::kDifference, true);
   *     stack.clipRRect(SkRRect::MakeRectXY(SkRect::MakeXYWH(200, 200, 200, 200), 60, 45),
   *                     SkMatrix::I(), SkClipOp::kDifference, true);
   *
   *     SkRRect nine;
   *     nine.setNinePatch(SkRect::MakeXYWH(550 - 30.25 - 100, 370.75, 100, 150), 12, 35, 23, 20);
   *     stack.clipRRect(nine, SkMatrix::I(), SkClipOp::kDifference, true);
   *
   *     SkRRect complx;
   *     SkVector complxRadii[4] = {{6, 4}, {8, 12}, {16, 24}, {48, 32}};
   *     complx.setRectRadii(SkRect::MakeXYWH(80.25, 80.75, 100, 149), complxRadii);
   *     stack.clipRRect(complx, SkMatrix::I(), SkClipOp::kDifference, false);
   *
   *     return this->coverClipStack(stack, canvas, errorMsg);
   * }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
