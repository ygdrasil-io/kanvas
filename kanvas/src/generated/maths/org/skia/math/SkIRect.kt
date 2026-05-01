package org.skia.math

import kotlin.Boolean
import kotlin.Int
import kotlin.Long

public open class SkIRect public constructor(
  public var fLeft: Int,
  public var fTop: Int,
  public var fRight: Int,
  public var fBottom: Int,
) {
  public fun left(): Int {
    TODO("Implement left")
  }

  public fun top(): Int {
    return fTop
  }

  public fun right(): Int {
    return fRight
  }

  public fun bottom(): Int {
    return bottom()
  }

  public fun x(): Int {
    return fLeft
  }

  public fun y(): Int {
    return this.fTop
  }

  public fun topLeft(): Int {
    TODO("Implement topLeft")
  }

  public fun width(): Int {
    return fRight - fLeft
  }

  public fun height(): Int {
    TODO("Implement height")
  }

  public fun size(): Int {
    TODO("Implement size")
  }

  public fun width64(): Long {
    TODO("Implement width64")
  }

  public fun height64(): Long {
    TODO("Implement height64")
  }

  public fun isEmpty64(): Boolean {
    TODO("Implement isEmpty64")
  }

  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  public fun setLTRB(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ) {
    TODO("Implement setLTRB")
  }

  public fun setXYWH(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    TODO("Implement setXYWH")
  }

  public fun setWH(width: Int, height: Int) {
    TODO("Implement setWH")
  }

  public fun setSize(size: SkISize) {
    TODO("Implement setSize")
  }

  public fun makeOffset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeOffset")
  }

  public fun makeOffset(offset: SkIVector): SkIRect {
    TODO("Implement makeOffset")
  }

  public fun makeInset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeInset")
  }

  public fun makeOutset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeOutset")
  }

  public fun offset(dx: Int, dy: Int) {
    TODO("Implement offset")
  }

  public fun offset(delta: SkIPoint) {
    TODO("Implement offset")
  }

  public fun offsetTo(newX: Int, newY: Int) {
    TODO("Implement offsetTo")
  }

  public fun inset(dx: Int, dy: Int) {
    TODO("Implement inset")
  }

  public fun outset(dx: Int, dy: Int) {
    TODO("Implement outset")
  }

  public fun adjust(
    dL: Int,
    dT: Int,
    dR: Int,
    dB: Int,
  ) {
    TODO("Implement adjust")
  }

  public fun contains(x: Int, y: Int): Boolean {
    TODO("Implement contains")
  }

  public fun contains(r: SkIRect): Boolean {
    TODO("Implement contains")
  }

  public fun contains(r: SkRect): Boolean {
    TODO("Implement contains")
  }

  public fun containsNoEmptyCheck(r: SkIRect): Boolean {
    TODO("Implement containsNoEmptyCheck")
  }

  public fun intersect(r: SkIRect): Boolean {
    TODO("Implement intersect")
  }

  public fun intersect(a: SkIRect, b: SkIRect): Boolean {
    TODO("Implement intersect")
  }

  public fun join(r: SkIRect) {
    TODO("Implement join")
  }

  public fun sort() {
    TODO("Implement sort")
  }

  public fun makeSorted(): SkIRect {
    TODO("Implement makeSorted")
  }

  public fun asInt32s(): Int {
    TODO("Implement asInt32s")
  }

  public companion object {
    public fun makeEmpty(): SkIRect {
      TODO("Implement makeEmpty")
    }

    public fun makeWH(w: Int, h: Int): SkIRect {
      TODO("Implement makeWH")
    }

    public fun makeSize(size: SkISize): SkIRect {
      TODO("Implement makeSize")
    }

    public fun makePtSize(pt: SkIPoint, size: SkISize): SkIRect {
      TODO("Implement makePtSize")
    }

    public fun makeLTRB(
      l: Int,
      t: Int,
      r: Int,
      b: Int,
    ): SkIRect {
      TODO("Implement makeLTRB")
    }

    public fun makeXYWH(
      x: Int,
      y: Int,
      w: Int,
      h: Int,
    ): SkIRect {
      TODO("Implement makeXYWH")
    }

    public fun intersects(a: SkIRect, b: SkIRect): Boolean {
      TODO("Implement intersects")
    }
  }
}
