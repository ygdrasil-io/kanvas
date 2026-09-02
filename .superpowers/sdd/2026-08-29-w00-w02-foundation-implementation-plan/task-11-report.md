# Task 11 report

- Foundation base: `1a9746924a948fc68e4ed636a5042ddf6167c0b3`
- Review-fix base: `4cdbadee2391626121d1b9d7a3b1fc0bc126ec28`
- Semantic-gap-fix base: `b6500fabf2ff64b2a074cbab5c8f5022097253ed`
- HEAD: this bounded-runtime-graph follow-up commit (`fix: bound scene runtime graphs`)
- RED: the fresh public adapter matrix initially failed for unsupported capture/inverse paths. The review reproductions then failed for a cyclic `ImageFilter.Merge`, Atlas cardinality, normalized image/layer axes, aggregate graph budget, `ClipStack.Complex(emptyList())`, and missing runtime registry API.
- Second-round RED: inverse of a captured `ImageFilter.RuntimeEffect` with both an implicit shader child and an image-filter child rejected the registered descriptor before reconstructing a `DisplayOp`.
- GREEN: 21 public `DisplayOpSceneAdapterTest` / `SceneRoundTripTest` tests and the complete `:render-ir:test` suite pass after the follow-up fixes.

## 22-variant matrix

The public ordered matrix covers every `DisplayOp`: DrawRect, DrawRRect, DrawPath (including provenance), DrawImage, DrawText, SetTransform, SetClip, BeginLayer, EndLayer, DrawColor, Clear, DrawPoint, DrawPoints, DrawDRRect, DrawImageNine, DrawImageLattice, DrawPicture, DrawVertices, DrawMesh, DrawAtlas, Annotation, and FlushAndSnapshot.

## Fidelity and invalid cases

- Draw and layer normalized axes retain source material, all paint effects, anti-alias coverage, and both blend mode and custom blender. Image-family draws use `ImageSample` even when a paint is present; PaintNode remains the inverse payload.
- Layer paint, backdrop, composite clip, transform and flags round-trip. `DrawColor` state remains typed and distinct from `Clear`.
- Complex-empty clip is distinct from WideOpen. Clip kind/order/AA/perspective/transform-class and typed SetTransform/SetClip remain reconstructible.
- Capture preflights material/effect/runtime graphs iteratively with identity-cycle detection and a capture-wide graph budget. Shared DAG occurrences are charged before recursive IR conversion, so a shared binary shader graph returns `graph-node-limit` at node 21 for a budget of 20 rather than expanding thousands of IR nodes. A mutable cyclic `ImageFilter.Merge` returns `Invalid`; nested Pictures remain depth/cycle bounded.
- Non-finite geometry, clip, gradient, noise, path-effect, lighting, kernel, glyph, TextBlob and Picture values return typed `Invalid`. Malformed Atlas cardinality returns `Invalid` before indexing.
- Runtime capture records id, complete module ABI (source, entry point, module uniform name/binding/type/size and texture name/binding), runtime uniform layout/values, child slots and vertex layout. The neutral `RuntimeEffect` registry rejects incompatible same-id descriptors across every captured module/runtime field; inverse checks the complete descriptor. The image-filter descriptor adds the implicit `SHADER` child slot alongside ordered `IMAGE_FILTER` children. Registered shader/color-filter/image-filter and non-null MeshProgram round-trip.
- Mutation isolation covers image pixels, color table, dash, convolution kernel, vertices, lattice divs, and a truly mutable public `MeshChildren.entries` list after capture; runtime descriptor input lists are defensively owned.

## IR extensions

- `BlendNode.Paint` preserves simultaneous mode plus optional custom blender.
- `ClipStackNode.Operations.of(emptyList())` preserves an explicit complex-empty clip.
- Runtime descriptors add immutable `ShaderModuleDescriptor` and `RuntimeTextureSlot`; canonical identity includes source, entry point, module uniforms, textures and existing vertex/runtime ABI metadata.

## Scope

No font implementation, codec/archive transfer, renderer submission, GPU visibility work or JPG color-cube was changed. Typeface inverse remains truthful: Kanvas resource identities reconstruct, while other neutral identities explicitly refuse reconstruction rather than fabricate a typeface.
