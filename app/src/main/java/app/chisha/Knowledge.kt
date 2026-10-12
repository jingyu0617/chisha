package app.chisha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 食材常识：分类、存放、保质期、单位、和菜谱用料的匹配。 */
object K {
    val ICATS = listOf("蔬菜", "水果", "肉禽", "水产", "蛋奶豆", "主食干货", "补剂冲饮", "调料", "其他")
    val LOCS = listOf("冷藏", "冷冻", "常温")
    val RCATS = listOf("荤菜", "素菜", "水产", "汤羹", "主食", "早餐")

    private val SUPP = Regex("蛋白粉|乳清|增肌粉|肌酸|咖啡豆|咖啡粉|挂耳|冻干咖啡|速溶咖啡|茶叶|代餐|BCAA|电解质", RegexOption.IGNORE_CASE)
    private val COFFEE = Regex("咖啡豆|咖啡粉|挂耳")

    private val CAT_RULES: List<Pair<String, Regex>> = listOf(
        "补剂冲饮" to SUPP,
        "蛋奶豆" to Regex("鸡蛋|鸭蛋|鹌鹑蛋|皮蛋|咸蛋|蛋|奶|豆腐|豆浆|腐竹|豆干|芝士|奶酪|黄油"),
        "水产" to Regex("鱼|虾|蟹|贝|鱿|蛤|蚝|海带|紫菜|海参|蛏|墨鱼|章鱼"),
        "水果" to Regex("苹果|香蕉|橙|橘|梨|葡萄|西瓜|草莓|桃|芒果|柠檬|猕猴桃|柚|蓝莓|樱桃|菠萝|榴莲|火龙果|哈密瓜"),
        "调料" to Regex("盐|糖|酱油|生抽|老抽|醋|料酒|蚝油|味精|鸡精|花椒|八角|淀粉|胡椒|豆瓣酱|辣椒酱|番茄酱|芝麻酱|香油|食用油|花生油|玉米油|菜籽油|橄榄油|调和油|十三香|孜然|桂皮|香叶|咖喱|火锅底料"),
        "肉禽" to Regex("肉|排骨|鸡|鸭|鹅|牛|羊|猪|培根|火腿|香肠|腊|里脊|蹄|翅|肝|肚|胗"),
        "蔬菜" to Regex("菜|椒|瓜|豆角|豆芽|茄|萝卜|土豆|马铃薯|番茄|西红柿|葱|姜|蒜|菇|菌|木耳|笋|藕|芹|韭|菠|生菜|西兰花|西蓝花|洋葱|莴|芋|山药|玉米|南瓜|秋葵|荷兰豆|毛豆|豌豆|葫芦|茭白|凤尾|黄花|豆苗|香菜"),
        "主食干货" to Regex("米|面|粉|馒头|饺|饼|燕麦|面包|年糕|粽|红枣|枸杞|花生|红豆|绿豆|黄豆"),
    )

    fun guessCat(n: String): String {
        if (n.isBlank()) return "其他"
        for ((c, re) in CAT_RULES) if (re.containsMatchIn(n)) return c
        return "其他"
    }

    private val LEAFY = Regex("菠菜|生菜|油麦菜|青菜|小白菜|韭菜|空心菜|苋菜|茼蒿|豆苗|香菜")
    private val KEEPS = Regex("土豆|马铃薯|洋葱|南瓜|红薯|芋|姜|蒜|冬瓜")

    fun guessLoc(n: String, c: String): String = when {
        c == "主食干货" || c == "调料" || c == "补剂冲饮" -> "常温"
        KEEPS.containsMatchIn(n) -> "常温"
        else -> "冷藏"
    }

    fun guessDays(n: String, c: String, loc: String): Int {
        if (c == "补剂冲饮") return if (COFFEE.containsMatchIn(n)) 90 else 365
        if (loc == "冷冻") return if (c == "肉禽" || c == "水产") 90 else 60
        if (n.contains("鸡蛋")) return 30
        if (Regex("豆腐|豆浆").containsMatchIn(n)) return 3
        if (Regex("牛奶|鲜奶").containsMatchIn(n)) return 7
        if (loc == "常温") {
            if (KEEPS.containsMatchIn(n)) return 21
            return mapOf("蔬菜" to 5, "水果" to 5, "主食干货" to 180, "调料" to 365, "蛋奶豆" to 7, "肉禽" to 1, "水产" to 1)[c] ?: 30
        }
        if (LEAFY.containsMatchIn(n)) return 3
        return mapOf("蔬菜" to 5, "水果" to 7, "肉禽" to 2, "水产" to 1, "蛋奶豆" to 7, "主食干货" to 5, "调料" to 180)[c] ?: 5
    }

