#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

import validate_gpu_renderer_r6_pm_evidence_bundle as root_pm_validator
import validate_gpu_renderer_r6_executed_pm_evidence_bundle as executed_pm_validator


DEFAULT_PM_BUNDLE_DIR = Path("build/reports/wgsl-pipeline-pm-bundle")
DEFAULT_EXECUTED_SUMMARY = Path(
    "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence/"
    "diagnostic-webgpu-first-route-pm-evidence-summary.json"
)
DEFAULT_REPORT = Path("reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md")
DEFAULT_ENTRY_RELATIVE_PATH = Path(
    "release/gpu-renderer-r6-first-route-pm-evidence/pm-bundle-manifest-entry.json"
)
ROOT_DEFAULT_KEY = "gpuRendererR6FirstRoutePmEvidence"
EXECUTED_KEY = "gpuRendererR6ExecutedFirstRoutePmEvidence"
EXPECTED_ROOT_VALIDATION_REPORT = "gpu-renderer-first-route-pm-evidence"
EXPECTED_ROOT_ARTIFACT_DIRECTORY = "release/gpu-renderer-r6-first-route-pm-evidence"
EXPECTED_ROOT_MANIFEST_ARTIFACT = (
    "release/gpu-renderer-r6-first-route-pm-evidence/"
    "gpu-renderer-first-route-pm-evidence-00-manifest.txt"
)
EXPECTED_ROOT_MANIFEST_ENTRY_JSON = (
    "release/gpu-renderer-r6-first-route-pm-evidence/pm-bundle-manifest-entry.json"
)
EXPECTED_ROOT_GENERATION_COMMAND = (
    "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererR6FirstRoutePmEvidenceBundle"
)
EXPECTED_ROOT_PM_PACKAGE_COMMAND = "rtk ./gradlew --no-daemon pipelinePmBundle"
EXPECTED_ROOT_ACTIVATION_DECISION_REF = "reports/gpu-renderer/2026-06-14-m1-promotion-policy-decision.md"
EXPECTED_ROOT_PACKAGING_STATE = "activation-candidate"
EXPECTED_ROOT_ADAPTER_EVIDENCE_PROVENANCE = "opt-in-adapter-backed-r6-executed-diagnostic"
EXPECTED_ROOT_ADAPTER_EVIDENCE_REQUIREMENT = "required-before-product-activation"
EXPECTED_ROOT_NON_CLAIMS = [
    "No product route activation.",
    "No WebGPU adapter, Kadre window, or native demo requirement for this bundle.",
    "Activation-candidate PM packaging is not a first-route support claim.",
    "No hidden CPU-rendered texture fallback.",
]
EXPECTED_ROOT_NOTICE = (
    "The GPU renderer R6 bundle packages validation-owned first-route PM evidence as an M1 activation candidate. "
    "The default artifact remains refusal-first and incomplete; this is PM packaging, "
    "not product route activation."
)
EXPECTED_EXECUTED_VALIDATION_REPORT = "diagnostic-webgpu-first-route-pm-evidence"
EXPECTED_EXECUTED_ARTIFACT_DIRECTORY = (
    "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
)
EXPECTED_EXECUTED_MANIFEST_ARTIFACT = (
    "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence/"
    "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"
)
EXPECTED_EXECUTED_ARTIFACT_NAMES = [
    "diagnostic-webgpu-first-route-pm-evidence-01-command.txt",
    "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt",
    "diagnostic-webgpu-first-route-pm-evidence-04-material.txt",
    "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt",
    "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt",
    "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt",
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt",
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt",
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt",
    "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt",
    "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt",
    "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt",
    "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt",
    "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt",
]
EXPECTED_EXECUTED_GENERATION_COMMAND = (
    "rtk ./gradlew --no-daemon :gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle"
)
EXPECTED_EXECUTED_VALIDATION_COMMAND = (
    "rtk python3 scripts/validate_gpu_renderer_r6_executed_pm_evidence_bundle.py . "
    "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
)
EXPECTED_EXECUTED_NON_CLAIMS = [
    "No product route activation.",
    "No root pipelinePmBundle dependency on adapter-backed evidence.",
    "No release readiness movement from this diagnostic lane.",
    "No raw readback payloads, backend handles, or WGSL source are exported.",
]
EXPECTED_EXECUTED_NOTICE = (
    "The executed GPU renderer R6 bundle is adapter-backed diagnostic PM evidence. "
    "It proves one opt-in WebGPU first-route submit/readback path for review, "
    "but it is not root PM packaging and not product support activation."
)
EXPECTED_DEFAULT_MISSING_EVIDENCE = [
    "route",
    "resource-decision",
    "submission",
    "readback",
    "pipeline-cache",
]
ROOT_DEFAULT_ENTRY_FIELDS = {
    "key",
    "claimLevel",
    "status",
    "packagingState",
    "validationReportStatus",
    "activationDecisionRef",
    "adapterEvidenceProvenance",
    "adapterEvidenceRequirement",
    "promotionGate",
    "promotionGatePassed",
    "missingEvidence",
    "validationReportName",
    "artifactDirectory",
    "manifestArtifact",
    "manifestEntryJson",
    "artifactCount",
    "dumpArtifactCount",
    "generationCommand",
    "pmPackageCommand",
    "productRouteActivated",
    "nativeKadreCiRequired",
    "webGpuAdapterRequired",
    "releaseBlocking",
    "readinessDelta",
    "nonClaims",
    "notice",
}
EXECUTED_SUMMARY_FIELDS = {
    "key",
    "claimLevel",
    "status",
    "validationReportName",
    "promotionGate",
    "promotionGatePassed",
    "missingEvidence",
    "scope",
    "artifactDirectory",
    "manifestArtifact",
    "artifactCount",
    "dumpArtifactCount",
    "generationCommand",
    "validationCommand",
    "productRouteActivated",
    "rootPipelinePmBundleDependency",
    "nativeKadreCiRequired",
    "webGpuAdapterRequired",
    "releaseBlocking",
    "readinessDelta",
    "nonClaims",
    "notice",
    "manifestSha256",
    "artifactHashes",
}
ROOT_PM_FORBIDDEN_EXECUTED_PATH_MARKERS = [
    "gpu-renderer-r6-executed-first-route-pm-evidence",
    "diagnostic-webgpu-first-route-pm-evidence",
    "gpuRendererR6ExecutedFirstRoutePmEvidence",
]
ROOT_PM_FORBIDDEN_EXECUTED_CONTENT_MARKERS = [
    b"gpu-renderer-r6-executed-diagnostic-pm-evidence-only",
    b"gpuRendererR6ExecutedFirstRoutePmEvidence",
    b"gpu-renderer-r6-executed-first-route-pm-evidence",
    b"diagnostic-webgpu-first-route-pm-evidence",
    b"recording.webgpu-submit",
    b"routing:GPURouteDecision.Native:",
    b"resources:GPUResourceMaterializationDecision.Materialized:",
    b"execution:GPUCommandSubmission.Submitted:",
    b"execution:GPUReadbackResult.Completed:",
    b"telemetry:GPUCacheTelemetry.pipeline:",
    b"rootPipelinePmBundleDependency",
    b"webgpu.first-route",
]
ROOT_PM_FORBIDDEN_TOP_LEVEL_CLAIM_FIELDS = {
    "productRouteActivated",
    "releaseBlocking",
    "readinessDelta",
    "promotionGatePassed",
    "supportClaim",
    "nativeKadreCiRequired",
    "webGpuAdapterRequired",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"GPU renderer R6 promotion readiness boundary validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


class DuplicateJSONKeyError(ValueError):
    pass


def reject_duplicate_json_object_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for key, value in pairs:
        if key in payload:
            raise DuplicateJSONKeyError(f"duplicate JSON key: {key}")
        payload[key] = value
    return payload


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON evidence file: {path}")
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_json_object_keys,
        )
    except DuplicateJSONKeyError as exc:
        fail(f"{path}: {exc}")
    require(isinstance(value, dict), f"JSON evidence file must be an object: {path}")
    return value


