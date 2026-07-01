You are implementing GPU dispatch for the new Canvas drawing commands in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

The following DisplayOp variants were just added to the codebase and need GPU dispatch in `GPUOpMapper.kt`. Currently they all emit `diagnostics.fatal("unsupported_operation")`. You need to implement real dispatch for each one that can be reasonably handled, matching the existing patterns in the file.

Read the existing file first: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`

Study how existing ops like `DrawRect` and `DrawRRect` are mapped. Follow the same patterns.

### TASKS

For each DisplayOp below, replace the `diagnostics.fatal` stub with real dispatch:

**1. DrawColor / Clear** — fill entire surface with a color.
- `DrawColor`: emit a full-surface rect fill using the color and blend mode. Use `Rect.fromLTRB(0, 0, surfaceWidth, surfaceHeight)` and the existing solid-color fill pipeline.
- `Clear`: same as DrawColor but force blend mode to SRC (direct overwrite).

**2. DrawPoint** — single pixel point.
- Convert to a tiny quad (1px × 1px) at (x, y) and dispatch as a rect fill. Or convert to a Path with moveTo/lineTo and dispatch as path.

**3. DrawPoints** — multiple points with a mode.
- `POINTS`: each point becomes a tiny rect (like DrawPoint)
- `LINES`: pairs of points become line segments → path with moveTo/lineTo pairs
- `POLYGON`: all points become a closed polygon → path with moveTo + lineTo chain + close
- Dispatch the resulting path via the existing path pipeline.

**4. DrawDRRect** — double rounded rectangle.
- Convert to a Path: outer RRect contour (clockwise) + inner RRect contour (counter-clockwise). Use `Path.addRRect` for the outer, reverse the inner points, then dispatch as a Path draw.

**5. DrawImageNine** — 9-patch image.
- Decompose into 9 individual `DrawImage` ops for each cell. The 9 cells are: 4 corners (fixed size), 4 edges (stretch in one direction), 1 center (stretch in both). Compute each cell's src/dst rects from the center rect and dst rect, then call the existing drawImage dispatch for each.

**6. DrawImageLattice** — lattice image grid.
- Same as DrawImageNine but the grid is defined by `lattice.xDivs` and `lattice.yDivs`. Decompose into (xDivs+1)×(yDivs+1) individual drawImage ops. Use `lattice.rects` for per-cell texture coordinates if present, `lattice.colors` for per-cell tinting.

**7. DrawVertices** — triangle mesh.
- Dispatch as a textured triangle mesh render pass. Use the vertices' positions, texCoords, colors, and indices. For now, emit a diagnostic DEGRADE if texCoords is non-null (textured mesh not yet supported) but still draw the mesh with colors.

**8. DrawAtlas** — sprite atlas batch.
- Decompose into individual drawImage ops. For each sprite: compute the dst rect from the transform matrix, use the corresponding texRect, apply the optional color tint. Dispatch each as drawImage.

**9. DrawPicture** — nested picture expansion.
- Recursively expand `picture.ops` into the current display list. Each op in the nested picture inherits the outer DrawPicture's transform (concatenated with the op's own transform) and clip (intersected with the op's own clip). Apply the DrawPicture's optional Paint as alpha modulation.

**10. Annotation** — metadata marker.
- No-op. These are invisible metadata tags. Just remove the fatal diagnostic.

### VERIFICATION

After making changes, compile and run tests:
```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test 2>&1 | tail -10
```

Commit with: `git add -A && git commit -m "feat(kanvas): GPU dispatch for Phase 1-2 DisplayOps"`

Return: compilation result, test result, and summary of what was implemented.
