package org.skia.effects

import kotlin.Float
import org.skia.core.ParameterSpace

/**
 * C++ original:
 * ```cpp
 * struct Material {
 *     enum class Type {
 *         kDiffuse,
 *         kSpecular,
 *         kEmbossSpecular,
 *         kLast = kEmbossSpecular
 *     };
 *
 *     Type fType;
 *     // The base scale factor applied to alpha image to go from [0-1] to [0-depth] before computing
 *     // surface normals.
 *     skif::ParameterSpace<ZValue> fSurfaceDepth;
 *
 *     // Non-geometric
 *     float fK; // Reflectance coefficient
 *     float fShininess; // Specular only
 *
 *     static Material Diffuse(float k, float surfaceDepth) {
 *         return {Type::kDiffuse, skif::ParameterSpace<ZValue>(surfaceDepth), k, 0.f};
 *     }
 *
 *     static Material Specular(float k, float shininess, float surfaceDepth) {
 *         return {Type::kSpecular, skif::ParameterSpace<ZValue>(surfaceDepth), k, shininess};
 *     }
 *
 *     static Material EmbossSpecular(float k, float shininess, float surfaceDepth) {
 *         return {Type::kEmbossSpecular , skif::ParameterSpace<ZValue>(surfaceDepth), k, shininess};
 *     }
 * }
 * ```
 */
public data class Material public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  public var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<ZValue> fSurfaceDepth
   * ```
   */
  public var fSurfaceDepth: ParameterSpace<ZValue>,
  /**
   * C++ original:
   * ```cpp
   * float fK
   * ```
   */
  public var fK: Float,
  /**
   * C++ original:
   * ```cpp
   * float fShininess
   * ```
   */
  public var fShininess: Float,
) {
  public enum class Type {
    kDiffuse,
    kSpecular,
    kEmbossSpecular,
    kLast,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Material Diffuse(float k, float surfaceDepth) {
     *         return {Type::kDiffuse, skif::ParameterSpace<ZValue>(surfaceDepth), k, 0.f};
     *     }
     * ```
     */
    public fun diffuse(k: Float, surfaceDepth: Float): Material {
      TODO("Implement diffuse")
    }

    /**
     * C++ original:
     * ```cpp
     * static Material Specular(float k, float shininess, float surfaceDepth) {
     *         return {Type::kSpecular, skif::ParameterSpace<ZValue>(surfaceDepth), k, shininess};
     *     }
     * ```
     */
    public fun specular(
      k: Float,
      shininess: Float,
      surfaceDepth: Float,
    ): Material {
      TODO("Implement specular")
    }

    /**
     * C++ original:
     * ```cpp
     * static Material EmbossSpecular(float k, float shininess, float surfaceDepth) {
     *         return {Type::kEmbossSpecular , skif::ParameterSpace<ZValue>(surfaceDepth), k, shininess};
     *     }
     * ```
     */
    public fun embossSpecular(
      k: Float,
      shininess: Float,
      surfaceDepth: Float,
    ): Material {
      TODO("Implement embossSpecular")
    }
  }
}
