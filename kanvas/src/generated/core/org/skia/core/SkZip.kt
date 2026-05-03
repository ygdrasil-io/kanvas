package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.Ts

/**
 * C++ original:
 * ```cpp
 * template<typename... Ts>
 * class SkZip {
 *     using ReturnTuple = std::tuple<Ts&...>;
 *
 *     class Iterator {
 *     public:
 *         using value_type = ReturnTuple;
 *         using difference_type = ptrdiff_t;
 *         using pointer = value_type*;
 *         using reference = value_type;
 *         using iterator_category = std::input_iterator_tag;
 *         constexpr Iterator(const SkZip* zip, size_t index) : fZip(zip), fIndex(index) {}
 *         constexpr Iterator(const Iterator& that) : Iterator(that.fZip, that.fIndex) {}
 *         constexpr Iterator& operator++() { ++fIndex; return *this; }
 *         constexpr Iterator operator++(int) { Iterator tmp(*this); operator++(); return tmp; }
 *         constexpr bool operator==(const Iterator& rhs) const { return fIndex == rhs.fIndex; }
 *         constexpr bool operator!=(const Iterator& rhs) const { return fIndex != rhs.fIndex; }
 *         constexpr reference operator*() { return (*fZip)[fIndex]; }
 *         friend constexpr difference_type operator-(Iterator lhs, Iterator rhs) {
 *             return lhs.fIndex - rhs.fIndex;
 *         }
 *
 *     private:
 *         const SkZip* const fZip = nullptr;
 *         size_t fIndex = 0;
 *     };
 *
 *     template<typename T>
 *     inline static constexpr T* nullify = nullptr;
 *
 * public:
 *     constexpr SkZip() : fPointers(nullify<Ts>...), fSize(0) {}
 *     constexpr SkZip(size_t) = delete;
 *     constexpr SkZip(size_t size, Ts*... ts) : fPointers(ts...), fSize(size) {}
 *     constexpr SkZip(const SkZip& that) = default;
 *     constexpr SkZip& operator=(const SkZip &that) = default;
 *
 *     // Check to see if U can be used for const T or is the same as T
 *     template <typename U, typename T>
 *     using CanConvertToConst = typename std::integral_constant<bool,
 *                     std::is_convertible<U*, T*>::value && sizeof(U) == sizeof(T)>::type;
 *
 *     // Allow SkZip<const T> to be constructed from SkZip<T>.
 *     template<typename... Us,
 *             typename = std::enable_if<std::conjunction<CanConvertToConst<Us, Ts>...>::value>>
 *     constexpr SkZip(const SkZip<Us...>& that) : fPointers(that.data()), fSize(that.size()) {}
 *
 *     constexpr ReturnTuple operator[](size_t i) const { return this->index(i);}
 *     constexpr size_t size() const { return fSize; }
 *     constexpr bool empty() const { return this->size() == 0; }
 *     constexpr ReturnTuple front() const { return this->index(0); }
 *     constexpr ReturnTuple back() const { return this->index(this->size() - 1); }
 *     constexpr Iterator begin() const { return Iterator(this, 0); }
 *     constexpr Iterator end() const { return Iterator(this, this->size()); }
 *     template<size_t I> constexpr auto get() const {
 *         return SkSpan(std::get<I>(fPointers), fSize);
 *     }
 *     constexpr std::tuple<Ts*...> data() const { return fPointers; }
 *     constexpr SkZip first(size_t n) const {
 *         SkASSERT(n <= this->size());
 *         if (n == 0) { return SkZip(); }
 *         return SkZip(n, fPointers);
 *     }
 *     constexpr SkZip last(size_t n) const {
 *         SkASSERT(n <= this->size());
 *         if (n == 0) { return SkZip(); }
 *         return SkZip(n, this->pointersAt(fSize - n));
 *     }
 *     constexpr SkZip subspan(size_t offset, size_t count) const {
 *         SkASSERT(offset < this->size());
 *         SkASSERT(count <= this->size() - offset);
 *         if (count == 0) { return SkZip(); }
 *         return SkZip(count, pointersAt(offset));
 *     }
 *
 * private:
 *     constexpr SkZip(size_t n, const std::tuple<Ts*...>& pointers) : fPointers(pointers), fSize(n) {}
 *
 *     constexpr ReturnTuple index(size_t i) const {
 *         SkASSERT(this->size() > 0);
 *         SkASSERT(i < this->size());
 *         return indexDetail(i, std::make_index_sequence<sizeof...(Ts)>());
 *     }
 *
 *     template<std::size_t... Is>
 *     constexpr ReturnTuple indexDetail(size_t i, std::index_sequence<Is...>) const {
 *         return ReturnTuple((std::get<Is>(fPointers))[i]...);
 *     }
 *
 *     std::tuple<Ts*...> pointersAt(size_t i) const {
 *         SkASSERT(this->size() > 0);
 *         SkASSERT(i < this->size());
 *         return pointersAtDetail(i, std::make_index_sequence<sizeof...(Ts)>());
 *     }
 *
 *     template<std::size_t... Is>
 *     constexpr std::tuple<Ts*...> pointersAtDetail(size_t i, std::index_sequence<Is...>) const {
 *         return std::tuple<Ts*...>(&(std::get<Is>(fPointers))[i]...);
 *     }
 *
 *     std::tuple<Ts*...> fPointers;
 *     size_t fSize;
 * }
 * ```
 */
public data class SkZip<Ts> public constructor(
  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     inline static constexpr T* nullify = nullptr
   * ```
   */
  private var fSize: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr SkZip& operator=(const SkZip &that) = default
   * ```
   */
  public fun assign(that: SkZip<Ts>) {
    TODO("Implement assign")
  }

  public data class Iterator<Ts> public constructor(
    private val fZip: SkZip<Ts>?,
    private var fIndex: Int,
  ) {
    public operator fun inc(): Iterator<Ts> {
      TODO("Implement inc")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public companion object {
    private val nullify: T? = TODO("Initialize nullify")

    public val sizeT: Any = TODO("Initialize sizeT")
  }
}
