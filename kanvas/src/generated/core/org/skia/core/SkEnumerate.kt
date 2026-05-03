package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * template <typename Iter, typename C = std::monostate>
 * class SkEnumerate {
 *     using Captured = decltype(*std::declval<Iter>());
 *     template <typename> struct is_tuple : std::false_type {};
 *     template <typename... T> struct is_tuple<std::tuple<T...>> : std::true_type {};
 *
 *     // v must be a r-value to bind to temporary non-const references.
 *     static constexpr auto MakeResult(size_t i, Captured&& v) {
 *         if constexpr (is_tuple<Captured>::value) {
 *             return std::tuple_cat(std::tuple<size_t>{i}, v);
 *         } else {
 *             // Capture v by reference instead of by value by using std::tie.
 *             return std::tuple_cat(std::tuple<size_t>{i}, std::tie(v));
 *         }
 *     }
 *
 *     using Result = decltype(MakeResult(0, std::declval<Captured>()));
 *
 *     class Iterator {
 *     public:
 *         using value_type = Result;
 *         using difference_type = ptrdiff_t;
 *         using pointer = value_type*;
 *         using reference = value_type;
 *         using iterator_category = std::input_iterator_tag;
 *         constexpr Iterator(ptrdiff_t index, Iter it) : fIndex{index}, fIt{it} { }
 *         constexpr Iterator(const Iterator&) = default;
 *         constexpr Iterator operator++() { ++fIndex; ++fIt; return *this; }
 *         constexpr Iterator operator++(int) { Iterator tmp(*this); operator++(); return tmp; }
 *         constexpr bool operator==(const Iterator& rhs) const { return fIt == rhs.fIt; }
 *         constexpr bool operator!=(const Iterator& rhs) const { return fIt != rhs.fIt; }
 *         constexpr reference operator*() { return MakeResult(fIndex, *fIt); }
 *
 *     private:
 *         ptrdiff_t fIndex;
 *         Iter fIt;
 *     };
 *
 * public:
 *     constexpr SkEnumerate(Iter begin, Iter end) : SkEnumerate{0, begin, end} {}
 *     explicit constexpr SkEnumerate(C&& c)
 *             : fCollection{std::move(c)}
 *             , fBeginIndex{0}
 *             , fBegin{std::begin(fCollection)}
 *             , fEnd{std::end(fCollection)} { }
 *     constexpr SkEnumerate(const SkEnumerate& that) = default;
 *     constexpr SkEnumerate& operator=(const SkEnumerate& that) {
 *         fBegin = that.fBegin;
 *         fEnd = that.fEnd;
 *         return *this;
 *     }
 *     constexpr Iterator begin() const { return Iterator{fBeginIndex, fBegin}; }
 *     constexpr Iterator end() const { return Iterator{fBeginIndex + this->ssize(), fEnd}; }
 *     constexpr bool empty() const { return fBegin == fEnd; }
 *     constexpr size_t size() const { return std::distance(fBegin,  fEnd); }
 *     constexpr ptrdiff_t ssize() const { return std::distance(fBegin,  fEnd); }
 *     constexpr SkEnumerate first(size_t n) {
 *         SkASSERT(n <= this->size());
 *         ptrdiff_t deltaEnd = this->ssize() - n;
 *         return SkEnumerate{fBeginIndex, fBegin, std::prev(fEnd, deltaEnd)};
 *     }
 *     constexpr SkEnumerate last(size_t n) {
 *         SkASSERT(n <= this->size());
 *         ptrdiff_t deltaBegin = this->ssize() - n;
 *         return SkEnumerate{fBeginIndex + deltaBegin, std::next(fBegin, deltaBegin), fEnd};
 *     }
 *     constexpr SkEnumerate subspan(size_t offset, size_t count) {
 *         SkASSERT(offset < this->size());
 *         SkASSERT(count <= this->size() - offset);
 *         auto newBegin = std::next(fBegin, offset);
 *         return SkEnumerate(fBeginIndex + offset, newBegin, std::next(newBegin, count));
 *     }
 *
 * private:
 *     constexpr SkEnumerate(ptrdiff_t beginIndex, Iter begin, Iter end)
 *         : fBeginIndex{beginIndex}
 *         , fBegin(begin)
 *         , fEnd(end) {}
 *
 *     C fCollection;
 *     const ptrdiff_t fBeginIndex;
 *     Iter fBegin;
 *     Iter fEnd;
 * }
 * ```
 */
public open class SkEnumerate<Iter, C> public constructor(
  begin: Iter,
  end: Iter,
) : SkEnumerate {
  /**
   * C++ original:
   * ```cpp
   * C fCollection
   * ```
   */
  private var fCollection: C = TODO("Initialize fCollection")

  /**
   * C++ original:
   * ```cpp
   * const ptrdiff_t fBeginIndex
   * ```
   */
  private val fBeginIndex: Int = TODO("Initialize fBeginIndex")

  /**
   * C++ original:
   * ```cpp
   * Iter fBegin
   * ```
   */
  private var fBegin: Iter = TODO("Initialize fBegin")

  /**
   * C++ original:
   * ```cpp
   * Iter fEnd
   * ```
   */
  private var fEnd: Iter = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate(Iter begin, Iter end) : SkEnumerate{0, begin, end} {}
   * ```
   */
  public constructor(c: C) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit constexpr SkEnumerate(C&& c)
   *             : fCollection{std::move(c)}
   *             , fBeginIndex{0}
   *             , fBegin{std::begin(fCollection)}
   *             , fEnd{std::end(fCollection)} { }
   * ```
   */
  public constructor(that: SkEnumerate<Iter, C>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate(const SkEnumerate& that) = default
   * ```
   */
  public constructor(
    beginIndex: Long,
    begin: Iter,
    end: Iter,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename... T> struct is_tuple<std::tuple<T...>> : std::true_type {};
   *
   *     // v must be a r-value to bind to temporary non-const references.
   *     static constexpr auto MakeResult(size_t i, Captured&& v) {
   *         if constexpr (is_tuple<Captured>::value) {
   *             return std::tuple_cat(std::tuple<size_t>{i}, v);
   *         } else {
   *             // Capture v by reference instead of by value by using std::tie.
   *             return std::tuple_cat(std::tuple<size_t>{i}, std::tie(v));
   *         }
   *     }
   * ```
   */
  public override fun <T> makeResult(param0: Int, param1: Int): Any {
    TODO("Implement makeResult")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate& operator=(const SkEnumerate& that) {
   *         fBegin = that.fBegin;
   *         fEnd = that.fEnd;
   *         return *this;
   *     }
   * ```
   */
  public override fun assign(that: SkEnumerate<Iter, C>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr Iterator begin() const { return Iterator{fBeginIndex, fBegin}; }
   * ```
   */
  public override fun begin(): Iterator {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr Iterator end() const { return Iterator{fBeginIndex + this->ssize(), fEnd}; }
   * ```
   */
  public override fun end(): Iterator {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool empty() const { return fBegin == fEnd; }
   * ```
   */
  public override fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t size() const { return std::distance(fBegin,  fEnd); }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr ptrdiff_t ssize() const { return std::distance(fBegin,  fEnd); }
   * ```
   */
  public override fun ssize(): Int {
    TODO("Implement ssize")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate first(size_t n) {
   *         SkASSERT(n <= this->size());
   *         ptrdiff_t deltaEnd = this->ssize() - n;
   *         return SkEnumerate{fBeginIndex, fBegin, std::prev(fEnd, deltaEnd)};
   *     }
   * ```
   */
  public override fun first(n: ULong): SkEnumerate<Iter, C> {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate last(size_t n) {
   *         SkASSERT(n <= this->size());
   *         ptrdiff_t deltaBegin = this->ssize() - n;
   *         return SkEnumerate{fBeginIndex + deltaBegin, std::next(fBegin, deltaBegin), fEnd};
   *     }
   * ```
   */
  public override fun last(n: ULong): SkEnumerate<Iter, C> {
    TODO("Implement last")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkEnumerate subspan(size_t offset, size_t count) {
   *         SkASSERT(offset < this->size());
   *         SkASSERT(count <= this->size() - offset);
   *         auto newBegin = std::next(fBegin, offset);
   *         return SkEnumerate(fBeginIndex + offset, newBegin, std::next(newBegin, count));
   *     }
   * ```
   */
  public override fun subspan(offset: ULong, count: ULong): SkEnumerate<Iter, C> {
    TODO("Implement subspan")
  }

  public open class IsTuple : Boolean()

  public data class Iterator public constructor(
    private var fIndex: Int,
    private var fIt: Iter,
  ) {
    public operator fun inc(): undefined.Iterator {
      TODO("Implement inc")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun times(): Int {
      TODO("Implement times")
    }
  }
}
