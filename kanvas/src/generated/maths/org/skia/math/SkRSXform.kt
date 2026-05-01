package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Int

public data class SkRSXform public constructor(
  public var fSCos: Int,
  public var fSSin: Int,
  public var fTx: Int,
  public var fTy: Int,
) {
  public fun rectStaysRect(): Boolean {
    return fSCos == 0 || fSSin == 0
  }

  public fun setIdentity() {
    fSCos = 1
    fSSin = 0
    fTx = 0
    fTy = 0
  }

  public fun `set`(
    scos: SkScalar,
    ssin: SkScalar,
    tx: SkScalar,
    ty: SkScalar,
  ) {
    TODO("Implement set")
  }

  public fun toQuad(
    width: SkScalar,
    height: SkScalar,
    quad: Array<SkPoint>,
  ) {
    quad[0].set(fSCos * width + fTx, fSSin * height + fTy)
    quad[1].set(-fSSin * width + fTx, fSCos * height + fTy)
    quad[2].set(-fSCos * width + fTx, -fSSin * height + fTy)
    quad[3].set(fSSin * width + fTx, -fSCos * height + fTy)
  }

  public fun toQuad(size: SkSize, quad: Array<SkPoint>) {
    TODO("Implement toQuad")
  }

  public fun toTriStrip(
    width: SkScalar,
    height: SkScalar,
    strip: Array<SkPoint>,
  ) {
    strip[0] = SkPoint.Companion.make(0f, 0f)
    strip[1] = SkPoint.Companion.make(width, 0f)
    strip[2] = SkPoint.Companion.make(0f, height)
    strip[3] = SkPoint.Companion.make(width, height)
  }

  public companion object {
    public fun make(
      scos: SkScalar,
      ssin: SkScalar,
      tx: SkScalar,
      ty: SkScalar,
    ): SkRSXform {
      return Companion.make(scos, ssin, tx, ty)
    }

    public fun makeFromRadians(
      scale: SkScalar,
      radians: SkScalar,
      tx: SkScalar,
      ty: SkScalar,
      ax: SkScalar,
      ay: SkScalar,
    ): SkRSXform {
      return Companion.makeFromRadians(scale, radians, tx, ty, ax, ay)
    }
  }
}
