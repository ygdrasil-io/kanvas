package org.skia.utils

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct sk_base_callable_traits {
 *     using return_type = R;
 *     static constexpr std::size_t arity = sizeof...(Args);
 *     template <std::size_t N> struct argument {
 *         static_assert(N < arity, "");
 *         using type = typename std::tuple_element<N, std::tuple<Args...>>::type;
 *     };
 * }
 * ```
 */
public open class SkBaseCallableTraits<R, Args> {
  public open class Argument

  public companion object {
    private val arity: Int = TODO("Initialize arity")
  }
}
