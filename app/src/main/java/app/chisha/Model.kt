package app.chisha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一道菜。id 只在 App 内部用，导出文件里不出现。 */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val cat: String,
    val diff: Int? = null,
    val minutes: Int? = null,
    val spicy: Boolean = false,
    val meat: Boolean = false,
    val desc: String = "",
    val ingredients: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val src: String = "m",
)

@Serializable
data class InvItem(
    val id: String,
    val name: String,
    val cat: String,
    val qty: Double,
    val unit: String,
    val loc: String,
    val exp: String? = null,
    val t: Long = 0,
    val sample: Boolean = false,
)

@Serializable
data class ShopItem(
    val id: String,
    val name: String,
    val cat: String,
    val forDishes: List<String> = emptyList(),
    val done: Boolean = false,
    val t: Long = 0,
)

@Serializable
data class Eaten(val n: String, val t: Long)

@Serializable
data class Settings(
    val mode: String = "rough",       // rough = 按个/份, exact = 按克/毫升
    val seasonHave: Boolean = true,   // 调料默认家里有
)

@Serializable
data class DrawFilters(
    val cats: List<String> = listOf("荤菜", "素菜", "水产", "汤羹", "主食"),
    val maxDiff: Int = 5,
    val maxTime: Int = 0,
    val noSpicy: Boolean = false,
    val vegOnly: Boolean = false,
    val avoidRecent: Boolean = true,
)

@Serializable
data class AppData(
    val inv: List<InvItem> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    val favs: Set<String> = emptySet(),
    val eaten: List<Eaten> = emptyList(),
    val settings: Settings = Settings(),
    val filters: DrawFilters = DrawFilters(),
    val ppl: Int = 3,
    val seeded: Boolean = false,
)

/* ---------- 菜谱文件（导入/导出用，字段名固定为中文） ---------- */

const val RECIPE_FILE_FORMAT = "今天吃啥菜谱"
const val RECIPE_FILE_VERSION = 1

@Serializable
data class RecipeFile(
    @SerialName("格式") val format: String,
    @SerialName("版本") val version: Int,
    @SerialName("说明") val note: String = "",
    @SerialName("菜谱") val recipes: List<RecipeEntry>,
)

@Serializable
data class RecipeEntry(
    @SerialName("菜名") val name: String,
    @SerialName("分类") val cat: String,
    @SerialName("难度") val diff: Int? = null,
    @SerialName("分钟") val minutes: Int? = null,
    @SerialName("辣") val spicy: Boolean = false,
    @SerialName("含肉") val meat: Boolean = false,
    @SerialName("简介") val desc: String = "",
    @SerialName("原料") val ingredients: List<String>,
    @SerialName("用量") val amounts: List<String> = emptyList(),
    @SerialName("做法") val steps: List<String>,
    @SerialName("小贴士") val tips: List<String> = emptyList(),
)

/* ---------- 网页版备份（迁移用） ---------- */

@Serializable
data class WebBackup(
    val inv: List<WebInv> = emptyList(),
    val shop: List<WebShop> = emptyList(),
    val favs: List<String> = emptyList(),
    val eaten: List<Eaten> = emptyList(),
)

@Serializable
data class WebInv(
    val name: String,
    val cat: String = "其他",
    val qty: Double = 1.0,
    val unit: String = "个",
    val loc: String = "冷藏",
    val exp: String? = null,
)

@Serializable
data class WebShop(
    val name: String,
    val cat: String = "其他",
    @SerialName("for") val forDishes: List<String> = emptyList(),
    val done: Boolean = false,
)
