package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkCachedData
import org.skia.modules.Visitor
import org.skia.pdf.Key
import org.skia.utils.FindVisitor
import undefined.DiscardableFactory
import undefined.Rec
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkSynchronizedResourceCache : public SkResourceCache {
 * public:
 *     bool find(const Key& key, FindVisitor, void* context) override;
 *     void add(Rec*, void* payload = nullptr) override;
 *
 *     void visitAll(Visitor, void* context) override;
 *
 *     size_t getTotalBytesUsed() const override;
 *     size_t getTotalByteLimit() const override;
 *     size_t setTotalByteLimit(size_t newLimit) override;
 *
 *     size_t setSingleAllocationByteLimit(size_t) override;
 *     size_t getSingleAllocationByteLimit() const override;
 *     size_t getEffectiveSingleAllocationByteLimit() const override;
 *
 *     void purgeAll() override;
 *
 *     DiscardableFactory discardableFactory() const override;
 *
 *     SkCachedData* newCachedData(size_t bytes) override;
 *
 *     void dump() const override;
 *
 *     SkSynchronizedResourceCache(DiscardableFactory);
 *     explicit SkSynchronizedResourceCache(size_t byteLimit);
 *     ~SkSynchronizedResourceCache() override;
 *
 * private:
 *     mutable SkMutex fMutex;
 * }
 * ```
 */
public open class SkSynchronizedResourceCache public constructor(
  fact: DiscardableFactory,
) : SkResourceCache(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex fMutex
   * ```
   */
  private var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * SkSynchronizedResourceCache::SkSynchronizedResourceCache(DiscardableFactory fact)
   *  : SkResourceCache(fact) {}
   * ```
   */
  public constructor(byteLimit: ULong) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSynchronizedResourceCache::find(const Key& key, FindVisitor visitor, void* context) {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::find(key, visitor, context);
   * }
   * ```
   */
  public override fun find(
    key: Key,
    visitor: FindVisitor,
    context: Unit?,
  ): Boolean {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSynchronizedResourceCache::add(Rec* rec, void* payload) {
   *     SkAutoMutexExclusive am(fMutex);
   *     SkResourceCache::add(rec, payload);
   * }
   * ```
   */
  public override fun add(rec: Rec?, payload: Unit? = TODO()) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSynchronizedResourceCache::visitAll(Visitor visitor, void* context) {
   *     SkAutoMutexExclusive am(fMutex);
   *     SkResourceCache::visitAll(visitor, context);
   * }
   * ```
   */
  public override fun visitAll(visitor: Visitor, context: Unit?) {
    TODO("Implement visitAll")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::getTotalBytesUsed() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::getTotalBytesUsed();
   * }
   * ```
   */
  public override fun getTotalBytesUsed(): Int {
    TODO("Implement getTotalBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::getTotalByteLimit() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::getTotalByteLimit();
   * }
   * ```
   */
  public override fun getTotalByteLimit(): Int {
    TODO("Implement getTotalByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::setTotalByteLimit(size_t newLimit) {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::setTotalByteLimit(newLimit);
   * }
   * ```
   */
  public override fun setTotalByteLimit(newLimit: ULong): Int {
    TODO("Implement setTotalByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::setSingleAllocationByteLimit(size_t size) {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::setSingleAllocationByteLimit(size);
   * }
   * ```
   */
  public override fun setSingleAllocationByteLimit(size: ULong): Int {
    TODO("Implement setSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::getSingleAllocationByteLimit() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::getSingleAllocationByteLimit();
   * }
   * ```
   */
  public override fun getSingleAllocationByteLimit(): Int {
    TODO("Implement getSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkSynchronizedResourceCache::getEffectiveSingleAllocationByteLimit() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::getEffectiveSingleAllocationByteLimit();
   * }
   * ```
   */
  public override fun getEffectiveSingleAllocationByteLimit(): Int {
    TODO("Implement getEffectiveSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSynchronizedResourceCache::purgeAll() {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::purgeAll();
   * }
   * ```
   */
  public override fun purgeAll() {
    TODO("Implement purgeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * SkResourceCache::DiscardableFactory SkSynchronizedResourceCache::discardableFactory() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::discardableFactory();
   * }
   * ```
   */
  public override fun discardableFactory(): DiscardableFactory {
    TODO("Implement discardableFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCachedData* SkSynchronizedResourceCache::newCachedData(size_t bytes) {
   *     SkAutoMutexExclusive am(fMutex);
   *     return SkResourceCache::newCachedData(bytes);
   * }
   * ```
   */
  public override fun newCachedData(bytes: ULong): SkCachedData {
    TODO("Implement newCachedData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSynchronizedResourceCache::dump() const {
   *     SkAutoMutexExclusive am(fMutex);
   *     SkResourceCache::dump();
   * }
   * ```
   */
  public override fun dump() {
    TODO("Implement dump")
  }
}
