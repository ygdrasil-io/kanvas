package org.skia.utils

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * class SK_API SkParsePath {
 * public:
 *     static std::optional<SkPath> FromSVGString(const char str[]);
 *     // Deprecated
 *     static bool FromSVGString(const char str[], SkPath* outPath) {
 *         if (auto result = FromSVGString(str)) {
 *             *outPath = *result;
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     enum class PathEncoding { Absolute, Relative };
 *     static SkString ToSVGString(const SkPath&, PathEncoding = PathEncoding::Absolute);
 * }
 * ```
 */
public open class SkParsePath {
  public enum class PathEncoding {
    Absolute,
    Relative,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::optional<SkPath> SkParsePath::FromSVGString(const char data[]) {
     *     // We will write all data to this local path and only write it
     *     // to result if the whole parsing succeeds.
     *     SkPathBuilder builder;
     *     SkPoint first = {0, 0};
     *     SkPoint c = {0, 0};
     *     SkPoint lastc = {0, 0};
     *     // We will use find_points and find_scalar to read into these.
     *     // There might not be enough data to fill them, so to avoid
     *     // MSAN warnings about using uninitialized bytes, we initialize
     *     // them there.
     *     SkPoint points[3] = {};
     *     SkScalar scratch = 0;
     *     char op = '\0';
     *     char previousOp = '\0';
     *     bool relative = false;
     *     for (;;) {
     *         if (!data) {
     *             // Truncated data
     *             return {};
     *         }
     *         data = skip_ws(data);
     *         if (data[0] == '\0') {
     *             break;
     *         }
     *         char ch = data[0];
     *         if (is_digit(ch) || ch == '-' || ch == '+' || ch == '.') {
     *             if (op == '\0' || op == 'Z') {
     *                 return {};
     *             }
     *         } else if (is_sep(ch)) {
     *             data = skip_sep(data);
     *         } else {
     *             op = ch;
     *             relative = false;
     *             if (is_lower(op)) {
     *                 op = (char) to_upper(op);
     *                 relative = true;
     *             }
     *             data++;
     *             data = skip_sep(data);
     *         }
     *         switch (op) {
     *             case 'M':  // Move
     *                 data = find_points(data, points, 1, relative, &c);
     *                 // find_points might have failed, so this might be the
     *                 // previous point. However, data will be set to nullptr
     *                 // if it failed, so we will check this at the top of the loop.
     *                 builder.moveTo(points[0]);
     *                 previousOp = '\0';
     *                 op = 'L';
     *                 c = points[0];
     *                 break;
     *             case 'L':  // Line
     *                 data = find_points(data, points, 1, relative, &c);
     *                 builder.lineTo(points[0]);
     *                 c = points[0];
     *                 break;
     *             case 'H':  // Horizontal Line
     *                 data = find_scalar(data, &scratch, relative, c.fX);
     *                 // Similarly, if there wasn't a scalar to read, data will
     *                 // be set to nullptr and this lineTo is bogus but will
     *                 // be ultimately ignored when the next time through the loop
     *                 // detects that and bails out.
     *                 builder.lineTo(scratch, c.fY);
     *                 c.fX = scratch;
     *                 break;
     *             case 'V':  // Vertical Line
     *                 data = find_scalar(data, &scratch, relative, c.fY);
     *                 builder.lineTo(c.fX, scratch);
     *                 c.fY = scratch;
     *                 break;
     *             case 'C':  // Cubic Bezier Curve
     *                 data = find_points(data, points, 3, relative, &c);
     *                 goto cubicCommon;
     *             case 'S':  // Continued "Smooth" Cubic Bezier Curve
     *                 data = find_points(data, &points[1], 2, relative, &c);
     *                 points[0] = c;
     *                 if (previousOp == 'C' || previousOp == 'S') {
     *                     points[0].fX -= lastc.fX - c.fX;
     *                     points[0].fY -= lastc.fY - c.fY;
     *                 }
     *             cubicCommon:
     *                 builder.cubicTo(points[0], points[1], points[2]);
     *                 lastc = points[1];
     *                 c = points[2];
     *                 break;
     *             case 'Q':  // Quadratic Bezier Curve
     *                 data = find_points(data, points, 2, relative, &c);
     *                 goto quadraticCommon;
     *             case 'T':  // Continued Quadratic Bezier Curve
     *                 data = find_points(data, &points[1], 1, relative, &c);
     *                 points[0] = c;
     *                 if (previousOp == 'Q' || previousOp == 'T') {
     *                     points[0].fX -= lastc.fX - c.fX;
     *                     points[0].fY -= lastc.fY - c.fY;
     *                 }
     *             quadraticCommon:
     *                 builder.quadTo(points[0], points[1]);
     *                 lastc = points[0];
     *                 c = points[1];
     *                 break;
     *             case 'A': {  // Arc (Elliptical)
     *                 SkPoint radii;
     *                 SkScalar angle;
     *                 bool largeArc, sweep;
     *                 if ((data = find_points(data, &radii, 1, false, nullptr))
     *                         && (data = skip_sep(data))
     *                         && (data = find_scalar(data, &angle, false, 0))
     *                         && (data = skip_sep(data))
     *                         && (data = find_flag(data, &largeArc))
     *                         && (data = skip_sep(data))
     *                         && (data = find_flag(data, &sweep))
     *                         && (data = skip_sep(data))
     *                         && (data = find_points(data, &points[0], 1, relative, &c))) {
     *                     builder.arcTo(radii, angle, (SkPathBuilder::ArcSize) largeArc,
     *                             (SkPathDirection) !sweep, points[0]);
     *                     c = builder.points().back();
     *                 }
     *                 } break;
     *             case 'Z':  // Close Path
     *                 builder.close();
     *                 c = first;
     *                 break;
     *             default:
     *                 return {};
     *         }
     *         if (previousOp == 0) {
     *             first = c;
     *         }
     *         previousOp = op;
     *     }
     *
     *     return builder.detach();
     * }
     * ```
     */
    public fun fromSVGString(str: CharArray): Int {
      TODO("Implement fromSVGString")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool FromSVGString(const char str[], SkPath* outPath) {
     *         if (auto result = FromSVGString(str)) {
     *             *outPath = *result;
     *             return true;
     *         }
     *         return false;
     *     }
     * ```
     */
    public fun fromSVGString(str: CharArray, outPath: SkPath?): Boolean {
      TODO("Implement fromSVGString")
    }

    /**
     * C++ original:
     * ```cpp
     * SkString SkParsePath::ToSVGString(const SkPath& path, PathEncoding encoding) {
     *     SkDynamicMemoryWStream  stream;
     *
     *     SkPoint current_point{0,0};
     *     const auto rel_selector = encoding == PathEncoding::Relative;
     *
     *     const auto append_command = [&](char cmd, const SkPoint pts[], size_t count) {
     *         // Use lower case cmds for relative encoding.
     *         cmd += 32 * rel_selector;
     *         stream.write(&cmd, 1);
     *
     *         for (size_t i = 0; i < count; ++i) {
     *             const auto pt = pts[i] - current_point;
     *             if (i > 0) {
     *                 stream.write(" ", 1);
     *             }
     *             stream.writeScalarAsText(pt.fX);
     *             stream.write(" ", 1);
     *             stream.writeScalarAsText(pt.fY);
     *         }
     *
     *         SkASSERT(count > 0);
     *         // For relative encoding, track the current point (otherwise == origin).
     *         current_point = pts[count - 1] * rel_selector;
     *     };
     *
     *     SkPath::Iter iter(path, false);
     *
     *     while (auto rec = iter.next()) {
     *         SkSpan<const SkPoint> pts = rec->fPoints;
     *         switch (rec->fVerb) {
     *             case SkPathVerb::kConic: {
     *                 const SkScalar tol = SK_Scalar1 / 1024; // how close to a quad
     *                 SkAutoConicToQuads quadder;
     *                 const SkPoint* quadPts = quadder.computeQuads(pts.data(), rec->conicWeight(), tol);
     *                 for (int i = 0; i < quadder.countQuads(); ++i) {
     *                     append_command('Q', &quadPts[i*2 + 1], 2);
     *                 }
     *             } break;
     *             case SkPathVerb::kMove:
     *                 append_command('M', &pts[0], 1);
     *                 break;
     *             case SkPathVerb::kLine:
     *                 append_command('L', &pts[1], 1);
     *                 break;
     *             case SkPathVerb::kQuad:
     *                 append_command('Q', &pts[1], 2);
     *                 break;
     *             case SkPathVerb::kCubic:
     *                 append_command('C', &pts[1], 3);
     *                 break;
     *             case SkPathVerb::kClose:
     *                 stream.write("Z", 1);
     *                 break;
     *         }
     *     }
     *
     *     SkString str;
     *     str.resize(stream.bytesWritten());
     *     stream.copyTo(str.data());
     *     return str;
     * }
     * ```
     */
    public fun toSVGString(path: SkPath, encoding: PathEncoding = TODO()): String {
      TODO("Implement toSVGString")
    }
  }
}
