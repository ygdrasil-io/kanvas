package org.skia.tools

import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Stats {
 *     Stats(const skia_private::TArray<double>& samples, bool want_plot) {
 *         int n = samples.size();
 *         if (!n) {
 *             min = max = mean = var = median = 0;
 *             return;
 *         }
 *
 *         min = samples[0];
 *         max = samples[0];
 *         for (int i = 0; i < n; i++) {
 *             if (samples[i] < min) { min = samples[i]; }
 *             if (samples[i] > max) { max = samples[i]; }
 *         }
 *
 *         double sum = 0.0;
 *         for (int i = 0 ; i < n; i++) {
 *             sum += samples[i];
 *         }
 *         mean = sum / n;
 *
 *         double err = 0.0;
 *         for (int i = 0 ; i < n; i++) {
 *             err += (samples[i] - mean) * (samples[i] - mean);
 *         }
 *         var = sk_ieee_double_divide(err, n-1);
 *
 *         std::vector<double> sorted(samples.begin(), samples.end());
 *         std::sort(sorted.begin(), sorted.end());
 *         median = sorted[n/2];
 *
 *         // Normalize samples to [min, max] in as many quanta as we have distinct bars to print.
 *         for (int i = 0; want_plot && i < n; i++) {
 *             if (min == max) {
 *                 // All samples are the same value.  Don't divide by zero.
 *                 plot.append(kBars[0]);
 *                 continue;
 *             }
 *
 *             double s = samples[i];
 *             s -= min;
 *             s /= (max - min);
 *             s *= (std::size(kBars) - 1);
 *             const size_t bar = (size_t)(s + 0.5);
 *             SkASSERT_RELEASE(bar < std::size(kBars));
 *             plot.append(kBars[bar]);
 *         }
 *     }
 *
 *     double min;
 *     double max;
 *     double mean;    // Estimate of population mean.
 *     double var;     // Estimate of population variance.
 *     double median;
 *     SkString plot;  // A single-line bar chart (_not_ histogram) of the samples.
 * }
 * ```
 */
public data class Stats public constructor(
  /**
   * C++ original:
   * ```cpp
   * double min
   * ```
   */
  public var min: Double,
  /**
   * C++ original:
   * ```cpp
   * double max
   * ```
   */
  public var max: Double,
  /**
   * C++ original:
   * ```cpp
   * double mean
   * ```
   */
  public var mean: Double,
  /**
   * C++ original:
   * ```cpp
   * double var
   * ```
   */
  public var `var`: Double,
  /**
   * C++ original:
   * ```cpp
   * double median
   * ```
   */
  public var median: Double,
  /**
   * C++ original:
   * ```cpp
   * SkString plot
   * ```
   */
  public var plot: Int,
)
