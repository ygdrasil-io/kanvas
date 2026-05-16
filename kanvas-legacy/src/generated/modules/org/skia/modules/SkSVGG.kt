package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGG : public SkSVGContainer {
 * public:
 *     static sk_sp<SkSVGG> Make() { return sk_sp<SkSVGG>(new SkSVGG()); }
 *
 * private:
 *     SkSVGG() : INHERITED(SkSVGTag::kG) { }
 *
 *     using INHERITED = SkSVGContainer;
 * }
 * ```
 */
public open class SkSVGG public constructor() : SkSVGContainer(TODO()) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGG> Make() { return sk_sp<SkSVGG>(new SkSVGG()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
