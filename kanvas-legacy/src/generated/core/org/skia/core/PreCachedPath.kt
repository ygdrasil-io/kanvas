package org.skia.core

import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * struct PreCachedPath : public SkPath {
 *     PreCachedPath() {}
 *     PreCachedPath(const SkPath& path);
 * }
 * ```
 */
public open class PreCachedPath public constructor() : SkPath() {
  /**
   * C++ original:
   * ```cpp
   * PreCachedPath() {}
   * ```
   */
  public constructor(path: SkPath) : this(TODO()) {
    TODO("Implement constructor")
  }
}
