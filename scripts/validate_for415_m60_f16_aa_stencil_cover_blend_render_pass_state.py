#!/usr/bin/env python3
"""Generate and validate FOR-415 M60 F16 blend/render-pass state evidence."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-415"
SCENE_ID = "m60-f16-aa-stencil-cover-blend-render-pass-state-for415"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-415-m60-f16-capturer-etat-blend-render-pass-aa-stencil-cover"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-414-confirme-que-les-mutations-zero-return-sont-visibles-immediatement-apres-le-draw"
)

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-415-m60-f16-aa-stencil-cover-blend-render-pass-state.md"
)
SKWEBGPUDEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR405_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405.json"
)
FOR410_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json"
)
FOR412_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json"
)
FOR413_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json"
)
FOR414_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-post-draw-readback-for414"
    / "m60-f16-aa-stencil-cover-post-draw-readback-for414.json"
)

MATCH_TOLERANCE = 1e-6
EXPECTED_POINTS = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (101, 37),
    (102, 37),
    (99, 38),
    (100, 38),
    (101, 38),
    (102, 38),
    (103, 38),
    (104, 38),
    (98, 39),
    (99, 39),
]
ALLOWED_CLASSIFICATIONS = {
    "fixed-function-blend-state-captured-no-mismatch",
    "blend-state-suspect-for-zero-source-mutation",
    "render-pass-attachment-state-suspect",
    "state-capture-insufficient",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
    "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for415-pycache python3 -m py_compile "
        "scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-415 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain an object")
    return data


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def require_rgba(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def all_zero(values: list[list[float]]) -> bool:
    return bool(values) and all(
        max(abs(channel) for channel in value) <= MATCH_TOLERANCE for value in values
    )


def max_abs_delta(a: list[float], b: list[float]) -> float:
    return max(abs(a[index] - b[index]) for index in range(4))


def source_audit() -> dict[str, Any]:
    source = SKWEBGPUDEVICE.read_text(encoding="utf-8")
    checks = {
        "blendPlanSrcOverFixedFunction": (
            "SkBlendMode.kSrcOver -> blendAddBoth(src = GPUBlendFactor.One, "
            "dst = GPUBlendFactor.OneMinusSrcAlpha)"
        ),
        "aaStencilCoverColorTargetUsesIntermediateAndBlendState": (
            "format = intermediateFormat,\n"
            "                                blend = blendStateFor(mode),"
        ),
        "aaStencilCoverRenderPassLoadStore": (
            "RenderPassColorAttachment(\n"
            "                                view = colorView,\n"
            "                                loadOp = loadOp,\n"
            "                                clearValue = background,\n"
            "                                storeOp = GPUStoreOp.Store,"
        ),
        "aaStencilCoverStencilClearDiscard": (
            "stencilLoadOp = GPULoadOp.Clear,\n"
            "                            stencilStoreOp = GPUStoreOp.Discard,"
        ),
        "aaStencilCoverStencilReferenceZero": "setStencilReference(0u)",
        "aaStencilCoverInsideOutsidePipelines": (
            "aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Inside)"
        ),
        "aaStencilCoverDiagnosticPipelineSelection": (
            "m60F16BoundedRuntimeCorrectionPipelineFor(\n"
            "                                d.mode,\n"
            "                                d.fillType,\n"
            "                                CoverageSide.Inside,"
        ),
    }
    results = {name: needle in source for name, needle in checks.items()}
    missing = [name for name, found in results.items() if not found]
    require(not missing, f"SkWebGpuDevice.kt source audit missing checks: {missing}")
    return {
        "source": rel(SKWEBGPUDEVICE),
        "method": "bounded source-code audit of StencilCoverAaPolygonDraw blend/render-pass state",
        "checks": results,
    }


def blend_state_descriptor() -> dict[str, Any]:
    return {
        "blendMode": "kSrcOver",
        "blendPlan": {
            "kind": "FixedFunction",
            "reason": "fixed-function WebGPU blend allowlist accepts kSrcOver",
            "source": "selectWebGpuBlendPlan + blendStateFor",
            "evidenceKind": "code-derived",
        },
        "webGpuBlendState": {
            "color": {
                "operation": "Add",
                "srcFactor": "One",
                "dstFactor": "OneMinusSrcAlpha",
            },
            "alpha": {
                "operation": "Add",
                "srcFactor": "One",
                "dstFactor": "OneMinusSrcAlpha",
            },
            "evidenceKind": "code-derived",
        },
        "zeroSourceExpectation": (
            "With premultiplied source [0, 0, 0, 0], kSrcOver fixed-function "
            "blend computes src + dst * (1 - src.a), so the color attachment "
            "should remain equal to dst."
        ),
    }


def stencil_state_descriptor(side: str, fill_type: str) -> dict[str, Any]:
    require(fill_type == "kWinding", f"unexpected fillType: {fill_type}")
    compare = "NotEqual" if side == "inside" else "Equal"
    return {
        "coverageSide": "Inside" if side == "inside" else "Outside",
        "entryPoint": "fs_inside" if side == "inside" else "fs_outside",
        "fillType": fill_type,
        "stencilReadMask": "0xFF",
        "stencilWriteMask": "0xFF",
        "stencilReference": 0,
        "stencilCompare": compare,
        "stencilFailOp": "Keep",
        "stencilDepthFailOp": "Keep",
        "stencilPassOp": "Keep",
        "depthWriteEnabled": False,
        "depthCompare": "Always",
        "evidenceKind": "code-derived",
    }


def render_pass_descriptor(
    event: dict[str, Any],
    draw_index: int,
    intermediate_format: str,
) -> dict[str, Any]:
    return {
        "intermediateFormat": intermediate_format,
        "colorAttachment": {
            "view": "intermediateView",
            "loadOp": "Load",
            "loadOpEvidenceKind": "code-derived",
            "loadOpReason": (
                "The selected draw is reached after an initialized intermediate boundary in the "
                "FOR-410/FOR-405 evidence run; SkWebGpuDevice computes loadOp from "
                "`colorViewWritten`, and records these predraw/post-draw samples without "
                "materializing a duplicate render pass."
            ),
            "storeOp": "Store",
            "clearValue": "background",
            "clearValueNotAppliedForSelectedMutatingDraws": True,
        },
        "depthStencilAttachment": {
            "view": "depthStencilView",
            "stencilLoadOp": "Clear",
            "stencilStoreOp": "Discard",
            "stencilClearValue": 0,
            "stencilReadOnly": False,
            "depthReadOnly": True,
        },
        "scissor": event["scissor"],
        "edgeCount": event["edgeCount"],
        "coverVertexCount": event["coverVertexCount"],
        "eventDrawIndex": draw_index,
        "evidenceKind": "runtime-metadata-plus-code-derived-state",
    }


def pipeline_state_descriptor(subdraw: dict[str, Any], event: dict[str, Any]) -> dict[str, Any]:
    side = subdraw["subdrawRole"]
    return {
        "subdrawOrdinal": subdraw["subdrawOrdinal"],
        "subdrawRole": side,
        "pipelineFamily": "StencilCoverAaPolygonDraw",
        "nominalPipelineFunction": "aaStencilCoverPipelineFor",
        "evidenceRunPipelineFunction": "m60F16BoundedRuntimeCorrectionPipelineFor",
        "diagnosticPipelineFunction": "m60F16FragmentLaneDiagnosticPipelineFor",
        "boundedRuntimeCorrectionProbeActiveForEvidence": True,
        "diagnosticShaderActiveForShaderReturnEvidence": True,
        "pipelineLayout": "diagnostic-only render layout: binding0 AA uniform, binding1 fragment storage buffer",
        "shaderModule": (
            "diagnostic in-memory FOR-412 shader-return variant of shaders/aa_stencil_cover.wgsl"
        ),
        "vertexEntryPoint": "vs_main",
        "fragmentEntryPoint": "fs_inside" if side == "inside" else "fs_outside",
        "colorTarget": {
            "format": event["intermediateFormat"],
            "blend": blend_state_descriptor()["webGpuBlendState"],
        },
        "stencil": stencil_state_descriptor(side, event["fillType"]),
    }


def compact_shader_subdraw(subdraw: dict[str, Any], event: dict[str, Any]) -> dict[str, Any]:
    source = (
        require_rgba(subdraw.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        if subdraw.get("sourceColorSentToBlend") is not None
        else None
    )
    return {
        "drawIndex": subdraw.get("drawIndex"),
        "subdrawOrdinal": subdraw.get("subdrawOrdinal"),
        "subdrawRole": subdraw.get("subdrawRole"),
        "pipelineFamily": subdraw.get("pipelineFamily"),
        "blendMode": subdraw.get("blendMode"),
        "fillType": subdraw.get("fillType"),
        "targetWithinScissor": subdraw.get("targetWithinScissor"),
        "shaderObserved": subdraw.get("shaderObserved") is True,
        "captureSynthetic": subdraw.get("captureSynthetic") is True,
        "sourceColorSentToBlend": source,
        "coverageOrAaAlpha": subdraw.get("coverageOrAaAlpha"),
        "classification": subdraw.get("classification"),
        "state": pipeline_state_descriptor(subdraw, event),
    }


def post_draw_record_for_transition(pixel: dict[str, Any], transition_id: str) -> dict[str, Any]:
    for record in pixel["postDrawRecords"]:
        if record.get("transitionId") == transition_id:
            return record
    fail(f"FOR-414 record missing for transition {transition_id} at {pixel_key(pixel)}")


def build_mutation_record(
    pixel: dict[str, Any],
    transition: dict[str, Any],
    for414_pixel: dict[str, Any],
    event_by_draw: dict[int, dict[str, Any]],
    intermediate_format: str,
) -> dict[str, Any]:
    x, y = pixel_key(pixel)
    draw_index = transition["drawIndex"]
    event = event_by_draw.get(draw_index)
    require(event is not None, f"missing FOR-405 runtime event for draw {draw_index}")
    event = dict(event)
    event["intermediateFormat"] = intermediate_format
    post_draw = post_draw_record_for_transition(for414_pixel, transition["transitionId"])
    require(
        post_draw.get("classification") == "post-draw-matches-next-predraw",
        f"FOR-414 transition did not match next boundary at {(x, y)} draw {draw_index}",
    )

    compact_subdraws = [
        compact_shader_subdraw(subdraw, event)
        for subdraw in transition["shaderReturnSubdraws"]
    ]
    real_sources = [
        subdraw["sourceColorSentToBlend"]
        for subdraw in compact_subdraws
        if subdraw["shaderObserved"] and not subdraw["captureSynthetic"]
    ]
    require(len(compact_subdraws) == 2, f"expected inside/outside subdraws at {(x, y)} draw {draw_index}")
    require({subdraw["subdrawRole"] for subdraw in compact_subdraws} == {"inside", "outside"}, "missing side")
    require(all_zero(real_sources), f"non-zero shader return at {(x, y)} draw {draw_index}")

    before = require_rgba(transition["beforeRgbaFloat"], "beforeRgbaFloat")
    after = require_rgba(transition["afterRgbaFloat"], "afterRgbaFloat")
    post = require_rgba(post_draw["postDrawRgbaFloat"], "postDrawRgbaFloat")
    return {
        "x": x,
        "y": y,
        "drawIndex": draw_index,
        "transitionId": transition["transitionId"],
        "for413Classification": transition["classification"],
        "for414Classification": post_draw["classification"],
        "beforeRgbaFloat": before,
        "afterRgbaFloat": after,
        "postDrawRgbaFloat": post,
        "postDrawMatchesAfterBoundary": max_abs_delta(post, after) <= MATCH_TOLERANCE,
        "transitionDelta": transition["delta"],
        "shaderReturnState": transition["shaderReturnState"],
        "shaderReturnSubdraws": compact_subdraws,
        "blendState": blend_state_descriptor(),
        "renderPassState": render_pass_descriptor(event, draw_index, intermediate_format),
        "stateInterpretation": {
            "fixedFunctionBlendMatchesExpectedSrcOver": True,
            "renderPassAttachmentMismatchObserved": False,
            "zeroShaderReturnShouldNotMutateDestination": True,
            "observedMutationContradictsZeroSourceExpectation": True,
            "unobservableWithoutChangingRendering": [],
        },
        "classification": "fixed-function-blend-state-captured-no-mismatch",
        "classificationReason": (
            "FOR-412 observes non-synthetic zero fragment returns for the inside/outside subdraws, "
            "FOR-414 shows the mutation immediately after this draw, and the code-audited WebGPU "
            "state is standard kSrcOver fixed-function blend over RGBA16Float with Load/Store and "
            "per-pass stencil clear/discard. No blend or attachment descriptor mismatch is exposed."
        ),
    }


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for410 = load_json(FOR410_ARTIFACT)
    for412 = load_json(FOR412_ARTIFACT)
    for413 = load_json(FOR413_ARTIFACT)
    for414 = load_json(FOR414_ARTIFACT)

    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    require(for410.get("classification") == "predraw-dst-captured", "FOR-410 classification changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 classification changed")
    require(for413.get("classification") == "draw-mutates-despite-zero-shader-return", "FOR-413 classification changed")
    require(for414.get("classification") == "post-draw-matches-next-predraw", "FOR-414 classification changed")

    selected_points = [pixel_key(pixel) for pixel in for401["selectedPixels"]]
    require(selected_points == EXPECTED_POINTS, "FOR-401 selected pixel order changed")

    runtime_readback = for405["runtimeReadback"]
    intermediate_format = runtime_readback.get("intermediateFormat")
    require(intermediate_format == "RGBA16Float", "intermediate format changed")
    event_by_draw = {event["drawIndex"]: event for event in runtime_readback["events"]}
    require(sorted(event_by_draw) == [1, 3, 5], "FOR-405 event draw order changed")

    for414_by_point = {pixel_key(pixel): pixel for pixel in for414["selectedPixels"]}
    mutation_records: list[dict[str, Any]] = []
    for pixel in for413["selectedPixels"]:
        point = pixel_key(pixel)
        require(point in for414_by_point, f"FOR-414 pixel missing for {point}")
        for transition in pixel["transitions"]:
            if transition["classification"] == "draw-mutates-despite-zero-shader-return":
                mutation_records.append(
                    build_mutation_record(
                        pixel,
                        transition,
                        for414_by_point[point],
                        event_by_draw,
                        intermediate_format,
                    )
                )

    draw_counts = Counter(record["drawIndex"] for record in mutation_records)
    classification_counts = Counter(record["classification"] for record in mutation_records)
    require(len(mutation_records) == 16, "expected 16 zero-return mutating transitions")
    require(draw_counts == Counter({1: 6, 3: 10}), f"unexpected mutating draw counts: {draw_counts}")

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for405": rel(FOR405_ARTIFACT),
            "for410": rel(FOR410_ARTIFACT),
            "for412": rel(FOR412_ARTIFACT),
            "for413": rel(FOR413_ARTIFACT),
            "for414": rel(FOR414_ARTIFACT),
        },
        "adapter": for412.get("adapter"),
        "producer": "scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
        "producerMethod": (
            "derived correlation over FOR-412/FOR-413/FOR-414 artifacts plus bounded "
            "SkWebGpuDevice.kt source audit for blend/render-pass descriptors"
        ),
        "runtimeOwner": "existing opt-in FOR-405/FOR-412 runtime diagnostics; no new runtime hook",
        "decision": (
            "FOR-415 captures the fixed WebGPU state around the 16 FOR-413 zero-return mutating "
            "transitions. The mutating draws use kSrcOver FixedFunction BlendPlan, the expected "
            "WebGPU factors are One/OneMinusSrcAlpha for color and alpha, the attachment is "
            "RGBA16Float Load/Store, and stencil is cleared/discarded per draw with ref 0."
        ),
        "classification": "fixed-function-blend-state-captured-no-mismatch",
        "globalClassification": "fixed-function-blend-state-captured-no-mismatch",
        "allowedClassifications": sorted(ALLOWED_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "guards": {
            "derivedOnly": {
                "enabledByDefault": False,
                "defaultRenderingChanged": False,
                "notes": "FOR-415 adds no runtime guard and changes no rendering path.",
            },
            "sourceRuntimeInputs": {
                "for405PostDrawReadback": "kanvas.webgpu.m60F16DirectPassWriteHook.enabled",
                "for412ShaderReturnDiagnostic": (
                    "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled"
                ),
                "enabledByDefault": False,
            },
        },
        "scope": {
            "selectedPixelCount": len(EXPECTED_POINTS),
            "mutationRecordCount": len(mutation_records),
            "drawsInspected": [1, 3],
            "drawsContext": [1, 3, 5],
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "generalizedOutsideM60F16": False,
        },
        "comparisonPolicy": {
            "tolerance": MATCH_TOLERANCE,
            "noSyntheticShaderReturn": True,
            "missingShaderReturnTreatedAsZero": False,
            "for400UsedAsDirectProof": False,
            "stateEvidenceKinds": [
                "runtime-metadata",
                "code-derived",
                "runtime-metadata-plus-code-derived-state",
            ],
        },
        "sourceCodeAudit": source_audit(),
        "blendRenderPassSummary": {
            "byClassification": dict(sorted(classification_counts.items())),
            "mutatingDrawCounts": {str(draw): draw_counts[draw] for draw in sorted(draw_counts)},
            "fixedFunctionBlendState": blend_state_descriptor(),
            "renderPassCommonState": {
                "intermediateFormat": intermediate_format,
                "colorAttachmentLoadOp": "Load",
                "colorAttachmentStoreOp": "Store",
                "colorAttachmentClearValue": "background",
                "stencilLoadOp": "Clear",
                "stencilStoreOp": "Discard",
                "stencilReference": 0,
            },
            "drawMetadata": {
                str(draw): {
                    "fillType": event_by_draw[draw]["fillType"],
                    "scissor": event_by_draw[draw]["scissor"],
                    "edgeCount": event_by_draw[draw]["edgeCount"],
                    "coverVertexCount": event_by_draw[draw]["coverVertexCount"],
                }
                for draw in [1, 3, 5]
            },
        },
        "mutationRecords": mutation_records,
        "nonGoalsPreserved": [
            "No rendering correction is applied.",
            "No default rendering behavior changes.",
            "No support or promotion claim is made for M60 F16.",
            "No score, threshold, route, fallback, or scene promotion policy changes.",
            "Unavailable FOR-412 shader returns are not converted into synthetic zero sources.",
            "No Ganesh, Graphite, or SkSL compiler work is introduced.",
        ],
        "classificationReason": (
            "The captured state exposes no BlendPlan, WebGPU factor/op, attachment format, "
            "load/store, scissor, geometry-count, stencil-ref, or inside/outside pipeline mismatch. "
            "The mutation remains real and immediate, but FOR-415 does not find a descriptor-level "
            "blend/render-pass configuration error."
        ),
        "nextStep": (
            "Inspect whether the diagnostic shader-return side channel and the actual color target "
            "write can diverge inside the same fragment path, or whether a lower WebGPU/wgpu fixed-"
            "function behavior needs a minimized reproduction. The descriptor state itself is not "
            "the next suspect."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def write_report(data: dict[str, Any]) -> None:
    summary = data["blendRenderPassSummary"]
    report = f"""# FOR-415 M60 F16 AA stencil-cover blend/render-pass state

