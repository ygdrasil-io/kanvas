package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class CtsEnforcement {
 * public:
 *     enum ApiLevel : int32_t {
 *         /* When used as fStrictVersion, always skip this test. It is not relevant to CTS.
 *          * When used as fWorkaroundsVersion, there are no API levels that should run the
 *          * test with workarounds.
 *          */
 *         kNever = INT32_MAX,
 *         /* The kApiLevel_* values are directly correlated with Android **vendor** API levels, which
 *          * are distinct from Android SDK API levels. Every new CTS/SkQP release has a corresponding
 *          * Android API level that will be captured by these enum values.
 *          */
 *         kApiLevel_T = 33,
 *         kApiLevel_U = 34,
 *         /* Beginning with Android 14-QPR3, vendor API levels (e.g. the ro.vendor.api_level system
 *          * property checked by SkQP) now follow a YYYYMM format.
 *          * See https://source.android.com/docs/core/architecture/api-flags for more information.
 *          */
 *         kApiLevel_202404 = 202404,
 *         kApiLevel_202504 = 202504,
 *         /* kNextRelease is a placeholder value that all new unit tests should use.  It implies that
 *          * this test will be enforced in the next Android release.  At the time of the release a
 *          * new kApiLevel_* value will be added and all current kNextRelease values will be replaced
 *          * with that new value.
 *          */
 *         kNextRelease = 202604
 *     };
 *
 *     /**
 *      * Tests will run in strict (no workarounds) mode if the device API level is >= strictVersion
 *      */
 *     constexpr CtsEnforcement(ApiLevel strictVersion)
 *             : fStrictVersion(strictVersion), fWorkaroundsVersion(kNever) {}
 *
 *     /**
 *      * Test will run with workarounds if the device API level is >= workaroundVersion
 *      * and < strictVersion
 *      */
 *     constexpr CtsEnforcement& withWorkarounds(ApiLevel workaroundVersion) {
 *         SkASSERT(workaroundVersion <= fStrictVersion);
 *         fWorkaroundsVersion = workaroundVersion;
 *         return *this;
 *     }
 *
 *     enum class RunMode { kSkip = 0, kRunWithWorkarounds = 1, kRunStrict = 2 };
 *     RunMode eval(int apiLevel) const;
 *
 * private:
 *     ApiLevel fStrictVersion;
 *     ApiLevel fWorkaroundsVersion;
 * }
 * ```
 */
public data class CtsEnforcement public constructor(
  /**
   * C++ original:
   * ```cpp
   * ApiLevel fStrictVersion
   * ```
   */
  private var fStrictVersion: ApiLevel,
  /**
   * C++ original:
   * ```cpp
   * ApiLevel fWorkaroundsVersion
   * ```
   */
  private var fWorkaroundsVersion: ApiLevel,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr CtsEnforcement& withWorkarounds(ApiLevel workaroundVersion) {
   *         SkASSERT(workaroundVersion <= fStrictVersion);
   *         fWorkaroundsVersion = workaroundVersion;
   *         return *this;
   *     }
   * ```
   */
  public fun withWorkarounds(workaroundVersion: ApiLevel): CtsEnforcement {
    TODO("Implement withWorkarounds")
  }

  /**
   * C++ original:
   * ```cpp
   * CtsEnforcement::RunMode CtsEnforcement::eval(int apiLevel) const {
   *     if (apiLevel >= fStrictVersion) {
   *         return RunMode::kRunStrict;
   *     } else if (apiLevel >= fWorkaroundsVersion) {
   *         return RunMode::kRunWithWorkarounds;
   *     }
   *     return RunMode::kSkip;
   * }
   * ```
   */
  public fun eval(apiLevel: Int): RunMode {
    TODO("Implement eval")
  }

  public enum class ApiLevel {
    kNever,
    kApiLevel_T,
    kApiLevel_U,
    kApiLevel_202404,
    kApiLevel_202504,
    kNextRelease,
  }

  public enum class RunMode {
    kSkip,
    kRunWithWorkarounds,
    kRunStrict,
  }
}
