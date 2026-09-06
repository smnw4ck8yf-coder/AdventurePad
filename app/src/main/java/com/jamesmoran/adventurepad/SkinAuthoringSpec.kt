package com.jamesmoran.adventurepad

internal data class SkinAuthoringSpec(
    val schemaVersion: Int,
    val templateVersion: String,
    val masterCanvas: SkinCanvas,
    val regions: List<SkinAuthoringRegion>,
)

internal data class SkinAuthoringRegion(
    val index: Int,
    val slotId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val alphaMode: String,
    val scaleMode: SkinScaleMode,
    val sliceInsets: SkinInsets?,
    val transparentCenter: SkinTransparentCenter?,
    val gameplaySafeCenter: SkinGameplaySafeCenter?,
)

internal data class SkinTransparentCenter(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val requiredAlpha: Int,
)

internal data class SkinGameplaySafeCenter(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val ignoredAlphaAtOrBelow: Int,
    val maxNonTransparentFraction: Double,
)

internal class SkinAuthoringSpecException(message: String) : IllegalArgumentException(message)

internal object SkinAuthoringSpecParser {
    private val authoritativeSlots = SkinSlots.supportedV1
        .minus(setOf(SkinSlots.LAUNCHER_BACKGROUND, SkinSlots.LAUNCHER_HEADER, SkinSlots.LAUNCHER_BRAND))
        .plus(SkinSlots.PREVIEW)

    fun parse(json: String): SkinAuthoringSpec {
        val root = jsonObject(SimpleJson.parse(json), "authoring spec")
        val schemaVersion = root.int("schemaVersion")
        specCheck(schemaVersion == 1, "Unsupported authoring spec schemaVersion $schemaVersion")
        val canvasObject = root.obj("masterCanvas")
        val canvas = SkinCanvas(canvasObject.int("width"), canvasObject.int("height"))
        specCheck(canvas.width in 1..8192 && canvas.height in 1..8192, "Invalid master canvas dimensions")
        val regions = root.array("regions").map { raw -> parseRegion(jsonObject(raw, "region"), canvas) }
        val slots = regions.map(SkinAuthoringRegion::slotId)
        specCheck(slots.size == slots.toSet().size, "Every authoring slot must occur exactly once")
        val indices = regions.map(SkinAuthoringRegion::index)
        specCheck(indices.size == indices.toSet().size, "Every authoring region index must occur exactly once")
        specCheck(slots.toSet() == authoritativeSlots, "Authoring spec does not match the authoritative v1 slots")
        return SkinAuthoringSpec(
            schemaVersion = schemaVersion,
            templateVersion = root.string("templateVersion"),
            masterCanvas = canvas,
            regions = regions.sortedBy(SkinAuthoringRegion::index),
        )
    }

