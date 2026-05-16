package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class MockProperty final : public AnimatablePropertyContainer {
 * public:
 *     explicit MockProperty(const char* jprop) {
 *         AnimationBuilder abuilder(nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr,
 *                                   nullptr, nullptr,
 *                                   {100, 100}, 10, 1, 0);
 *         skjson::DOM json_dom(jprop, strlen(jprop));
 *
 *         fDidBind = this->bind(abuilder, json_dom.root(), &fValue);
 *     }
 *
 *     explicit operator bool() const { return fDidBind; }
 *
 *     const T& operator()(float t) { this->seek(t); return fValue; }
 *
 * private:
 *     void onSync() override {}
 *
 *     T     fValue = T();
 *     bool  fDidBind;
 * }
 * ```
 */
public open class MockProperty<T> public constructor(
  jprop: String?,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * T     fValue = T()
   * ```
   */
  private var fValue: T = TODO("Initialize fValue")

  /**
   * C++ original:
   * ```cpp
   * bool  fDidBind
   * ```
   */
  private var fDidBind: Boolean = TODO("Initialize fDidBind")

  /**
   * C++ original:
   * ```cpp
   * const T& operator()(float t) { this->seek(t); return fValue; }
   * ```
   */
  public operator fun invoke(t: Float): T {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {}
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
