package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSpan

public open class SkMatrix public constructor() {
  private var fMat: IntArray = TODO("Initialize fMat")

  private var fTypeMask: Int = TODO("Initialize fTypeMask")

  public constructor(
    sx: SkScalar,
    kx: SkScalar,
    tx: SkScalar,
    ky: SkScalar,
    sy: SkScalar,
    ty: SkScalar,
    p0: SkScalar,
    p1: SkScalar,
    p2: SkScalar,
    typeMask: Int,
  ) : this() {
    TODO("Implement constructor")
  }

  public fun getType(): TypeMask {
    TODO("Implement getType")
  }

  public fun isIdentity(): Boolean {
    TODO("Implement isIdentity")
  }

  public fun isScaleTranslate(): Boolean {
    return !(getType().ordinal and (TypeMask.kScale_Mask.ordinal or TypeMask.kTranslate_Mask.ordinal).inv()).let { it != 0 }
  }

  public fun isTranslate(): Boolean {
    TODO("Implement isTranslate")
  }

  public fun rectStaysRect(): Boolean {
    return this.rectStaysRect()
  }

  public fun preservesAxisAlignment(): Boolean {
    return this.rectStaysRect()
  }

  public fun hasPerspective(): Boolean {
    TODO("Implement hasPerspective")
  }

  public fun isSimilarity(tol: SkScalar = TODO()): Boolean {
    return getType() == SkMatrix.TypeMask.kScale_Mask || getType() == SkMatrix.TypeMask.kTranslate_Mask || getType() == SkMatrix.TypeMask.kScale_Mask || getType() == SkMatrix.TypeMask.kIdentity_Mask
  }

  public fun preservesRightAngles(tol: SkScalar = TODO()): Boolean {
    return this.preservesAxisAlignment()
  }

  public operator fun `get`(index: Int): Int {
    return get(index)
  }

  public fun rc(r: Int, c: Int): Int {
    TODO("Implement rc")
  }

  public fun getScaleX(): Int {
    TODO("Implement getScaleX")
  }

  public fun getScaleY(): Int {
    TODO("Implement getScaleY")
  }

  public fun getSkewY(): Int {
    TODO("Implement getSkewY")
  }

  public fun getSkewX(): Int {
    TODO("Implement getSkewX")
  }

  public fun getTranslateX(): Int {
    TODO("Implement getTranslateX")
  }

  public fun getTranslateY(): Int {
    TODO("Implement getTranslateY")
  }

  public fun getPerspX(): Int {
    TODO("Implement getPerspX")
  }

  public fun getPerspY(): Int {
    TODO("Implement getPerspY")
  }

  public fun `set`(index: Int, `value`: SkScalar): SkMatrix {
    TODO("Implement set")
  }

  public fun setScaleX(v: SkScalar): SkMatrix {
    TODO("Implement setScaleX")
  }

  public fun setScaleY(v: SkScalar): SkMatrix {
    TODO("Implement setScaleY")
  }

  public fun setSkewY(v: SkScalar): SkMatrix {
    TODO("Implement setSkewY")
  }

  public fun setSkewX(v: SkScalar): SkMatrix {
    TODO("Implement setSkewX")
  }

  public fun setTranslateX(v: SkScalar): SkMatrix {
    TODO("Implement setTranslateX")
  }

  public fun setTranslateY(v: SkScalar): SkMatrix {
    TODO("Implement setTranslateY")
  }

  public fun setPerspX(v: SkScalar): SkMatrix {
    TODO("Implement setPerspX")
  }

  public fun setPerspY(v: SkScalar): SkMatrix {
    TODO("Implement setPerspY")
  }

  public fun setAll(
    scaleX: SkScalar,
    skewX: SkScalar,
    transX: SkScalar,
    skewY: SkScalar,
    scaleY: SkScalar,
    transY: SkScalar,
    persp0: SkScalar,
    persp1: SkScalar,
    persp2: SkScalar,
  ): SkMatrix {
    TODO("Implement setAll")
  }

  public fun get9(buffer: Array<SkScalar>) {
    TODO("Implement get9")
  }

  public fun set9(buffer: Array<SkScalar>): SkMatrix {
    TODO("Implement set9")
  }

  public fun reset(): SkMatrix {
    TODO("Implement reset")
  }

  public fun setIdentity(): SkMatrix {
    TODO("Implement setIdentity")
  }

