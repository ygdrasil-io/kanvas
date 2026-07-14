# Math Modular Refactoring — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create 5 new Kotlin Multiplatform sub-modules (`:math:scalar`, `:math:vector`, `:math:matrix`, `:math:geometry`, `:math:color`) with idiomatic Kotlin naming, ported from the existing `:math` module. Old `:math` remains intact.

**Architecture:** Each module lives under `math/<name>/` with its own `build.gradle.kts`, package under `org.graphiks.math.<name>`, and mirrors the KMP source layout of `:math`. All modules use the `buildsrc.convention.kotlin-multiplatform` convention plugin. Dependencies flow: scalar → vector → matrix, scalar+vector → geometry, scalar+matrix → color.

**Tech Stack:** Kotlin Multiplatform, JUnit 5, kotlinx-benchmark (deferred — benchmarks stay on `:math` for now)

---

## File Structure

```
math/
├── build.gradle.kts          ← existing :math (unchanged)
├── module.md                  ← existing (unchanged)
├── src/                       ← existing :math sources (unchanged)
├── scalar/
│   ├── build.gradle.kts       ← new
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── ScalarF32.kt   ← port from SkScalar.kt
│       └── commonTest/kotlin/
│           └── ScalarF32Test.kt
├── vector/
│   ├── build.gradle.kts       ← new
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── Vector2F32.kt       ← port from SkV2.kt + SkPoint.kt
│       │   ├── Vector3F32.kt       ← port from SkV3.kt + SkPoint3.kt
│       │   ├── Vector4F32.kt       ← port from SkV4.kt
│       │   ├── MutableVector2F32.kt
│       │   └── MutableVector3F32.kt
│       └── commonTest/kotlin/
│           ├── Vector2F32Test.kt
│           ├── Vector3F32Test.kt
│           ├── Vector4F32Test.kt
│           ├── MutableVector2F32Test.kt
│           └── MutableVector3F32Test.kt
├── matrix/
│   ├── build.gradle.kts       ← new
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── Matrix3x3F32.kt  ← port from SkMatrix.kt
│       │   └── Matrix4x4F32.kt  ← port from SkM44.kt
│       └── commonTest/kotlin/
│           ├── Matrix3x3F32Test.kt
│           └── Matrix4x4F32Test.kt
├── geometry/
│   ├── build.gradle.kts       ← new
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── Vector2I32.kt      ← port from SkIPoint.kt (+ typealias Point2I32)
│       │   ├── RectF32.kt         ← port from SkRect.kt
│       │   ├── RectI32.kt         ← port from SkRect.kt (IRect portion)
│       │   ├── SizeF32.kt         ← port from SkISize.kt
│       │   ├── SizeI32.kt         ← port from SkISize.kt
│       │   ├── Point2F64.kt       ← port from SkDPoint.kt
│       │   └── PathOpsEpsilon.kt  ← port from SkPathOpsTypes.kt
│       └── commonTest/kotlin/
│           ├── Vector2I32Test.kt
│           ├── RectF32Test.kt
│           ├── RectI32Test.kt
│           ├── SizeF32Test.kt
│           ├── SizeI32Test.kt
│           ├── Point2F64Test.kt
│           └── PathOpsEpsilonTest.kt
└── color/
    ├── build.gradle.kts       ← new
    └── src/
        ├── commonMain/kotlin/
        │   ├── ColorARGB.kt         ← port from SkColor.kt
        │   ├── ColorF32.kt          ← port from SkColor4f.kt
        │   ├── ColorMatrixF32.kt    ← port from SkColorMatrix.kt
        │   ├── TransferFunction.kt  ← port from SkcmsTransferFunction.kt
        │   └── IccExtensions.kt     ← port from SkcmsMatrix3x3/3x4 (extensions on Matrix3x3F32)
        ├── commonTest/kotlin/
        │   ├── ColorARGBTest.kt
        │   ├── ColorF32Test.kt
        │   ├── ColorMatrixF32Test.kt
        │   └── TransferFunctionTest.kt
        └── jvmMain/kotlin/
            └── HalfFloat.kt         ← port from HalfFloat.kt (jvm)
```

---

### Task 1: `:math:scalar` — Scalar primitives

**Files:**
- Create: `math/scalar/build.gradle.kts`
- Create: `math/scalar/src/commonMain/kotlin/org/graphiks/math/scalar/ScalarF32.kt`
- Create: `math/scalar/src/commonTest/kotlin/org/graphiks/math/scalar/ScalarF32Test.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `math/scalar/build.gradle.kts`** — minimal KMP module with no dependencies

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Add `include(":math:scalar")` to `settings.gradle.kts`**

In `settings.gradle.kts`, add after line 66 (`include(":math")`):

```kotlin
include(":math:scalar")
```

- [ ] **Step 3: Create `ScalarF32.kt`** — value class wrapping `Float` with all constants/functions from `SkScalar.kt`

```kotlin
package org.graphiks.math.scalar

import kotlin.math.*

@JvmInline
public value class ScalarF32(public val value: Float) {

    public companion object {
        public val Zero: ScalarF32 = ScalarF32(0f)
        public val One: ScalarF32 = ScalarF32(1f)
        public val Half: ScalarF32 = ScalarF32(0.5f)
        public val Pi: ScalarF32 = ScalarF32(PI.toFloat())
        public val TwoPi: ScalarF32 = ScalarF32((2.0 * PI).toFloat())
        public val PiOver2: ScalarF32 = ScalarF32((PI / 2.0).toFloat())
        public val Sqrt2: ScalarF32 = ScalarF32(SQRT2.toFloat())
        public val Max: ScalarF32 = ScalarF32(Float.MAX_VALUE)
        public val Infinity: ScalarF32 = ScalarF32(Float.POSITIVE_INFINITY)
        public val NegativeInfinity: ScalarF32 = ScalarF32(Float.NEGATIVE_INFINITY)
        public val NaN: ScalarF32 = ScalarF32(Float.NaN)

        public fun nearlyZero(value: Float, tolerance: Float = 1e-7f): Boolean =
            abs(value) <= tolerance

        public fun nearlyEqual(a: Float, b: Float, tolerance: Float = 1e-7f): Boolean =
            abs(a - b) <= tolerance

        public fun clamp(value: Float, min: Float, max: Float): Float =
            value.coerceIn(min, max)

        public fun interp(a: Float, b: Float, t: Float): Float =
            a + (b - a) * t

        public fun sign(x: Float): Float =
            if (x > 0f) 1f else if (x < 0f) -1f else 0f

        public fun sin(radians: Float): Float {
            val r = sin(radians.toDouble()).toFloat()
            return if (nearlyZero(r)) 0f else r
        }

        public fun cos(radians: Float): Float {
            val r = cos(radians.toDouble()).toFloat()
            return if (nearlyZero(r)) 0f else r
        }

        public fun tan(radians: Float): Float {
            val r = tan(radians.toDouble()).toFloat()
            return if (nearlyZero(r)) 0f else r
        }

        public fun saturatingAdd32(a: Int, b: Int): Int {
            val sum = a.toLong() + b.toLong()
            return sum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }

        public fun saturatingSub32(a: Int, b: Int): Int {
            val diff = a.toLong() - b.toLong()
            return diff.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }

        public fun floorToInt(value: Float): Int = floor(value).toInt()
        public fun ceilToInt(value: Float): Int = ceil(value).toInt()
        public fun roundToInt(value: Float): Int = round(value).toInt()

        public fun isFinite(value: Float): Boolean = value.isFinite()
        public fun isInteger(value: Float): Boolean = value == floor(value)
        public fun isNaN(value: Float): Boolean = value.isNaN()

        public fun lerp(a: Float, b: Float, t: Float): Float = interp(a, b, t)
    }
}
```

- [ ] **Step 4: Create `ScalarF32Test.kt`**

```kotlin
package org.graphiks.math.scalar

