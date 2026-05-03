package org.skia.tools

import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.core.SkThreadID

/**
 * C++ original:
 * ```cpp
 * struct TraceEvent {
 *     char     fPhase;
 *     uint8_t  fNumArgs;
 *     uint32_t fSize;
 *
 *     const char* fName;
 *     // TODO: Merge fID and fClockEnd (never used together)
 *     uint64_t   fID;
 *     uint64_t   fClockBegin;
 *     uint64_t   fClockEnd;
 *     SkThreadID fThreadID;
 *
 *     TraceEvent* next() {
 *         return reinterpret_cast<TraceEvent*>(reinterpret_cast<char*>(this) + fSize);
 *     }
 *     TraceEventArg* args() { return reinterpret_cast<TraceEventArg*>(this + 1); }
 *     char*          stringTable() { return reinterpret_cast<char*>(this->args() + fNumArgs); }
 * }
 * ```
 */
public data class TraceEvent public constructor(
  /**
   * C++ original:
   * ```cpp
   * char     fPhase
   * ```
   */
  public var fPhase: Char,
  /**
   * C++ original:
   * ```cpp
   * uint8_t  fNumArgs
   * ```
   */
  public var fNumArgs: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fSize
   * ```
   */
  public var fSize: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  public val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * uint64_t   fID
   * ```
   */
  public var fID: Int,
  /**
   * C++ original:
   * ```cpp
   * uint64_t   fClockBegin
   * ```
   */
  public var fClockBegin: Int,
  /**
   * C++ original:
   * ```cpp
   * uint64_t   fClockEnd
   * ```
   */
  public var fClockEnd: Int,
  /**
   * C++ original:
   * ```cpp
   * SkThreadID fThreadID
   * ```
   */
  public var fThreadID: SkThreadID,
) {
  /**
   * C++ original:
   * ```cpp
   * TraceEvent* next() {
   *         return reinterpret_cast<TraceEvent*>(reinterpret_cast<char*>(this) + fSize);
   *     }
   * ```
   */
  public fun next(): TraceEvent {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * TraceEventArg* args() { return reinterpret_cast<TraceEventArg*>(this + 1); }
   * ```
   */
  public fun args(): TraceEventArg {
    TODO("Implement args")
  }

  /**
   * C++ original:
   * ```cpp
   * char*          stringTable() { return reinterpret_cast<char*>(this->args() + fNumArgs); }
   * ```
   */
  public fun stringTable(): Char {
    TODO("Implement stringTable")
  }
}
