package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSpan
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.sksl.Program

/**
 * C++ original:
 * ```cpp
 * struct SkMeshSpecificationPriv {
 *     using Varying   = SkMeshSpecification::Varying;
 *     using Attribute = SkMeshSpecification::Attribute;
 *     using ColorType = SkMeshSpecification::ColorType;
 *
 *     static SkSpan<const Varying> Varyings(const SkMeshSpecification& spec) {
 *         return SkSpan(spec.fVaryings);
 *     }
 *
 *     static const SkSL::Program* VS(const SkMeshSpecification& spec) { return spec.fVS.get(); }
 *     static const SkSL::Program* FS(const SkMeshSpecification& spec) { return spec.fFS.get(); }
 *
 *     static int Hash(const SkMeshSpecification& spec) { return spec.fHash; }
 *
 *     static ColorType GetColorType(const SkMeshSpecification& spec) { return spec.fColorType; }
 *     static bool HasColors(const SkMeshSpecification& spec) {
 *         return GetColorType(spec) != ColorType::kNone;
 *     }
 *
 *     static SkColorSpace* ColorSpace(const SkMeshSpecification& spec) {
 *         return spec.fColorSpace.get();
 *     }
 *
 *     static SkAlphaType AlphaType(const SkMeshSpecification& spec) { return spec.fAlphaType; }
 *
 *     static SkSLType VaryingTypeAsSLType(Varying::Type type) {
 *         switch (type) {
 *             case Varying::Type::kFloat:  return SkSLType::kFloat;
 *             case Varying::Type::kFloat2: return SkSLType::kFloat2;
 *             case Varying::Type::kFloat3: return SkSLType::kFloat3;
 *             case Varying::Type::kFloat4: return SkSLType::kFloat4;
 *             case Varying::Type::kHalf:   return SkSLType::kHalf;
 *             case Varying::Type::kHalf2:  return SkSLType::kHalf2;
 *             case Varying::Type::kHalf3:  return SkSLType::kHalf3;
 *             case Varying::Type::kHalf4:  return SkSLType::kHalf4;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     static SkSLType AttrTypeAsSLType(Attribute::Type type) {
 *         switch (type) {
 *             case Attribute::Type::kFloat:        return SkSLType::kFloat;
 *             case Attribute::Type::kFloat2:       return SkSLType::kFloat2;
 *             case Attribute::Type::kFloat3:       return SkSLType::kFloat3;
 *             case Attribute::Type::kFloat4:       return SkSLType::kFloat4;
 *             case Attribute::Type::kUByte4_unorm: return SkSLType::kHalf4;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     static int PassthroughLocalCoordsVaryingIndex(const SkMeshSpecification& spec) {
 *         return spec.fPassthroughLocalCoordsVaryingIndex;
 *     }
 *
 *     /**
 *      * A varying is dead if it is never referenced OR it is only referenced as a passthrough for
 *      * local coordinates. In the latter case, its index will returned as
 *      * PassthroughLocalCoordsVaryingIndex. Our analysis is not very sophisticated so this is
 *      * determined conservatively.
 *      */
 *     static bool VaryingIsDead(const SkMeshSpecification& spec, int v) {
 *         SkASSERT(v >= 0 && SkToSizeT(v) < spec.fVaryings.size());
 *         return (1 << v) & spec.fDeadVaryingMask;
 *     }
 * }
 * ```
 */
public open class SkMeshSpecificationPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkSpan<const Varying> Varyings(const SkMeshSpecification& spec) {
     *         return SkSpan(spec.fVaryings);
     *     }
     * ```
     */
    public fun varyings(spec: SkMeshSpecification): SkSpan<SkMeshSpecificationPrivVarying> {
      TODO("Implement varyings")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkSL::Program* VS(const SkMeshSpecification& spec) { return spec.fVS.get(); }
     * ```
     */
    public fun vs(spec: SkMeshSpecification): Program {
      TODO("Implement vs")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkSL::Program* FS(const SkMeshSpecification& spec) { return spec.fFS.get(); }
     * ```
     */
    public fun fs(spec: SkMeshSpecification): Program {
      TODO("Implement fs")
    }

    /**
     * C++ original:
     * ```cpp
     * static int Hash(const SkMeshSpecification& spec) { return spec.fHash; }
     * ```
     */
    public fun hash(spec: SkMeshSpecification): Int {
      TODO("Implement hash")
    }

    /**
     * C++ original:
     * ```cpp
     * static ColorType GetColorType(const SkMeshSpecification& spec) { return spec.fColorType; }
     * ```
     */
    public fun getColorType(spec: SkMeshSpecification): SkMeshSpecificationPrivColorType {
      TODO("Implement getColorType")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool HasColors(const SkMeshSpecification& spec) {
     *         return GetColorType(spec) != ColorType::kNone;
     *     }
     * ```
     */
    public fun hasColors(spec: SkMeshSpecification): Boolean {
      TODO("Implement hasColors")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkColorSpace* ColorSpace(const SkMeshSpecification& spec) {
     *         return spec.fColorSpace.get();
     *     }
     * ```
     */
    public fun colorSpace(spec: SkMeshSpecification): SkColorSpace {
      TODO("Implement colorSpace")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkAlphaType AlphaType(const SkMeshSpecification& spec) { return spec.fAlphaType; }
     * ```
     */
    public fun alphaType(spec: SkMeshSpecification): SkAlphaType {
      TODO("Implement alphaType")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSLType VaryingTypeAsSLType(Varying::Type type) {
     *         switch (type) {
     *             case Varying::Type::kFloat:  return SkSLType::kFloat;
     *             case Varying::Type::kFloat2: return SkSLType::kFloat2;
     *             case Varying::Type::kFloat3: return SkSLType::kFloat3;
     *             case Varying::Type::kFloat4: return SkSLType::kFloat4;
     *             case Varying::Type::kHalf:   return SkSLType::kHalf;
     *             case Varying::Type::kHalf2:  return SkSLType::kHalf2;
     *             case Varying::Type::kHalf3:  return SkSLType::kHalf3;
     *             case Varying::Type::kHalf4:  return SkSLType::kHalf4;
     *         }
     *         SkUNREACHABLE;
     *     }
     * ```
     */
    public fun varyingTypeAsSLType(type: SkMeshSpecificationPrivVarying.Type): SkSLType {
      TODO("Implement varyingTypeAsSLType")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSLType AttrTypeAsSLType(Attribute::Type type) {
     *         switch (type) {
     *             case Attribute::Type::kFloat:        return SkSLType::kFloat;
     *             case Attribute::Type::kFloat2:       return SkSLType::kFloat2;
     *             case Attribute::Type::kFloat3:       return SkSLType::kFloat3;
     *             case Attribute::Type::kFloat4:       return SkSLType::kFloat4;
     *             case Attribute::Type::kUByte4_unorm: return SkSLType::kHalf4;
     *         }
     *         SkUNREACHABLE;
     *     }
     * ```
     */
    public fun attrTypeAsSLType(type: SkMeshSpecificationPrivAttribute.Type): SkSLType {
      TODO("Implement attrTypeAsSLType")
    }

    /**
     * C++ original:
     * ```cpp
     * static int PassthroughLocalCoordsVaryingIndex(const SkMeshSpecification& spec) {
     *         return spec.fPassthroughLocalCoordsVaryingIndex;
     *     }
     * ```
     */
    public fun passthroughLocalCoordsVaryingIndex(spec: SkMeshSpecification): Int {
      TODO("Implement passthroughLocalCoordsVaryingIndex")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool VaryingIsDead(const SkMeshSpecification& spec, int v) {
     *         SkASSERT(v >= 0 && SkToSizeT(v) < spec.fVaryings.size());
     *         return (1 << v) & spec.fDeadVaryingMask;
     *     }
     * ```
     */
    public fun varyingIsDead(spec: SkMeshSpecification, v: Int): Boolean {
      TODO("Implement varyingIsDead")
    }
  }
}
