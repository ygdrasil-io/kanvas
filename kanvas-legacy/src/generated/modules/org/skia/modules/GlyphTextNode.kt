package org.skia.modules

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class GlyphTextNode final : public sksg::GeometryNode {
 * public:
 *     explicit GlyphTextNode(Shaper::ShapedGlyphs&& glyphs) : fGlyphs(std::move(glyphs)) {}
 *
 *     ~GlyphTextNode() override = default;
 *
 *     const Shaper::ShapedGlyphs* glyphs() const { return &fGlyphs; }
 *
 * protected:
 *     SkRect onRevalidate(sksg::InvalidationController*, const SkMatrix&) override {
 *         return fGlyphs.computeBounds(Shaper::ShapedGlyphs::BoundsType::kTight);
 *     }
 *
 *     void onDraw(SkCanvas* canvas, const SkPaint& paint) const override {
 *         fGlyphs.draw(canvas, {0,0}, paint);
 *     }
 *
 *     void onClip(SkCanvas* canvas, bool antiAlias) const override {
 *         canvas->clipPath(this->asPath(), antiAlias);
 *     }
 *
 *     bool onContains(const SkPoint& p) const override {
 *         return this->asPath().contains(p.x(), p.y());
 *     }
 *
 *     SkPath onAsPath() const override {
 *         // TODO
 *         return SkPath();
 *     }
 *
 * private:
 *     const Shaper::ShapedGlyphs fGlyphs;
 * }
 * ```
 */
public class GlyphTextNode public constructor(
  glyphs: Shaper.ShapedGlyphs,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * const Shaper::ShapedGlyphs fGlyphs
   * ```
   */
  private val fGlyphs: Shaper.ShapedGlyphs = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * const Shaper::ShapedGlyphs* glyphs() const { return &fGlyphs; }
   * ```
   */
  public fun glyphs(): Shaper.ShapedGlyphs {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController*, const SkMatrix&) override {
   *         return fGlyphs.computeBounds(Shaper::ShapedGlyphs::BoundsType::kTight);
   *     }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): SkRect {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas, const SkPaint& paint) const override {
   *         fGlyphs.draw(canvas, {0,0}, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onClip(SkCanvas* canvas, bool antiAlias) const override {
   *         canvas->clipPath(this->asPath(), antiAlias);
   *     }
   * ```
   */
  protected override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onContains(const SkPoint& p) const override {
   *         return this->asPath().contains(p.x(), p.y());
   *     }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath onAsPath() const override {
   *         // TODO
   *         return SkPath();
   *     }
   * ```
   */
  protected override fun onAsPath(): SkPath {
    TODO("Implement onAsPath")
  }
}