    fun daysText(d: Int): String = if (d >= 60) "${(d / 30.0).roundToInt()} 个月" else "$d 天"

    /* ---------- 单位 ---------- */
    val MASS = mapOf("g" to 1.0, "kg" to 1000.0, "斤" to 500.0)
    val VOL = mapOf("ml" to 1.0, "L" to 1000.0)
    val UNITS_ROUGH = listOf("个", "份", "把", "根", "盒", "袋", "瓶", "块", "斤")
    val UNITS_EXACT = listOf("g", "kg", "ml", "L")

    fun unitsFor(mode: String): List<String> =
        if (mode == "exact") UNITS_EXACT + UNITS_ROUGH else UNITS_ROUGH + UNITS_EXACT

    fun unitKind(u: String) = when (u) {
        in MASS -> "mass"
        in VOL -> "vol"
        else -> "count"
    }

    fun stepOf(u: String): Double = mapOf("g" to 50.0, "kg" to 0.5, "ml" to 100.0, "L" to 0.5, "斤" to 0.5)[u] ?: 1.0

    fun defUnit(n: String, c: String, mode: String): String {
        if (mode == "exact" || c == "补剂冲饮") {
            return if (Regex("奶|油|酱油|醋|酒|汁|水").containsMatchIn(n) && c != "蔬菜") "ml" else "g"
        }
        return when {
            Regex("米|面粉").containsMatchIn(n) && c == "主食干货" -> "斤"
            Regex("(^|[小香大])葱$|菠菜|青菜|韭菜|香菜|面条|挂面").containsMatchIn(n) -> "把"
            Regex("豆腐|牛奶|酸奶").containsMatchIn(n) -> "盒"
            Regex("豆芽|木耳|粉丝|粉条|香菇").containsMatchIn(n) -> "袋"
            c == "肉禽" || c == "水产" -> "份"
            c == "调料" -> "瓶"
            else -> "个"
        }
    }

    fun unitMode(u: String) = if (unitKind(u) == "count") "rough" else "exact"

    /** 新录入时的默认数量 */
    fun defQty(unit: String, name: String = ""): Double = when {
        unit == "g" && Regex("蛋白粉|乳清|增肌粉").containsMatchIn(name) -> 900.0
        unit == "g" && COFFEE.containsMatchIn(name) -> 250.0
        else -> defQty(unit)
    }

    fun defQty(unit: String): Double = when (unit) {
        "g" -> 500.0
        "ml" -> 1000.0
        else -> 1.0
    }

