#!/usr/bin/env python3
"""Aggregate deterministic three-attempt GM render-cost measurements."""

import argparse
import json
import os
import platform
import subprocess
from pathlib import Path


SCHEMA_VERSION = 1


def _parse_attempt(attempt):
    raw = attempt["record"] if isinstance(attempt, dict) else attempt
    if not isinstance(raw, str):
        raise ValueError("attempt record must be a string")
    parts = raw.split("|", 3)
    if len(parts) < 3:
        raise ValueError("invalid attempt record: %r" % raw)
    kind, index, name = parts[:3]
    try:
        index = int(index)
    except ValueError as error:
        raise ValueError("invalid attempt index in %r" % raw) from error
    if kind == "PASS":
        if len(parts) != 4:
            raise ValueError("PASS record requires elapsedMs: %r" % raw)
        try:
            elapsed_ms = float(parts[3])
        except ValueError as error:
            raise ValueError("invalid elapsedMs in %r" % raw) from error
        return {"kind": kind, "index": index, "name": name, "elapsedMs": elapsed_ms, "raw": raw}
    if kind in {"FAIL", "TIMEOUT"}:
        detail = parts[3] if len(parts) == 4 else ""
        return {"kind": kind, "index": index, "name": name, "detail": detail, "raw": raw}
    raise ValueError("unsupported attempt record: %r" % raw)


def _ordered_names(names):
    ordered = list(names)
    if not ordered:
        raise ValueError("expected one or more registry indices")
    indices = [entry[0] if isinstance(entry, tuple) else int(entry) for entry in ordered]
    if any(not isinstance(index, int) or index < 0 for index in indices):
        raise ValueError("expected one or more registry indices")
    if len(set(indices)) != len(indices):
        raise ValueError("GM names must be unique")
    return indices


def fallback_names(names, attempt, records):
    """Return batch names without a flushed record for the given outer attempt."""
    ordered_names = _ordered_names(names)
    recorded = set()
    for record in records:
        if not isinstance(record, dict) or record.get("attempt") != attempt:
            continue
        parsed = _parse_attempt(record)
        if parsed["index"] in ordered_names:
            recorded.add(parsed["index"])
    return [name for name in ordered_names if name not in recorded]


def aggregate_attempt_records(names, records):
    """Validate and group raw outer-attempt records for Task 2 aggregation."""
    ordered_names = _ordered_names(names)
    expected_names = set(ordered_names)
    grouped = {name: [] for name in ordered_names}
    seen = set()

    for record in records:
        if not isinstance(record, dict) or not isinstance(record.get("attempt"), int):
            raise ValueError("attempt record requires an integer outer attempt")
        attempt = record["attempt"]
        if attempt not in {0, 1, 2}:
            raise ValueError("outer attempt must be 0, 1, or 2")
        parsed = _parse_attempt(record)
        name = parsed["index"]
        if name not in expected_names:
            raise ValueError("recorded GM is not selected: %s" % name)
        key = (name, attempt)
        if key in seen:
            raise ValueError("duplicate GM/attempt record: %s/%s" % key)
        seen.add(key)
        grouped[name].append((attempt, {"record": parsed["raw"]}))

    result = {}
    for name in ordered_names:
        samples = sorted(grouped[name], key=lambda sample: sample[0])
        if len(samples) != 3:
            raise ValueError("expected exactly three attempts for %s" % name)
        result[name] = [sample for _, sample in samples]
    return result


