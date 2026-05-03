package org.skia.core

import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * class SkNoDestructor {
 * public:
 *     static_assert(!(std::is_trivially_constructible_v<T> && std::is_trivially_destructible_v<T>),
 *                   "T is trivially constructible and destructible; please use a constinit object of "
 *                   "type T directly instead");
 *
 *     static_assert(!std::is_trivially_destructible_v<T>,
 *                   "T is trivially destructible; please use a function-local static of type T "
 *                   "directly instead");
 *
 *     // Not constexpr; just write static constexpr T x = ...; if the value should be a constexpr.
 *     template <typename... Args> explicit SkNoDestructor(Args&&... args) {
 *         new (fStorage) T(std::forward<Args>(args)...);
 *     }
 *
 *     // Allows copy and move construction of the contained type, to allow construction from an
 *     // initializer list, e.g. for std::vector.
 *     explicit SkNoDestructor(const T& x) { new (fStorage) T(x); }
 *     explicit SkNoDestructor(T&& x) { new (fStorage) T(std::move(x)); }
 *
 *     SkNoDestructor(const SkNoDestructor&) = delete;
 *     SkNoDestructor& operator=(const SkNoDestructor&) = delete;
 *
 *     ~SkNoDestructor() = default;
 *
 *     const T& operator*() const { return *get(); }
 *     T& operator*() { return *get(); }
 *
 *     const T* operator->() const { return get(); }
 *     T* operator->() { return get(); }
 *
 *     const T* get() const { return reinterpret_cast<const T*>(fStorage); }
 *     T* get() { return reinterpret_cast<T*>(fStorage); }
 *
 * private:
 *     alignas(T) std::byte fStorage[sizeof(T)];
 *
 * #if defined(__clang__) && defined(__has_feature)
 * #if __has_feature(leak_sanitizer) || __has_feature(address_sanitizer)
 *     // TODO(https://crbug.com/812277): This is a hack to work around the fact that LSan doesn't seem
 *     // to treat SkNoDestructor as a root for reachability analysis. This means that code like this:
 *     //     static SkNoDestructor<std::vector<int>> v({1, 2, 3});
 *     // is considered a leak. Using the standard leak sanitizer annotations to suppress leaks doesn't
 *     // work: std::vector is implicitly constructed before calling the SkNoDestructor constructor.
 *     //
 *     // Unfortunately, I haven't been able to demonstrate this issue in simpler reproductions: until
 *     // that's resolved, hold an explicit pointer to the placement-new'd object in leak sanitizer
 *     // mode to help LSan realize that objects allocated by the contained type are still reachable.
 *     T* fStoragePtr = reinterpret_cast<T*>(fStorage);
 * #endif  // leak_sanitizer/address_sanitizer
 * #endif  // __has_feature
 * }
 * ```
 */
public data class SkNoDestructor<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::byte fStorage[sizeof(T)]
   * ```
   */
  private var fStorage: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * SkNoDestructor& operator=(const SkNoDestructor&) = delete
   * ```
   */
  public fun assign(param0: SkNoDestructor<T>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const T& operator*() const { return *get(); }
   * ```
   */
  public fun `get`(): T {
    TODO("Implement get")
  }
}
