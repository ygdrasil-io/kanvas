package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkImageFilter
import org.skia.math.SkIPoint
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Mapping {
 * public:
 *     Mapping() = default;
 *
 *     // Helper constructor that equates device and layer space to the same coordinate space.
 *     explicit Mapping(const SkM44& paramToLayer)
 *             : fLayerToDevMatrix(SkM44())
 *             , fParamToLayerMatrix(paramToLayer)
 *             , fDevToLayerMatrix(SkM44()) {}
 *
 *     // This constructor allows the decomposition to be explicitly provided, assumes that
 *     // 'layerToDev's inverse has already been calculated in 'devToLayer'
 *     Mapping(const SkM44& layerToDev, const SkM44& devToLayer, const SkM44& paramToLayer)
 *             : fLayerToDevMatrix(layerToDev)
 *             , fParamToLayerMatrix(paramToLayer)
 *             , fDevToLayerMatrix(devToLayer) {}
 *
 *     // Sets this Mapping to the default decomposition of the canvas's total transform, given the
 *     // requirements of the 'filter'. Returns false if the decomposition failed or would produce an
 *     // invalid device matrix. Assumes 'ctm' is invertible.
 *     [[nodiscard]] bool decomposeCTM(const SkM44& ctm,
 *                                     const SkImageFilter* filter,
 *                                     const skif::ParameterSpace<SkPoint>& representativePt);
 *     [[nodiscard]] bool decomposeCTM(const SkM44& ctm,
 *                                     MatrixCapability,
 *                                     const skif::ParameterSpace<SkPoint>& representativePt);
 *
 *     // Update the mapping's parameter-to-layer matrix to be pre-concatenated with the specified
 *     // local space transformation. This changes the definition of parameter space, any
 *     // skif::ParameterSpace<> values are interpreted anew. Layer space and device space are
 *     // unchanged.
 *     void concatLocal(const SkMatrix& local) { fParamToLayerMatrix.preConcat(local); }
 *
 *     // Update the mapping's layer space coordinate system by post-concatenating the given matrix
 *     // to it's parameter-to-layer transform, and pre-concatenating the inverse of the matrix with
 *     // it's layer-to-device transform. The net effect is that neither the parameter nor device
 *     // coordinate systems are changed, but skif::LayerSpace is adjusted.
 *     //
 *     // Returns false if the layer matrix cannot be inverted, and this mapping is left unmodified.
 *     bool adjustLayerSpace(const SkM44& layer);
 *
 *     // Update the mapping's layer space so that the point 'origin' in the current layer coordinate
 *     // space maps to (0, 0) in the adjusted coordinate space.
 *     void applyOrigin(const LayerSpace<SkIPoint>& origin) {
 *         SkAssertResult(this->adjustLayerSpace(SkM44::Translate(-origin.x(), -origin.y())));
 *     }
 *
 *     const SkM44& layerToDevice() const { return fLayerToDevMatrix; }
 *     const SkM44& deviceToLayer() const { return fDevToLayerMatrix; }
 *     const SkM44& layerMatrix() const { return fParamToLayerMatrix; }
 *     SkM44 totalMatrix() const {
 *         return fLayerToDevMatrix * fParamToLayerMatrix;
 *     }
 *
 *     template<typename T>
 *     LayerSpace<T> paramToLayer(const ParameterSpace<T>& paramGeometry) const {
 *         return LayerSpace<T>(map(static_cast<const T&>(paramGeometry),
 *                                  fParamToLayerMatrix.asM33()));
 *     }
 *
 *     template<typename T>
 *     LayerSpace<T> deviceToLayer(const DeviceSpace<T>& devGeometry) const {
 *         // For inverse mapping back to layer space, we may be undoing perspective projection.
 *         // Using fDevToLayerMatrix for this would require knowing the device-space Z values,
 *         // which are discarded. fDevToLayerMatrix.asM33() would operate as if all those
 *         // Z values were 0 (this is true for local 2D geometry, not device space). Instead,
 *         // derive the 3x3 inverse of the flattened layer-to-device matrix, returning empty
 *         // if numerical stability meant its 4x4 was invertible but somehow the 3x3 wasn't.
 *         if (auto devToLayer33 = fLayerToDevMatrix.asM33().invert()) {
 *             return LayerSpace<T>(map(static_cast<const T&>(devGeometry), *devToLayer33));
 *         }
 *         return LayerSpace<T>::Empty();
 *     }
 *
 *     template<typename T>
 *     DeviceSpace<T> layerToDevice(const LayerSpace<T>& layerGeometry) const {
 *         return DeviceSpace<T>(map(static_cast<const T&>(layerGeometry), fLayerToDevMatrix.asM33()));
 *     }
 *
 * private:
 *     friend class LayerSpace<SkMatrix>; // for map()
 *     friend class FilterResult;         // ""
 *
 *     // The image filter process decomposes the total CTM into layerToDev * paramToLayer and uses the
 *     // param-to-layer matrix to define the layer-space coordinate system. Depending on how it's
 *     // decomposed, either the layer matrix or the device matrix could be the identity matrix (but
 *     // sometimes neither).
 *     SkM44 fLayerToDevMatrix;
 *     SkM44 fParamToLayerMatrix;
 *
 *     // Cached inverse of fLayerToDevMatrix. We keep this as 4x4 so that conversion between different
 *     // SkDevice coordinate spaces and coord space reconstruction is lossless.
 *     SkM44 fDevToLayerMatrix;
 *
 *     // Actual geometric mapping operations that work on coordinates and matrices w/o the type
 *     // safety of the coordinate space wrappers (hence these are private).
 *     // TODO(b/40042800): Finish moving skif::Mapping operations to use the SkM44 directly.
 *     template<typename T>
 *     static T map(const T& geom, const SkMatrix& matrix);
 * }
 * ```
 */
public data class Mapping public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkM44 fLayerToDevMatrix
   * ```
   */
  private var fLayerToDevMatrix: SkM44,
  /**
   * C++ original:
   * ```cpp
   * SkM44 fParamToLayerMatrix
   * ```
   */
  private var fParamToLayerMatrix: SkM44,
  /**
   * C++ original:
   * ```cpp
   * SkM44 fDevToLayerMatrix
   * ```
   */
  private var fDevToLayerMatrix: SkM44,
) {
  /**
   * C++ original:
   * ```cpp
   * bool Mapping::decomposeCTM(const SkM44& ctm,
   *                            const SkImageFilter* filter,
   *                            const skif::ParameterSpace<SkPoint>& representativePt) {
   *     return this->decomposeCTM(
   *             ctm,
   *             filter ? as_IFB(filter)->getCTMCapability() : MatrixCapability::kComplex,
   *             representativePt);
   * }
   * ```
   */
  public fun decomposeCTM(
    ctm: SkM44,
    filter: SkImageFilter?,
    representativePt: ParameterSpace<SkPoint>,
  ): Boolean {
    TODO("Implement decomposeCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Mapping::decomposeCTM(const SkM44& ctm, MatrixCapability capability,
   *                            const skif::ParameterSpace<SkPoint>& representativePt) {
   *     SkM44 remainder{SkM44::kUninitialized_Constructor};
   *     SkM44 layer{SkM44::kUninitialized_Constructor};
   *     if (capability == MatrixCapability::kTranslate) {
   *         // Apply the entire CTM post-filtering
   *         remainder = ctm;
   *         layer = SkM44();
   *     } else if (SkMatrixPriv::IsScaleTranslateAsM33(ctm) ||
   *                capability == MatrixCapability::kComplex) {
   *         // Either layer space can be anything (kComplex) - or - it can be scale+translate, and the
   *         // ctm is. In both cases, the layer space can be equivalent to device space.
   *         remainder = SkM44();
   *         layer = ctm;
   *     } else {
   *         // This case implies some amount of sampling post-filtering, either due to skew or rotation
   *         // in the original matrix. As such, keep the layer matrix as simple as possible.
   *         SkMatrix layer33;
   *         decompose_transform(ctm.asM33(), SkPoint(representativePt),
   *                             /*postScaling=*/nullptr, &layer33);
   *         layer = SkM44(layer33);
   *         // Reconstruct full 4x4 remainder matrix so the mapping doesn't lose the 3rd row/column.
   *         remainder = ctm;
   *         remainder.preScale(1.f / layer.rc(0,0), 1.f / layer.rc(1,1));
   *     }
   *
   *     SkM44 invRemainder;
   *     if (!remainder.invert(&invRemainder)) {
   *         // Under floating point arithmetic, it's possible to decompose an invertible matrix into
   *         // a scaling matrix and a remainder and have the remainder be non-invertible. Generally
   *         // when this happens the scale factors are so large and the matrix so ill-conditioned that
   *         // it's unlikely that any drawing would be reasonable, so failing to make a layer is okay.
   *         return false;
   *     } else {
   *         fParamToLayerMatrix = layer;
   *         fLayerToDevMatrix = remainder;
   *         fDevToLayerMatrix = invRemainder;
   *         return true;
   *     }
   * }
   * ```
   */
  public fun decomposeCTM(
    ctm: SkM44,
    capability: MatrixCapability,
    representativePt: ParameterSpace<SkPoint>,
  ): Boolean {
    TODO("Implement decomposeCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void concatLocal(const SkMatrix& local) { fParamToLayerMatrix.preConcat(local); }
   * ```
   */
  public fun concatLocal(local: SkMatrix) {
    TODO("Implement concatLocal")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Mapping::adjustLayerSpace(const SkM44& layer) {
   *     SkM44 invLayer;
   *     if (!layer.invert(&invLayer)) {
   *         return false;
   *     }
   *     fParamToLayerMatrix.postConcat(layer);
   *     fDevToLayerMatrix.postConcat(layer);
   *     fLayerToDevMatrix.preConcat(invLayer);
   *     return true;
   * }
   * ```
   */
  public fun adjustLayerSpace(layer: SkM44): Boolean {
    TODO("Implement adjustLayerSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * void applyOrigin(const LayerSpace<SkIPoint>& origin) {
   *         SkAssertResult(this->adjustLayerSpace(SkM44::Translate(-origin.x(), -origin.y())));
   *     }
   * ```
   */
  public fun applyOrigin(origin: LayerSpace<SkIPoint>) {
    TODO("Implement applyOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& layerToDevice() const { return fLayerToDevMatrix; }
   * ```
   */
  public fun layerToDevice(): SkM44 {
    TODO("Implement layerToDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& deviceToLayer() const { return fDevToLayerMatrix; }
   * ```
   */
  public fun deviceToLayer(): SkM44 {
    TODO("Implement deviceToLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& layerMatrix() const { return fParamToLayerMatrix; }
   * ```
   */
  public fun layerMatrix(): SkM44 {
    TODO("Implement layerMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44 totalMatrix() const {
   *         return fLayerToDevMatrix * fParamToLayerMatrix;
   *     }
   * ```
   */
  public fun totalMatrix(): SkM44 {
    TODO("Implement totalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     LayerSpace<T> paramToLayer(const ParameterSpace<T>& paramGeometry) const {
   *         return LayerSpace<T>(map(static_cast<const T&>(paramGeometry),
   *                                  fParamToLayerMatrix.asM33()));
   *     }
   * ```
   */
  public fun <T> paramToLayer(paramGeometry: ParameterSpace<T>): LayerSpace<T> {
    TODO("Implement paramToLayer")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     LayerSpace<T> deviceToLayer(const DeviceSpace<T>& devGeometry) const {
   *         // For inverse mapping back to layer space, we may be undoing perspective projection.
   *         // Using fDevToLayerMatrix for this would require knowing the device-space Z values,
   *         // which are discarded. fDevToLayerMatrix.asM33() would operate as if all those
   *         // Z values were 0 (this is true for local 2D geometry, not device space). Instead,
   *         // derive the 3x3 inverse of the flattened layer-to-device matrix, returning empty
   *         // if numerical stability meant its 4x4 was invertible but somehow the 3x3 wasn't.
   *         if (auto devToLayer33 = fLayerToDevMatrix.asM33().invert()) {
   *             return LayerSpace<T>(map(static_cast<const T&>(devGeometry), *devToLayer33));
   *         }
   *         return LayerSpace<T>::Empty();
   *     }
   * ```
   */
  public fun <T> deviceToLayer(devGeometry: DeviceSpace<T>): LayerSpace<T> {
    TODO("Implement deviceToLayer")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     DeviceSpace<T> layerToDevice(const LayerSpace<T>& layerGeometry) const {
   *         return DeviceSpace<T>(map(static_cast<const T&>(layerGeometry), fLayerToDevMatrix.asM33()));
   *     }
   * ```
   */
  public fun <T> layerToDevice(layerGeometry: LayerSpace<T>): DeviceSpace<T> {
    TODO("Implement layerToDevice")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * template<>
     * SkMatrix Mapping::map<SkMatrix>(const SkMatrix& m, const SkMatrix& matrix) {
     *     // If 'matrix' maps from the C1 coord space to the C2 coord space, and 'm' is a transform that
     *     // operates on, and outputs to, the C1 coord space, we want to return a new matrix that is
     *     // equivalent to 'm' that operates on and outputs to C2. This is the same as mapping the input
     *     // from C2 to C1 (matrix^-1), then transforming by 'm', and then mapping from C1 to C2 (matrix).
     *     SkMatrix inv = matrix.invert().value_or(SkMatrix());
     *     inv.postConcat(m);
     *     inv.postConcat(matrix);
     *     return inv;
     * }
     * ```
     */
    private fun <T> map(geom: T, matrix: SkMatrix): T {
      TODO("Implement map")
    }
  }
}