def _read_ndjson(path):
    if not path.exists():
        return []
    records = []
    for line_number, line in enumerate(path.read_text().splitlines(), start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError("invalid NDJSON at %s:%s" % (path, line_number)) from error
        records.append(record)
    return records


def append_scanner_records(names, attempt, scanner_output, attempts_ndjson):
    """Append scanner lines as attributed NDJSON, rejecting duplicate GM/attempt pairs."""
    ordered_names = _ordered_names(names)
    selected_names = set(ordered_names)
    existing = _read_ndjson(attempts_ndjson)
    seen = set()
    for record in existing:
        if not isinstance(record, dict) or not isinstance(record.get("attempt"), int):
            raise ValueError("attempt record requires an integer outer attempt")
        parsed = _parse_attempt(record)
        seen.add((parsed["index"], record["attempt"]))

    additions = []
    for line in scanner_output.read_text().splitlines():
        if not line.strip():
            continue
        if not line.startswith(("PASS|", "FAIL|", "TIMEOUT|")):
            continue
        parsed = _parse_attempt(line)
        if parsed["index"] not in selected_names:
            raise ValueError("scanner returned unselected GM index: %s" % parsed["index"])
        key = (parsed["index"], attempt)
        if key in seen:
            raise ValueError("duplicate GM/attempt record: %s/%s" % key)
        seen.add(key)
        additions.append({"attempt": attempt, "record": parsed["raw"]})

    if additions:
        attempts_ndjson.parent.mkdir(parents=True, exist_ok=True)
        with attempts_ndjson.open("a") as output:
            for record in additions:
                output.write(json.dumps(record, sort_keys=True) + "\n")


def write_environment(path, uname, java_version, git_head):
    path.write_text(to_sorted_json({
        "gitHead": git_head,
        "javaVersion": java_version,
        "uname": uname,
    }))


def _median(samples):
    ordered = sorted(samples)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2


def _number(value):
    return int(value) if value == int(value) else value


def classify_attempts(attempts):
    """Classify exactly three raw scanner attempt records."""
    if len(attempts) != 3:
        raise ValueError("expected exactly three attempts")
    parsed = [_parse_attempt(attempt) for attempt in attempts]
    timeouts = [attempt for attempt in parsed if attempt["kind"] == "TIMEOUT"]
    failures = [attempt for attempt in parsed if attempt["kind"] == "FAIL"]
    samples = [attempt["elapsedMs"] for attempt in parsed if attempt["kind"] == "PASS"]

    result = {
        "tag": None,
        "medianMs": None,
        "timeoutCount": len(timeouts),
        "errorCount": len(failures),
        "classificationReason": None,
    }
    if failures:
        details = sorted(filter(None, (attempt["detail"] for attempt in failures)))
        result.update(
            tag="BLOCKING",
            classificationReason="incomplete-or-error: " + (", ".join(details) or "render failure"),
        )
        return result
    if len(timeouts) >= 2:
        result.update(tag="BLOCKING", classificationReason="two or more attempts timed out")
        return result
    if len(samples) < 2:
        result.update(tag="INCOMPLETE_OR_ERROR", classificationReason="fewer than two completed samples")
        return result

    median_ms = _number(_median(samples))
    if median_ms < 1000:
        tag, reason = "FAST", "median below 1000 ms"
    elif median_ms < 5000:
        tag, reason = "MEDIUM", "median below 5000 ms"
    elif median_ms < 10000:
        tag, reason = "SLOW", "median below 10000 ms"
    else:
        tag, reason = "BLOCKING", "median at or above 10000 ms"
    result.update(tag=tag, medianMs=median_ms, classificationReason=reason)
    return result


def build_report(attempts_by_name, timed_out_batches, provenance):
    """Build a sorted report retaining all records required for rebaseline review."""
    rows = []
    for name in sorted(attempts_by_name, key=str):
        attempts = attempts_by_name[name]
        first = _parse_attempt(attempts[0])
        row = {"registryIndex": first["index"], "name": first["name"], "rawSamples": [
            attempt["record"] if isinstance(attempt, dict) else attempt for attempt in attempts
        ]}
        row.update(classify_attempts(attempts))
        rows.append(row)
    return {
        "provenance": {key: provenance[key] for key in sorted(provenance)},
        "rows": rows,
        "schemaVersion": SCHEMA_VERSION,
        "timedOutBatches": [sorted(batch) for batch in sorted(timed_out_batches)],
    }


def to_sorted_json(report):
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def _escape_markdown_table_cell(value):
    return str(value).replace("|", r"\|")


def to_markdown(report):
    lines = ["# GM render-cost measurement", "", "- schemaVersion: `%s`" % report["schemaVersion"], "", "## Provenance", ""]
    for key, value in report["provenance"].items():
        lines.append("- %s: `%s`" % (key, value))
    lines.extend(["", "## Results", "", "| Name | Tag | Median (ms) | Timeouts | Errors | Reason |", "| --- | --- | ---: | ---: | ---: | --- |"])
    for row in report["rows"]:
        median = "" if row["medianMs"] is None else str(row["medianMs"])
        lines.append("| `%s` | %s | %s | %s | %s | %s |" % (
            _escape_markdown_table_cell(row["name"]),
            _escape_markdown_table_cell(row["tag"]),
            median,
            row["timeoutCount"],
            row["errorCount"],
            _escape_markdown_table_cell(row["classificationReason"]),
        ))
        lines.append("|  |  |  |  |  | Raw samples: %s |" % ", ".join(
            "`%s`" % _escape_markdown_table_cell(sample) for sample in row["rawSamples"]
        ))
    lines.extend(["", "## Timed-out batches", ""])
    if report["timedOutBatches"]:
        lines.extend("- %s" % ", ".join("`%s`" % name for name in batch) for batch in report["timedOutBatches"])
    else:
        lines.append("- None")
    return "\n".join(lines) + "\n"


fallback_indices = fallback_names


def _git_head():
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _default_provenance(backend):
    return {
        "backend": backend,
        "gitHead": _git_head(),
        "jdk": os.environ.get("JAVA_VERSION", "unknown"),
        "os": "%s %s" % (platform.system(), platform.release()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="JSON mapping GM names to three attempt records")
    parser.add_argument("--json-out", type=Path, help="destination for sorted JSON")
    parser.add_argument("--markdown-out", type=Path, help="destination for Markdown report")
    parser.add_argument("--backend", default="unknown", help="renderer backend used for measurement")
    parser.add_argument("--attempts-ndjson", type=Path, help="attributed raw scanner records")
    parser.add_argument("--scanner-output", type=Path, help="raw output from one scanner invocation")
    parser.add_argument("--names", help="comma-separated selected GM names")
    parser.add_argument("--attempt", type=int, help="outer attempt index")
    parser.add_argument("--fallback", action="store_true", help="print names missing the outer attempt")
    parser.add_argument("--aggregate", action="store_true", help="write Task 2 aggregation input to stdout")
    parser.add_argument("--append-scanner-records", action="store_true", help="attribute scanner output into NDJSON")
    parser.add_argument("--environment-out", type=Path, help="write captured environment JSON")
    parser.add_argument("--uname", help="captured uname")
    parser.add_argument("--java-version", help="captured Java version")
    parser.add_argument("--git-head", help="captured Git HEAD")
    args = parser.parse_args()

    if args.environment_out:
        if any(value is None for value in (args.uname, args.java_version, args.git_head)):
            parser.error("--environment-out requires --uname, --java-version, and --git-head")
        write_environment(args.environment_out, args.uname, args.java_version, args.git_head)
        return

    names = args.names.split(",") if args.names else None
    if args.append_scanner_records:
        if names is None or args.attempt is None or not args.scanner_output or not args.attempts_ndjson:
            parser.error("--append-scanner-records requires --names, --attempt, --scanner-output, and --attempts-ndjson")
        append_scanner_records(names, args.attempt, args.scanner_output, args.attempts_ndjson)
        return
    if args.fallback:
        if names is None or args.attempt is None or not args.attempts_ndjson:
            parser.error("--fallback requires --names, --attempt, and --attempts-ndjson")
        print("\n".join(map(str, fallback_names(names, args.attempt, _read_ndjson(args.attempts_ndjson)))) )
        return
    if args.aggregate:
        if names is None or not args.attempts_ndjson:
            parser.error("--aggregate requires --names and --attempts-ndjson")
        print(to_sorted_json({"attempts": aggregate_attempt_records(names, _read_ndjson(args.attempts_ndjson))}), end="")
        return

    if not args.input or not args.json_out or not args.markdown_out:
        parser.error("--input, --json-out, and --markdown-out are required for report generation")
    input_data = json.loads(args.input.read_text())
    report = build_report(
        input_data["attempts"],
        input_data.get("timedOutBatches", []),
        {**_default_provenance(args.backend), **input_data.get("provenance", {})},
    )
    args.json_out.write_text(to_sorted_json(report))
    args.markdown_out.write_text(to_markdown(report))


if __name__ == "__main__":
    main()
