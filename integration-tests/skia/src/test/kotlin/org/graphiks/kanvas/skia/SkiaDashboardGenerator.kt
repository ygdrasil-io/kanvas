package org.graphiks.kanvas.skia

import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO

data class GmEntry(
    val name: String,
    val family: String,
    val similarity: Double?,
    val minSimilarity: Double,
    val isPassing: Boolean?,
    val width: Int,
    val height: Int,
    val maxDiffR: Int, val maxDiffG: Int, val maxDiffB: Int, val maxDiffA: Int,
    val meanDiffR: Double, val meanDiffG: Double, val meanDiffB: Double, val meanDiffA: Double,
    val matchingPixels: Int?,
    val totalPixels: Int?,
    val hasDiff: Boolean,
    val renderFailed: Boolean,
    val noReference: Boolean,
    val sizeMismatch: Boolean,
)

fun main(args: Array<String>) {
    val refDir = File(argAt(args, "--ref-dir"))
    val genDir = File(argAt(args, "--gen-dir"))
    val scoresFile = File(argAt(args, "--scores"))
    val outputDir = File(argAt(args, "--output-dir"))

    val scores = Properties().apply {
        if (scoresFile.exists()) FileInputStream(scoresFile).use { load(it) }
    }

    val gms = SkiaGmRegistry.all()
    val entries = mutableListOf<GmEntry>()
    var passed = 0; var failed = 0; var noScore = 0; var sumSim = 0.0; var simCount = 0

    outputDir.resolve("images/reference").mkdirs()
    outputDir.resolve("images/generated").mkdirs()
    outputDir.resolve("images/diff").mkdirs()

    for (gm in gms) {
        val refFile = refDir.resolve("${gm.name}.png")
        val genFile = genDir.resolve("${gm.renderFamily.name.lowercase()}/${gm.name}.png")
        val fam = gm.renderFamily.name

        if (!refFile.exists()) {
            entries.add(GmEntry(gm.name, fam, null, gm.minSimilarity, null, gm.width, gm.height, 0,0,0,0, 0.0,0.0,0.0,0.0, null, null, false, false, true, false))
            noScore++
            continue
        }

        if (!genFile.exists()) {
            entries.add(GmEntry(gm.name, fam, null, gm.minSimilarity, null, gm.width, gm.height, 0,0,0,0, 0.0,0.0,0.0,0.0, null, null, false, true, false, false))
            noScore++
            continue
        }

        val refImg = ImageIO.read(refFile) ?: error("Failed to decode reference PNG: ${refFile.name}")
        val genImg = ImageIO.read(genFile) ?: error("Failed to decode generated PNG: ${genFile.name}")

        if (refImg.width != genImg.width || refImg.height != genImg.height) {
            println("[SKIP] ${gm.name}: size mismatch (ref=${refImg.width}x${refImg.height}, gen=${genImg.width}x${genImg.height})")
            refFile.copyTo(outputDir.resolve("images/reference/${gm.name}.png"), overwrite = true)
            genFile.copyTo(outputDir.resolve("images/generated/${gm.name}.png"), overwrite = true)
            entries.add(GmEntry(gm.name, fam, null, gm.minSimilarity, null, refImg.width, refImg.height, 0,0,0,0, 0.0,0.0,0.0,0.0, null, null, false, false, false, true))
            noScore++
            continue
        }

        val refRgba = ComparisonUtils.bufferedImageToRgba(refImg)
        val genRgba = ComparisonUtils.bufferedImageToRgba(genImg)

        val result = try {
            ComparisonUtils.compareRgba(
                actual = genRgba,
                reference = refRgba,
                width = refImg.width,
                height = refImg.height,
                tolerance = gm.tolerance,
                minSimilarity = gm.minSimilarity,
            )
        } catch (e: Exception) {
            println("[SKIP] ${gm.name}: comparison failed (${e.message})")
            refFile.copyTo(outputDir.resolve("images/reference/${gm.name}.png"), overwrite = true)
            genFile.copyTo(outputDir.resolve("images/generated/${gm.name}.png"), overwrite = true)
            entries.add(GmEntry(gm.name, fam, null, gm.minSimilarity, null, refImg.width, refImg.height, 0,0,0,0, 0.0,0.0,0.0,0.0, null, null, false, false, false, false))
            noScore++
            continue
        }

        val diffRgba = result.diffRgba
        if (diffRgba != null) {
            ComparisonUtils.saveRgbaAsPng(diffRgba, refImg.width, refImg.height, outputDir.resolve("images/diff/${gm.name}.png"))
        }

        refFile.copyTo(outputDir.resolve("images/reference/${gm.name}.png"), overwrite = true)
        genFile.copyTo(outputDir.resolve("images/generated/${gm.name}.png"), overwrite = true)

        val previousScore = scores.getProperty(gm.name)?.toDoubleOrNull()
        val similarity = previousScore ?: result.similarity

        entries.add(GmEntry(
            name = gm.name,
            family = fam,
            similarity = similarity,
            minSimilarity = gm.minSimilarity,
            isPassing = result.isPassing,
            width = refImg.width,
            height = refImg.height,
            maxDiffR = result.maxDiff[0], maxDiffG = result.maxDiff[1],
            maxDiffB = result.maxDiff[2], maxDiffA = result.maxDiff[3],
            meanDiffR = result.meanDiff[0], meanDiffG = result.meanDiff[1],
            meanDiffB = result.meanDiff[2], meanDiffA = result.meanDiff[3],
            matchingPixels = result.matchingPixels,
            totalPixels = result.totalPixels,
            hasDiff = diffRgba != null,
            renderFailed = false,
            noReference = false,
            sizeMismatch = false,
        ))

        if (result.isPassing) passed++ else failed++
        sumSim += result.similarity; simCount++

        val status = if (result.isPassing) "PASS" else "FAIL"
        println("[$status] ${gm.name}: similarity=${String.format(Locale.US, "%.2f", result.similarity)}% (threshold: ${gm.minSimilarity}%)")
    }

    val avgSim = if (simCount > 0) sumSim / simCount else 0.0
    val families = gms.map { it.renderFamily.name }.distinct().sorted()
    val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    val json = buildJson(entries, passed, failed, noScore, gms.size, avgSim, now, families)
    outputDir.resolve("data/gms.json").writeText(json)
    copyHtml(outputDir, json)

    println()
    println("Dashboard generated: ${outputDir.absolutePath}")
    println("Total: ${gms.size}, Pass: $passed, Fail: $failed, No score: $noScore, Avg sim: ${String.format(Locale.US, "%.1f", avgSim)}%")
}