  public fun setTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement setTranslate")
  }

  public fun setTranslate(v: SkVector): SkMatrix {
    TODO("Implement setTranslate")
  }

  public fun setScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setScale")
  }

  public fun setScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement setScale")
  }

  public fun setRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setRotate")
  }

  public fun setRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement setRotate")
  }

  public fun setSinCos(
    sinValue: SkScalar,
    cosValue: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setSinCos")
  }

  public fun setSinCos(sinValue: SkScalar, cosValue: SkScalar): SkMatrix {
    TODO("Implement setSinCos")
  }

  public fun setRSXform(rsxForm: SkRSXform): SkMatrix {
    TODO("Implement setRSXform")
  }

  public fun setSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setSkew")
  }

  public fun setSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement setSkew")
  }

  public fun setConcat(a: SkMatrix, b: SkMatrix): SkMatrix {
    TODO("Implement setConcat")
  }

  public fun preTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement preTranslate")
  }

  public fun preScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preScale")
  }

  public fun preScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement preScale")
  }

  public fun preRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preRotate")
  }

  public fun preRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement preRotate")
  }

  public fun preSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preSkew")
  }

  public fun preSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement preSkew")
  }

  public fun preConcat(other: SkMatrix): SkMatrix {
    TODO("Implement preConcat")
  }

  public fun postTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement postTranslate")
  }

  public fun postScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postScale")
  }

  public fun postScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement postScale")
  }

  public fun postRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postRotate")
  }

  public fun postRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement postRotate")
  }

  public fun postSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postSkew")
  }

  public fun postSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement postSkew")
  }

  public fun postConcat(other: SkMatrix): SkMatrix {
    TODO("Implement postConcat")
  }

  public fun setRectToRect(
    src: SkRect,
    dst: SkRect,
    stf: ScaleToFit,
  ): Boolean {
    TODO("Implement setRectToRect")
  }

  public fun setPolyToPoly(src: SkSpan<SkPoint>, dst: SkSpan<SkPoint>): Boolean {
    TODO("Implement setPolyToPoly")
  }

  public fun invert(): SkMatrix? {
    TODO("Implement invert")
  }

  public fun invert(inverse: SkMatrix?): Boolean {
    TODO("Implement invert")
  }

  public fun asAffine(affine: Array<SkScalar>): Boolean {
    TODO("Implement asAffine")
  }

  public fun setAffine(affine: Array<SkScalar>): SkMatrix {
    TODO("Implement setAffine")
  }

  public fun normalizePerspective() {
    TODO("Implement normalizePerspective")
  }

  public fun mapPoints(dst: SkSpan<SkPoint>, src: SkSpan<SkPoint>) {
    TODO("Implement mapPoints")
  }

  public fun mapPoints(pts: SkSpan<SkPoint>) {
    TODO("Implement mapPoints")
  }

  public fun mapHomogeneousPoints(dst: SkSpan<SkPoint3>, src: SkSpan<SkPoint3>) {
    TODO("Implement mapHomogeneousPoints")
  }

  public fun mapHomogeneousPoint(src: SkPoint3): Int {
    TODO("Implement mapHomogeneousPoint")
  }

  public fun mapPointsToHomogeneous(dst: SkSpan<SkPoint3>, src: SkSpan<SkPoint>) {
    TODO("Implement mapPointsToHomogeneous")
  }

  public fun mapPointToHomogeneous(src: SkPoint): Int {
    TODO("Implement mapPointToHomogeneous")
  }

  public fun mapPoint(p: SkPoint): Int {
    TODO("Implement mapPoint")
  }

  public fun mapPointAffine(p: SkPoint): Int {
    TODO("Implement mapPointAffine")
  }

  public fun mapOrigin(): Int {
    TODO("Implement mapOrigin")
  }

  public fun mapVectors(dst: SkSpan<SkVector>, src: SkSpan<SkVector>) {
    TODO("Implement mapVectors")
  }

  public fun mapVectors(vecs: SkSpan<SkVector>) {
    TODO("Implement mapVectors")
  }

  public fun mapVector(vec: SkVector): Int {
    TODO("Implement mapVector")
  }

  public fun mapVector(dx: SkScalar, dy: SkScalar): Int {
    TODO("Implement mapVector")
  }

  public fun mapRect(dst: SkRect?, src: SkRect): Boolean {
    TODO("Implement mapRect")
  }

  public fun mapRect(rect: SkRect?): Boolean {
    TODO("Implement mapRect")
  }

  public fun mapRect(src: SkRect): Int {
    TODO("Implement mapRect")
  }

  public fun mapRectToQuad(dst: Array<SkPoint>, rect: SkRect) {
    TODO("Implement mapRectToQuad")
  }

  public fun mapRectScaleTranslate(dst: SkRect?, src: SkRect) {
    TODO("Implement mapRectScaleTranslate")
  }

  public fun mapRadius(radius: SkScalar): Int {
    TODO("Implement mapRadius")
  }

  public fun dump() {
    TODO("Implement dump")
  }

  public fun getMinScale(): Int {
    TODO("Implement getMinScale")
  }

  public fun getMaxScale(): Int {
    TODO("Implement getMaxScale")
  }

  public fun getMinMaxScales(scaleFactors: Array<SkScalar>): Boolean {
    TODO("Implement getMinMaxScales")
  }

  public fun decomposeScale(scale: SkSize?, remaining: SkMatrix? = null): Boolean {
    TODO("Implement decomposeScale")
  }

  public fun dirtyMatrixTypeCache() {
    TODO("Implement dirtyMatrixTypeCache")
  }

  public fun setScaleTranslate(
    sx: SkScalar,
    sy: SkScalar,
    tx: SkScalar,
    ty: SkScalar,
  ) {
    TODO("Implement setScaleTranslate")
  }

  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  private fun computeTypeMask(): UByte {
    TODO("Implement computeTypeMask")
  }

  private fun computePerspectiveTypeMask(): UByte {
    TODO("Implement computePerspectiveTypeMask")
  }

  private fun setTypeMask(mask: Int) {
    TODO("Implement setTypeMask")
  }

  private fun orTypeMask(mask: Int) {
    TODO("Implement orTypeMask")
  }

  private fun clearTypeMask(mask: Int) {
    TODO("Implement clearTypeMask")
  }

  private fun getPerspectiveTypeMaskOnly(): TypeMask {
    TODO("Implement getPerspectiveTypeMaskOnly")
  }

  private fun isTriviallyIdentity(): Boolean {
    TODO("Implement isTriviallyIdentity")
  }

  private fun updateTranslateMask() {
    TODO("Implement updateTranslateMask")
  }

  private fun mapPointPerspective(pt: SkPoint): Int {
    TODO("Implement mapPointPerspective")
  }

  private fun getMapPtsProc(): SkMatrixMapPtsProc {
    TODO("Implement getMapPtsProc")
  }

  private fun writeToMemory(buffer: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  private fun readFromMemory(buffer: Unit?, length: ULong): ULong {
    TODO("Implement readFromMemory")
  }

  private fun postIDiv(divx: Int, divy: Int): Boolean {
    TODO("Implement postIDiv")
  }

  private fun doNormalizePerspective() {
    TODO("Implement doNormalizePerspective")
  }

  public enum class ScaleToFit {
    kFill_ScaleToFit,
    kStart_ScaleToFit,
    kCenter_ScaleToFit,
    kEnd_ScaleToFit,
  }

  public enum class TypeMask {
    kIdentity_Mask,
    kTranslate_Mask,
    kScale_Mask,
    kAffine_Mask,
    kPerspective_Mask,
  }

  public companion object {
    public val kMScaleX: Int = TODO("Initialize kMScaleX")

    public val kMSkewX: Int = TODO("Initialize kMSkewX")

    public val kMTransX: Int = TODO("Initialize kMTransX")

    public val kMSkewY: Int = TODO("Initialize kMSkewY")

    public val kMScaleY: Int = TODO("Initialize kMScaleY")

    public val kMTransY: Int = TODO("Initialize kMTransY")

    public val kMPersp0: Int = TODO("Initialize kMPersp0")

    public val kMPersp1: Int = TODO("Initialize kMPersp1")

    public val kMPersp2: Int = TODO("Initialize kMPersp2")

    public val kAScaleX: Int = TODO("Initialize kAScaleX")

    public val kASkewY: Int = TODO("Initialize kASkewY")

    public val kASkewX: Int = TODO("Initialize kASkewX")

    public val kAScaleY: Int = TODO("Initialize kAScaleY")

    public val kATransX: Int = TODO("Initialize kATransX")

    public val kATransY: Int = TODO("Initialize kATransY")

    private val kRectStaysRectMask: Int = TODO("Initialize kRectStaysRectMask")

    private val kOnlyPerspectiveValidMask: Int = TODO("Initialize kOnlyPerspectiveValidMask")

    private val kUnknownMask: Int = TODO("Initialize kUnknownMask")

    private val kORableMasks: Int = TODO("Initialize kORableMasks")

    private val kAllMasks: Int = TODO("Initialize kAllMasks")

    private val gMapPtsProcs: Array<SkMatrixMapPtsProc> = TODO("Initialize gMapPtsProcs")

    public fun scale(sx: SkScalar, sy: SkScalar): SkMatrix {
      TODO("Implement scale")
    }

    public fun translate(dx: SkScalar, dy: SkScalar): SkMatrix {
      TODO("Implement translate")
    }

    public fun translate(t: SkVector): SkMatrix {
      TODO("Implement translate")
    }

    public fun translate(t: SkIVector): SkMatrix {
      TODO("Implement translate")
    }

    public fun scaleTranslate(
      sx: Float,
      sy: Float,
      tx: Float,
      ty: Float,
    ): SkMatrix {
      TODO("Implement scaleTranslate")
    }

    public fun rotateDeg(deg: SkScalar): SkMatrix {
      TODO("Implement rotateDeg")
    }

    public fun rotateDeg(deg: SkScalar, pt: SkPoint): SkMatrix {
      TODO("Implement rotateDeg")
    }

    public fun rotateRad(rad: SkScalar): SkMatrix {
      TODO("Implement rotateRad")
    }

    public fun skew(kx: SkScalar, ky: SkScalar): SkMatrix {
      TODO("Implement skew")
    }

    public fun makeAll(
      scaleX: SkScalar,
      skewX: SkScalar,
      transX: SkScalar,
      skewY: SkScalar,
      scaleY: SkScalar,
      transY: SkScalar,
      pers0: SkScalar,
      pers1: SkScalar,
      pers2: SkScalar,
    ): SkMatrix {
      TODO("Implement makeAll")
    }

    public fun rect2Rect(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit = TODO(),
    ): SkMatrix? {
      TODO("Implement rect2Rect")
    }

    public fun rectToRectOrIdentity(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit = TODO(),
    ): SkMatrix {
      TODO("Implement rectToRectOrIdentity")
    }

    public fun makeRectToRect(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit,
    ): SkMatrix {
      TODO("Implement makeRectToRect")
    }

    public fun rectToRect(
      src: SkRect,
      dst: SkRect,
      mode: ScaleToFit = TODO(),
    ): SkMatrix {
      TODO("Implement rectToRect")
    }

    public fun polyToPoly(src: SkSpan<SkPoint>, dst: SkSpan<SkPoint>): SkMatrix? {
      TODO("Implement polyToPoly")
    }

    public fun setAffineIdentity(affine: Array<SkScalar>) {
      TODO("Implement setAffineIdentity")
    }

    public fun i(): SkMatrix {
      TODO("Implement i")
    }

    public fun invalidMatrix(): SkMatrix {
      TODO("Implement invalidMatrix")
    }

    public fun concat(a: SkMatrix, b: SkMatrix): SkMatrix {
      TODO("Implement concat")
    }

    private fun computeInv(
      dst: Array<SkScalar>,
      src: Array<SkScalar>,
      invDet: Double,
      isPersp: Boolean,
    ) {
      TODO("Implement computeInv")
    }

    private fun getMapPtsProc(mask: TypeMask): SkMatrixMapPtsProc {
      TODO("Implement getMapPtsProc")
    }

    private fun poly2Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly2Proc")
    }

    private fun poly3Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly3Proc")
    }

    private fun poly4Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly4Proc")
    }

    private fun identityPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement identityPts")
    }

    private fun transPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement transPts")
    }

    private fun scalePts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement scalePts")
    }

    private fun scaleTransPts(
      param0: SkMatrix,
      dst: Array<SkPoint>,
      param2: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement scaleTransPts")
    }

    private fun perspPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement perspPts")
    }

    private fun affineVpts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement affineVpts")
    }
  }
}
