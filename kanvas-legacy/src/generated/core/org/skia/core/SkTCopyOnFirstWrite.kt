package org.skia.core

import kotlin.Int
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class SkTCopyOnFirstWrite {
 * public:
 *     explicit SkTCopyOnFirstWrite(const T& initial) : fObj(&initial) {}
 *
 *     explicit SkTCopyOnFirstWrite(const T* initial) : fObj(initial) {}
 *
 *     // Constructor for delayed initialization.
 *     SkTCopyOnFirstWrite() : fObj(nullptr) {}
 *
 *     SkTCopyOnFirstWrite(const SkTCopyOnFirstWrite&  that) { *this = that;            }
 *     SkTCopyOnFirstWrite(      SkTCopyOnFirstWrite&& that) { *this = std::move(that); }
 *
 *     SkTCopyOnFirstWrite& operator=(const SkTCopyOnFirstWrite& that) {
 *         fLazy = that.fLazy;
 *         fObj  = fLazy.has_value() ? &fLazy.value() : that.fObj;
 *         return *this;
 *     }
 *
 *     SkTCopyOnFirstWrite& operator=(SkTCopyOnFirstWrite&& that) {
 *         fLazy = std::move(that.fLazy);
 *         fObj  = fLazy.has_value() ? &fLazy.value() : that.fObj;
 *         return *this;
 *     }
 *
 *     // Should only be called once, and only if the default constructor was used.
 *     void init(const T& initial) {
 *         SkASSERT(!fObj);
 *         SkASSERT(!fLazy.has_value());
 *         fObj = &initial;
 *     }
 *
 *     // If not already initialized, in-place instantiates the writable object
 *     template <typename... Args>
 *     void initIfNeeded(Args&&... args) {
 *         if (!fObj) {
 *             SkASSERT(!fLazy.has_value());
 *             fObj = &fLazy.emplace(std::forward<Args>(args)...);
 *         }
 *     }
 *
 *     /**
 *      * Returns a writable T*. The first time this is called the initial object is cloned.
 *      */
 *     T* writable() {
 *         SkASSERT(fObj);
 *         if (!fLazy.has_value()) {
 *             fLazy = *fObj;
 *             fObj = &fLazy.value();
 *         }
 *         return &fLazy.value();
 *     }
 *
 *     const T* get() const { return fObj; }
 *
 *     /**
 *      * Operators for treating this as though it were a const pointer.
 *      */
 *
 *     const T *operator->() const { return fObj; }
 *
 *     operator const T*() const { return fObj; }
 *
 *     const T& operator *() const { return *fObj; }
 *
 * private:
 *     const T*         fObj;
 *     std::optional<T> fLazy;
 * }
 * ```
 */
public data class SkTCopyOnFirstWrite<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * const T*         fObj
   * ```
   */
  private val fObj: T,
  /**
   * C++ original:
   * ```cpp
   * std::optional<T> fLazy
   * ```
   */
  private var fLazy: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkTCopyOnFirstWrite& operator=(const SkTCopyOnFirstWrite& that) {
   *         fLazy = that.fLazy;
   *         fObj  = fLazy.has_value() ? &fLazy.value() : that.fObj;
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: SkTCopyOnFirstWrite<T>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTCopyOnFirstWrite& operator=(SkTCopyOnFirstWrite&& that) {
   *         fLazy = std::move(that.fLazy);
   *         fObj  = fLazy.has_value() ? &fLazy.value() : that.fObj;
   *         return *this;
   *     }
   * ```
   */
  public fun `init`(initial: T) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(const T& initial) {
   *         SkASSERT(!fObj);
   *         SkASSERT(!fLazy.has_value());
   *         fObj = &initial;
   *     }
   * ```
   */
  public fun <Args> initIfNeeded(args: Args) {
    TODO("Implement initIfNeeded")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename... Args>
   *     void initIfNeeded(Args&&... args) {
   *         if (!fObj) {
   *             SkASSERT(!fLazy.has_value());
   *             fObj = &fLazy.emplace(std::forward<Args>(args)...);
   *         }
   *     }
   * ```
   */
  public fun writable(): T {
    TODO("Implement writable")
  }

  /**
   * C++ original:
   * ```cpp
   * T* writable() {
   *         SkASSERT(fObj);
   *         if (!fLazy.has_value()) {
   *             fLazy = *fObj;
   *             fObj = &fLazy.value();
   *         }
   *         return &fLazy.value();
   *     }
   * ```
   */
  public fun `get`(): T {
    TODO("Implement get")
  }
}
