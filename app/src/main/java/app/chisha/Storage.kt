package app.chisha

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * 用官方 DataStore 保存数据：先写临时文件再整体替换，写到一半被杀也不会损坏原数据；
 * 所有写入按顺序排队，不会互相覆盖。
 */
val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class RecipeBook(
    val list: List<Recipe> = emptyList(),
    val initialized: Boolean = false,
)

private class JsonSerializer<T>(private val ser: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        AppJson.decodeFromString(ser, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("数据文件读不出来", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(AppJson.encodeToString(ser, t).encodeToByteArray())
    }
}

val Context.appStore: DataStore<AppData> by dataStore(
    fileName = "app_data.json",
    serializer = JsonSerializer(AppData.serializer(), AppData()),
)

val Context.recipeStore: DataStore<RecipeBook> by dataStore(
    fileName = "recipe_book.json",
    serializer = JsonSerializer(RecipeBook.serializer(), RecipeBook()),
)