    fun fmtQ(q: Double): String {
        val r = (q * 100).roundToInt() / 100.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    /* ---------- 消耗品 ---------- */
    fun isCoffee(n: String) = COFFEE.containsMatchIn(n)

    /** 常见消耗品的默认用法：每次用量（克）、每天几次 */
    fun consumableDefaults(n: String): Pair<Double, Double>? = when {
        Regex("蛋白粉|乳清|增肌粉").containsMatchIn(n) -> 30.0 to 1.0
        n.contains("肌酸") -> 5.0 to 1.0
        COFFEE.containsMatchIn(n) -> 15.0 to 2.0
        else -> null
    }

    /** 按每天用量算，还能用几天；不是消耗品返回 null */
    fun supplyDays(it: InvItem): Int? {
        val d = it.dose ?: return null
        val p = it.perDay ?: return null
        if (d <= 0 || p <= 0) return null
        return (it.qty / (d * p)).toInt()
    }

    /** 咖啡豆新鲜度：烘焙后第几天 */
    fun coffeeFresh(roast: String?): Pair<String, Level>? {
        if (roast.isNullOrBlank()) return null
        val d = try { ChronoUnit.DAYS.between(LocalDate.parse(roast), LocalDate.now()).toInt() } catch (e: Exception) { return null }
        return when {
            d < 0 -> null
            d < 4 -> "养豆中·第${d}天" to Level.WARN
            d <= 30 -> "最佳赏味·第${d}天" to Level.OK
            d <= 45 -> "尽快喝完·第${d}天" to Level.WARN
            else -> "风味下降·已${d}天" to Level.BAD
        }
    }

    const val HIGH_PROTEIN = 25

    /* ---------- 日期 ---------- */
    fun ymd(offset: Int): String = LocalDate.now().plusDays(offset.toLong()).toString()

    fun daysLeft(exp: String?): Int? {
        if (exp.isNullOrBlank()) return null
        return try {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exp)).toInt()
        } catch (e: Exception) {
            null
        }
    }

    enum class Level { BAD, WARN, OK }

    fun expInfo(n: Int?): Pair<String, Level>? = when {
        n == null -> null
        n < 0 -> "过期${-n}天" to Level.BAD
        n == 0 -> "今天到期" to Level.BAD
        n == 1 -> "明天到期" to Level.WARN
        n <= 3 -> "${n}天后到期" to Level.WARN
        else -> "还剩${n}天" to Level.OK
    }

    /* ---------- 匹配 ---------- */
    private val GROUPS: List<List<String>> = listOf(
        listOf("番茄", "西红柿"), listOf("土豆", "马铃薯", "洋芋"),
        listOf("猪肉", "五花肉", "瘦肉", "里脊", "猪里脊", "肉末", "肉馅", "前腿肉", "梅花肉", "猪肉末"),
        listOf("鸡肉", "鸡腿", "鸡胸", "鸡胸肉", "鸡翅", "鸡块", "鸡爪", "鸡腿肉"),
        listOf("牛肉", "牛腩", "肥牛", "牛里脊", "牛排"),
        listOf("鸡蛋", "蛋液", "鸡蛋液", "蛋清", "蛋黄", "蛋白"),
        listOf("木耳", "黑木耳", "干木耳", "云耳"), listOf("粉丝", "龙口粉丝", "红薯粉丝"), listOf("粉条", "红薯粉条"),
        listOf("米饭", "剩饭", "隔夜饭"), listOf("面条", "挂面", "手擀面", "拉面"),
        listOf("青椒", "尖椒", "菜椒", "彩椒", "青红椒", "螺丝椒", "线椒", "杭椒", "二荆条"),
        listOf("香菇", "蘑菇", "平菇", "金针菇", "口蘑", "杏鲍菇"),
        listOf("白菜", "大白菜", "娃娃菜"), listOf("虾", "虾仁", "大虾", "基围虾"),
        listOf("葱", "小葱", "香葱", "葱花"), listOf("大葱", "葱白"),
        listOf("蒜", "大蒜", "蒜头", "蒜末", "蒜瓣"), listOf("姜", "生姜", "姜片", "姜末", "姜丝"),
        listOf("豆腐", "嫩豆腐", "老豆腐", "北豆腐", "南豆腐"),
        listOf("包菜", "卷心菜", "圆白菜", "甘蓝"), listOf("西兰花", "西蓝花"), listOf("花菜", "菜花", "花椰菜"),
        listOf("黄瓜", "青瓜"), listOf("牛奶", "纯牛奶"), listOf("排骨", "猪排", "肋排", "猪肋排"),
        listOf("胡萝卜", "红萝卜"), listOf("鱼", "草鱼", "鲈鱼", "鲫鱼", "鱼块", "鱼片"),
    )
    private val termCache = HashMap<String, List<String>>()

    fun termsFor(name: String): List<String> = termCache.getOrPut(name) {
        val s = linkedSetOf(name)
        for (g in GROUPS) if (g.any { v -> v == name || (v.length >= 2 && name.contains(v)) }) s.addAll(g)
        s.toList()
    }

    private val PAREN = Regex("[（(][^）)]*[)）]")
    private val SPLIT = Regex("[，,、\\s/;；：:或和及]+")
    private val NUMTAIL = Regex("[0-9０-９.~～\\-]+.*$")
    private val PREFIX = Regex("^(泡好的|泡发的|泡软的|新鲜的?|切好的|煮熟的|熟的|适量的?)")

    fun tokens(line: String): List<String> =
        line.replace(PAREN, " ").split(SPLIT)
            .map { it.replace(NUMTAIL, "").replace(PREFIX, "").trim() }
            .filter { it.isNotEmpty() }

    private val SAUCE_NAME = Regex("[酱汁粉油]$")
    private val SAUCE_TOK = Regex("(酱|汁|油|粉)$")

    fun matchItem(name: String, line: String): Boolean {
        val g = termsFor(name)
        val sauce = SAUCE_NAME.containsMatchIn(name)
        return tokens(line).any { tok ->
            if (!sauce && SAUCE_TOK.containsMatchIn(tok) && tok !in g) return@any false
            g.any { t -> if (t.length >= 2) tok.contains(t) else tok == t } || (tok.length >= 2 && name.contains(tok))
        }
    }

    private val SEASON = Regex("^(盐|食盐|精盐|糖|白糖|白砂糖|冰糖|红糖|油|食用油|植物油|花生油|玉米油|菜籽油|橄榄油|调和油|生抽|老抽|酱油|醋|陈醋|香醋|米醋|白醋|料酒|黄酒|蚝油|味精|鸡精|鸡粉|葱|小葱|香葱|大葱|葱花|葱段|姜|生姜|姜片|姜末|蒜|大蒜|蒜头|蒜末|淀粉|玉米淀粉|水淀粉|生粉|胡椒|胡椒粉|白胡椒粉|黑胡椒|花椒|八角|桂皮|香叶|香油|芝麻油|水|清水|开水|热水|冷水|温水|矿泉水|十三香|五香粉|孜然|辣椒面|辣椒粉|干辣椒|豆瓣酱|白芝麻|芝麻|冰块|香菜)")
    private val SEASON_ANY = Regex("淀粉|花椒|豆瓣|胡椒|八角|桂皮|香叶|料酒|生抽|老抽|蚝油|鸡精|味精|白糖|冰糖|陈醋|香醋")
    private val SEASON_END = Regex("(盐|糖|酱|醋|酒|精|油)$")
    private val NOT_SEASON = Regex("油麦菜|油条|油豆腐|奶油|黄油")
    private val AROMATIC = Regex("葱|姜|蒜")
    private val NOT_AROMATIC = Regex("洋葱|蒜苗|蒜苔|蒜薹|蒜黄")
    private val NONFOOD = Regex("材料|原则|可选|任选|适量|备用|工具|小料|配料|辅料|调料|调味|锅|刀|砧板|碗|盘|筷|勺子|保鲜|模具|烤箱|电饭|微波|蒸笼|量杯|厨房|手套|容器|纸|牙签|^等$")

    fun isSeason(nm: String): Boolean {
        if (SEASON_ANY.containsMatchIn(nm)) return true
        if (nm.length <= 3 && AROMATIC.containsMatchIn(nm) && !NOT_AROMATIC.containsMatchIn(nm)) return true
        return SEASON.matches(nm) || (SEASON_END.containsMatchIn(nm) && !NOT_SEASON.containsMatchIn(nm))
    }

    fun isNonFood(nm: String) = NONFOOD.containsMatchIn(nm)

    fun ingNames(r: Recipe): List<String> =
        r.ingredients.mapNotNull { tokens(it).firstOrNull() }.filter { !isNonFood(it) }

    private val GENERIC = Regex("材料|等.{0,4}类|任意|随意|喜欢的")
    fun isGeneric(r: Recipe) = r.ingredients.count { GENERIC.containsMatchIn(it) } >= 2

    /* ---------- 用量解析 ---------- */
    private val CN = mapOf('一' to 1.0, '二' to 2.0, '两' to 2.0, '三' to 3.0, '四' to 4.0, '五' to 5.0, '六' to 6.0, '七' to 7.0, '八' to 8.0, '九' to 9.0, '十' to 10.0, '半' to 0.5)
    private const val COUNTU = "个|只|颗|根|片|块|条|把|棵|瓣|盒|袋|瓶|份"
    private val AMT_NUM = Regex("(\\d+(?:\\.\\d+)?)(?:\\s*[-~～到]\\s*(\\d+(?:\\.\\d+)?))?\\s*(kg|千克|公斤|g|克|ml|mL|毫升|L|l|升|斤|$COUNTU)", RegexOption.IGNORE_CASE)
    private val AMT_CN = Regex("([一二两三四五六七八九十半])\\s*($COUNTU|斤)")

    fun parseAmt(line: String): Pair<Double, String>? {
        AMT_NUM.find(line)?.let { m ->
            var n = m.groupValues[1].toDouble()
            if (m.groupValues[2].isNotEmpty()) n = (n + m.groupValues[2].toDouble()) / 2
            val raw = m.groupValues[3]
            val u = when {
                Regex("^(kg|千克|公斤)$", RegexOption.IGNORE_CASE).matches(raw) -> "kg"
                Regex("^(g|克)$", RegexOption.IGNORE_CASE).matches(raw) -> "g"
                Regex("^(ml|毫升)$", RegexOption.IGNORE_CASE).matches(raw) -> "ml"
                Regex("^(l|升)$", RegexOption.IGNORE_CASE).matches(raw) -> "L"
                else -> raw
            }
            return n to u
        }
        AMT_CN.find(line)?.let { m -> return (CN[m.groupValues[1][0]] ?: 1.0) to m.groupValues[2] }
        return null
    }

    fun suggestUse(item: InvItem, r: Recipe): Double {
        val line = r.amounts.firstOrNull { matchItem(item.name, it) }
        val p = line?.let { parseAmt(it) }
        val k = unitKind(item.unit)
        if (p != null) {
            val (n, u) = p
            val pk = unitKind(u)
            if (k == "mass" && pk == "mass") return n * MASS.getValue(u) / MASS.getValue(item.unit)
            if (k == "vol" && pk == "vol") return n * VOL.getValue(u) / VOL.getValue(item.unit)
            if (k == "count" && pk == "count" && (u == item.unit || !Regex("盒|袋|瓶|份").containsMatchIn(item.unit))) return n
        }
        return mapOf("g" to 100.0, "kg" to 0.1, "ml" to 100.0, "L" to 0.1, "斤" to 0.2)[item.unit] ?: 1.0
    }

    fun sameThing(a: String, b: String): Boolean =
        a == b || termsFor(a).contains(b) || termsFor(b).contains(a)

    val COMMON = listOf("鸡蛋", "西红柿", "土豆", "青椒", "洋葱", "胡萝卜", "黄瓜", "茄子", "白菜", "菠菜", "西兰花", "豆腐", "香菇", "猪肉", "五花肉", "排骨", "鸡腿", "鸡胸肉", "牛肉", "虾", "鱼", "牛奶", "大米", "面条")
}

