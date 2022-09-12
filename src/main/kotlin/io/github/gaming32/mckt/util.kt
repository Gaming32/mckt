package io.github.gaming32.mckt

import kotlinx.serialization.json.Json
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.NbtCompression
import net.benwoodworth.knbt.NbtVariant
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory
import org.slf4j.helpers.Util
import java.util.EnumMap
import kotlin.math.max

// Note: This does not handle translation args
val PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.builder().apply {
    flattener(ComponentFlattener.basic().toBuilder().apply {
        mapper(TranslatableComponent::class.java) { component ->
            DEFAULT_TRANSLATIONS[component.key()] ?: component.key()
        }
    }.build())
}.build()

val NETWORK_NBT = Nbt {
    variant = NbtVariant.Java
    compression = NbtCompression.None
}

val SAVE_NBT = Nbt {
    variant = NbtVariant.Java
    compression = NbtCompression.Gzip
    ignoreUnknownKeys = true
}

val PRETTY_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}

val USERNAME_REGEX = Regex("^\\w{1,16}\$")

fun getLogger() = LoggerFactory.getLogger(Util.getCallingClass())!!

fun Component.plainText() = PLAIN_TEXT_SERIALIZER.serialize(this)

fun Int.squared() = this * this

inline fun spiralLoop(w: Int, h: Int, action: (x: Int, y: Int) -> Unit) {
    var x = 0
    var y = 0
    var dx = 0
    var dy = -1
    repeat(max(w, h).squared()) {
        if (-w / 2 < x && x <= w / 2 && -h / 2 < y && y <= h / 2) {
            action(x, y)
        }
        if (x == y || (x < 0 && x == -y) || (x > 0 && x == 1 - y)) {
            val temp = dx
            dx = -dy
            dy = temp
        }
        x += dx
        y += dy
    }
}

inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String) = try {
    enumValueOf<T>(name)
} catch (e: IllegalArgumentException) {
    null
}

fun CharSequence.capitalize() = if (isEmpty()) "" else "${this[0].titlecase()}${this.substring(1).lowercase()}"

fun <K, V> Map<K, V>.flip() = asSequence().associate { (k, v) -> v to k }

fun ByteArray.toHexString(): String = joinToString("") { it.toUByte().toString(radix = 16).padStart(2, '0') }

fun <K, V> Map<K, V>.toTypedArray() = iterator().let { iter -> Array(size) { iter.next().run { key to value } } }

inline fun <reified K : Enum<K>, V> enumMapOf(): MutableMap<K, V> = EnumMap(K::class.java)

fun <K : Enum<K>, V> enumMapOf(vararg elements: Pair<K, V>): MutableMap<K, V> {
    require(elements.isNotEmpty()) { "At least one element is required" }
    val result = EnumMap<K, V>(elements[0].first.javaClass)
    elements.forEach { result[it.first] = it.second }
    return result
}
