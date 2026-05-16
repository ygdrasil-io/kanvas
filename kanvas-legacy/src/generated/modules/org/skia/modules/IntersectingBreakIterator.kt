package org.skia.modules

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import org.skia.`external`.Status
import org.skia.sksl.Position

/**
 * C++ original:
 * ```cpp
 * class IntersectingBreakIterator final : public SkBreakIterator {
 * public:
 *     IntersectingBreakIterator(std::unique_ptr<SkBreakIterator> a,
 *                               std::unique_ptr<SkBreakIterator> b)
 *         : fA(std::move(a))
 *         , fB(std::move(b))
 *     {
 *         SkASSERT(fA);
 *         SkASSERT(fB);
 *     }
 *
 *     Position first() override {
 *         fCurrent = fA->first();
 *         SkAssertResult(fB->first() == fCurrent);
 *         fDone = false;
 *         return fCurrent;
 *     }
 *
 *     Position current() override {
 *         return fCurrent;
 *     }
 *
 *     bool isDone() override {
 *         SkASSERT((fA->isDone() || fB->isDone()) == fDone);
 *         return fDone;
 *     }
 *
 *     Position next() override {
 *         SkASSERT(fDone || (fA->current() == fB->current()));
 *         Position pos_a = fCurrent,
 *                  pos_b = fCurrent;
 *
 *         while (!fDone) {
 *             if (pos_a < pos_b) {
 *                 pos_a = fA->next();
 *                 fDone = fA->isDone();
 *             } else {
 *                 pos_b = fB->next();
 *                 fDone = fB->isDone();
 *             }
 *
 *             if (pos_a == pos_b) {
 *                 break;
 *             }
 *         }
 *
 *         // At this point the positions are either valid and equal (advanced successfully),
 *         // or one of them is negative (for the done iterator);
 *         SkASSERT(pos_a == pos_b || fDone);
 *         SkASSERT((pos_a < 0 || pos_b < 0) == fDone);
 *
 *         return fCurrent = std::min(pos_a, pos_b);
 *     }
 *
 *     bool setText(const char utftext8[], int utf8Units) override {
 *         fDone = false;
 *         fCurrent = 0;
 *         return fA->setText(utftext8, utf8Units) &&
 *                fB->setText(utftext8, utf8Units);
 *     }
 *
 *     bool setText(const char16_t utftext16[], int utf16Units) override {
 *         fDone = false;
 *         fCurrent = 0;
 *         return fA->setText(utftext16, utf16Units) &&
 *                fB->setText(utftext16, utf16Units);
 *     }
 *
 *     Status status() override {
 *         SkUNREACHABLE;
 *     }
 *
 * private:
 *     const std::unique_ptr<SkBreakIterator> fA, fB;
 *     Position                               fCurrent = 0;
 *     bool                                   fDone    = false;
 * }
 * ```
 */
public class IntersectingBreakIterator public constructor(
  a: SkBreakIterator?,
  b: SkBreakIterator?,
) : SkBreakIterator() {
  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<SkBreakIterator> fA
   * ```
   */
  private val fA: Int = TODO("Initialize fA")

  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<SkBreakIterator> fA, fB
   * ```
   */
  private val fB: Int = TODO("Initialize fB")

  /**
   * C++ original:
   * ```cpp
   * Position                               fCurrent = 0
   * ```
   */
  private var fCurrent: Position = TODO("Initialize fCurrent")

  /**
   * C++ original:
   * ```cpp
   * bool                                   fDone    = false
   * ```
   */
  private var fDone: Boolean = TODO("Initialize fDone")

  /**
   * C++ original:
   * ```cpp
   * Position first() override {
   *         fCurrent = fA->first();
   *         SkAssertResult(fB->first() == fCurrent);
   *         fDone = false;
   *         return fCurrent;
   *     }
   * ```
   */
  public override fun first(): Position {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * Position current() override {
   *         return fCurrent;
   *     }
   * ```
   */
  public override fun current(): Position {
    TODO("Implement current")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isDone() override {
   *         SkASSERT((fA->isDone() || fB->isDone()) == fDone);
   *         return fDone;
   *     }
   * ```
   */
  public override fun isDone(): Boolean {
    TODO("Implement isDone")
  }

  /**
   * C++ original:
   * ```cpp
   * Position next() override {
   *         SkASSERT(fDone || (fA->current() == fB->current()));
   *         Position pos_a = fCurrent,
   *                  pos_b = fCurrent;
   *
   *         while (!fDone) {
   *             if (pos_a < pos_b) {
   *                 pos_a = fA->next();
   *                 fDone = fA->isDone();
   *             } else {
   *                 pos_b = fB->next();
   *                 fDone = fB->isDone();
   *             }
   *
   *             if (pos_a == pos_b) {
   *                 break;
   *             }
   *         }
   *
   *         // At this point the positions are either valid and equal (advanced successfully),
   *         // or one of them is negative (for the done iterator);
   *         SkASSERT(pos_a == pos_b || fDone);
   *         SkASSERT((pos_a < 0 || pos_b < 0) == fDone);
   *
   *         return fCurrent = std::min(pos_a, pos_b);
   *     }
   * ```
   */
  public override fun next(): Position {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setText(const char utftext8[], int utf8Units) override {
   *         fDone = false;
   *         fCurrent = 0;
   *         return fA->setText(utftext8, utf8Units) &&
   *                fB->setText(utftext8, utf8Units);
   *     }
   * ```
   */
  public override fun setText(utftext8: CharArray, utf8Units: Int): Boolean {
    TODO("Implement setText")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setText(const char16_t utftext16[], int utf16Units) override {
   *         fDone = false;
   *         fCurrent = 0;
   *         return fA->setText(utftext16, utf16Units) &&
   *                fB->setText(utftext16, utf16Units);
   *     }
   * ```
   */
  public override fun status(): Status {
    TODO("Implement status")
  }
}
