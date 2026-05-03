package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGDefs : public SkSVGHiddenContainer {
 * public:
 *     static sk_sp<SkSVGDefs> Make() { return sk_sp<SkSVGDefs>(new SkSVGDefs()); }
 *
 * private:
 *     SkSVGDefs() : INHERITED(SkSVGTag::kDefs) {}
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public open class SkSVGDefs public constructor() : SkSVGHiddenContainer(TODO()) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGDefs> Make() { return sk_sp<SkSVGDefs>(new SkSVGDefs()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