def load_key_value_fields(path: Path) -> dict[str, str]:
    require(path.is_file(), f"missing executed manifest: {path}")
    fields: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        require("=" in line, f"executed manifest line must be key=value: {line}")
        key, value = line.split("=", 1)
        fields[key] = value
    return fields


def sha256_file(path: Path) -> str:
    require(path.is_file(), f"missing hash input file: {path}")
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def require_bool(payload: dict[str, Any], field: str, expected: bool, owner: str) -> None:
    require(payload.get(field) is expected, f"{owner}.{field} must be {str(expected).lower()}")


def require_float(payload: dict[str, Any], field: str, expected: float, owner: str) -> None:
    value = payload.get(field)
    require(isinstance(value, int | float) and not isinstance(value, bool), f"{owner}.{field} must be numeric")
    require(float(value) == expected, f"{owner}.{field} must be {expected}")


def require_equal(payload: dict[str, Any], field: str, expected: Any, owner: str) -> None:
    require(payload.get(field) == expected, f"{owner}.{field} changed")


def require_exact_fields(payload: dict[str, Any], expected_fields: set[str], owner: str) -> None:
    actual_fields = set(payload.keys())
    unexpected = sorted(actual_fields - expected_fields)
    missing = sorted(expected_fields - actual_fields)
    require(not unexpected, f"{owner} has unexpected fields: {', '.join(unexpected)}")
    require(not missing, f"{owner} missing fields: {', '.join(missing)}")


