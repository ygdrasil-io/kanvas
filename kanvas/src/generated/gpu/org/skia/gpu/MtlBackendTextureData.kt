package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import undefined.AnyBackendTextureData
import undefined.CFTypeRef

/**
 * C++ original:
 * ```cpp
 * class MtlBackendTextureData final : public BackendTextureData {
 * public:
 *     MtlBackendTextureData(CFTypeRef tex) : fMtlTexture(tex) {}
 *
 * #if defined(SK_DEBUG)
 *     skgpu::BackendApi type() const override { return skgpu::BackendApi::kMetal; }
 * #endif
 *
 *     CFTypeRef texture() const { return fMtlTexture; }
 *
 * private:
 *     CFTypeRef fMtlTexture;
 *
 *     void copyTo(AnyBackendTextureData& dstData) const override {
 *         // Don't assert that dstData is a metal type because it could be
 *         // uninitialized and that assert would fail.
 *         dstData.emplace<MtlBackendTextureData>(fMtlTexture);
 *     }
 *
 *     bool equal(const BackendTextureData* that) const override {
 *         SkASSERT(!that || that->type() == skgpu::BackendApi::kMetal);
 *         if (auto otherMtl = static_cast<const MtlBackendTextureData*>(that)) {
 *             return fMtlTexture == otherMtl->fMtlTexture;
 *         }
 *         return false;
 *     }
 * }
 * ```
 */
public class MtlBackendTextureData public constructor(
  tex: CFTypeRef,
) : BackendTextureData() {
  /**
   * C++ original:
   * ```cpp
   * CFTypeRef fMtlTexture
   * ```
   */
  private var fMtlTexture: Int = TODO("Initialize fMtlTexture")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi type() const override { return skgpu::BackendApi::kMetal; }
   * ```
   */
  public override fun type(): BackendApi {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * CFTypeRef texture() const { return fMtlTexture; }
   * ```
   */
  public fun texture(): Int {
    TODO("Implement texture")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyTo(AnyBackendTextureData& dstData) const override {
   *         // Don't assert that dstData is a metal type because it could be
   *         // uninitialized and that assert would fail.
   *         dstData.emplace<MtlBackendTextureData>(fMtlTexture);
   *     }
   * ```
   */
  public override fun copyTo(dstData: AnyBackendTextureData) {
    TODO("Implement copyTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equal(const BackendTextureData* that) const override {
   *         SkASSERT(!that || that->type() == skgpu::BackendApi::kMetal);
   *         if (auto otherMtl = static_cast<const MtlBackendTextureData*>(that)) {
   *             return fMtlTexture == otherMtl->fMtlTexture;
   *         }
   *         return false;
   *     }
   * ```
   */
  public override fun equal(that: BackendTextureData?): Boolean {
    TODO("Implement equal")
  }
}
