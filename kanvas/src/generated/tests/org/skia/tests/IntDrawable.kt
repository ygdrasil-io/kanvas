package org.skia.tests

import kotlin.Char
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class IntDrawable : public SkDrawable {
 * public:
 *     IntDrawable(uint32_t a, uint32_t b, uint32_t c, uint32_t d)
 *         : fA(a)
 *         , fB(b)
 *         , fC(c)
 *         , fD(d)
 *     {}
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeUInt(fA);
 *         buffer.writeUInt(fB);
 *         buffer.writeUInt(fC);
 *         buffer.writeUInt(fD);
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         uint32_t a = buffer.readUInt();
 *         uint32_t b = buffer.readUInt();
 *         uint32_t c = buffer.readUInt();
 *         uint32_t d = buffer.readUInt();
 *         return sk_sp<IntDrawable>(new IntDrawable(a, b, c, d));
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *
 *     uint32_t a() const { return fA; }
 *     uint32_t b() const { return fB; }
 *     uint32_t c() const { return fC; }
 *     uint32_t d() const { return fD; }
 *
 *     const char* getTypeName() const override { return "IntDrawable"; }
 *
 * protected:
 *     SkRect onGetBounds() override { return SkRect::MakeEmpty(); }
 *     void onDraw(SkCanvas*) override {}
 *
 * private:
 *     uint32_t fA;
 *     uint32_t fB;
 *     uint32_t fC;
 *     uint32_t fD;
 * }
 * ```
 */
public open class IntDrawable public constructor(
  a: UInt,
  b: UInt,
  c: UInt,
  d: UInt,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * uint32_t fA
   * ```
   */
  private var fA: UInt = TODO("Initialize fA")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fB
   * ```
   */
  private var fB: UInt = TODO("Initialize fB")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fC
   * ```
   */
  private var fC: UInt = TODO("Initialize fC")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fD
   * ```
   */
  private var fD: UInt = TODO("Initialize fD")

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeUInt(fA);
   *         buffer.writeUInt(fB);
   *         buffer.writeUInt(fC);
   *         buffer.writeUInt(fD);
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
   * uint32_t a() const { return fA; }
   * ```
   */
  public fun a(): UInt {
    TODO("Implement a")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t b() const { return fB; }
   * ```
   */
  public fun b(): UInt {
    TODO("Implement b")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t c() const { return fC; }
   * ```
   */
  public fun c(): UInt {
    TODO("Implement c")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t d() const { return fD; }
   * ```
   */
  public fun d(): UInt {
    TODO("Implement d")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "IntDrawable"; }
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
     *         uint32_t a = buffer.readUInt();
     *         uint32_t b = buffer.readUInt();
     *         uint32_t c = buffer.readUInt();
     *         uint32_t d = buffer.readUInt();
     *         return sk_sp<IntDrawable>(new IntDrawable(a, b, c, d));
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
