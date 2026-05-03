package org.skia.core

import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class GlyphRunListPainter {
 * public:
 *     GlyphRunListPainter(const SkSurfaceProps& props, SkColorType colorType, SkColorSpace* cs);
 *
 *     void drawForBitmapDevice(
 *             SkCanvas* canvas, const BitmapDevicePainter* bitmapDevice,
 *             const sktext::GlyphRunList& glyphRunList, const SkPaint& paint,
 *             const SkMatrix& drawMatrix);
 * private:
 *     // The props as on the actual device.
 *     const SkSurfaceProps fDeviceProps;
 *
 *     // The props for when the bitmap device can't draw LCD text.
 *     const SkSurfaceProps fBitmapFallbackProps;
 *     const SkColorType fColorType;
 *     const SkScalerContextFlags fScalerContextFlags;
 * }
 * ```
 */
public data class GlyphRunListPainter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fDeviceProps
   * ```
   */
  private val fDeviceProps: SkSurfaceProps,
  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fBitmapFallbackProps
   * ```
   */
  private val fBitmapFallbackProps: SkSurfaceProps,
  /**
   * C++ original:
   * ```cpp
   * const SkColorType fColorType
   * ```
   */
  private val fColorType: SkColorType,
  /**
   * C++ original:
   * ```cpp
   * const SkScalerContextFlags fScalerContextFlags
   * ```
   */
  private val fScalerContextFlags: SkScalerContextFlags,
) {
  /**
   * C++ original:
   * ```cpp
   * void GlyphRunListPainter::drawForBitmapDevice(SkCanvas* canvas,
   *                                               const BitmapDevicePainter* bitmapDevice,
   *                                               const sktext::GlyphRunList& glyphRunList,
   *                                               const SkPaint& paint,
   *                                               const SkMatrix& drawMatrix) {
   *     STArray<64, const SkGlyph*> acceptedPackedGlyphIDs;
   *     STArray<64, SkPoint> acceptedPositions;
   *     STArray<64, SkGlyphID> rejectedGlyphIDs;
   *     STArray<64, SkPoint> rejectedPositions;
   *     const int maxGlyphRunSize = glyphRunList.maxGlyphRunSize();
   *     acceptedPackedGlyphIDs.resize(maxGlyphRunSize);
   *     acceptedPositions.resize(maxGlyphRunSize);
   *     const auto acceptedBuffer = SkMakeZip(acceptedPackedGlyphIDs, acceptedPositions);
   *     rejectedGlyphIDs.resize(maxGlyphRunSize);
   *     rejectedPositions.resize(maxGlyphRunSize);
   *     const auto rejectedBuffer = SkMakeZip(rejectedGlyphIDs, rejectedPositions);
   *
   *     // The bitmap blitters can only draw lcd text to a N32 bitmap in srcOver. Otherwise,
   *     // convert the lcd text into A8 text. The props communicate this to the scaler.
   *     auto& props = (kN32_SkColorType == fColorType && paint.isSrcOver())
   *                           ? fDeviceProps
   *                           : fBitmapFallbackProps;
   *
   *     SkPoint drawOrigin = glyphRunList.origin();
   *     SkMatrix positionMatrix{drawMatrix};
   *     positionMatrix.preTranslate(drawOrigin.x(), drawOrigin.y());
   *     for (auto& glyphRun : glyphRunList) {
   *         const SkFont& runFont = glyphRun.font();
   *
   *         SkZip<const SkGlyphID, const SkPoint> source = glyphRun.source();
   *
   *         if (SkStrikeSpec::ShouldDrawAsPath(paint, runFont, positionMatrix)) {
   *             auto [strikeSpec, strikeToSourceScale] =
   *                     SkStrikeSpec::MakePath(runFont, paint, props, fScalerContextFlags);
   *
   *             auto strike = strikeSpec.findOrCreateStrike();
   *
   *             {
   *                 auto [accepted, rejected] = prepare_for_path_drawing(strike.get(),
   *                                                                      source,
   *                                                                      acceptedBuffer,
   *                                                                      rejectedBuffer);
   *
   *                 source = rejected;
   *                 // The paint we draw paths with must have the same anti-aliasing state as the
   *                 // runFont allowing the paths to have the same edging as the glyph masks.
   *                 SkPaint pathPaint = paint;
   *                 pathPaint.setAntiAlias(runFont.hasSomeAntiAliasing());
   *
   *                 const bool stroking = pathPaint.getStyle() != SkPaint::kFill_Style;
   *                 const bool hairline = pathPaint.getStrokeWidth() == 0;
   *                 const bool needsExactCTM = pathPaint.getShader()     ||
   *                                            pathPaint.getPathEffect() ||
   *                                            pathPaint.getMaskFilter() ||
   *                                            (stroking && !hairline);
   *
   *                 if (!needsExactCTM) {
   *                     for (auto [glyph, pos] : accepted) {
   *                         const SkPath* path = glyph->path();
   *                         SkMatrix m;
   *                         SkPoint translate = drawOrigin + pos;
   *                         m.setScaleTranslate(strikeToSourceScale, strikeToSourceScale,
   *                                             translate.x(), translate.y());
   *                         SkAutoCanvasRestore acr(canvas, true);
   *                         canvas->concat(m);
   *                         canvas->drawPath(*path, pathPaint);
   *                     }
   *                 } else {
   *                     for (auto [glyph, pos] : accepted) {
   *                         const SkPath* path = glyph->path();
   *                         SkMatrix m;
   *                         SkPoint translate = drawOrigin + pos;
   *                         m.setScaleTranslate(strikeToSourceScale, strikeToSourceScale,
   *                                             translate.x(), translate.y());
   *
   *                         SkPathBuilder builder;
   *                         builder.addPath(*path, m);
   *                         builder.setIsVolatile(true);
   *                         canvas->drawPath(builder.detach(), pathPaint);
   *                     }
   *                 }
   *             }
   *
   *             if (!source.empty()) {
   *                 auto [accepted, rejected] = prepare_for_drawable_drawing(strike.get(),
   *                                                                          source,
   *                                                                          acceptedBuffer,
   *                                                                          rejectedBuffer);
   *                 source = rejected;
   *
   *                 for (auto [glyph, pos] : accepted) {
   *                     SkDrawable* drawable = glyph->drawable();
   *                     SkMatrix m;
   *                     SkPoint translate = drawOrigin + pos;
   *                     m.setScaleTranslate(strikeToSourceScale, strikeToSourceScale,
   *                                         translate.x(), translate.y());
   *                     SkAutoCanvasRestore acr(canvas, false);
   *                     SkRect drawableBounds = drawable->getBounds();
   *                     m.mapRect(&drawableBounds);
   *                     canvas->saveLayer(&drawableBounds, &paint);
   *                     drawable->draw(canvas, &m);
   *                 }
   *             }
   *         }
   *         if (!source.empty() && !positionMatrix.hasPerspective()) {
   *             SkStrikeSpec strikeSpec = SkStrikeSpec::MakeMask(
   *                     runFont, paint, props, fScalerContextFlags, positionMatrix);
   *
   *             auto strike = strikeSpec.findOrCreateStrike();
   *
   *             auto [accepted, rejected] = prepare_for_direct_mask_drawing(strike.get(),
   *                                                                         positionMatrix,
   *                                                                         source,
   *                                                                         acceptedBuffer,
   *                                                                         rejectedBuffer);
   *             source = rejected;
   *             bitmapDevice->paintMasks(accepted, paint);
   *         }
   *         if (!source.empty()) {
   *             // Create a strike is source space to calculate scale information.
   *             SkStrikeSpec scaleStrikeSpec = SkStrikeSpec::MakeMask(
   *                     runFont, paint, props, fScalerContextFlags, SkMatrix::I());
   *             SkBulkGlyphMetrics metrics{scaleStrikeSpec};
   *
   *             auto glyphIDs = source.get<0>();
   *             auto positions = source.get<1>();
   *             SkSpan<const SkGlyph*> glyphs = metrics.glyphs(glyphIDs);
   *             SkScalar maxScale = SK_ScalarMin;
   *
   *             // Calculate the scale that makes the longest edge 1:1 with its side in the cache.
   *             for (auto [glyph, pos] : SkMakeZip(glyphs, positions)) {
   *                 if (glyph->isEmpty()) {
   *                     continue;
   *                 }
   *                 SkPoint corners[4];
   *                 SkRect rect = glyph->rect();
   *                 rect.makeOffset(drawOrigin + pos);
   *                 positionMatrix.mapRectToQuad(corners, rect);
   *                 // left top -> right top
   *                 SkScalar scale = (corners[1] - corners[0]).length() / rect.width();
   *                 maxScale = std::max(maxScale, scale);
   *                 // right top -> right bottom
   *                 scale = (corners[2] - corners[1]).length() / rect.height();
   *                 maxScale = std::max(maxScale, scale);
   *                 // right bottom -> left bottom
   *                 scale = (corners[3] - corners[2]).length() / rect.width();
   *                 maxScale = std::max(maxScale, scale);
   *                 // left bottom -> left top
   *                 scale = (corners[0] - corners[3]).length() / rect.height();
   *                 maxScale = std::max(maxScale, scale);
   *             }
   *
   *             if (maxScale <= 0) {
   *                 continue;  // to the next run.
   *             }
   *
   *             if (maxScale * runFont.getSize() > 256) {
   *                 maxScale = 256.0f / runFont.getSize();
   *             }
   *
   *             SkMatrix cacheScale = SkMatrix::Scale(maxScale, maxScale);
   *             SkStrikeSpec strikeSpec = SkStrikeSpec::MakeMask(
   *                     runFont, paint, props, fScalerContextFlags, cacheScale);
   *
   *             auto strike = strikeSpec.findOrCreateStrike();
   *
   *             auto [accepted, rejected] = prepare_for_direct_bitmap_drawing(strike.get(),
   *                                                                           positionMatrix,
   *                                                                           source,
   *                                                                           acceptedBuffer,
   *                                                                           rejectedBuffer);
   *             const SkScalar invMaxScale = 1.0f/maxScale;
   *             for (auto [glyph, srcPos] : accepted) {
   *                 SkMask mask = glyph->mask();
   *                 // TODO: is this needed will A8 and BW just work?
   *                 if (mask.fFormat != SkMask::kARGB32_Format) {
   *                     continue;
   *                 }
   *                 SkBitmap bm;
   *                 bm.installPixels(SkImageInfo::MakeN32Premul(mask.fBounds.size()),
   *                                  const_cast<uint8_t*>(mask.fImage),
   *                                  mask.fRowBytes);
   *                 bm.setImmutable();
   *
   *                 // Since the glyph in the cache is scaled by maxScale, its top left vector is too
   *                 // long. Reduce it to find proper positions on the device.
   *                 SkPoint pos = drawOrigin + srcPos
   *                             + SkPoint::Make(mask.fBounds.left(), mask.fBounds.top())*invMaxScale;
   *
   *                 // Calculate the preConcat matrix for drawBitmap to get the rectangle from the
   *                 // glyph cache (which is multiplied by maxScale) to land in the right place.
   *                 SkMatrix translate = SkMatrix::Translate(pos);
   *                 translate.preScale(invMaxScale, invMaxScale);
   *
   *                 // Draw the bitmap using the rect from the scaled cache, and not the source
   *                 // rectangle for the glyph.
   *                 bitmapDevice->drawBitmap(bm, translate, nullptr, SkFilterMode::kLinear, paint);
   *             }
   *         }
   *
   *         // TODO: have the mask stage above reject the glyphs that are too big, and handle the
   *         //  rejects in a more sophisticated stage.
   *     }
   * }
   * ```
   */
  public fun drawForBitmapDevice(
    canvas: SkCanvas?,
    bitmapDevice: BitmapDevicePainter?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
    drawMatrix: SkMatrix,
  ) {
    TODO("Implement drawForBitmapDevice")
  }
}
