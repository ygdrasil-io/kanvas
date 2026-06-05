#!/usr/bin/env python3
"""Validate FOR-421 M60 F16 verified return-path diagnostic evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-421-m60-f16-aa-stencil-cover-verified-return-path-diagnostic.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"

EXPECTED_VARIANTS = {
    "normal-bounded-runtime-correction",
    "for412-shader-return-storage",
    "for418-storage-vs-color-target",
    "for419-storage-zero-cause",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-421 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def variants_by_name(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    variants = data.get("variants")
    require(isinstance(variants, list), "variants must be a list")
    by_name: dict[str, dict[str, Any]] = {}
    for variant in variants:
        require(isinstance(variant, dict), "variant must be an object")
        name = variant.get("logicalName")
        require(isinstance(name, str) and name, "variant logicalName missing")
        require(name not in by_name, f"duplicate variant {name}")
        by_name[name] = variant
    require(set(by_name) == EXPECTED_VARIANTS, f"variant set mismatch: {sorted(by_name)}")
    return by_name


def assert_hash(value: Any, name: str) -> None:
    require(isinstance(value, str), f"{name} sourceHashSha256 must be a string")
    require(len(value) == 64, f"{name} sourceHashSha256 must be SHA-256 length")
    require(all(ch in "0123456789abcdef" for ch in value), f"{name} sourceHashSha256 must be lowercase hex")


def assert_source_checks() -> None:
    device = DEVICE.read_text(encoding="utf-8")
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    checks = {
        "validatedReturnBlockCount": "check(occurrences == 2)" in device,
        "validatedInstrumentedReturnCount": "check(instrumentedReturns == 2)" in device,
        "helperMethodUsed": "instrumentM60F16AaStencilCoverApplicationPointReturnPath(wgsl, cacheKey)" in device,
        "noFragileTrimIndentBlock": "val boundedReturnBlock = \"\\n    let c = m60_f16_bounded_runtime_corrected_color" not in device,
        "for421SceneWriter": SCENE_ID in capture,
        "for421SummaryCounts": "storageNonzeroSourceCount" in capture and "colorTargetNonzeroCount" in capture,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source checks failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-421", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("classification") == "verified-return-path-storage-nonzero", "classification mismatch")
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "diagnostic return path not verified")
    require(summary.get("for419EntryStorageDisabled") is True, "FOR-419 entry storage must be disabled")
    require(summary.get("storageObservedCount") == 32, "expected 32 observed storage writes")
    require(summary.get("storageNonzeroSourceCount") == 32, "expected 32 nonzero storage source writes")
    require(summary.get("colorTargetNonzeroCount") == 16, "expected 16 nonzero color-target samples")

    variants = variants_by_name(data)
    hashes = {name: variant.get("sourceHashSha256") for name, variant in variants.items()}
    for name, source_hash in hashes.items():
        assert_hash(source_hash, name)
    require(hashes["for418-storage-vs-color-target"] == hashes["for412-shader-return-storage"], "FOR-418 must share FOR-412 source")
    require(hashes["for419-storage-zero-cause"] != hashes["for412-shader-return-storage"], "FOR-419 must have distinct source")
    require(hashes["normal-bounded-runtime-correction"] != hashes["for412-shader-return-storage"], "normal source must differ")

    normal = variants["normal-bounded-runtime-correction"]
    require(normal.get("fsInsideReturnsApplicationPointOutput") is False, "normal fs_inside must not be instrumented")
    require(normal.get("fsOutsideReturnsApplicationPointOutput") is False, "normal fs_outside must not be instrumented")

    for name in ("for412-shader-return-storage", "for418-storage-vs-color-target"):
        variant = variants[name]
        require(variant.get("fsInsideReturnsApplicationPointOutput") is True, f"{name} fs_inside not instrumented")
        require(variant.get("fsOutsideReturnsApplicationPointOutput") is True, f"{name} fs_outside not instrumented")
        require(variant.get("fsInsideCallsEntryStorage") is True, f"{name} entry storage should remain enabled")
        require(variant.get("fsOutsideCallsEntryStorage") is True, f"{name} entry storage should remain enabled")

    for419 = variants["for419-storage-zero-cause"]
    require(for419.get("fsInsideReturnsApplicationPointOutput") is True, "FOR-419 fs_inside not instrumented")
    require(for419.get("fsOutsideReturnsApplicationPointOutput") is True, "FOR-419 fs_outside not instrumented")
    require(for419.get("fsInsideCallsEntryStorage") is False, "FOR-419 fs_inside entry storage must be disabled")
    require(for419.get("fsOutsideCallsEntryStorage") is False, "FOR-419 fs_outside entry storage must be disabled")
    require(for419.get("containsOutputNonzeroGate") is True, "FOR-419 output_nonzero gate missing")

    assert_source_checks()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    require("verified-return-path-storage-nonzero" in report, "report missing classification")
    require("32" in report and "16" in report, "report missing evidence counts")
    print("FOR-421 validation passed")


if __name__ == "__main__":
    main()