import kotlin.test.*

class ScalarF32Test {

    @Test
    fun testConstants() {
        assertEquals(0f, ScalarF32.Zero.value)
        assertEquals(1f, ScalarF32.One.value)
        assertEquals(0.5f, ScalarF32.Half.value)
    }

    @Test
    fun testNearlyZero() {
        assertTrue(ScalarF32.nearlyZero(0f))
        assertTrue(ScalarF32.nearlyZero(1e-8f))
        assertFalse(ScalarF32.nearlyZero(0.001f))
    }

    @Test
    fun testNearlyEqual() {
        assertTrue(ScalarF32.nearlyEqual(1f, 1.00000001f))
        assertFalse(ScalarF32.nearlyEqual(1f, 2f))
    }

    @Test
    fun testClamp() {
        assertEquals(5f, ScalarF32.clamp(5f, 0f, 10f))
        assertEquals(0f, ScalarF32.clamp(-5f, 0f, 10f))
        assertEquals(10f, ScalarF32.clamp(15f, 0f, 10f))
    }

    @Test
    fun testInterp() {
        assertEquals(0f, ScalarF32.interp(0f, 10f, 0f))
        assertEquals(10f, ScalarF32.interp(0f, 10f, 1f))
        assertEquals(5f, ScalarF32.interp(0f, 10f, 0.5f))
    }

    @Test
    fun testSign() {
        assertEquals(1f, ScalarF32.sign(5f))
        assertEquals(-1f, ScalarF32.sign(-5f))
        assertEquals(0f, ScalarF32.sign(0f))
    }

    @Test
    fun testSin() {
        assertEquals(0f, ScalarF32.sin(0f))
        assertEquals(1f, ScalarF32.sin(PI.toFloat() / 2f))
        assertEquals(0f, ScalarF32.sin(PI.toFloat()))
    }

    @Test
    fun testCos() {
        assertEquals(1f, ScalarF32.cos(0f))
        assertEquals(0f, ScalarF32.cos(PI.toFloat() / 2f))
        assertEquals(-1f, ScalarF32.cos(PI.toFloat()))
    }

    @Test
    fun testTan() {
        assertEquals(0f, ScalarF32.tan(0f))
        assertEquals(0f, ScalarF32.tan(PI.toFloat()))
    }

    @Test
    fun testSaturatingAdd32() {
        assertEquals(10, ScalarF32.saturatingAdd32(7, 3))
        assertEquals(Int.MAX_VALUE, ScalarF32.saturatingAdd32(Int.MAX_VALUE, 1))
        assertEquals(Int.MIN_VALUE, ScalarF32.saturatingAdd32(Int.MIN_VALUE, -1))
    }

    @Test
    fun testSaturatingSub32() {
        assertEquals(4, ScalarF32.saturatingSub32(7, 3))
        assertEquals(Int.MIN_VALUE, ScalarF32.saturatingSub32(Int.MIN_VALUE, 1))
        assertEquals(Int.MAX_VALUE, ScalarF32.saturatingSub32(Int.MAX_VALUE, -1))
    }

    @Test
    fun testFloorToInt() {
        assertEquals(3, ScalarF32.floorToInt(3.7f))
        assertEquals(-4, ScalarF32.floorToInt(-3.2f))
    }

    @Test
    fun testCeilToInt() {
        assertEquals(4, ScalarF32.ceilToInt(3.2f))
        assertEquals(-3, ScalarF32.ceilToInt(-3.7f))
    }

    @Test
    fun testRoundToInt() {
        assertEquals(4, ScalarF32.roundToInt(3.7f))
        assertEquals(3, ScalarF32.roundToInt(3.2f))
    }

    @Test
    fun testIsFinite() {
        assertTrue(ScalarF32.isFinite(1f))
        assertFalse(ScalarF32.isFinite(Float.POSITIVE_INFINITY))
        assertFalse(ScalarF32.isFinite(Float.NaN))
    }

    @Test
    fun testIsInteger() {
        assertTrue(ScalarF32.isInteger(5f))
        assertFalse(ScalarF32.isInteger(5.3f))
    }
}
```

- [ ] **Step 5: Compile `:math:scalar`**

Run: `./gradlew :math:scalar:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run `:math:scalar` tests**

Run: `./gradlew :math:scalar:jvmTest`
Expected: all tests pass, BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts math/scalar/
git commit -m "feat: add :math:scalar module with ScalarF32 value class"
```

---

### Task 2: `:math:vector` — Float vectors (immutable + mutable)

**Files:**
- Create: `math/vector/build.gradle.kts`
- Create: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector2F32.kt`
- Create: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector3F32.kt`
- Create: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector4F32.kt`
- Create: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/MutableVector2F32.kt`
- Create: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/MutableVector3F32.kt`
- Create: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/Vector2F32Test.kt`
- Create: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/Vector3F32Test.kt`
- Create: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/Vector4F32Test.kt`
- Create: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/MutableVector2F32Test.kt`
- Create: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/MutableVector3F32Test.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `math/vector/build.gradle.kts`**

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":math:scalar"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Add `include(":math:vector")` to `settings.gradle.kts`**

After the `include(":math:scalar")` line added in Task 1:

```kotlin
include(":math:vector")
```

- [ ] **Step 3: Create `Vector2F32.kt`** — immutable 2D vector, merges `SkV2` + `SkPoint` API

