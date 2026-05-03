package org.skia.tools

import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkFontMetrics
import org.skia.core.SkGlyph
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkScalerContext
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkVector
import org.skia.memory.SkArenaAlloc
import undefined.GlyphMetrics

/**
 * C++ original:
 * ```cpp
 * class SkTestSVGScalerContext : public SkScalerContext {
 * public:
 *     SkTestSVGScalerContext(TestSVGTypeface& face,
 *                            const SkScalerContextEffects& effects,
 *                            const SkDescriptor* desc)
 *         : SkScalerContext(face, effects, desc)
 *         , fMatrix(fRec.getSingleMatrix())
 *     {
 *         SkScalar upem = this->getTestSVGTypeface()->fUpem;
 *         fMatrix.preScale(1.f / upem, 1.f / upem);
 *     }
 *
 * protected:
 *     TestSVGTypeface* getTestSVGTypeface() const {
 *         return static_cast<TestSVGTypeface*>(this->getTypeface());
 *     }
 *
 *     SkVector computeAdvance(SkGlyphID glyphID) {
 *         auto advance = this->getTestSVGTypeface()->getAdvance(glyphID);
 *         return fMatrix.mapPoint(advance);
 *     }
 *
 *     GlyphMetrics generateMetrics(const SkGlyph& glyph, SkArenaAlloc*) override {
 *         SkGlyphID glyphID = glyph.getGlyphID();
 *         glyphID           = glyphID < this->getTestSVGTypeface()->fGlyphCount ? glyphID : 0;
 *
 *         GlyphMetrics mx(SkMask::kARGB32_Format);
 *         mx.neverRequestPath = true;
 *         mx.advance = this->computeAdvance(glyph.getGlyphID());
 *
 *         TestSVGTypeface::Glyph& glyphData = this->getTestSVGTypeface()->fGlyphs[glyphID];
 *
 *         SkSize containerSize = glyphData.size();
 *         SkRect newBounds = SkRect::MakeXYWH(glyphData.fOrigin.fX,
 *                                            -glyphData.fOrigin.fY,
 *                                             containerSize.fWidth,
 *                                             containerSize.fHeight);
 *         fMatrix.mapRect(&newBounds);
 *         SkScalar dx = SkFixedToScalar(glyph.getSubXFixed());
 *         SkScalar dy = SkFixedToScalar(glyph.getSubYFixed());
 *         newBounds.offset(dx, dy);
 *         newBounds.roundOut(&mx.bounds);
 *         return mx;
 *     }
 *
 *     void generateImage(const SkGlyph& glyph, void* imageBuffer) override {
 *         SkGlyphID glyphID = glyph.getGlyphID();
 *         glyphID           = glyphID < this->getTestSVGTypeface()->fGlyphCount ? glyphID : 0;
 *
 *         SkBitmap bm;
 *         // TODO: this should be SkImageInfo::MakeS32 when that passes all the tests.
 *         bm.installPixels(SkImageInfo::MakeN32(glyph.width(), glyph.height(), kPremul_SkAlphaType),
 *                          imageBuffer, glyph.rowBytes());
 *         bm.eraseColor(0);
 *
 *         TestSVGTypeface::Glyph& glyphData = this->getTestSVGTypeface()->fGlyphs[glyphID];
 *
 *         SkScalar dx = SkFixedToScalar(glyph.getSubXFixed());
 *         SkScalar dy = SkFixedToScalar(glyph.getSubYFixed());
 *
 *         SkCanvas canvas(bm);
 *         canvas.translate(-glyph.left(), -glyph.top());
 *         canvas.translate(dx, dy);
 *         canvas.concat(fMatrix);
 *         canvas.translate(glyphData.fOrigin.fX, -glyphData.fOrigin.fY);
 *
 *         glyphData.render(&canvas);
 *     }
 *
 *     std::optional<SkScalerContext::GeneratedPath> generatePath(const SkGlyph& glyph) override {
 *         // Should never get here since generateMetrics always sets the path to not exist.
 *         SK_ABORT("Path requested, but it should have been indicated that there isn't one.");
 *         return {};
 *     }
 *
 *     struct SVGGlyphDrawable : public SkDrawable {
 *         SkTestSVGScalerContext* fSelf;
 *         SkGlyph fGlyph;
 *         SVGGlyphDrawable(SkTestSVGScalerContext* self, const SkGlyph& glyph)
 *             : fSelf(self), fGlyph(glyph) {}
 *         SkRect onGetBounds() override { return fGlyph.rect();  }
 *         size_t onApproximateBytesUsed() override { return sizeof(SVGGlyphDrawable); }
 *
 *         void onDraw(SkCanvas* canvas) override {
 *             SkGlyphID glyphID = fGlyph.getGlyphID();
 *             glyphID = glyphID < fSelf->getTestSVGTypeface()->fGlyphCount ? glyphID : 0;
 *
 *             TestSVGTypeface::Glyph& glyphData = fSelf->getTestSVGTypeface()->fGlyphs[glyphID];
 *
 *             SkScalar dx = SkFixedToScalar(fGlyph.getSubXFixed());
 *             SkScalar dy = SkFixedToScalar(fGlyph.getSubYFixed());
 *
 *             canvas->translate(dx, dy);
 *             canvas->concat(fSelf->fMatrix);
 *             canvas->translate(glyphData.fOrigin.fX, -glyphData.fOrigin.fY);
 *
 *             glyphData.render(canvas);
 *         }
 *     };
 *     sk_sp<SkDrawable> generateDrawable(const SkGlyph& glyph) override {
 *         return sk_sp<SVGGlyphDrawable>(new SVGGlyphDrawable(this, glyph));
 *     }
 *
 *     void generateFontMetrics(SkFontMetrics* metrics) override {
 *         this->getTestSVGTypeface()->getFontMetrics(metrics);
 *         SkFontPriv::ScaleFontMetrics(metrics, fMatrix.getScaleY());
 *     }
 *
 * private:
 *     SkMatrix fMatrix;
 * }
 * ```
 */