private fun argAt(args: Array<String>, flag: String): String {
    val idx = args.indexOf(flag)
    require(idx >= 0) { "$flag required" }
    require(idx + 1 < args.size) { "$flag requires a value" }
    return args[idx + 1]
}

private fun buildJson(
    entries: List<GmEntry>,
    passing: Int, failing: Int, noScore: Int, total: Int,
    avgSimilarity: Double, generatedAt: String,
    families: List<String>,
): String {
    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("  \"generatedAt\": \"$generatedAt\",")

    sb.appendLine("  \"summary\": {")
    sb.appendLine("    \"total\": $total,")
    sb.appendLine("    \"passing\": $passing,")
    sb.appendLine("    \"failing\": $failing,")
    sb.appendLine("    \"noScore\": $noScore,")
    sb.appendLine("    \"avgSimilarity\": ${String.format(Locale.US, "%.1f", avgSimilarity)}")
    sb.appendLine("  },")

    sb.appendLine("  \"families\": [")
    sb.appendLine(families.joinToString(",\n") { "    \"${jsonEsc(it)}\"" })
    sb.appendLine("  ],")

    sb.appendLine("  \"gms\": [")
    for ((i, e) in entries.withIndex()) {
        val comma = if (i < entries.size - 1) "," else ""
        sb.appendLine("    {")
        sb.appendLine("      \"name\": \"${jsonEsc(e.name)}\",")
        sb.appendLine("      \"family\": \"${jsonEsc(e.family)}\",")
        sb.appendLine("      \"similarity\": ${e.similarity ?: "null"},")
        sb.appendLine("      \"minSimilarity\": ${e.minSimilarity},")
        sb.appendLine("      \"isPassing\": ${e.isPassing ?: "null"},")
        sb.appendLine("      \"width\": ${e.width},")
        sb.appendLine("      \"height\": ${e.height},")
        sb.appendLine("      \"maxDiff\": { \"r\": ${e.maxDiffR}, \"g\": ${e.maxDiffG}, \"b\": ${e.maxDiffB}, \"a\": ${e.maxDiffA} },")
        sb.appendLine("      \"meanDiff\": { \"r\": ${fmt2(e.meanDiffR)}, \"g\": ${fmt2(e.meanDiffG)}, \"b\": ${fmt2(e.meanDiffB)}, \"a\": ${fmt2(e.meanDiffA)} },")
        sb.appendLine("      \"matchingPixels\": ${e.matchingPixels ?: "null"},")
        sb.appendLine("      \"totalPixels\": ${e.totalPixels ?: "null"},")
        sb.appendLine("      \"hasDiff\": ${e.hasDiff},")
        sb.appendLine("      \"renderFailed\": ${e.renderFailed},")
        sb.appendLine("      \"noReference\": ${e.noReference},")
        sb.appendLine("      \"sizeMismatch\": ${e.sizeMismatch}")
        sb.append("    }$comma\n")
    }
    sb.appendLine("  ]")
    sb.append("}")
    return sb.toString()
}

