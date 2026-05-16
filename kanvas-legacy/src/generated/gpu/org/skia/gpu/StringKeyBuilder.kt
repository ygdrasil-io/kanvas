package org.skia.gpu

import kotlin.String
import kotlin.UInt
import skia_private.TArraytrue

/**
 * C++ original:
 * ```cpp
 * class StringKeyBuilder : public KeyBuilder {
 * public:
 *     StringKeyBuilder(skia_private::TArray<uint32_t, true>* data) : KeyBuilder(data) {}
 *
 *     void addBits(uint32_t numBits, uint32_t val, std::string_view label) override {
 *         KeyBuilder::addBits(numBits, val, label);
 *         fDescription.appendf("%.*s: %u\n", (int)label.size(), label.data(), val);
 *     }
 *
 *     void appendComment(const char* comment) override {
 *         fDescription.appendf("%s\n", comment);
 *     }
 *
 *     SkString description() const { return fDescription; }
 *
 * private:
 *     SkString fDescription;
 * }
 * ```
 */
public open class StringKeyBuilder public constructor(
  `data`: TArraytrue<UInt>,
) : KeyBuilder(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkString fDescription
   * ```
   */
  private var fDescription: String = TODO("Initialize fDescription")

  /**
   * C++ original:
   * ```cpp
   * void addBits(uint32_t numBits, uint32_t val, std::string_view label) override {
   *         KeyBuilder::addBits(numBits, val, label);
   *         fDescription.appendf("%.*s: %u\n", (int)label.size(), label.data(), val);
   *     }
   * ```
   */
  public override fun addBits(
    numBits: UInt,
    `val`: UInt,
    label: String,
  ) {
    TODO("Implement addBits")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendComment(const char* comment) override {
   *         fDescription.appendf("%s\n", comment);
   *     }
   * ```
   */
  public override fun appendComment(comment: String?) {
    TODO("Implement appendComment")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString description() const { return fDescription; }
   * ```
   */
  public fun description(): String {
    TODO("Implement description")
  }
}
