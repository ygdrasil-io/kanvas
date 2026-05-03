package org.skia.core

import kotlin.Boolean
import kotlin.CharArray
import kotlin.ULong
import kotlin.Unit
import undefined.FILE

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFILEWStream : public SkWStream {
 * public:
 *     explicit SkFILEWStream(const char path[]);
 *     ~SkFILEWStream() override;
 *
 *     /** Returns true if the current path could be opened.
 *     */
 *     bool isValid() const { return fFILE != nullptr; }
 *
 *     bool write(const void* buffer, size_t size) override;
 *     void flush() override;
 *     void fsync();
 *     size_t bytesWritten() const override;
 *
 * private:
 *     FILE* fFILE;
 *
 *     using INHERITED = SkWStream;
 * }
 * ```
 */
public open class SkFILEWStream public constructor(
  path: CharArray,
) : SkWStream() {
  /**
   * C++ original:
   * ```cpp
   * FILE* fFILE
   * ```
   */
  private var fFILE: FILE? = TODO("Initialize fFILE")

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fFILE != nullptr; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkFILEWStream::write(const void* buffer, size_t size)
   * {
   *     if (fFILE == nullptr) {
   *         return false;
   *     }
   *
   *     if (sk_fwrite(buffer, size, fFILE) != size)
   *     {
   *         SkDEBUGCODE(SkDebugf("SkFILEWStream failed writing %zu bytes\n", size);)
   *         sk_fclose(fFILE);
   *         fFILE = nullptr;
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun write(buffer: Unit?, size: ULong): Boolean {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFILEWStream::flush()
   * {
   *     if (fFILE) {
   *         sk_fflush(fFILE);
   *     }
   * }
   * ```
   */
  public override fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFILEWStream::fsync()
   * {
   *     flush();
   *     if (fFILE) {
   *         sk_fsync(fFILE);
   *     }
   * }
   * ```
   */
  public fun fsync() {
    TODO("Implement fsync")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFILEWStream::bytesWritten() const {
   *     return sk_ftell(fFILE);
   * }
   * ```
   */
  public override fun bytesWritten(): ULong {
    TODO("Implement bytesWritten")
  }
}