```kotlin
package org.graphiks.math.vector

import org.graphiks.math.scalar.ScalarF32

public data class Vector2F32(public val x: Float, public val y: Float) {

    public operator fun unaryMinus(): Vector2F32 = Vector2F32(-x, -y)

    public operator fun plus(v: Vector2F32): Vector2F32 = Vector2F32(x + v.x, y + v.y)
    public operator fun minus(v: Vector2F32): Vector2F32 = Vector2F32(x - v.x, y - v.y)

    public operator fun times(v: Vector2F32): Vector2F32 = Vector2F32(x * v.x, y * v.y)
    public operator fun times(s: Float): Vector2F32 = Vector2F32(x * s, y * s)
    public operator fun div(s: Float): Vector2F32 = Vector2F32(x / s, y / s)

    public fun lengthSquared(): Float = x * x + y * y
    public fun length(): Float = Companion.length(x, y)
    public fun dot(v: Vector2F32): Float = x * v.x + y * v.y
    public fun cross(v: Vector2F32): Float = x * v.y - y * v.x
    public fun normalize(): Vector2F32 {
        val len = length()
        return if (ScalarF32.nearlyZero(len)) Vector2F32(0f, 0f) else this / len
    }
    public fun distanceTo(other: Vector2F32): Float = (this - other).length()
    public fun isFinite(): Boolean = ScalarF32.isFinite(x) && ScalarF32.isFinite(y)
    public fun isZero(): Boolean = ScalarF32.nearlyZero(x) && ScalarF32.nearlyZero(y)

    public companion object {
        public val Zero: Vector2F32 = Vector2F32(0f, 0f)
        public val One: Vector2F32 = Vector2F32(1f, 1f)
        public val UnitX: Vector2F32 = Vector2F32(1f, 0f)
        public val UnitY: Vector2F32 = Vector2F32(0f, 1f)

        public fun dot(a: Vector2F32, b: Vector2F32): Float = a.x * b.x + a.y * b.y
        public fun cross(a: Vector2F32, b: Vector2F32): Float = a.x * b.y - a.y * b.x
        public fun distance(a: Vector2F32, b: Vector2F32): Float = (a - b).length()
        public fun length(x: Float, y: Float): Float {
            val mag2 = x * x + y * y
            if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
            val xx = x.toDouble()
            val yy = y.toDouble()
            return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
        }
    }
}

public operator fun Float.times(v: Vector2F32): Vector2F32 = v * this
```

- [ ] **Step 4: Create `Vector3F32.kt`** — immutable 3D vector, merges `SkV3` + `SkPoint3` API

```kotlin
package org.graphiks.math.vector

import org.graphiks.math.scalar.ScalarF32

public data class Vector3F32(public val x: Float, public val y: Float, public val z: Float) {

    public operator fun unaryMinus(): Vector3F32 = Vector3F32(-x, -y, -z)

    public operator fun plus(v: Vector3F32): Vector3F32 = Vector3F32(x + v.x, y + v.y, z + v.z)
    public operator fun minus(v: Vector3F32): Vector3F32 = Vector3F32(x - v.x, y - v.y, z - v.z)

    public operator fun times(v: Vector3F32): Vector3F32 = Vector3F32(x * v.x, y * v.y, z * v.z)
    public operator fun times(s: Float): Vector3F32 = Vector3F32(x * s, y * s, z * s)
    public operator fun div(s: Float): Vector3F32 = Vector3F32(x / s, y / s, z / s)

    public fun lengthSquared(): Float = x * x + y * y + z * z
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())
    public fun dot(v: Vector3F32): Float = x * v.x + y * v.y + z * v.z
    public fun cross(v: Vector3F32): Vector3F32 = Vector3F32(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )
    public fun normalize(): Vector3F32 {
        val len = length()
        return if (ScalarF32.nearlyZero(len)) Vector3F32(0f, 0f, 0f) else this / len
    }
    public fun isFinite(): Boolean = ScalarF32.isFinite(x) && ScalarF32.isFinite(y) && ScalarF32.isFinite(z)

    public companion object {
        public val Zero: Vector3F32 = Vector3F32(0f, 0f, 0f)

        public fun dot(a: Vector3F32, b: Vector3F32): Float = a.x * b.x + a.y * b.y + a.z * b.z
        public fun cross(a: Vector3F32, b: Vector3F32): Vector3F32 = a.cross(b)
    }
}

public operator fun Float.times(v: Vector3F32): Vector3F32 = v * this
```

- [ ] **Step 5: Create `Vector4F32.kt`** — immutable 4D vector from `SkV4`

```kotlin
package org.graphiks.math.vector

import org.graphiks.math.scalar.ScalarF32

public data class Vector4F32(
    public val x: Float,
    public val y: Float,
    public val z: Float,
    public val w: Float
) {
    public operator fun unaryMinus(): Vector4F32 = Vector4F32(-x, -y, -z, -w)

    public operator fun plus(v: Vector4F32): Vector4F32 = Vector4F32(x + v.x, y + v.y, z + v.z, w + v.w)
    public operator fun minus(v: Vector4F32): Vector4F32 = Vector4F32(x - v.x, y - v.y, z - v.z, w - v.w)

    public operator fun times(v: Vector4F32): Vector4F32 = Vector4F32(x * v.x, y * v.y, z * v.z, w * v.w)
    public operator fun times(s: Float): Vector4F32 = Vector4F32(x * s, y * s, z * s, w * s)
    public operator fun div(s: Float): Vector4F32 = Vector4F32(x / s, y / s, z / s, w / s)

    public operator fun get(i: Int): Float = when (i) {
        0 -> x; 1 -> y; 2 -> z; 3 -> w
        else -> throw IndexOutOfBoundsException("index $i")
    }

    public fun lengthSquared(): Float = x * x + y * y + z * z + w * w
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())
    public fun dot(v: Vector4F32): Float = x * v.x + y * v.y + z * v.z + w * v.w
    public fun normalize(): Vector4F32 {
        val len = length()
        return if (ScalarF32.nearlyZero(len)) Vector4F32(0f, 0f, 0f, 0f) else this / len
    }

    public companion object {
        public fun dot(a: Vector4F32, b: Vector4F32): Float = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
    }
}

public operator fun Float.times(v: Vector4F32): Vector4F32 = v * this
```

- [ ] **Step 6: Create `MutableVector2F32.kt`** — inline class wrapping `FloatArray(2)` for in-place mutation

```kotlin
package org.graphiks.math.vector

import org.graphiks.math.scalar.ScalarF32

@JvmInline
public value class MutableVector2F32(private val data: FloatArray) {

    public var x: Float
        get() = data[0]
        set(value) { data[0] = value }

    public var y: Float
        get() = data[1]
        set(value) { data[1] = value }

    public fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    public fun offset(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    public fun scale(s: Float) {
        x *= s
        y *= s
    }

    public fun negate() {
        x = -x
        y = -y
    }

    public fun length(): Float {
        val mag2 = x * x + y * y
        if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
        val xx = x.toDouble()
        val yy = y.toDouble()
        return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
    }

    public fun normalize(): Boolean {
        val xx = x.toDouble()
        val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = 1.0 / dmag
        val nx = (xx * dscale).toFloat()
        val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) {
            set(0f, 0f); return false
        }
        set(nx, ny); return true
    }

    public fun setLength(length: Float): Boolean {
        val xx = x.toDouble()
        val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = length.toDouble() / dmag
        val nx = (xx * dscale).toFloat()
        val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) {
            set(0f, 0f); return false
        }
        set(nx, ny); return true
    }

    public fun toVector(): Vector2F32 = Vector2F32(x, y)
    public fun isZero(): Boolean = ScalarF32.nearlyZero(x) && ScalarF32.nearlyZero(y)

    public companion object {
        public fun of(x: Float = 0f, y: Float = 0f): MutableVector2F32 =
            MutableVector2F32(floatArrayOf(x, y))

        public fun from(v: Vector2F32): MutableVector2F32 =
            MutableVector2F32(floatArrayOf(v.x, v.y))
    }
}
```

