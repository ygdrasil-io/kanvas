package org.skia.modules

import kotlin.CharArray
import kotlin.String
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API Logger : public SkRefCnt {
 * public:
 *     enum class Level {
 *         kWarning,
 *         kError,
 *     };
 *
 *     virtual void log(Level, const char message[], const char* json = nullptr) = 0;
 * }
 * ```
 */
public abstract class Logger : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void log(Level, const char message[], const char* json = nullptr) = 0
   * ```
   */
  public abstract fun log(
    param0: Level,
    message: CharArray,
    json: String? = TODO(),
  )

  public enum class Level {
    kWarning,
    kError,
  }
}
