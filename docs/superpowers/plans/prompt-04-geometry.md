You are implementing the full geometry surface for Kanvas: Path introspection, PathMeasure, PathOps, and Region.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

The Path class at `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt` has all verbs and convenience methods implemented, but zero introspection methods. You need to add 9 query methods, then create 3 new files.

Read first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/FillType.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/ClipStack.kt (ClipOp usage)

### PART A: Path Introspection (modify Path.kt)

Add these methods to the Path class:

**1. isEmpty(): Boolean** — returns `verbs().isEmpty()`

**2. isRect(rect: Rect? = null): Boolean**
- Returns true if the path is equivalent to an axis-aligned rectangle (moveTo + 4 lineTo forming a closed rectangle). If rect param is non-null write the rect into it.
- Algorithm: walk verbs. Expect: moveTo, 3× lineTo, close (or 4× lineTo). Check that all edges are axis-aligned.

**3. isOval(bounds: Rect? = null): Boolean**
- Returns true if path is an oval (moveTo + 4 cubicTo forming ellipse). Write bounds if param given.
- Check: moveTo at (cx+rx, cy), then 4 cubicTo approximating ellipse (control points at ±k × radius).

**4. isRRect(rrect: RRect? = null): Boolean**
- Returns true if path is a rounded rectangle. Walk segments: lineTo for straight edges, arcTo/conic for corners.

**5. isLine(line: Line? = null): Boolean**
- True if path is a single line segment (moveTo + lineTo).

**6. isConvex(): Boolean**
- Compute cross product of consecutive edges. All must have same sign (or zero).

**7. isInterpolatable(other: Path): Boolean**
- True if both paths have same number of verbs and compatible structure.

**8. contains(point: Point): Boolean**
- Ray casting algorithm. Cast a horizontal ray from the point, count intersections with path edges. Odd = inside. Handle even-odd and winding fill types.

**9. conservativelyContainsRect(rect: Rect): Boolean**
- True if rect is fully inside the filled path. Quick check: all 4 corners must be `contains()`.

### PART B: PathMeasure (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathMeasure.kt`:

```kotlin
package org.graphiks.kanvas.geometry

class PathMeasure(path: Path, forceClosed: Boolean = false, resScale: Float = 1f) {
    val length: Float
    val isClosed: Boolean

    fun getPosition(distance: Float, position: Point?, tangent: Point?): Boolean
    fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean
    fun getMatrix(distance: Float, matrix: Matrix33, flags: Int): Boolean
    fun nextContour(): Boolean
}
```

Implementation:
- On construction: walk all verbs and points. Pre-compute segment lengths (line = euclidean distance, quad/cubic = approximate by 16 subdivisions). Store cumulative lengths.
- `length`: sum of all segment lengths
- `getPosition`: binary search in cumulative lengths, interpolate line/cubic at the found fraction
- `getSegment`: extract sub-path between two distances, add to dst
- `getMatrix`: position the matrix at distance (translate to position, rotate to tangent)
- `nextContour`: advance internal index to the next moveTo (multi-contour paths)

### PART C: PathOps (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathOps.kt`:

```kotlin
package org.graphiks.kanvas.geometry

enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path?
    fun simplify(path: Path): Path?
    fun asWinding(path: Path): Path?
}
```

Start with a simplified implementation:
- `op`: for rectangles, use Region logic. For general paths, return null with a diagnostic comment.
- `simplify`: no-op for now (return copy of path)
- `asWinding`: return copy with fillType set to WINDING

### PART D: Region (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Region.kt`:

```kotlin
package org.graphiks.kanvas.geometry

enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

class Region {
    constructor()
    constructor(rect: Rect)
    constructor(region: Region)

    val isEmpty: Boolean
    val isRect: Boolean
    val isComplex: Boolean
    val bounds: Rect

    fun setEmpty()
    fun setRect(rect: Rect)
    fun setRegion(region: Region)

    fun op(rect: Rect, op: RegionOp): Boolean
    fun op(region: Region, op: RegionOp): Boolean

    fun contains(x: Float, y: Float): Boolean
    fun quickReject(rect: Rect): Boolean
    fun translate(dx: Float, dy: Float)
}
```

Implementation: store a sorted list of non-overlapping rectangles (Y-sorted then X-sorted). `op()` merges/intersects/diffs two region rect lists.

### PART E: Tests

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/GeometryTest.kt`:

Test at minimum:
- `isEmpty()` on empty and non-empty paths
- `isRect()` on rect path (true) and triangle (false), with out-param writing
- `isConvex()` on square (true) and star (false)
- `contains()` with a point inside and outside a rect path
- `PathMeasure.length` on a 100px line (expect 100f)
- `PathMeasure.getPosition` at 50% of a line
- `Region.isEmpty` on empty and rect region
- `Region.op(UNION)` combining two rects
- `Region.contains()` inside/outside
- `Region.quickReject` for disjoint rects
- `PathOps.op` with two rects union → larger rect (if implemented)

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.geometry.GeometryTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 4 geometry — Path queries, PathMeasure, PathOps, Region"`
