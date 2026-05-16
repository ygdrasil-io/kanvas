package org.skia.tests

import kotlin.Boolean
import org.skia.core.SkIDChangeListener

/**
 * C++ original:
 * ```cpp
 * class ChangeListener : public SkIDChangeListener {
 * public:
 *     ChangeListener(bool *changed) : fChanged(changed) { *fChanged = false; }
 *     ~ChangeListener() override {}
 *     void changed() override { *fChanged = true; }
 *
 * private:
 *     bool* fChanged;
 * }
 * ```
 */
public open class ChangeListener public constructor(
  changed: Boolean?,
) : SkIDChangeListener() {
  /**
   * C++ original:
   * ```cpp
   * bool* fChanged
   * ```
   */
  private var fChanged: Boolean? = TODO("Initialize fChanged")

  /**
   * C++ original:
   * ```cpp
   * void changed() override { *fChanged = true; }
   * ```
   */
  public override fun changed() {
    TODO("Implement changed")
  }
}
