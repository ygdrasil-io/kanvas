package org.skia.tools

import kotlin.Int
import kotlin.String
import kotlin.UByte
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPath
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkUnichar
import org.skia.math.SkFixed
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkTestFont : public SkRefCnt {
 * public:
 *     SkTestFont(const SkTestFontData&);
 *     ~SkTestFont() override;
 *     SkGlyphID glyphForUnichar(SkUnichar charCode) const;
 *     void      init(const SkScalar* pts, const unsigned char* verbs);
 *
 * private:
 *     const SkUnichar*     fCharCodes;
 *     const size_t         fCharCodesCount;
 *     const SkFixed*       fWidths;
 *     const SkFontMetrics& fMetrics;
 *     const char*          fName;
 *     SkPath*              fPaths;
 *     friend class TestTypeface;
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkTestFont public constructor(
  fontData: SkTestFontData,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const SkUnichar*     fCharCodes
   * ```
   */
  private val fCharCodes: SkUnichar? = TODO("Initialize fCharCodes")

  /**
   * C++ original:
   * ```cpp
   * const size_t         fCharCodesCount
   * ```
   */
  private val fCharCodesCount: Int = TODO("Initialize fCharCodesCount")

  /**
   * C++ original:
   * ```cpp
   * const SkFixed*       fWidths
   * ```
   */
  private val fWidths: SkFixed? = TODO("Initialize fWidths")

  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics& fMetrics
   * ```
   */
  private val fMetrics: SkFontMetrics = TODO("Initialize fMetrics")

  /**
   * C++ original:
   * ```cpp
   * const char*          fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkPath*              fPaths
   * ```
   */
  private var fPaths: SkPath? = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID SkTestFont::glyphForUnichar(SkUnichar charCode) const {
   *     for (size_t index = 0; index < fCharCodesCount; ++index) {
   *         if (fCharCodes[index] == charCode) {
   *             return SkTo<SkGlyphID>(index);
   *         }
   *     }
   *     return 0;
   * }
   * ```
   */
  public fun glyphForUnichar(charCode: SkUnichar): SkGlyphID {
    TODO("Implement glyphForUnichar")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTestFont::init(const SkScalar* pts, const unsigned char* verbs) {
   *     fPaths = new SkPath[fCharCodesCount];
   *     for (unsigned index = 0; index < fCharCodesCount; ++index) {
   *         SkPathBuilder b;
   *         SkPath::Verb verb;
   *         while ((verb = (SkPath::Verb)*verbs++) != SkPath::kDone_Verb) {
   *             switch (verb) {
   *                 case SkPath::kMove_Verb:
   *                     b.moveTo(pts[0], pts[1]);
   *                     pts += 2;
   *                     break;
   *                 case SkPath::kLine_Verb:
   *                     b.lineTo(pts[0], pts[1]);
   *                     pts += 2;
   *                     break;
   *                 case SkPath::kQuad_Verb:
   *                     b.quadTo(pts[0], pts[1], pts[2], pts[3]);
   *                     pts += 4;
   *                     break;
   *                 case SkPath::kCubic_Verb:
   *                     b.cubicTo(pts[0], pts[1], pts[2], pts[3], pts[4], pts[5]);
   *                     pts += 6;
   *                     break;
   *                 case SkPath::kClose_Verb:
   *                     b.close();
   *                     break;
   *                 default:
   *                     SK_ABORT("bad verb");
   *             }
   *         }
   *         fPaths[index] = b.detach();
   *     }
   * }
   * ```
   */
  public fun `init`(pts: SkScalar?, verbs: UByte?) {
    TODO("Implement init")
  }
}
