package org.skia.core

import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkShaderBlitter : public SkRasterBlitter {
 * public:
 *     /**
 *       *  The storage for shaderContext is owned by the caller, but the object itself is not.
 *       *  The blitter only ensures that the storage always holds a live object, but it may
 *       *  exchange that object.
 *       */
 *     SkShaderBlitter(const SkPixmap& device, const SkPaint& paint,
 *                     SkShaderBase::Context* shaderContext);
 *     ~SkShaderBlitter() override;
 *
 * protected:
 *     sk_sp<SkShader>         fShader;
 *     SkShaderBase::Context* fShaderContext;
 * }
 * ```
 */
public open class SkShaderBlitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
  shaderContext: SkShaderBase.Context?,
) : SkRasterBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>         fShader
   * ```
   */
  protected var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::Context* fShaderContext
   * ```
   */
  protected var fShaderContext: SkShaderBase.Context? = TODO("Initialize fShaderContext")
}
