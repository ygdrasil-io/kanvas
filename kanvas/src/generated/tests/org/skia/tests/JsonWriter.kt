package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.skia.tools.CommandLineFlags

/**
 * C++ original:
 * ```cpp
 * class JsonWriter {
 * public:
 *     /**
 *      *  Info describing a single run.
 *      */
 *     struct BitmapResult {
 *         SkString name;            // E.g. "ninepatch-stretch", "desk_gws.skp"
 *         SkString config;          //      "gpu", "8888", "serialize", "pipe"
 *         SkString sourceType;      //      "gm", "skp", "image"
 *         SkString sourceOptions;   //      "image", "codec", "subset", "scanline"
 *         SkString md5;             // In ASCII, so 32 bytes long.
 *         SkString ext;             // Extension of file we wrote: "png", "pdf", ...
 *         SkString gamut;
 *         SkString transferFn;
 *         SkString colorType;
 *         SkString alphaType;
 *         SkString colorDepth;
 *     };
 *
 *     /**
 *      *  Add a result to the end of the list of results.
 *      */
 *     static void AddBitmapResult(const BitmapResult&);
 *
 *     /**
 *      *  Write all collected results to the file dir/dm.json.
 *      */
 *     static void DumpJson(const char* dir,
 *                          CommandLineFlags::StringArray key,
 *                          CommandLineFlags::StringArray properties);
 *
 *     /**
 *      * Read JSON file at path written by DumpJson, calling callback for each
 *      * BitmapResult recorded in the file.  Return success.
 *      */
 *     static bool ReadJson(const char* path, void(*callback)(BitmapResult));
 * }
 * ```
 */
public open class JsonWriter {
  public data class BitmapResult public constructor(
    public var name: Int,
    public var config: Int,
    public var sourceType: Int,
    public var sourceOptions: Int,
    public var md5: Int,
    public var ext: Int,
    public var gamut: Int,
    public var transferFn: Int,
    public var colorType: Int,
    public var alphaType: Int,
    public var colorDepth: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void JsonWriter::AddBitmapResult(const BitmapResult& result) {
     *     SkAutoMutexExclusive lock(bitmap_result_mutex());
     *     gBitmapResults.push_back(result);
     * }
     * ```
     */
    public fun addBitmapResult(result: BitmapResult) {
      TODO("Implement addBitmapResult")
    }

    /**
     * C++ original:
     * ```cpp
     * void JsonWriter::DumpJson(const char* dir,
     *                           CommandLineFlags::StringArray key,
     *                           CommandLineFlags::StringArray properties) {
     *     if (0 == strcmp(dir, "")) {
     *         return;
     *     }
     *
     *     SkString path = SkOSPath::Join(dir, "dm.json");
     *     sk_mkdir(dir);
     *     SkFILEWStream stream(path.c_str());
     *     SkJSONWriter writer(&stream, SkJSONWriter::Mode::kPretty);
     *
     *     writer.beginObject(); // root
     *
     *     for (int i = 1; i < properties.size(); i += 2) {
     *         writer.appendCString(properties[i-1], properties[i]);
     *     }
     *
     *     writer.beginObject("key");
     *     for (int i = 1; i < key.size(); i += 2) {
     *         writer.appendCString(key[i-1], key[i]);
     *     }
     *     writer.endObject();
     *
     *     int maxResidentSetSizeMB = sk_tools::getMaxResidentSetSizeMB();
     *     if (maxResidentSetSizeMB != -1) {
     *         writer.appendS32("max_rss_MB", maxResidentSetSizeMB);
     *     }
     *
     *     {
     *         SkAutoMutexExclusive lock(bitmap_result_mutex());
     *         writer.beginArray("results");
     *         for (int i = 0; i < gBitmapResults.size(); i++) {
     *             writer.beginObject();
     *
     *             writer.beginObject("key");
     *             writer.appendString("name"       , gBitmapResults[i].name);
     *             writer.appendString("config"     , gBitmapResults[i].config);
     *             writer.appendString("source_type", gBitmapResults[i].sourceType);
     *
     *             // Source options only need to be part of the key if they exist.
     *             // Source type by source type, we either always set options or never set options.
     *             if (!gBitmapResults[i].sourceOptions.isEmpty()) {
     *                 writer.appendString("source_options", gBitmapResults[i].sourceOptions);
     *             }
     *             writer.endObject(); // key
     *
     *             writer.beginObject("options");
     *             writer.appendString("ext"  ,       gBitmapResults[i].ext);
     *             writer.appendString("gamut",       gBitmapResults[i].gamut);
     *             writer.appendString("transfer_fn", gBitmapResults[i].transferFn);
     *             writer.appendString("color_type",  gBitmapResults[i].colorType);
     *             writer.appendString("alpha_type",  gBitmapResults[i].alphaType);
     *             writer.appendString("color_depth", gBitmapResults[i].colorDepth);
     *             writer.endObject(); // options
     *
     *             writer.appendString("md5", gBitmapResults[i].md5);
     *
     *             writer.endObject(); // 1 result
     *         }
     *         writer.endArray(); // results
     *     }
     *
     *     writer.endObject(); // root
     *     writer.flush();
     *     stream.flush();
     * }
     * ```
     */
    public fun dumpJson(
      dir: String?,
      key: CommandLineFlags.StringArray,
      properties: CommandLineFlags.StringArray,
    ) {
      TODO("Implement dumpJson")
    }

    /**
     * C++ original:
     * ```cpp
     * bool JsonWriter::ReadJson(const char* path, void(*callback)(BitmapResult)) {
     *     sk_sp<SkData> json(SkData::MakeFromFileName(path));
     *     if (!json) {
     *         return false;
     *     }
     *
     *     DOM dom((const char*)json->data(), json->size());
     *     const ObjectValue* root = dom.root();
     *     if (!root) {
     *         return false;
     *     }
     *
     *     const ArrayValue* results = (*root)["results"];
     *     if (!results) {
     *         return false;
     *     }
     *
     *     BitmapResult br;
     *     for (const ObjectValue* r : *results) {
     *         const ObjectValue& key = (*r)["key"].as<ObjectValue>();
     *         const ObjectValue& options = (*r)["options"].as<ObjectValue>();
     *
     *         br.name         = key["name"].as<StringValue>().begin();
     *         br.config       = key["config"].as<StringValue>().begin();
     *         br.sourceType   = key["source_type"].as<StringValue>().begin();
     *         br.ext          = options["ext"].as<StringValue>().begin();
     *         br.gamut        = options["gamut"].as<StringValue>().begin();
     *         br.transferFn   = options["transfer_fn"].as<StringValue>().begin();
     *         br.colorType    = options["color_type"].as<StringValue>().begin();
     *         br.alphaType    = options["alpha_type"].as<StringValue>().begin();
     *         br.colorDepth   = options["color_depth"].as<StringValue>().begin();
     *         br.md5          = (*r)["md5"].as<StringValue>().begin();
     *
     *         if (const StringValue* so = key["source_options"]) {
     *             br.sourceOptions = so->begin();
     *         }
     *         callback(br);
     *     }
     *     return true;
     * }
     * ```
     */
    public fun readJson(path: String?, param1: (Any) -> Unit): Boolean {
      TODO("Implement readJson")
    }
  }
}
