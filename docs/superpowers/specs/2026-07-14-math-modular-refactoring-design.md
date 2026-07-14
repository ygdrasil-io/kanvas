# Math Module Modular Refactoring — Design

**Date:** 2026-07-14  
**Status:** Approved  
**Scope:** Create 5 new Kotlin Multiplatform sub-modules under `:math:*` with idiomatic Kotlin naming. Migration of consumers deferred.

## Motivation

The current `:math` module ships 38 source files in a single flat `org.graphiks.math` package. Types are named with Skia `Sk`/`Skcms` prefixes and organized by upstream heritage rather than mathematical domain. This design decomposes the module into 5 focused sub-modules with clear boundaries, Kotlin-idiomatic naming, and one package per module.

## Module Structure

```
:math:scalar          ← no dependencies
:math:vector          ← :math:scalar
:math:matrix          ← :math:scalar, :math:vector
:math:geometry        ← :math:scalar, :math:vector
:math:color           ← :math:scalar, :math:matrix
```

### `:math:scalar`
- **Package:** `org.graphiks.math.scalar`
- **Gradle ID:** `:math:scalar` (folder `math/scalar/`)
- **Build:** `kotlin-multiplatform` convention plugin
- **Types:**
  - `ScalarF32` — `@JvmInline value class` wrapping `Float`
- **Functions (top-level):**
  - Constants: `ScalarF32.One`, `ScalarF32.Pi`, `ScalarF32.Epsilon`, etc.
  - Trig: `sin`, `cos`, `tan` (with snap-to-zero at multiples of π)
  - Predicates: `nearlyZero`, `isFinite`, `isInteger`
  - Rounding: `floor`, `ceil`, `round` (port of `SkScalarFloorTo*`)
  - Math: `sign` (copy-sign), `clamp`, `interp` (lerp), `lerp`
  - Saturating integer arithmetic: `saturatingAdd32`, `saturatingSub32`
- **Sources ported from:** `SkScalar.kt`

### `:math:vector`
- **Package:** `org.graphiks.math.vector`
- **Gradle ID:** `:math:vector` (folder `math/vector/`)
- **Build:** `kotlin-multiplatform` convention plugin
- **Types:**
  - `Vector2F32` — immutable `data class` with `val x, y: Float`
  - `Vector3F32` — immutable `data class` with `val x, y, z: Float`
  - `Vector4F32` — immutable `data class` with `val x, y, z, w: Float`
  - `MutableVector2F32` — `@JvmInline value class` wrapping `FloatArray(2)`
  - `MutableVector3F32` — `@JvmInline value class` wrapping `FloatArray(3)`
- **Operations:** `+`, `-`, `*` (scalar & component-wise), `/`, `dot`, `cross`, `length`, `normalize`
- **Design decisions:**
  - Point and vector types are merged — `Vector2F32` serves both position and direction semantics
  - Mutable variants for in-place C++ idioms (`offset`, `scale`, `setLength`)
  - No mutable `Vector4F32` (used only in matrix row/col operations, immutable is fine)
- **Sources ported from:** `SkV2.kt`, `SkV3.kt`, `SkV4.kt`, `SkPoint.kt`, `SkPoint3.kt`, `SkMathBackend.kt` (internal dot helpers)

### `:math:matrix`
- **Package:** `org.graphiks.math.matrix`
- **Gradle ID:** `:math:matrix` (folder `math/matrix/`)
- **Build:** `kotlin-multiplatform` convention plugin
- **Types:**
  - `Matrix3x3F32` — row-major 3×3 float transform (was `SkMatrix`), `class` (mutable)
  - `Matrix4x4F32` — column-major 4×4 float transform (was `SkM44`), `class` (mutable)
- **Operations:** `invert`, `mapPoint`, `mapVector`, `concat`, `determinant`, `decompose`, `perspective`, pre/post transforms, `ScaleToFit` enum
- **Sources ported from:** `SkMatrix.kt`, `SkM44.kt`, `SkMathBackend.kt` (m44Concat)