def json_strings(value: Any) -> list[str]:
    if isinstance(value, dict):
        strings: list[str] = []
        for key, child in value.items():
            strings.append(str(key))
            strings.extend(json_strings(child))
        return strings
    if isinstance(value, list):
        return [item for child in value for item in json_strings(child)]
    if isinstance(value, str):
        return [value]
    return []


def validate_root_default_entry(entry: dict[str, Any]) -> dict[str, Any]:
    require_exact_fields(entry, ROOT_DEFAULT_ENTRY_FIELDS, "rootDefaultBundle")
    require(entry.get("key") == ROOT_DEFAULT_KEY, "root default PM evidence key changed")
    require(
        entry.get("claimLevel") == "gpu-renderer-r6-pm-evidence-only",
        "root default PM evidence claimLevel changed",
    )
    require(
        entry.get("status") == "ActivationCandidate",
        "root default PM evidence must remain ActivationCandidate",
    )
    require(
        entry.get("packagingState") == EXPECTED_ROOT_PACKAGING_STATE,
        "root default PM packaging state changed",
    )
    require(
        entry.get("validationReportStatus") == "Incomplete",
        "root default PM validation report status must remain Incomplete",
    )
    require(
        entry.get("activationDecisionRef") == EXPECTED_ROOT_ACTIVATION_DECISION_REF,
        "root default PM activation decision reference changed",
    )
    require(
        entry.get("adapterEvidenceProvenance") == EXPECTED_ROOT_ADAPTER_EVIDENCE_PROVENANCE,
        "root default PM adapter evidence provenance changed",
    )
    require(
        entry.get("adapterEvidenceRequirement") == EXPECTED_ROOT_ADAPTER_EVIDENCE_REQUIREMENT,
        "root default PM adapter evidence requirement changed",
    )
    require(entry.get("promotionGate") == "first-route-promotion", "root default promotion gate changed")
    require_bool(entry, "promotionGatePassed", False, "rootDefaultBundle")
    require(
        entry.get("missingEvidence") == EXPECTED_DEFAULT_MISSING_EVIDENCE,
        "root default missing evidence set changed",
    )
    require_equal(entry, "validationReportName", EXPECTED_ROOT_VALIDATION_REPORT, "rootDefaultBundle")
    require_equal(entry, "artifactDirectory", EXPECTED_ROOT_ARTIFACT_DIRECTORY, "rootDefaultBundle")
    require_equal(entry, "manifestArtifact", EXPECTED_ROOT_MANIFEST_ARTIFACT, "rootDefaultBundle")
    require_equal(entry, "manifestEntryJson", EXPECTED_ROOT_MANIFEST_ENTRY_JSON, "rootDefaultBundle")
    require_equal(entry, "artifactCount", 15, "rootDefaultBundle")
    require_equal(entry, "dumpArtifactCount", 14, "rootDefaultBundle")
    require_equal(entry, "generationCommand", EXPECTED_ROOT_GENERATION_COMMAND, "rootDefaultBundle")
    require_equal(entry, "pmPackageCommand", EXPECTED_ROOT_PM_PACKAGE_COMMAND, "rootDefaultBundle")
    require_equal(entry, "nonClaims", EXPECTED_ROOT_NON_CLAIMS, "rootDefaultBundle")
    require_equal(entry, "notice", EXPECTED_ROOT_NOTICE, "rootDefaultBundle")
    require_bool(entry, "productRouteActivated", False, "rootDefaultBundle")
    require_bool(entry, "nativeKadreCiRequired", False, "rootDefaultBundle")
    require_bool(entry, "webGpuAdapterRequired", False, "rootDefaultBundle")
    require_bool(entry, "releaseBlocking", False, "rootDefaultBundle")
    require_float(entry, "readinessDelta", 0.0, "rootDefaultBundle")

    return {
        "key": entry["key"],
        "status": entry["status"],
        "packagingState": entry["packagingState"],
        "validationReportStatus": entry["validationReportStatus"],
        "promotionGatePassed": entry["promotionGatePassed"],
        "missingEvidence": list(entry["missingEvidence"]),
        "artifactCount": entry.get("artifactCount"),
        "dumpArtifactCount": entry.get("dumpArtifactCount"),
        "webGpuAdapterRequired": entry["webGpuAdapterRequired"],
    }


