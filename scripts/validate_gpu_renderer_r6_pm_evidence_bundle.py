#!/usr/bin/env python3
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "gpu-renderer/build/reports/gpu-renderer-r6-first-route-pm-evidence"
RELEASE_DIR = "release/gpu-renderer-r6-first-route-pm-evidence"
MANIFEST_ARTIFACT = "gpu-renderer-first-route-pm-evidence-00-manifest.txt"
OUTPUT_MANIFEST_ENTRY = "pm-bundle-manifest-entry.json"

EXPECTED_REPORT_NAME = "gpu-renderer-first-route-pm-evidence"
EXPECTED_MISSING_EVIDENCE_SEQUENCE = [
    "route",
    "resource-decision",
    "submission",
    "readback",
    "pipeline-cache",
]
EXPECTED_MISSING_EVIDENCE = set(EXPECTED_MISSING_EVIDENCE_SEQUENCE)
EXPECTED_ARTIFACT_NAMES = [
    "gpu-renderer-first-route-pm-evidence-01-command.txt",
    "gpu-renderer-first-route-pm-evidence-02-analysis.txt",
    "gpu-renderer-first-route-pm-evidence-03-route.txt",
    "gpu-renderer-first-route-pm-evidence-04-material.txt",
    "gpu-renderer-first-route-pm-evidence-05-wgsl.txt",
    "gpu-renderer-first-route-pm-evidence-06-payload.txt",
    "gpu-renderer-first-route-pm-evidence-07-pipeline-key.txt",
    "gpu-renderer-first-route-pm-evidence-08-resource-decision.txt",
    "gpu-renderer-first-route-pm-evidence-09-submission.txt",
    "gpu-renderer-first-route-pm-evidence-10-readback.txt",
    "gpu-renderer-first-route-pm-evidence-11-telemetry.txt",
    "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt",
    "gpu-renderer-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt",
]
EXPECTED_REPORT_DIAGNOSTICS = [
    "first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
    "route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
    "resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
    "submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
    "readback requires GPUReadbackResult.Completed",
    "pipeline-cache requires GPUCacheTelemetry.pipeline",
]
EXPECTED_GATE_DIAGNOSTICS = [
    "validation report status is Incomplete",
    "first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
    "route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
    "resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
    "submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
    "readback requires GPUReadbackResult.Completed",
    "pipeline-cache requires GPUCacheTelemetry.pipeline",
]
FORBIDDEN_CLAIM_SNIPPETS = {
    "productRouteActivated=true",
    "supported=true",
    "releaseBlocking=true",
    "nativeKadreCiRequired=true",
    "webGpuAdapterRequired=true",
    "supportClaim",
}
FORBIDDEN_CLAIM_PATTERNS = [
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?product[\s_-]*route[\s_-]*activated[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?release[\s_-]*blocking[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?native[\s_-]*kadre[\s_-]*ci[\s_-]*required[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?web[\s_-]*gpu[\s_-]*adapter[\s_-]*required[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?support[\s_-]*claim[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?supported[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
]
ROOT_ARTIFACT_CONTAMINATION_SNIPPETS = [
    "gpuRendererShadow v=1",
    "kanvas.gpu.renderer.shadow.fillRect",
    "routing:GPURouteDecision.Native:",
    "resources:GPUResourceMaterializationDecision.Materialized:",
    "GPUCommandSubmission.Submitted",
    "GPUReadbackResult.Completed",
    "telemetry:GPUCacheTelemetry.pipeline:",
    "readbackBytes",
    "rawPixels",
    "supportClaim",
]
EXPECTED_MANIFEST_ENTRY_FIELDS = {
    "key",
    "claimLevel",
    "status",
    "validationReportName",
    "promotionGate",
    "promotionGatePassed",
    "missingEvidence",
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


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"GPU renderer R6 PM evidence validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_exact_fields(payload: dict[str, Any], expected_fields: set[str], owner: str) -> None:
    actual_fields = set(payload.keys())
    unexpected = sorted(actual_fields - expected_fields)
    missing = sorted(expected_fields - actual_fields)
    require(not unexpected, f"{owner} has unexpected fields: {', '.join(unexpected)}")
    require(not missing, f"{owner} missing fields: {', '.join(missing)}")


def require_float(payload: dict[str, Any], field: str, expected: float, owner: str) -> None:
    value = payload.get(field)
    require(isinstance(value, int | float) and not isinstance(value, bool), f"{owner}.{field} must be numeric")
    require(float(value) == expected, f"{owner}.{field} must be {expected}")


def canonical_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=True, separators=(",", ":"), sort_keys=True)


def reject_duplicate_json_object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in payload, f"duplicate JSON key: {key}")
        payload[key] = value
    return payload


def load_manifest_fields(manifest_path: Path) -> dict[str, str]:
    require(manifest_path.is_file(), f"missing validation manifest: {manifest_path}")
    fields: dict[str, str] = {}
    for line in manifest_path.read_text(encoding="utf-8").splitlines():
        require("=" in line, f"manifest line must be key=value: {line}")
        key, value = line.split("=", 1)
        require(key not in fields, f"duplicate manifest key: {key}")
        fields[key] = value
    return fields


def expected_manifest_fields() -> set[str]:
    fields = {
        "validation.report.name",
        "validation.report.status",
        "validation.gate.name",
        "validation.gate.passed",
        "validation.gate.missingEvidence",
        "validation.bundle.scope",
        "validation.report.artifacts",
        "validation.report.diagnostics",
        "validation.gate.diagnostics",
    }
    for index in range(1, len(EXPECTED_ARTIFACT_NAMES) + 1):
        ordinal = f"{index:02d}"
        fields.update(
            {
                f"artifact.{ordinal}.name",
                f"artifact.{ordinal}.lines",
                f"artifact.{ordinal}.sha256",
            }
        )
    for index in range(1, len(EXPECTED_REPORT_DIAGNOSTICS) + 1):
        fields.add(f"diagnostic.{index:02d}")
    for index in range(1, len(EXPECTED_GATE_DIAGNOSTICS) + 1):
        fields.add(f"gateDiagnostic.{index:02d}")
    return fields


def validate_manifest_field_inventory(fields: dict[str, str]) -> None:
    expected = expected_manifest_fields()
    actual = set(fields.keys())
    unexpected = sorted(actual - expected)
    missing = sorted(expected - actual)
    require(not unexpected, f"root PM manifest has unexpected fields: {', '.join(unexpected)}")
    require(not missing, f"root PM manifest missing fields: {', '.join(missing)}")


def line_count_for_exported_text(text: str) -> int:
    if text == "\n":
        return 0
    require(text.endswith("\n"), "artifact text must end with a final newline")
    return len(text[:-1].split("\n"))


def artifact_rows(fields: dict[str, str]) -> list[dict[str, Any]]:
    artifact_count = int(fields.get("validation.report.artifacts", "-1"))
    require(artifact_count == len(EXPECTED_ARTIFACT_NAMES), "root PM evidence artifact count changed")
    rows: list[dict[str, Any]] = []
    for index in range(1, artifact_count + 1):
        ordinal = f"{index:02d}"
        name = fields.get(f"artifact.{ordinal}.name")
        lines = fields.get(f"artifact.{ordinal}.lines")
        sha256 = fields.get(f"artifact.{ordinal}.sha256")
        require(name is not None, f"artifact.{ordinal}.name missing")
        require(lines is not None, f"artifact.{ordinal}.lines missing")
        require(sha256 is not None, f"artifact.{ordinal}.sha256 missing")
        rows.append(
            {
                "ordinal": ordinal,
                "name": name,
                "lines": int(lines),
                "sha256": sha256,
            }
        )
    actual_names = [str(row["name"]) for row in rows]
    require(actual_names == EXPECTED_ARTIFACT_NAMES, f"root PM evidence artifact order changed: {actual_names}")
    return rows


def missing_evidence(fields: dict[str, str]) -> list[str]:
    raw = fields.get("validation.gate.missingEvidence", "")
    if raw in ("", "none"):
        return []
    return raw.split(",")


def validate_manifest_diagnostics(fields: dict[str, str]) -> None:
    require(
        fields.get("validation.report.diagnostics") == str(len(EXPECTED_REPORT_DIAGNOSTICS)),
        "root PM report diagnostic count changed",
    )
    require(
        fields.get("validation.gate.diagnostics") == str(len(EXPECTED_GATE_DIAGNOSTICS)),
        "root PM gate diagnostic count changed",
    )
    for index, expected in enumerate(EXPECTED_REPORT_DIAGNOSTICS, start=1):
        key = f"diagnostic.{index:02d}"
        require(fields.get(key) == expected, f"root PM report {key} changed")
    for index, expected in enumerate(EXPECTED_GATE_DIAGNOSTICS, start=1):
        key = f"gateDiagnostic.{index:02d}"
        require(fields.get(key) == expected, f"root PM gate diagnostic.{index:02d} changed")
    report_keys = {key for key in fields if key.startswith("diagnostic.")}
    gate_keys = {key for key in fields if key.startswith("gateDiagnostic.")}
    expected_report_keys = {f"diagnostic.{index:02d}" for index in range(1, len(EXPECTED_REPORT_DIAGNOSTICS) + 1)}
    expected_gate_keys = {f"gateDiagnostic.{index:02d}" for index in range(1, len(EXPECTED_GATE_DIAGNOSTICS) + 1)}
    require(report_keys == expected_report_keys, "root PM report diagnostic keys changed")
    require(gate_keys == expected_gate_keys, "root PM gate diagnostic keys changed")


def validate_artifact_hashes(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    for row in rows:
        relative_path = str(row["name"])
        require(not relative_path.startswith("/") and ".." not in relative_path.split("/"), f"non-portable artifact path: {relative_path}")
        artifact_path = output_dir / relative_path
        require(artifact_path.is_file(), f"missing artifact: {relative_path}")
        bytes_value = artifact_path.read_bytes()
        actual_hash = "sha256:" + hashlib.sha256(bytes_value).hexdigest()
        require(actual_hash == row["sha256"], f"hash mismatch for {relative_path}: {actual_hash} != {row['sha256']}")
        actual_lines = line_count_for_exported_text(bytes_value.decode("utf-8"))
        require(actual_lines == row["lines"], f"line-count mismatch for {relative_path}: {actual_lines} != {row['lines']}")


def validate_no_product_claims(output_dir: Path, fields: dict[str, str], rows: list[dict[str, Any]]) -> None:
    joined_manifest = "\n".join(f"{key}={value}" for key, value in sorted(fields.items()))
    for snippet in FORBIDDEN_CLAIM_SNIPPETS:
        require(snippet not in joined_manifest, f"manifest contains forbidden product claim: {snippet}")
    for pattern in FORBIDDEN_CLAIM_PATTERNS:
        require(pattern.search(joined_manifest) is None, f"manifest contains forbidden product claim: {pattern.pattern}")
    for row in rows:
        artifact_path = output_dir / str(row["name"])
        text = artifact_path.read_text(encoding="utf-8")
        for snippet in FORBIDDEN_CLAIM_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains forbidden product claim: {snippet}")
        for pattern in FORBIDDEN_CLAIM_PATTERNS:
            require(pattern.search(text) is None, f"{row['name']} contains forbidden product claim: {pattern.pattern}")


def validate_no_root_artifact_content_contamination(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    for row in rows:
        artifact_path = output_dir / str(row["name"])
        text = artifact_path.read_text(encoding="utf-8")
        for snippet in ROOT_ARTIFACT_CONTAMINATION_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains root artifact content contamination: {snippet}")


def validate_no_unexpected_files(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    allowed_files = {MANIFEST_ARTIFACT, OUTPUT_MANIFEST_ENTRY}
    allowed_files.update(str(row["name"]) for row in rows)
    actual_files = sorted(
        str(path.relative_to(output_dir)).replace("\\", "/")
        for path in output_dir.rglob("*")
        if path.is_file()
    )
    unexpected = [path for path in actual_files if path not in allowed_files]
    require(not unexpected, f"unexpected root PM evidence files: {', '.join(unexpected)}")


def build_manifest_entry(fields: dict[str, str], rows: list[dict[str, Any]]) -> dict[str, Any]:
    missing = missing_evidence(fields)
    return {
        "key": "gpuRendererR6FirstRoutePmEvidence",
        "claimLevel": "gpu-renderer-r6-pm-evidence-only",
        "status": fields["validation.report.status"],
        "validationReportName": fields["validation.report.name"],
        "promotionGate": fields["validation.gate.name"],
        "promotionGatePassed": False,
        "missingEvidence": missing,
        "artifactDirectory": RELEASE_DIR,
        "manifestArtifact": f"{RELEASE_DIR}/{MANIFEST_ARTIFACT}",
        "manifestEntryJson": f"{RELEASE_DIR}/{OUTPUT_MANIFEST_ENTRY}",
        "artifactCount": len(rows) + 1,
        "dumpArtifactCount": len(rows),
        "generationCommand": "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererR6FirstRoutePmEvidenceBundle",
        "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
        "productRouteActivated": False,
        "nativeKadreCiRequired": False,
        "webGpuAdapterRequired": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "nonClaims": [
            "No product route activation.",
            "No WebGPU adapter, Kadre window, or native demo requirement for this bundle.",
            "No first-route support claim while promotion evidence is missing.",
            "No hidden CPU-rendered texture fallback.",
        ],
        "notice": "The GPU renderer R6 bundle packages validation-owned first-route PM evidence. The default artifact is refusal-first and incomplete; it is review evidence, not a product support claim.",
    }


def validate_entry(entry: dict[str, Any], fields: dict[str, str]) -> None:
    require_exact_fields(entry, EXPECTED_MANIFEST_ENTRY_FIELDS, "manifest entry")
    require(entry["status"] == "Incomplete", "default GPU renderer PM evidence must remain Incomplete")
    require(entry["promotionGatePassed"] is False, "default GPU renderer promotion gate must remain false")
    require(entry["productRouteActivated"] is False, "PM bundle must not activate product routes")
    require(entry["nativeKadreCiRequired"] is False, "PM bundle must not require native Kadre CI")
    require(entry["webGpuAdapterRequired"] is False, "PM bundle must not require a WebGPU adapter")
    require(entry["releaseBlocking"] is False, "PM bundle must not add release-blocking gates")
    require_float(entry, "readinessDelta", 0.0, "manifest entry")
    require(fields["validation.bundle.scope"] == "pm-evidence-only", "validation bundle scope must be pm-evidence-only")


def validate_manifest_entry_sidecar(output_dir: Path, entry: dict[str, Any], fields: dict[str, str]) -> None:
    sidecar_path = output_dir / OUTPUT_MANIFEST_ENTRY
    if not sidecar_path.is_file():
        return
    try:
        sidecar = json.loads(
            sidecar_path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_json_object_pairs,
        )
    except json.JSONDecodeError as exc:
        fail(f"manifest entry sidecar is not valid JSON: {exc}")
    require(isinstance(sidecar, dict), "manifest entry sidecar must be a JSON object")
    try:
        validate_entry(sidecar, fields)
    except ValidationError as exc:
        fail(f"manifest entry sidecar failed validation: {exc}")
    require(canonical_json(sidecar) == canonical_json(entry), "manifest entry sidecar disagrees with rebuilt root PM evidence entry")


def validate_output(output_dir: Path) -> tuple[dict[str, str], list[dict[str, Any]], dict[str, Any]]:
    manifest_path = output_dir / MANIFEST_ARTIFACT
    fields = load_manifest_fields(manifest_path)
    validate_manifest_field_inventory(fields)
    require(fields.get("validation.report.name") == EXPECTED_REPORT_NAME, "unexpected validation report name")
    require(fields.get("validation.report.status") == "Incomplete", "default validation report must remain Incomplete")
    require(fields.get("validation.gate.name") == "first-route-promotion", "unexpected promotion gate name")
    require(fields.get("validation.gate.passed") == "false", "default promotion gate must remain false")
    require(fields.get("validation.bundle.scope") == "pm-evidence-only", "bundle must remain evidence-only")
    require(
        missing_evidence(fields) == EXPECTED_MISSING_EVIDENCE_SEQUENCE,
        "default missing evidence sequence changed",
    )
    validate_manifest_diagnostics(fields)
    rows = artifact_rows(fields)
    validate_artifact_hashes(output_dir, rows)
    validate_no_unexpected_files(output_dir, rows)
    validate_no_root_artifact_content_contamination(output_dir, rows)
    validate_no_product_claims(output_dir, fields, rows)
    entry = build_manifest_entry(fields, rows)
    validate_entry(entry, fields)
    validate_manifest_entry_sidecar(output_dir, entry, fields)
    return fields, rows, entry


def inject_pm_bundle(output_dir: Path, bundle_dir: Path, entry: dict[str, Any]) -> None:
    manifest_path = bundle_dir / "manifest.json"
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")
    manifest = json.loads(
        manifest_path.read_text(encoding="utf-8"),
        object_pairs_hook=reject_duplicate_json_object_pairs,
    )
    require(isinstance(manifest, dict), "PM bundle manifest must be a JSON object")
    require(manifest.get("generatedBy") == "pipelinePmBundle", "target manifest must be generated by pipelinePmBundle")

    target_dir = bundle_dir / RELEASE_DIR
    if target_dir.exists():
        shutil.rmtree(target_dir)
    shutil.copytree(output_dir, target_dir)
    (target_dir / OUTPUT_MANIFEST_ENTRY).write_text(json.dumps(entry, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    manifest[entry["key"]] = entry
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    readme_path = bundle_dir / "README.md"
    if readme_path.is_file():
        readme = readme_path.read_text(encoding="utf-8")
        marker = "GPU renderer R6 first-route PM evidence"
        if marker not in readme:
            readme += (
                "\n"
                "- GPU renderer R6 first-route PM evidence lives in `manifest.json` under "
                "`gpuRendererR6FirstRoutePmEvidence` and in `release/gpu-renderer-r6-first-route-pm-evidence/`; "
                "it is validation-owned, refusal-first, `pm-evidence-only`, and does not activate a product route.\n"
            )
            readme_path.write_text(readme, encoding="utf-8")


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 and argv[2] != "--inject-pm-bundle" else root / DEFAULT_OUTPUT_DIR
    _, rows, entry = validate_output(output_dir)
    if "--inject-pm-bundle" in argv:
        bundle_arg_index = argv.index("--inject-pm-bundle") + 1
        require(bundle_arg_index < len(argv), "--inject-pm-bundle requires bundle directory")
        inject_pm_bundle(output_dir, Path(argv[bundle_arg_index]).resolve(), entry)
    print(
        "GPU renderer R6 PM evidence validation passed: "
        f"{len(rows)} dump artifacts, gatePassed={entry['promotionGatePassed']}, status={entry['status']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