public open class SkTestSVGScalerContext public constructor(
  face: TestSVGTypeface,
  effects: SkScalerContextEffects,
  desc: SkDescriptor?,
) : SkScalerContext(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fMatrix
   * ```
   */
  private var fMatrix: SkMatrix = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * TestSVGTypeface* getTestSVGTypeface() const {
   *         return static_cast<TestSVGTypeface*>(this->getTypeface());
   *     }
   * ```
   */
  protected fun getTestSVGTypeface(): TestSVGTypeface {
    TODO("Implement getTestSVGTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector computeAdvance(SkGlyphID glyphID) {
   *         auto advance = this->getTestSVGTypeface()->getAdvance(glyphID);
   *         return fMatrix.mapPoint(advance);
   *     }
   * ```
   */
  protected fun computeAdvance(glyphID: SkGlyphID): SkVector {
    TODO("Implement computeAdvance")
  }

  /**
   * C++ original:
   * ```cpp
   * GlyphMetrics generateMetrics(const SkGlyph& glyph, SkArenaAlloc*) override {
   *         SkGlyphID glyphID = glyph.getGlyphID();
   *         glyphID           = glyphID < this->getTestSVGTypeface()->fGlyphCount ? glyphID : 0;
   *
   *         GlyphMetrics mx(SkMask::kARGB32_Format);
   *         mx.neverRequestPath = true;
   *         mx.advance = this->computeAdvance(glyph.getGlyphID());
   *
   *         TestSVGTypeface::Glyph& glyphData = this->getTestSVGTypeface()->fGlyphs[glyphID];
   *
   *         SkSize containerSize = glyphData.size();
   *         SkRect newBounds = SkRect::MakeXYWH(glyphData.fOrigin.fX,
   *                                            -glyphData.fOrigin.fY,
   *                                             containerSize.fWidth,
   *                                             containerSize.fHeight);
   *         fMatrix.mapRect(&newBounds);
   *         SkScalar dx = SkFixedToScalar(glyph.getSubXFixed());
   *         SkScalar dy = SkFixedToScalar(glyph.getSubYFixed());
   *         newBounds.offset(dx, dy);
   *         newBounds.roundOut(&mx.bounds);
   *         return mx;
   *     }
   * ```
   */
  protected override fun generateMetrics(glyph: SkGlyph, param1: SkArenaAlloc?): GlyphMetrics {
    TODO("Implement generateMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void generateImage(const SkGlyph& glyph, void* imageBuffer) override {
   *         SkGlyphID glyphID = glyph.getGlyphID();
   *         glyphID           = glyphID < this->getTestSVGTypeface()->fGlyphCount ? glyphID : 0;
   *
   *         SkBitmap bm;
   *         // TODO: this should be SkImageInfo::MakeS32 when that passes all the tests.
   *         bm.installPixels(SkImageInfo::MakeN32(glyph.width(), glyph.height(), kPremul_SkAlphaType),
   *                          imageBuffer, glyph.rowBytes());
   *         bm.eraseColor(0);
   *
   *         TestSVGTypeface::Glyph& glyphData = this->getTestSVGTypeface()->fGlyphs[glyphID];
   *
   *         SkScalar dx = SkFixedToScalar(glyph.getSubXFixed());
   *         SkScalar dy = SkFixedToScalar(glyph.getSubYFixed());
   *
   *         SkCanvas canvas(bm);
   *         canvas.translate(-glyph.left(), -glyph.top());
   *         canvas.translate(dx, dy);
   *         canvas.concat(fMatrix);
   *         canvas.translate(glyphData.fOrigin.fX, -glyphData.fOrigin.fY);
   *
   *         glyphData.render(&canvas);
   *     }
   * ```
   */
  protected override fun generateImage(glyph: SkGlyph, imageBuffer: Unit?) {
    TODO("Implement generateImage")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkScalerContext::GeneratedPath> generatePath(const SkGlyph& glyph) override {
   *         // Should never get here since generateMetrics always sets the path to not exist.
   *         SK_ABORT("Path requested, but it should have been indicated that there isn't one.");
   *         return {};
   *     }
   * ```
   */
  protected override fun generatePath(glyph: SkGlyph): Int {
    TODO("Implement generatePath")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable> generateDrawable(const SkGlyph& glyph) override {
   *         return sk_sp<SVGGlyphDrawable>(new SVGGlyphDrawable(this, glyph));
   *     }
   * ```
   */
  protected override fun generateDrawable(glyph: SkGlyph): SkSp<SkDrawable> {
    TODO("Implement generateDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void generateFontMetrics(SkFontMetrics* metrics) override {
   *         this->getTestSVGTypeface()->getFontMetrics(metrics);
   *         SkFontPriv::ScaleFontMetrics(metrics, fMatrix.getScaleY());
   *     }
   * ```
   */
  protected override fun generateFontMetrics(metrics: SkFontMetrics?) {
    TODO("Implement generateFontMetrics")
  }

  public open class SVGGlyphDrawable public constructor(
    public var fSelf: SkTestSVGScalerContext?,
    public var fGlyph: SkGlyph,
  ) : SkDrawable(TODO()) {
    public constructor(self: SkTestSVGScalerContext?, glyph: SkGlyph) : this() {
      TODO("Implement constructor")
    }

    public override fun onGetBounds(): SkRect {
      TODO("Implement onGetBounds")
    }

    public override fun onApproximateBytesUsed(): ULong {
      TODO("Implement onApproximateBytesUsed")
    }

    public override fun onDraw(canvas: SkCanvas?) {
      TODO("Implement onDraw")
    }
  }
}
