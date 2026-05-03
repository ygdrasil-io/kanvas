package org.skia.modules

import kotlin.CharArray
import kotlin.Float
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API MarkerObserver : public SkRefCnt {
 * public:
 *     // t0,t1 are in the Animation::seek() domain.
 *     virtual void onMarker(const char name[], float t0, float t1) = 0;
 * }
 * ```
 */
public abstract class MarkerObserver : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void onMarker(const char name[], float t0, float t1) = 0
   * ```
   */
  public abstract fun onMarker(
    name: CharArray,
    t0: Float,
    t1: Float,
  )
}