### `:math:geometry`
- **Package:** `org.graphiks.math.geometry`
- **Gradle ID:** `:math:geometry` (folder `math/geometry/`)
- **Build:** `kotlin-multiplatform` convention plugin
- **Types:**
  - `Vector2I32` — integer 2D vector with saturating arithmetic, `data class`, `typealias Point2I32 = Vector2I32`
  - `RectF32` — float rectangle, mutable `data class`
  - `RectI32` — integer rectangle with overflow-safe width/height
  - `SizeF32` — float size (width, height)
  - `SizeI32` — integer size (width, height)
  - **PathOps (double precision):**
    - `Point2F64` — double point, mutable
    - `Vector2F64` — double vector, same type as `Point2F64` via companion functions
    - `Line2F64` — double line segment (`pts[2]`)
    - `PathOpsEpsilon` — `object` with epsilon constants and ULPs-tolerant comparison predicates (`approximatelyEqual`, `roughlyEqual`, `preciselyEqual`, etc.)
- **Sources ported from:** `SkIPoint.kt`, `SkRect.kt`, `SkIRect.kt`, `SkISize.kt`, `SkDPoint.kt`, `SkDLine.kt`, `SkPathOpsTypes.kt`

### `:math:color`
- **Package:** `org.graphiks.math.color`
- **Gradle ID:** `:math:color` (folder `math/color/`)
- **Build:** `kotlin-multiplatform` convention plugin
- **Types:**
  - `ColorARGB` — `typealias ColorARGB = Int` with top-level functions for packing/unpacking, premultiply, HSV conversion
  - `ColorF32` — float RGBA (non-premultiplied), mutable `data class`
  - `ColorMatrixF32` — 4×5 color transform matrix, `class` (mutable)
  - `TransferFunction` — `data class` with 7 floats (`g, a, b, c, d, e, f`), parametric curve
  - ICC colorimetric operations as extension functions on `Matrix3x3F32` from `:math:matrix`
  - `HalfFloat` — JVM-only half-float conversion functions (in `src/jvmMain/`)
- **Sources ported from:** `SkColor.kt`, `SkColor4f.kt`, `SkColorMatrix.kt`, `SkcmsTransferFunction.kt`, `SkcmsMatrix3x3.kt`, `SkcmsMatrix3x4.kt`, `HalfFloat.kt` (jvm)

## Naming Conventions

| Rule | Example |
|---|---|
| No abbreviations | `ColorMatrix`, not `ColMat` |
| Type suffix (`F32`, `I32`, `F64`) | `Vector2F32`, `RectI32`, `Point2F64` |
| Dimension before type | `Vector2F32`, `Matrix4x4F32` |
| Format explicit in color types | `ColorARGB`, `ColorF32` |
| `value class` where possible | `ScalarF32`, `MutableVector2F32` |
| Single type + typealias over two types | `Vector2I32` + `typealias Point2I32` |

## What Does NOT Change

- `:math` module remains intact until migration is complete
- No consumer code changes in this phase (`:kanvas`, `:color-management`, etc.)
- Upstream correspondence map (`.upstream/source/map/math/`) stays on `:math` until migration
- File-level upstream trace comments are maintained in the new modules
- Test coverage: port existing tests from `math/src/commonTest/` with updated type names; every previously-tested behavior remains tested in the corresponding new module

## Out of Scope (Deferred)

- Migrating consumers (`:kanvas`, `:color-management`, `integration-tests`, etc.)
- Removing `:math` module after migration
- Updating `.upstream/source/map/math/` correspondence map
- Performance baseline comparison (benchmarks already exist in `SkMathBenchmarks.kt` — they continue to reference `:math` types)

## Module Build Configuration

Each new module uses the existing `buildsrc.convention.kotlin-multiplatform` convention plugin, matching the conventions of the current `:math` module:

```kotlin
// in math/scalar/build.gradle.kts
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}
kotlin {
    sourceSets {
        commonMain.dependencies { }
        commonTest.dependencies { }
    }
}
```

Module dependency declarations use `api(project(":math:scalar"))` so types are transitively available — matching how `color-management` declares `api(project(":math"))` today.

## Verification

- Each module compiles independently
- Each module's ported tests pass
- No regression in existing `:math` module tests
