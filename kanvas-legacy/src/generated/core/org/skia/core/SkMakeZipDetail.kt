package org.skia.core

import `Ts&&`
import kotlin.Any
import kotlin.Array
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkMakeZipDetail {
 *     template<typename T> struct DecayPointer{
 *         using U = typename std::remove_cv<typename std::remove_reference<T>::type>::type;
 *         using type = typename std::conditional<std::is_pointer<U>::value, U, T>::type;
 *     };
 *     template<typename T> using DecayPointerT = typename DecayPointer<T>::type;
 *
 *     template<typename C> struct ContiguousMemory {};
 *     template<typename T> struct ContiguousMemory<T*> {
 *         using value_type = T;
 *         static constexpr value_type* Data(T* t) { return t; }
 *         static constexpr size_t Size(T* s) { return SIZE_MAX; }
 *     };
 *     template<typename T, size_t N> struct ContiguousMemory<T(&)[N]> {
 *         using value_type = T;
 *         static constexpr value_type* Data(T(&t)[N]) { return t; }
 *         static constexpr size_t Size(T(&)[N]) { return N; }
 *     };
 *     // In general, we don't want r-value collections, but SkSpans are ok, because they are a view
 *     // onto an actual container.
 *     template<typename T> struct ContiguousMemory<SkSpan<T>> {
 *         using value_type = T;
 *         static constexpr value_type* Data(SkSpan<T> s) { return s.data(); }
 *         static constexpr size_t Size(SkSpan<T> s) { return s.size(); }
 *     };
 *     // Only accept l-value references to collections.
 *     template<typename C> struct ContiguousMemory<C&> {
 *         using value_type = typename std::remove_pointer<decltype(std::declval<C>().data())>::type;
 *         static constexpr value_type* Data(C& c) { return c.data(); }
 *         static constexpr size_t Size(C& c) { return c.size(); }
 *     };
 *     template<typename C> using Span = ContiguousMemory<DecayPointerT<C>>;
 *     template<typename C> using ValueType = typename Span<C>::value_type;
 *
 *     template<typename C, typename... Ts> struct PickOneSize {};
 *     template <typename T, typename... Ts> struct PickOneSize<T*, Ts...> {
 *         static constexpr size_t Size(T* t, Ts... ts) {
 *             return PickOneSize<Ts...>::Size(std::forward<Ts>(ts)...);
 *         }
 *     };
 *     template <typename T, typename... Ts, size_t N> struct PickOneSize<T(&)[N], Ts...> {
 *         static constexpr size_t Size(T(&)[N], Ts...) { return N; }
 *     };
 *     template<typename T, typename... Ts> struct PickOneSize<SkSpan<T>, Ts...> {
 *         static constexpr size_t Size(SkSpan<T> s, Ts...) { return s.size(); }
 *     };
 *     template<typename C, typename... Ts> struct PickOneSize<C&, Ts...> {
 *         static constexpr size_t Size(C& c, Ts...) { return c.size(); }
 *     };
 *
 * public:
 *     template<typename... Ts>
 *     static constexpr auto MakeZip(Ts&& ... ts) {
 *         // NOLINTBEGIN
 *         // Pick the first collection that has a size, and use that for the size.
 *         size_t size = PickOneSize<DecayPointerT<Ts>...>::Size(std::forward<Ts>(ts)...);
 *
 * #ifdef SK_DEBUG
 *         // Check that all sizes are the same.
 *         size_t minSize = SIZE_MAX;
 *         size_t maxSize = 0;
 *         size_t sizes[sizeof...(Ts)] = {Span<Ts>::Size(std::forward<Ts>(ts))...};
 *         for (size_t s : sizes) {
 *             if (s != SIZE_MAX) {
 *                 minSize = std::min(minSize, s);
 *                 maxSize = std::max(maxSize, s);
 *             }
 *         }
 *         SkASSERT(minSize == maxSize);
 * #endif
 *
 *         return SkZip<ValueType<Ts>...>(size, Span<Ts>::Data(std::forward<Ts>(ts))...);
 *         // NOLINTEND
 *     }
 * }
 * ```
 */
public open class SkMakeZipDetail {
  public open class DecayPointer<T>

  public open class ContiguousMemory<C>

  public open class ContiguousMemory<T> {
    public companion object {
      private fun `data`(t: Any): Any {
        TODO("Implement data")
      }

      private fun size(s: Any): Int {
        TODO("Implement size")
      }
    }
  }

  public open class ContiguousMemory<T> {
    public companion object {
      private fun `data`(param0: Any): Any {
        TODO("Implement data")
      }

      private fun size(param0: Any): Int {
        TODO("Implement size")
      }
    }
  }

  public open class ContiguousMemory<C> {
    public companion object {
      private fun `data`(c: Any): Int {
        TODO("Implement data")
      }

      private fun size(c: Any): Int {
        TODO("Implement size")
      }
    }
  }

  public open class PickOneSize<C, Ts>

  public open class PickOneSize<T, Ts> {
    private fun <T, Ts> size(param0: T, param1: Ts): Int {
      TODO("Implement size")
    }
  }

  public open class PickOneSize<T, Ts> {
    private fun <T, Ts, N> size(param0: Array<(Any) -> T>, param1: Ts): Int {
      TODO("Implement size")
    }
  }

  public open class PickOneSize<C, Ts> {
    private fun <C, Ts> size(param0: C, param1: Ts): Int {
      TODO("Implement size")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template<typename... Ts>
     *     static constexpr auto MakeZip(Ts&& ... ts) {
     *         // NOLINTBEGIN
     *         // Pick the first collection that has a size, and use that for the size.
     *         size_t size = PickOneSize<DecayPointerT<Ts>...>::Size(std::forward<Ts>(ts)...);
     *
     * #ifdef SK_DEBUG
     *         // Check that all sizes are the same.
     *         size_t minSize = SIZE_MAX;
     *         size_t maxSize = 0;
     *         size_t sizes[sizeof...(Ts)] = {Span<Ts>::Size(std::forward<Ts>(ts))...};
     *         for (size_t s : sizes) {
     *             if (s != SIZE_MAX) {
     *                 minSize = std::min(minSize, s);
     *                 maxSize = std::max(maxSize, s);
     *             }
     *         }
     *         SkASSERT(minSize == maxSize);
     * #endif
     *
     *         return SkZip<ValueType<Ts>...>(size, Span<Ts>::Data(std::forward<Ts>(ts))...);
     *         // NOLINTEND
     *     }
     * ```
     */
    public fun <Ts> makeZip(ts: `Ts&&`): Any {
      TODO("Implement makeZip")
    }
  }
}
