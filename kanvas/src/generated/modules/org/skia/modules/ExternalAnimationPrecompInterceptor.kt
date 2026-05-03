package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class ExternalAnimationPrecompInterceptor final : public skottie::PrecompInterceptor {
 * public:
 *     ExternalAnimationPrecompInterceptor(sk_sp<skresources::ResourceProvider>, const char prefix[]);
 *     ~ExternalAnimationPrecompInterceptor() override;
 *
 * private:
 *     sk_sp<skottie::ExternalLayer> onLoadPrecomp(const char[], const char[], const SkSize&) override;
 *
 *     const sk_sp<skresources::ResourceProvider> fResourceProvider;
 *     const SkString                             fPrefix;
 * }
 * ```
 */
public class ExternalAnimationPrecompInterceptor public constructor(
  rprovider: SkSp<ResourceProvider>,
  prefixp: CharArray,
) : PrecompInterceptor() {
  /**
   * C++ original:
   * ```cpp
   * ExternalAnimationPrecompInterceptor(sk_sp<skresources::ResourceProvider>, const char prefix[])
   * ```
   */
  public var skSp: ExternalAnimationPrecompInterceptor = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skresources::ResourceProvider> fResourceProvider
   * ```
   */
  private val fResourceProvider: Int = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * const SkString                             fPrefix
   * ```
   */
  private val fPrefix: Int = TODO("Initialize fPrefix")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skottie::ExternalLayer> ExternalAnimationPrecompInterceptor::onLoadPrecomp(
   *         const char[], const char name[], const SkSize& size) {
   *     if (0 != strncmp(name, fPrefix.c_str(), fPrefix.size())) {
   *         return nullptr;
   *     }
   *
   *     auto data = fResourceProvider->load("", name + fPrefix.size());
   *     if (!data) {
   *         return nullptr;
   *     }
   *
   *     auto anim = skottie::Animation::Builder()
   *                     .setPrecompInterceptor(sk_ref_sp(this))
   *                     .setResourceProvider(fResourceProvider)
   *                     .make(static_cast<const char*>(data->data()), data->size());
   *
   *     return anim ? sk_make_sp<ExternalAnimationLayer>(std::move(anim), size)
   *                 : nullptr;
   * }
   * ```
   */
  public override fun onLoadPrecomp(
    param0: CharArray,
    name: CharArray,
    size: SkSize,
  ): Int {
    TODO("Implement onLoadPrecomp")
  }
}