private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

private fun jsonEsc(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            in '\u0000'..'\u001F' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}

private fun copyHtml(outputDir: File, json: String) {
    val html = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Skia GM Dashboard</title>
<style>
:root{color-scheme:dark;--bg:#0d0d1a;--panel:#141428;--card:#1a1a30;--border:#2a2a44;--text:#e0e0e8;--muted:#7878a0;--green:#2ecc71;--orange:#f39c12;--red:#e74c3c;--blue:#3498db}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;padding:2rem 1rem}
main{max-width:1400px;margin:0 auto}
h1{font-size:1.5rem;margin-bottom:0.25rem}
.subtitle{color:var(--muted);font-size:0.85rem;margin-bottom:1.5rem}
.summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:0.75rem;margin-bottom:1.5rem}
.stat{padding:0.75rem;border-radius:8px;border:1px solid var(--border);text-align:center}
.stat .num{font-size:1.4rem;font-weight:700}
.stat .lbl{font-size:0.7rem;color:var(--muted);margin-top:0.15rem}
.toolbar{display:flex;gap:0.5rem;flex-wrap:wrap;margin-bottom:1.5rem}
.toolbar select,.toolbar input{background:var(--panel);color:var(--text);border:1px solid var(--border);border-radius:6px;padding:0.4rem 0.65rem;font-size:0.8rem}
.toolbar input[type=text]{flex:1;min-width:150px}
.count{font-size:0.8rem;color:var(--muted);margin-bottom:0.75rem}
.grid{display:flex;flex-direction:column;gap:0.75rem}
.card{border:1px solid var(--border);border-radius:10px;padding:1rem;background:var(--card)}
.card.pass{border-color:#1a3a1a}
.card.fail{border-color:#3a1a1a}
.card-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:0.75rem}
.card-header .name{font-weight:600}
.badge{font-size:0.7rem;padding:0.15rem 0.5rem;border-radius:4px;font-weight:600}
.badge.pass{background:#1a3a1a;color:var(--green);border:1px solid #2a5a2a}
.badge.fail{background:#3a1a1a;color:var(--red);border:1px solid #5a2a2a}
.badge.none{background:#1a1a2e;color:var(--muted);border:1px solid var(--border)}
.score{font-weight:700;font-size:1.1rem}
.score.pass{color:var(--green)}.score.warn{color:var(--orange)}.score.fail{color:var(--red)}
.images{display:grid;grid-template-columns:1fr 1fr 1fr;gap:0.75rem}
.images .col{text-align:center}
.images .col .label{font-size:0.7rem;color:var(--muted);margin-bottom:0.25rem;display:flex;justify-content:space-between}
.img-wrap{background:var(--panel);border:1px solid var(--border);border-radius:6px;overflow:hidden;position:relative;cursor:pointer}
.img-wrap img{width:100%;height:auto;display:block}
.img-wrap .placeholder{aspect-ratio:4/3;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:0.75rem;min-height:100px}
.card-footer{display:flex;gap:1rem;margin-top:0.5rem;font-size:0.72rem;color:var(--muted);flex-wrap:wrap}
.modal{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.85);z-index:100;align-items:center;justify-content:center;cursor:pointer}
.modal.open{display:flex}
.modal img{max-width:95vw;max-height:95vh;border-radius:4px}
</style>
</head>
<body>
<main>
<h1>Skia GM Dashboard</h1>
<p class="subtitle" id="genInfo"></p>
<div class="summary" id="summary"></div>
<div class="toolbar" id="toolbar">
<select id="filterFamily"><option value="">Family: All</option></select>
<select id="filterScore"><option value="">Score: All</option><option value="0-25">0-25%</option><option value="25-50">25-50%</option><option value="50-75">50-75%</option><option value="75-90">75-90%</option><option value="90-101">90-100%</option></select>
<select id="filterStatus"><option value="">Status: All</option><option value="pass">Pass</option><option value="fail">Fail</option><option value="none">No Score</option></select>
<input type="text" id="filterSearch" placeholder="Search GM name...">
</div>
<p class="count" id="count"></p>
<div class="grid" id="grid"></div>
</main>
<div class="modal" id="modal" onclick="this.classList.remove('open')"><img id="modalImg"></div>
<script>
const DATA = $json;

function render(){
const fFam=document.getElementById('filterFamily').value;
const fScore=document.getElementById('filterScore').value;
const fStat=document.getElementById('filterStatus').value;
const fSearch=document.getElementById('filterSearch').value.toLowerCase();
let filtered=DATA.gms.filter(g=>{
if(fFam&&g.family!==fFam)return false;
if(fScore){const[lo,hi]=fScore.split('-').map(Number);if(g.similarity===null||g.similarity<lo||g.similarity>hi)return false}
if(fStat==='pass'&&g.isPassing!==true)return false;
if(fStat==='fail'&&g.isPassing!==false)return false;
if(fStat==='none'&&g.isPassing!==null)return false;
if(fSearch&&!g.name.toLowerCase().includes(fSearch))return false;
return true;
});
const s=DATA.summary;
document.getElementById('genInfo').textContent='Generated: '+DATA.generatedAt;
document.getElementById('summary').innerHTML=
'<div class="stat"><div class="num" style="color:var(--text)">'+s.total+'</div><div class="lbl">Total GMs</div></div>'+
'<div class="stat"><div class="num" style="color:var(--green)">'+s.passing+'</div><div class="lbl">Pass</div></div>'+
'<div class="stat"><div class="num" style="color:var(--red)">'+s.failing+'</div><div class="lbl">Fail</div></div>'+
'<div class="stat"><div class="num" style="color:var(--muted)">'+s.noScore+'</div><div class="lbl">No Score</div></div>'+
'<div class="stat"><div class="num" style="color:'+(s.avgSimilarity>=90?'var(--green)':s.avgSimilarity>=75?'var(--orange)':'var(--red)')+'">'+s.avgSimilarity+'%</div><div class="lbl">Avg Similarity</div></div>';
document.getElementById('count').textContent='Showing '+filtered.length+' of '+DATA.gms.length+' GMs';
document.getElementById('grid').innerHTML=filtered.map(g=>{
const sc=g.similarity;const pass=g.isPassing;
const scCls=sc===null?'none':sc>=95?'pass':pass?'warn':'fail';
const badgeCls=pass===true?'pass':pass===false?'fail':g.sizeMismatch?'none':'none';
const badgeTxt=pass===true?'Pass':pass===false?'Fail':g.sizeMismatch?'Size mismatch':'No Score';
const borderCls=pass===true?'pass':pass===false?'fail':g.sizeMismatch?'fail':'';
const diffPct=sc!==null?(100-sc).toFixed(1):'?';
return '<div class="card '+borderCls+'">'+
'<div class="card-header"><div><span class="name">'+g.name+'</span> <span class="badge '+badgeCls+'">'+badgeTxt+'</span></div>'+
'<div><span class="score '+scCls+'">'+(sc!==null?sc.toFixed(1)+'%':'N/A')+'</span> <span style="color:var(--muted);font-size:0.75rem">threshold: '+g.minSimilarity+'%</span></div></div>'+
'<div class="images">'+
'<div class="col"><div class="label"><span>Reference</span><span>'+g.width+'×'+g.height+'</span></div>'+(g.noReference?'<div class="img-wrap"><div class="placeholder">No reference</div></div>':'<div class="img-wrap" onclick="openModal(\'images/reference/'+g.name+'.png\')"><img src="images/reference/'+g.name+'.png" loading="lazy"></div>')+'</div>'+
'<div class="col"><div class="label"><span>Generated</span><span>'+g.width+'×'+g.height+'</span></div>'+(g.renderFailed?'<div class="img-wrap"><div class="placeholder">Render failed</div></div>':'<div class="img-wrap" onclick="openModal(\'images/generated/'+g.name+'.png\')"><img src="images/generated/'+g.name+'.png" loading="lazy"></div>')+'</div>'+
'<div class="col"><div class="label"><span>Diff</span>'+(g.hasDiff?'<span>'+diffPct+'% diff</span>':'')+'</div>'+(g.hasDiff?'<div class="img-wrap" onclick="openModal(\'images/diff/'+g.name+'.png\')"><img src="images/diff/'+g.name+'.png" loading="lazy"></div>':'<div class="img-wrap"><div class="placeholder">'+(g.noReference||g.renderFailed||g.sizeMismatch?'N/A':'Identical')+'</div></div>')+'</div>'+
'</div>'+
'<div class="card-footer"><span>Family: '+g.family+'</span><span>Max diff: R='+g.maxDiff.r+' G='+g.maxDiff.g+' B='+g.maxDiff.b+' A='+g.maxDiff.a+'</span><span>Mean miss: R='+g.meanDiff.r.toFixed(1)+' G='+g.meanDiff.g.toFixed(1)+' B='+g.meanDiff.b.toFixed(1)+' A='+g.meanDiff.a.toFixed(1)+'</span></div>'+
'</div>';
}).join('');
}
function openModal(src){document.getElementById('modalImg').src=src;document.getElementById('modal').classList.add('open')}
document.getElementById('filterFamily').addEventListener('change',render);
document.getElementById('filterScore').addEventListener('change',render);
document.getElementById('filterStatus').addEventListener('change',render);
document.getElementById('filterSearch').addEventListener('input',render);
const fams=[...new Set(DATA.gms.map(g=>g.family))].sort();
document.getElementById('filterFamily').innerHTML='<option value="">Family: All</option>'+fams.map(f=>'<option value="'+f+'">'+f+'</option>').join('');
render();
</script>
</body>
</html>"""
    outputDir.resolve("index.html").writeText(html)
}