def looks_like_executed_manifest_entry(key: str, value: Any) -> bool:
    if key == EXECUTED_KEY:
        return True
    if not isinstance(value, dict):
        return False

    text_fields = [key] + json_strings(value)
    joined = "\n".join(text_fields)
    return (
        "gpuRendererR6ExecutedFirstRoutePmEvidence" in joined or
        "gpu-renderer-r6-executed-diagnostic-pm-evidence-only" in joined or
        "gpu-renderer-r6-executed-first-route-pm-evidence" in joined or
        "diagnostic-webgpu-first-route-pm-evidence" in joined or
        "gpuRendererR6ExecutedFirstRoutePmEvidenceBundle" in joined or
        "validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle" in joined or
        "validate_gpu_renderer_r6_executed_pm_evidence_bundle.py" in joined
    )


def validate_root_pm_manifest(pm_bundle_dir: Path, entry: dict[str, Any]) -> None:
    manifest_path = pm_bundle_dir / "manifest.json"
    manifest = load_json(manifest_path)
    require(manifest.get("generatedBy") == "pipelinePmBundle", "root PM manifest must be generated by pipelinePmBundle")
    top_level_claims = sorted(ROOT_PM_FORBIDDEN_TOP_LEVEL_CLAIM_FIELDS & set(manifest.keys()))
    require(
        not top_level_claims,
        "root PM manifest top-level claim fields are forbidden: " + ", ".join(top_level_claims),
    )
    require(ROOT_DEFAULT_KEY in manifest, f"root PM manifest must contain {ROOT_DEFAULT_KEY}")
    executed_entries = [
        key for key, value in manifest.items()
        if looks_like_executed_manifest_entry(key, value)
    ]
    require(
        not executed_entries,
        "root PM manifest must not contain adapter-backed executed evidence: " +
        ", ".join(sorted(executed_entries)),
    )

    manifest_entry = manifest.get(ROOT_DEFAULT_KEY)
    require(isinstance(manifest_entry, dict), "root PM manifest default entry must be an object")
    validate_root_default_entry(manifest_entry)
    compared_fields = [
        "key",
        "claimLevel",
        "status",
        "packagingState",
        "validationReportStatus",
        "activationDecisionRef",
        "adapterEvidenceProvenance",
        "adapterEvidenceRequirement",
        "promotionGate",
        "promotionGatePassed",
        "missingEvidence",
        "validationReportName",
        "artifactDirectory",
        "manifestArtifact",
        "manifestEntryJson",
        "artifactCount",
        "dumpArtifactCount",
        "generationCommand",
        "pmPackageCommand",
        "productRouteActivated",
        "nativeKadreCiRequired",
        "webGpuAdapterRequired",
        "releaseBlocking",
        "readinessDelta",
        "nonClaims",
        "notice",
    ]
    for field in compared_fields:
        require(
            manifest_entry.get(field) == entry.get(field),
            f"root PM manifest and release entry disagree on {field}",
        )


