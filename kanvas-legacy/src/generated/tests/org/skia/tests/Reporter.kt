package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class Reporter : SkNoncopyable {
 * public:
 *     virtual ~Reporter() {}
 *     virtual void bumpTestCount();
 *     virtual void reportFailed(const skiatest::Failure&) = 0;
 *     virtual bool allowExtendedTest() const;
 *     virtual bool verbose() const;
 *     virtual void* stats() const { return nullptr; }
 *
 *     void reportFailedWithContext(const skiatest::Failure&);
 *
 *     /**
 *      * Show additional context (e.g. subtest name) on failure assertions.
 *      */
 *     void push(const SkString& message) {
 *         fContextStack.push_back(message);
 *     }
 *     void push(const std::string& message) {
 *         fContextStack.push_back(SkString(message));
 *     }
 *
 *     /**
 *      * Remove additional context from failure assertions.
 *      */
 *     void pop() {
 *         fContextStack.pop_back();
 *     }
 *
 * private:
 *     skia_private::TArray<SkString> fContextStack;
 * }
 * ```
 */
public abstract class Reporter : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkString> fContextStack
   * ```
   */
  private var fContextStack: Int = TODO("Initialize fContextStack")

  /**
   * C++ original:
   * ```cpp
   * void skiatest::Reporter::bumpTestCount() {}
   * ```
   */
  public open fun bumpTestCount() {
    TODO("Implement bumpTestCount")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void reportFailed(const skiatest::Failure&) = 0
   * ```
   */
  public abstract fun reportFailed(param0: Failure)

  /**
   * C++ original:
   * ```cpp
   * bool skiatest::Reporter::allowExtendedTest() const { return false; }
   * ```
   */
  public open fun allowExtendedTest(): Boolean {
    TODO("Implement allowExtendedTest")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skiatest::Reporter::verbose() const { return false; }
   * ```
   */
  public open fun verbose(): Boolean {
    TODO("Implement verbose")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void* stats() const { return nullptr; }
   * ```
   */
  public open fun stats() {
    TODO("Implement stats")
  }

  /**
   * C++ original:
   * ```cpp
   * void skiatest::Reporter::reportFailedWithContext(const skiatest::Failure& f) {
   *     SkString fullMessage = f.message;
   *     if (!fContextStack.empty()) {
   *         fullMessage.append(" [");
   *         for (int i = 0; i < fContextStack.size(); ++i) {
   *             if (i > 0) {
   *                 fullMessage.append(", ");
   *             }
   *             fullMessage.append(fContextStack[i]);
   *         }
   *         fullMessage.append("]");
   *     }
   *     this->reportFailed(skiatest::Failure(f.fileName, f.lineNo, f.condition, fullMessage));
   * }
   * ```
   */
  public fun reportFailedWithContext(f: Failure) {
    TODO("Implement reportFailedWithContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void push(const SkString& message) {
   *         fContextStack.push_back(message);
   *     }
   * ```
   */
  public fun push(message: String) {
    TODO("Implement push")
  }

  /**
   * C++ original:
   * ```cpp
   * void push(const std::string& message) {
   *         fContextStack.push_back(SkString(message));
   *     }
   * ```
   */
  public fun pop() {
    TODO("Implement pop")
  }
}
