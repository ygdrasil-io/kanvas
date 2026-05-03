package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class CoverageMaskShape {
 *     using half2 = skvx::half2;
 *     using int2 = skvx::int2;
 *
 * public:
 *     struct MaskInfo {
 *         // The texture-relative integer UV coordinates of the top-left corner of this shape's
 *         // coverage mask bounds. This will include the rounded out transformed device space bounds
 *         // of the shape plus a 1-pixel border.
 *         half2 fTextureOrigin;
 *
 *         // The width and height of the bounds of the coverage mask shape in device coordinates. This
 *         // includes the rounded out transformed device space bounds of the shape + a 1-pixel border
 *         // added for AA.
 *         half2 fMaskSize;
 *     };
 *
 *     CoverageMaskShape() = default;
 *     CoverageMaskShape(const Shape& shape,
 *                       sk_sp<TextureProxy> proxy,
 *                       const SkM44& deviceToLocal,
 *                       const MaskInfo& maskInfo)
 *             : fTextureProxy(std::move(proxy))
 *             , fDeviceToLocal(deviceToLocal)
 *             , fInverted(shape.inverted())
 *             , fMaskInfo(maskInfo) {
 *         SkASSERT(fTextureProxy);
 *     }
 *     CoverageMaskShape(const CoverageMaskShape&) = default;
 *
 *     ~CoverageMaskShape() = default;
 *
 *     // NOTE: None of the geometry types benefit from move semantics, so we don't bother
 *     // defining a move assignment operator for CoverageMaskShape.
 *     CoverageMaskShape& operator=(CoverageMaskShape&&) = delete;
 *     CoverageMaskShape& operator=(const CoverageMaskShape&) = default;
 *
 *     // Returns the mask-space bounds of the clipped coverage mask shape. For inverse fills this
 *     // is different from the actual draw bounds stored in the Clip.
 *     Rect bounds() const {
 *         return Rect(0.f, 0.f, (float) this->maskSize().x(), (float) this->maskSize().y());
 *     }
 *
 *     // The inverse local-to-device matrix.
 *     const SkM44& deviceToLocal() const { return fDeviceToLocal; }
 *
 *     // The texture-relative integer UV coordinates of the top-left corner of this shape's
 *     // coverage mask bounds.
 *     const half2& textureOrigin() const { return fMaskInfo.fTextureOrigin; }
 *
 *     // The width and height of the bounds of the coverage mask shape in device coordinates.
 *     const half2& maskSize() const { return fMaskInfo.fMaskSize; }
 *
 *     // The texture that the shape will be rendered to.
 *     const TextureProxy* textureProxy() const { return fTextureProxy.get(); }
 *
 *     // Whether or not the shape will be painted according to an inverse fill rule.
 *     bool inverted() const { return fInverted; }
 *
 * private:
 *     // We store a ref to the texture proxy to keep it alive.
 *     // TODO: switch to raw pointer once textures/uniforms are extracted in Device::drawGeometry.
 *     sk_sp<TextureProxy> fTextureProxy;
 *     SkM44 fDeviceToLocal;
 *     bool fInverted;
 *     MaskInfo fMaskInfo;
 * }
 * ```
 */
public data class CoverageMaskShape public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTextureProxy
   * ```
   */
  private var fTextureProxy: Int,
  /**
   * C++ original:
   * ```cpp
   * SkM44 fDeviceToLocal
   * ```
   */
  private var fDeviceToLocal: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fInverted
   * ```
   */
  private var fInverted: Boolean,
  /**
   * C++ original:
   * ```cpp
   * MaskInfo fMaskInfo
   * ```
   */
  private var fMaskInfo: MaskInfo,
) {
  /**
   * C++ original:
   * ```cpp
   * CoverageMaskShape& operator=(CoverageMaskShape&&) = delete
   * ```
   */
  public fun assign(param0: CoverageMaskShape) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * CoverageMaskShape& operator=(const CoverageMaskShape&) = default
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect bounds() const {
   *         return Rect(0.f, 0.f, (float) this->maskSize().x(), (float) this->maskSize().y());
   *     }
   * ```
   */
  public fun deviceToLocal(): Int {
    TODO("Implement deviceToLocal")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& deviceToLocal() const { return fDeviceToLocal; }
   * ```
   */
  public fun textureOrigin(): Int {
    TODO("Implement textureOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * const half2& textureOrigin() const { return fMaskInfo.fTextureOrigin; }
   * ```
   */
  public fun maskSize(): Int {
    TODO("Implement maskSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const half2& maskSize() const { return fMaskInfo.fMaskSize; }
   * ```
   */
  public fun textureProxy(): Int {
    TODO("Implement textureProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureProxy* textureProxy() const { return fTextureProxy.get(); }
   * ```
   */
  public fun inverted(): Boolean {
    TODO("Implement inverted")
  }

  public data class MaskInfo public constructor(
    public var fTextureOrigin: Int,
    public var fMaskSize: Int,
  )
}
