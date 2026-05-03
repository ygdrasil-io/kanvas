package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * struct Sink {
 *     virtual ~Sink() {}
 *     // You may write to either the bitmap or stream.  If you write to log, we'll print that out.
 *     [[nodiscard]] virtual Result draw(const Src&, SkBitmap*, SkWStream*, SkString* log) const = 0;
 *
 *     virtual void done() const {}
 *
 *     // Override the color space of this Sink, after creation
 *     virtual void setColorSpace(sk_sp<SkColorSpace>) {}
 *
 *     // Force Tasks using this Sink to run on the main thread?
 *     virtual bool serial() const { return false; }
 *
 *     // File extension for the content draw() outputs, e.g. "png", "pdf".
 *     virtual const char* fileExtension() const  = 0;
 *
 *     virtual SinkFlags flags() const = 0;
 *
 *     /** Returns the color type and space used by the sink. */
 *     virtual SkColorInfo colorInfo() const { return SkColorInfo(); }
 * }
 * ```
 */
public abstract class Sink {
  /**
   * C++ original:
   * ```cpp
   * virtual Result draw(const Src&, SkBitmap*, SkWStream*, SkString* log) const = 0
   * ```
   */
  public abstract fun draw(
    param0: Src,
    param1: SkBitmap?,
    param2: SkWStream?,
    log: String?,
  ): Result

  /**
   * C++ original:
   * ```cpp
   * virtual void done() const {}
   * ```
   */
  public open fun done() {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void setColorSpace(sk_sp<SkColorSpace>) {}
   * ```
   */
  public open fun setColorSpace(param0: SkSp<SkColorSpace>) {
    TODO("Implement setColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool serial() const { return false; }
   * ```
   */
  public open fun serial(): Boolean {
    TODO("Implement serial")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const char* fileExtension() const  = 0
   * ```
   */
  public abstract fun fileExtension(): Char

  /**
   * C++ original:
   * ```cpp
   * virtual SinkFlags flags() const = 0
   * ```
   */
  public abstract fun flags(): SinkFlags

  /**
   * C++ original:
   * ```cpp
   * virtual SkColorInfo colorInfo() const { return SkColorInfo(); }
   * ```
   */
  public open fun colorInfo(): Int {
    TODO("Implement colorInfo")
  }
}
