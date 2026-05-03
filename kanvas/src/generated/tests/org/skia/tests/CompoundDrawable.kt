package org.skia.tests

import kotlin.Char
import kotlin.UInt
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
 * class CompoundDrawable : public SkDrawable {
 * public:
 *     CompoundDrawable(uint32_t a, uint32_t b, uint32_t c, uint32_t d, const SkPaint& paint)
 *         : fIntDrawable(new IntDrawable(a, b, c, d))
 *         , fPaintDrawable(new PaintDrawable(paint))
 *     {}
 *
 *     CompoundDrawable(IntDrawable* intDrawable, PaintDrawable* paintDrawable)
 *         : fIntDrawable(SkRef(intDrawable))
 *         , fPaintDrawable(SkRef(paintDrawable))
 *     {}
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeFlattenable(fIntDrawable.get());
 *         buffer.writeFlattenable(fPaintDrawable.get());
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         sk_sp<SkFlattenable> intDrawable(
 *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
 *         SkASSERT(intDrawable);
 *         SkASSERT(!strcmp("IntDrawable", intDrawable->getTypeName()));
 *
 *         sk_sp<SkFlattenable> paintDrawable(
 *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
 *         SkASSERT(paintDrawable);
 *         SkASSERT(!strcmp("PaintDrawable", paintDrawable->getTypeName()));
 *
 *         return sk_sp<CompoundDrawable>(new CompoundDrawable((IntDrawable*) intDrawable.get(),
 *                                                             (PaintDrawable*) paintDrawable.get()));
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *
 *     IntDrawable* intDrawable() const { return fIntDrawable.get(); }
 *     PaintDrawable* paintDrawable() const { return fPaintDrawable.get(); }
 *
 *     const char* getTypeName() const override { return "CompoundDrawable"; }
 *
 * protected:
 *     SkRect onGetBounds() override { return SkRect::MakeEmpty(); }
 *     void onDraw(SkCanvas*) override {}
 *
 * private:
 *     sk_sp<IntDrawable>   fIntDrawable;
 *     sk_sp<PaintDrawable> fPaintDrawable;
 * }
 * ```
 */
public open class CompoundDrawable public constructor(
  a: UInt,
  b: UInt,
  c: UInt,
  d: UInt,
  paint: SkPaint,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<IntDrawable>   fIntDrawable
   * ```
   */
  private var fIntDrawable: SkSp<IntDrawable> = TODO("Initialize fIntDrawable")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PaintDrawable> fPaintDrawable
   * ```
   */
  private var fPaintDrawable: SkSp<PaintDrawable> = TODO("Initialize fPaintDrawable")

  /**
   * C++ original:
   * ```cpp
   * CompoundDrawable(uint32_t a, uint32_t b, uint32_t c, uint32_t d, const SkPaint& paint)
   *         : fIntDrawable(new IntDrawable(a, b, c, d))
   *         , fPaintDrawable(new PaintDrawable(paint))
   *     {}
   * ```
   */
  public constructor(intDrawable: IntDrawable?, paintDrawable: PaintDrawable?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeFlattenable(fIntDrawable.get());
   *         buffer.writeFlattenable(fPaintDrawable.get());
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
   * IntDrawable* intDrawable() const { return fIntDrawable.get(); }
   * ```
   */
  public fun intDrawable(): IntDrawable {
    TODO("Implement intDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintDrawable* paintDrawable() const { return fPaintDrawable.get(); }
   * ```
   */
  public fun paintDrawable(): PaintDrawable {
    TODO("Implement paintDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "CompoundDrawable"; }
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
     *         sk_sp<SkFlattenable> intDrawable(
     *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
     *         SkASSERT(intDrawable);
     *         SkASSERT(!strcmp("IntDrawable", intDrawable->getTypeName()));
     *
     *         sk_sp<SkFlattenable> paintDrawable(
     *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
     *         SkASSERT(paintDrawable);
     *         SkASSERT(!strcmp("PaintDrawable", paintDrawable->getTypeName()));
     *
     *         return sk_sp<CompoundDrawable>(new CompoundDrawable((IntDrawable*) intDrawable.get(),
     *                                                             (PaintDrawable*) paintDrawable.get()));
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
