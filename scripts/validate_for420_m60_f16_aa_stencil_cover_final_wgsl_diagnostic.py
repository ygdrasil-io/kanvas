#!/usr/bin/env python3
"""Validate FOR-420 M60 F16 final WGSL source diagnostic evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-420-m60-f16-aa-stencil-cover-final-wgsl-diagnostic.md"
)
FOR419_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419"
    / "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419.json"
)
SKWEBGPUDEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPUSINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD_GRADLE = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

GUARD = "kanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled"
EXPECTED_VARIANTS = {
    "normal-bounded-runtime-correction",
    "for412-shader-return-storage",
    "for418-storage-vs-color-target",
    "for419-storage-zero-cause",
}
EXPECTED_CLASSIFICATION = "diagnostic-final-wgsl-hooks-not-on-rendered-return-path"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-420 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def source_audit() -> dict[str, Any]:
    device = SKWEBGPUDEVICE.read_text(encoding="utf-8")
    sink = WEBGPUSINK.read_text(encoding="utf-8")
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD_GRADLE.read_text(encoding="utf-8")
    checks = {
        "guardPresent": GUARD in device,
        "guardDefaultFalse": (
            "WEBGPU_M60_F16_AA_STENCIL_COVER_FINAL_WGSL_DIAGNOSTIC_FLAG" in device
            and 'WEBGPU_M60_F16_AA_STENCIL_COVER_FINAL_WGSL_DIAGNOSTIC_FLAG,\n            "false"' in device
        ),
        "recordsBeforeCreateShaderModule": (
            "recordM60F16AaStencilCoverFinalWgslSource(cacheKey, wgsl)"
            in device
            and "createShaderModule(ShaderModuleDescriptor(code = wgsl))" in device
        ),
        "snapshotForcesExpectedSources": (
            "ensureM60F16AaStencilCoverFinalWgslDiagnosticSources()" in device
            and "m60F16BoundedRuntimeCorrectionShader" in device
            and "m60F16AaStencilCoverShaderReturnDiagnosticShader" in device
            and "m60F16AaStencilCoverShaderReturnStorageZeroCauseShader" in device
        ),
        "snapshotExportedThroughSink": "aaStencilCoverFinalWgslDiagnosticSnapshot" in sink,
        "captureWritesFor420Artifact": SCENE_ID in capture,
        "propertyForwardedByGradle": GUARD in build,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit checks failed: {missing}")
    return {
        "runtimeOwner": rel(SKWEBGPUDEVICE),
        "testSink": rel(WEBGPUSINK),
        "captureTest": rel(CAPTURE_TEST),
        "buildFile": rel(BUILD_GRADLE),
        "checks": checks,
    }


def variants_by_name(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    variants = data.get("variants")
    require(isinstance(variants, list), "variants must be a list")
    out: dict[str, dict[str, Any]] = {}
    for variant in variants:
        require(isinstance(variant, dict), "each variant must be an object")
        name = variant.get("logicalName")
        require(isinstance(name, str) and name, "variant logicalName missing")
        require(name not in out, f"duplicate variant logicalName {name}")
        out[name] = variant
    require(set(out) == EXPECTED_VARIANTS, f"variant set mismatch: {sorted(out)}")
    return out


def function_by_name(variant: dict[str, Any], name: str) -> dict[str, Any]:
    functions = variant.get("functions")
    require(isinstance(functions, list), f"{variant.get('logicalName')} functions must be a list")
    for function in functions:
        if function.get("name") == name:
            require(isinstance(function, dict), f"{name} summary must be an object")
            return function
    fail(f"{variant.get('logicalName')} missing function {name}")


def assert_common_function_shape(variant: dict[str, Any]) -> None:
    for function_name in ("fs_inside", "fs_outside"):
        function = function_by_name(variant, function_name)
        require(function.get("present") is True, f"{variant['logicalName']} {function_name} missing")
        require(
            function.get("signatureHasLocationReturn") is True,
            f"{variant['logicalName']} {function_name} must return @location(0)",
        )
        lines = function.get("boundedNormalizedLines")
        require(isinstance(lines, list) and len(lines) <= 18, "bounded function summary too large")
        require(function.get("returnStatementCount", 0) >= 1, f"{function_name} has no return")


def assert_hashes(variants: dict[str, dict[str, Any]]) -> None:
    hashes: dict[str, str] = {}
    for name, variant in variants.items():
        source_hash = variant.get("sourceHashSha256")
        require(
            isinstance(source_hash, str)
            and len(source_hash) == 64
            and all(ch in "0123456789abcdef" for ch in source_hash),
            f"{name} sourceHashSha256 must be a lowercase SHA-256",
        )
        require(int(variant.get("sourceLengthBytes", 0)) > 1000, f"{name} source is unexpectedly small")
        hashes[name] = source_hash
    require(
        hashes["for418-storage-vs-color-target"] == hashes["for412-shader-return-storage"],
        "FOR-418 must share the FOR-412 final shader source",
    )
    require(
        hashes["for419-storage-zero-cause"] != hashes["for412-shader-return-storage"],
        "FOR-419 must have a distinct final shader source",
    )
    require(
        hashes["normal-bounded-runtime-correction"] != hashes["for412-shader-return-storage"],
        "normal bounded shader must differ from FOR-412 diagnostic source",
    )


def assert_render_path_finding(variants: dict[str, dict[str, Any]]) -> None:
    normal = variants["normal-bounded-runtime-correction"]
    assert_common_function_shape(normal)
    for function_name in ("fs_inside", "fs_outside"):
        function = function_by_name(normal, function_name)
        require(function.get("callsRecordFragmentLane") is False, "normal shader must not write storage")
        require(function.get("callsRecordApplicationPoint") is False, "normal shader must not call storage hook")
        require(function.get("returnsApplicationPointOutput") is False, "normal shader must keep direct return")

    for name in ("for412-shader-return-storage", "for418-storage-vs-color-target"):
        variant = variants[name]
        assert_common_function_shape(variant)
        require(variant.get("containsRecordApplicationPointFunction") is True, f"{name} hook function missing")
        require(variant.get("containsRecordFragmentLaneFunction") is True, f"{name} entry hook missing")
        for function_name in ("fs_inside", "fs_outside"):
            function = function_by_name(variant, function_name)
            require(function.get("callsRecordFragmentLane") is True, f"{name} should have entry storage write")
            require(
                function.get("returnsApplicationPointOutput") is False,
                f"{name} unexpectedly returns m60_f16_application_point_output",
            )
        app = variant.get("applicationPointOutput")
        require(isinstance(app, dict) and app.get("present") is True, f"{name} application helper missing")
        require(app.get("callsRecordApplicationPoint") is True, f"{name} app helper should call storage hook")
        require(
            variant.get("divergenceFromNormal") in {
                "diagnostic-return-path-not-replaced",
                "shares-for412-shader-return-source; pipeline target differs in FOR-418",
            },
            f"{name} divergence classification changed",
        )

    for419 = variants["for419-storage-zero-cause"]
    assert_common_function_shape(for419)
    require(for419.get("containsOutputNonzeroGate") is True, "FOR-419 output_nonzero gate missing")
    require(for419.get("containsRecordApplicationPointFunction") is True, "FOR-419 hook function missing")
    for function_name in ("fs_inside", "fs_outside"):
        function = function_by_name(for419, function_name)
        require(function.get("callsRecordFragmentLane") is False, "FOR-419 entry hook must be disabled")
        require(
            function.get("returnsApplicationPointOutput") is False,
            "FOR-419 final fs return should prove the application hook is not on the rendered path",
        )
    app = for419.get("applicationPointOutput")
    require(isinstance(app, dict) and app.get("callsRecordApplicationPoint") is True, "FOR-419 helper hook missing")
    require(
        for419.get("divergenceFromNormal")
        == "diagnostic-return-path-not-replaced",
        "FOR-419 divergence classification changed",
    )


def validate_for419_baseline() -> None:
    for419 = load_json(FOR419_ARTIFACT)
    require(
        for419.get("classification")
        == "application-point-storage-hook-not-on-rendered-return-path",
        "FOR-419 baseline classification changed",
    )
    summary = for419.get("summary", {})
    require(summary.get("recordCount") == 16, "FOR-419 record count changed")
    require(
        summary.get("for419ApplicationPointStorageObservedRecords") == 0,
        "FOR-419 application-point storage observation changed",
    )


def main() -> None:
    data = load_json(ARTIFACT)
    require(data.get("linear") == "FOR-420", "Linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "classification changed")
    require(data.get("supportClaim") is False, "FOR-420 must not raise support claim")
    require(data.get("promoted") is False, "FOR-420 must not promote M60 F16")
    require(data.get("defaultRenderingChanged") is False, "FOR-420 must not change default rendering")
    require(data.get("thresholdChanged") is False, "FOR-420 must not change thresholds")
    require(data.get("scoringChanged") is False, "FOR-420 must not change scoring")

    runtime = data.get("runtimeSnapshot", {})
    require(runtime.get("propertyName") == GUARD, "guard property mismatch")
    require(runtime.get("enabled") is True, "FOR-420 evidence must be opt-in enabled")
    require(runtime.get("variantCount") == 4, "variant count changed")
    require(runtime.get("missingSourceCount") == 0, "final source export missing at least one source")

    guards = data.get("guards", {})
    final_guard = guards.get("finalWgslDiagnostic", {})
    require(final_guard.get("enabledByDefault") is False, "FOR-420 guard must be disabled by default")

    policy = data.get("comparisonPolicy", {})
    require(policy.get("sourceDumpPolicy", "").startswith("No full WGSL dump"), "full WGSL dump policy changed")
    non_goals = data.get("nonGoalsPreserved", {})
    require(non_goals.get("fullWgslDumpStored") is False, "artifact must not store full WGSL dump")
    require(non_goals.get("wgsl4kModified") is False, "FOR-420 must not modify wgsl4k")
    require(non_goals.get("renderingFixApplied") is False, "FOR-420 must not fix final rendering")

    structural = data.get("structuralSummary", {})
    require(structural.get("for418UsesFor412ShaderSource") is True, "FOR-418 source sharing changed")
    require(structural.get("allVariantsHaveFsInsideAndFsOutside") is True, "entrypoint summary changed")
    require(
        structural.get("diagnosticVariantsReturnApplicationPointOutput") is False,
        "diagnostic return-path finding changed",
    )
    require(structural.get("for419HasEntryStorageWrite") is False, "FOR-419 entry storage write changed")

    variants = variants_by_name(data)
    assert_hashes(variants)
    assert_render_path_finding(variants)
    validate_for419_baseline()
    audit = source_audit()

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for needle in (
        EXPECTED_CLASSIFICATION,
        "fs_inside",
        "fs_outside",
        "createShaderModule",
        "FOR-418 partage la source FOR-412",
    ):
        require(needle in report, f"report missing {needle!r}")

    print(
        json.dumps(
            {
                "linear": "FOR-420",
                "classification": data["classification"],
                "variantCount": runtime["variantCount"],
                "sourceAudit": audit,
                "artifact": rel(ARTIFACT),
                "report": rel(REPORT),
            },
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
