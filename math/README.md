# Semantic geometry primitives

The math modules distinguish positions (`Point*`) from displacements and
directions (`Vector*`). They are concrete, unrelated types: there is no public
common interface, type alias, implicit conversion, or general point/vector
conversion. Direct constructors are the normal construction API.

## Generated type inventory

| Semantic type | Immutable types | Mutable types |
|---|---|---|
| Point | `Point2F32`, `Point3F32`, `Point2F64`, `Point2I32` | `MutablePoint2F32`, `MutablePoint2F64` |
| Vector | `Vector2F32`, `Vector3F32`, `Vector4F32`, `Vector2F64`, `Vector2I32` | `MutableVector2F32`, `MutableVector3F32`, `MutableVector2F64` |

Immutable values have structural equality. Mutable variants have reference
identity and expose component-wise mutation and `hasSameComponentsAs`; they do
not override `equals` or `hashCode`. Explicit `toMutable()` and `toImmutable()`
conversions copy every component and do not share mutable storage.

## Semantic operations

The generated API uses a closed operation table.

| Expression | Result |
|---|---|
| `point + vector`, `point - vector` | `Point` |
| `pointB - pointA` | `Vector` |
| `vector + vector`, `vector - vector`, `-vector` | `Vector` |
| `vector * scalar`, `scalar * vector` | `Vector` |
| `vector / scalar` | `Vector` for floating-point vectors |
| `vector.dot(vector)` | scalar, or widened accumulator for `I32` |
| `vector.cross(vector)` | scalar in 2D, vector in 3D |
| `vector.normalized()` | `Vector` for `F32` and `F64` |
| `point.distanceTo(point)`, `point.midpointTo(point)` | floating-point scalar and `Point` respectively |

The following expressions are intentionally unavailable: `Point + Point`,
`-Point`, `Point * scalar`, `Point.dot(...)`, `Point.cross(...)`,
`Point.normalized()`, and component-wise `Vector * Vector`. A point can only
become a vector through an operation that names an origin, such as subtracting
two points.

`I32` addition, subtraction, negation, and scalar multiplication saturate at
the `Int` limits. Integer `dot` and `cross` use widened products and saturating
`Long` accumulation. Integer normalization and division are not generated.

## Matrix transforms

Named `transform` functions are the documented API, with `operator *` as the
concise mathematical spelling:

```kotlin
val devicePoint: Point2F32 = matrix * localPoint
val deviceVector: Vector2F32 = matrix * localVector
```

An affine transform applies its translation to a point and only its linear
block to a vector. A projective `Matrix3x3F32` or `Matrix4x4F32` transforms a
point with homogeneous division, but does not define a position-independent
vector transform. Calling `transform(vector)` or `matrix * vector` on such a
matrix requires the matrix to be affine and otherwise throws
`IllegalArgumentException`.

A finite displacement under perspective must name its anchor and use
`transformDisplacementAt(anchor, displacement)`. This computes the difference
between the transformed endpoint and transformed anchor. Bulk APIs retain
explicit names such as `transformPoints` and `transformVectors`, take the
source before the destination, and write into mutable destination values.

## Generation workflow

The typed manifest and generator live in the unpublished JVM tool module
`:math:geometry-codegen`. The generated, checked-in source directories are:

```text
math/vector/src/generated/kotlin
math/geometry/src/generated/kotlin
```

Never edit these files by hand. After changing
`MathPrimitiveManifest.kt` or the emitter, synchronize them explicitly:

```shell
rtk ./gradlew generateMathPrimitives
```

Verify the exact file list, byte content, absence of stale outputs, generation
determinism, and forbidden immutable identity usage without modifying the
checkout:

```shell
rtk ./gradlew verifyMathPrimitivesGenerated
```

Normal JVM/JS compilation and test tasks consume the versioned sources as
ordinary `commonMain` inputs. They do not invoke generation, benchmark
execution, or allocation-measurement tasks. Gradle's global Kotlin/JS npm
resolution can still prepare `package.json` metadata for the registered
benchmark compilation; this does not run a benchmark.

## Immutable representation and MFVC

The manifest targets `MULTI_FIELD_VALUE`, while Kotlin 2.4.0 currently uses the
declared `FINAL_CLASS` fallback. The fallback has `val` components and generated
structural `equals`, `hashCode`, and `toString`, but deliberately has no
`copy()` or `componentN()` API. Internal consumers must not observe reference
identity so a future multi-field value class (MFVC) backend remains
source-compatible.

Switching to native MFVC is a single generator-strategy change only after the
project compiler and both JVM/JS targets support it, the complete test matrix
passes, and dedicated benchmarks justify any performance claim. Mutable
variants remain ordinary classes.
