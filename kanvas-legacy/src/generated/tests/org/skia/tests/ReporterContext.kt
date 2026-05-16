package org.skia.tests

import kotlin.String
import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class ReporterContext : SkNoncopyable {
 * public:
 *     ReporterContext(Reporter* reporter, const SkString& message) : fReporter(reporter) {
 *         fReporter->push(message);
 *     }
 *     ReporterContext(Reporter* reporter, const std::string& message) : fReporter(reporter) {
 *         fReporter->push(message);
 *     }
 *     ~ReporterContext() {
 *         fReporter->pop();
 *     }
 *
 * private:
 *     Reporter* fReporter;
 * }
 * ```
 */
public open class ReporterContext public constructor(
  reporter: Reporter?,
  message: String,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * Reporter* fReporter
   * ```
   */
  private var fReporter: Reporter? = TODO("Initialize fReporter")
}
