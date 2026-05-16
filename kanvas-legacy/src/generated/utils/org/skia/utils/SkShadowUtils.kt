package org.skia.utils

import kotlin.Boolean
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkShadowUtils {
 * public:
 *     /**
 *      * Draw an offset spot shadow and outlining ambient shadow for the given path using a disc
 *      * light. The shadow may be cached, depending on the path type and canvas matrix. If the
 *      * matrix is perspective or the path is volatile, it will not be cached.
 *      *
 *      * @param canvas  The canvas on which to draw the shadows.
 *      * @param path  The occluder used to generate the shadows.
 *      * @param zPlaneParams  Values for the plane function which returns the Z offset of the
 *      *  occluder from the canvas based on local x and y values (the current matrix is not applied).
 *      * @param lightPos  Generally, the 3D position of the light relative to the canvas plane.
 *      *                  If kDirectionalLight_ShadowFlag is set, this specifies a vector pointing
 *      *                  towards the light.
 *      * @param lightRadius  Generally, the radius of the disc light.
 *      *                     If DirectionalLight_ShadowFlag is set, this specifies the amount of
 *      *                     blur when the occluder is at Z offset == 1. The blur will grow linearly
 *      *                     as the Z value increases.
 *      * @param ambientColor  The color of the ambient shadow.
 *      * @param spotColor  The color of the spot shadow.
 *      * @param flags  Options controlling opaque occluder optimizations, shadow appearance,
 *      *               and light position. See SkShadowFlags.
 *      */
 *     static void DrawShadow(SkCanvas* canvas, const SkPath& path, const SkPoint3& zPlaneParams,
 *                            const SkPoint3& lightPos, SkScalar lightRadius,
 *                            SkColor ambientColor, SkColor spotColor,
 *                            uint32_t flags = SkShadowFlags::kNone_ShadowFlag);
 *
 *     /**
 *      * Generate bounding box for shadows relative to path. Includes both the ambient and spot
 *      * shadow bounds.
 *      *
 *      * @param ctm  Current transformation matrix to device space.
 *      * @param path  The occluder used to generate the shadows.
 *      * @param zPlaneParams  Values for the plane function which returns the Z offset of the
 *      *  occluder from the canvas based on local x and y values (the current matrix is not applied).
 *      * @param lightPos  Generally, the 3D position of the light relative to the canvas plane.
 *      *                  If kDirectionalLight_ShadowFlag is set, this specifies a vector pointing
 *      *                  towards the light.
 *      * @param lightRadius  Generally, the radius of the disc light.
 *      *                     If DirectionalLight_ShadowFlag is set, this specifies the amount of
 *      *                     blur when the occluder is at Z offset == 1. The blur will grow linearly
 *      *                     as the Z value increases.
 *      * @param flags  Options controlling opaque occluder optimizations, shadow appearance,
 *      *               and light position. See SkShadowFlags.
 *      * @param bounds Return value for shadow bounding box.
 *      * @return Returns true if successful, false otherwise.
 *      */
 *     static bool GetLocalBounds(const SkMatrix& ctm, const SkPath& path,
 *                                const SkPoint3& zPlaneParams, const SkPoint3& lightPos,
 *                                SkScalar lightRadius, uint32_t flags, SkRect* bounds);
 *
 *     /**
 *      * Helper routine to compute color values for one-pass tonal alpha.
 *      *
 *      * @param inAmbientColor  Original ambient color
 *      * @param inSpotColor  Original spot color
 *      * @param outAmbientColor  Modified ambient color
 *      * @param outSpotColor  Modified spot color
 *      */
 *     static void ComputeTonalColors(SkColor inAmbientColor, SkColor inSpotColor,
 *                                    SkColor* outAmbientColor, SkColor* outSpotColor);
 * }
 * ```
 */
public open class SkShadowUtils {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkShadowUtils::DrawShadow(SkCanvas* canvas, const SkPath& path, const SkPoint3& zPlaneParams,
     *                                const SkPoint3& lightPos, SkScalar lightRadius,
     *                                SkColor ambientColor, SkColor spotColor,
     *                                uint32_t flags) {
     *     SkDrawShadowRec rec;
     *     if (!fill_shadow_rec(path, zPlaneParams, lightPos, lightRadius, ambientColor, spotColor,
     *                          flags, canvas->getTotalMatrix(), &rec)) {
     *         return;
     *     }
     *
     *     canvas->private_draw_shadow_rec(path, rec);
     * }
     * ```
     */
    public fun drawShadow(
      canvas: SkCanvas?,
      path: SkPath,
      zPlaneParams: SkPoint3,
      lightPos: SkPoint3,
      lightRadius: SkScalar,
      ambientColor: SkColor,
      spotColor: SkColor,
      flags: UInt = TODO(),
    ) {
      TODO("Implement drawShadow")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkShadowUtils::GetLocalBounds(const SkMatrix& ctm, const SkPath& path,
     *                                    const SkPoint3& zPlaneParams, const SkPoint3& lightPos,
     *                                    SkScalar lightRadius, uint32_t flags, SkRect* bounds) {
     *     SkDrawShadowRec rec;
     *     if (!fill_shadow_rec(path, zPlaneParams, lightPos, lightRadius, SK_ColorBLACK, SK_ColorBLACK,
     *                          flags, ctm, &rec)) {
     *         return false;
     *     }
     *
     *     SkDrawShadowMetrics::GetLocalBounds(path, rec, ctm, bounds);
     *
     *     return true;
     * }
     * ```
     */
    public fun getLocalBounds(
      ctm: SkMatrix,
      path: SkPath,
      zPlaneParams: SkPoint3,
      lightPos: SkPoint3,
      lightRadius: SkScalar,
      flags: UInt,
      bounds: SkRect?,
    ): Boolean {
      TODO("Implement getLocalBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShadowUtils::ComputeTonalColors(SkColor inAmbientColor, SkColor inSpotColor,
     *                                        SkColor* outAmbientColor, SkColor* outSpotColor) {
     *     // For tonal color we only compute color values for the spot shadow.
     *     // The ambient shadow is greyscale only.
     *
     *     // Ambient
     *     *outAmbientColor = SkColorSetARGB(SkColorGetA(inAmbientColor), 0, 0, 0);
     *
     *     // Spot
     *     int spotR = SkColorGetR(inSpotColor);
     *     int spotG = SkColorGetG(inSpotColor);
     *     int spotB = SkColorGetB(inSpotColor);
     *     int max = std::max(std::max(spotR, spotG), spotB);
     *     int min = std::min(std::min(spotR, spotG), spotB);
     *     SkScalar luminance = 0.5f*(max + min)/255.f;
     *     SkScalar origA = SkColorGetA(inSpotColor)/255.f;
     *
     *     // We compute a color alpha value based on the luminance of the color, scaled by an
     *     // adjusted alpha value. We want the following properties to match the UX examples
     *     // (assuming a = 0.25) and to ensure that we have reasonable results when the color
     *     // is black and/or the alpha is 0:
     *     //     f(0, a) = 0
     *     //     f(luminance, 0) = 0
     *     //     f(1, 0.25) = .5
     *     //     f(0.5, 0.25) = .4
     *     //     f(1, 1) = 1
     *     // The following functions match this as closely as possible.
     *     SkScalar alphaAdjust = (2.6f + (-2.66667f + 1.06667f*origA)*origA)*origA;
     *     SkScalar colorAlpha = (3.544762f + (-4.891428f + 2.3466f*luminance)*luminance)*luminance;
     *     colorAlpha = SkTPin(alphaAdjust*colorAlpha, 0.0f, 1.0f);
     *
     *     // Similarly, we set the greyscale alpha based on luminance and alpha so that
     *     //     f(0, a) = a
     *     //     f(luminance, 0) = 0
     *     //     f(1, 0.25) = 0.15
     *     SkScalar greyscaleAlpha = SkTPin(origA*(1 - 0.4f*luminance), 0.0f, 1.0f);
     *
     *     // The final color we want to emulate is generated by rendering a color shadow (C_rgb) using an
     *     // alpha computed from the color's luminance (C_a), and then a black shadow with alpha (S_a)
     *     // which is an adjusted value of 'a'.  Assuming SrcOver, a background color of B_rgb, and
     *     // ignoring edge falloff, this becomes
     *     //
     *     //      (C_a - S_a*C_a)*C_rgb + (1 - (S_a + C_a - S_a*C_a))*B_rgb
     *     //
     *     // Assuming premultiplied alpha, this means we scale the color by (C_a - S_a*C_a) and
     *     // set the alpha to (S_a + C_a - S_a*C_a).
     *     SkScalar colorScale = colorAlpha*(SK_Scalar1 - greyscaleAlpha);
     *     SkScalar tonalAlpha = colorScale + greyscaleAlpha;
     *     SkScalar unPremulScale = colorScale / tonalAlpha;
     *     *outSpotColor = SkColorSetARGB(tonalAlpha*255.999f,
     *                                    unPremulScale*spotR,
     *                                    unPremulScale*spotG,
     *                                    unPremulScale*spotB);
     * }
     * ```
     */
    public fun computeTonalColors(
      inAmbientColor: SkColor,
      inSpotColor: SkColor,
      outAmbientColor: SkColor?,
      outSpotColor: SkColor?,
    ) {
      TODO("Implement computeTonalColors")
    }
  }
}
