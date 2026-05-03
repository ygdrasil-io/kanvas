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
 * class RootDrawable : public SkDrawable {
 * public:
 *     RootDrawable(uint32_t a, uint32_t b, uint32_t c, uint32_t d, const SkPaint& paint,
 *                    uint32_t e, uint32_t f, uint32_t g, uint32_t h, SkDrawable* drawable)
 *         : fCompoundDrawable(new CompoundDrawable(a, b, c, d, paint))
 *         , fIntDrawable(new IntDrawable(e, f, g, h))
 *         , fDrawable(SkRef(drawable))
 *     {}
 *
 *     RootDrawable(CompoundDrawable* compoundDrawable, IntDrawable* intDrawable,
 *             SkDrawable* drawable)
 *         : fCompoundDrawable(SkRef(compoundDrawable))
 *         , fIntDrawable(SkRef(intDrawable))
 *         , fDrawable(SkRef(drawable))
 *     {}
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeFlattenable(fCompoundDrawable.get());
 *         buffer.writeFlattenable(fIntDrawable.get());
 *         buffer.writeFlattenable(fDrawable.get());
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         sk_sp<SkFlattenable> compoundDrawable(
 *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
 *         SkASSERT(compoundDrawable);
 *         SkASSERT(!strcmp("CompoundDrawable", compoundDrawable->getTypeName()));
 *
 *         sk_sp<SkFlattenable> intDrawable(
 *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
 *         SkASSERT(intDrawable);
 *         SkASSERT(!strcmp("IntDrawable", intDrawable->getTypeName()));
 *
 *         sk_sp<SkFlattenable> drawable(
 *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
 *         SkASSERT(drawable);
 *
 *         return sk_sp<RootDrawable>(new RootDrawable((CompoundDrawable*) compoundDrawable.get(),
 *                                                     (IntDrawable*) intDrawable.get(),
 *                                                     (SkDrawable*) drawable.get()));
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *
 *     CompoundDrawable* compoundDrawable() const { return fCompoundDrawable.get(); }
 *     IntDrawable* intDrawable() const { return fIntDrawable.get(); }
 *     SkDrawable* drawable() const { return fDrawable.get(); }
 *
 *     const char* getTypeName() const override { return "RootDrawable"; }
 *
 * protected:
 *     SkRect onGetBounds() override { return SkRect::MakeEmpty(); }
 *     void onDraw(SkCanvas*) override {}
 *
 * private:
 *     sk_sp<CompoundDrawable> fCompoundDrawable;
 *     sk_sp<IntDrawable>      fIntDrawable;
 *     sk_sp<SkDrawable>       fDrawable;
 * }
 * ```
 */
public open class RootDrawable public constructor(
  a: UInt,
  b: UInt,
  c: UInt,
  d: UInt,
  paint: SkPaint,
  e: UInt,
  f: UInt,
  g: UInt,
  h: UInt,
  drawable: SkDrawable?,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<CompoundDrawable> fCompoundDrawable
   * ```
   */
  private var fCompoundDrawable: SkSp<CompoundDrawable> = TODO("Initialize fCompoundDrawable")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<IntDrawable>      fIntDrawable
   * ```
   */
  private var fIntDrawable: SkSp<IntDrawable> = TODO("Initialize fIntDrawable")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable>       fDrawable
   * ```
   */
  private var fDrawable: SkSp<SkDrawable> = TODO("Initialize fDrawable")

  /**
   * C++ original:
   * ```cpp
   * RootDrawable(uint32_t a, uint32_t b, uint32_t c, uint32_t d, const SkPaint& paint,
   *                    uint32_t e, uint32_t f, uint32_t g, uint32_t h, SkDrawable* drawable)
   *         : fCompoundDrawable(new CompoundDrawable(a, b, c, d, paint))
   *         , fIntDrawable(new IntDrawable(e, f, g, h))
   *         , fDrawable(SkRef(drawable))
   *     {}
   * ```
   */
  public constructor(
    compoundDrawable: CompoundDrawable?,
    intDrawable: IntDrawable?,
    drawable: SkDrawable?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeFlattenable(fCompoundDrawable.get());
   *         buffer.writeFlattenable(fIntDrawable.get());
   *         buffer.writeFlattenable(fDrawable.get());
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
   * CompoundDrawable* compoundDrawable() const { return fCompoundDrawable.get(); }
   * ```
   */
  public fun compoundDrawable(): CompoundDrawable {
    TODO("Implement compoundDrawable")
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
   * SkDrawable* drawable() const { return fDrawable.get(); }
   * ```
   */
  public fun drawable(): SkDrawable {
    TODO("Implement drawable")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "RootDrawable"; }
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
     *         sk_sp<SkFlattenable> compoundDrawable(
     *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
     *         SkASSERT(compoundDrawable);
     *         SkASSERT(!strcmp("CompoundDrawable", compoundDrawable->getTypeName()));
     *
     *         sk_sp<SkFlattenable> intDrawable(
     *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
     *         SkASSERT(intDrawable);
     *         SkASSERT(!strcmp("IntDrawable", intDrawable->getTypeName()));
     *
     *         sk_sp<SkFlattenable> drawable(
     *                 buffer.readFlattenable(SkFlattenable::kSkDrawable_Type));
     *         SkASSERT(drawable);
     *
     *         return sk_sp<RootDrawable>(new RootDrawable((CompoundDrawable*) compoundDrawable.get(),
     *                                                     (IntDrawable*) intDrawable.get(),
     *                                                     (SkDrawable*) drawable.get()));
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
