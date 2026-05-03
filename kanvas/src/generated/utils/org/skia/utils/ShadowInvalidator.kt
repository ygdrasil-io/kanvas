package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.core.SkIDChangeListener
import org.skia.core.SkResourceCache

/**
 * C++ original:
 * ```cpp
 * class ShadowInvalidator : public SkIDChangeListener {
 * public:
 *     ShadowInvalidator(const SkResourceCache::Key& key) {
 *         fKey.reset(new uint8_t[key.size()]);
 *         memcpy(fKey.get(), &key, key.size());
 *     }
 *
 * private:
 *     const SkResourceCache::Key& getKey() const {
 *         return *reinterpret_cast<SkResourceCache::Key*>(fKey.get());
 *     }
 *
 *     // always purge
 *     static bool FindVisitor(const SkResourceCache::Rec&, void*) {
 *         return false;
 *     }
 *
 *     void changed() override {
 *         SkResourceCache::Find(this->getKey(), ShadowInvalidator::FindVisitor, nullptr);
 *     }
 *
 *     std::unique_ptr<uint8_t[]> fKey;
 * }
 * ```
 */
public open class ShadowInvalidator public constructor(
  key: SkResourceCache.Key,
) : SkIDChangeListener() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<uint8_t[]> fKey
   * ```
   */
  private var fKey: Int = TODO("Initialize fKey")

  /**
   * C++ original:
   * ```cpp
   * const SkResourceCache::Key& getKey() const {
   *         return *reinterpret_cast<SkResourceCache::Key*>(fKey.get());
   *     }
   * ```
   */
  private fun getKey(): SkResourceCache.Key {
    TODO("Implement getKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void changed() override {
   *         SkResourceCache::Find(this->getKey(), ShadowInvalidator::FindVisitor, nullptr);
   *     }
   * ```
   */
  public override fun changed() {
    TODO("Implement changed")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool FindVisitor(const SkResourceCache::Rec&, void*) {
     *         return false;
     *     }
     * ```
     */
    private fun findVisitor(param0: SkResourceCache.Rec, param1: Unit?): Boolean {
      TODO("Implement findVisitor")
    }
  }
}
