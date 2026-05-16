package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.UInt
import kotlin.Unit
import org.skia.core.SkResourceCache
import org.skia.pdf.Key
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * struct TestingRec : public SkResourceCache::Rec {
 *     TestingRec(const TestingKey& key, uint32_t value) : fKey(key), fValue(value) {}
 *
 *     TestingKey  fKey;
 *     intptr_t    fValue;
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return sizeof(fKey) + sizeof(fValue); }
 *     const char* getCategory() const override { return "test_cache"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override { return nullptr; }
 *
 *     static bool Visitor(const SkResourceCache::Rec& baseRec, void* context) {
 *         const TestingRec& rec = static_cast<const TestingRec&>(baseRec);
 *         intptr_t* result = (intptr_t*)context;
 *
 *         *result = rec.fValue;
 *         return true;
 *     }
 * }
 * ```
 */
public open class TestingRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * TestingKey  fKey
   * ```
   */
  public var fKey: TestingKey,
  /**
   * C++ original:
   * ```cpp
   * intptr_t    fValue
   * ```
   */
  public var fValue: Int,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestingRec(const TestingKey& key, uint32_t value) : fKey(key), fValue(value) {}
   * ```
   */
  public constructor(key: TestingKey, `value`: UInt) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const Key& getKey() const override { return fKey; }
   * ```
   */
  public override fun getKey(): Key {
    TODO("Implement getKey")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesUsed() const override { return sizeof(fKey) + sizeof(fValue); }
   * ```
   */
  public override fun bytesUsed(): Int {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "test_cache"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* diagnostic_only_getDiscardable() const override { return nullptr; }
   * ```
   */
  public override fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
    TODO("Implement diagnosticOnlyGetDiscardable")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Visitor(const SkResourceCache::Rec& baseRec, void* context) {
     *         const TestingRec& rec = static_cast<const TestingRec&>(baseRec);
     *         intptr_t* result = (intptr_t*)context;
     *
     *         *result = rec.fValue;
     *         return true;
     *     }
     * ```
     */
    public fun visitor(baseRec: SkResourceCache.Rec, context: Unit?): Boolean {
      TODO("Implement visitor")
    }
  }
}
