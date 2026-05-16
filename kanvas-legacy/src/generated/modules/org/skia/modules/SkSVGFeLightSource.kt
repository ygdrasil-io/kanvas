package org.skia.modules

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeLightSource : public SkSVGHiddenContainer {
 * public:
 *     void appendChild(sk_sp<SkSVGNode>) final {
 *         SkDEBUGF("cannot append child nodes to an SVG light source.\n");
 *     }
 *
 * protected:
 *     explicit SkSVGFeLightSource(SkSVGTag tag) : INHERITED(tag) {}
 *
 * private:
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public open class SkSVGFeLightSource public constructor(
  tag: SkSVGTag,
) : SkSVGHiddenContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void appendChild(sk_sp<SkSVGNode>) final {
   *         SkDEBUGF("cannot append child nodes to an SVG light source.\n");
   *     }
   * ```
   */
  public fun appendChild(param0: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }
}

public typealias SkSVGFeDistantLightINHERITED = SkSVGFeLightSource

public typealias SkSVGFePointLightINHERITED = SkSVGFeLightSource

public typealias SkSVGFeSpotLightINHERITED = SkSVGFeLightSource