def validate_root_pm_release_copy(pm_bundle_dir: Path, entry: dict[str, Any]) -> None:
    output_dir = pm_bundle_dir / root_pm_validator.RELEASE_DIR
    try:
        _, rows, generated_entry = root_pm_validator.validate_output(output_dir)
    except root_pm_validator.ValidationError as exc:
        fail(f"root PM evidence release copy is stale or invalid: {exc}")

    expected_paths = {
        root_pm_validator.MANIFEST_ARTIFACT,
        root_pm_validator.OUTPUT_MANIFEST_ENTRY,
        *(str(row["name"]) for row in rows),
    }
    actual_paths = {
        str(path.relative_to(output_dir)).replace("\\", "/")
        for path in output_dir.rglob("*")
        if path.is_file()
    }
    require(
        actual_paths == expected_paths,
        "unexpected root PM evidence release copy paths: " +
        ", ".join(sorted((actual_paths - expected_paths) | (expected_paths - actual_paths))),
    )

    compared_fields = [
        "key",
        "claimLevel",
        "status",
        "packagingState",
        "validationReportStatus",
        "activationDecisionRef",
        "adapterEvidenceProvenance",
        "adapterEvidenceRequirement",
        "promotionGate",
        "promotionGatePassed",
        "missingEvidence",
        "validationReportName",
        "artifactDirectory",
        "manifestArtifact",
        "manifestEntryJson",
        "artifactCount",
        "dumpArtifactCount",
        "generationCommand",
        "pmPackageCommand",
        "productRouteActivated",
        "nativeKadreCiRequired",
        "webGpuAdapterRequired",
        "releaseBlocking",
        "readinessDelta",
        "nonClaims",
        "notice",
    ]
    for field in compared_fields:
        require(
            generated_entry.get(field) == entry.get(field),
            f"root PM evidence release copy and manifest entry disagree on {field}",
        )


def validate_root_pm_bundle_tree(pm_bundle_dir: Path) -> None:
    forbidden_paths = []
    for path in pm_bundle_dir.rglob("*"):
        relative_path = str(path.relative_to(pm_bundle_dir)).replace("\\", "/")
        for marker in ROOT_PM_FORBIDDEN_EXECUTED_PATH_MARKERS:
            if marker in relative_path:
                forbidden_paths.append(relative_path)
                break
        if path.is_file():
            bytes_value = path.read_bytes()
            for marker in ROOT_PM_FORBIDDEN_EXECUTED_CONTENT_MARKERS:
                if marker in bytes_value:
                    forbidden_paths.append(relative_path)
                    break
    require(
        not forbidden_paths,
        "root PM bundle must not contain adapter-backed executed files: " +
        ", ".join(sorted(forbidden_paths)),
    )


def validate_executed_summary(summary: dict[str, Any]) -> dict[str, Any]:
    require_exact_fields(summary, EXECUTED_SUMMARY_FIELDS, "executedDiagnosticEvidence")
    require(summary.get("key") == EXECUTED_KEY, "executed summary key changed")
    require(
        summary.get("claimLevel") == "gpu-renderer-r6-executed-diagnostic-pm-evidence-only",
        "executed summary claimLevel changed",
    )
    require(summary.get("status") == "Passed", "executed diagnostic evidence must be Passed")
    require(summary.get("promotionGate") == "first-route-promotion", "executed promotion gate changed")
    require_bool(summary, "promotionGatePassed", True, "executedDiagnosticEvidence")
    require(summary.get("missingEvidence") == [], "executed diagnostic evidence must have no missing evidence")
    require(summary.get("scope") == "pm-evidence-only", "executed diagnostic evidence must remain pm-evidence-only")
    require_equal(summary, "validationReportName", EXPECTED_EXECUTED_VALIDATION_REPORT, "executedDiagnosticEvidence")
    require_equal(summary, "artifactDirectory", EXPECTED_EXECUTED_ARTIFACT_DIRECTORY, "executedDiagnosticEvidence")
    require_equal(summary, "manifestArtifact", EXPECTED_EXECUTED_MANIFEST_ARTIFACT, "executedDiagnosticEvidence")
    require_equal(summary, "artifactCount", 19, "executedDiagnosticEvidence")
    require_equal(summary, "dumpArtifactCount", 18, "executedDiagnosticEvidence")
    require_equal(summary, "generationCommand", EXPECTED_EXECUTED_GENERATION_COMMAND, "executedDiagnosticEvidence")
    require_equal(summary, "validationCommand", EXPECTED_EXECUTED_VALIDATION_COMMAND, "executedDiagnosticEvidence")
    require_equal(summary, "nonClaims", EXPECTED_EXECUTED_NON_CLAIMS, "executedDiagnosticEvidence")
    require_equal(summary, "notice", EXPECTED_EXECUTED_NOTICE, "executedDiagnosticEvidence")
    require(isinstance(summary.get("manifestSha256"), str), "executedDiagnosticEvidence.manifestSha256 missing")
    artifact_hashes = summary.get("artifactHashes")
    require(isinstance(artifact_hashes, dict), "executedDiagnosticEvidence.artifactHashes missing")
    require(
        sorted(artifact_hashes.keys()) == sorted(EXPECTED_EXECUTED_ARTIFACT_NAMES),
        "executedDiagnosticEvidence.artifactHashes keys changed",
    )
    require_bool(summary, "productRouteActivated", False, "executedDiagnosticEvidence")
    require_bool(summary, "rootPipelinePmBundleDependency", False, "executedDiagnosticEvidence")
    require_bool(summary, "nativeKadreCiRequired", False, "executedDiagnosticEvidence")
    require_bool(summary, "webGpuAdapterRequired", True, "executedDiagnosticEvidence")
    require_bool(summary, "releaseBlocking", False, "executedDiagnosticEvidence")
    require_float(summary, "readinessDelta", 0.0, "executedDiagnosticEvidence")

    return {
        "key": summary["key"],
        "status": summary["status"],
        "promotionGatePassed": summary["promotionGatePassed"],
        "missingEvidence": list(summary["missingEvidence"]),
        "scope": summary["scope"],
        "webGpuAdapterRequired": summary["webGpuAdapterRequired"],
        "rootPipelinePmBundleDependency": summary["rootPipelinePmBundleDependency"],
        "artifactCount": summary.get("artifactCount"),
        "dumpArtifactCount": summary.get("dumpArtifactCount"),
    }