- [ ] **Step 7: Create `MutableVector3F32.kt`** — inline class wrapping `FloatArray(3)` for in-place mutation

```kotlin
package org.graphiks.math.vector

import org.graphiks.math.scalar.ScalarF32

@JvmInline
public value class MutableVector3F32(private val data: FloatArray) {

    public var x: Float
        get() = data[0]
        set(value) { data[0] = value }

    public var y: Float
        get() = data[1]
        set(value) { data[1] = value }

    public var z: Float
        get() = data[2]
        set(value) { data[2] = value }

    public fun set(x: Float, y: Float, z: Float) {
        this.x = x; this.y = y; this.z = z
    }

    public fun offset(dx: Float, dy: Float, dz: Float) {
        x += dx; y += dy; z += dz
    }

    public fun scale(s: Float) {
        x *= s; y *= s; z *= s
    }

    public fun negate() {
        x = -x; y = -y; z = -z
    }

    public fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    public fun normalize(): Boolean {
        val len = length()
        return if (ScalarF32.nearlyZero(len)) {
            set(0f, 0f, 0f); false
        } else {
            scale(1f / len); true
        }
    }

    public fun toVector(): Vector3F32 = Vector3F32(x, y, z)

    public companion object {
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): MutableVector3F32 =
            MutableVector3F32(floatArrayOf(x, y, z))

        public fun from(v: Vector3F32): MutableVector3F32 =
            MutableVector3F32(floatArrayOf(v.x, v.y, v.z))
    }
}
```

- [ ] **Step 8: Create `Vector2F32Test.kt`**

```kotlin
package org.graphiks.math.vector

import kotlin.test.*

class Vector2F32Test {

    @Test
    fun testConstants() {
        assertEquals(0f, Vector2F32.Zero.x)
        assertEquals(0f, Vector2F32.Zero.y)
        assertEquals(1f, Vector2F32.UnitX.x)
        assertEquals(0f, Vector2F32.UnitX.y)
    }

    @Test
    fun testPlus() {
        val a = Vector2F32(1f, 2f)
        val b = Vector2F32(3f, 4f)
        assertEquals(Vector2F32(4f, 6f), a + b)
    }

    @Test
    fun testMinus() {
        val a = Vector2F32(5f, 7f)
        val b = Vector2F32(2f, 3f)
        assertEquals(Vector2F32(3f, 4f), a - b)
    }

    @Test
    fun testTimesScalar() {
        val a = Vector2F32(1f, 2f)
        assertEquals(Vector2F32(3f, 6f), a * 3f)
        assertEquals(Vector2F32(3f, 6f), 3f * a)
    }

    @Test
    fun testTimesComponentWise() {
        val a = Vector2F32(2f, 3f)
        val b = Vector2F32(4f, 5f)
        assertEquals(Vector2F32(8f, 15f), a * b)
    }

    @Test
    fun testDiv() {
        assertEquals(Vector2F32(2f, 3f), Vector2F32(4f, 6f) / 2f)
    }

    @Test
    fun testUnaryMinus() {
        assertEquals(Vector2F32(-1f, 2f), -Vector2F32(1f, -2f))
    }

    @Test
    fun testLength() {
        assertEquals(5f, Vector2F32(3f, 4f).length())
        assertEquals(0f, Vector2F32(0f, 0f).length())
    }

    @Test
    fun testLengthSquared() {
        assertEquals(25f, Vector2F32(3f, 4f).lengthSquared())
    }

    @Test
    fun testDot() {
        assertEquals(11f, Vector2F32(1f, 2f).dot(Vector2F32(3f, 4f)))
        assertEquals(11f, Vector2F32.dot(Vector2F32(1f, 2f), Vector2F32(3f, 4f)))
    }

    @Test
    fun testCross() {
        assertEquals(-2f, Vector2F32(1f, 2f).cross(Vector2F32(3f, 4f)))
    }

    @Test
    fun testNormalize() {
        val v = Vector2F32(3f, 4f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeZero() {
        val v = Vector2F32(0f, 0f).normalize()
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testDistance() {
        assertEquals(5f, Vector2F32(0f, 0f).distanceTo(Vector2F32(3f, 4f)))
    }

    @Test
    fun testIsFinite() {
        assertTrue(Vector2F32(1f, 2f).isFinite())
        assertFalse(Vector2F32(Float.NaN, 2f).isFinite())
    }

    @Test
    fun testIsZero() {
        assertTrue(Vector2F32(0f, 0f).isZero())
        assertFalse(Vector2F32(1f, 0f).isZero())
    }

    @Test
    fun testLengthOverflowFallback() {
        val x = Float.MAX_VALUE
        val y = Float.MAX_VALUE
        val v = Vector2F32(x, y)
        assertTrue(v.length().isFinite())
    }
}
```

- [ ] **Step 9: Create `Vector3F32Test.kt`**

```kotlin
package org.graphiks.math.vector

import kotlin.test.*

class Vector3F32Test {

    @Test
    fun testConstruct() {
        val v = Vector3F32(1f, 2f, 3f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
    }

    @Test
    fun testPlus() {
        assertEquals(Vector3F32(5f, 7f, 9f), Vector3F32(1f, 2f, 3f) + Vector3F32(4f, 5f, 6f))
    }

    @Test
    fun testMinus() {
        assertEquals(Vector3F32(3f, 3f, 3f), Vector3F32(4f, 5f, 6f) - Vector3F32(1f, 2f, 3f))
    }

    @Test
    fun testUnaryMinus() {
        assertEquals(Vector3F32(-1f, 2f, -3f), -Vector3F32(1f, -2f, 3f))
    }

    @Test
    fun testTimesScalar() {
        assertEquals(Vector3F32(2f, 4f, 6f), Vector3F32(1f, 2f, 3f) * 2f)
        assertEquals(Vector3F32(2f, 4f, 6f), 2f * Vector3F32(1f, 2f, 3f))
    }

    @Test
    fun testTimesComponentWise() {
        assertEquals(Vector3F32(2f, 6f, 12f), Vector3F32(1f, 2f, 3f) * Vector3F32(2f, 3f, 4f))
    }

    @Test
    fun testDiv() {
        assertEquals(Vector3F32(1f, 2f, 3f), Vector3F32(2f, 4f, 6f) / 2f)
    }

    @Test
    fun testLength() {
        val v = Vector3F32(1f, 2f, 2f)
        assertEquals(3f, v.length())
    }

    @Test
    fun testDot() {
        assertEquals(32f, Vector3F32(1f, 2f, 3f).dot(Vector3F32(4f, 5f, 6f)))
    }

    @Test
    fun testCross() {
        val a = Vector3F32(1f, 0f, 0f)
        val b = Vector3F32(0f, 1f, 0f)
        assertEquals(Vector3F32(0f, 0f, 1f), a.cross(b))
    }

    @Test
    fun testNormalize() {
        val v = Vector3F32(3f, 0f, 0f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
        assertEquals(1f, v.x)
    }

    @Test
    fun testIsFinite() {
        assertTrue(Vector3F32(1f, 2f, 3f).isFinite())
        assertFalse(Vector3F32(Float.NaN, 2f, 3f).isFinite())
    }
}
```

