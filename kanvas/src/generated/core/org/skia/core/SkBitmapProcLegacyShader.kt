package org.skia.core

import ContextRec
import org.skia.foundation.SkSamplingOptions
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkBitmapProcLegacyShader : public SkShaderBase {
 * private:
 *     friend class SkImageShader;
 *
 *     static Context* MakeContext(const SkShaderBase&, SkTileMode tmx, SkTileMode tmy,
 *                                 const SkSamplingOptions&, const SkImage_Base*,
 *                                 const ContextRec&, SkArenaAlloc* alloc);
 *
 *     using INHERITED = SkShaderBase;
 * }
 * ```
 */
public open class SkBitmapProcLegacyShader : SkShaderBase() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkShaderBase::Context* SkBitmapProcLegacyShader::MakeContext(
     *     const SkShaderBase& shader, SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions& sampling,
     *     const SkImage_Base* image, const ContextRec& rec, SkArenaAlloc* alloc)
     * {
     *     auto totalInverse = rec.fMatrixRec.totalInverse();
     *     if (!totalInverse) {
     *         return nullptr;
     *     }
     *
     *     SkBitmapProcState* state = alloc->make<SkBitmapProcState>(image, tmx, tmy);
     *     if (!state->setup(*totalInverse, rec.fPaintAlpha, sampling)) {
     *         return nullptr;
     *     }
     *     return alloc->make<BitmapProcShaderContext>(shader, rec, state);
     * }
     * ```
     */
    private fun makeContext(
      shader: SkShaderBase,
      tmx: SkTileMode,
      tmy: SkTileMode,
      sampling: SkSamplingOptions,
      image: SkImageBase?,
      rec: ContextRec,
      alloc: SkArenaAlloc?,
    ): Context {
      TODO("Implement makeContext")
    }
  }
}
