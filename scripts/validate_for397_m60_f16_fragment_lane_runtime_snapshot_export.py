#!/usr/bin/env python3
"""Validate FOR-397 M60 F16 fragment-lane runtime snapshot export evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-397"
DECISION = "M60_F16_FRAGMENT_LANE_RUNTIME_SNAPSHOT_EXPORTED"
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-396-installe-un-canal-diagnostique-fragment-aa-stencil-cover-m60-f16-sans-preuve-exact-match-runtime-exportee"
)
GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
OLD_PROBE_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-fragment-lane-runtime-snapshot-export-for397"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-fragment-lane-runtime-snapshot-export-for397.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-397-m60-f16-fragment-lane-runtime-snapshot-export.md"
)
FOR396_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396/"
    "m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396.json"
)
FOR394_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394.json"
)
FOR391_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391/"
    "m60-f16-source-facing-lane-metadata-for391.json"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
WEBGPU_SINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
AA_STENCIL_COVER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_PIXELS = [
    {"x": 93, "y": 74},
    {"x": 92, "y": 75},
    {"x": 91, "y": 76},
    {"x": 17, "y": 77},
    {"x": 90, "y": 77},
    {"x": 89, "y": 78},
    {"x": 88, "y": 79},
    {"x": 87, "y": 80},
]
ALLOWED_CLASSIFICATIONS = [
    "fragment-lane-runtime-snapshot-exported",
    "fragment-lane-runtime-snapshot-mismatch",
    "fragment-lane-runtime-snapshot-empty",
]
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for397-pycache-parent "
        "python3 -m py_compile scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py"
    ),
    "rtk git diff --check",
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py",
    "rtk python3 scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py",
    "rtk python3 scripts/validate_for394_m60_f16_aa_stencil_cover_band_metadata_transport.py",
    "rtk python3 scripts/validate_for393_m60_f16_source_facing_lane_shader_metadata.py",
    "rtk python3 scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py",
    "rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
    "rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
    "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
    "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
    "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
    "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
    "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
    "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
    "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
    "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
    "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
    "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
    "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
    "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
    "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
    "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
    "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
    "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
    "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-397 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_source(path: Path) -> str:
    require(path.is_file(), f"missing source file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), f"pixel coordinate is invalid: {pixel!r}")
    return x, y


def sorted_pixels(pixels: list[dict[str, Any]]) -> list[dict[str, int]]:
    return [
        {"x": x, "y": y}
        for x, y in sorted({pixel_key(pixel) for pixel in pixels}, key=lambda item: (item[1], item[0]))
    ]


def validate_sources() -> None:
    for396 = load_json(FOR396_ARTIFACT)
    require(
        for396.get("decision") == "M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_CHANNEL_INSTALLED",
        "FOR-396 decision changed",
    )
    require(
        for396.get("classification") == "fragment-lane-diagnostic-channel-installed",
        "FOR-396 classification changed",
    )
    for396_comparison = for396.get("pixelComparison")
    require(isinstance(for396_comparison, dict), "FOR-396 pixel comparison missing")
    require(for396_comparison.get("expectedUsefulPixels") == EXPECTED_PIXELS, "FOR-396 expected pixels changed")

    for394 = load_json(FOR394_ARTIFACT)
    require(
        for394.get("decision") == "M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED",
        "FOR-394 decision changed",
    )
    require(
        for394.get("classification") == "diagnostic-transport-added-not-connected",
        "FOR-394 classification changed",
    )

    for391 = load_json(FOR391_ARTIFACT)
    require(
        for391.get("decision") == "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED",
        "FOR-391 decision changed",
    )
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixelCoordinates") == EXPECTED_PIXELS, "FOR-391 selected pixels changed")
    require(selection.get("selectedPixels") == 8, "FOR-391 selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "FOR-391 improved count changed")
    require(selection.get("regressionsIncluded") == 0, "FOR-391 regression count changed")

    capture = read_source(CAPTURE_PRODUCER)
    sink = read_source(WEBGPU_SINK)
    renderer = read_source(RENDERER)
    shader = read_source(AA_STENCIL_COVER_SHADER)
    build = read_source(GPU_RASTER_BUILD)

    for needle in (
        "writeM60F16FragmentLaneRuntimeSnapshotExport(fragmentLaneRuntimeSnapshot, adapter)",
        "m60F16FragmentLaneRuntimeSnapshotExportJson(",
        "m60F16FragmentLaneDiagnosticSnapshot()",
        "fragment-lane-runtime-snapshot-empty",
        "fragment-lane-runtime-snapshot-mismatch",
        "supportClaim\": false",
        "promoted\": false",
    ):
        require(needle in capture, f"capture source missing proof: {needle}")

    require(
        "drawWithM60F16FragmentLaneDiagnosticSnapshot" in sink
        and "device.m60F16FragmentLaneDiagnosticSnapshot()" in sink,
        "WebGpuSink must expose the test-only snapshot draw helper",
    )

    for needle in (
        f'"{GUARD}"',
        f'"{TRANSPORT_GUARD}"',
        f'"{OLD_PROBE_GUARD}"',
        "System.getProperty(\n            WEBGPU_M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_FLAG,\n            \"false\",\n        ).toBoolean()",
        "recordM60F16FragmentLaneDiagnostics(perDrawResources)",
    ):
        require(needle in renderer, f"renderer source missing proof: {needle}")

    require("m60F16FragmentLaneDiagnostic" not in shader, "default shader must not contain diagnostic storage")
    for needle in (
        f'System.getProperty("{TRANSPORT_GUARD}")?.let',
        f'systemProperty("{TRANSPORT_GUARD}", it)',
        f'System.getProperty("{GUARD}")?.let',
        f'systemProperty("{GUARD}", it)',
    ):
        require(needle in build, f"gpu-raster test task does not forward proof guard: {needle}")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("supportClaim") is False, "FOR-397 must not claim M60 support")
    require(data.get("promoted") is False, "FOR-397 must not promote M60")

    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"classification not allowed: {classification!r}")

    runtime = data.get("runtimeSnapshot")
    require(isinstance(runtime, dict), "runtime snapshot block missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16FragmentLaneDiagnosticSnapshot()", "runtime API changed")
    require(runtime.get("propertyName") == GUARD, "runtime guard property changed")
    samples = runtime.get("samples")
    require(isinstance(samples, list), "runtime samples missing")
    require(runtime.get("sampleCount") == len(samples), "runtime sample count mismatch")
    for sample in samples:
        require(isinstance(sample, dict), "runtime sample must be an object")
        pixel_key(sample)
        require(isinstance(sample.get("observedCandidateLane"), bool), "sample lane flag missing")
        require(sample.get("coverageSide") in ("inside", "outside", "unknown"), "sample coverage side changed")
        require(isinstance(sample.get("validExpectedSlot"), bool), "sample expected-slot validity missing")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards block missing")
    fragment_guard = guards.get("fragmentLaneDiagnostic")
    transport_guard = guards.get("bandMetadataTransport")
    require(isinstance(fragment_guard, dict), "fragment guard missing")
    require(isinstance(transport_guard, dict), "transport guard missing")
    require(fragment_guard.get("guardId") == GUARD, "fragment guard id changed")
    require(transport_guard.get("guardId") == TRANSPORT_GUARD, "transport guard id changed")
    require(fragment_guard.get("enabledByDefault") is False, "fragment guard default changed")
    require(transport_guard.get("enabledByDefault") is False, "transport guard default changed")

    comparison = data.get("pixelComparison")
    require(isinstance(comparison, dict), "pixel comparison missing")
    require(comparison.get("expectedUsefulPixels") == EXPECTED_PIXELS, "expected pixels changed")
    require(comparison.get("expectedUsefulPixelCount") == 8, "expected count changed")
    observed = comparison.get("shaderObservedPixels")
    false_positives = comparison.get("falsePositives")
    false_negatives = comparison.get("falseNegatives")
    require(isinstance(observed, list), "observed pixels missing")
    require(isinstance(false_positives, list), "false positives missing")
    require(isinstance(false_negatives, list), "false negatives missing")
    require(comparison.get("shaderObservedPixelCount") == len(observed), "observed count mismatch")
    require(comparison.get("falsePositiveCount") == len(false_positives), "false-positive count mismatch")
    require(comparison.get("falseNegativeCount") == len(false_negatives), "false-negative count mismatch")

    expected_set = {pixel_key(pixel) for pixel in EXPECTED_PIXELS}
    observed_set = {pixel_key(pixel) for pixel in observed}
    require(sorted_pixels(false_positives) == sorted_pixels([p for p in observed if pixel_key(p) not in expected_set]),
            "false positives are not derived from observed minus expected")
    require(sorted_pixels(false_negatives) == sorted_pixels([p for p in EXPECTED_PIXELS if pixel_key(p) not in observed_set]),
            "false negatives are not derived from expected minus observed")

    captured = bool(comparison.get("runtimeReadbackArtifactCaptured"))
    exact = bool(comparison.get("exactMatchProvenByRuntimeReadback"))
    measured = bool(comparison.get("falsePositiveFalseNegativeMeasured"))
    require(captured == measured, "captured and measured flags must agree")

    if classification == "fragment-lane-runtime-snapshot-empty":
        require(not captured, "empty snapshot must not claim captured runtime readback")
        require(not exact, "empty snapshot must not prove exact match")
        require(observed == [], "empty snapshot must not claim observed pixels")
    elif classification == "fragment-lane-runtime-snapshot-exported":
        require(captured, "exported snapshot must claim captured runtime readback")
        require(exact, "exported snapshot must prove exact match")
        require(runtime.get("sampleCount") == len(EXPECTED_PIXELS), "exported snapshot must contain exactly 8 samples")
        require(observed == EXPECTED_PIXELS, "exported snapshot must match expected pixels")
        require(false_positives == [], "exported snapshot must have no false positives")
        require(false_negatives == [], "exported snapshot must have no false negatives")
        require(all(sample.get("validExpectedSlot") is True for sample in samples), "exported samples must be valid")
    else:
        require(captured, "mismatch snapshot must still be captured")
        require(not exact, "mismatch snapshot must not prove exact match")
        require(false_positives or false_negatives or observed != EXPECTED_PIXELS, "mismatch must name a difference")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def render_report(data: dict[str, Any]) -> str:
    comparison = data["pixelComparison"]
    runtime = data["runtimeSnapshot"]
    validations = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    observed = "\n".join(
        f"- ({pixel['x']}, {pixel['y']})" for pixel in comparison["shaderObservedPixels"]
    ) or "- none"
    false_positives = "\n".join(
        f"- ({pixel['x']}, {pixel['y']})" for pixel in comparison["falsePositives"]
    ) or "- none"
    false_negatives = "\n".join(
        f"- ({pixel['x']}, {pixel['y']})" for pixel in comparison["falseNegatives"]
    ) or "- none"
    return f"""# FOR-397 M60 F16 fragment-lane runtime snapshot export

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-397 exports the runtime samples from
`m60F16FragmentLaneDiagnosticSnapshot()` into checked-in M60 F16 evidence. The
scene evidence uses the FOR-394 band metadata guard and the FOR-396 fragment
diagnostic guard. Production defaults remain disabled.

