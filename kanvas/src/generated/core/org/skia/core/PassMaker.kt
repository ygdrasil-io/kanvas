package org.skia.core

import kotlin.Float
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class PassMaker {
 * public:
 *     explicit PassMaker(int window, float sigma) : fWindow{window},
 *                                                   fSigma{sigma} {}
 *     virtual ~PassMaker() = default;
 *     virtual Pass* makePass(void* buffer, SkArenaAlloc* alloc) const = 0;
 *     virtual size_t bufferSizeBytes() const = 0;
 *     int window() const {return fWindow;}
 *     float sigma() const {return fSigma;}
 *
 * private:
 *     const int fWindow;
 *     const float fSigma;
 * }
 * ```
 */
public abstract class PassMaker public constructor(
  window: Int,
  sigma: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * const int fWindow
   * ```
   */
  private val fWindow: Int = TODO("Initialize fWindow")

  /**
   * C++ original:
   * ```cpp
   * const float fSigma
   * ```
   */
  private val fSigma: Float = TODO("Initialize fSigma")

  /**
   * C++ original:
   * ```cpp
   * virtual Pass* makePass(void* buffer, SkArenaAlloc* alloc) const = 0
   * ```
   */
  public abstract fun makePass(buffer: Unit?, alloc: SkArenaAlloc?): Pass

  /**
   * C++ original:
   * ```cpp
   * virtual size_t bufferSizeBytes() const = 0
   * ```
   */
  public abstract fun bufferSizeBytes(): ULong

  /**
   * C++ original:
   * ```cpp
   * int window() const {return fWindow;}
   * ```
   */
  public fun window(): Int {
    TODO("Implement window")
  }

  /**
   * C++ original:
   * ```cpp
   * float sigma() const {return fSigma;}
   * ```
   */
  public fun sigma(): Float {
    TODO("Implement sigma")
  }
}