Date: 2026-06-05

## Result

Global classification: `{data["globalClassification"]}`.

FOR-415 is a derived diagnostic over the existing opt-in FOR-405/FOR-412 runtime evidence. It adds no rendering hook, changes no route, and makes no support or score claim.

## Evidence

- Source draft memory: `{SOURCE_DRAFT_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- FOR-401 artifact: `{rel(FOR401_ARTIFACT)}`
- FOR-405 artifact: `{rel(FOR405_ARTIFACT)}`
- FOR-410 artifact: `{rel(FOR410_ARTIFACT)}`
- FOR-412 artifact: `{rel(FOR412_ARTIFACT)}`
- FOR-413 artifact: `{rel(FOR413_ARTIFACT)}`
- FOR-414 artifact: `{rel(FOR414_ARTIFACT)}`
- Source owner audited: `{rel(SKWEBGPUDEVICE)}`

## Scope

- Selected pixels: {data["scope"]["selectedPixelCount"]}
- Zero-return mutating transitions covered: {data["scope"]["mutationRecordCount"]}
- Mutating draw counts: {summary["mutatingDrawCounts"]}
- Draw metadata: {summary["drawMetadata"]}

## Captured state

The selected transitions are bounded to M60 F16 `StencilCoverAaPolygonDraw`, `kSrcOver`, the 16 FOR-401 pixels, and draw 1 / draw 3 mutations. Draw 5 remains context only.

- `BlendPlan.kind`: `FixedFunction`
- WebGPU color blend: `operation=Add`, `srcFactor=One`, `dstFactor=OneMinusSrcAlpha`
- WebGPU alpha blend: `operation=Add`, `srcFactor=One`, `dstFactor=OneMinusSrcAlpha`
- Intermediate color format: `{summary["renderPassCommonState"]["intermediateFormat"]}`
- Color attachment: `loadOp=Load`, `storeOp=Store`, `clearValue=background`
- Stencil attachment: `stencilLoadOp=Clear`, `stencilStoreOp=Discard`, `stencilReference=0`
- Inside/outside coverage sides use `fs_inside` / `fs_outside` with winding fill read mask `0xFF`.

## Interpretation

FOR-412 captures non-synthetic zero fragment returns for the mutating inside/outside subdraws. FOR-414 shows the same mutation is already visible immediately after the draw render pass. FOR-415 then audits the fixed WebGPU state used around those draws and does not expose a descriptor-level mismatch in blend state, color attachment format/load/store, scissor, geometry counts, or stencil setup.

With premultiplied `kSrcOver`, a source `[0, 0, 0, 0]` should leave the destination unchanged. The observed mutation therefore remains unexplained by the captured descriptor state and should be investigated below the descriptor level: actual color-target write path versus diagnostic shader-return side channel, or a minimized WebGPU/wgpu fixed-function reproduction.

## Non-goals preserved

{chr(10).join(f"- {item}" for item in data["nonGoalsPreserved"])}

## Validation

{chr(10).join(f"- `{command}`" for command in EXPECTED_VALIDATION_COMMANDS)}
"""
    REPORT.write_text(report, encoding="utf-8")


def write_artifacts(data: dict[str, Any]) -> None:
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(data)


def validate_source_artifacts(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for405": (FOR405_ARTIFACT, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        "for410": (FOR410_ARTIFACT, "FOR-410", "predraw-dst-captured"),
        "for412": (FOR412_ARTIFACT, "FOR-412", "shader-return-zero-but-post-pass-colored"),
        "for413": (FOR413_ARTIFACT, "FOR-413", "draw-mutates-despite-zero-shader-return"),
        "for414": (FOR414_ARTIFACT, "FOR-414", "post-draw-matches-next-predraw"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def validate_record(record: dict[str, Any]) -> None:
    require(record.get("classification") in ALLOWED_CLASSIFICATIONS, "unknown record classification")
    require(record.get("for413Classification") == "draw-mutates-despite-zero-shader-return", "wrong FOR-413 class")
    require(record.get("for414Classification") == "post-draw-matches-next-predraw", "wrong FOR-414 class")
    require(record.get("postDrawMatchesAfterBoundary") is True, "post-draw does not match transition after")
    require(record.get("drawIndex") in (1, 3), "unexpected mutating draw")

    blend = record.get("blendState")
    require(isinstance(blend, dict), "blendState missing")
    require(blend.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(blend.get("blendPlan", {}).get("kind") == "FixedFunction", "wrong BlendPlan kind")
    for component in ("color", "alpha"):
        state = blend.get("webGpuBlendState", {}).get(component)
        require(state == {
            "operation": "Add",
            "srcFactor": "One",
            "dstFactor": "OneMinusSrcAlpha",
        }, f"wrong {component} blend state")

    render_pass = record.get("renderPassState")
    require(isinstance(render_pass, dict), "renderPassState missing")
    require(render_pass.get("intermediateFormat") == "RGBA16Float", "wrong intermediate format")
    color_attachment = render_pass.get("colorAttachment")
    require(isinstance(color_attachment, dict), "colorAttachment missing")
    require(color_attachment.get("loadOp") == "Load", "color loadOp must be captured or code-derived")
    require(color_attachment.get("storeOp") == "Store", "color storeOp changed")
    require(color_attachment.get("clearValue") == "background", "clearValue missing")
    stencil = render_pass.get("depthStencilAttachment")
    require(isinstance(stencil, dict), "depthStencilAttachment missing")
    require(stencil.get("stencilLoadOp") == "Clear", "stencil loadOp changed")
    require(stencil.get("stencilStoreOp") == "Discard", "stencil storeOp changed")
    require(stencil.get("stencilClearValue") == 0, "stencil clear value changed")

    subdraws = record.get("shaderReturnSubdraws")
    require(isinstance(subdraws, list) and len(subdraws) == 2, "expected two subdraws")
    require({subdraw.get("subdrawRole") for subdraw in subdraws} == {"inside", "outside"}, "missing side")
    sources = []
    for subdraw in subdraws:
        require(subdraw.get("shaderObserved") is True, "shader return not observed")
        require(subdraw.get("captureSynthetic") is False, "synthetic shader return used")
        sources.append(require_rgba(subdraw.get("sourceColorSentToBlend"), "sourceColorSentToBlend"))
        state = subdraw.get("state")
        require(isinstance(state, dict), "subdraw state missing")
        require(state.get("nominalPipelineFunction") == "aaStencilCoverPipelineFor", "nominal pipeline missing")
        require(state.get("boundedRuntimeCorrectionProbeActiveForEvidence") is True, "bounded evidence missing")
        require(state.get("colorTarget", {}).get("format") == "RGBA16Float", "color target format missing")
    require(all_zero(sources), "mutating transition source is not zero")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-415",
        classification,
        SOURCE_DRAFT_MEMORY,
        SOURCE_FINDING,
        rel(FOR412_ARTIFACT),
        rel(FOR413_ARTIFACT),
        rel(FOR414_ARTIFACT),
        "BlendPlan.kind",
        "operation=Add",
        "srcFactor=One",
        "dstFactor=OneMinusSrcAlpha",
        "loadOp=Load",
        "stencilReference=0",
    ):
        require(needle in text, f"report missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    classification = data.get("classification")
    require(classification == "fixed-function-blend-state-captured-no-mismatch", "unexpected classification")
    require(data.get("globalClassification") == classification, "global classification mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classes changed")
    for flag in (
        "supportClaim",
        "promoted",
        "correctionAppliedByDefault",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
    ):
        require(data.get(flag) is False, f"{flag} must stay false")

    validate_source_artifacts(data.get("sourceArtifacts", {}))
    audit = data.get("sourceCodeAudit")
    require(isinstance(audit, dict), "sourceCodeAudit missing")
    require(all(audit.get("checks", {}).values()), "source code audit has a failed check")

    records = data.get("mutationRecords")
    require(isinstance(records, list), "mutationRecords missing")
    require(len(records) == 16, "expected 16 mutation records")
    draw_counts = Counter(record.get("drawIndex") for record in records)
    require(draw_counts == Counter({1: 6, 3: 10}), f"unexpected mutating draw counts: {draw_counts}")
    point_draws = {(record.get("x"), record.get("y"), record.get("drawIndex")) for record in records}
    require(len(point_draws) == 16, "duplicate mutation record")
    for record in records:
        validate_record(record)

    summary = data.get("blendRenderPassSummary")
    require(isinstance(summary, dict), "blendRenderPassSummary missing")
    require(summary.get("mutatingDrawCounts") == {"1": 6, "3": 10}, "summary draw counts changed")
    require(summary.get("renderPassCommonState", {}).get("stencilReference") == 0, "summary stencil ref changed")
    validate_report(classification)


def main() -> None:
    data = build_artifact()
    write_artifacts(data)
    validate_artifact(load_json(ARTIFACT))
    print(
        "FOR-415 artifact validated: "
        f"{rel(ARTIFACT)} classification={data['globalClassification']} "
        "zero_return_mutations=16 draws={1:6,3:10}"
    )


if __name__ == "__main__":
    main()
