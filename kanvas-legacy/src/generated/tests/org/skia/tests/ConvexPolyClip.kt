package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.core.SkPathBuilder
import org.skia.foundation.SkImage
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ConvexPolyClip : public GM {
 * public:
 *     ConvexPolyClip() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("convex_poly_clip"); }
 *
 *     SkISize getISize() override {
 *         // When benchmarking the saveLayer set of draws is skipped.
 *         int w = 435;
 *         if (kBench_Mode != this->getMode()) {
 *             w *= 2;
 *         }
 *         return SkISize::Make(w, 540);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         // On < c++17, emplace_back() returns a void :(
 *         auto emplace_back = [](std::vector<Clip>& clips) -> Clip& {
 *             clips.emplace_back();
 *             return clips.back();
 *         };
 *
 *         emplace_back(fClips).setPath(SkPath::Polygon({{
 *             {  5.f,   5.f},
 *             {100.f,  20.f},
 *             { 15.f, 100.f},
 *         }}, false));
 *
 *         SkPathBuilder hexagon;
 *         constexpr SkScalar kRadius = 45.f;
 *         const SkPoint center = { kRadius, kRadius };
 *         for (int i = 0; i < 6; ++i) {
 *             SkScalar angle = 2 * SK_ScalarPI * i / 6;
 *             SkPoint point = { SkScalarCos(angle), SkScalarSin(angle) };
 *             point.scale(kRadius);
 *             point = center + point;
 *             if (0 == i) {
 *                 hexagon.moveTo(point);
 *             } else {
 *                 hexagon.lineTo(point);
 *             }
 *         }
 *         emplace_back(fClips).setPath(hexagon.snapshot());
 *
 *         SkMatrix scaleM;
 *         scaleM.setScale(1.1f, 0.4f, kRadius, kRadius);
 *         emplace_back(fClips).setPath(hexagon.detach().makeTransform(scaleM));
 *
 *         emplace_back(fClips).setRect(SkRect::MakeXYWH(8.3f, 11.6f, 78.2f, 72.6f));
 *
 *         SkRect rect = SkRect::MakeLTRB(10.f, 12.f, 80.f, 86.f);
 *         SkMatrix rotM;
 *         rotM.setRotate(23.f, rect.centerX(), rect.centerY());
 *         emplace_back(fClips).setPath(SkPath::Rect(rect).makeTransform(rotM));
 *
 *         fImg = make_img(100, 100);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar y = 0;
 *         constexpr SkScalar kMargin = 10.f;
 *
 *         SkPaint bgPaint;
 *         bgPaint.setAlpha(0x15);
 *         SkISize size = canvas->getBaseLayerSize();
 *         canvas->drawImageRect(fImg, SkRect::MakeIWH(size.fWidth, size.fHeight),
 *                               SkSamplingOptions(), &bgPaint);
 *
 *         constexpr char kTxt[] = "Clip Me!";
 *         SkFont         font(ToolUtils::DefaultPortableTypeface(), 23);
 *         SkScalar textW = font.measureText(kTxt, std::size(kTxt)-1, SkTextEncoding::kUTF8);
 *         SkPaint txtPaint;
 *         txtPaint.setColor(SK_ColorDKGRAY);
 *
 *         SkScalar startX = 0;
 *         int testLayers = kBench_Mode != this->getMode();
 *         for (int doLayer = 0; doLayer <= testLayers; ++doLayer) {
 *             for (const Clip& clip : fClips) {
 *                 SkScalar x = startX;
 *                 for (int aa = 0; aa < 2; ++aa) {
 *                     if (doLayer) {
 *                         SkRect bounds;
 *                         clip.getBounds(&bounds);
 *                         bounds.outset(2, 2);
 *                         bounds.offset(x, y);
 *                         canvas->saveLayer(&bounds, nullptr);
 *                     } else {
 *                         canvas->save();
 *                     }
 *                     canvas->translate(x, y);
 *                     clip.setOnCanvas(canvas, SkClipOp::kIntersect, SkToBool(aa));
 *                     canvas->drawImage(fImg, 0, 0);
 *                     canvas->restore();
 *                     x += fImg->width() + kMargin;
 *                 }
 *                 for (int aa = 0; aa < 2; ++aa) {
 *
 *                     SkPaint clipOutlinePaint;
 *                     clipOutlinePaint.setAntiAlias(true);
 *                     clipOutlinePaint.setColor(0x50505050);
 *                     clipOutlinePaint.setStyle(SkPaint::kStroke_Style);
 *                     clipOutlinePaint.setStrokeWidth(0);
 *
 *                     if (doLayer) {
 *                         SkRect bounds;
 *                         clip.getBounds(&bounds);
 *                         bounds.outset(2, 2);
 *                         bounds.offset(x, y);
 *                         canvas->saveLayer(&bounds, nullptr);
 *                     } else {
 *                         canvas->save();
 *                     }
 *                     canvas->translate(x, y);
 *                     SkPath closedClipPath = clip.asClosedPath();
 *                     canvas->drawPath(closedClipPath, clipOutlinePaint);
 *                     clip.setOnCanvas(canvas, SkClipOp::kIntersect, SkToBool(aa));
 *                     canvas->scale(1.f, 1.8f);
 *                     canvas->drawSimpleText(kTxt, std::size(kTxt)-1, SkTextEncoding::kUTF8,
 *                                      0, 1.5f * font.getSize(), font, txtPaint);
 *                     canvas->restore();
 *                     x += textW + 2 * kMargin;
 *                 }
 *                 y += fImg->height() + kMargin;
 *             }
 *             y = 0;
 *             startX += 2 * fImg->width() + SkScalarCeilToInt(2 * textW) + 6 * kMargin;
 *         }
 *     }
 *
 *     bool runAsBench() const override { return true; }
 *
 * private:
 *     class Clip {
 *     public:
 *         enum ClipType {
 *             kNone_ClipType,
 *             kPath_ClipType,
 *             kRect_ClipType
 *         };
 *
 *         Clip () : fClipType(kNone_ClipType) {}
 *
 *         void setOnCanvas(SkCanvas* canvas, SkClipOp op, bool aa) const {
 *             switch (fClipType) {
 *                 case kPath_ClipType:
 *                     canvas->clipPath(fPathBuilder.snapshot(), op, aa);
 *                     break;
 *                 case kRect_ClipType:
 *                     canvas->clipRect(fRect, op, aa);
 *                     break;
 *                 case kNone_ClipType:
 *                     SkDEBUGFAIL("Uninitialized Clip.");
 *                     break;
 *             }
 *         }
 *
 *         SkPath asClosedPath() const {
 *             switch (fClipType) {
 *                 case kPath_ClipType:
 *                     return SkPathBuilder(fPathBuilder).close().detach();
 *                 case kRect_ClipType:
 *                     return SkPath::Rect(fRect);
 *                 case kNone_ClipType:
 *                     SkDEBUGFAIL("Uninitialized Clip.");
 *                     break;
 *             }
 *             return SkPath();
 *         }
 *
 *         void setPath(const SkPath& path) {
 *             fClipType = kPath_ClipType;
 *             fPathBuilder = path;
 *         }
 *
 *         void setRect(const SkRect& rect) {
 *             fClipType = kRect_ClipType;
 *             fRect = rect;
 *             fPathBuilder.reset();
 *         }
 *
 *         ClipType getType() const { return fClipType; }
 *
 *         void getBounds(SkRect* bounds) const {
 *             switch (fClipType) {
 *                 case kPath_ClipType:
 *                     *bounds = fPathBuilder.computeBounds();
 *                     break;
 *                 case kRect_ClipType:
 *                     *bounds = fRect;
 *                     break;
 *                 case kNone_ClipType:
 *                     SkDEBUGFAIL("Uninitialized Clip.");
 *                     break;
 *             }
 *         }
 *
 *     private:
 *         ClipType fClipType;
 *         SkPathBuilder fPathBuilder;
 *         SkRect fRect;
 *     };
 *
 *     std::vector<Clip> fClips;
 *     sk_sp<SkImage>    fImg;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ConvexPolyClip public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<Clip> fClips
   * ```
   */
  private var fClips: Int = TODO("Initialize fClips")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>    fImg
   * ```
   */
  private var fImg: SkSp<SkImage> = TODO("Initialize fImg")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("convex_poly_clip"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         // When benchmarking the saveLayer set of draws is skipped.
   *         int w = 435;
   *         if (kBench_Mode != this->getMode()) {
   *             w *= 2;
   *         }
   *         return SkISize::Make(w, 540);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // On < c++17, emplace_back() returns a void :(
   *         auto emplace_back = [](std::vector<Clip>& clips) -> Clip& {
   *             clips.emplace_back();
   *             return clips.back();
   *         };
   *
   *         emplace_back(fClips).setPath(SkPath::Polygon({{
   *             {  5.f,   5.f},
   *             {100.f,  20.f},
   *             { 15.f, 100.f},
   *         }}, false));
   *
   *         SkPathBuilder hexagon;
   *         constexpr SkScalar kRadius = 45.f;
   *         const SkPoint center = { kRadius, kRadius };
   *         for (int i = 0; i < 6; ++i) {
   *             SkScalar angle = 2 * SK_ScalarPI * i / 6;
   *             SkPoint point = { SkScalarCos(angle), SkScalarSin(angle) };
   *             point.scale(kRadius);
   *             point = center + point;
   *             if (0 == i) {
   *                 hexagon.moveTo(point);
   *             } else {
   *                 hexagon.lineTo(point);
   *             }
   *         }
   *         emplace_back(fClips).setPath(hexagon.snapshot());
   *
   *         SkMatrix scaleM;
   *         scaleM.setScale(1.1f, 0.4f, kRadius, kRadius);
   *         emplace_back(fClips).setPath(hexagon.detach().makeTransform(scaleM));
   *
   *         emplace_back(fClips).setRect(SkRect::MakeXYWH(8.3f, 11.6f, 78.2f, 72.6f));
   *
   *         SkRect rect = SkRect::MakeLTRB(10.f, 12.f, 80.f, 86.f);
   *         SkMatrix rotM;
   *         rotM.setRotate(23.f, rect.centerX(), rect.centerY());
   *         emplace_back(fClips).setPath(SkPath::Rect(rect).makeTransform(rotM));
   *
   *         fImg = make_img(100, 100);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar y = 0;
   *         constexpr SkScalar kMargin = 10.f;
   *
   *         SkPaint bgPaint;
   *         bgPaint.setAlpha(0x15);
   *         SkISize size = canvas->getBaseLayerSize();
   *         canvas->drawImageRect(fImg, SkRect::MakeIWH(size.fWidth, size.fHeight),
   *                               SkSamplingOptions(), &bgPaint);
   *
   *         constexpr char kTxt[] = "Clip Me!";
   *         SkFont         font(ToolUtils::DefaultPortableTypeface(), 23);
   *         SkScalar textW = font.measureText(kTxt, std::size(kTxt)-1, SkTextEncoding::kUTF8);
   *         SkPaint txtPaint;
   *         txtPaint.setColor(SK_ColorDKGRAY);
   *
   *         SkScalar startX = 0;
   *         int testLayers = kBench_Mode != this->getMode();
   *         for (int doLayer = 0; doLayer <= testLayers; ++doLayer) {
   *             for (const Clip& clip : fClips) {
   *                 SkScalar x = startX;
   *                 for (int aa = 0; aa < 2; ++aa) {
   *                     if (doLayer) {
   *                         SkRect bounds;
   *                         clip.getBounds(&bounds);
   *                         bounds.outset(2, 2);
   *                         bounds.offset(x, y);
   *                         canvas->saveLayer(&bounds, nullptr);
   *                     } else {
   *                         canvas->save();
   *                     }
   *                     canvas->translate(x, y);
   *                     clip.setOnCanvas(canvas, SkClipOp::kIntersect, SkToBool(aa));
   *                     canvas->drawImage(fImg, 0, 0);
   *                     canvas->restore();
   *                     x += fImg->width() + kMargin;
   *                 }
   *                 for (int aa = 0; aa < 2; ++aa) {
   *
   *                     SkPaint clipOutlinePaint;
   *                     clipOutlinePaint.setAntiAlias(true);
   *                     clipOutlinePaint.setColor(0x50505050);
   *                     clipOutlinePaint.setStyle(SkPaint::kStroke_Style);
   *                     clipOutlinePaint.setStrokeWidth(0);
   *
   *                     if (doLayer) {
   *                         SkRect bounds;
   *                         clip.getBounds(&bounds);
   *                         bounds.outset(2, 2);
   *                         bounds.offset(x, y);
   *                         canvas->saveLayer(&bounds, nullptr);
   *                     } else {
   *                         canvas->save();
   *                     }
   *                     canvas->translate(x, y);
   *                     SkPath closedClipPath = clip.asClosedPath();
   *                     canvas->drawPath(closedClipPath, clipOutlinePaint);
   *                     clip.setOnCanvas(canvas, SkClipOp::kIntersect, SkToBool(aa));
   *                     canvas->scale(1.f, 1.8f);
   *                     canvas->drawSimpleText(kTxt, std::size(kTxt)-1, SkTextEncoding::kUTF8,
   *                                      0, 1.5f * font.getSize(), font, txtPaint);
   *                     canvas->restore();
   *                     x += textW + 2 * kMargin;
   *                 }
   *                 y += fImg->height() + kMargin;
   *             }
   *             y = 0;
   *             startX += 2 * fImg->width() + SkScalarCeilToInt(2 * textW) + 6 * kMargin;
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  public data class Clip public constructor(
    private var fClipType: org.skia.gpu.Clip.ClipType,
    private var fPathBuilder: SkPathBuilder,
    private var fRect: SkRect,
  ) {
    public fun setOnCanvas(
      canvas: SkCanvas?,
      op: SkClipOp,
      aa: Boolean,
    ) {
      TODO("Implement setOnCanvas")
    }

    public fun asClosedPath(): SkPath {
      TODO("Implement asClosedPath")
    }

    public fun setPath(path: SkPath) {
      TODO("Implement setPath")
    }

    public fun setRect(rect: SkRect) {
      TODO("Implement setRect")
    }

    public fun getType(): org.skia.gpu.Clip.ClipType {
      TODO("Implement getType")
    }

    public fun getBounds(bounds: SkRect?) {
      TODO("Implement getBounds")
    }

    public enum class ClipType {
      kNone_ClipType,
      kPath_ClipType,
      kRect_ClipType,
    }
  }
}
