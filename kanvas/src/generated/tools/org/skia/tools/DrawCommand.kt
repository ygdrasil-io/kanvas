package org.skia.tools

import SkColor4f
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkWStream
import org.skia.json.SkJSONWriter
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DrawCommand {
 * public:
 *     enum OpType {
 *         kBeginDrawPicture_OpType,
 *         kClear_OpType,
 *         kClipPath_OpType,
 *         kClipRegion_OpType,
 *         kClipRect_OpType,
 *         kClipRRect_OpType,
 *         kClipShader_OpType,
 *         kResetClip_OpType,
 *         kConcat_OpType,
 *         kConcat44_OpType,
 *         kDrawAnnotation_OpType,
 *         kDrawBitmap_OpType,
 *         kDrawBitmapRect_OpType,
 *         kDrawDRRect_OpType,
 *         kDrawImage_OpType,
 *         kDrawImageLattice_OpType,
 *         kDrawImageRect_OpType,
 *         kDrawImageRectLayer_OpType, // unique to DebugCanvas
 *         kDrawOval_OpType,
 *         kDrawArc_OpType,
 *         kDrawPaint_OpType,
 *         kDrawPatch_OpType,
 *         kDrawPath_OpType,
 *         kDrawPoints_OpType,
 *         kDrawRect_OpType,
 *         kDrawRRect_OpType,
 *         kDrawRegion_OpType,
 *         kDrawShadow_OpType,
 *         kDrawTextBlob_OpType,
 *         kDrawVertices_OpType,
 *         kDrawAtlas_OpType,
 *         kDrawDrawable_OpType,
 *         kDrawEdgeAAQuad_OpType,
 *         kDrawEdgeAAImageSet_OpType,
 *         kEndDrawPicture_OpType,
 *         kRestore_OpType,
 *         kSave_OpType,
 *         kSaveLayer_OpType,
 *         kSetMatrix_OpType,
 *         kSetM44_OpType,
 *
 *         kLast_OpType = kSetM44_OpType
 *     };
 *
 *     static const int kOpTypeCount = kLast_OpType + 1;
 *
 *     static void WritePNG(const SkBitmap& bitmap, SkWStream& out);
 *
 *     explicit DrawCommand(OpType opType);
 *
 *     virtual ~DrawCommand() {}
 *
 *     bool isVisible() const { return fVisible; }
 *
 *     void setVisible(bool toggle) { fVisible = toggle; }
 *
 *     virtual void execute(SkCanvas*) const = 0;
 *
 *     virtual bool render(SkCanvas* canvas) const { return false; }
 *
 *     virtual void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const;
 *
 *     static const char* GetCommandString(OpType type);
 *
 *     // Helper methods for converting things to JSON
 *     static void MakeJsonColor(SkJSONWriter&, const SkColor color);
 *     static void MakeJsonColor4f(SkJSONWriter&, const SkColor4f& color);
 *     static void MakeJsonPoint(SkJSONWriter&, const SkPoint& point);
 *     static void MakeJsonPoint(SkJSONWriter&, SkScalar x, SkScalar y);
 *     static void MakeJsonPoint3(SkJSONWriter&, const SkPoint3& point);
 *     static void MakeJsonRect(SkJSONWriter&, const SkRect& rect);
 *     static void MakeJsonIRect(SkJSONWriter&, const SkIRect&);
 *     static void MakeJsonMatrix(SkJSONWriter&, const SkMatrix&);
 *     static void MakeJsonMatrix44(SkJSONWriter&, const SkM44&);
 *     static void MakeJsonPath(SkJSONWriter&, const SkPath& path);
 *     static void MakeJsonRegion(SkJSONWriter&, const SkRegion& region);
 *     static void MakeJsonSampling(SkJSONWriter&, const SkSamplingOptions& sampling);
 *     static void MakeJsonPaint(SkJSONWriter&, const SkPaint& paint, UrlDataManager& urlDataManager);
 *     static void MakeJsonLattice(SkJSONWriter&, const SkCanvas::Lattice& lattice);
 *
 *     static void flatten(const SkFlattenable* flattenable,
 *                         SkJSONWriter&        writer,
 *                         UrlDataManager&      urlDataManager);
 *     static bool flatten(const SkImage& image, SkJSONWriter& writer, UrlDataManager& urlDataManager);
 *     static bool flatten(const SkBitmap& bitmap,
 *                         SkJSONWriter&   writer,
 *                         UrlDataManager& urlDataManager);
 *     OpType getOpType() const { return fOpType; }
 *
 * private:
 *     OpType fOpType;
 *     bool   fVisible;
 * }
 * ```
 */
public abstract class DrawCommand public constructor(
  opType: OpType,
) {
  /**
   * C++ original:
   * ```cpp
   * static const int kOpTypeCount = kLast_OpType + 1
   * ```
   */
  private var fOpType: OpType = TODO("Initialize fOpType")

  /**
   * C++ original:
   * ```cpp
   * OpType fOpType
   * ```
   */
  private var fVisible: Boolean = TODO("Initialize fVisible")

  /**
   * C++ original:
   * ```cpp
   * bool isVisible() const { return fVisible; }
   * ```
   */
  public fun isVisible(): Boolean {
    TODO("Implement isVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * void setVisible(bool toggle) { fVisible = toggle; }
   * ```
   */
  public fun setVisible(toggle: Boolean) {
    TODO("Implement setVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void execute(SkCanvas*) const = 0
   * ```
   */
  public abstract fun execute(param0: SkCanvas?)

  /**
   * C++ original:
   * ```cpp
   * virtual bool render(SkCanvas* canvas) const { return false; }
   * ```
   */
  public open fun render(canvas: SkCanvas?): Boolean {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_COMMAND, GetCommandString(fOpType));
   *     writer.appendBool(DEBUGCANVAS_ATTRIBUTE_VISIBLE, this->isVisible());
   * }
   * ```
   */
  public open fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }

  /**
   * C++ original:
   * ```cpp
   * OpType getOpType() const { return fOpType; }
   * ```
   */
  public fun getOpType(): OpType {
    TODO("Implement getOpType")
  }

  public enum class OpType {
    kBeginDrawPicture_OpType,
    kClear_OpType,
    kClipPath_OpType,
    kClipRegion_OpType,
    kClipRect_OpType,
    kClipRRect_OpType,
    kClipShader_OpType,
    kResetClip_OpType,
    kConcat_OpType,
    kConcat44_OpType,
    kDrawAnnotation_OpType,
    kDrawBitmap_OpType,
    kDrawBitmapRect_OpType,
    kDrawDRRect_OpType,
    kDrawImage_OpType,
    kDrawImageLattice_OpType,
    kDrawImageRect_OpType,
    kDrawImageRectLayer_OpType,
    kDrawOval_OpType,
    kDrawArc_OpType,
    kDrawPaint_OpType,
    kDrawPatch_OpType,
    kDrawPath_OpType,
    kDrawPoints_OpType,
    kDrawRect_OpType,
    kDrawRRect_OpType,
    kDrawRegion_OpType,
    kDrawShadow_OpType,
    kDrawTextBlob_OpType,
    kDrawVertices_OpType,
    kDrawAtlas_OpType,
    kDrawDrawable_OpType,
    kDrawEdgeAAQuad_OpType,
    kDrawEdgeAAImageSet_OpType,
    kEndDrawPicture_OpType,
    kRestore_OpType,
    kSave_OpType,
    kSaveLayer_OpType,
    kSetMatrix_OpType,
    kSetM44_OpType,
    kLast_OpType,
  }

  public companion object {
    public val kOpTypeCount: Int = TODO("Initialize kOpTypeCount")

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::WritePNG(const SkBitmap& bitmap, SkWStream& out) {
     *     SkPixmap pm;
     *     SkAssertResult(bitmap.peekPixels(&pm));
     *
     *     SkPngEncoder::Options options;
     *     options.fZLibLevel   = 1;
     *     options.fFilterFlags = SkPngEncoder::FilterFlag::kNone;
     *     SkPngEncoder::Encode(&out, pm, options);
     * }
     * ```
     */
    public fun writePNG(bitmap: SkBitmap, `out`: SkWStream) {
      TODO("Implement writePNG")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* DrawCommand::GetCommandString(OpType type) {
     *     switch (type) {
     *         case kBeginDrawPicture_OpType: return "BeginDrawPicture";
     *         case kClear_OpType: return "DrawClear";
     *         case kClipPath_OpType: return "ClipPath";
     *         case kClipRegion_OpType: return "ClipRegion";
     *         case kClipRect_OpType: return "ClipRect";
     *         case kClipRRect_OpType: return "ClipRRect";
     *         case kResetClip_OpType: return "ResetClip";
     *         case kConcat_OpType: return "Concat";
     *         case kConcat44_OpType: return "Concat44";
     *         case kDrawAnnotation_OpType: return "DrawAnnotation";
     *         case kDrawBitmap_OpType: return "DrawBitmap";
     *         case kDrawBitmapRect_OpType: return "DrawBitmapRect";
     *         case kDrawDRRect_OpType: return "DrawDRRect";
     *         case kDrawImage_OpType: return "DrawImage";
     *         case kDrawImageLattice_OpType: return "DrawImageLattice";
     *         case kDrawImageRect_OpType: return "DrawImageRect";
     *         case kDrawImageRectLayer_OpType: return "DrawImageRectLayer";
     *         case kDrawOval_OpType: return "DrawOval";
     *         case kDrawPaint_OpType: return "DrawPaint";
     *         case kDrawPatch_OpType: return "DrawPatch";
     *         case kDrawPath_OpType: return "DrawPath";
     *         case kDrawArc_OpType: return "DrawArc";
     *         case kDrawPoints_OpType: return "DrawPoints";
     *         case kDrawRect_OpType: return "DrawRect";
     *         case kDrawRRect_OpType: return "DrawRRect";
     *         case kDrawRegion_OpType: return "DrawRegion";
     *         case kDrawShadow_OpType: return "DrawShadow";
     *         case kDrawTextBlob_OpType: return "DrawTextBlob";
     *         case kDrawVertices_OpType: return "DrawVertices";
     *         case kDrawAtlas_OpType: return "DrawAtlas";
     *         case kDrawDrawable_OpType: return "DrawDrawable";
     *         case kDrawEdgeAAQuad_OpType: return "DrawEdgeAAQuad";
     *         case kDrawEdgeAAImageSet_OpType: return "DrawEdgeAAImageSet";
     *         case kEndDrawPicture_OpType: return "EndDrawPicture";
     *         case kRestore_OpType: return "Restore";
     *         case kSave_OpType: return "Save";
     *         case kSaveLayer_OpType: return "SaveLayer";
     *         case kSetMatrix_OpType: return "SetMatrix";
     *         case kSetM44_OpType: return "SetM44";
     *         default:
     *             SkDebugf("OpType error 0x%08x\n", type);
     *             SkASSERT(0);
     *             break;
     *     }
     *     SkDEBUGFAIL("DrawType UNUSED\n");
     *     return nullptr;
     * }
     * ```
     */
    public fun getCommandString(type: OpType): Char {
      TODO("Implement getCommandString")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonColor(SkJSONWriter& writer, const SkColor color) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendS32(SkColorGetA(color));
     *     writer.appendS32(SkColorGetR(color));
     *     writer.appendS32(SkColorGetG(color));
     *     writer.appendS32(SkColorGetB(color));
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonColor(writer: SkJSONWriter, color: SkColor) {
      TODO("Implement makeJsonColor")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonColor4f(SkJSONWriter& writer, const SkColor4f& color) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendFloat(color.fA);
     *     writer.appendFloat(color.fR);
     *     writer.appendFloat(color.fG);
     *     writer.appendFloat(color.fB);
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonColor4f(writer: SkJSONWriter, color: SkColor4f) {
      TODO("Implement makeJsonColor4f")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonPoint(SkJSONWriter& writer, const SkPoint& point) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendFloat(point.x());
     *     writer.appendFloat(point.y());
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonPoint(writer: SkJSONWriter, point: SkPoint) {
      TODO("Implement makeJsonPoint")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonPoint(SkJSONWriter& writer, SkScalar x, SkScalar y) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendFloat(x);
     *     writer.appendFloat(y);
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonPoint(
      writer: SkJSONWriter,
      x: SkScalar,
      y: SkScalar,
    ) {
      TODO("Implement makeJsonPoint")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonPoint3(SkJSONWriter& writer, const SkPoint3& point) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendFloat(point.x());
     *     writer.appendFloat(point.y());
     *     writer.appendFloat(point.z());
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonPoint3(writer: SkJSONWriter, point: SkPoint3) {
      TODO("Implement makeJsonPoint3")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonRect(SkJSONWriter& writer, const SkRect& rect) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendFloat(rect.left());
     *     writer.appendFloat(rect.top());
     *     writer.appendFloat(rect.right());
     *     writer.appendFloat(rect.bottom());
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonRect(writer: SkJSONWriter, rect: SkRect) {
      TODO("Implement makeJsonRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonIRect(SkJSONWriter& writer, const SkIRect& rect) {
     *     writer.beginArray(nullptr, false);
     *     writer.appendS32(rect.left());
     *     writer.appendS32(rect.top());
     *     writer.appendS32(rect.right());
     *     writer.appendS32(rect.bottom());
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonIRect(writer: SkJSONWriter, rect: SkIRect) {
      TODO("Implement makeJsonIRect")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonMatrix(SkJSONWriter& writer, const SkMatrix& matrix) {
     *     writer.beginArray();
     *     for (int r = 0; r < 3; ++r) {
     *         writer.beginArray(nullptr, false);
     *         for (int c = 0; c < 3; ++c) {
     *             writer.appendFloat(matrix[r * 3 + c]);
     *         }
     *         writer.endArray();
     *     }
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonMatrix(writer: SkJSONWriter, matrix: SkMatrix) {
      TODO("Implement makeJsonMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonMatrix44(SkJSONWriter& writer, const SkM44& matrix) {
     *     writer.beginArray();
     *     for (int r = 0; r < 4; ++r) {
     *         writer.beginArray(nullptr, false);
     *         for (int c = 0; c < 4; ++c) {
     *             writer.appendFloat(matrix.rc(r, c));
     *         }
     *         writer.endArray();
     *     }
     *     writer.endArray();
     * }
     * ```
     */
    public fun makeJsonMatrix44(writer: SkJSONWriter, matrix: SkM44) {
      TODO("Implement makeJsonMatrix44")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonPath(SkJSONWriter& writer, const SkPath& path) {
     *     writer.beginObject();
     *
     *     SkDynamicMemoryWStream wstream;
     *     path.dump(&wstream, false);
     *     auto data = wstream.detachAsData();
     *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_DUMP,
     *                         static_cast<const char*>(data->data()), data->size());
     *
     *     switch (path.getFillType()) {
     *         case SkPathFillType::kWinding:
     *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_FILLTYPE, DEBUGCANVAS_FILLTYPE_WINDING);
     *             break;
     *         case SkPathFillType::kEvenOdd:
     *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_FILLTYPE, DEBUGCANVAS_FILLTYPE_EVENODD);
     *             break;
     *         case SkPathFillType::kInverseWinding:
     *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_FILLTYPE,
     *                                  DEBUGCANVAS_FILLTYPE_INVERSEWINDING);
     *             break;
     *         case SkPathFillType::kInverseEvenOdd:
     *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_FILLTYPE,
     *                                  DEBUGCANVAS_FILLTYPE_INVERSEEVENODD);
     *             break;
     *     }
     *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_VERBS);
     *     SkPath::Iter iter(path, false);
     *     while (auto rec = iter.next()) {
     *         if (rec->fVerb == SkPathVerb::kClose) {
     *             writer.appendNString(DEBUGCANVAS_VERB_CLOSE);
     *             continue;
     *         }
     *         writer.beginObject();  // verb
     *         SkSpan<const SkPoint> pts = rec->fPoints;
     *         switch (rec->fVerb) {
     *             case SkPathVerb::kLine: {
     *                 writer.appendName(DEBUGCANVAS_VERB_LINE);
     *                 MakeJsonPoint(writer, pts[1]);
     *                 break;
     *             }
     *             case SkPathVerb::kQuad: {
     *                 writer.beginArray(DEBUGCANVAS_VERB_QUAD);
     *                 MakeJsonPoint(writer, pts[1]);
     *                 MakeJsonPoint(writer, pts[2]);
     *                 writer.endArray();  // quad coords
     *                 break;
     *             }
     *             case SkPathVerb::kCubic: {
     *                 writer.beginArray(DEBUGCANVAS_VERB_CUBIC);
     *                 MakeJsonPoint(writer, pts[1]);
     *                 MakeJsonPoint(writer, pts[2]);
     *                 MakeJsonPoint(writer, pts[3]);
     *                 writer.endArray();  // cubic coords
     *                 break;
     *             }
     *             case SkPathVerb::kConic: {
     *                 writer.beginArray(DEBUGCANVAS_VERB_CONIC);
     *                 MakeJsonPoint(writer, pts[1]);
     *                 MakeJsonPoint(writer, pts[2]);
     *                 writer.appendFloat(rec->conicWeight());
     *                 writer.endArray();  // conic coords
     *                 break;
     *             }
     *             case SkPathVerb::kMove: {
     *                 writer.appendName(DEBUGCANVAS_VERB_MOVE);
     *                 MakeJsonPoint(writer, pts[0]);
     *                 break;
     *             }
     *             case SkPathVerb::kClose:
     *                 // Unreachable
     *                 break;
     *         }
     *         writer.endObject();  // verb
     *     }
     *     writer.endArray();   // verbs
     *     writer.endObject();  // path
     * }
     * ```
     */
    public fun makeJsonPath(writer: SkJSONWriter, path: SkPath) {
      TODO("Implement makeJsonPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonRegion(SkJSONWriter& writer, const SkRegion& region) {
     *     // TODO: Actually serialize the rectangles, rather than just devolving to path
     *     MakeJsonPath(writer, region.getBoundaryPath());
     * }
     * ```
     */
    public fun makeJsonRegion(writer: SkJSONWriter, region: SkRegion) {
      TODO("Implement makeJsonRegion")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonSampling(SkJSONWriter& writer, const SkSamplingOptions& sampling) {
     *     writer.beginObject();
     *     writer.appendS32("maxAniso", sampling.maxAniso);
     *     writer.appendBool("useCubic", sampling.useCubic);
     *     writer.appendS32("filter", (int)sampling.filter);
     *     writer.appendS32("mipmap", (int)sampling.mipmap);
     *     writer.appendFloat("cubic.B", sampling.cubic.B);
     *     writer.appendFloat("cubic.C", sampling.cubic.C);
     *     writer.endObject();
     * }
     * ```
     */
    public fun makeJsonSampling(writer: SkJSONWriter, sampling: SkSamplingOptions) {
      TODO("Implement makeJsonSampling")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonPaint(SkJSONWriter&   writer,
     *                                 const SkPaint&  paint,
     *                                 UrlDataManager& urlDataManager) {
     *     writer.beginObject();
     *     store_scalar(writer, DEBUGCANVAS_ATTRIBUTE_STROKEWIDTH, paint.getStrokeWidth(), 0.0f);
     *     store_scalar(writer,
     *                  DEBUGCANVAS_ATTRIBUTE_STROKEMITER,
     *                  paint.getStrokeMiter(),
     *                  SkPaintDefaults_MiterLimit);
     *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_ANTIALIAS, paint.isAntiAlias(), false);
     *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_DITHER, paint.isDither(), false);
     *
     *     apply_paint_color(paint, writer);
     *     apply_paint_style(paint, writer);
     *     apply_paint_blend_mode(paint, writer);
     *     apply_paint_cap(paint, writer);
     *     apply_paint_join(paint, writer);
     *     apply_paint_patheffect(paint, writer, urlDataManager);
     *     apply_paint_maskfilter(paint, writer, urlDataManager);
     *     apply_flattenable(DEBUGCANVAS_ATTRIBUTE_SHADER, paint.getShader(), writer, urlDataManager);
     *     apply_flattenable(
     *             DEBUGCANVAS_ATTRIBUTE_IMAGEFILTER, paint.getImageFilter(), writer, urlDataManager);
     *     apply_flattenable(
     *             DEBUGCANVAS_ATTRIBUTE_COLORFILTER, paint.getColorFilter(), writer, urlDataManager);
     *     writer.endObject();  // paint
     * }
     * ```
     */
    public fun makeJsonPaint(
      writer: SkJSONWriter,
      paint: SkPaint,
      urlDataManager: UrlDataManager,
    ) {
      TODO("Implement makeJsonPaint")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::MakeJsonLattice(SkJSONWriter& writer, const SkCanvas::Lattice& lattice) {
     *     writer.beginObject();
     *     writer.appendS32(DEBUGCANVAS_ATTRIBUTE_LATTICEXCOUNT, lattice.fXCount);
     *     writer.appendS32(DEBUGCANVAS_ATTRIBUTE_LATTICEYCOUNT, lattice.fYCount);
     *     if (nullptr != lattice.fBounds) {
     *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_BOUNDS);
     *         MakeJsonIRect(writer, *lattice.fBounds);
     *     }
     *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_LATTICEXDIVS);
     *     for (int i = 0; i < lattice.fXCount; i++) {
     *         writer.appendS32(lattice.fXDivs[i]);
     *     }
     *     writer.endArray();  // xdivs
     *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_LATTICEYDIVS);
     *     for (int i = 0; i < lattice.fYCount; i++) {
     *         writer.appendS32(lattice.fYDivs[i]);
     *     }
     *     writer.endArray();  // ydivs
     *     if (nullptr != lattice.fRectTypes) {
     *         writer.beginArray(DEBUGCANVAS_ATTRIBUTE_LATTICEFLAGS);
     *         int flagCount = 0;
     *         for (int row = 0; row < lattice.fYCount + 1; row++) {
     *             writer.beginArray();
     *             for (int column = 0; column < lattice.fXCount + 1; column++) {
     *                 writer.appendS32(lattice.fRectTypes[flagCount++]);
     *             }
     *             writer.endArray();  // row
     *         }
     *         writer.endArray();
     *     }
     *     writer.endObject();
     * }
     * ```
     */
    public fun makeJsonLattice(writer: SkJSONWriter, lattice: SkCanvas.Lattice) {
      TODO("Implement makeJsonLattice")
    }

    /**
     * C++ original:
     * ```cpp
     * void DrawCommand::flatten(const SkFlattenable* flattenable,
     *                           SkJSONWriter&        writer,
     *                           UrlDataManager&      urlDataManager) {
     *     SkBinaryWriteBuffer buffer({});  // TODO(kjlubick, bungeman) feed SkSerialProcs through API
     *     flattenable->flatten(buffer);
     *     void* data = sk_malloc_throw(buffer.bytesWritten());
     *     buffer.writeToMemory(data);
     *     SkString url =
     *             encode_data(data, buffer.bytesWritten(), "application/octet-stream", urlDataManager);
     *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_NAME, flattenable->getTypeName());
     *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_DATA, url);
     *
     *     writer.beginObject(DEBUGCANVAS_ATTRIBUTE_VALUES);
     *     JsonWriteBuffer jsonBuffer(&writer, &urlDataManager);
     *     flattenable->flatten(jsonBuffer);
     *     writer.endObject();  // values
     *
     *     sk_free(data);
     * }
     * ```
     */
    public fun flatten(
      flattenable: SkFlattenable?,
      writer: SkJSONWriter,
      urlDataManager: UrlDataManager,
    ) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * bool DrawCommand::flatten(const SkImage&  image,
     *                           SkJSONWriter&   writer,
     *                           UrlDataManager& urlDataManager) {
     *     // For MSKP files, there is no need to encode the image,
     *     // just report its id.
     *     if (urlDataManager.hasImageIndex()) {
     *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_IMAGE_INDEX);
     *         writer.appendU64(urlDataManager.lookupImage(&image));
     *         return true;
     *     }
     *
     *     writer.beginObject(DEBUGCANVAS_ATTRIBUTE_IMAGE);
     *     size_t       rowBytes = 4 * image.width();
     *     SkAutoMalloc buffer(rowBytes * image.height());
     *     SkImageInfo  dstInfo =
     *             SkImageInfo::Make(image.dimensions(), kN32_SkColorType, kPremul_SkAlphaType);
     *     // "cheat" for this debug tool and use image's context
     *     GrDirectContext* dContext = nullptr;
     * #if defined(SK_GANESH)
     *     dContext = GrAsDirectContext(as_IB(&image)->context());
     * #endif
     *     if (!image.readPixels(dContext, dstInfo, buffer.get(), rowBytes, 0, 0)) {
     *         SkDebugf("DrawCommand::flatten SkImage: readPixels failed\n");
     *         writer.endObject();
     *         return false;
     *     }
     *
     *     SkBitmap bm;
     *     bm.installPixels(dstInfo, buffer.get(), rowBytes);
     *
     *     SkDynamicMemoryWStream out;
     *     DrawCommand::WritePNG(bm, out);
     *     sk_sp<SkData> encoded = out.detachAsData();
     *     if (encoded == nullptr) {
     *         SkDebugf("DrawCommand::flatten SkImage: could not encode image as PNG\n");
     *         writer.endObject();
     *         return false;
     *     }
     *     auto dataPtr = encoded->data();
     *     if (!dataPtr) {
     *       SkDebugf("DrawCommand::flatten SkImage: encoding as PNG produced zero length data\n");
     *       writer.endObject();
     *       return false;
     *     }
     *     SkString url = encode_data(encoded->data(), encoded->size(), "image/png", urlDataManager);
     *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_DATA, url);
     *     writer.endObject();
     *     return true;
     * }
     * ```
     */
    public fun flatten(
      image: SkImage,
      writer: SkJSONWriter,
      urlDataManager: UrlDataManager,
    ): Boolean {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * bool DrawCommand::flatten(const SkBitmap& bitmap,
     *                           SkJSONWriter&   writer,
     *                           UrlDataManager& urlDataManager) {
     *     sk_sp<SkImage> image(bitmap.asImage());
     *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_COLOR, color_type_name(bitmap.colorType()));
     *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_ALPHA, alpha_type_name(bitmap.alphaType()));
     *     // Image will appear to have no uses, TODO(nifong): provide the user with a useful explanation
     *     bool success = flatten(*image, writer, urlDataManager);
     *     return success;
     * }
     * ```
     */
    public fun flatten(
      bitmap: SkBitmap,
      writer: SkJSONWriter,
      urlDataManager: UrlDataManager,
    ): Boolean {
      TODO("Implement flatten")
    }
  }
}

public typealias RestoreCommandINHERITED = DrawCommand

public typealias ClearCommandINHERITED = DrawCommand

public typealias ClipPathCommandINHERITED = DrawCommand

public typealias ClipRegionCommandINHERITED = DrawCommand

public typealias ClipRectCommandINHERITED = DrawCommand

public typealias ClipRRectCommandINHERITED = DrawCommand

public typealias ClipShaderCommandINHERITED = DrawCommand

public typealias ResetClipCommandINHERITED = DrawCommand

public typealias ConcatCommandINHERITED = DrawCommand

public typealias Concat44CommandINHERITED = DrawCommand

public typealias DrawAnnotationCommandINHERITED = DrawCommand

public typealias DrawImageCommandINHERITED = DrawCommand

public typealias DrawImageLatticeCommandINHERITED = DrawCommand

public typealias DrawImageRectCommandINHERITED = DrawCommand

public typealias DrawImageRectLayerCommandINHERITED = DrawCommand

public typealias DrawOvalCommandINHERITED = DrawCommand

public typealias DrawArcCommandINHERITED = DrawCommand

public typealias DrawPaintCommandINHERITED = DrawCommand

public typealias DrawBehindCommandINHERITED = DrawCommand

public typealias DrawPathCommandINHERITED = DrawCommand

public typealias BeginDrawPictureCommandINHERITED = DrawCommand

public typealias EndDrawPictureCommandINHERITED = DrawCommand

public typealias DrawPointsCommandINHERITED = DrawCommand

public typealias DrawRegionCommandINHERITED = DrawCommand

public typealias DrawTextBlobCommandINHERITED = DrawCommand

public typealias DrawPatchCommandINHERITED = DrawCommand

public typealias DrawRectCommandINHERITED = DrawCommand

public typealias DrawRRectCommandINHERITED = DrawCommand

public typealias DrawDRRectCommandINHERITED = DrawCommand

public typealias DrawVerticesCommandINHERITED = DrawCommand

public typealias DrawAtlasCommandINHERITED = DrawCommand

public typealias SaveCommandINHERITED = DrawCommand

public typealias SaveLayerCommandINHERITED = DrawCommand

public typealias SetMatrixCommandINHERITED = DrawCommand

public typealias SetM44CommandINHERITED = DrawCommand

public typealias DrawShadowCommandINHERITED = DrawCommand

public typealias DrawDrawableCommandINHERITED = DrawCommand

public typealias DrawEdgeAAQuadCommandINHERITED = DrawCommand

public typealias DrawEdgeAAImageSetCommandINHERITED = DrawCommand
