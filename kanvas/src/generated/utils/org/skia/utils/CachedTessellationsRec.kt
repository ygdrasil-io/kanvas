package org.skia.utils

import kotlin.Char
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkResourceCache
import org.skia.core.SkVertices
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkVector
import org.skia.pdf.Key
import undefined.FACTORY

/**
 * C++ original:
 * ```cpp
 * class CachedTessellationsRec : public SkResourceCache::Rec {
 * public:
 *     CachedTessellationsRec(const SkResourceCache::Key& key,
 *                            sk_sp<CachedTessellations> tessellations)
 *             : fTessellations(std::move(tessellations)) {
 *         fKey.reset(new uint8_t[key.size()]);
 *         memcpy(fKey.get(), &key, key.size());
 *     }
 *
 *     const Key& getKey() const override {
 *         return *reinterpret_cast<SkResourceCache::Key*>(fKey.get());
 *     }
 *
 *     size_t bytesUsed() const override { return fTessellations->size(); }
 *
 *     const char* getCategory() const override { return "tessellated shadow masks"; }
 *
 *     sk_sp<CachedTessellations> refTessellations() const { return fTessellations; }
 *
 *     template <typename FACTORY>
 *     sk_sp<SkVertices> find(const FACTORY& factory, const SkMatrix& matrix,
 *                            SkVector* translate) const {
 *         return fTessellations->find(factory, matrix, translate);
 *     }
 *
 * private:
 *     std::unique_ptr<uint8_t[]> fKey;
 *     sk_sp<CachedTessellations> fTessellations;
 * }
 * ```
 */
public open class CachedTessellationsRec public constructor(
  key: SkResourceCache.Key,
  tessellations: SkSp<CachedTessellations>,
) : SkResourceCache.Rec() {
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
   * sk_sp<CachedTessellations> fTessellations
   * ```
   */
  private var fTessellations: SkSp<CachedTessellations> = TODO("Initialize fTessellations")

  /**
   * C++ original:
   * ```cpp
   * const Key& getKey() const override {
   *         return *reinterpret_cast<SkResourceCache::Key*>(fKey.get());
   *     }
   * ```
   */
  public override fun getKey(): Key {
    TODO("Implement getKey")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesUsed() const override { return fTessellations->size(); }
   * ```
   */
  public override fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "tessellated shadow masks"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<CachedTessellations> refTessellations() const { return fTessellations; }
   * ```
   */
  public fun refTessellations(): SkSp<CachedTessellations> {
    TODO("Implement refTessellations")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename FACTORY>
   *     sk_sp<SkVertices> find(const FACTORY& factory, const SkMatrix& matrix,
   *                            SkVector* translate) const {
   *         return fTessellations->find(factory, matrix, translate);
   *     }
   * ```
   */
  public fun <FACTORY> find(
    factory: FACTORY,
    matrix: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement find")
  }
}
