# W52 — Runtime-effect ABI and reflection evidence

The runtime-effect ABI slice defines deterministic std140-style alignment for
scalars, vectors, matrices, and arrays, including explicit offsets, strides,
tail padding, and a zero-cost immutable computed layout. Field names, matrix
dimensions, and array counts are validated before a slab can be used.

Binding group/index mismatches return
`unsupported.runtime_effect.binding_layout_mismatch`, before pipeline creation.
Material cache identity includes length-delimited descriptor ID/version, uniform
slab bytes, and ordered child identities. WGSL acceptance remains exclusively
through the registered descriptor registry's parser-backed validation; no
arbitrary source is admitted.

Uniform slabs are explicitly zero-initialized and expose defensive byte
snapshots, so padding cannot leak uninitialized data into evidence dumps.

Verification: `:gpu-renderer:test --tests '*RuntimeEffectAbiW52Test'` — 6 tests
passed. No native pixel capability is claimed; `gpu-renderer-scenes` was not
modified.
