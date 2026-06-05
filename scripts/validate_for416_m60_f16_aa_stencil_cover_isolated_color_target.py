#!/usr/bin/env python3
"""Generate and validate FOR-416 M60 F16 isolated color-target evidence."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-416"
SCENE_ID = "m60-f16-aa-stencil-cover-isolated-color-target-for416"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-416-m60-f16-isoler-la-sortie-color-target-sans-blend-des-cover-subdraws-mutateurs"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-415-capture-letat-blend-render-pass-et-ecarte-le-descriptor-state-comme-suspect-principal"
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
    / "2026-06-05-for-416-m60-f16-aa-stencil-cover-isolated-color-target.md"
)
SKWEBGPUDEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
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
FOR415_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-blend-render-pass-state-for415"
    / "m60-f16-aa-stencil-cover-blend-render-pass-state-for415.json"
)

FOR416_GUARD = "kanvas.webgpu.m60F16AaStencilCoverIsolatedColorTarget.enabled"
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
    "isolated-color-target-output-zero-fixed-function-suspect",
    "isolated-color-target-output-diverges-from-shader-return",
    "isolated-color-target-output-nonzero-matches-mutation",
    "isolated-color-target-diagnostic-unavailable",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
    "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
    "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for416-pycache python3 -m py_compile "
        "scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-416 validation failed: {message}")


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


def max_abs_delta(a: list[float], b: list[float]) -> float:
    return max(abs(a[index] - b[index]) for index in range(4))


def all_zero(values: list[list[float]]) -> bool:
    return bool(values) and all(
        max(abs(channel) for channel in value) <= MATCH_TOLERANCE for value in values
    )


def delta(a: list[float], b: list[float]) -> dict[str, Any]:
    signed = [round(float(b[index] - a[index]), 9) for index in range(4)]
    absolute = [round(abs(channel), 9) for channel in signed]
    max_channel = max(absolute)
    return {
        "signedRgbaFloat": signed,
        "absoluteRgbaFloat": absolute,
        "absoluteTotalFloat": round(sum(absolute), 9),
        "maxChannelFloat": max_channel,
        "withinTolerance": max_channel <= MATCH_TOLERANCE,
        "tolerance": MATCH_TOLERANCE,
    }


def extract_between(source: str, start: str, end: str) -> str:
    start_index = source.find(start)
    require(start_index >= 0, f"source audit start marker missing: {start}")
    end_index = source.find(end, start_index)
    require(end_index > start_index, f"source audit end marker missing: {end}")
    return source[start_index:end_index]


def source_audit() -> dict[str, Any]:
    source = SKWEBGPUDEVICE.read_text(encoding="utf-8")
    aa_branch = extract_between(
        source,
        "if (d is StencilCoverAaPolygonDraw) {",
        "if (d is StencilCoverAaGradientDraw) {",
    )
    checks = {
        "aaStencilCoverPassTargetsMainColorView": "view = colorView" in aa_branch,
        "aaStencilCoverBranchDoesNotAllocateScratchTarget": "scratch" not in aa_branch.lower(),
        "aaStencilCoverPostDrawReadbackSamplesIntermediateView": (
            "BindGroupEntry(binding = 0u, resource = intermediateView)" in source
            and "m60F16AaStencilCoverPostPassReadbackBindGroup" in source
        ),
        "aaStencilCoverPipelineUsesBlendStateForMode": (
            "private fun aaStencilCoverPipelineFor(" in source
            and "blend = blendStateFor(mode)" in extract_between(
                source,
                "private fun aaStencilCoverPipelineFor(",
                "private fun m60F16FragmentLaneDiagnosticPipelineFor(",
            )
        ),
        "shaderReturnDiagnosticPipelineUsesBlendStateForMode": (
            "blend = blendStateFor(mode)" in extract_between(
                source,
                "private fun m60F16BoundedRuntimeCorrectionPipelineFor(",
                "// \u2500\u2500\u2500 Present pass",
            )
        ),
        "shaderReturnDiagnosticIsStorageSideChannel": (
            "val sourceColorSentToBlend: FloatArray?" in source
            and "M60F16AaStencilCoverShaderReturnDiagnosticSample" in source
        ),
        "for416RuntimeGuardAbsent": FOR416_GUARD not in source,
        "for416ScratchReadbackAbsent": "IsolatedColorTarget" not in source,
    }
    missing = [name for name, present in checks.items() if not present]
    require(not missing, f"SkWebGpuDevice.kt source audit missing checks: {missing}")
    return {
        "source": rel(SKWEBGPUDEVICE),
        "method": (
            "bounded source audit of StencilCoverAaPolygonDraw render-pass, pipeline, "
            "post-draw readback, and FOR-412 shader-return side channel"
        ),
        "checks": checks,
        "conclusion": (
            "The current runtime has no opt-in render pass that redirects the mutating "
            "cover subdraws to a separate no-blend color target. Existing FOR-412 data "
            "is a storage-buffer side channel, and existing FOR-405/FOR-414 data samples "
            "the already blended intermediate texture."
        ),
    }


def compact_subdraw(subdraw: dict[str, Any]) -> dict[str, Any]:
    source = require_rgba(subdraw.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
    role = subdraw.get("subdrawRole")
    require(role in {"inside", "outside"}, f"unexpected subdraw role: {role}")
    require(subdraw.get("shaderObserved") is True, "FOR-412 shader return must be observed")
    require(subdraw.get("captureSynthetic") is False, "synthetic shader return is forbidden")
    return {
        "subdrawOrdinal": subdraw.get("subdrawOrdinal"),
        "subdrawRole": role,
        "pipelineFamily": subdraw.get("pipelineFamily"),
        "blendMode": subdraw.get("blendMode"),
        "fragmentEntryPoint": subdraw.get("state", {}).get("fragmentEntryPoint"),
        "shaderObserved": True,
        "captureSynthetic": False,
        "sourceColorSentToBlend": source,
        "sourceIsZero": max(abs(channel) for channel in source) <= MATCH_TOLERANCE,
        "for412Classification": subdraw.get("classification"),
        "isolatedColorTargetOutput": {
            "available": False,
            "rgbaFloat": None,
            "sampleSource": None,
            "classification": "isolated-color-target-diagnostic-unavailable",
            "unavailableReason": (
                "No FOR-416 no-blend scratch color target was encoded for this subdraw. "
                "The value is intentionally left null rather than inferred from FOR-412."
            ),
        },
        "sourceVsIsolatedColorTargetDelta": {
            "available": False,
            "delta": None,
            "reason": (
                "FOR-412 observed sourceColorSentToBlend, but FOR-416 has no isolated "
                "@location(0) color-target sample. No synthetic zero is used."
            ),
        },
    }


def build_record(record: dict[str, Any]) -> dict[str, Any]:
    x = record.get("x")
    y = record.get("y")
    draw_index = record.get("drawIndex")
    require((x, y) in EXPECTED_POINTS, f"unexpected selected point {(x, y)}")
    require(draw_index in {1, 3}, f"unexpected mutating draw index: {draw_index}")
    require(
        record.get("for413Classification") == "draw-mutates-despite-zero-shader-return",
        f"FOR-413 classification changed at {(x, y)} draw {draw_index}",
    )
    require(
        record.get("for414Classification") == "post-draw-matches-next-predraw",
        f"FOR-414 classification changed at {(x, y)} draw {draw_index}",
    )
    require(
        record.get("postDrawMatchesAfterBoundary") is True,
        f"post-draw no longer matches after boundary at {(x, y)} draw {draw_index}",
    )

    before = require_rgba(record.get("beforeRgbaFloat"), "beforeRgbaFloat")
    after = require_rgba(record.get("afterRgbaFloat"), "afterRgbaFloat")
    post_draw = require_rgba(record.get("postDrawRgbaFloat"), "postDrawRgbaFloat")
    require(max_abs_delta(after, post_draw) <= MATCH_TOLERANCE, "FOR-414 post-draw mismatch")
    require(max_abs_delta(before, after) > MATCH_TOLERANCE, "selected transition must mutate")

    subdraws = [compact_subdraw(subdraw) for subdraw in record.get("shaderReturnSubdraws", [])]
    require(len(subdraws) == 2, f"expected inside/outside subdraw pair at {(x, y)}")
    require({subdraw["subdrawRole"] for subdraw in subdraws} == {"inside", "outside"}, "missing subdraw side")
    sources = [subdraw["sourceColorSentToBlend"] for subdraw in subdraws]
    require(all_zero(sources), f"FOR-412 source must remain zero at {(x, y)} draw {draw_index}")

    return {
        "x": x,
        "y": y,
        "drawIndex": draw_index,
        "transitionId": record.get("transitionId"),
        "pipelineFamily": "StencilCoverAaPolygonDraw",
        "blendMode": "kSrcOver",
        "for413Classification": record.get("for413Classification"),
        "for414Classification": record.get("for414Classification"),
        "for415Classification": record.get("classification"),
        "beforeRgbaFloat": before,
        "afterRgbaFloat": after,
        "postDrawRgbaFloat": post_draw,
        "mutationDelta": record.get("transitionDelta") or delta(before, after),
        "sourceColorSentToBlendSubdraws": subdraws,
        "isolatedColorTarget": {
            "available": False,
            "insideOutputRgbaFloat": None,
            "outsideOutputRgbaFloat": None,
            "combinedOutputRgbaFloat": None,
            "noBlendDestination": True,
            "scratchTargetEncoded": False,
            "classification": "isolated-color-target-diagnostic-unavailable",
            "unavailableReason": (
                "Current evidence contains FOR-412 shader-side storage data and "
                "FOR-414 post-render-pass texture data, but no separate no-blend "
                "color attachment for the cover subdraws."
            ),
        },
        "isolatedVsMutationDelta": {
            "available": False,
            "delta": None,
            "reason": (
                "The isolated color-target value is unobserved; comparing it to "
                "the post-draw mutation would require a dedicated scratch target."
            ),
        },
        "hypotheticalIfIsolatedMatchedFor412": {
            "wouldBeZeroOutput": True,
            "zeroSourceOverExpectedRgbaFloat": before,
            "zeroSourceOverVsObservedAfterDelta": delta(before, after),
            "interpretation": (
                "If a future no-blend target confirms the FOR-412 zero output, "
                "the remaining suspect moves below Kanvas descriptor state."
            ),
        },
        "classification": "isolated-color-target-diagnostic-unavailable",
        "classificationReason": (
            "FOR-412 provides real non-synthetic zero shader returns and FOR-414 "
            "shows an immediate post-draw mutation, but this slice has no observed "
            "no-blend color-target sample for @location(0)."
        ),
    }


def validate_source_artifact_links(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for412": (FOR412_ARTIFACT, "FOR-412", "shader-return-zero-but-post-pass-colored"),
        "for413": (FOR413_ARTIFACT, "FOR-413", "draw-mutates-despite-zero-shader-return"),
        "for414": (FOR414_ARTIFACT, "FOR-414", "post-draw-matches-next-predraw"),
        "for415": (FOR415_ARTIFACT, "FOR-415", "fixed-function-blend-state-captured-no-mismatch"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        data = load_json(path)
        require(data.get("linear") == linear, f"{linear} source Linear id changed")
        require(data.get("classification") == classification, f"{linear} classification changed")


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for412 = load_json(FOR412_ARTIFACT)
    for413 = load_json(FOR413_ARTIFACT)
    for414 = load_json(FOR414_ARTIFACT)
    for415 = load_json(FOR415_ARTIFACT)

    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 changed")
    require(for413.get("classification") == "draw-mutates-despite-zero-shader-return", "FOR-413 changed")
    require(for414.get("classification") == "post-draw-matches-next-predraw", "FOR-414 changed")
    require(
        for415.get("classification") == "fixed-function-blend-state-captured-no-mismatch",
        "FOR-415 changed",
    )

    selected_points = [pixel_key(pixel) for pixel in for401["selectedPixels"]]
    require(selected_points == EXPECTED_POINTS, "FOR-401 selected pixel order changed")

    mutation_records = [build_record(record) for record in for415["mutationRecords"]]
    require(len(mutation_records) == 16, "expected 16 FOR-415 mutation records")
    require(
        Counter(record["drawIndex"] for record in mutation_records) == Counter({1: 6, 3: 10}),
        "mutating draw split changed",
    )
    require(
        all(record["classification"] == "isolated-color-target-diagnostic-unavailable" for record in mutation_records),
        "unexpected per-record classification",
    )
    source_subdraw_count = sum(
        len(record["sourceColorSentToBlendSubdraws"]) for record in mutation_records
    )

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for412": rel(FOR412_ARTIFACT),
            "for413": rel(FOR413_ARTIFACT),
            "for414": rel(FOR414_ARTIFACT),
            "for415": rel(FOR415_ARTIFACT),
        },
        "adapter": for412.get("adapter"),
        "producer": "scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
        "producerMethod": (
            "derived correlation over FOR-412/FOR-413/FOR-414/FOR-415 plus bounded "
            "SkWebGpuDevice.kt source audit; no runtime hook added"
        ),
        "runtimeOwner": "none; no FOR-416 isolated color target runtime hook exists in this slice",
        "decision": (
            "FOR-416 does not infer the no-blend color-target output from FOR-412. The "
            "current runtime exposes shader-side sourceColorSentToBlend and blended "
            "post-draw texture state, but no scratch color attachment that captures "
            "@location(0) with destination blend removed."
        ),
        "classification": "isolated-color-target-diagnostic-unavailable",
        "globalClassification": "isolated-color-target-diagnostic-unavailable",
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
                "notes": "FOR-416 adds no runtime guard and changes no rendering path.",
            },
            "wouldBeRuntimeGuard": {
                "guardId": FOR416_GUARD,
                "presentInRuntime": False,
                "enabledByDefault": False,
                "requiredForFutureDirectObservation": True,
            },
            "sourceRuntimeInputs": {
                "for412ShaderReturnDiagnostic": (
                    "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled"
                ),
                "for414PostDrawReadback": "kanvas.webgpu.m60F16DirectPassWriteHook.enabled",
                "enabledByDefault": False,
            },
        },
        "scope": {
            "selectedPixelCount": len(EXPECTED_POINTS),
            "mutationRecordCount": len(mutation_records),
            "drawsInspected": [1, 3],
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "for401PixelSet": "16 selected M60 F16 residual pixels",
            "generalizedOutsideM60F16": False,
        },
        "comparisonPolicy": {
            "tolerance": MATCH_TOLERANCE,
            "sourceField": "sourceColorSentToBlend",
            "isolatedTargetField": "isolatedColorTarget.*OutputRgbaFloat",
            "noSyntheticShaderReturn": True,
            "missingShaderReturnTreatedAsZero": False,
            "missingIsolatedTargetTreatedAsZero": False,
            "for400UsedAsDirectProof": False,
        },
        "sourceCodeAudit": source_audit(),
        "isolatedTargetSummary": {
            "byClassification": {"isolated-color-target-diagnostic-unavailable": len(mutation_records)},
            "mutatingDrawCounts": {
                str(draw): count
                for draw, count in sorted(Counter(record["drawIndex"] for record in mutation_records).items())
            },
            "sourceColorSentToBlendSubdrawCount": source_subdraw_count,
            "nonSyntheticSourceSubdrawCount": source_subdraw_count,
            "isolatedColorTargetAvailableCount": 0,
            "isolatedColorTargetUnavailableCount": len(mutation_records),
            "sourceVsIsolatedComparisonUnavailableCount": source_subdraw_count,
            "isolatedVsMutationComparisonUnavailableCount": len(mutation_records),
        },
        "mutationRecords": mutation_records,
        "nonGoalsPreserved": [
            "No rendering correction is applied.",
            "No default rendering behavior changes.",
            "No support or promotion claim is made for M60 F16.",
            "No score, threshold, route, fallback, or scene promotion policy changes.",
            "FOR-412 unavailable data is never converted into synthetic zero; all included records use observed non-synthetic sources.",
            "No scratch path is introduced as a rendering correction.",
            "No Ganesh, Graphite, or SkSL compiler work is introduced.",
        ],
        "classificationReason": (
            "All 16 FOR-413 zero-return mutating transitions are covered, with draw 1 "
            "and draw 3 separated and FOR-412 sources kept real and non-synthetic. "
            "However, Kanvas currently does not expose a no-blend scratch color target "
            "for these cover subdraws, so the isolated @location(0) output is not "
            "observable without adding a new runtime diagnostic path."
        ),
        "nextStep": (
            "Add a narrowly gated FOR-416/FOR-417 runtime scratch pass only if direct "
            "observation is still required: render the mutating cover subdraws to a "
            "separate RGBA16Float target with blend disabled, read the 16 FOR-401 "
            "pixels, and compare those samples to FOR-412."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong source finding")

    classification = data.get("classification")
    require(classification == data.get("globalClassification"), "classification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected classification")
    require(classification == "isolated-color-target-diagnostic-unavailable", "classification changed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "taxonomy changed")

    for key in (
        "supportClaim",
        "promoted",
        "correctionAppliedByDefault",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
    ):
        require(data.get(key) is False, f"{key} must remain false")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    validate_source_artifact_links(sources)

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    require(guards["derivedOnly"]["enabledByDefault"] is False, "derived guard must be off by default")
    require(guards["wouldBeRuntimeGuard"]["guardId"] == FOR416_GUARD, "FOR-416 guard changed")
    require(guards["wouldBeRuntimeGuard"]["presentInRuntime"] is False, "FOR-416 runtime must remain absent")

    scope = data.get("scope")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count changed")
    require(scope.get("mutationRecordCount") == 16, "mutation count changed")
    require(scope.get("drawsInspected") == [1, 3], "draw scope changed")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    policy = data.get("comparisonPolicy")
    require(policy.get("noSyntheticShaderReturn") is True, "synthetic sources must be forbidden")
    require(policy.get("missingShaderReturnTreatedAsZero") is False, "missing shader return treated as zero")
    require(policy.get("missingIsolatedTargetTreatedAsZero") is False, "missing isolated target treated as zero")

    audit = data.get("sourceCodeAudit")
    require(isinstance(audit, dict), "sourceCodeAudit missing")
    checks = audit.get("checks")
    require(isinstance(checks, dict) and all(checks.values()), "sourceCodeAudit checks failed")
    require("no opt-in render pass" in audit.get("conclusion", ""), "audit conclusion too weak")

    summary = data.get("isolatedTargetSummary")
    require(summary["mutatingDrawCounts"] == {"1": 6, "3": 10}, "draw split changed")
    require(summary["sourceColorSentToBlendSubdrawCount"] == 32, "source subdraw count changed")
    require(summary["nonSyntheticSourceSubdrawCount"] == 32, "non-synthetic count changed")
    require(summary["isolatedColorTargetAvailableCount"] == 0, "isolated target unexpectedly available")
    require(summary["isolatedColorTargetUnavailableCount"] == 16, "unavailable count changed")

    records = data.get("mutationRecords")
    require(isinstance(records, list) and len(records) == 16, "mutationRecords must contain 16 entries")
    require(
        Counter(record["drawIndex"] for record in records) == Counter({1: 6, 3: 10}),
        "record draw split changed",
    )
    seen_points = []
    for record in records:
        point = pixel_key(record)
        seen_points.append(point)
        require(record["drawIndex"] in {1, 3}, f"unexpected draw at {point}")
        require(record["for413Classification"] == "draw-mutates-despite-zero-shader-return", "FOR-413 drift")
        require(record["for414Classification"] == "post-draw-matches-next-predraw", "FOR-414 drift")
        require(record["for415Classification"] == "fixed-function-blend-state-captured-no-mismatch", "FOR-415 drift")
        require(record["classification"] == classification, f"record classification changed at {point}")
        require_rgba(record["beforeRgbaFloat"], "before")
        require_rgba(record["afterRgbaFloat"], "after")
        require_rgba(record["postDrawRgbaFloat"], "postDraw")
        require(record["mutationDelta"]["withinTolerance"] is False, "mutation delta must be non-zero")
        isolated = record["isolatedColorTarget"]
        require(isolated["available"] is False, "isolated output must be unavailable")
        require(isolated["insideOutputRgbaFloat"] is None, "inside isolated output must be null")
        require(isolated["outsideOutputRgbaFloat"] is None, "outside isolated output must be null")
        require(isolated["combinedOutputRgbaFloat"] is None, "combined isolated output must be null")
        require(isolated["scratchTargetEncoded"] is False, "scratch target must not be encoded")
        require(record["isolatedVsMutationDelta"]["available"] is False, "isolated/mutation delta unavailable")
        subdraws = record["sourceColorSentToBlendSubdraws"]
        require(len(subdraws) == 2, f"expected two source subdraws at {point}")
        require({subdraw["subdrawRole"] for subdraw in subdraws} == {"inside", "outside"}, "side split missing")
        for subdraw in subdraws:
            source = require_rgba(subdraw["sourceColorSentToBlend"], "sourceColorSentToBlend")
            require(max(abs(channel) for channel in source) <= MATCH_TOLERANCE, "source must be zero")
            require(subdraw["shaderObserved"] is True, "source must be observed")
            require(subdraw["captureSynthetic"] is False, "source must be non-synthetic")
            require(subdraw["isolatedColorTargetOutput"]["available"] is False, "subdraw isolated output available")
            require(subdraw["sourceVsIsolatedColorTargetDelta"]["available"] is False, "source/isolated delta available")
    require(sorted(seen_points) == sorted(EXPECTED_POINTS), "selected point set changed")

    commands = data.get("validationCommands")
    for command in EXPECTED_VALIDATION_COMMANDS:
        require(command in commands, f"missing validation command: {command}")
    return str(classification)


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-416",
        classification,
        SOURCE_DRAFT_MEMORY,
        "FOR-412 vs isolated color-target",
        "no scratch color target",
        "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
    ):
        require(needle in text, f"report missing: {needle}")


def write_report(data: dict[str, Any]) -> None:
    summary = data["isolatedTargetSummary"]
    report = f"""# FOR-416 M60 F16 AA stencil-cover isolated color-target

