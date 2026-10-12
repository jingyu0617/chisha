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
    val protein: Int? = null,   // 每人份蛋白质（克，估算）
    val kcal: Int? = null,      // 每人份热量（千卡，估算）
    val source: String? = null, // 来源链接（比如做饭视频）
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
    // 消耗品：每次用量（和 unit 同单位）、每天几次；按天自动扣
    val dose: Double? = null,
    val perDay: Double? = null,
    val autoAt: Long? = null,      // 上次自动扣到的时间
    val reordered: Boolean = false, // 本轮快用完时已自动加进清单
    val roast: String? = null,     // 咖啡豆烘焙日期
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
    val mode: String = "rough",       // 旧版全局单位设置，已改为录入时逐项选择
    val seasonHave: Boolean = true,   // 调料默认家里有
    val skin: String = "android",     // 已废弃（苹果外观已删除），保留字段只为读旧数据不出错
    val muscle: Boolean = true,       // 增肌：优先推荐高蛋白
    val notify: Boolean = true,       // 每天 9 点的系统通知
    val askedNotif: Boolean = false,  // 已经问过通知权限
)

@Serializable
data class DrawFilters(
    val cats: List<String> = listOf("荤菜", "素菜", "水产", "汤羹", "主食"),
    val maxDiff: Int = 5,
    val maxTime: Int = 0,
    val noSpicy: Boolean = false,
    val vegOnly: Boolean = false,
    val avoidRecent: Boolean = true,
    val highProtein: Boolean = false,
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
const val RECIPE_FILE_VERSION = 3

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
    @SerialName("蛋白质克") val protein: Int? = null,
    @SerialName("热量千卡") val kcal: Int? = null,
    @SerialName("来源") val source: String? = null,
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
