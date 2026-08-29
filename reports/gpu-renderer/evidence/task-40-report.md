# W60 — Text/vertices/mesh payload evidence

The existing `GPUPreparedVerticesPacker` and upload artifact provide the closed
route used by W60: position `f32x2`, optional premultiplied RGBA8 colors,
optional `f32x2` texcoords, indexed or non-indexed triangle/strip input, and
explicit fan canonicalization. Inputs are snapshotted before publication;
artifact buffers and layout are independently owned.

Bounds are computed from finite positions. Attribute cardinality, index range,
index format/capability, topology, and byte budgets refuse with stable
diagnostics before upload. Affine transforms, clip, image shader, color filter,
and blend remain route metadata/dependency-gated where no native promotion is
present; this report claims no additional pixel capability.

Verification: `:gpu-renderer:test --tests '*GPUPreparedVerticesPackerTest' --tests '*GPUPreparedVerticesW60Test' --tests '*GPUPreparedVerticesNativeSmokeTest' --tests '*GPUPreparedMeshProgramMapperTest'` — 25 tests passed.

The W60-specific contract adds two tests; it does not claim a new rendered GM
or a new native WebGPU pixel route. The evidence is limited to the already
implemented prepared-vertices upload path and its stable acceptance/refusal
semantics.
