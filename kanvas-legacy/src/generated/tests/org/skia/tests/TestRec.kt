package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkResourceCache
import org.skia.pdf.Key

/**
 * C++ original:
 * ```cpp
 * struct TestRec : SkResourceCache::Rec {
 *     enum {
 *         kDidInstall = 1 << 0,
 *     };
 *
 *     TestKey fKey;
 *     int*    fFlags;
 *     bool    fCanBePurged;
 *
 *     TestRec(int sharedID, int32_t data, int* flagPtr) : fKey(sharedID, data), fFlags(flagPtr) {
 *         fCanBePurged = false;
 *     }
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return 1024; /* just need a value */ }
 *     bool canBePurged() override { return fCanBePurged; }
 *     void postAddInstall(void*) override {
 *         *fFlags |= kDidInstall;
 *     }
 *     const char* getCategory() const override { return "test-category"; }
 * }
 * ```
 */
public open class TestRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * TestKey fKey
   * ```
   */
  public var fKey: TestKey,
  /**
   * C++ original:
   * ```cpp
   * int*    fFlags
   * ```
   */
  public var fFlags: Int?,
  /**
   * C++ original:
   * ```cpp
   * bool    fCanBePurged
   * ```
   */
  public var fCanBePurged: Boolean,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestRec(int sharedID, int32_t data, int* flagPtr) : fKey(sharedID, data), fFlags(flagPtr) {
   *         fCanBePurged = false;
   *     }
   * ```
   */
  public constructor(
    sharedID: Int,
    `data`: Int,
    flagPtr: Int?,
  ) : this() {
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
   * size_t bytesUsed() const override { return 1024; /* just need a value */ }
   * ```
   */
  public override fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canBePurged() override { return fCanBePurged; }
   * ```
   */
  public override fun canBePurged(): Boolean {
    TODO("Implement canBePurged")
  }

  /**
   * C++ original:
   * ```cpp
   * void postAddInstall(void*) override {
   *         *fFlags |= kDidInstall;
   *     }
   * ```
   */
  public override fun postAddInstall(param0: Unit?) {
    TODO("Implement postAddInstall")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "test-category"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  public companion object {
    public val kDidInstall: Int = TODO("Initialize kDidInstall")
  }
}
