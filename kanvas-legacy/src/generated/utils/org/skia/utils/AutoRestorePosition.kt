package org.skia.utils

import kotlin.ULong
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class AutoRestorePosition {
 *     SkStream* fStream;
 *     size_t fPosition;
 * public:
 *     AutoRestorePosition(SkStream* stream) : fStream(stream) {
 *         fPosition = stream->getPosition();
 *     }
 *
 *     ~AutoRestorePosition() {
 *         if (fStream) {
 *             fStream->seek(fPosition);
 *         }
 *     }
 *
 *     // So we don't restore the position
 *     void markDone() { fStream = nullptr; }
 * }
 * ```
 */
public data class AutoRestorePosition public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkStream* fStream
   * ```
   */
  private var fStream: SkStream?,
  /**
   * C++ original:
   * ```cpp
   * size_t fPosition
   * ```
   */
  private var fPosition: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * void markDone() { fStream = nullptr; }
   * ```
   */
  public fun markDone() {
    TODO("Implement markDone")
  }
}
