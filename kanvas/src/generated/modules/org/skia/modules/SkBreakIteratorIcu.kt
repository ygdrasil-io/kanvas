package org.skia.modules

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import org.skia.`external`.Status
import org.skia.sksl.Position
import undefined.ICUBreakIterator

/**
 * C++ original:
 * ```cpp
 * class SkBreakIterator_icu : public SkBreakIterator {
 *     ICUBreakIterator fBreakIterator;
 *     Position fLastResult;
 *  public:
 *     explicit SkBreakIterator_icu(ICUBreakIterator iter)
 *             : fBreakIterator(std::move(iter))
 *             , fLastResult(0) {}
 *     Position first() override { return fLastResult = sk_ubrk_first(fBreakIterator.get()); }
 *     Position current() override { return fLastResult = sk_ubrk_current(fBreakIterator.get()); }
 *     Position next() override { return fLastResult = sk_ubrk_next(fBreakIterator.get()); }
 *     Status status() override { return sk_ubrk_getRuleStatus(fBreakIterator.get()); }
 *     bool isDone() override { return fLastResult == UBRK_DONE; }
 *
 *     bool setText(const char utftext8[], int utf8Units) override {
 *         UErrorCode status = U_ZERO_ERROR;
 *         ICUUText text(sk_utext_openUTF8(nullptr, &utftext8[0], utf8Units, &status));
 *
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         SkASSERT(text);
 *         sk_ubrk_setUText(fBreakIterator.get(), text.get(), &status);
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         fLastResult = 0;
 *         return true;
 *     }
 *     bool setText(const char16_t utftext16[], int utf16Units) override {
 *         UErrorCode status = U_ZERO_ERROR;
 *         ICUUText text(sk_utext_openUChars(nullptr, reinterpret_cast<const UChar*>(&utftext16[0]),
 *                                           utf16Units, &status));
 *
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         SkASSERT(text);
 *         sk_ubrk_setUText(fBreakIterator.get(), text.get(), &status);
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         fLastResult = 0;
 *         return true;
 *     }
 * }
 * ```
 */
public open class SkBreakIteratorIcu public constructor(
  iter: ICUBreakIterator,
) : SkBreakIterator() {
  /**
   * C++ original:
   * ```cpp
   * ICUBreakIterator fBreakIterator
   * ```
   */
  private var fBreakIterator: Int = TODO("Initialize fBreakIterator")

  /**
   * C++ original:
   * ```cpp
   * Position fLastResult
   * ```
   */
  private var fLastResult: Position = TODO("Initialize fLastResult")

  /**
   * C++ original:
   * ```cpp
   * Position first() override { return fLastResult = sk_ubrk_first(fBreakIterator.get()); }
   * ```
   */
  public override fun first(): Position {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * Position current() override { return fLastResult = sk_ubrk_current(fBreakIterator.get()); }
   * ```
   */
  public override fun current(): Position {
    TODO("Implement current")
  }

  /**
   * C++ original:
   * ```cpp
   * Position next() override { return fLastResult = sk_ubrk_next(fBreakIterator.get()); }
   * ```
   */
  public override fun next(): Position {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * Status status() override { return sk_ubrk_getRuleStatus(fBreakIterator.get()); }
   * ```
   */
  public override fun status(): Status {
    TODO("Implement status")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isDone() override { return fLastResult == UBRK_DONE; }
   * ```
   */
  public override fun isDone(): Boolean {
    TODO("Implement isDone")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setText(const char utftext8[], int utf8Units) override {
   *         UErrorCode status = U_ZERO_ERROR;
   *         ICUUText text(sk_utext_openUTF8(nullptr, &utftext8[0], utf8Units, &status));
   *
   *         if (U_FAILURE(status)) {
   *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
   *             return false;
   *         }
   *         SkASSERT(text);
   *         sk_ubrk_setUText(fBreakIterator.get(), text.get(), &status);
   *         if (U_FAILURE(status)) {
   *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
   *             return false;
   *         }
   *         fLastResult = 0;
   *         return true;
   *     }
   * ```
   */
  public override fun setText(utftext8: CharArray, utf8Units: Int): Boolean {
    TODO("Implement setText")
  }
}