def validate_executed_artifact_freshness(summary: dict[str, Any], summary_path: Path) -> None:
    output_dir = summary_path.parent
    manifest_path = output_dir / "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"
    require(manifest_path.is_file(), f"executed manifest is missing: {manifest_path}")
    require(
        summary["manifestSha256"] == sha256_file(manifest_path),
        "executed manifest hash mismatch",
    )

    fields = load_key_value_fields(manifest_path)
    artifact_hashes = summary["artifactHashes"]
    for index, artifact_name in enumerate(EXPECTED_EXECUTED_ARTIFACT_NAMES, start=1):
        ordinal = f"{index:02d}"
        require(
            fields.get(f"artifact.{ordinal}.name") == artifact_name,
            f"executed manifest artifact order changed at {ordinal}",
        )
        require(
            fields.get(f"artifact.{ordinal}.sha256") == artifact_hashes[artifact_name],
            f"executed manifest hash disagrees with summary for {artifact_name}",
        )
        artifact_path = output_dir / artifact_name
        require(
            sha256_file(artifact_path) == artifact_hashes[artifact_name],
            f"executed artifact hash mismatch: {artifact_name}",
        )


def validate_executed_output_dir(summary_path: Path) -> None:
    output_dir = summary_path.parent
    try:
        executed_pm_validator.validate_output(output_dir)
    except executed_pm_validator.ValidationError as exc:
        fail(f"executed diagnostic evidence is stale or invalid: {exc}")


def validate_boundary(pm_bundle_dir: Path, executed_summary_path: Path | None = None) -> dict[str, Any]:
    entry_path = pm_bundle_dir / DEFAULT_ENTRY_RELATIVE_PATH
    entry = load_json(entry_path)
    root_default = validate_root_default_entry(entry)
    validate_root_pm_manifest(pm_bundle_dir, entry)
    validate_root_pm_bundle_tree(pm_bundle_dir)
    validate_root_pm_release_copy(pm_bundle_dir, entry)

    executed_summary: dict[str, Any] | None = None
    if executed_summary_path is not None and executed_summary_path.is_file():
        executed_summary_payload = load_json(executed_summary_path)
        executed_summary = validate_executed_summary(executed_summary_payload)
        validate_executed_artifact_freshness(executed_summary_payload, executed_summary_path)
        validate_executed_output_dir(executed_summary_path)

    return {
        "classification": "promotion-boundary-held",
        "rootDefaultBundle": root_default,
        "executedDiagnosticEvidence": executed_summary or {
            "status": "absent",
            "promotionGatePassed": False,
            "webGpuAdapterRequired": True,
            "rootPipelinePmBundleDependency": False,
        },
        "productRouteActivated": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "promotionDecisionRequired": False,
        "requiredBeforeActivation": [
            "Controlled first-route product flag from KGPU-M1-003.",
            "Rollback and parity validation from KGPU-M1-004.",
            "No default product route until the flag and rollback gates are accepted.",
        ],
        "nonClaims": [
            "The root PM bundle is activation-candidate packaging with refusal-first evidence.",
            "The executed diagnostic lane is opt-in and not a root pipelinePmBundle dependency.",
            "This boundary report does not activate a product route or move readiness.",
        ],
    }


