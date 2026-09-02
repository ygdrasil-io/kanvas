# Task 11 report

- Foundation base: `1a9746924a948fc68e4ed636a5042ddf6167c0b3`
- Review-fix base: `4cdbadee2391626121d1b9d7a3b1fc0bc126ec28`
- HEAD: this Task 11 review-fix commit (`fix: close scene capture semantic gaps`)
- RED: the fresh public adapter matrix initially failed for unsupported capture/inverse paths. The review reproductions then failed for a cyclic `ImageFilter.Merge`, Atlas cardinality, normalized image/layer axes, aggregate graph budget, `ClipStack.Complex(emptyList())`, and missing runtime registry API.
- GREEN: 17 public `DisplayOpSceneAdapterTest` / `SceneRoundTripTest` tests pass after the fixes.

## 22-variant matrix

The public ordered matrix covers every `DisplayOp`: DrawRect, DrawRRect, DrawPath (including provenance), DrawImage, DrawText, SetTransform, SetClip, BeginLayer, EndLayer, DrawColor, Clear, DrawPoint, DrawPoints, DrawDRRect, DrawImageNine, DrawImageLattice, DrawPicture, DrawVertices, DrawMesh, DrawAtlas, Annotation, and FlushAndSnapshot.

## Fidelity and invalid cases

- Draw and layer normalized axes retain source material, all paint effects, anti-alias coverage, and both blend mode and custom blender. Image-family draws use `ImageSample` even when a paint is present; PaintNode remains the inverse payload.
- Layer paint, backdrop, composite clip, transform and flags round-trip. `DrawColor` state remains typed and distinct from `Clear`.
- Complex-empty clip is distinct from WideOpen. Clip kind/order/AA/perspective/transform-class and typed SetTransform/SetClip remain reconstructible.
- Capture preflights material/effect/runtime graphs iteratively with identity-cycle detection and a capture-wide graph budget. A mutable cyclic `ImageFilter.Merge` returns `Invalid`; nested Pictures remain depth/cycle bounded.
- Non-finite geometry, clip, gradient, noise, path-effect, lighting, kernel, glyph, TextBlob and Picture values return typed `Invalid`. Malformed Atlas cardinality returns `Invalid` before indexing.
- Runtime capture records id, module metadata, uniform layout/values, child slots and vertex layout. The neutral `RuntimeEffect` registry rejects incompatible same-id descriptors; inverse checks the complete descriptor. Registered shader/color-filter/image-filter and non-null MeshProgram round-trip.
- Mutation isolation covers image pixels, color table, dash, convolution kernel, vertices, lattice divs, runtime matrix uniform input and Mesh children.

## IR extensions

- `BlendNode.Paint` preserves simultaneous mode plus optional custom blender.
- `ClipStackNode.Operations.of(emptyList())` preserves an explicit complex-empty clip.
- Runtime descriptors add `RuntimeModuleMetadata`; capture records neutral runtime vertex metadata.

## Scope

No font implementation, codec/archive transfer, renderer submission, GPU visibility work or JPG color-cube was changed. Typeface inverse remains truthful: Kanvas resource identities reconstruct, while other neutral identities explicitly refuse reconstruction rather than fabricate a typeface.