- [ ] **Step 10: Create `Vector4F32Test.kt`**

```kotlin
package org.graphiks.math.vector

import kotlin.test.*

class Vector4F32Test {

    @Test
    fun testConstruct() {
        val v = Vector4F32(1f, 2f, 3f, 4f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
        assertEquals(4f, v.w)
    }

    @Test
    fun testIndexedGet() {
        val v = Vector4F32(1f, 2f, 3f, 4f)
        assertEquals(1f, v[0])
        assertEquals(2f, v[1])
        assertEquals(3f, v[2])
        assertEquals(4f, v[3])
    }

    @Test
    fun testIndexedGetOutOfBounds() {
        assertFailsWith<IndexOutOfBoundsException> { Vector4F32(1f, 2f, 3f, 4f)[4] }
    }

    @Test
    fun testPlus() {
        val a = Vector4F32(1f, 2f, 3f, 4f)
        val b = Vector4F32(5f, 6f, 7f, 8f)
        assertEquals(Vector4F32(6f, 8f, 10f, 12f), a + b)
    }

    @Test
    fun testTimesScalar() {
        assertEquals(Vector4F32(2f, 4f, 6f, 8f), Vector4F32(1f, 2f, 3f, 4f) * 2f)
    }

    @Test
    fun testTimesComponentWise() {
        val a = Vector4F32(1f, 2f, 3f, 4f)
        val b = Vector4F32(2f, 3f, 4f, 5f)
        assertEquals(Vector4F32(2f, 6f, 12f, 20f), a * b)
    }

    @Test
    fun testLength() {
        val v = Vector4F32(1f, 2f, 2f, 4f)
        assertEquals(5f, v.length())
    }

    @Test
    fun testDot() {
        assertEquals(70f, Vector4F32(1f, 2f, 3f, 4f).dot(Vector4F32(5f, 6f, 7f, 8f)))
    }

    @Test
    fun testNormalize() {
        val v = Vector4F32(3f, 4f, 0f, 0f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }
}
```

- [ ] **Step 11: Create `MutableVector2F32Test.kt`**

```kotlin
package org.graphiks.math.vector

import kotlin.test.*

class MutableVector2F32Test {

    @Test
    fun testOf() {
        val v = MutableVector2F32.of(1f, 2f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testDefault() {
        val v = MutableVector2F32.of()
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.set(3f, 4f)
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
    }

    @Test
    fun testOffset() {
        val v = MutableVector2F32.of(1f, 2f)
        v.offset(3f, 4f)
        assertEquals(4f, v.x)
        assertEquals(6f, v.y)
    }

    @Test
    fun testScale() {
        val v = MutableVector2F32.of(3f, 4f)
        v.scale(2f)
        assertEquals(6f, v.x)
        assertEquals(8f, v.y)
    }

    @Test
    fun testNegate() {
        val v = MutableVector2F32.of(1f, -2f)
        v.negate()
        assertEquals(-1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testLength() {
        assertEquals(5f, MutableVector2F32.of(3f, 4f).length())
    }

    @Test
    fun testNormalize() {
        val v = MutableVector2F32.of(3f, 4f)
        assertTrue(v.normalize())
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeZero() {
        val v = MutableVector2F32.of(0f, 0f)
        assertFalse(v.normalize())
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testSetLength() {
        val v = MutableVector2F32.of(1f, 0f)
        assertTrue(v.setLength(2f))
        assertEquals(2f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testToVector() {
        val v = MutableVector2F32.of(1f, 2f).toVector()
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testFrom() {
        val v = MutableVector2F32.from(Vector2F32(3f, 4f))
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
    }

    @Test
    fun testXSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.x = 10f
        assertEquals(10f, v.x)
    }

    @Test
    fun testYSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.y = 20f
        assertEquals(20f, v.y)
    }
}
```

- [ ] **Step 12: Create `MutableVector3F32Test.kt`**

```kotlin
package org.graphiks.math.vector

import kotlin.test.*

class MutableVector3F32Test {

    @Test
    fun testOf() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
    }

    @Test
    fun testSet() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.set(4f, 5f, 6f)
        assertEquals(4f, v.x)
        assertEquals(5f, v.y)
        assertEquals(6f, v.z)
    }

    @Test
    fun testOffset() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.offset(1f, 1f, 1f)
        assertEquals(2f, v.x)
        assertEquals(3f, v.y)
        assertEquals(4f, v.z)
    }

    @Test
    fun testScale() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.scale(2f)
        assertEquals(2f, v.x)
        assertEquals(4f, v.y)
        assertEquals(6f, v.z)
    }

    @Test
    fun testNegate() {
        val v = MutableVector3F32.of(1f, -2f, 3f)
        v.negate()
        assertEquals(-1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(-3f, v.z)
    }

    @Test
    fun testLength() {
        val v = MutableVector3F32.of(1f, 2f, 2f)
        assertEquals(3f, v.length())
    }

    @Test
    fun testNormalize() {
        val v = MutableVector3F32.of(3f, 0f, 0f)
        assertTrue(v.normalize())
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeZero() {
        val v = MutableVector3F32.of(0f, 0f, 0f)
        assertFalse(v.normalize())
        assertEquals(0f, v.x)
    }

    @Test
    fun testToVector() {
        val v = MutableVector3F32.of(1f, 2f, 3f).toVector()
        assertEquals(1f, v.x); assertEquals(2f, v.y); assertEquals(3f, v.z)
    }

    @Test
    fun testFrom() {
        val v = MutableVector3F32.from(Vector3F32(4f, 5f, 6f))
        assertEquals(4f, v.x); assertEquals(5f, v.y); assertEquals(6f, v.z)
    }
}
```

- [ ] **Step 13: Compile `:math:vector`**

