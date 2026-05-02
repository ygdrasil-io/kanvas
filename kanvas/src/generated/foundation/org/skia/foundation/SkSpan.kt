package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class SkSpan {
 * public:
 *     constexpr SkSpan() : fPtr{nullptr}, fSize{0} {}
 *
 *     template <typename Integer, std::enable_if_t<std::is_integral_v<Integer>, bool> = true>
 *     constexpr SkSpan(T* ptr, Integer size) : fPtr{ptr}, fSize{SkToSizeT(size)} {
 *         SkASSERT(ptr || fSize == 0);  // disallow nullptr + a nonzero size
 *         SkASSERT(fSize < (std::numeric_limits<size_t>::max() / sizeof(T)));
 *     }
 *     template <typename U, typename = std::enable_if_t<std::is_same_v<const U, T>>>
 *     constexpr SkSpan(const SkSpan<U>& that) : fPtr(std::data(that)), fSize(std::size(that)) {}
 *     constexpr SkSpan(const SkSpan& o) = default;
 *     template<size_t N> constexpr SkSpan(T(&a)[N]) : SkSpan(a, N) { }
 *     template<typename Container>
 *     constexpr SkSpan(Container&& c) : SkSpan(std::data(c), std::size(c)) { }
 *     SkSpan(std::initializer_list<T> il SK_CHECK_IL_LIFETIME)
 *             : SkSpan(std::data(il), std::size(il)) {}
 *
 *     constexpr SkSpan& operator=(const SkSpan& that) = default;
 *
 *     constexpr T& operator [] (size_t i) const {
 *         return fPtr[sk_collection_check_bounds(i, this->size())];
 *     }
 *     constexpr T& front() const { sk_collection_not_empty(this->empty()); return fPtr[0]; }
 *     constexpr T& back()  const { sk_collection_not_empty(this->empty()); return fPtr[fSize - 1]; }
 *     constexpr T* begin() const { return fPtr; }
 *     constexpr T* end() const { return fPtr + fSize; }
 *     constexpr auto rbegin() const { return std::make_reverse_iterator(this->end()); }
 *     constexpr auto rend() const { return std::make_reverse_iterator(this->begin()); }
 *     constexpr T* data() const { return this->begin(); }
 *     constexpr size_t size() const { return fSize; }
 *     constexpr bool empty() const { return fSize == 0; }
 *     constexpr size_t size_bytes() const { return fSize * sizeof(T); }
 *     constexpr SkSpan<T> first(size_t prefixLen) const {
 *         return SkSpan{fPtr, sk_collection_check_length(prefixLen, fSize)};
 *     }
 *     constexpr SkSpan<T> last(size_t postfixLen) const {
 *         return SkSpan{fPtr + (this->size() - postfixLen),
 *                       sk_collection_check_length(postfixLen, fSize)};
 *     }
 *     constexpr SkSpan<T> subspan(size_t offset) const {
 *         return this->subspan(offset, this->size() - offset);
 *     }
 *     constexpr SkSpan<T> subspan(size_t offset, size_t count) const {
 *         const size_t safeOffset = sk_collection_check_length(offset, fSize);
 *
 *         // Should read offset + count > size(), but that could overflow. We know that safeOffset
 *         // is <= size, therefore the subtraction will not overflow.
 *         if (count > this->size() - safeOffset) SK_UNLIKELY {
 *             // The count is too large.
 *             SkUNREACHABLE;
 *         }
 *         return SkSpan{fPtr + safeOffset, count};
 *     }
 *
 * private:
 *     T* fPtr;
 *     size_t fSize;
 * }
 * ```
 */
public data class SkSpan<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T* fPtr
   * ```
   */
  private var fPtr: T,
  /**
   * C++ original:
   * ```cpp
   * size_t fSize
   * ```
   */
  private var fSize: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr SkSpan& operator=(const SkSpan& that) = default
   * ```
   */
  public fun assign(that: SkSpan<T>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T& operator [] (size_t i) const {
   *         return fPtr[sk_collection_check_bounds(i, this->size())];
   *     }
   * ```
   */
  public operator fun `get`(i: ULong): T {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T& front() const { sk_collection_not_empty(this->empty()); return fPtr[0]; }
   * ```
   */
  public fun front(): T {
    TODO("Implement front")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T& back()  const { sk_collection_not_empty(this->empty()); return fPtr[fSize - 1]; }
   * ```
   */
  public fun back(): T {
    TODO("Implement back")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T* begin() const { return fPtr; }
   * ```
   */
  public fun begin(): T {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T* end() const { return fPtr + fSize; }
   * ```
   */
  public fun end(): T {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr auto rbegin() const { return std::make_reverse_iterator(this->end()); }
   * ```
   */
  public fun rbegin(): Any {
    TODO("Implement rbegin")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr auto rend() const { return std::make_reverse_iterator(this->begin()); }
   * ```
   */
  public fun rend(): Any {
    TODO("Implement rend")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr T* data() const { return this->begin(); }
   * ```
   */
  public fun `data`(): T {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t size() const { return fSize; }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool empty() const { return fSize == 0; }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t size_bytes() const { return fSize * sizeof(T); }
   * ```
   */
  public fun sizeBytes(): ULong {
    TODO("Implement sizeBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSpan<T> first(size_t prefixLen) const {
   *         return SkSpan{fPtr, sk_collection_check_length(prefixLen, fSize)};
   *     }
   * ```
   */
  public fun first(prefixLen: ULong): SkSpan<T> {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSpan<T> last(size_t postfixLen) const {
   *         return SkSpan{fPtr + (this->size() - postfixLen),
   *                       sk_collection_check_length(postfixLen, fSize)};
   *     }
   * ```
   */
  public fun last(postfixLen: ULong): SkSpan<T> {
    TODO("Implement last")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSpan<T> subspan(size_t offset) const {
   *         return this->subspan(offset, this->size() - offset);
   *     }
   * ```
   */
  public fun subspan(offset: ULong): SkSpan<T> {
    TODO("Implement subspan")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkSpan<T> subspan(size_t offset, size_t count) const {
   *         const size_t safeOffset = sk_collection_check_length(offset, fSize);
   *
   *         // Should read offset + count > size(), but that could overflow. We know that safeOffset
   *         // is <= size, therefore the subtraction will not overflow.
   *         if (count > this->size() - safeOffset) SK_UNLIKELY {
   *             // The count is too large.
   *             SkUNREACHABLE;
   *         }
   *         return SkSpan{fPtr + safeOffset, count};
   *     }
   * ```
   */
  public fun subspan(offset: ULong, count: ULong): SkSpan<T> {
    TODO("Implement subspan")
  }
}
