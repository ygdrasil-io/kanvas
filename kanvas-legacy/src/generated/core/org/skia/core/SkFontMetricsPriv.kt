package org.skia.core

import kotlin.Int
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkFontMetricsPriv {
 * public:
 *     static void Flatten(SkWriteBuffer& buffer, const SkFontMetrics& metrics);
 *     static std::optional<SkFontMetrics> MakeFromBuffer(SkReadBuffer& buffer);
 * }
 * ```
 */
public open class SkFontMetricsPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkFontMetricsPriv::Flatten(SkWriteBuffer& buffer, const SkFontMetrics& metrics) {
     *     buffer.writeUInt(metrics.fFlags);
     *     buffer.writeScalar(metrics.fTop);
     *     buffer.writeScalar(metrics.fAscent);
     *     buffer.writeScalar(metrics.fDescent);
     *     buffer.writeScalar(metrics.fBottom);
     *     buffer.writeScalar(metrics.fLeading);
     *     buffer.writeScalar(metrics.fAvgCharWidth);
     *     buffer.writeScalar(metrics.fMaxCharWidth);
     *     buffer.writeScalar(metrics.fXMin);
     *     buffer.writeScalar(metrics.fXMax);
     *     buffer.writeScalar(metrics.fXHeight);
     *     buffer.writeScalar(metrics.fCapHeight);
     *     buffer.writeScalar(metrics.fUnderlineThickness);
     *     buffer.writeScalar(metrics.fUnderlinePosition);
     *     buffer.writeScalar(metrics.fStrikeoutThickness);
     *     buffer.writeScalar(metrics.fStrikeoutPosition);
     * }
     * ```
     */
    public fun flatten(buffer: SkWriteBuffer, metrics: SkFontMetrics) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkFontMetrics> SkFontMetricsPriv::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkASSERT(buffer.isValid());
     *
     *     SkFontMetrics metrics;
     *     metrics.fFlags = buffer.readUInt();
     *     metrics.fTop = buffer.readScalar();
     *     metrics.fAscent = buffer.readScalar();
     *     metrics.fDescent = buffer.readScalar();
     *     metrics.fBottom = buffer.readScalar();
     *     metrics.fLeading = buffer.readScalar();
     *     metrics.fAvgCharWidth = buffer.readScalar();
     *     metrics.fMaxCharWidth = buffer.readScalar();
     *     metrics.fXMin = buffer.readScalar();
     *     metrics.fXMax = buffer.readScalar();
     *     metrics.fXHeight = buffer.readScalar();
     *     metrics.fCapHeight = buffer.readScalar();
     *     metrics.fUnderlineThickness = buffer.readScalar();
     *     metrics.fUnderlinePosition = buffer.readScalar();
     *     metrics.fStrikeoutThickness = buffer.readScalar();
     *     metrics.fStrikeoutPosition = buffer.readScalar();
     *
     *     // All the reads above were valid, so return the metrics.
     *     if (buffer.isValid()) {
     *         return metrics;
     *     }
     *
     *     return std::nullopt;
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
