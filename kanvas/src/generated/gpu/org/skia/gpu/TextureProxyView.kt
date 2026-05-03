package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TextureProxyView {
 * public:
 *     TextureProxyView() = default;
 *
 *     TextureProxyView(sk_sp<TextureProxy> proxy, Swizzle swizzle)
 *             : fProxy(std::move(proxy)), fSwizzle(swizzle) {}
 *
 *     TextureProxyView(sk_sp<TextureProxy> proxy, Swizzle swizzle, Origin origin)
 *             : fProxy(std::move(proxy)), fSwizzle(swizzle), fOrigin(origin) {}
 *
 *     // This entry point is used when we don't care about the swizzle and assume TopLeft origin.
 *     explicit TextureProxyView(sk_sp<TextureProxy> proxy)
 *             : fProxy(std::move(proxy)) {}
 *
 *     TextureProxyView(TextureProxyView&& view) = default;
 *     TextureProxyView(const TextureProxyView&) = default;
 *
 *     explicit operator bool() const { return SkToBool(fProxy.get()); }
 *
 *     TextureProxyView& operator=(const TextureProxyView&) = default;
 *     TextureProxyView& operator=(TextureProxyView&& view) = default;
 *
 *     bool operator==(const TextureProxyView& view) const {
 *         return fProxy == view.fProxy &&
 *                fSwizzle == view.fSwizzle &&
 *                fOrigin == view.fOrigin;
 *     }
 *     bool operator!=(const TextureProxyView& other) const { return !(*this == other); }
 *
 *     int width() const { return this->proxy()->dimensions().width(); }
 *     int height() const { return this->proxy()->dimensions().height(); }
 *     SkISize dimensions() const { return this->proxy()->dimensions(); }
 *
 *     skgpu::Mipmapped mipmapped() const {
 *         if (const TextureProxy* proxy = this->proxy()) {
 *             return proxy->mipmapped();
 *         }
 *         return skgpu::Mipmapped::kNo;
 *     }
 *
 *     TextureProxy* proxy() const { return fProxy.get(); }
 *     sk_sp<TextureProxy> refProxy() const { return fProxy; }
 *
 *     Swizzle swizzle() const { return fSwizzle; }
 *     Origin origin() const { return fOrigin; }
 *
 *     void concatSwizzle(Swizzle swizzle) {
 *         fSwizzle = skgpu::Swizzle::Concat(fSwizzle, swizzle);
 *     }
 *
 *     // makeSwizzle returns a new view with 'swizzle' composed on to this view's existing swizzle
 *     TextureProxyView makeSwizzle(Swizzle swizzle) const & {
 *         return {fProxy, Swizzle::Concat(fSwizzle, swizzle), fOrigin};
 *     }
 *
 *     TextureProxyView makeSwizzle(Swizzle swizzle) && {
 *         return {std::move(fProxy), Swizzle::Concat(fSwizzle, swizzle), fOrigin};
 *     }
 *
 *     // resetSwizzle returns a new view that uses 'swizzle' and disregards this view's prior swizzle.
 *     TextureProxyView replaceSwizzle(Swizzle swizzle) const {
 *         return {fProxy, swizzle, fOrigin};
 *     }
 *
 *     void reset() {
 *         *this = {};
 *     }
 *
 *     // This does not reset the swizzle, so the View can still be used to access those
 *     // properties associated with the detached proxy.
 *     sk_sp<TextureProxy> detachProxy() {
 *         return std::move(fProxy);
 *     }
 *
 * private:
 *     sk_sp<TextureProxy> fProxy;
 *     Swizzle fSwizzle;
 *     Origin fOrigin = Origin::kTopLeft;
 * }
 * ```
 */
public data class TextureProxyView public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextureProxyView(sk_sp<TextureProxy> proxy, Swizzle swizzle)
   * ```
   */
  public var skSp: TextureProxyView,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fProxy
   * ```
   */
  private var fProxy: Int,
  /**
   * C++ original:
   * ```cpp
   * Origin fOrigin
   * ```
   */
  private var fOrigin: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TextureProxyView(sk_sp<TextureProxy> proxy, Swizzle swizzle)
   *             : fProxy(std::move(proxy)), fSwizzle(swizzle)
   * ```
   */
  public fun fSwizzle(param0: Int): TextureProxyView {
    TODO("Implement fSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView& operator=(const TextureProxyView&) = default
   * ```
   */
  public fun assign(param0: TextureProxyView) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView& operator=(TextureProxyView&& view) = default
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const TextureProxyView& view) const {
   *         return fProxy == view.fProxy &&
   *                fSwizzle == view.fSwizzle &&
   *                fOrigin == view.fOrigin;
   *     }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const TextureProxyView& other) const { return !(*this == other); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return this->proxy()->dimensions().width(); }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return this->proxy()->dimensions().height(); }
   * ```
   */
  public fun mipmapped(): Int {
    TODO("Implement mipmapped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return this->proxy()->dimensions(); }
   * ```
   */
  public fun proxy(): TextureProxyView {
    TODO("Implement proxy")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Mipmapped mipmapped() const {
   *         if (const TextureProxy* proxy = this->proxy()) {
   *             return proxy->mipmapped();
   *         }
   *         return skgpu::Mipmapped::kNo;
   *     }
   * ```
   */
  public fun refProxy(): Int {
    TODO("Implement refProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy* proxy() const { return fProxy.get(); }
   * ```
   */
  public fun swizzle(): Int {
    TODO("Implement swizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> refProxy() const { return fProxy; }
   * ```
   */
  public fun origin(): Int {
    TODO("Implement origin")
  }

  /**
   * C++ original:
   * ```cpp
   * Swizzle swizzle() const { return fSwizzle; }
   * ```
   */
  public fun concatSwizzle(swizzle: Swizzle) {
    TODO("Implement concatSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * Origin origin() const { return fOrigin; }
   * ```
   */
  public fun makeSwizzle(swizzle: Swizzle): TextureProxyView {
    TODO("Implement makeSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * void concatSwizzle(Swizzle swizzle) {
   *         fSwizzle = skgpu::Swizzle::Concat(fSwizzle, swizzle);
   *     }
   * ```
   */
  public fun replaceSwizzle(swizzle: Swizzle): TextureProxyView {
    TODO("Implement replaceSwizzle")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView makeSwizzle(Swizzle swizzle) const & {
   *         return {fProxy, Swizzle::Concat(fSwizzle, swizzle), fOrigin};
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView makeSwizzle(Swizzle swizzle) && {
   *         return {std::move(fProxy), Swizzle::Concat(fSwizzle, swizzle), fOrigin};
   *     }
   * ```
   */
  public fun detachProxy(): Int {
    TODO("Implement detachProxy")
  }
}