    private fun parseRegion(value: Map<String, Any?>, canvas: SkinCanvas): SkinAuthoringRegion {
        val slot = value.string("slotId")
        val origin = value.obj("origin")
        val size = value.obj("size")
        val output = value.obj("output")
        specCheck(output.string("format") == "png", "$slot is not a PNG output")
        val scale = runCatching {
            SkinScaleMode.valueOf(value.string("scaleMode").asEnumName())
        }.getOrElse { throw SkinAuthoringSpecException("Invalid scaleMode for $slot") }
        val insets = value.optionalObj("nineSliceInsets")?.let {
            SkinInsets(it.int("left"), it.int("top"), it.int("right"), it.int("bottom"))
        }
        specCheck(scale == SkinScaleMode.NINE_SLICE || insets == null, "sliceInsets require nineSlice for $slot")
        specCheck(scale != SkinScaleMode.NINE_SLICE || insets != null, "nineSlice requires sliceInsets for $slot")
        val center = value.optionalObj("transparentCenter")?.let {
            SkinTransparentCenter(
                x = it.int("x"), y = it.int("y"), width = it.int("width"), height = it.int("height"),
                requiredAlpha = it.int("requiredAlpha"),
            )
        }
        val gameplaySafeCenter = value.optionalObj("gameplaySafeCenter")?.let {
            SkinGameplaySafeCenter(
                x = it.int("x"), y = it.int("y"), width = it.int("width"), height = it.int("height"),
                ignoredAlphaAtOrBelow = it.int("ignoredAlphaAtOrBelow"),
                maxNonTransparentFraction = it.double("maxNonTransparentFraction"),
            )
        }
        val region = SkinAuthoringRegion(
            index = value.int("index"), slotId = slot,
            x = origin.int("x"), y = origin.int("y"),
            width = size.int("width"), height = size.int("height"),
            outputWidth = output.int("width"), outputHeight = output.int("height"),
            alphaMode = value.string("alphaMode"), scaleMode = scale,
            sliceInsets = insets, transparentCenter = center, gameplaySafeCenter = gameplaySafeCenter,
        )
        specCheck(region.index > 0, "Invalid index for $slot")
        specCheck(region.x >= 0 && region.y >= 0 && region.width > 0 && region.height > 0, "Invalid crop for $slot")
        specCheck(region.outputWidth > 0 && region.outputHeight > 0, "Invalid output size for $slot")
        specCheck(region.x + region.width <= canvas.width && region.y + region.height <= canvas.height, "$slot escapes the master canvas")
        insets?.let {
            specCheck(it.left >= 0 && it.top >= 0 && it.right >= 0 && it.bottom >= 0, "Negative slice inset for $slot")
            specCheck(it.left + it.right < region.outputWidth && it.top + it.bottom < region.outputHeight, "Slice insets consume $slot")
        }
        center?.let {
            specCheck(it.requiredAlpha in 0..255, "Invalid requiredAlpha for $slot")
            specCheck(it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0, "Invalid transparent center for $slot")
            specCheck(it.x + it.width <= region.outputWidth && it.y + it.height <= region.outputHeight, "Transparent center escapes $slot")
        }
        gameplaySafeCenter?.let {
            specCheck(slot == SkinSlots.TOP_SURROUND, "gameplaySafeCenter is only valid for ${SkinSlots.TOP_SURROUND}")
            specCheck(it.ignoredAlphaAtOrBelow in 0..254, "Invalid ignored alpha for $slot")
            specCheck(it.maxNonTransparentFraction in 0.0..1.0, "Invalid gameplay-safe tolerance for $slot")
            specCheck(it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0, "Invalid gameplay-safe center for $slot")
            specCheck(it.x + it.width <= region.outputWidth && it.y + it.height <= region.outputHeight, "Gameplay-safe center escapes $slot")
        }
        return region
    }

    private fun jsonObject(value: Any?, label: String): Map<String, Any?> =
        (value as? Map<*, *>)?.entries?.associate { (key, child) ->
            (key as? String ?: throw SkinAuthoringSpecException("$label contains a non-string key")) to child
        } ?: throw SkinAuthoringSpecException("$label must be an object")

    private fun Map<String, Any?>.obj(key: String) = jsonObject(get(key), key)
    private fun Map<String, Any?>.optionalObj(key: String) = get(key)?.let { jsonObject(it, key) }
    private fun Map<String, Any?>.array(key: String) = get(key) as? List<*>
        ?: throw SkinAuthoringSpecException("$key must be an array")
    private fun Map<String, Any?>.string(key: String) = get(key) as? String
        ?: throw SkinAuthoringSpecException("$key must be a string")
    private fun Map<String, Any?>.int(key: String): Int {
        val number = get(key) as? Number ?: throw SkinAuthoringSpecException("$key must be an integer")
        val long = number.toLong()
        specCheck(number.toDouble() == long.toDouble() && long in Int.MIN_VALUE..Int.MAX_VALUE, "$key must be an integer")
        return long.toInt()
    }
    private fun Map<String, Any?>.double(key: String): Double =
        (get(key) as? Number)?.toDouble()?.takeIf(Double::isFinite)
            ?: throw SkinAuthoringSpecException("$key must be a finite number")

    private fun String.asEnumName(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace('-', '_')
        .uppercase()

    private fun specCheck(condition: Boolean, message: String) {
        if (!condition) throw SkinAuthoringSpecException(message)
    }
}
