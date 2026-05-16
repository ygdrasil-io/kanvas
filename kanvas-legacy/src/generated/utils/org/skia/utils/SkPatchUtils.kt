package org.skia.utils

import kotlin.Array
import kotlin.Int
import org.skia.core.SkVertices
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkPatchUtils {
 *
 * public:
 *     // Enums for control points based on the order specified in the constructor (clockwise).
 *     enum {
 *     kNumCtrlPts = 12,
 *         kNumCorners = 4,
 *         kNumPtsCubic = 4
 *     };
 *
 *     /**
 *      * Get the points corresponding to the top cubic of cubics.
 *      */
 *     static void GetTopCubic(const SkPoint cubics[12], SkPoint points[4]);
 *
 *     /**
 *      * Get the points corresponding to the bottom cubic of cubics.
 *      */
 *     static void GetBottomCubic(const SkPoint cubics[12], SkPoint points[4]);
 *
 *     /**
 *      * Get the points corresponding to the left cubic of cubics.
 *      */
 *     static void GetLeftCubic(const SkPoint cubics[12], SkPoint points[4]);
 *
 *     /**
 *      * Get the points corresponding to the right cubic of cubics.
 *      */
 *     static void GetRightCubic(const SkPoint cubics[12], SkPoint points[4]);
 *
 *     /**
 *      * Method that calculates a level of detail (number of subdivisions) for a patch in both axis.
 *      */
 *     static SkISize GetLevelOfDetail(const SkPoint cubics[12], const SkMatrix* matrix);
 *
 *     static sk_sp<SkVertices> MakeVertices(const SkPoint cubics[12], const SkColor colors[4],
 *                                           const SkPoint texCoords[4], int lodX, int lodY,
 *                                           SkColorSpace* colorSpace = nullptr);
 * }
 * ```
 */
public open class SkPatchUtils {
  public companion object {
    public val kNumCtrlPts: Int = TODO("Initialize kNumCtrlPts")

    public val kNumCorners: Int = TODO("Initialize kNumCorners")

    public val kNumPtsCubic: Int = TODO("Initialize kNumPtsCubic")

    /**
     * C++ original:
     * ```cpp
     * void SkPatchUtils::GetTopCubic(const SkPoint cubics[12], SkPoint points[4]) {
     *     points[0] = cubics[kTopP0_CubicCtrlPts];
     *     points[1] = cubics[kTopP1_CubicCtrlPts];
     *     points[2] = cubics[kTopP2_CubicCtrlPts];
     *     points[3] = cubics[kTopP3_CubicCtrlPts];
     * }
     * ```
     */
    public fun getTopCubic(cubics: Array<SkPoint>, points: Array<SkPoint>) {
      TODO("Implement getTopCubic")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPatchUtils::GetBottomCubic(const SkPoint cubics[12], SkPoint points[4]) {
     *     points[0] = cubics[kBottomP0_CubicCtrlPts];
     *     points[1] = cubics[kBottomP1_CubicCtrlPts];
     *     points[2] = cubics[kBottomP2_CubicCtrlPts];
     *     points[3] = cubics[kBottomP3_CubicCtrlPts];
     * }
     * ```
     */
    public fun getBottomCubic(cubics: Array<SkPoint>, points: Array<SkPoint>) {
      TODO("Implement getBottomCubic")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPatchUtils::GetLeftCubic(const SkPoint cubics[12], SkPoint points[4]) {
     *     points[0] = cubics[kLeftP0_CubicCtrlPts];
     *     points[1] = cubics[kLeftP1_CubicCtrlPts];
     *     points[2] = cubics[kLeftP2_CubicCtrlPts];
     *     points[3] = cubics[kLeftP3_CubicCtrlPts];
     * }
     * ```
     */
    public fun getLeftCubic(cubics: Array<SkPoint>, points: Array<SkPoint>) {
      TODO("Implement getLeftCubic")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPatchUtils::GetRightCubic(const SkPoint cubics[12], SkPoint points[4]) {
     *     points[0] = cubics[kRightP0_CubicCtrlPts];
     *     points[1] = cubics[kRightP1_CubicCtrlPts];
     *     points[2] = cubics[kRightP2_CubicCtrlPts];
     *     points[3] = cubics[kRightP3_CubicCtrlPts];
     * }
     * ```
     */
    public fun getRightCubic(cubics: Array<SkPoint>, points: Array<SkPoint>) {
      TODO("Implement getRightCubic")
    }

    /**
     * C++ original:
     * ```cpp
     * SkISize SkPatchUtils::GetLevelOfDetail(const SkPoint cubics[12], const SkMatrix* matrix) {
     *     // Approximate length of each cubic.
     *     SkPoint pts[kNumPtsCubic];
     *     SkPatchUtils::GetTopCubic(cubics, pts);
     *     matrix->mapPoints(pts);
     *     SkScalar topLength = approx_arc_length(pts, kNumPtsCubic);
     *
     *     SkPatchUtils::GetBottomCubic(cubics, pts);
     *     matrix->mapPoints(pts);
     *     SkScalar bottomLength = approx_arc_length(pts, kNumPtsCubic);
     *
     *     SkPatchUtils::GetLeftCubic(cubics, pts);
     *     matrix->mapPoints(pts);
     *     SkScalar leftLength = approx_arc_length(pts, kNumPtsCubic);
     *
     *     SkPatchUtils::GetRightCubic(cubics, pts);
     *     matrix->mapPoints(pts);
     *     SkScalar rightLength = approx_arc_length(pts, kNumPtsCubic);
     *
     *     if (topLength < 0 || bottomLength < 0 || leftLength < 0 || rightLength < 0) {
     *         return {0, 0};  // negative length is a sentinel for bad length (i.e. non-finite)
     *     }
     *
     *     // Level of detail per axis, based on the larger side between top and bottom or left and right
     *     int lodX = static_cast<int>(std::max(topLength, bottomLength) / kPartitionSize);
     *     int lodY = static_cast<int>(std::max(leftLength, rightLength) / kPartitionSize);
     *
     *     return SkISize::Make(std::max(8, lodX), std::max(8, lodY));
     * }
     * ```
     */
    public fun getLevelOfDetail(cubics: Array<SkPoint>, matrix: SkMatrix?): SkISize {
      TODO("Implement getLevelOfDetail")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkVertices> SkPatchUtils::MakeVertices(const SkPoint cubics[12], const SkColor srcColors[4],
     *                                              const SkPoint srcTexCoords[4], int lodX, int lodY,
     *                                              SkColorSpace* colorSpace) {
     *     if (lodX < 1 || lodY < 1 || nullptr == cubics) {
     *         return nullptr;
     *     }
     *
     *     // check for overflow in multiplication
     *     const int64_t lodX64 = (lodX + 1),
     *     lodY64 = (lodY + 1),
     *     mult64 = lodX64 * lodY64;
     *     if (mult64 > SK_MaxS32) {
     *         return nullptr;
     *     }
     *
     *     // Treat null interpolation space as sRGB.
     *     if (!colorSpace) {
     *         colorSpace = sk_srgb_singleton();
     *     }
     *
     *     int vertexCount = SkToS32(mult64);
     *     // it is recommended to generate draw calls of no more than 65536 indices, so we never generate
     *     // more than 60000 indices. To accomplish that we resize the LOD and vertex count
     *     if (vertexCount > 10000 || lodX > 200 || lodY > 200) {
     *         float weightX = static_cast<float>(lodX) / (lodX + lodY);
     *         float weightY = static_cast<float>(lodY) / (lodX + lodY);
     *
     *         // 200 comes from the 100 * 2 which is the max value of vertices because of the limit of
     *         // 60000 indices ( sqrt(60000 / 6) that comes from data->fIndexCount = lodX * lodY * 6)
     *         // Need a min of 1 since we later divide by lod
     *         lodX = std::max(1, sk_float_floor2int_no_saturate(weightX * 200));
     *         lodY = std::max(1, sk_float_floor2int_no_saturate(weightY * 200));
     *         vertexCount = (lodX + 1) * (lodY + 1);
     *     }
     *     const int indexCount = lodX * lodY * 6;
     *     uint32_t flags = 0;
     *     if (srcTexCoords) {
     *         flags |= SkVertices::kHasTexCoords_BuilderFlag;
     *     }
     *     if (srcColors) {
     *         flags |= SkVertices::kHasColors_BuilderFlag;
     *     }
     *
     *     SkSTArenaAlloc<2048> alloc;
     *     SkPMColor4f* cornerColors = srcColors ? alloc.makeArray<SkPMColor4f>(4) : nullptr;
     *     SkPMColor4f* tmpColors = srcColors ? alloc.makeArray<SkPMColor4f>(vertexCount) : nullptr;
     *
     *     SkVertices::Builder builder(SkVertices::kTriangles_VertexMode, vertexCount, indexCount, flags);
     *     SkPoint* pos = builder.positions();
     *     SkPoint* texs = builder.texCoords();
     *     uint16_t* indices = builder.indices();
     *
     *     if (cornerColors) {
     *         skcolor_to_float(cornerColors, srcColors, kNumCorners, colorSpace);
     *     }
     *
     *     SkPoint pts[kNumPtsCubic];
     *     SkPatchUtils::GetBottomCubic(cubics, pts);
     *     FwDCubicEvaluator fBottom(pts);
     *     SkPatchUtils::GetTopCubic(cubics, pts);
     *     FwDCubicEvaluator fTop(pts);
     *     SkPatchUtils::GetLeftCubic(cubics, pts);
     *     FwDCubicEvaluator fLeft(pts);
     *     SkPatchUtils::GetRightCubic(cubics, pts);
     *     FwDCubicEvaluator fRight(pts);
     *
     *     fBottom.restart(lodX);
     *     fTop.restart(lodX);
     *
     *     SkScalar u = 0.0f;
     *     int stride = lodY + 1;
     *     for (int x = 0; x <= lodX; x++) {
     *         SkPoint bottom = fBottom.next(), top = fTop.next();
     *         fLeft.restart(lodY);
     *         fRight.restart(lodY);
     *         SkScalar v = 0.f;
     *         for (int y = 0; y <= lodY; y++) {
     *             int dataIndex = x * (lodY + 1) + y;
     *
     *             SkPoint left = fLeft.next(), right = fRight.next();
     *
     *             SkPoint s0 = SkPoint::Make((1.0f - v) * top.x() + v * bottom.x(),
     *                                        (1.0f - v) * top.y() + v * bottom.y());
     *             SkPoint s1 = SkPoint::Make((1.0f - u) * left.x() + u * right.x(),
     *                                        (1.0f - u) * left.y() + u * right.y());
     *             SkPoint s2 = SkPoint::Make(
     *                                        (1.0f - v) * ((1.0f - u) * fTop.getCtrlPoints()[0].x()
     *                                                      + u * fTop.getCtrlPoints()[3].x())
     *                                        + v * ((1.0f - u) * fBottom.getCtrlPoints()[0].x()
     *                                               + u * fBottom.getCtrlPoints()[3].x()),
     *                                        (1.0f - v) * ((1.0f - u) * fTop.getCtrlPoints()[0].y()
     *                                                      + u * fTop.getCtrlPoints()[3].y())
     *                                        + v * ((1.0f - u) * fBottom.getCtrlPoints()[0].y()
     *                                               + u * fBottom.getCtrlPoints()[3].y()));
     *             pos[dataIndex] = s0 + s1 - s2;
     *
     *             if (cornerColors) {
     *                 bilerp(u, v, skvx::float4::Load(cornerColors[kTopLeft_Corner].vec()),
     *                              skvx::float4::Load(cornerColors[kTopRight_Corner].vec()),
     *                              skvx::float4::Load(cornerColors[kBottomLeft_Corner].vec()),
     *                              skvx::float4::Load(cornerColors[kBottomRight_Corner].vec()))
     *                     .store(tmpColors[dataIndex].vec());
     *             }
     *
     *             if (texs) {
     *                 texs[dataIndex] = SkPoint::Make(bilerp(u, v, srcTexCoords[kTopLeft_Corner].x(),
     *                                                        srcTexCoords[kTopRight_Corner].x(),
     *                                                        srcTexCoords[kBottomLeft_Corner].x(),
     *                                                        srcTexCoords[kBottomRight_Corner].x()),
     *                                                 bilerp(u, v, srcTexCoords[kTopLeft_Corner].y(),
     *                                                        srcTexCoords[kTopRight_Corner].y(),
     *                                                        srcTexCoords[kBottomLeft_Corner].y(),
     *                                                        srcTexCoords[kBottomRight_Corner].y()));
     *
     *             }
     *
     *             if(x < lodX && y < lodY) {
     *                 int i = 6 * (x * lodY + y);
     *                 indices[i] = x * stride + y;
     *                 indices[i + 1] = x * stride + 1 + y;
     *                 indices[i + 2] = (x + 1) * stride + 1 + y;
     *                 indices[i + 3] = indices[i];
     *                 indices[i + 4] = indices[i + 2];
     *                 indices[i + 5] = (x + 1) * stride + y;
     *             }
     *             v = SkTPin(v + 1.f / lodY, 0.0f, 1.0f);
     *         }
     *         u = SkTPin(u + 1.f / lodX, 0.0f, 1.0f);
     *     }
     *
     *     if (tmpColors) {
     *         float_to_skcolor(builder.colors(), tmpColors, vertexCount, colorSpace);
     *     }
     *     return builder.detach();
     * }
     * ```
     */
    public fun makeVertices(
      cubics: Array<SkPoint>,
      colors: Array<SkColor>,
      texCoords: Array<SkPoint>,
      lodX: Int,
      lodY: Int,
      colorSpace: SkColorSpace? = TODO(),
    ): SkSp<SkVertices> {
      TODO("Implement makeVertices")
    }
  }
}
