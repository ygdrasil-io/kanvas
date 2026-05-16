package org.skia.tests

import kotlin.Int
import org.skia.core.SkIDChangeListener

/**
 * C++ original:
 * ```cpp
 * class TestListener : public SkIDChangeListener {
 * public:
 *     explicit TestListener(int* ptr) : fPtr(ptr) {}
 *     void changed() override { (*fPtr)++; }
 * private:
 *     int* fPtr;
 * }
 * ```
 */
public open class TestListener public constructor(
  ptr: Int?,
) : SkIDChangeListener() {
  /**
   * C++ original:
   * ```cpp
   * int* fPtr
   * ```
   */
  private var fPtr: Int? = TODO("Initialize fPtr")

  /**
   * C++ original:
   * ```cpp
   * void changed() override { (*fPtr)++; }
   * ```
   */
  public override fun changed() {
    TODO("Implement changed")
  }
}
