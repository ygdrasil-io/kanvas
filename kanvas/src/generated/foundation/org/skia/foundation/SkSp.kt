package org.skia.foundation

import kotlin.Any

/**
 * C++ original:
 * ```cpp
 * class SK_TRIVIAL_ABI sk_sp {
 * public:
 *     using element_type = T;
 *
 *     constexpr sk_sp() : fPtr(nullptr) {}
 *     constexpr sk_sp(std::nullptr_t) : fPtr(nullptr) {}
 *
 *     /**
 *      *  Shares the underlying object by calling ref(), so that both the argument and the newly
 *      *  created sk_sp both have a reference to it.
 *      */
 *     sk_sp(const sk_sp<T>& that) : fPtr(SkSafeRef(that.get())) {}
 *     template <typename U,
 *               typename = typename std::enable_if<std::is_convertible<U*, T*>::value>::type>
 *     sk_sp(const sk_sp<U>& that) : fPtr(SkSafeRef(that.get())) {}
 *
 *     /**
 *      *  Move the underlying object from the argument to the newly created sk_sp. Afterwards only
 *      *  the new sk_sp will have a reference to the object, and the argument will point to null.
 *      *  No call to ref() or unref() will be made.
 *      */
 *     sk_sp(sk_sp<T>&& that) : fPtr(that.release()) {}
 *     template <typename U,
 *               typename = typename std::enable_if<std::is_convertible<U*, T*>::value>::type>
 *     sk_sp(sk_sp<U>&& that) : fPtr(that.release()) {}
 *
 *     /**
 *      *  Adopt the bare pointer into the newly created sk_sp.
 *      *  No call to ref() or unref() will be made.
 *      */
 *     explicit sk_sp(T* obj) : fPtr(obj) {}
 *
 *     /**
 *      *  Calls unref() on the underlying object pointer.
 *      */
 *     ~sk_sp() {
 *         SkSafeUnref(fPtr);
 *         SkDEBUGCODE(fPtr = nullptr);
 *     }
 *
 *     sk_sp<T>& operator=(std::nullptr_t) { this->reset(); return *this; }
 *
 *     /**
 *      *  Shares the underlying object referenced by the argument by calling ref() on it. If this
 *      *  sk_sp previously had a reference to an object (i.e. not null) it will call unref() on that
 *      *  object.
 *      */
 *     sk_sp<T>& operator=(const sk_sp<T>& that) {
 *         if (this != &that) {
 *             this->reset(SkSafeRef(that.get()));
 *         }
 *         return *this;
 *     }
 *     template <typename U,
 *               typename = typename std::enable_if<std::is_convertible<U*, T*>::value>::type>
 *     sk_sp<T>& operator=(const sk_sp<U>& that) {
 *         this->reset(SkSafeRef(that.get()));
 *         return *this;
 *     }
 *
 *     /**
 *      *  Move the underlying object from the argument to the sk_sp. If the sk_sp previously held
 *      *  a reference to another object, unref() will be called on that object. No call to ref()
 *      *  will be made.
 *      */
 *     sk_sp<T>& operator=(sk_sp<T>&& that) {
 *         this->reset(that.release());
 *         return *this;
 *     }
 *     template <typename U,
 *               typename = typename std::enable_if<std::is_convertible<U*, T*>::value>::type>
 *     sk_sp<T>& operator=(sk_sp<U>&& that) {
 *         this->reset(that.release());
 *         return *this;
 *     }
 *
 *     T& operator*() const {
 *         SkASSERT(this->get() != nullptr);
 *         return *this->get();
 *     }
 *
 *     explicit operator bool() const { return this->get() != nullptr; }
 *
 *     T* get() const { return fPtr; }
 *     T* operator->() const { return fPtr; }
 *
 *     /**
 *      *  Adopt the new bare pointer, and call unref() on any previously held object (if not null).
 *      *  No call to ref() will be made.
 *      */
 *     void reset(T* ptr = nullptr) {
 *         // Calling fPtr->unref() may call this->~() or this->reset(T*).
 *         // http://wg21.cmeerw.net/lwg/issue998
 *         // http://wg21.cmeerw.net/lwg/issue2262
 *         T* oldPtr = fPtr;
 *         fPtr = ptr;
 *         SkSafeUnref(oldPtr);
 *     }
 *
 *     /**
 *      *  Return the bare pointer, and set the internal object pointer to nullptr.
 *      *  The caller must assume ownership of the object, and manage its reference count directly.
 *      *  No call to unref() will be made.
 *      */
 *     [[nodiscard]] T* release() {
 *         T* ptr = fPtr;
 *         fPtr = nullptr;
 *         return ptr;
 *     }
 *
 *     void swap(sk_sp<T>& that) /*noexcept*/ {
 *         using std::swap;
 *         swap(fPtr, that.fPtr);
 *     }
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 *
 * private:
 *     T*  fPtr;
 * }
 * ```
 */
public data class SkSp<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T*  fPtr
   * ```
   */
  private var fPtr: T,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<T>& operator=(std::nullptr_t) { this->reset(); return *this; }
   * ```
   */
  public fun assign(param0: Any?) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<T>& operator=(const sk_sp<T>& that) {
   *         if (this != &that) {
   *             this->reset(SkSafeRef(that.get()));
   *         }
   *         return *this;
   *     }
   * ```
   */
  public fun <U> assign(that: SkSp<U>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<T>& operator=(const sk_sp<U>& that) {
   *         this->reset(SkSafeRef(that.get()));
   *         return *this;
   *     }
   * ```
   */
  public fun times(): T {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<T>& operator=(sk_sp<T>&& that) {
   *         this->reset(that.release());
   *         return *this;
   *     }
   * ```
   */
  public fun `get`(): T {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<T>& operator=(sk_sp<U>&& that) {
   *         this->reset(that.release());
   *         return *this;
   *     }
   * ```
   */
  public fun reset(ptr: T? = null) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * T& operator*() const {
   *         SkASSERT(this->get() != nullptr);
   *         return *this->get();
   *     }
   * ```
   */
  public fun release(): T {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * T* get() const { return fPtr; }
   * ```
   */
  public fun swap(that: SkSp<T>) {
    TODO("Implement swap")
  }
}

public typealias SkFlattenableFactory = (SkReadBuffer) -> SkSp<SkFlattenable>
