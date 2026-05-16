package org.skia.modules

import SkShapers.Factory
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.ULong
import org.skia.core.SkFourByteTag
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrimitiveFactory final : public SkShapers::Factory {
 *     std::unique_ptr<SkShaper> makeShaper(sk_sp<SkFontMgr>) override {
 *         return SkShapers::Primitive::PrimitiveText();
 *     }
 *     std::unique_ptr<SkShaper::BiDiRunIterator> makeBidiRunIterator(const char*,
 *                                                                 size_t,
 *                                                                 uint8_t) override {
 *         return std::make_unique<SkShaper::TrivialBiDiRunIterator>(0, 0);
 *     }
 *     std::unique_ptr<SkShaper::ScriptRunIterator> makeScriptRunIterator(const char*,
 *                                                                  size_t,
 *                                                                  SkFourByteTag) override {
 *         return std::make_unique<SkShaper::TrivialScriptRunIterator>(0, 0);
 *     }
 *
 *     SkUnicode* getUnicode() override {
 *         return nullptr;
 *     }
 * }
 * ```
 */
public class PrimitiveFactory : Factory() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper> makeShaper(sk_sp<SkFontMgr>) override {
   *         return SkShapers::Primitive::PrimitiveText();
   *     }
   * ```
   */
  public override fun makeShaper(param0: SkSp<SkFontMgr>): Int {
    TODO("Implement makeShaper")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::BiDiRunIterator> makeBidiRunIterator(const char*,
   *                                                                 size_t,
   *                                                                 uint8_t) override {
   *         return std::make_unique<SkShaper::TrivialBiDiRunIterator>(0, 0);
   *     }
   * ```
   */
  public override fun makeBidiRunIterator(
    param0: String?,
    param1: ULong,
    param2: UByte,
  ): Int {
    TODO("Implement makeBidiRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::ScriptRunIterator> makeScriptRunIterator(const char*,
   *                                                                  size_t,
   *                                                                  SkFourByteTag) override {
   *         return std::make_unique<SkShaper::TrivialScriptRunIterator>(0, 0);
   *     }
   * ```
   */
  public override fun makeScriptRunIterator(
    param0: String?,
    param1: ULong,
    param2: SkFourByteTag,
  ): Int {
    TODO("Implement makeScriptRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * SkUnicode* getUnicode() override {
   *         return nullptr;
   *     }
   * ```
   */
  public override fun getUnicode(): SkUnicode {
    TODO("Implement getUnicode")
  }
}
