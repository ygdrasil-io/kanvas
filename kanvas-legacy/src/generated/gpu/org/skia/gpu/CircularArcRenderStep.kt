package org.skia.gpu

import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class CircularArcRenderStep final : public RenderStep {
 * public:
 *     CircularArcRenderStep(Layout, StaticBufferManager*);
 *
 *     ~CircularArcRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     const char* fragmentCoverageSkSL() const override;
 *
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 *     // Points to the static buffers holding the fixed indexed vertex template for drawing instances.
 *     BindBufferInfo fVertexBuffer;
 *     BindBufferInfo fIndexBuffer;
 * }
 * ```
 */
public class CircularArcRenderStep public constructor(
  param0: Layout,
  param1: StaticBufferManager,
) : RenderStep() {
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fVertexBuffer
   * ```
   */
  private var fVertexBuffer: Int = TODO("Initialize fVertexBuffer")

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fIndexBuffer
   * ```
   */
  private var fIndexBuffer: Int = TODO("Initialize fIndexBuffer")

  /**
   * C++ original:
   * ```cpp
   * CircularArcRenderStep(Layout, StaticBufferManager*)
   * ```
   */
  public constructor(layout: Layout, bufferManager: StaticBufferManager?) : this(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string CircularArcRenderStep::vertexSkSL() const {
   *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
   *     // must write to an already-defined float2 stepLocalCoords variable.
   *     return "float4 devPosition = circular_arc_vertex_fn("
   *                    // Static Data Attributes
   *                    "position, "
   *                    // Append Data Attributes
   *                    "centerScales, radiiAndFlags, geoClipPlane, fragClipPlane0, fragClipPlane1, "
   *                    "inRoundCapPos, inRoundCapRadius, depth, float3x3(mat0, mat1, mat2), "
   *                    // Varyings
   *                    "circleEdge, clipPlane, isectPlane, unionPlane, "
   *                    "roundCapRadius, roundCapPos, "
   *                    // Render Step
   *                    "stepLocalCoords);\n";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* CircularArcRenderStep::fragmentCoverageSkSL() const {
   *     // The returned SkSL must write its coverage into a 'half4 outputCoverage' variable (defined in
   *     // the calling code) with the actual coverage splatted out into all four channels.
   *     return "outputCoverage = circular_arc_coverage_fn(circleEdge, "
   *                                                      "clipPlane, "
   *                                                      "isectPlane, "
   *                                                      "unionPlane, "
   *                                                      "roundCapRadius, "
   *                                                      "roundCapPos);";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void CircularArcRenderStep::writeVertices(DrawWriter* writer,
   *                                           const DrawParams& params,
   *                                           uint32_t ssboIndex) const {
   *     SkASSERT(params.geometry().isShape() && params.geometry().shape().isArc());
   *
   *     DrawWriter::Instances instance{*writer, fVertexBuffer, {}, kVertexCount};
   *     auto vw = instance.append(1);
   *
   *     const Shape& shape = params.geometry().shape();
   *     const SkArc& arc = shape.arc();
   *
   *     SkPoint localCenter = arc.oval().center();
   *     float localOuterRadius = arc.oval().width() / 2;
   *     float localInnerRadius = 0.0f;
   *
   *     // We know that we have a similarity matrix so this will transform radius to device space
   *     const Transform& transform = params.transform();
   *     float radius = localOuterRadius * transform.maxScaleFactor();
   *     bool isStroke = params.isStroke();
   *
   *     float innerRadius = -SK_ScalarHalf;
   *     float outerRadius = radius;
   *     float halfWidth = 0;
   *     if (isStroke) {
   *         float localHalfWidth = params.strokeStyle().halfWidth();
   *
   *         halfWidth = localHalfWidth * transform.maxScaleFactor();
   *         if (SkScalarNearlyZero(halfWidth)) {
   *             halfWidth = SK_ScalarHalf;
   *             // Need to map this back to local space
   *             localHalfWidth = halfWidth / transform.maxScaleFactor();
   *         }
   *
   *         outerRadius += halfWidth;
   *         innerRadius = radius - halfWidth;
   *         localInnerRadius = localOuterRadius - localHalfWidth;
   *         localOuterRadius += localHalfWidth;
   *     }
   *
   *     // The radii are outset for two reasons. First, it allows the shader to simply perform
   *     // simpler computation because the computed alpha is zero, rather than 50%, at the radius.
   *     // Second, the outer radius is used to compute the verts of the bounding box that is
   *     // rendered and the outset ensures the box will cover all partially covered by the circle.
   *     outerRadius += SK_ScalarHalf;
   *     innerRadius -= SK_ScalarHalf;
   *
   *     // The shader operates in a space where the circle is translated to be centered at the
   *     // origin. Here we compute points on the unit circle at the starting and ending angles.
   *     SkV2 localPoints[3];
   *     float startAngleRadians = SkDegreesToRadians(arc.startAngle());
   *     float sweepAngleRadians = SkDegreesToRadians(arc.sweepAngle());
   *     localPoints[0].y = SkScalarSin(startAngleRadians);
   *     localPoints[0].x = SkScalarCos(startAngleRadians);
   *     SkScalar endAngle = startAngleRadians + sweepAngleRadians;
   *     localPoints[1].y = SkScalarSin(endAngle);
   *     localPoints[1].x = SkScalarCos(endAngle);
   *     localPoints[2] = {0, 0};
   *
   *     // Adjust the start and end points based on the view matrix (to handle rotated arcs)
   *     SkV4 devPoints[3];
   *     transform.mapPoints(localPoints, devPoints, 3);
   *     // Translate the point relative to the transformed origin
   *     SkV2 startPoint = {devPoints[0].x - devPoints[2].x, devPoints[0].y - devPoints[2].y};
   *     SkV2 stopPoint = {devPoints[1].x - devPoints[2].x, devPoints[1].y - devPoints[2].y};
   *     startPoint = startPoint.normalize();
   *     stopPoint = stopPoint.normalize();
   *
   *     // We know the matrix is a similarity here. Detect mirroring which will affect how we
   *     // should orient the clip planes for arcs.
   *     const SkM44& m = transform.matrix();
   *     auto upperLeftDet = m.rc(0,0) * m.rc(1,1) -
   *                         m.rc(0,1) * m.rc(1,0);
   *     if (upperLeftDet < 0) {
   *         std::swap(startPoint, stopPoint);
   *     }
   *
   *     // Like a fill without useCenter, butt-cap stroke can be implemented by clipping against
   *     // radial lines. We treat round caps the same way, but track coverage of circles at the
   *     // center of the butts.
   *     // However, in both cases we have to be careful about the half-circle.
   *     // case. In that case the two radial lines are equal and so that edge gets clipped
   *     // twice. Since the shared edge goes through the center we fall back on the !useCenter
   *     // case.
   *     auto absSweep = SkScalarAbs(sweepAngleRadians);
   *     bool useCenter = (arc.isWedge() || isStroke) &&
   *                      !SkScalarNearlyEqual(absSweep, SK_ScalarPI);
   *
   *     // This makes every point fully inside the plane.
   *     SkV3 geoClipPlane = {0.f, 0.f, 1.f};
   *     SkV3 clipPlane0;
   *     SkV3 clipPlane1;
   *     SkV2 roundCapPos0 = {0, 0};
   *     SkV2 roundCapPos1 = {0, 0};
   *     static constexpr float kIntersection_NoRoundCaps = 1;
   *     static constexpr float kIntersection_RoundCaps = 2;
   *
   *     float roundCapRadius = 0;
   *     // Default to intersection and no round caps.
   *     float flags = kIntersection_NoRoundCaps;
   *     // Determine if we need round caps.
   *     if (isStroke &&
   *         params.strokeStyle().halfWidth() > 0 &&
   *         params.strokeStyle().cap() == SkPaint::kRound_Cap) {
   *         // Compute the cap center points in the normalized space.
   *         float midRadius = (innerRadius + outerRadius) / (2 * outerRadius);
   *         roundCapPos0 = startPoint * midRadius;
   *         roundCapPos1 = stopPoint * midRadius;
   *         flags = kIntersection_RoundCaps;
   *         // Compute the cap radius in the normalized space.
   *         roundCapRadius = (outerRadius - innerRadius) / (2 * outerRadius);
   *     }
   *
   *     // Determine clip planes.
   *     if (useCenter) {
   *         SkV2 norm0 = {startPoint.y, -startPoint.x};
   *         SkV2 norm1 = {stopPoint.y, -stopPoint.x};
   *         // This ensures that norm0 is always the clockwise plane, and norm1 is CCW.
   *         if (sweepAngleRadians < 0) {
   *             std::swap(norm0, norm1);
   *         }
   *         norm0 = -norm0;
   *         clipPlane0 = {norm0.x, norm0.y, 0.5f};
   *         clipPlane1 = {norm1.x, norm1.y, 0.5f};
   *         if (absSweep > SK_ScalarPI) {
   *             // Union
   *             flags = -flags;
   *         } else {
   *             // Intersection
   *             // Highly acute arc. We need to clip the vertices to the perpendicular half-plane.
   *             if (!isStroke && absSweep < 0.5f*SK_ScalarPI) {
   *                 // We do this clipping in normalized space so use our original local points.
   *                 // Should already be normalized since they're sin/cos.
   *                 SkV2 localNorm0 = {localPoints[0].y, -localPoints[0].x};
   *                 SkV2 localNorm1 = {localPoints[1].y, -localPoints[1].x};
   *                 // This ensures that norm0 is always the clockwise plane, and norm1 is CCW.
   *                 if (sweepAngleRadians < 0) {
   *                     std::swap(localNorm0, localNorm1);
   *                 }
   *                 // Negate norm0 and compute the perpendicular of the difference
   *                 SkV2 clipNorm = {-localNorm0.y - localNorm1.y, localNorm1.x + localNorm0.x};
   *                 clipNorm = clipNorm.normalize();
   *                 // This should give us 1/2 pixel spacing from the half-plane
   *                 // after transforming from normalized to local to device space.
   *                 float dist = 0.5f / radius / transform.maxScaleFactor();
   *                 geoClipPlane = {clipNorm.x, clipNorm.y, dist};
   *             }
   *         }
   *     } else {
   *         // We clip to a secant of the original circle, only one clip plane
   *         startPoint *= radius;
   *         stopPoint *= radius;
   *         SkV2 norm = {startPoint.y - stopPoint.y, stopPoint.x - startPoint.x};
   *         norm = norm.normalize();
   *         if (sweepAngleRadians > 0) {
   *             norm = -norm;
   *         }
   *         float d = -norm.dot(startPoint) + 0.5f;
   *         clipPlane0 = {norm.x, norm.y, d};
   *         clipPlane1 = {0.f, 0.f, 1.f}; // no clipping
   *     }
   *
   *     if (isStroke && innerRadius < -SK_ScalarHalf) {
   *         // Reset the inner radius to render a filled arc instead of a stroked arc, as the stroke
   *         // width is greater than or equal to the oval's width.
   *         innerRadius = -SK_ScalarHalf;
   *         localInnerRadius = 0.f;
   *     }
   *
   *     // The inner radius in the vertex data must be specified in normalized space.
   *     innerRadius = innerRadius / outerRadius;
   *
   *     vw << localCenter << localOuterRadius << localInnerRadius
   *        << outerRadius << innerRadius << flags
   *        << geoClipPlane << clipPlane0 << clipPlane1
   *        << roundCapPos0 << roundCapPos1 << roundCapRadius
   *        << params.order().depthAsFloat()
   *        << ssboIndex
   *        << m.rc(0,0) << m.rc(1,0) << m.rc(3,0)  // mat0
   *        << m.rc(0,1) << m.rc(1,1) << m.rc(3,1)  // mat1
   *        << m.rc(0,3) << m.rc(1,3) << m.rc(3,3); // mat2
   * }
   * ```
   */
  public override fun writeVertices(
    writer: DrawWriter?,
    param1: DrawParams,
    ssboIndex: UInt,
  ) {
    TODO("Implement writeVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void CircularArcRenderStep::writeUniformsAndTextures(const DrawParams&,
   *                                                      PipelineDataGatherer* gatherer) const {
   *     // All data is uploaded as instance attributes, so no uniforms are needed.
   *     SkDEBUGCODE(gatherer->checkRewind());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