/** 一道菜和当前冰箱的关系。 */
data class Analysis(
    val used: List<InvItem>,
    val missing: List<String>,
    val urgent: List<InvItem>,
    val score: Double,
)

/** 能拿来做菜的食材：有数量、没过期、不是补剂冲饮 */
fun liveInv(inv: List<InvItem>) = inv.filter { it.qty > 0 && it.cat != "补剂冲饮" && (K.daysLeft(it.exp)?.let { d -> d >= 0 } ?: true) }

fun missingFor(r: Recipe, live: List<InvItem>, seasonHave: Boolean): List<String> {
    val out = mutableListOf<String>()
    for (nm in K.ingNames(r)) {
        if (K.isSeason(nm) && seasonHave) continue
        if (live.any { K.matchItem(it.name, nm) }) continue
        if (nm !in out) out += nm
    }
    return out
}

fun analyze(r: Recipe, live: List<InvItem>, seasonHave: Boolean, muscle: Boolean = false): Analysis? {
    val used = live.filter { it2 -> r.ingredients.any { line -> K.matchItem(it2.name, line) } }
    if (used.isEmpty()) return null
    val missing = missingFor(r, live, seasonHave)
    var score = 0.0
    val urgent = mutableListOf<InvItem>()
    for (it in used) {
        val n = K.daysLeft(it.exp)
        score += 1 + when {
            n == null -> 0.0
            n <= 1 -> 3.0
            n <= 3 -> 2.0
            n <= 7 -> 0.5
            else -> 0.0
        }
        if (n != null && n <= 3) urgent += it
    }
    score -= missing.size * 1.0
    if (K.isGeneric(r)) score *= 0.3
    if ((r.minutes ?: 0) > 90) score -= 1
    if (muscle) score += (r.protein ?: 0) / 12.0
    return Analysis(used, missing, urgent, score)
}
