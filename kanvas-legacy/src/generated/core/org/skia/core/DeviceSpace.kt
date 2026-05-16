package org.skia.core

/**
 * C++ original:
 * ```cpp
 * template<typename T>
 * class DeviceSpace {
 * public:
 *     DeviceSpace() = default;
 *     explicit DeviceSpace(const T& data) : fData(data) {}
 *     explicit DeviceSpace(T&& data) : fData(std::move(data)) {}
 *
 *     explicit operator const T&() const { return fData; }
 *
 * private:
 *     T fData;
 * }
 * ```
 */
public data class DeviceSpace<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T fData
   * ```
   */
  private var fData: T,
)
