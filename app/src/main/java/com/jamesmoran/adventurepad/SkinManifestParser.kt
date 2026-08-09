package com.jamesmoran.adventurepad

internal enum class SkinManifestFailure {
    INVALID,
    UNSUPPORTED_VERSION,
    RESERVED_ID,
}

internal class SkinManifestException(
    message: String,
    val failure: SkinManifestFailure = SkinManifestFailure.INVALID,
) : IllegalArgumentException(message)

internal object SkinManifestParser {
    private val IdPattern = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)+")
    private val VersionPattern = Regex("[0-9]+(?:\\.[0-9]+){0,2}(?:[-+][A-Za-z0-9.-]+)?")
    private val ShaPattern = Regex("[0-9a-fA-F]{64}")
    private val ColorPattern = Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")

    fun parse(json: String): SkinManifest {
        val root = SimpleJson.parse(json).asObject("skin.json")
        val formatVersion = root.requiredInt("formatVersion")
        if (formatVersion != SKIN_FORMAT_VERSION) {
            throw SkinManifestException(
                "Unsupported formatVersion $formatVersion",
                SkinManifestFailure.UNSUPPORTED_VERSION,
            )
        }
        val id = root.requiredString("id")
        checkManifest(IdPattern.matches(id) && id.length <= 128, "Invalid skin id")
        if (id.startsWith("builtin.")) {
            throw SkinManifestException(
                "External skins cannot use the builtin namespace",
                SkinManifestFailure.RESERVED_ID,
            )
        }
        val packageVersion = root.requiredString("packageVersion")
        checkManifest(VersionPattern.matches(packageVersion), "Invalid packageVersion")
        val assets = root.optionalObject("assets").orEmpty().mapValues { (slot, raw) ->
            parseAsset(slot, raw.asObject("asset '$slot'"))
        }
        val colors = root.optionalObject("colors").orEmpty().mapValues { (name, value) ->
            value.asString("color '$name'").also {
                checkManifest(ColorPattern.matches(it), "Invalid color '$name'")
            }
        }
        val metrics = root.optionalObject("metrics").orEmpty().mapValues { (name, value) ->
            value.asNumber("metric '$name'").toFloat().also {
                checkManifest(it.isFinite() && it in 0.5f..2f, "Metric '$name' is outside 0.5..2.0")
            }
        }
        return SkinManifest(
            formatVersion = formatVersion,
            id = id,
            name = root.requiredString("name").bounded("name", 128),
            author = root.requiredString("author").bounded("author", 128),
            packageVersion = packageVersion,
            minimumAdventurePadVersionCode = root.requiredInt("minimumAdventurePadVersionCode").also {
                checkManifest(it >= 1, "minimumAdventurePadVersionCode must be positive")
            },
            features = root.optionalArray("features").orEmpty().mapTo(linkedSetOf()) {
                it.asString("feature").bounded("feature", 64)
            },
            assets = assets,
            colors = colors,
            metrics = metrics,
            extensions = root.optionalObject("extensions").orEmpty(),
        )
    }

    private fun parseAsset(slot: String, value: Map<String, Any?>): SkinAsset {
        checkManifest(slot.length <= 96 && slot.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")), "Invalid asset slot '$slot'")
        checkManifest(
            value.keys.all { it in AssetFields },
            "Unsupported field in asset '$slot'",
        )
        val path = value.requiredString("path")
        validateRelativePath(path)
        val scale = value.optionalString("scale")?.let {
            runCatching {
                SkinScaleMode.valueOf(
                    it.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace('-', '_').uppercase(),
                )
            }
                .getOrElse { throw SkinManifestException("Invalid scale '$it' for '$slot'") }
        } ?: SkinScaleMode.CONTAIN
        val sha = value.requiredString("sha256")
        checkManifest(ShaPattern.matches(sha), "Invalid sha256 for '$slot'")
        val canvas = value.optionalObject("canvas")?.let {
            SkinCanvas(it.requiredInt("width"), it.requiredInt("height")).also { canvas ->
                checkManifest(canvas.width in 1..8192 && canvas.height in 1..8192, "Canvas for '$slot' is too large")
            }
        }
        val slices = value.optionalObject("sliceInsets")?.let {
            SkinInsets(it.requiredInt("left"), it.requiredInt("top"), it.requiredInt("right"), it.requiredInt("bottom")).also { inset ->
                checkManifest(listOf(inset.left, inset.top, inset.right, inset.bottom).all { side -> side >= 0 }, "Negative slice inset for '$slot'")
            }
        }
        checkManifest(scale == SkinScaleMode.NINE_SLICE || slices == null, "sliceInsets require nineSlice for '$slot'")
        checkManifest(scale != SkinScaleMode.NINE_SLICE || slices != null, "nineSlice requires sliceInsets for '$slot'")
        val hotspot = value.optionalObject("hotspot")?.let {
            SkinHotspot(it.requiredNumber("x").toFloat(), it.requiredNumber("y").toFloat()).also { point ->
                checkManifest(point.x in 0f..1f && point.y in 0f..1f, "Hotspot for '$slot' must be normalized")
            }
        }
        if (slot == SkinSlots.PANEL_FRAME) {
            checkManifest(
                scale == SkinScaleMode.NINE_SLICE,
                "'${SkinSlots.PANEL_FRAME}' must use nineSlice so its center remains content-safe",
            )
        }
        return SkinAsset(slot, path, scale, sha.lowercase(), canvas, slices, hotspot)
    }

    fun validateRelativePath(path: String) {
        checkManifest(path.length in 1..240, "Asset path length is invalid")
        checkManifest(!path.startsWith('/') && !path.startsWith('\\'), "Asset path must be relative")
        checkManifest('\\' !in path, "Asset paths must use POSIX separators")
        val segments = path.split('/')
        checkManifest(segments.none { it.isBlank() || it == "." || it == ".." }, "Asset path contains traversal or empty segments")
        checkManifest(!path.contains(':') && !path.contains('\u0000'), "Asset path contains forbidden characters")
    }

    private fun String.bounded(label: String, maximum: Int): String = also {
        checkManifest(isNotBlank() && length <= maximum, "$label is blank or too long")
    }

    private fun checkManifest(condition: Boolean, message: String) {
        if (!condition) throw SkinManifestException(message)
    }

    private val AssetFields = setOf("path", "scale", "sha256", "canvas", "sliceInsets", "hotspot")
}

private fun Any?.asObject(label: String): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { (key, value) ->
        (key as? String ?: throw SkinManifestException("$label contains a non-string key")) to value
    } ?: throw SkinManifestException("$label must be an object")

private fun Any?.asString(label: String): String = this as? String
    ?: throw SkinManifestException("$label must be a string")

private fun Any?.asNumber(label: String): Number = this as? Number
    ?: throw SkinManifestException("$label must be a number")

private fun Map<String, Any?>.requiredString(name: String) = get(name).asString(name)
private fun Map<String, Any?>.optionalString(name: String) = get(name)?.asString(name)
private fun Map<String, Any?>.requiredNumber(name: String) = get(name).asNumber(name)
private fun Map<String, Any?>.requiredInt(name: String): Int {
    val number = requiredNumber(name)
    val long = number.toLong()
    if (number.toDouble() != long.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) {
        throw SkinManifestException("$name must be an integer")
    }
    return long.toInt()
}
private fun Map<String, Any?>.optionalObject(name: String) = get(name)?.asObject(name)
private fun Map<String, Any?>.optionalArray(name: String) = get(name)?.let {
    it as? List<Any?> ?: throw SkinManifestException("$name must be an array")
}
