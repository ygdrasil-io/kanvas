package org.skia.tools

import kotlin.Array
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkEventTracingCategories {
 * public:
 *     SkEventTracingCategories() : fNumCategories(0) {}
 *
 *     uint8_t*    getCategoryGroupEnabled(const char* name);
 *     const char* getCategoryGroupName(const uint8_t* categoryEnabledFlag);
 *
 * private:
 *     enum { kMaxCategories = 256 };
 *
 *     struct CategoryState {
 *         uint8_t     fEnabled;
 *         const char* fName;
 *     };
 *
 *     CategoryState fCategories[kMaxCategories];
 *     int           fNumCategories;
 *     SkMutex       fMutex;
 * }
 * ```
 */
public data class SkEventTracingCategories public constructor(
  /**
   * C++ original:
   * ```cpp
   * CategoryState fCategories[kMaxCategories]
   * ```
   */
  private var fCategories: Array<CategoryState>,
  /**
   * C++ original:
   * ```cpp
   * int           fNumCategories
   * ```
   */
  private var fNumCategories: Int,
  /**
   * C++ original:
   * ```cpp
   * SkMutex       fMutex
   * ```
   */
  private var fMutex: SkMutex,
) {
  /**
   * C++ original:
   * ```cpp
   * uint8_t* SkEventTracingCategories::getCategoryGroupEnabled(const char* name) {
   *     static_assert(0 == offsetof(CategoryState, fEnabled), "CategoryState");
   *
   *     // We ignore the "disabled-by-default-" prefix in our internal tools
   *     if (SkStrStartsWith(name, TRACE_CATEGORY_PREFIX)) {
   *         name += strlen(TRACE_CATEGORY_PREFIX);
   *     }
   *
   *     // Chrome's implementation of this API does a two-phase lookup (once without a lock, then again
   *     // with a lock. But the tracing macros avoid calling these functions more than once per site,
   *     // so just do something simple (and easier to reason about):
   *     SkAutoMutexExclusive lock(fMutex);
   *     for (int i = 0; i < fNumCategories; ++i) {
   *         if (0 == strcmp(name, fCategories[i].fName)) {
   *             return reinterpret_cast<uint8_t*>(&fCategories[i]);
   *         }
   *     }
   *
   *     if (fNumCategories >= kMaxCategories) {
   *         SkDEBUGFAIL("Exhausted event tracing categories. Increase kMaxCategories.");
   *         return reinterpret_cast<uint8_t*>(&fCategories[0]);
   *     }
   *
   *     fCategories[fNumCategories].fEnabled =
   *             CommandLineFlags::ShouldSkip(FLAGS_traceMatch, name)
   *                     ? 0
   *                     : SkEventTracer::kEnabledForRecording_CategoryGroupEnabledFlags;
   *
   *     fCategories[fNumCategories].fName = name;
   *     return reinterpret_cast<uint8_t*>(&fCategories[fNumCategories++]);
   * }
   * ```
   */
  public fun getCategoryGroupEnabled(name: String?): Int {
    TODO("Implement getCategoryGroupEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* SkEventTracingCategories::getCategoryGroupName(const uint8_t* categoryEnabledFlag) {
   *     if (categoryEnabledFlag) {
   *         return reinterpret_cast<const CategoryState*>(categoryEnabledFlag)->fName;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun getCategoryGroupName(categoryEnabledFlag: UByte?): Char {
    TODO("Implement getCategoryGroupName")
  }

  public data class CategoryState public constructor(
    public var fEnabled: Int,
    public val fName: String?,
  )

  public companion object {
    public val kMaxCategories: Int = TODO("Initialize kMaxCategories")
  }
}
