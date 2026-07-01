You are writing tests for the new complex Canvas drawing methods in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

These methods were added to Canvas.kt but need tests:
- drawImageNine, drawImageLattice
- drawVertices, drawAtlas
- drawPatch (in CanvasExtensions.kt)
- drawAnnotation

Read these files first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/CanvasExtensions.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt
- kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/ScaffoldCanvasTest.kt (existing test, follow its pattern)

### TASKS

Add tests to `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/ScaffoldCanvasTest.kt`:

**1. drawImageNine**: Create a test Image (100x100), call drawImageNine with center=Rect(30,30,70,70) and dst=Rect(0,0,200,200). Verify a DrawImageNine DisplayOp is emitted with correct fields.

**2. drawImageLattice**: Create a Lattice with xDivs=[25,75], yDivs=[25,75], call drawImageLattice. Verify DrawImageLattice op emitted.

**3. drawVertices**: Create Vertices with 3 positions forming a triangle, call drawVertices. Verify DrawVertices op emitted.

**4. drawAtlas**: Create test Image, 2 transform matrices, 2 tex rects, call drawAtlas. Verify DrawAtlas op emitted.

**5. drawPatch**: Call drawPatch with 12 control points (4×3 for 4 cubic curves forming a rectangle). Verify it doesn't crash and emits a DrawVertices DisplayOp (since patch tessellates to vertices).

**6. drawAnnotation**: Call drawAnnotation with a rect, key, value. Verify Annotation op emitted.

**7. Edge cases**: 
- drawImageNine with empty center rect
- drawAtlas with empty lists
- drawPatch with fewer than 12 points (should handle gracefully)

### VERIFICATION

```bash
./gradlew :kanvas:test --tests "org.graphiks.kanvas.canvas.ScaffoldCanvasTest" 2>&1 | tail -15
```

Commit: `git commit -m "test(kanvas): Phase 2 complex Canvas draw tests"`

Return: test results and any issues found.
