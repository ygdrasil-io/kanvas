package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SK_API SkNoncopyable {
 * public:
 *     SkNoncopyable() = default;
 *
 *     SkNoncopyable(SkNoncopyable&&) = default;
 *     SkNoncopyable& operator =(SkNoncopyable&&) = default;
 *
 * private:
 *     SkNoncopyable(const SkNoncopyable&) = delete;
 *     SkNoncopyable& operator=(const SkNoncopyable&) = delete;
 * }
 * ```
 */
public open class SkNoncopyable public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkNoncopyable() = default
   * ```
   */
  public constructor(param0: SkNoncopyable) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkNoncopyable& operator =(SkNoncopyable&&) = default
   * ```
   */
  public fun assign(param0: SkNoncopyable) {
    TODO("Implement assign")
  }
}

public typealias SkPicturePlaybackINHERITED = SkNoncopyable
