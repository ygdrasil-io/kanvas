# W61 — MeshProgram / prepared vertices evidence

Audit of the existing prepared route confirms that the MeshProgram contract is
implemented by `GPUMaterialMapper` and the typed material descriptors, rather
than by a second renderer. Uniforms are copied into the immutable runtime-effect
descriptor; `ShaderChild`, `ColorFilterChild`, and `BlenderChild` are mapped to
ordered typed child descriptors. Matrix/blend/compose filters and mode blenders
are accepted only in their closed set; unsupported descendants, arithmetic
blenders, duplicate child names, invalid alpha, hostile depth, and blank effect
IDs refuse deterministically. The existing lowerer and resolver keep unknown
programs, ABI/WGSL/CPU availability, layout, and resource failures fail-closed
before publication.

The adjacent prepared vertices/payload route supplies the vertex-side contract:
immutable snapshots, canonical position/color/texcoord layouts, indexed and
non-indexed triangle/strip topology, fan canonicalization, bounds, affine
transform/scissor validation, ownership, and byte budgets. These facts are
inputs to MeshProgram lowering; they do not imply a new native pixel route.
No additional GM promotion is claimed by this audit.

Verification commands:

```text
./gradlew --no-daemon :gpu-renderer:test \
  --tests '*GPUPreparedVerticesPackerTest' \
  --tests '*GPUPreparedVerticesPayloadTest' \
  --tests '*GPUPreparedSurfaceVerticesNativePreflightTest'
./gradlew --no-daemon :kanvas:test \
  --tests '*GPUPreparedMeshProgramMapperTest' \
  --tests '*GPUPreparedVerticesLowererTest'
```

No `gpu-renderer-scenes` files were modified and no production source was
needed: the implementation was already present and this wave records its
executable proof. The report itself is the only W61 addition.

Result: 108 targeted tests passed (23 packer, 8 payload, 31 native-preflight,
13 MeshProgram mapper, and 33 prepared-lowerer tests).
