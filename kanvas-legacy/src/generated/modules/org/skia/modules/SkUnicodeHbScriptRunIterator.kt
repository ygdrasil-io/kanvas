package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.`external`.HbScriptT
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class SkUnicodeHbScriptRunIterator final: public SkShaper::ScriptRunIterator {
 * public:
 *     SkUnicodeHbScriptRunIterator(const char* utf8,
 *                                  size_t utf8Bytes,
 *                                  hb_script_t defaultScript)
 *             : fCurrent(utf8)
 *             , fBegin(utf8)
 *             , fEnd(fCurrent + utf8Bytes)
 *             , fCurrentScript(defaultScript) {}
 *     hb_script_t hb_script_for_unichar(SkUnichar u) {
 *          return hb_unicode_script(hb_unicode_funcs_get_default(), u);
 *     }
 *     void consume() override {
 *         SkASSERT(fCurrent < fEnd);
 *         SkUnichar u = utf8_next(&fCurrent, fEnd);
 *         fCurrentScript = hb_script_for_unichar(u);
 *         while (fCurrent < fEnd) {
 *             const char* prev = fCurrent;
 *             u = utf8_next(&fCurrent, fEnd);
 *             const hb_script_t script = hb_script_for_unichar(u);
 *             if (script != fCurrentScript) {
 *                 if (fCurrentScript == HB_SCRIPT_INHERITED || fCurrentScript == HB_SCRIPT_COMMON) {
 *                     fCurrentScript = script;
 *                 } else if (script == HB_SCRIPT_INHERITED || script == HB_SCRIPT_COMMON) {
 *                     continue;
 *                 } else {
 *                     fCurrent = prev;
 *                     break;
 *                 }
 *             }
 *         }
 *         if (fCurrentScript == HB_SCRIPT_INHERITED) {
 *             fCurrentScript = HB_SCRIPT_COMMON;
 *         }
 *     }
 *     size_t endOfCurrentRun() const override {
 *         return fCurrent - fBegin;
 *     }
 *     bool atEnd() const override {
 *         return fCurrent == fEnd;
 *     }
 *
 *     SkFourByteTag currentScript() const override {
 *         return SkSetFourByteTag(HB_UNTAG(fCurrentScript));
 *     }
 * private:
 *     char const * fCurrent;
 *     char const * const fBegin;
 *     char const * const fEnd;
 *     hb_script_t fCurrentScript;
 * }
 * ```
 */
public class SkUnicodeHbScriptRunIterator public constructor(
  utf8: String?,
  utf8Bytes: ULong,
  defaultScript: HbScriptT,
) : SkShaper.ScriptRunIterator() {
  /**
   * C++ original:
   * ```cpp
   * char const * fCurrent
   * ```
   */
  private val fCurrent: String? = TODO("Initialize fCurrent")

  /**
   * C++ original:
   * ```cpp
   * char const * const fBegin
   * ```
   */
  private val fBegin: String? = TODO("Initialize fBegin")

  /**
   * C++ original:
   * ```cpp
   * char const * const fEnd
   * ```
   */
  private val fEnd: String? = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * hb_script_t fCurrentScript
   * ```
   */
  private var fCurrentScript: HbScriptT = TODO("Initialize fCurrentScript")

  /**
   * C++ original:
   * ```cpp
   * hb_script_t hb_script_for_unichar(SkUnichar u) {
   *          return hb_unicode_script(hb_unicode_funcs_get_default(), u);
   *     }
   * ```
   */
  public fun hbScriptForUnichar(u: SkUnichar): HbScriptT {
    TODO("Implement hbScriptForUnichar")
  }

  /**
   * C++ original:
   * ```cpp
   * void consume() override {
   *         SkASSERT(fCurrent < fEnd);
   *         SkUnichar u = utf8_next(&fCurrent, fEnd);
   *         fCurrentScript = hb_script_for_unichar(u);
   *         while (fCurrent < fEnd) {
   *             const char* prev = fCurrent;
   *             u = utf8_next(&fCurrent, fEnd);
   *             const hb_script_t script = hb_script_for_unichar(u);
   *             if (script != fCurrentScript) {
   *                 if (fCurrentScript == HB_SCRIPT_INHERITED || fCurrentScript == HB_SCRIPT_COMMON) {
   *                     fCurrentScript = script;
   *                 } else if (script == HB_SCRIPT_INHERITED || script == HB_SCRIPT_COMMON) {
   *                     continue;
   *                 } else {
   *                     fCurrent = prev;
   *                     break;
   *                 }
   *             }
   *         }
   *         if (fCurrentScript == HB_SCRIPT_INHERITED) {
   *             fCurrentScript = HB_SCRIPT_COMMON;
   *         }
   *     }
   * ```
   */
  public override fun consume() {
    TODO("Implement consume")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t endOfCurrentRun() const override {
   *         return fCurrent - fBegin;
   *     }
   * ```
   */
  public override fun endOfCurrentRun(): ULong {
    TODO("Implement endOfCurrentRun")
  }

  /**
   * C++ original:
   * ```cpp
   * bool atEnd() const override {
   *         return fCurrent == fEnd;
   *     }
   * ```
   */
  public override fun atEnd(): Boolean {
    TODO("Implement atEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFourByteTag currentScript() const override {
   *         return SkSetFourByteTag(HB_UNTAG(fCurrentScript));
   *     }
   * ```
   */
  public override fun currentScript(): Int {
    TODO("Implement currentScript")
  }
}
