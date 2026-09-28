// Aggregate real GM journals; missing or duplicated cases cannot become a complete checkpoint.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [directory, output] = process.argv.slice(2);
if (!directory || !output) throw new Error('Usage: node summarize-parity.mjs <journal-directory> <new-output.json>');
const journals = readdirSync(directory).filter(name => /^slice-\d+-\d+\.jsonl$/.test(name)).sort();
const records = journals.flatMap(name => readFileSync(join(directory, name), 'utf8').trim().split('\n').map(JSON.parse));
const runs = records.filter(row => row.kind === 'run');
if (!runs.length) throw new Error('No run metadata');
const metadata = runs[0];
for (const run of runs) {
    for (const field of ['schema', 'rendererCommit', 'registrySha256', 'registryCount', 'timeoutSeconds', 'os', 'arch', 'java']) {
        if (run[field] !== metadata[field]) throw new Error(`Mixed ${field} across journals`);
    }
}
const cases = records.filter(row => row.kind === 'gm').sort((a, b) => a.index - b.index);
if (cases.length !== metadata.registryCount || cases.some((row, index) => row.index !== index) ||
    new Set(cases.map(row => row.name)).size !== cases.length) {
    throw new Error('Incomplete or duplicate corpus: preserve failures and resume missing indices before aggregating');
}
const countBy = (rows, field) => Object.fromEntries(
    Object.entries(rows.reduce((counts, row) => {
        const key = row[field] ?? 'unknown';
        counts[key] = (counts[key] ?? 0) + 1;
        return counts;
    }, Object.create(null))).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])),
);
const eligible = cases.filter(row => ['eligible', 'accepted-skia-gap'].includes(row.scope));
const compared = eligible.filter(row => row.outcome === 'compared');
const sortedMatches = compared.map(row => row.pixelMatchTolerance2).sort((a, b) => a - b);
const middle = Math.floor(sortedMatches.length / 2);
const median = !sortedMatches.length ? null : sortedMatches.length % 2
    ? sortedMatches[middle] : (sortedMatches[middle - 1] + sortedMatches[middle]) / 2;
const summary = {
    registryCount: cases.length,
    scopes: countBy(cases, 'scope'),
    eligibleCount: eligible.length,
    eligibleOutcomes: countBy(eligible, 'outcome'),
    renderedCount: eligible.filter(row => row.rendered).length,
    comparedCount: compared.length,
    atLeast99PercentPixelsTolerance2: compared.filter(row => row.pixelMatchTolerance2 >= 99).length,
    atLeast95PercentPixelsTolerance2: compared.filter(row => row.pixelMatchTolerance2 >= 95).length,
    medianComparedPixelMatchTolerance2: median,
    // These are first surfaced diagnostics, not proven independent root causes or promised gains.
    firstFailureDiagnostics: countBy(eligible.filter(row => !row.rendered), 'diagnostic'),
    sumCaseElapsedMs: cases.reduce((sum, row) => sum + (row.elapsedMs ?? 0), 0),
};
writeFileSync(output, JSON.stringify({ schema: 'skia-parity-snapshot-v1', runs, summary, cases }, null, 2) + '\n', { flag: 'wx' });
console.log(JSON.stringify(summary, null, 2));
