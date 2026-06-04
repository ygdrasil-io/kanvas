#!/usr/bin/env python3
"""Validate the FOR-369 M60 F16 source/candidate coordinate probe evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-369"
SCENE_ID = "m60-f16-source-candidate-coordinate-probe-for369"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
FAMILY_ID = "m60-bounded-stroke-cap-join-positive-residual-evidence-target"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-05-for-369-m60-f16-source-candidate-coordinate-probe.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-source-et-candidate-coordinate-probe-apres-for-368"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-368-confirme-que-les-metadonnees-candidate-m60-f16-restent-absentes-des-artefacts-commites"
)

FOR365_SCENE_ID = "f16-constrained-candidate-evaluation-for365"
FOR365_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR365_SCENE_ID / f"{FOR365_SCENE_ID}.json"
)
FOR366_SCENE_ID = "f16-positive-residual-target-inventory-for366"
FOR366_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR366_SCENE_ID / f"{FOR366_SCENE_ID}.json"
)
FOR367_SCENE_ID = "m60-bounded-stroke-cap-join-comparable-f16-evidence-for367"
FOR367_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR367_SCENE_ID / f"{FOR367_SCENE_ID}.json"
)
FOR368_SCENE_ID = "m60-f16-candidate-metadata-capture-for368"
FOR368_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR368_SCENE_ID / f"{FOR368_SCENE_ID}.json"
)

SOURCE_ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SOURCE_SCENE_ID
AA_RESIDUAL_ARTIFACT = SOURCE_ARTIFACT_DIR / "aa-residual-diagnostic.json"
STATS_ARTIFACT = SOURCE_ARTIFACT_DIR / "stats.json"
ROUTE_CPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "route-cpu.json"
ROUTE_GPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "route-gpu.json"
EXPERIMENTAL_GPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "experimental-gpu-diagnostic.json"
CAPTURE_PRODUCER = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

FOR365_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
FOR366_REQUIRED_DECISION = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
FOR367_REQUIRED_DECISION = "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED"
FOR367_REQUIRED_CLASSIFICATION = "still-missing-comparable-metadata"
FOR368_REQUIRED_DECISION = "M60_F16_CANDIDATE_METADATA_STILL_MISSING"
FOR368_REQUIRED_CLASSIFICATION = "candidate-metadata-still-missing"

DECISION = "M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA"
CLASSIFICATION = "capture-path-still-missing-source-metadata"
ALLOWED_CLASSIFICATIONS = [
    "ready-for-candidate-evaluation",
    CLASSIFICATION,
    "candidate-probe-refused-by-ambiguous-coverage",
]
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
EXPECTED_COORDINATES = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (21, 81),
    (93, 74),
    (17, 77),
    (69, 81),
]

CANDIDATE_STATUS = "not-produced-by-current-m60-capture-path"
SOURCE_STATUS = "not-exposed-by-current-m60-capture-path"
PREMUL_BLEND_STATUS = "not-exposed-by-current-m60-capture-path"
PROBE_RESULT = "refused-before-candidate-evaluation"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for369-pycache python3 -m py_compile "
        "scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

IMPLEMENTATION_FALSE_KEYS = (
    "rendererBehaviorChanged",
    "newColorPolicyImplemented",
    "candidateSelectedForImplementation",
    "selectableCandidateDefined",
    "implementationPlanAuthorized",
    "colorToF16PremulChanged",
    "blendF16PremulModeChanged",
    "skBitmapGetPixelChanged",
    "skBitmapGetPixelAsSrgbChanged",
    "gpuOrWgslChanged",
    "geometryChanged",
    "coverageChanged",
    "fallbackChanged",
    "thresholdsChanged",
    "promotionChanged",
    "scoreChanged",
    "kadreChanged",
    "rendererFixtureOrCoordinateBranchAdded",
    "rendererSceneBranchAdded",
    "rendererSelectedCellOrFullGmCropBranchAdded",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-369 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result: list[int] = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def abs_delta(left: list[int], right: list[int]) -> list[int]:
    return [abs(left[index] - right[index]) for index in range(4)]


def signed_delta(left: list[int], right: list[int]) -> list[int]:
    return [left[index] - right[index] for index in range(4)]


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def has_key(value: Any, key: str) -> bool:
    if isinstance(value, dict):
        return key in value or any(has_key(child, key) for child in value.values())
    if isinstance(value, list):
        return any(has_key(child, key) for child in value)
    return False


def find_line(source: str, needle: str) -> int:
    for index, line in enumerate(source.splitlines(), start=1):
        if needle in line:
            return index
    fail(f"capture producer source no longer contains: {needle}")


def find_line_after(source: str, needle: str, after_needle: str) -> int:
    after_line = find_line(source, after_needle)
    for index, line in enumerate(source.splitlines(), start=1):
        if index <= after_line:
            continue
        if needle in line:
            return index
    fail(f"capture producer source no longer contains {needle} after {after_needle}")


def validate_implementation_false(data: dict[str, Any], linear: str) -> None:
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"{linear} implementation guard changed: {key}")


def validate_inputs(
    for365: dict[str, Any],
    for366: dict[str, Any],
    for367: dict[str, Any],
    for368: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    require(for365.get("linear") == "FOR-365", "FOR-365 identity changed")
    require(for365.get("decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    validate_implementation_false(for365, "FOR-365")

    require(for366.get("linear") == "FOR-366", "FOR-366 identity changed")
    require(for366.get("decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    validate_implementation_false(for366, "FOR-366")
    target = for366.get("proposedNextEvidenceTarget")
    require(isinstance(target, dict), "FOR-366 selected target missing")
    require(target.get("familyId") == FAMILY_ID, "FOR-366 family changed")
    require(target.get("selectedRowId") == ROW_ID, "FOR-366 row changed")
    require(target.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-366 residual changed")

    require(for367.get("linear") == "FOR-367", "FOR-367 identity changed")
    require(for367.get("decision") == FOR367_REQUIRED_DECISION, "FOR-367 decision changed")
    require(for367.get("classification") == FOR367_REQUIRED_CLASSIFICATION, "FOR-367 classification changed")
    validate_implementation_false(for367, "FOR-367")
    for367_line = for367.get("evidenceLine")
    require(isinstance(for367_line, dict), "FOR-367 evidence line missing")
    require(for367_line.get("rowId") == ROW_ID, "FOR-367 row changed")
    require(for367_line.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")
    require(for367_line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-367 sample count changed")

    require(for368.get("linear") == "FOR-368", "FOR-368 identity changed")
    require(for368.get("decision") == FOR368_REQUIRED_DECISION, "FOR-368 decision changed")
    require(for368.get("classification") == FOR368_REQUIRED_CLASSIFICATION, "FOR-368 classification changed")
    validate_implementation_false(for368, "FOR-368")
    for368_line = for368.get("evidenceLine")
    require(isinstance(for368_line, dict), "FOR-368 evidence line missing")
    require(for368_line.get("rowId") == ROW_ID, "FOR-368 row changed")
    require(for368_line.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-368 residual changed")
    require(for368_line.get("computedResidual") == REQUIRED_RESIDUAL, "FOR-368 residual changed")
    require(for368_line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-368 sample count changed")

    return for367_line, for368_line


def validate_source_artifacts(artifacts: dict[str, dict[str, Any]]) -> dict[str, Any]:
    for name, artifact in artifacts.items():
        require(artifact.get("sceneId") == SOURCE_SCENE_ID, f"{name} scene id changed")

    aa = artifacts["aaResidualDiagnostic"]
    stats = artifacts["stats"]
    route_cpu = artifacts["routeCpu"]
    route_gpu = artifacts["routeGpu"]
    experimental = artifacts["experimentalGpuDiagnostic"]

    require(aa.get("status") == "diagnostic-only", "M60 AA diagnostic status changed")
    require(aa.get("targetColorSpaceBlend") is True, "M60 targetColorSpaceBlend changed")
    require(stats.get("gpuStatus") == "expected-unsupported", "M60 GPU status changed")
    require(route_cpu.get("status") == "pass", "M60 CPU route status changed")
    require(route_gpu.get("status") == "expected-unsupported", "M60 GPU route status changed")
    require(
        route_gpu.get("fallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold",
        "M60 fallback reason changed",
    )
    require(experimental.get("status") == "diagnostic-only", "M60 experimental diagnostic status changed")

    prohibited_keys = [
        "candidatePolicyRgba",
        "sourceRawRgba",
        "sourceInputRgba",
        "rawF16Premul",
        "rawF16Unpremul",
        "sourceCoverage",
        "sourceColor",
    ]
    presence = {
        name: {key: has_key(artifact, key) for key in prohibited_keys}
        for name, artifact in artifacts.items()
    }
    for name, keys in presence.items():
        for key, present in keys.items():
            require(present is False, f"{name} unexpectedly exposes {key}")

    return {
        "aaResidualDiagnostic": rel(AA_RESIDUAL_ARTIFACT),
        "stats": rel(STATS_ARTIFACT),
        "routeCpu": rel(ROUTE_CPU_ARTIFACT),
        "routeGpu": rel(ROUTE_GPU_ARTIFACT),
        "experimentalGpuDiagnostic": rel(EXPERIMENTAL_GPU_ARTIFACT),
        "jsonKeyPresence": presence,
        "diagnosticStatus": aa.get("status"),
        "targetColorSpaceBlend": aa.get("targetColorSpaceBlend"),
        "gpuStatus": stats.get("gpuStatus"),
        "routeGpuStatus": route_gpu.get("status"),
        "fallbackReason": route_gpu.get("fallbackReason"),
        "remainingRootCause": route_gpu.get("remainingRootCause"),
        "experimentalStatus": experimental.get("status"),
    }


def inspect_capture_producer() -> dict[str, Any]:
    require(CAPTURE_PRODUCER.is_file(), f"missing capture producer: {rel(CAPTURE_PRODUCER)}")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    required_needles = [
        "val residualStats = strokeResidualStats(experimentalGpu, reference)",
        "private fun strokeResidualStats(gpu: SkBitmap, reference: SkBitmap): StrokeResidualStats",
        "val gpuPixel = gpu.getPixel(x, y)",
        "val refPixel = reference.getPixel(x, y)",
        "highDeltaSamples += ResidualSample(",
        "private data class ResidualSample(",
        "fun toJson(): String =",
        '"referenceRgba": ${rgbaJson(reference)}',
        '"gpuRgba": ${rgbaJson(gpu)}',
        "private data class ResidualSample(",
        "reference = refPixel",
        "gpu = gpuPixel",
        "WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)",
    ]
    lines = {needle: find_line(source, needle) for needle in required_needles}
    residual_function = "private fun strokeResidualStats(gpu: SkBitmap, reference: SkBitmap): StrokeResidualStats"
    lines["strokeResidualStats.gpuPixel"] = find_line_after(
        source,
        "val gpuPixel = gpu.getPixel(x, y)",
        residual_function,
    )
    lines["strokeResidualStats.refPixel"] = find_line_after(
        source,
        "val refPixel = reference.getPixel(x, y)",
        residual_function,
    )
    require(F16_POLICY_ID not in source, "M60 capture producer unexpectedly mentions the retired F16 policy")
    return {
        "producer": rel(CAPTURE_PRODUCER),
        "producerMentionsCandidatePolicy": False,
        "entryPoint": {
            "line": lines["val residualStats = strokeResidualStats(experimentalGpu, reference)"],
            "finding": (
                "The M60 residual artifact is produced from strokeResidualStats with only "
                "the experimental GPU bitmap and the Skia reference bitmap."
            ),
        },
        "sampleConstruction": {
            "classifyResidualLine": lines[
                "private fun strokeResidualStats(gpu: SkBitmap, reference: SkBitmap): StrokeResidualStats"
            ],
            "gpuPixelLine": lines["strokeResidualStats.gpuPixel"],
            "referencePixelLine": lines["strokeResidualStats.refPixel"],
            "highDeltaSampleLine": lines["highDeltaSamples += ResidualSample("],
            "residualSampleClassLine": lines["private data class ResidualSample("],
            "fromDeltaReferenceLine": lines["reference = refPixel"],
            "fromDeltaGpuLine": lines["gpu = gpuPixel"],
            "finding": (
                "ResidualSample is built from getPixel(reference) and getPixel(gpu) only; source/input/raw "
                "RGBA, candidatePolicyRgba, and raw F16 premul/blend inputs are not available "
                "to the sample object."
            ),
        },
        "serialization": {
            "toJsonLine": lines["fun toJson(): String ="],
            "referenceRgbaLine": lines['"referenceRgba": ${rgbaJson(reference)}'],
            "gpuRgbaLine": lines['"gpuRgba": ${rgbaJson(gpu)}'],
            "finding": (
                "ResidualSample.toJson serializes x/y/maxChannelDelta/referenceRgba/gpuRgba only."
            ),
        },
        "policyProbe": {
            "targetBlendTrueLine": lines["WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)"],
            "candidatePolicyId": F16_POLICY_ID,
            "finding": (
                "The existing M60 probe policy is a targetColorSpaceBlend diagnostic toggle, "
                "not the straight_srgb_quantized_alpha_src_over_white candidate policy."
            ),
        },
    }


def build_sample(raw: dict[str, Any], aa_raw: dict[str, Any], index: int, blocker: str) -> dict[str, Any]:
    require(raw.get("index") == index, "FOR-368 sample index changed")
    require((raw.get("x"), raw.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-368 sample coordinate changed")
    require((aa_raw.get("x"), aa_raw.get("y")) == EXPECTED_COORDINATES[index - 1], "M60 AA sample coordinate changed")

    reference = rgba(raw.get("referenceRgba"), "FOR-368 referenceRgba")
    current = rgba(raw.get("currentRgba"), "FOR-368 currentRgba")
    aa_reference = rgba(aa_raw.get("referenceRgba"), "M60 AA referenceRgba")
    aa_current = rgba(aa_raw.get("gpuRgba"), "M60 AA gpuRgba")
    require(reference == aa_reference, "FOR-368 and M60 AA reference sample differ")
    require(current == aa_current, "FOR-368 and M60 AA current sample differ")
    residual = sample_residual(reference, current)
    require(raw.get("sampleResidual") == residual, "FOR-368 sample residual changed")
    require(aa_raw.get("maxChannelDelta") == max(abs_delta(reference, current)), "M60 max delta changed")
    require(raw.get("candidatePolicyRgba") is None, "FOR-368 unexpectedly captured candidatePolicyRgba")
    require(raw.get("sourceRawRgba") is None, "FOR-368 unexpectedly captured sourceRawRgba")

    return {
        "index": index,
        "x": raw["x"],
        "y": raw["y"],
        "region": raw.get("region"),
        "referenceRgba": reference,
        "currentRgba": current,
        "signedCurrentMinusReferenceDelta": signed_delta(current, reference),
        "absoluteChannelDelta": abs_delta(reference, current),
        "sampleResidual": residual,
        "candidatePolicyRgba": None,
        "candidatePolicyRgbaStatus": CANDIDATE_STATUS,
        "candidatePolicyRgbaProbeResult": PROBE_RESULT,
        "sourceRawRgba": None,
        "sourceRawRgbaStatus": SOURCE_STATUS,
        "sourceCoverage": None,
        "sourceCoverageStatus": SOURCE_STATUS,
        "sourceColor": None,
        "sourceColorStatus": SOURCE_STATUS,
        "premulBlendAssumptionStatus": PREMUL_BLEND_STATUS,
        "probeRefusalReason": blocker,
        "artifactOnlyValueProduced": False,
        "rendererAppliedCandidate": False,
        "readyForCandidateEvaluation": False,
    }


def build_artifact() -> dict[str, Any]:
    for365 = load_json(FOR365_ARTIFACT)
    for366 = load_json(FOR366_ARTIFACT)
    for367 = load_json(FOR367_ARTIFACT)
    for368 = load_json(FOR368_ARTIFACT)
    for367_line, for368_line = validate_inputs(for365, for366, for367, for368)

    source_artifacts = {
        "aaResidualDiagnostic": load_json(AA_RESIDUAL_ARTIFACT),
        "stats": load_json(STATS_ARTIFACT),
        "routeCpu": load_json(ROUTE_CPU_ARTIFACT),
        "routeGpu": load_json(ROUTE_GPU_ARTIFACT),
        "experimentalGpuDiagnostic": load_json(EXPERIMENTAL_GPU_ARTIFACT),
    }
    source_probe = validate_source_artifacts(source_artifacts)
    producer_probe = inspect_capture_producer()

    raw_samples = for368_line.get("samples")
    aa_samples = source_artifacts["aaResidualDiagnostic"].get("highDeltaSamples")
    require(isinstance(raw_samples, list), "FOR-368 samples missing")
    require(isinstance(aa_samples, list), "M60 AA highDeltaSamples missing")
    require(len(raw_samples) == REQUIRED_SAMPLE_COUNT, "FOR-368 sample count changed")
    require(len(aa_samples) >= REQUIRED_SAMPLE_COUNT, "M60 AA highDeltaSamples too short")

    blocker = (
        f"{producer_probe['producer']}:"
        f"{producer_probe['sampleConstruction']['classifyResidualLine']} and "
        f"{producer_probe['producer']}:{producer_probe['serialization']['toJsonLine']} "
        "build and serialize residual samples from reference/current bitmap deltas only; "
        "the capture path does not carry candidatePolicyRgba, source/input/raw RGBA, "
        "source coverage/color, or explicit F16 premul/blend inputs."
    )
    samples = [
        build_sample(raw, aa_raw, index, blocker)
        for index, (raw, aa_raw) in enumerate(zip(raw_samples, aa_samples, strict=True), start=1)
    ]
    computed_residual = sum(sample["sampleResidual"] for sample in samples)
    require(computed_residual == REQUIRED_RESIDUAL, "computed residual changed")

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": SOURCE_SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "decisionReason": (
            "FOR-369 inspected the M60 residual capture path after FOR-368 and found that "
            "the producer records reference/current residual samples only. It cannot expose "
            "candidatePolicyRgba, source/raw RGBA, source coverage/color, or explicit F16 "
            "premul/blend inputs without changing the capture producer or renderer path."
        ),
        "inputValidation": {
            "for365Artifact": rel(FOR365_ARTIFACT),
            "for365Decision": for365.get("decision"),
            "for365RequiredDecision": FOR365_REQUIRED_DECISION,
            "for366Artifact": rel(FOR366_ARTIFACT),
            "for366Decision": for366.get("decision"),
            "for366RequiredDecision": FOR366_REQUIRED_DECISION,
            "for367Artifact": rel(FOR367_ARTIFACT),
            "for367Decision": for367.get("decision"),
            "for367RequiredDecision": FOR367_REQUIRED_DECISION,
            "for367Classification": for367.get("classification"),
            "for367RequiredClassification": FOR367_REQUIRED_CLASSIFICATION,
            "for367RowId": for367_line.get("rowId"),
            "for367CurrentResidual": for367_line.get("currentResidual"),
            "for368Artifact": rel(FOR368_ARTIFACT),
            "for368Decision": for368.get("decision"),
            "for368RequiredDecision": FOR368_REQUIRED_DECISION,
            "for368Classification": for368.get("classification"),
            "for368RequiredClassification": FOR368_REQUIRED_CLASSIFICATION,
            "for368CurrentResidual": for368_line.get("currentResidual"),
        },
        "sourceArtifacts": source_probe,
        "captureProducerInspection": producer_probe,
        "probeLine": {
            "rowId": ROW_ID,
            "familyId": FAMILY_ID,
            "sourceKind": for367_line.get("sourceKind"),
            "rowKind": for367_line.get("rowKind"),
            "candidatePolicyId": F16_POLICY_ID,
            "currentResidual": REQUIRED_RESIDUAL,
            "computedResidual": computed_residual,
            "sampleCount": REQUIRED_SAMPLE_COUNT,
            "sampleCoordinatesPreservedFromFor367": True,
            "referenceCurrentComparable": True,
            "referenceCurrentCandidateComparable": False,
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "blocker": {
                "status": CLASSIFICATION,
                "point": blocker,
                "candidatePolicyRgba": CANDIDATE_STATUS,
                "sourceRawRgba": SOURCE_STATUS,
                "sourceCoverageOrColor": SOURCE_STATUS,
                "premulBlendAssumptions": PREMUL_BLEND_STATUS,
            },
            "samples": samples,
        },
        "candidateProbeReadiness": {
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "candidatePolicyRgbaProduced": False,
            "artifactOnlyCandidateValuesProduced": False,
            "ambiguousCoverageRefusal": False,
            "missingMetadata": [
                "candidatePolicyRgba for straight_srgb_quantized_alpha_src_over_white",
                "source/input/raw RGBA at the same 10 coordinates",
                "source coverage/color sufficient to compute the candidate externally",
                "explicit F16 premul/blend assumptions for the diagnostic calculation",
            ],
            "blockingCapturePath": blocker,
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "candidateImplementationAuthorized": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "kadreChanged": False,
            "f16PremulBlendRuntimeChanged": False,
            "skBitmapGetPixelChanged": False,
            "rendererSceneBranchAdded": False,
            "rendererCoordinateBranchAdded": False,
            "rendererSelectedCellBranchAdded": False,
            "fixtureOnlyPathAdded": False,
            "fullGmCropPathAdded": False,
        },
        "criteriaEvaluation": {
            "sourceMemoryRecorded": True,
            "sourceFindingRecorded": True,
            "for368DecisionRequired": True,
            "for368ClassificationRequired": True,
            "sameTenCoordinatesPreserved": True,
            "currentResidualKeptAt856": True,
            "sourceCapturePathInspected": True,
            "candidatePolicyRgbaStatusRecordedForEachSample": True,
            "sourceRawRgbaStatusRecordedForEachSample": True,
            "premulBlendAssumptionStatusRecordedForEachSample": True,
            "blockingCapturePointRecorded": True,
            "noValuesInvented": True,
            "rendererBehaviorChanged": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "candidateImplementationAuthorized": False,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "selectableCandidateDefined": False,
            "implementationPlanAuthorized": False,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "thresholdsChanged": False,
            "promotionChanged": False,
            "scoreChanged": False,
            "kadreChanged": False,
            "rendererFixtureOrCoordinateBranchAdded": False,
            "rendererSceneBranchAdded": False,
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def render_report(data: dict[str, Any]) -> str:
    line = data["probeLine"]
    readiness = data["candidateProbeReadiness"]
    producer = data["captureProducerInspection"]
    lines = [
        "# FOR-369 Probe source/candidate F16 M60",
        "",
        f"Linear: `{LINEAR_ID}`",
        "",
        f"Decision: `{data['decision']}`",
        "",
        f"Classification: `{data['classification']}`",
        "",
        "FOR-369 reste une preuve diagnostique. Il reprend exactement les 10",
        "coordonnees FOR-367/FOR-368, conserve le residuel courant `856`, puis",
        "inspecte le chemin de capture M60 qui produit les artefacts",
        "`aa-residual-diagnostic.json`, `stats.json`, `route-cpu.json`,",
        "`route-gpu.json` et `experimental-gpu-diagnostic.json`.",
        "",
        "## Source memoire",
        "",
        f"- Brouillon: `{data['sourceMemory']}`",
        f"- Finding FOR-368: `{data['sourceFinding']}`",
        "",
        "## Entrees verrouillees",
        "",
        f"- FOR-368 decision requise: `{FOR368_REQUIRED_DECISION}`",
        f"- FOR-368 classification requise: `{FOR368_REQUIRED_CLASSIFICATION}`",
        f"- FOR-367 ligne: `{ROW_ID}`",
        f"- Residuel FOR-367/FOR-368: `{REQUIRED_RESIDUAL}`",
        "",
        "## Resultat du probe",
        "",
        f"- Classification: `{readiness['classification']}`",
        f"- Pret pour evaluation candidate: `{readiness['readyForCandidateEvaluation']}`",
        f"- candidatePolicyRgba produites: `{readiness['candidatePolicyRgbaProduced']}`",
        f"- Valeurs candidate d'artefact produites: `{readiness['artifactOnlyCandidateValuesProduced']}`",
        f"- Refus par couverture ambigue: `{readiness['ambiguousCoverageRefusal']}`",
        "",
        "Le blocage est dans le chemin de capture, pas dans une evaluation candidate:",
        "",
        f"`{readiness['blockingCapturePath']}`",
        "",
        "## Inspection du producteur",
        "",
        f"- Producteur: `{producer['producer']}`",
        f"- Entree residual: ligne `{producer['entryPoint']['line']}` - {producer['entryPoint']['finding']}",
        (
            f"- Construction sample: lignes `{producer['sampleConstruction']['classifyResidualLine']}`, "
            f"`{producer['sampleConstruction']['gpuPixelLine']}`, "
            f"`{producer['sampleConstruction']['referencePixelLine']}`, "
            f"`{producer['sampleConstruction']['highDeltaSampleLine']}` - "
            f"{producer['sampleConstruction']['finding']}"
        ),
        (
            f"- Serialisation: lignes `{producer['serialization']['toJsonLine']}`, "
            f"`{producer['serialization']['referenceRgbaLine']}`, "
            f"`{producer['serialization']['gpuRgbaLine']}` - "
            f"{producer['serialization']['finding']}"
        ),
        f"- Politique existante: ligne `{producer['policyProbe']['targetBlendTrueLine']}` - {producer['policyProbe']['finding']}",
        "",
        "## Metadonnees manquantes",
        "",
    ]
    lines.extend(f"- {item}" for item in readiness["missingMetadata"])
    lines.extend(
        [
            "",
            "## Table des echantillons",
            "",
            "| # | x | y | reference RGBA | current RGBA | residual | candidatePolicyRgba | source/raw | premul/blend |",
            "|---:|---:|---:|---|---|---:|---|---|---|",
        ]
    )
    for sample in line["samples"]:
        lines.append(
            f"| {sample['index']} | {sample['x']} | {sample['y']} | "
            f"`{sample['referenceRgba']}` | `{sample['currentRgba']}` | "
            f"{sample['sampleResidual']} | `{sample['candidatePolicyRgbaStatus']}` | "
            f"`{sample['sourceRawRgbaStatus']}` | `{sample['premulBlendAssumptionStatus']}` |"
        )
    lines.extend(
        [
            "",
            "## Non-objectifs respectes",
            "",
            "- Aucun changement renderer.",
            "- Aucun changement GPU/WGSL, geometrie, couverture, fallback, Kadre,",
            "  runtime F16 premul/blend ou `SkBitmap.getPixel`.",
            "- Aucun score augmente, seuil modifie, promotion ou statut de support.",
            "- Aucune branche renderer par scene, coordonnee, selected-cell, fixture-only path",
            "  ou full-GM crop.",
            "",
            "## Artefacts",
            "",
            f"- JSON: `{rel(ARTIFACT)}`",
            "- Validateur: `scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`",
            f"- Rapport: `{rel(REPORT)}`",
            "",
            "## Validation",
            "",
        ]
    )
    lines.extend(f"- `{command}`" for command in VALIDATION_COMMANDS)
    return "\n".join(lines) + "\n"


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("allowedClassifications") == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")

    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for365Decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    require(inputs.get("for366Decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    require(inputs.get("for367Decision") == FOR367_REQUIRED_DECISION, "FOR-367 decision changed")
    require(inputs.get("for367Classification") == FOR367_REQUIRED_CLASSIFICATION, "FOR-367 classification changed")
    require(inputs.get("for367RowId") == ROW_ID, "FOR-367 row changed")
    require(inputs.get("for367CurrentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")
    require(inputs.get("for368Decision") == FOR368_REQUIRED_DECISION, "FOR-368 decision changed")
    require(inputs.get("for368Classification") == FOR368_REQUIRED_CLASSIFICATION, "FOR-368 classification changed")
    require(inputs.get("for368CurrentResidual") == REQUIRED_RESIDUAL, "FOR-368 residual changed")

    source_artifacts = data.get("sourceArtifacts")
    require(isinstance(source_artifacts, dict), "source artifact probe missing")
    presence = source_artifacts.get("jsonKeyPresence")
    require(isinstance(presence, dict), "source artifact key presence missing")
    for artifact_name, keys in presence.items():
        require(isinstance(keys, dict), f"{artifact_name} key presence must be object")
        require(all(value is False for value in keys.values()), f"{artifact_name} exposes missing metadata")
    require(source_artifacts.get("fallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold",
            "fallback reason changed")

    producer = data.get("captureProducerInspection")
    require(isinstance(producer, dict), "capture producer inspection missing")
    require(producer.get("producer") == rel(CAPTURE_PRODUCER), "capture producer path changed")
    require(producer.get("producerMentionsCandidatePolicy") is False, "producer unexpectedly mentions candidate")

    line = data.get("probeLine")
    require(isinstance(line, dict), "probe line missing")
    require(line.get("rowId") == ROW_ID, "probe row changed")
    require(line.get("familyId") == FAMILY_ID, "probe family changed")
    require(line.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(line.get("currentResidual") == REQUIRED_RESIDUAL, "residual changed")
    require(line.get("computedResidual") == REQUIRED_RESIDUAL, "computed residual changed")
    require(line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    require(line.get("sampleCoordinatesPreservedFromFor367") is True, "coordinates not preserved")
    require(line.get("referenceCurrentCandidateComparable") is False, "candidate comparability changed")
    require(line.get("classification") == CLASSIFICATION, "probe classification changed")
    require(line.get("readyForCandidateEvaluation") is False, "probe unexpectedly ready")
    blocker = line.get("blocker")
    require(isinstance(blocker, dict), "blocker missing")
    require(blocker.get("status") == CLASSIFICATION, "blocker classification changed")
    require(isinstance(blocker.get("point"), str) and blocker["point"], "blocker point missing")

    samples = line.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples changed")
    residual = 0
    for index, sample in enumerate(samples, start=1):
        require(isinstance(sample, dict), "sample must be object")
        require(sample.get("index") == index, "sample index changed")
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "sample coordinate changed")
        reference = rgba(sample.get("referenceRgba"), "sample referenceRgba")
        current = rgba(sample.get("currentRgba"), "sample currentRgba")
        require(sample.get("absoluteChannelDelta") == abs_delta(reference, current), "sample delta changed")
        expected_residual = sample_residual(reference, current)
        require(sample.get("sampleResidual") == expected_residual, "sample residual changed")
        residual += expected_residual
        require(sample.get("candidatePolicyRgba") is None, "candidate value unexpectedly present")
        require(sample.get("candidatePolicyRgbaStatus") == CANDIDATE_STATUS, "candidate status changed")
        require(sample.get("sourceRawRgba") is None, "source raw value unexpectedly present")
        require(sample.get("sourceRawRgbaStatus") == SOURCE_STATUS, "source raw status changed")
        require(sample.get("sourceCoverage") is None, "source coverage unexpectedly present")
        require(sample.get("sourceColor") is None, "source color unexpectedly present")
        require(sample.get("premulBlendAssumptionStatus") == PREMUL_BLEND_STATUS, "premul status changed")
        require(sample.get("artifactOnlyValueProduced") is False, "artifact value unexpectedly produced")
        require(sample.get("rendererAppliedCandidate") is False, "candidate applied")
        require(sample.get("readyForCandidateEvaluation") is False, "sample unexpectedly ready")
    require(residual == REQUIRED_RESIDUAL, "sample residual total changed")

    readiness = data.get("candidateProbeReadiness")
    require(isinstance(readiness, dict), "candidate probe readiness missing")
    require(readiness.get("classification") == CLASSIFICATION, "readiness classification changed")
    require(readiness.get("readyForCandidateEvaluation") is False, "readiness changed")
    require(readiness.get("candidatePolicyRgbaProduced") is False, "candidate produced flag changed")
    require(readiness.get("artifactOnlyCandidateValuesProduced") is False, "artifact value flag changed")
    require(readiness.get("ambiguousCoverageRefusal") is False, "ambiguous coverage flag changed")
    require(isinstance(readiness.get("blockingCapturePath"), str) and readiness["blockingCapturePath"],
            "blocking capture path missing")

    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteria evaluation missing")
    for key, expected in (
        ("sourceMemoryRecorded", True),
        ("sourceFindingRecorded", True),
        ("for368DecisionRequired", True),
        ("for368ClassificationRequired", True),
        ("sameTenCoordinatesPreserved", True),
        ("currentResidualKeptAt856", True),
        ("sourceCapturePathInspected", True),
        ("candidatePolicyRgbaStatusRecordedForEachSample", True),
        ("sourceRawRgbaStatusRecordedForEachSample", True),
        ("premulBlendAssumptionStatusRecordedForEachSample", True),
        ("blockingCapturePointRecorded", True),
        ("noValuesInvented", True),
        ("rendererBehaviorChanged", False),
        ("scoreIncreased", False),
        ("thresholdChanged", False),
        ("candidateImplementationAuthorized", False),
    ):
        require(criteria.get(key) is expected, f"criteria changed: {key}")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    require(all(value is False for value in non_goals.values()), "a non-goal was not preserved")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("evidenceOnly") is True, "FOR-369 is no longer evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def main() -> None:
    data = build_artifact()
    json_text = json.dumps(data, indent=2, sort_keys=False) + "\n"
    report_text = render_report(data)
    write_if_changed(ARTIFACT, json_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    require(REPORT.read_text(encoding="utf-8") == report_text, "report is stale")
    print(
        f"{DECISION}: row={ROW_ID} residual={REQUIRED_RESIDUAL} "
        f"classification={CLASSIFICATION}"
    )


if __name__ == "__main__":
    main()
