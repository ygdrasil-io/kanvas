# Spec 01: wgsl4k Validation And Reflection Contract

Status: Draft
Date: 2026-06-15

## Purpose

Define the minimal `wgsl4k` contract Kanvas needs before treating a complete
WGSL module as valid GPU route evidence.

The contract covers validation, reflection, machine-readable reports, and
stable diagnostics. It does not define a shader compiler, a SkSL translation
layer, or a CPU execution model.

## Inputs

`wgsl4k` must accept complete WGSL modules from Kanvas-controlled sources:

- checked-in WGSL resources;
- generated WGSL modules;
- reusable helper modules assembled into complete modules;
- registered runtime-effect WGSL implementations;
- negative fixtures used by tests.

Fragments alone are not sufficient evidence for a promoted GPU route. Kanvas
must validate and reflect the complete module that will be submitted to WebGPU.

## Validation Contract

For each module, `wgsl4k` must report:

- source id or generated module id;
- module hash over normalized or exact source, with the chosen policy recorded;
- validation success or failure;
- deterministic diagnostics;
- diagnostic spans when available;
- unsupported WGSL features when validation can parse the source but reflection
  cannot represent a required fact.

`wgsl4k` must not silently accept invalid WGSL. If parser behavior is ambiguous
or surprising, Kanvas records the minimized source and keeps the consuming route
unpromoted.

## Entry Point Reflection

Reflection must expose entry points as structured facts:

| Field | Requirement |
|---|---|
| `name` | Stable entry point name. |
| `stage` | `vertex`, `fragment`, or `compute`. |
| `workgroupSize` | Required for compute entry points when declared or inferred. |
| `inputs` | Vertex, fragment, or builtin inputs when representable. |
| `outputs` | Fragment outputs and builtin outputs when representable. |

If an input or output form is valid WGSL but cannot be represented, the report
must emit an unsupported-feature diagnostic instead of omitting it silently.

## Binding Reflection

Reflection must expose each resource binding:

| Field | Requirement |
|---|---|
| `group` | WGSL `@group` number. |
| `binding` | WGSL `@binding` number. |
| `name` | Declared global variable name. |
| `resourceKind` | Uniform buffer, storage buffer, sampled texture, storage texture, sampler, comparison sampler, or explicit unsupported kind. |
| `visibility` | Stages that reference the binding when representable. |
| `access` | Read, write, or read-write where applicable. |
| `sampleType` | Texture sample type when applicable. |
| `viewDimension` | Texture view dimension when applicable. |
| `storageFormat` | Storage texture format when applicable. |
| `minBindingSize` | Reflected or derived minimum binding size when available. |

Kanvas compares these facts with `WGSLBindingLayout`,
`WGSLResourceBindingPlan`, `GPUTextBinding`, `GPURuntimeEffectDescriptor`
resource plans, and future route-specific binding contracts.

## Uniform And Storage Layout Reflection

Reflection must expose layouts for uniform and storage structs:

| Field | Requirement |
|---|---|
| `structName` | WGSL struct name. |
| `addressSpace` | Uniform, storage, or explicit unsupported address space. |
| `size` | Total size when representable. |
| `alignment` | Struct alignment when representable. |
| `members` | Ordered member facts. |
| `member.name` | Field name. |
| `member.type` | Scalar, vector, matrix, array, struct, or explicit unsupported type. |
| `member.offset` | Byte offset. |
| `member.size` | Byte size. |
| `member.alignment` | Byte alignment. |
| `member.stride` | Array or matrix stride when applicable. |

Nested structs, arrays, vectors, matrices, and padding must either be reflected
or explicitly diagnosed as unsupported. Kanvas must not invent default offsets,
sizes, or strides when reflection cannot provide them.

## Machine-Readable Report

Kanvas expects a stable JSON report shape equivalent to:

```json
{
  "schemaVersion": 1,
  "sourceId": "a8_text_mask.wgsl",
  "moduleHash": "sha256:...",
  "validation": {
    "success": true,
    "diagnostics": []
  },
  "entryPoints": [
    {
      "name": "fragmentMain",
      "stage": "fragment",
      "workgroupSize": null,
      "inputs": [],
      "outputs": []
    }
  ],
  "bindings": [
    {
      "group": 2,
      "binding": 0,
      "name": "glyphAtlas",
      "resourceKind": "sampledTexture",
      "visibility": ["fragment"],
      "access": "read",
      "sampleType": "float",
      "viewDimension": "2d",
      "storageFormat": null,
      "minBindingSize": null
    }
  ],
  "layouts": [
    {
      "structName": "TextParams",
      "addressSpace": "uniform",
      "size": 64,
      "alignment": 16,
      "members": [
        {
          "name": "atlasScale",
          "type": "vec2<f32>",
          "offset": 0,
          "size": 8,
          "alignment": 8,
          "stride": null
        }
      ]
    }
  ],
  "unsupportedFeatures": []
}
```

The exact Kotlin model may differ, but the persisted Kanvas report must retain
these facts and preserve stable names.

## Diagnostic Contract

Diagnostics must be deterministic enough for tests and evidence reports.

Initial reason-code examples:

- `wgsl4k.validation.syntax_error`
- `wgsl4k.validation.semantic_error`
- `wgsl4k.reflection.entry_point_missing`
- `wgsl4k.reflection.binding_missing`
- `wgsl4k.reflection.binding_kind_unsupported`
- `wgsl4k.reflection.uniform_layout_unavailable`
- `wgsl4k.reflection.storage_layout_unavailable`
- `wgsl4k.reflection.feature_unrepresented`
- `wgsl4k.io.source_unavailable`

Kanvas may translate these into renderer-specific diagnostics such as
`unsupported.wgsl.feature_unrepresented_by_wgsl4k` or
`unsupported.wgsl.binding_reflection_mismatch`.

## Kanvas Comparison Requirements

Before a route can be promoted, Kanvas must compare wgsl4k reflection against:

- declared `WGSLBindingLayout`;
- declared `WGSLUniformLayout`;
- declared `WGSLStorageLayout`;
- `WGSLPackingPlan`;
- pipeline-layout descriptor inputs;
- route-specific resource plans such as `GPUTextBinding`;
- registered runtime-effect descriptor uniform, child, and resource plans.

Mismatches refuse the route with stable diagnostics. They do not fall back to
unvalidated shader code.

## Failure Policy

If wgsl4k cannot validate or reflect a required fact:

- the consuming Kanvas route remains `blocked`, `proposed`, or `not-promoted`;
- the minimized WGSL case is recorded in the contribution packet;
- no product support claim is expanded;
- no hidden workaround is added to Kanvas.

## Non-Goals

- Do not compile SkSL.
- Do not translate SkSL to WGSL.
- Do not execute WGSL on CPU.
- Do not accept arbitrary user WGSL as renderer extension code.
- Do not treat syntax validation as visual correctness evidence.

## Acceptance Criteria

- Complete WGSL modules can be validated through wgsl4k.
- Entry points are reported with stage and compute workgroup facts where
  applicable.
- Bindings are reported with group, binding, resource kind, visibility, access,
  and texture/sampler/storage facts where applicable.
- Uniform and storage layouts report offset, size, alignment, and stride facts
  or explicit unsupported-feature diagnostics.
- Reports are machine-readable and stable enough for Kanvas fixtures.
- Negative fixtures prove syntax errors, binding mismatches, layout
  unsupported cases, and unregistered module refusals.
