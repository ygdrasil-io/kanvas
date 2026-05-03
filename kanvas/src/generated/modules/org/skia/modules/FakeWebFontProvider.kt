package org.skia.modules

import kotlin.CharArray
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class FakeWebFontProvider final : public skresources::ResourceProvider {
 * public:
 *     FakeWebFontProvider() : fTypeface(ToolUtils::CreateTypefaceFromResource(kWebFontResource)) {}
 *
 *     sk_sp<SkTypeface> loadTypeface(const char[], const char[]) const override {
 *         return fTypeface;
 *     }
 *
 * private:
 *     sk_sp<SkTypeface> fTypeface;
 *
 *     using INHERITED = skresources::ResourceProvider;
 * }
 * ```
 */
public class FakeWebFontProvider public constructor() : ResourceProvider() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fTypeface: SkSp<SkTypeface> = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> loadTypeface(const char[], const char[]) const override {
   *         return fTypeface;
   *     }
   * ```
   */
  public override fun loadTypeface(param0: CharArray, param1: CharArray): SkSp<SkTypeface> {
    TODO("Implement loadTypeface")
  }
}
