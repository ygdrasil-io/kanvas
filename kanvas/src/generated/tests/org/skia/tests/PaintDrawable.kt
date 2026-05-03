package org.skia.tests

import kotlin.Char
import org.skia.core.SkCanvas
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class PaintDrawable : public SkDrawable {
 * public:
 *     PaintDrawable(const SkPaint& paint)
 *         : fPaint(paint)
 *     {}
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writePaint(fPaint);
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         return sk_sp<PaintDrawable>(new PaintDrawable(buffer.readPaint()));
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *
 *     const SkPaint& paint() const { return fPaint; }
 *
 *     const char* getTypeName() const override { return "PaintDrawable"; }
 *
 * protected:
 *     SkRect onGetBounds() override { return SkRect::MakeEmpty(); }
 *     void onDraw(SkCanvas*) override {}
 *
 * private:
 *     SkPaint fPaint;
 * }
 * ```
 */
public open class PaintDrawable public constructor(
  paint: SkPaint,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * SkPaint fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writePaint(fPaint);
   *     }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return CreateProc; }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPaint& paint() const { return fPaint; }
   * ```
   */
  public fun paint(): SkPaint {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "PaintDrawable"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onGetBounds() override { return SkRect::MakeEmpty(); }
   * ```
   */
  protected override fun onGetBounds(): SkRect {
    TODO("Implement onGetBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas*) override {}
   * ```
   */
  protected override fun onDraw(param0: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
     *         return sk_sp<PaintDrawable>(new PaintDrawable(buffer.readPaint()));
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
