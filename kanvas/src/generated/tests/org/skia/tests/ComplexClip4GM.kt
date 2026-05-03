package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ComplexClip4GM : public GM {
 * public:
 *   ComplexClip4GM(bool aaclip)
 *     : fDoAAClip(aaclip) {
 *         this->setBGColor(0xFFDEDFDE);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("complexclip4_%s",
 *                    fDoAAClip ? "aa" : "bw");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(970, 780); }
 *
 *     // Android Framework will still support the legacy kReplace SkClipOp on older devices, so
 *     // this represents how to do so while also respecting the device restriction using the newer
 *     // androidFramework_resetClip() API.
 *     void emulateDeviceRestriction(SkCanvas* canvas, const SkIRect& deviceRestriction) {
 *         // TODO(michaelludwig): It may make more sense for device clip restriction to move on to
 *         // the SkSurface (which would let this GM draw correctly in viewer).
 *         canvas->androidFramework_setDeviceClipRestriction(deviceRestriction);
 *     }
 *
 *     void emulateClipRectReplace(SkCanvas* canvas,
 *                                 const SkRect& clipRect,
 *                                 bool aa) {
 *         SkCanvasPriv::ResetClip(canvas);
 *         canvas->clipRect(clipRect, SkClipOp::kIntersect, aa);
 *     }
 *
 *     void emulateClipRRectReplace(SkCanvas* canvas,
 *                                  const SkRRect& clipRRect,
 *                                  bool aa) {
 *         SkCanvasPriv::ResetClip(canvas);
 *         canvas->clipRRect(clipRRect, SkClipOp::kIntersect, aa);
 *     }
 *
 *     void emulateClipPathReplace(SkCanvas* canvas,
 *                                 const SkPath& path,
 *                                 bool aa) {
 *         SkCanvasPriv::ResetClip(canvas);
 *         canvas->clipPath(path, SkClipOp::kIntersect, aa);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint p;
 *         p.setAntiAlias(fDoAAClip);
 *         p.setColor(SK_ColorYELLOW);
 *
 *         canvas->save();
 *             // draw a yellow rect through a rect clip
 *             canvas->save();
 *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(100, 100, 300, 300));
 *                 canvas->drawColor(SK_ColorGREEN);
 *                 emulateClipRectReplace(canvas, SkRect::MakeLTRB(100, 200, 400, 500), fDoAAClip);
 *                 canvas->drawRect(SkRect::MakeLTRB(100, 200, 400, 500), p);
 *             canvas->restore();
 *
 *             // draw a yellow rect through a diamond clip
 *             canvas->save();
 *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(500, 100, 800, 300));
 *                 canvas->drawColor(SK_ColorGREEN);
 *
 *                 SkPath pathClip = SkPath::Polygon({{
 *                     {650, 200},
 *                     {900, 300},
 *                     {650, 400},
 *                     {650, 300},
 *                 }}, true);
 *                 emulateClipPathReplace(canvas, pathClip, fDoAAClip);
 *                 canvas->drawRect(SkRect::MakeLTRB(500, 200, 900, 500), p);
 *             canvas->restore();
 *
 *             // draw a yellow rect through a round rect clip
 *             canvas->save();
 *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(500, 500, 800, 700));
 *                 canvas->drawColor(SK_ColorGREEN);
 *
 *                 emulateClipRRectReplace(
 *                         canvas, SkRRect::MakeOval(SkRect::MakeLTRB(500, 600, 900, 750)), fDoAAClip);
 *                 canvas->drawRect(SkRect::MakeLTRB(500, 600, 900, 750), p);
 *             canvas->restore();
 *
 *             // fill the clip with yellow color showing that androidFramework_replaceClip is
 *             // in device space
 *             canvas->save();
 *                 canvas->clipRect(SkRect::MakeLTRB(100, 400, 300, 750),
 *                                  SkClipOp::kIntersect, fDoAAClip);
 *                 canvas->drawColor(SK_ColorGREEN);
 *                 // should not affect the device-space clip
 *                 canvas->rotate(20.f);
 *                 canvas->translate(50.f, 50.f);
 *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(150, 450, 250, 700));
 *                 canvas->drawColor(SK_ColorYELLOW);
 *             canvas->restore();
 *
 *         canvas->restore();
 *     }
 * private:
 *     bool    fDoAAClip;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ComplexClip4GM public constructor(
  aaclip: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool    fDoAAClip
   * ```
   */
  private var fDoAAClip: Boolean = TODO("Initialize fDoAAClip")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("complexclip4_%s",
   *                    fDoAAClip ? "aa" : "bw");
   *         return str;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(970, 780); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void emulateDeviceRestriction(SkCanvas* canvas, const SkIRect& deviceRestriction) {
   *         // TODO(michaelludwig): It may make more sense for device clip restriction to move on to
   *         // the SkSurface (which would let this GM draw correctly in viewer).
   *         canvas->androidFramework_setDeviceClipRestriction(deviceRestriction);
   *     }
   * ```
   */
  protected fun emulateDeviceRestriction(canvas: SkCanvas?, deviceRestriction: SkIRect) {
    TODO("Implement emulateDeviceRestriction")
  }

  /**
   * C++ original:
   * ```cpp
   * void emulateClipRectReplace(SkCanvas* canvas,
   *                                 const SkRect& clipRect,
   *                                 bool aa) {
   *         SkCanvasPriv::ResetClip(canvas);
   *         canvas->clipRect(clipRect, SkClipOp::kIntersect, aa);
   *     }
   * ```
   */
  protected fun emulateClipRectReplace(
    canvas: SkCanvas?,
    clipRect: SkRect,
    aa: Boolean,
  ) {
    TODO("Implement emulateClipRectReplace")
  }

  /**
   * C++ original:
   * ```cpp
   * void emulateClipRRectReplace(SkCanvas* canvas,
   *                                  const SkRRect& clipRRect,
   *                                  bool aa) {
   *         SkCanvasPriv::ResetClip(canvas);
   *         canvas->clipRRect(clipRRect, SkClipOp::kIntersect, aa);
   *     }
   * ```
   */
  protected fun emulateClipRRectReplace(
    canvas: SkCanvas?,
    clipRRect: SkRRect,
    aa: Boolean,
  ) {
    TODO("Implement emulateClipRRectReplace")
  }

  /**
   * C++ original:
   * ```cpp
   * void emulateClipPathReplace(SkCanvas* canvas,
   *                                 const SkPath& path,
   *                                 bool aa) {
   *         SkCanvasPriv::ResetClip(canvas);
   *         canvas->clipPath(path, SkClipOp::kIntersect, aa);
   *     }
   * ```
   */
  protected fun emulateClipPathReplace(
    canvas: SkCanvas?,
    path: SkPath,
    aa: Boolean,
  ) {
    TODO("Implement emulateClipPathReplace")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint p;
   *         p.setAntiAlias(fDoAAClip);
   *         p.setColor(SK_ColorYELLOW);
   *
   *         canvas->save();
   *             // draw a yellow rect through a rect clip
   *             canvas->save();
   *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(100, 100, 300, 300));
   *                 canvas->drawColor(SK_ColorGREEN);
   *                 emulateClipRectReplace(canvas, SkRect::MakeLTRB(100, 200, 400, 500), fDoAAClip);
   *                 canvas->drawRect(SkRect::MakeLTRB(100, 200, 400, 500), p);
   *             canvas->restore();
   *
   *             // draw a yellow rect through a diamond clip
   *             canvas->save();
   *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(500, 100, 800, 300));
   *                 canvas->drawColor(SK_ColorGREEN);
   *
   *                 SkPath pathClip = SkPath::Polygon({{
   *                     {650, 200},
   *                     {900, 300},
   *                     {650, 400},
   *                     {650, 300},
   *                 }}, true);
   *                 emulateClipPathReplace(canvas, pathClip, fDoAAClip);
   *                 canvas->drawRect(SkRect::MakeLTRB(500, 200, 900, 500), p);
   *             canvas->restore();
   *
   *             // draw a yellow rect through a round rect clip
   *             canvas->save();
   *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(500, 500, 800, 700));
   *                 canvas->drawColor(SK_ColorGREEN);
   *
   *                 emulateClipRRectReplace(
   *                         canvas, SkRRect::MakeOval(SkRect::MakeLTRB(500, 600, 900, 750)), fDoAAClip);
   *                 canvas->drawRect(SkRect::MakeLTRB(500, 600, 900, 750), p);
   *             canvas->restore();
   *
   *             // fill the clip with yellow color showing that androidFramework_replaceClip is
   *             // in device space
   *             canvas->save();
   *                 canvas->clipRect(SkRect::MakeLTRB(100, 400, 300, 750),
   *                                  SkClipOp::kIntersect, fDoAAClip);
   *                 canvas->drawColor(SK_ColorGREEN);
   *                 // should not affect the device-space clip
   *                 canvas->rotate(20.f);
   *                 canvas->translate(50.f, 50.f);
   *                 emulateDeviceRestriction(canvas, SkIRect::MakeLTRB(150, 450, 250, 700));
   *                 canvas->drawColor(SK_ColorYELLOW);
   *             canvas->restore();
   *
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