def markdown_bool(value: bool) -> str:
    return "true" if value else "false"


def write_markdown_report(result: dict[str, Any], report_path: Path) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    root = result["rootDefaultBundle"]
    executed = result["executedDiagnosticEvidence"]
    lines = [
        "# GPU Renderer R6 Promotion Readiness Boundary",
        "",
        "This report validates the boundary between the root activation-candidate PM packaging and the opt-in executed diagnostic evidence lane.",
        "",
        f"- Classification: `{result['classification']}`",
        f"- Root default bundle status: `{root['status']}`",
        f"- Root default packaging state: `{root['packagingState']}`",
        f"- Root default validation report status: `{root['validationReportStatus']}`",
        f"- Root default promotion gate passed: `{markdown_bool(root['promotionGatePassed'])}`",
        f"- Root missing evidence: `{','.join(root['missingEvidence'])}`",
        f"- Executed diagnostic status: `{executed['status']}`",
        f"- Executed diagnostic promotion gate passed: `{markdown_bool(executed['promotionGatePassed'])}`",
        f"- Executed diagnostic requires WebGPU adapter: `{markdown_bool(executed['webGpuAdapterRequired'])}`",
        f"- Executed diagnostic in root PM bundle: `{markdown_bool(executed['rootPipelinePmBundleDependency'])}`",
        f"- Product route activated: `{markdown_bool(result['productRouteActivated'])}`",
        f"- Release blocking: `{markdown_bool(result['releaseBlocking'])}`",
        f"- Readiness delta: `{result['readinessDelta']}`",
        f"- Promotion decision required: `{markdown_bool(result['promotionDecisionRequired'])}`",
        "",
        "Required Before Activation",
        "",
    ]
    lines.extend(f"- {item}" for item in result["requiredBeforeActivation"])
    lines.extend(["", "Non-Claims", ""])
    lines.extend(f"- {item}" for item in result["nonClaims"])
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the GPU renderer R6 PM evidence promotion boundary.",
    )
    parser.add_argument(
        "root",
        nargs="?",
        default=".",
        help="repository root; defaults to current directory",
    )
    parser.add_argument(
        "--pm-bundle-dir",
        default=str(DEFAULT_PM_BUNDLE_DIR),
        help="root pipelinePmBundle output directory, relative to root unless absolute",
    )
    parser.add_argument(
        "--executed-summary",
        default=str(DEFAULT_EXECUTED_SUMMARY),
        help="optional executed diagnostic summary JSON, relative to root unless absolute",
    )
    parser.add_argument(
        "--require-executed-summary",
        action="store_true",
        help="fail if the executed diagnostic summary JSON is absent",
    )
    parser.add_argument(
        "--write-report",
        nargs="?",
        const=str(DEFAULT_REPORT),
        help="write a markdown boundary report; default path is reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md",
    )
    return parser.parse_args(argv[1:])


def root_relative(root: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else root / path


def main(argv: list[str]) -> int:
    try:
        args = parse_args(argv)
        root = Path(args.root).resolve()
        try:
            executed_pm_validator.validate_root_pipeline_isolation(root)
        except executed_pm_validator.ValidationError as exc:
            fail(f"root pipeline isolation failed: {exc}")

        pm_bundle_dir = root_relative(root, args.pm_bundle_dir)
        executed_summary_path = root_relative(root, args.executed_summary)
        if args.require_executed_summary:
            require(executed_summary_path.is_file(), f"missing required executed summary: {executed_summary_path}")

        result = validate_boundary(pm_bundle_dir, executed_summary_path)
        if args.write_report is not None:
            write_markdown_report(result, root_relative(root, args.write_report))

        print(
            "GPU renderer R6 promotion readiness boundary validation passed: "
            f"classification={result['classification']}, "
            f"rootStatus={result['rootDefaultBundle']['status']}, "
            f"executedStatus={result['executedDiagnosticEvidence']['status']}, "
            f"productRouteActivated={result['productRouteActivated']}"
        )
        return 0
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
