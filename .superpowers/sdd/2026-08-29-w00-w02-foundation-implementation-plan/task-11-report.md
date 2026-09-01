# Task 11 report

- Base: `1a9746924a948fc68e4ed636a5042ddf6167c0b3`
- HEAD: `6f2ec541a541884e581dc646d60309e91a1f2939`
- RED: adapter test compilation failed before the four adapters existed; the 22-variant matrix then failed while variants were unsupported.
- GREEN: `rtk ./gradlew :render-ir:test :kanvas:test --tests '*DisplayOpSceneAdapterTest*' --tests '*SceneRoundTripTest*' --rerun-tasks` passed (8 targeted tests).

## Coverage

The public ordered matrix covers every DisplayOp: DrawRect, DrawRRect, DrawPath (including provenance), DrawImage, DrawText, SetTransform, SetClip, BeginLayer, EndLayer, DrawColor, Clear, DrawPoint, DrawPoints, DrawDRRect, DrawImageNine, DrawImageLattice, DrawPicture, DrawVertices, DrawMesh, DrawAtlas, Annotation, and FlushAndSnapshot.

Capture retains typed transform/clip, nullable image paints, image ownership, lattice/atlas/vertices optional values, full Paint axes, layer paint/backdrop/composite clip/transform, text resolved metadata, and Picture subscenes. IR extensions add typed Draw origin and state commands, full PaintNode/LayerDescriptor, typed clip/device-rect distinction, TextBlob metadata, and MeshProgramNode runtime metadata/children.

## Invalid and isolation cases

Tests cover non-finite coordinates and aggregate image-resource limits. Capture also bounds nodes, nested Pictures and material/effect graphs; cyclic pictures and invalid runtime bindings produce typed invalid diagnostics through the same boundary. Owned image pixels, color tables, dash intervals and convolution kernels are copied before inverse.

## Scope

No font implementation, codec/archive transfer, renderer submit, or GPU visibility work was changed. Inverse restores runtime effects only through `RuntimeEffect.registered(id)` and fails explicitly for an unregistered external descriptor; non-resource Typeface identities stay neutral in IR and are explicitly non-reconstructible.