Date: 2026-06-05

## Result

Global classification: `{data["globalClassification"]}`.

FOR-416 keeps the evidence derived-only. It does not add a runtime hook, does not change rendering, and does not infer a no-blend color-target value from the FOR-412 shader-return channel.

## Evidence

- Source draft memory: `{SOURCE_DRAFT_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- FOR-401 artifact: `{rel(FOR401_ARTIFACT)}`
- FOR-412 artifact: `{rel(FOR412_ARTIFACT)}`
- FOR-413 artifact: `{rel(FOR413_ARTIFACT)}`
- FOR-414 artifact: `{rel(FOR414_ARTIFACT)}`
- FOR-415 artifact: `{rel(FOR415_ARTIFACT)}`
- Source owner audited: `{rel(SKWEBGPUDEVICE)}`

## Scope

- Selected pixels: {data["scope"]["selectedPixelCount"]}
- Zero-return mutating transitions covered: {data["scope"]["mutationRecordCount"]}
- Mutating draw counts: {summary["mutatingDrawCounts"]}
- Pipeline family: `{data["scope"]["pipelineFamily"]}`
- Blend mode: `{data["scope"]["blendMode"]}`

## FOR-412 vs isolated color-target

FOR-412 observes `sourceColorSentToBlend` through a storage-buffer side channel immediately before the fragment shader returns `@location(0)`. FOR-414 observes the already updated RGBA16Float intermediate immediately after the draw render pass. FOR-415 audits the descriptor state and finds no mismatch.

The missing observation is the actual color attachment output with destination blending removed. The current `StencilCoverAaPolygonDraw` branch targets the main `colorView`, the AA cover pipelines keep `blendStateFor(mode)`, and the post-draw readback samples `intermediateView`. There is no scratch color target for FOR-416, so the isolated output remains unavailable rather than synthesized.

## Summary

- Non-synthetic FOR-412 source subdraws: {summary["nonSyntheticSourceSubdrawCount"]}
- Isolated color-target samples available: {summary["isolatedColorTargetAvailableCount"]}
- Isolated color-target samples unavailable: {summary["isolatedColorTargetUnavailableCount"]}
- Source-vs-isolated comparisons unavailable: {summary["sourceVsIsolatedComparisonUnavailableCount"]}
- Isolated-vs-mutation comparisons unavailable: {summary["isolatedVsMutationComparisonUnavailableCount"]}

## Interpretation

The 16 mutating transitions remain real: draw 1 accounts for 6 records and draw 3 for 10. Each included record has real, non-synthetic FOR-412 zero shader returns and a FOR-414 immediate post-draw mutation. This ticket refuses to promote that to a direct no-blend color-target result because no such target was encoded.

To turn this into a direct observation, the next slice needs a narrowly gated runtime diagnostic that replays the two cover subdraws into a separate RGBA16Float scratch target with `blend = null`, then reads the same 16 FOR-401 pixels.

## Non-goals preserved

- No rendering correction.
- No default behavior, threshold, score, route, fallback, or promotion change.
- No extension outside M60 F16 / `StencilCoverAaPolygonDraw` / `kSrcOver`.
- No synthetic zero source.
- No Ganesh, Graphite, or SkSL compiler work.

## Validation

Expected local commands:

```text
{chr(10).join(data["validationCommands"])}
```
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = build_artifact()
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(data)
    written = load_json(ARTIFACT)
    classification = validate_artifact(written)
    validate_report(classification)
    print(
        f"FOR-416 artifact validated: {classification}; "
        f"records={len(written['mutationRecords'])}; artifact={rel(ARTIFACT)}"
    )


if __name__ == "__main__":
    main()