Run: `./gradlew :math:vector:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: Run `:math:vector` tests**

Run: `./gradlew :math:vector:jvmTest`
Expected: all tests pass, BUILD SUCCESSFUL

- [ ] **Step 15: Commit**

```bash
git add settings.gradle.kts math/vector/
git commit -m "feat: add :math:vector module with Vector2F32/V3F32/V4F32 + mutable variants"
```

---

### Task 3: `:math:matrix` — Transformation matrices

**Files:**
- Create: `math/matrix/build.gradle.kts`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F32.kt` (port from `SkMatrix.kt`)
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix4x4F32.kt` (port from `SkM44.kt`)
- Create: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F32Test.kt`
- Create: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix4x4F32Test.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `math/matrix/build.gradle.kts`**

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":math:scalar"))
                api(project(":math:vector"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Add `include(":math:matrix")` to `settings.gradle.kts`**

After the `include(":math:vector")` line:

```kotlin
include(":math:matrix")
```

- [ ] **Step 3: Create `Matrix3x3F32.kt`** — port of `SkMatrix.kt` (3x3 row-major 2D transform)

Port the full `SkMatrix.kt` content (1279 lines) with these renames:
- `SkMatrix` → `Matrix3x3F32`
- `SkScalar` → `ScalarF32` (use `Float` directly or `ScalarF32` value class)
- `SkPoint` → `Vector2F32`
- `SkRect` → `RectF32` (use as local type or port geometry types here)
- All `SkScalar*` functions (e.g. `SkScalarNearlyZero`) → `ScalarF32.nearlyZero()`
- Package: `org.graphiks.math.matrix`

Since this file is large, the port is a mechanical rename operation. Key public API surface:

```kotlin
package org.graphiks.math.matrix

import org.graphiks.math.scalar.ScalarF32
import org.graphiks.math.vector.Vector2F32

public data class Matrix3x3F32(
    public var sx: Float, public var kx: Float, public var tx: Float,
    public var ky: Float, public var sy: Float, public var ty: Float,
    public var persp0: Float, public var persp1: Float, public var persp2: Float
) {
    // ... complete port of SkMatrix.kt body with renames ...
}
```

**Migration mapping:**
| `:math` | `:math:matrix` |
|---|---|
| `SkMatrix` | `Matrix3x3F32` |
| `SkScalarNearlyZero(x)` | `ScalarF32.nearlyZero(x)` |
| `SkScalarSqrt(x)` | `kotlin.math.sqrt(x)` |
| `SkScalarSinCos(x, ..)` | `ScalarF32.sin(x)` / `ScalarF32.cos(x)` |
| `SkPoint` | `Vector2F32` |
| `SkVector` | `Vector2F32` |
| `SkScalarInterp` | `ScalarF32.interp` |
| `SkScalar` | `Float` |

- [ ] **Step 4: Create `Matrix4x4F32.kt`** — port of `SkM44.kt` (4x4 column-major matrix)

Port migration mapping:
| `:math` | `:math:matrix` |
|---|---|
| `SkM44` | `Matrix4x4F32` |
| `SkV4` | `Vector4F32` |
| `SkV3` | `Vector3F32` |
| `SkV2` | `Vector2F32` |
| `SkScalarNearlyZero(x)` | `ScalarF32.nearlyZero(x)` |
| `SkScalarSqrt(x)` | `kotlin.math.sqrt(x)` |

- [ ] **Step 5: Create `Matrix3x3F32Test.kt`** — port of `SkMatrixTest.kt` with renames

Port all test methods from `math/src/commonTest/kotlin/SkMatrixTest.kt` (1260 lines) with type renames. Since this is large, the critical thing is that every test assertion uses the new types.

- [ ] **Step 6: Create `Matrix4x4F32Test.kt`** — port of `SkM44Test.kt` with renames

Port `math/src/commonTest/kotlin/SkM44Test.kt` (419 lines) with type renames.

- [ ] **Step 7: Compile `:math:matrix`**

Run: `./gradlew :math:matrix:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Run `:math:matrix` tests**

Run: `./gradlew :math:matrix:jvmTest`
Expected: all tests pass, BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts math/matrix/
git commit -m "feat: add :math:matrix module with Matrix3x3F32 and Matrix4x4F32"
```

---

### Task 4: `:math:geometry` — Points, rects, sizes, PathOps

**Files:**
- Create: `math/geometry/build.gradle.kts`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Vector2I32.kt` (port from `SkIPoint.kt`)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectF32.kt` (port from `SkRect.kt`)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectI32.kt` (port from `SkRect.kt` IRect portions)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/SizeF32.kt` (port from `SkISize.kt` float size)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/SizeI32.kt` (port from `SkISize.kt` int size)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Point2F64.kt` (port from `SkDPoint.kt`)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Line2F64.kt` (port from `SkDLine.kt`)
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsEpsilon.kt` (port from `SkPathOpsTypes.kt`)
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Vector2I32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectF32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectI32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/SizeTest.kt` (combined F32 + I32)
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Point2F64Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Line2F64Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsEpsilonTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `math/geometry/build.gradle.kts`**

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":math:scalar"))
                api(project(":math:vector"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Add `include(":math:geometry")` to `settings.gradle.kts`**

```kotlin
include(":math:geometry")
```

- [ ] **Step 3: Create `Vector2I32.kt`** — integer 2D vector with saturating arithmetic. Port from `SkIPoint.kt`.

Key public API:
```kotlin
package org.graphiks.math.geometry

public data class Vector2I32(public var x: Int, public var y: Int) {
    // ... port of SkIPoint.kt body ...
    // Saturating arithmetic: +=, -=, +, - operators
}

public typealias Point2I32 = Vector2I32
```

Migration mapping:
| `:math` | `:math:geometry` |
|---|---|
| `SkIPoint` | `Vector2I32` |
| `SkIVector` | `Vector2I32` |
| `SkScalar` | n/a (uses Int) |

- [ ] **Step 4: Create `RectF32.kt`** — port of `SkRect.kt`

Migration mapping:
| `:math` | `:math:geometry` |
|---|---|
| `SkRect` | `RectF32` |
| `SkIRect` | `RectI32` |
| `SkScalarNearlyZero` | `ScalarF32.nearlyZero` |
| `SkScalarIsNaN` | `ScalarF32.isNaN` |
| `SkScalarIsFinite` | `ScalarF32.isFinite` |

- [ ] **Step 5: Create `RectI32.kt`** — integer rectangle with overflow-safe width/height

Contains the `RectI32` data class and companion factories. If `SkIRect` is currently defined in the same file as `SkRect`, extract it into its own file.

- [ ] **Step 6: Create `SizeF32.kt` and `SizeI32.kt`** — port from `SkISize.kt`

```kotlin
// SizeF32.kt
package org.graphiks.math.geometry

public data class SizeF32(public val width: Float, public val height: Float) {
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f
    public companion object {
        public val Empty: SizeF32 = SizeF32(0f, 0f)
    }
}

// SizeI32.kt
package org.graphiks.math.geometry

public data class SizeI32(public val width: Int, public val height: Int) {
    public val isEmpty: Boolean get() = width <= 0 || height <= 0
    public companion object {
        public val Empty: SizeI32 = SizeI32(0, 0)
    }
}
```

- [ ] **Step 7: Create `Point2F64.kt`** — double precision point/vector. Port from `SkDPoint.kt`.

```kotlin
package org.graphiks.math.geometry

public data class Point2F64(public var x: Double, public var y: Double) {
    // ... port of SkDPoint.kt body ...
}

public typealias Vector2F64 = Point2F64
```

- [ ] **Step 8: Create `Line2F64.kt`** — double precision line segment. Port from `SkDLine.kt`.

- [ ] **Step 9: Create `PathOpsEpsilon.kt`** — ULPs-tolerant comparisons. Port from `SkPathOpsTypes.kt`.

- [ ] **Step 10: Create test files** — port each existing test file with renamed types. Test file → test file mapping:

| Source test | New test |
|---|---|
| `SkIPointTest.kt` | `Vector2I32Test.kt` |
| `SkRectTest.kt` | `RectF32Test.kt` |
| `SkIRectTest.kt` | `RectI32Test.kt` |
| `SkDPointTest.kt` (if exists) | `Point2F64Test.kt` |
| `SkPoint3Test.kt` | (covered in vector) |

Create new tests for types that don't have dedicated test files (`Line2F64Test.kt`, `PathOpsEpsilonTest.kt`, `SizeTest.kt`).

- [ ] **Step 11: Compile `:math:geometry`**

Run: `./gradlew :math:geometry:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Run `:math:geometry` tests**

Run: `./gradlew :math:geometry:jvmTest`
Expected: all tests pass, BUILD SUCCESSFUL

- [ ] **Step 13: Commit**

```bash
git add settings.gradle.kts math/geometry/
git commit -m "feat: add :math:geometry module with Vector2I32, RectF32/I32, SizeF32/I32, PathOps types"
```

---

### Task 5: `:math:color` — Color types and transfer functions

**Files:**
- Create: `math/color/build.gradle.kts`
- Create: `math/color/src/commonMain/kotlin/org/graphiks/math/color/ColorARGB.kt` (port from `SkColor.kt`)
- Create: `math/color/src/commonMain/kotlin/org/graphiks/math/color/ColorF32.kt` (port from `SkColor4f.kt`)
- Create: `math/color/src/commonMain/kotlin/org/graphiks/math/color/ColorMatrixF32.kt` (port from `SkColorMatrix.kt`)
- Create: `math/color/src/commonMain/kotlin/org/graphiks/math/color/TransferFunction.kt` (port from `SkcmsTransferFunction.kt`)
- Create: `math/color/src/commonMain/kotlin/org/graphiks/math/color/IccExtensions.kt` (port from `SkcmsMatrix3x3/3x4`)
- Create: `math/color/src/jvmMain/kotlin/org/graphiks/math/color/HalfFloat.kt` (port from jvm `HalfFloat.kt`)
- Create: `math/color/src/commonTest/kotlin/org/graphiks/math/color/ColorARGBTest.kt`
- Create: `math/color/src/commonTest/kotlin/org/graphiks/math/color/ColorF32Test.kt`
- Create: `math/color/src/commonTest/kotlin/org/graphiks/math/color/ColorMatrixF32Test.kt`
- Create: `math/color/src/commonTest/kotlin/org/graphiks/math/color/TransferFunctionTest.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `math/color/build.gradle.kts`**

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":math:scalar"))
                api(project(":math:matrix"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Add `include(":math:color")` to `settings.gradle.kts`**

```kotlin
include(":math:color")
```

- [ ] **Step 3: Create `ColorARGB.kt`** — port of `SkColor.kt`

```kotlin
package org.graphiks.math.color

import org.graphiks.math.scalar.ScalarF32

public typealias ColorARGB = Int

public const val COLOR_TRANSPARENT: ColorARGB = 0x00000000.toInt()
public const val COLOR_BLACK: ColorARGB = 0xFF000000.toInt()
public const val COLOR_DARK_GRAY: ColorARGB = 0xFF444444.toInt()
public const val COLOR_GRAY: ColorARGB = 0xFF888888.toInt()
public const val COLOR_LIGHT_GRAY: ColorARGB = 0xFFCCCCCC.toInt()
public const val COLOR_WHITE: ColorARGB = 0xFFFFFFFF.toInt()
public const val COLOR_RED: ColorARGB = 0xFFFF0000.toInt()
public const val COLOR_GREEN: ColorARGB = 0xFF00FF00.toInt()
public const val COLOR_BLUE: ColorARGB = 0xFF0000FF.toInt()
public const val COLOR_YELLOW: ColorARGB = 0xFFFFFF00.toInt()
public const val COLOR_CYAN: ColorARGB = 0xFF00FFFF.toInt()
public const val COLOR_MAGENTA: ColorARGB = 0xFFFF00FF.toInt()

public fun colorARGB(a: Int, r: Int, g: Int, b: Int): ColorARGB =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

public fun colorRGB(r: Int, g: Int, b: Int): ColorARGB =
    colorARGB(0xFF, r, g, b)

public val ColorARGB.alpha: Int get() = (this shr 24) and 0xFF
public val ColorARGB.red: Int get() = (this shr 16) and 0xFF
public val ColorARGB.green: Int get() = (this shr 8) and 0xFF
public val ColorARGB.blue: Int get() = this and 0xFF

public fun ColorARGB.withAlpha(a: Int): ColorARGB =
    ((a and 0xFF) shl 24) or (this and 0x00FFFFFF.toInt())

public fun premultiplyARGB(a: Int, r: Int, g: Int, b: Int): Int {
    if (a == 0xFF) return colorARGB(a, r, g, b)
    if (a == 0) return 0
    return colorARGB(a, (r * a + 127) / 255, (g * a + 127) / 255, (b * a + 127) / 255)
}

public fun premultiplyColorARGB(color: ColorARGB): ColorARGB =
    premultiplyARGB(color.alpha, color.red, color.green, color.blue)

public fun ColorARGB.premultiplied(): ColorARGB = premultiplyColorARGB(this)

public fun unpremultiplyColorARGB(color: ColorARGB): ColorARGB {
    val a = color.alpha
    if (a == 0 || a == 0xFF) return color
    val r = (color.red * 255 + a / 2) / a
    val g = (color.green * 255 + a / 2) / a
    val b = (color.blue * 255 + a / 2) / a
    return colorARGB(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

public fun ColorARGB.unpremultiplied(): ColorARGB = unpremultiplyColorARGB(this)

public fun multiplyAlpha255(a: Int, scale: Int): Int = (a * scale + 127) / 255
public fun multiplyAlpha32(a: Int, scale: Int): Int = (a * scale + 0x7FFF) / 0xFFFF
```

- [ ] **Step 4: Create `ColorF32.kt`** — port of `SkColor4f.kt`

```kotlin
package org.graphiks.math.color

import org.graphiks.math.scalar.ScalarF32

public data class ColorF32(public var red: Float, public var green: Float, public var blue: Float, public var alpha: Float = 1f) {

    public val isOpaque: Boolean get() = alpha >= 1f

    public fun toColorARGB(): ColorARGB {
        val a = (ScalarF32.clamp(alpha, 0f, 1f) * 255f + 0.5f).toInt()
        val r = (ScalarF32.clamp(red, 0f, 1f) * 255f + 0.5f).toInt()
        val g = (ScalarF32.clamp(green, 0f, 1f) * 255f + 0.5f).toInt()
        val b = (ScalarF32.clamp(blue, 0f, 1f) * 255f + 0.5f).toInt()
        return colorARGB(a, r, g, b)
    }

    public fun toBytesRGBA(): ByteArray {
        val a = (ScalarF32.clamp(alpha, 0f, 1f) * 255f + 0.5f).toInt().toByte()
        val r = (ScalarF32.clamp(red, 0f, 1f) * 255f + 0.5f).toInt().toByte()
        val g = (ScalarF32.clamp(green, 0f, 1f) * 255f + 0.5f).toInt().toByte()
        val b = (ScalarF32.clamp(blue, 0f, 1f) * 255f + 0.5f).toInt().toByte()
        return byteArrayOf(r, g, b, a)
    }

    public fun premultiplied(): ColorF32 {
        val a = alpha
        return ColorF32(red * a, green * a, blue * a, a)
    }

    public fun unpremultiplied(): ColorF32 {
        val a = alpha
        if (a == 0f || a == 1f) return this
        val invA = 1f / a
        return ColorF32(red * invA, green * invA, blue * invA, a)
    }

    public operator fun times(s: Float): ColorF32 = ColorF32(red * s, green * s, blue * s, alpha * s)
    public operator fun times(c: ColorF32): ColorF32 = ColorF32(red * c.red, green * c.green, blue * c.blue, alpha * c.alpha)
    public operator fun plus(c: ColorF32): ColorF32 = ColorF32(red + c.red, green + c.green, blue + c.blue, alpha + c.alpha)

    public companion object {
        public val Transparent: ColorF32 = ColorF32(0f, 0f, 0f, 0f)
        public val Black: ColorF32 = ColorF32(0f, 0f, 0f)
        public val White: ColorF32 = ColorF32(1f, 1f, 1f)
        public val Red: ColorF32 = ColorF32(1f, 0f, 0f)
        public val Green: ColorF32 = ColorF32(0f, 1f, 0f)
        public val Blue: ColorF32 = ColorF32(0f, 0f, 1f)

        public fun fromBytesRGBA(r: Byte, g: Byte, b: Byte, a: Byte = (-1).toByte()): ColorF32 =
            ColorF32(
                (r.toInt() and 0xFF) / 255f,
                (g.toInt() and 0xFF) / 255f,
                (b.toInt() and 0xFF) / 255f,
                (a.toInt() and 0xFF) / 255f
            )

        public fun fromColorARGB(color: ColorARGB): ColorF32 = ColorF32(
            color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f
        )
    }
}

public operator fun Float.times(c: ColorF32): ColorF32 = c * this
```

- [ ] **Step 5: Create `ColorMatrixF32.kt`** — port of `SkColorMatrix.kt` (4x5 color transform)

Port the 253-line file with rename: `SkColorMatrix` → `ColorMatrixF32`.

- [ ] **Step 6: Create `TransferFunction.kt`** — port of `SkcmsTransferFunction.kt`

```kotlin
package org.graphiks.math.color

public data class TransferFunction(
    public val g: Float,
    public val a: Float,
    public val b: Float,
    public val c: Float,
    public val d: Float,
    public val e: Float,
    public val f: Float
) {
    public companion object {
        public val sRGB: TransferFunction = TransferFunction(
            g = 2.4f, a = 1f / 1.055f, b = 0.055f / 1.055f,
            c = 1f / 12.92f, d = 0.04045f, e = 0f, f = 0f
        )

        public val Linear: TransferFunction = TransferFunction(
            g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f
        )

        public val Rec2020: TransferFunction = TransferFunction(
            g = 2.2222222f, a = 0.9096724f, b = 0.0903276f,
            c = 0.2222222f / 0.45f, d = 0.0812429f, e = 0f, f = 0f
        )

        public val PQ: TransferFunction = TransferFunction(
            g = 0.8359375f, a = 0.1593018f, b = 0.0f,
            c = 1.0f, d = 0.0f, e = 0.0f, f = 0.0f
        )

        public val HLG: TransferFunction = TransferFunction(
            g = 1.2f, a = 0.7746413f, b = 0.0042930f,
            c = 0.5555556f, d = 0.0f, e = 0.0f, f = 0.0f
        )
    }
}
```

- [ ] **Step 7: Create `IccExtensions.kt`** — ICC matrix operations as extensions on `Matrix3x3F32`

Port the `SkcmsMatrix3x3` and `SkcmsMatrix3x4` functionality as extension functions on `org.graphiks.math.matrix.Matrix3x3F32`. These are simple data containers with `get(row, col)` accessors — they can be represented using `Matrix3x3F32` from `:math:matrix` plus helper functions.

- [ ] **Step 8: Create `HalfFloat.kt`** — jvm-only half-float conversion. Port from `math/src/jvmMain/kotlin/HalfFloat.kt`.

Place in `math/color/src/jvmMain/kotlin/org/graphiks/math/color/HalfFloat.kt`

- [ ] **Step 9: Create test files** — port each test file with renames:

| Source test | New test |
|---|---|
| `SkColorTest.kt` (if exists) | `ColorARGBTest.kt` (top-level function tests) |
| `SkColor4fTest.kt` | `ColorF32Test.kt` |

Create additional tests for `TransferFunctionTest.kt` and `ColorMatrixF32Test.kt`.

- [ ] **Step 10: Compile `:math:color`**

Run: `./gradlew :math:color:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Run `:math:color` tests**

Run: `./gradlew :math:color:jvmTest`
Expected: all tests pass, BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add settings.gradle.kts math/color/
git commit -m "feat: add :math:color module with ColorARGB, ColorF32, ColorMatrixF32, TransferFunction"
```

---

### Task 6: Final verification

- [ ] **Step 1: Compile entire project with new modules**

Run: `./gradlew compileKotlinJvm`
Expected: BUILD SUCCESSFUL (new modules compile, old `:math` still compiles)

- [ ] **Step 2: Run all tests including new modules and existing `:math`**

Run: `./gradlew jvmTest`
Expected: ALL tests pass (both old `:math` and new modules)

- [ ] **Step 3: Verify no regression in `:math`**

Run: `./gradlew :math:jvmTest`
Expected: all existing `:math` tests pass unchanged

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: verify all math sub-modules compile and tests pass"
```
