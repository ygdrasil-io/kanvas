package org.skia.core

import kotlin.Unit
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SkRefCntSet : public SkTPtrSet<SkRefCnt*> {
 * public:
 *     ~SkRefCntSet() override;
 *
 * protected:
 *     // overrides
 *     void incPtr(void*) override;
 *     void decPtr(void*) override;
 * }
 * ```
 */
public open class SkRefCntSet : SkTPtrSet(), SkRefCnt {
  /**
   * C++ original:
   * ```cpp
   * void SkRefCntSet::incPtr(void* ptr) {
   *     ((SkRefCnt*)ptr)->ref();
   * }
   * ```
   */
  protected override fun incPtr(ptr: Unit?) {
    TODO("Implement incPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRefCntSet::decPtr(void* ptr) {
   *     ((SkRefCnt*)ptr)->unref();
   * }
   * ```
   */
  protected override fun decPtr(ptr: Unit?) {
    TODO("Implement decPtr")
  }
}
