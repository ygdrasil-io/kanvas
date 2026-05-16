package org.skia.effects

import kotlin.Char
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class SkPath2DPathEffectImpl : public Sk2DPathEffect {
 * public:
 *     SkPath2DPathEffectImpl(const SkMatrix& m, const SkPath& p) : INHERITED(m), fPath(p) {}
 *
 *     void next(const SkPoint& loc, int u, int v, SkPathBuilder* dst) const override {
 *         dst->addPath(fPath, loc.fX, loc.fY);
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         SkMatrix matrix;
 *         buffer.readMatrix(&matrix);
 *         if (auto path = buffer.readPath()) {
 *             return SkPath2DPathEffect::Make(matrix, *path);
 *         }
 *         return nullptr;
 *     }
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeMatrix(this->getMatrix());
 *         buffer.writePath(fPath);
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *     const char* getTypeName() const override { return "SkPath2DPathEffect"; }
 *
 * private:
 *     SkPath  fPath;
 *
 *     using INHERITED = Sk2DPathEffect;
 * }
 * ```
 */
public open class SkPath2DPathEffectImpl public constructor(
  m: SkMatrix,
  p: SkPath,
) : Sk2DPathEffect(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPath  fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * void next(const SkPoint& loc, int u, int v, SkPathBuilder* dst) const override {
   *         dst->addPath(fPath, loc.fX, loc.fY);
   *     }
   * ```
   */
  public override fun next(
    loc: SkPoint,
    u: Int,
    v: Int,
    dst: SkPathBuilder?,
  ) {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeMatrix(this->getMatrix());
   *         buffer.writePath(fPath);
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
   * const char* getTypeName() const override { return "SkPath2DPathEffect"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
     *         SkMatrix matrix;
     *         buffer.readMatrix(&matrix);
     *         if (auto path = buffer.readPath()) {
     *             return SkPath2DPathEffect::Make(matrix, *path);
     *         }
     *         return nullptr;
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
