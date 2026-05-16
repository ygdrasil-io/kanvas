package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkSVGTSpan final : public SkSVGTextContainer {
 * public:
 *     static sk_sp<SkSVGTSpan> Make() { return sk_sp<SkSVGTSpan>(new SkSVGTSpan()); }
 *
 * private:
 *     SkSVGTSpan() : INHERITED(SkSVGTag::kTSpan) {}
 *
 *     using INHERITED = SkSVGTextContainer;
 * }
 * ```
 */
public class SkSVGTSpan public constructor() : SkSVGTextContainer(TODO()) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGTSpan> Make() { return sk_sp<SkSVGTSpan>(new SkSVGTSpan()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
