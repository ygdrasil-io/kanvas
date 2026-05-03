package org.skia.core

import kotlin.Boolean
import kotlin.Int
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * class SkTLazy {
 * public:
 *     SkTLazy() = default;
 *     explicit SkTLazy(const T* src) : fValue(src ? std::optional<T>(*src) : std::nullopt) {}
 *     SkTLazy(const SkTLazy& that) : fValue(that.fValue) {}
 *     SkTLazy(SkTLazy&& that) : fValue(std::move(that.fValue)) {}
 *
 *     ~SkTLazy() = default;
 *
 *     SkTLazy& operator=(const SkTLazy& that) {
 *         fValue = that.fValue;
 *         return *this;
 *     }
 *
 *     SkTLazy& operator=(SkTLazy&& that) {
 *         fValue = std::move(that.fValue);
 *         return *this;
 *     }
 *
 *     /**
 *      *  Return a pointer to an instance of the class initialized with 'args'.
 *      *  If a previous instance had been initialized (either from init() or
 *      *  set()) it will first be destroyed, so that a freshly initialized
 *      *  instance is always returned.
 *      */
 *     template <typename... Args> T* init(Args&&... args) {
 *         fValue.emplace(std::forward<Args>(args)...);
 *         return this->get();
 *     }
 *
 *     /**
 *      *  Copy src into this, and return a pointer to a copy of it. Note this
 *      *  will always return the same pointer, so if it is called on a lazy that
 *      *  has already been initialized, then this will copy over the previous
 *      *  contents.
 *      */
 *     T* set(const T& src) {
 *         fValue = src;
 *         return this->get();
 *     }
 *
 *     T* set(T&& src) {
 *         fValue = std::move(src);
 *         return this->get();
 *     }
 *
 *     /**
 *      * Destroy the lazy object (if it was created via init() or set())
 *      */
 *     void reset() {
 *         fValue.reset();
 *     }
 *
 *     /**
 *      *  Returns true if a valid object has been initialized in the SkTLazy,
 *      *  false otherwise.
 *      */
 *     bool isValid() const { return fValue.has_value(); }
 *
 *     /**
 *      * Returns the object. This version should only be called when the caller
 *      * knows that the object has been initialized.
 *      */
 *     T* get() {
 *         SkASSERT(fValue.has_value());
 *         return &fValue.value();
 *     }
 *     const T* get() const {
 *         SkASSERT(fValue.has_value());
 *         return &fValue.value();
 *     }
 *
 *     T* operator->() { return this->get(); }
 *     const T* operator->() const { return this->get(); }
 *
 *     T& operator*() {
 *         SkASSERT(fValue.has_value());
 *         return *fValue;
 *     }
 *     const T& operator*() const {
 *         SkASSERT(fValue.has_value());
 *         return *fValue;
 *     }
 *
 *     /**
 *      * Like above but doesn't assert if object isn't initialized (in which case
 *      * nullptr is returned).
 *      */
 *     const T* getMaybeNull() const { return fValue.has_value() ? this->get() : nullptr; }
 *           T* getMaybeNull()       { return fValue.has_value() ? this->get() : nullptr; }
 *
 * private:
 *     std::optional<T> fValue;
 * }
 * ```
 */
public data class SkTLazy<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::optional<T> fValue
   * ```
   */
  private var fValue: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkTLazy& operator=(const SkTLazy& that) {
   *         fValue = that.fValue;
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: SkTLazy<T>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTLazy& operator=(SkTLazy&& that) {
   *         fValue = std::move(that.fValue);
   *         return *this;
   *     }
   * ```
   */
  public fun `init`(args: Args): T {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * T* init(Args&&... args) {
   *         fValue.emplace(std::forward<Args>(args)...);
   *         return this->get();
   *     }
   * ```
   */
  public fun `set`(src: T): T {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * T* set(const T& src) {
   *         fValue = src;
   *         return this->get();
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * T* set(T&& src) {
   *         fValue = std::move(src);
   *         return this->get();
   *     }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fValue.reset();
   *     }
   * ```
   */
  public fun `get`(): T {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fValue.has_value(); }
   * ```
   */
  public operator fun times(): T {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * T* get() {
   *         SkASSERT(fValue.has_value());
   *         return &fValue.value();
   *     }
   * ```
   */
  public fun getMaybeNull(): T {
    TODO("Implement getMaybeNull")
  }
}
