package org.skia.core

/**
 * C++ original:
 * ```cpp
 * template<typename T>
 * class ParameterSpace {
 * public:
 *     ParameterSpace() = default;
 *     explicit ParameterSpace(const T& data) : fData(data) {}
 *     explicit ParameterSpace(T&& data) : fData(std::move(data)) {}
 *
 *     explicit operator const T&() const { return fData; }
 *
 * private:
 *     T fData;
 * }
 * ```
 */
public data class ParameterSpace<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T fData
   * ```
   */
  private var fData: T,
)