## Runtime snapshot

- API: `{runtime["api"]}`
- guard: `{runtime["propertyName"]}`
- enabled for evidence: `{runtime["enabled"]}`
- sample count: `{runtime["sampleCount"]}`
- runtime readback captured: `{comparison["runtimeReadbackArtifactCaptured"]}`
- exact match proven by runtime readback: `{comparison["exactMatchProvenByRuntimeReadback"]}`

Observed shader pixels:

{observed}

False positives:

{false_positives}

False negatives:

{false_negatives}

## Classification

{data["classificationReason"]}

Next step: {data["nextStep"]}

## Non-goals preserved

- supportClaim remains `false`
- promoted remains `false`
- no color correction, coverage, fallback, scoring, threshold, promotion, or FOR-380 route change

## Validations

{validations}
"""


def main() -> None:
    validate_sources()
    artifact = load_json(ARTIFACT)
    validate_artifact(artifact)
    REPORT.write_text(render_report(artifact), encoding="utf-8")
    print(
        "FOR-397 validation passed: "
        f"{artifact['classification']} observed="
        f"{artifact['pixelComparison']['shaderObservedPixelCount']} "
        f"falsePositives={artifact['pixelComparison']['falsePositiveCount']} "
        f"falseNegatives={artifact['pixelComparison']['falseNegativeCount']} "
        f"exactMatch={artifact['pixelComparison']['exactMatchProvenByRuntimeReadback']}"
    )


if __name__ == "__main__":
    main()
