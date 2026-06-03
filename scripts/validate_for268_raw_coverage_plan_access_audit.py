#!/usr/bin/env python3
"""Validate FOR-268 raw coverage plan access audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "raw-coverage-plan-access-audit-for268.json"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/raw-coverage-plan-access-audit-for268"
    / PROBE_NAME
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-268-raw-coverage-plan-access-audit.md"
CONTRACTS = PROJECT_ROOT / "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
HARNESS = PROJECT_ROOT / "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageMigrationHarness.kt"
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
SELECTOR = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"
SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"
FOR267_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"

DECISION = "RAW_COVERAGE_CAPTURE_NOT_AVAILABLE"
FALLBACK_REASON = "coverage.stroke-cap-join-visual-parity-below-threshold"
CROP_FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
EXPERIMENTAL_PROPERTY = "kanvas.webgpu.strokeCapJoin.experimentalRender"
MISSING_CONDITION = "missing_raw_cpu_gpu_coverage_plane_capture_for_round_cap_join_boundary_cells"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-268 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_bool(owner: dict[str, Any], field: str, expected: bool) -> None:
    actual = owner.get(field)
    if actual is not expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def entry_by_module(probe: dict[str, Any], module: str) -> dict[str, Any]:
    entries = probe.get("auditedEntryPoints")
    if not isinstance(entries, list):
        fail("missing auditedEntryPoints array")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("module") == module:
            return entry
    fail(f"missing audited entry `{module}`")


def validate_entry(
    probe: dict[str, Any],
    *,
    module: str,
    access: str,
    path_fragment: str,
    finding_fragment: str,
) -> None:
    entry = entry_by_module(probe, module)
    require_text(entry, "rawCoverageAccess", access)
    path = entry.get("path")
    if not isinstance(path, str) or path_fragment not in path:
        fail(f"`{module}` path must contain `{path_fragment}`, got `{path}`")
    finding = entry.get("finding")
    if not isinstance(finding, str) or finding_fragment not in finding:
        fail(f"`{module}` finding must contain `{finding_fragment}`")


def validate_probe() -> dict[str, Any]:
    probe = load_json(ARTIFACT)
    require_text(probe, "backend", "CPU/WebGPU")
    require_text(probe, "linear", "FOR-268")
    require_text(probe, "parent", "FOR-241")
    require_text(probe, "probe", "raw-coverage-plan-access-audit")
    require_text(probe, "sceneId", "m60-bounded-stroke-cap-join")
    require_text(probe, "decision", DECISION)
    require_bool(probe, "rawCoverageCaptureAvailable", False)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "productionRoute", "webgpu.coverage.refuse")
    require_text(probe, "diagnosticRoute", "webgpu.coverage.stroke-cap-join.experimental-render")
    require_text(probe, "experimentalRenderProperty", EXPERIMENTAL_PROPERTY)
    require_text(probe, "fallbackReason", FALLBACK_REASON)
    require_text(probe, "preservedUnsupportedReason", CROP_FALLBACK_REASON)
    require_text(probe, "remainingBoundary", "coverage.stroke-cap-join-aa-residual")
    require_text(probe, "nextMissingCondition", MISSING_CONDITION)

    for field in (
        "normalRenderingChanged",
        "normalShadersChanged",
        "normalThresholdsChanged",
        "cropPolicyChanged",
        "fallbackPolicyChanged",
        "targetColorSpaceBlendGloballyEnabled",
        "productionStrokePromotionChanged",
        "cpuReadbackFallbackAdded",
    ):
        require_bool(probe, field, False)

    validate_entry(
        probe,
        module="CoveragePlan",
        access="not-available",
        path_fragment="GeometryCoverageContracts.kt",
        finding_fragment="semantic descriptor",
    )
    validate_entry(
        probe,
        module="PathCoverage",
        access="not-available",
        path_fragment="GeometryCoverageContracts.kt",
        finding_fragment="CpuSpanPath",
    )
    validate_entry(
        probe,
        module="GeometryCoverageMigrationHarness",
        access="cpu-test-input-only",
        path_fragment="GeometryCoverageMigrationHarness.kt",
        finding_fragment="coverageAlpha",
    )
    validate_entry(
        probe,
        module="CPU raster",
        access="not-available",
        path_fragment="SkBitmapDevice.kt",
        finding_fragment="no public test-only snapshot API",
    )
    validate_entry(
        probe,
        module="aa_stencil_cover.wgsl",
        access="shader-local-only",
        path_fragment="aa_stencil_cover.wgsl",
        finding_fragment="supersampled_path_cov",
    )
    validate_entry(
        probe,
        module="SkWebGpuDevice",
        access="not-available",
        path_fragment="SkWebGpuDevice.kt",
        finding_fragment="experimental property only permits rendering",
    )
    validate_entry(
        probe,
        module="WebGpuCoveragePlanSelector",
        access="not-available",
        path_fragment="WebGpuCoveragePlanSelector.kt",
        finding_fragment="stroke style facts",
    )
    validate_entry(
        probe,
        module=EXPERIMENTAL_PROPERTY,
        access="diagnostic-render-only",
        path_fragment="SkWebGpuDevice.kt",
        finding_fragment="diagnostic-only",
    )

    cpu = probe.get("cpuFacts")
    if not isinstance(cpu, dict):
        fail("missing cpuFacts")
    require_bool(cpu, "rawCoverageCellsAvailable", False)
    require_text(cpu, "coveragePlan", "PathCoverage(fillType=Winding,aa=true,inverse=false)")
    if "byte-derived coverage proxies" not in cpu.get("currentEvidenceKind", ""):
        fail("cpuFacts.currentEvidenceKind must name byte-derived coverage proxies")

    gpu = probe.get("webgpuFacts")
    if not isinstance(gpu, dict):
        fail("missing webgpuFacts")
    require_bool(gpu, "rawCoverageCellsAvailable", False)
    require_text(gpu, "coverageShader", "aa_stencil_cover.wgsl")
    if "fragment-local float" not in gpu.get("limitation", ""):
        fail("webgpuFacts.limitation must name fragment-local coverage")

    missing = probe.get("missingInstrumentation")
    if not isinstance(missing, list) or len(missing) != 4:
        fail("expected exactly four missingInstrumentation records")
    missing_ids = [item.get("id") for item in missing]
    expected_ids = [
        "cpu-raw-coverage-extractor",
        "webgpu-raw-coverage-output",
        "shared-cell-sampling-schema",
        "coverage-plane-provenance",
    ]
    if missing_ids != expected_ids:
        fail(f"missingInstrumentation order expected {expected_ids}, got {missing_ids}")

    next_option = probe.get("nextRealisticOption")
    if not isinstance(next_option, dict):
        fail("missing nextRealisticOption")
    require_text(next_option, "decision", "future-ticket")
    require_text(next_option, "productionBehaviorRequired", "unchanged")
    require_text(
        probe,
        "noKotlinTestReason",
        "A Kotlin render test would currently reproduce FOR-266/FOR-267 byte/proxy evidence. Because raw capture is unavailable, a validator plus stable JSON/report is the useful FOR-268 evidence.",
    )
    return probe


def validate_sources() -> None:
    require_file_text(
        CONTRACTS,
        [
            "data class PathCoverage(val fillType: PathFillType, val aa: Boolean, val inverse: Boolean)",
            "CoverageBackendStrategy.CpuSpanPath",
        ],
    )
    require_file_text(
        HARNESS,
        [
            "comparePathCoverageAgainstOracle",
            "coverageAlpha: ByteArray",
            "pathCoveragePixels(width, height, color, coverageAlpha, fixture)",
        ],
    )
    device_text = require_file_text(
        DEVICE,
        [
            EXPERIMENTAL_PROPERTY,
            "coverageSelectionDiagnosticsForTests",
            "selectCoveragePlanForDraw",
            "StandardCoverageReason.StrokeCapJoinVisualParityBelowThreshold",
        ],
    )
    for forbidden in (
        "FOR268",
        "for268",
        "kanvas.webgpu.for268",
        "rawCoverageCapture",
        "coveragePlaneReadback",
        "targetColorSpaceBlend: Boolean = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-268 must not add production switches or raw capture code: `{forbidden}`")
    require_file_text(
        SELECTOR,
        [
            "facts.hasStrokeStyleFacts",
            "StandardCoverageReason.StrokeCapJoinVisualParityBelowThreshold",
            "pathAaStrokeCapJoinBlocked",
        ],
    )
    require_file_text(
        SHADER,
        [
            "fn supersampled_path_cov",
            "coverage = coverage * clip_cov",
            "return quantize_rgba8_if_target_blend(vec4f(c.rgb * alpha, alpha));",
        ],
    )
    require_file_text(
        FOR267_REPORT,
        [
            "Decision: `KEEP_DIAGNOSTIC`",
            "coverage.stroke-cap-join-visual-parity-below-threshold",
            "byte-derived coverage proxies",
            "raw CPU/GPU coverage-plane equivalence is not proven",
        ],
    )


def validate_report() -> None:
    require_file_text(
        REPORT,
        [
            "FOR-268 Raw Coverage Plan Access Audit",
            f"Decision: `{DECISION}`",
            "Preserved production decision: `KEEP_DIAGNOSTIC`",
            FALLBACK_REASON,
            CROP_FALLBACK_REASON,
            "No Kotlin test was added for FOR-268",
            "rtk python3 scripts/validate_for268_raw_coverage_plan_access_audit.py",
        ],
    )


def main() -> None:
    validate_probe()
    validate_sources()
    validate_report()
    print(
        "FOR-268 validation passed: raw CPU/WebGPU coverage capture is marked "
        f"{DECISION}, production diagnostics are preserved, and the audit names "
        "the missing instrumentation and next option."
    )


if __name__ == "__main__":
    main()
