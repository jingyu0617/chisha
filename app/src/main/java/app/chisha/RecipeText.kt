package app.chisha

/**
 * 菜谱的文字模板：GPT 按这个格式写，App 按这个格式认。
 * 识别尽量宽松：【菜名】、菜名：、[菜名] 都认；中英文冒号都认；序号写法都认；
 * 缺了选填项就留空，缺了菜名/原料/做法会标成“有问题”，导入前让人看一眼。
 */
object RecipeText {

    val PROMPT = """
你是做菜笔记助手。请把我给你的做饭视频（或字幕、文字）整理成下面这个固定格式的菜谱。只输出菜谱本身，不要其它解释，也不要用代码框。
要求：
1. 每个【】字段单独一行，字段名一个字都不要改。
2. 分类只能是：荤菜、素菜、水产、汤羹、主食、早餐 之一。
3. 难度填 1 到 5 的数字；分钟填大约的总时长（数字）。
4. 辣、含肉填“是”或“否”。
5. 蛋白质克、热量千卡：按一盘菜两人分、不含米饭，估算每人份的数字。
6. 原料：每行一样，只写食材名。用量：每行一样，写“食材 数量单位”。视频里没说的写“适量”，不要编造。
7. 做法：按顺序编号，一步一行，写清楚火候和时间。
8. 小贴士：视频里提到的窍门；没有就省略这一节。
9. 有多道菜时，菜与菜之间用单独一行 --- 隔开。

【菜名】
【分类】
【难度】
【分钟】
【辣】
【含肉】
【蛋白质克】
【热量千卡】
【来源】视频链接，没有就省略这一行
【简介】一两句话
【原料】
-
【用量】
-
【做法】
1.
【小贴士】
-
""".trimIndent()

    /** 识别出来的一道菜，可能带提醒或问题 */
    data class Parsed(
        val recipe: Recipe,
        val warnings: List<String>,
        val errors: List<String>,
        val catGuessed: Boolean,
    ) {
        val ok get() = errors.isEmpty()
    }

    private enum class F { NAME, CAT, DIFF, MIN, SPICY, MEAT, PROTEIN, KCAL, DESC, SOURCE, ING, AMT, STEP, TIP }

    private val ALIASES: List<Pair<F, List<String>>> = listOf(
        F.NAME to listOf("菜名", "名称", "菜品", "标题"),
        F.CAT to listOf("分类", "类别", "类型"),
        F.DIFF to listOf("难度"),
        F.MIN to listOf("分钟", "时间", "耗时", "用时", "烹饪时间", "时长"),
        F.SPICY to listOf("辣", "是否辣", "辣度", "辣不辣"),
        F.MEAT to listOf("含肉", "是否含肉", "荤素"),
        F.PROTEIN to listOf("蛋白质克", "蛋白质", "蛋白"),
        F.KCAL to listOf("热量千卡", "热量", "卡路里"),
        F.DESC to listOf("简介", "描述", "介绍"),
        F.SOURCE to listOf("来源", "来源链接", "链接", "视频", "视频链接"),
        F.ING to listOf("原料", "食材", "材料", "主料", "配料", "辅料"),
        F.AMT to listOf("用量", "分量", "配比", "食材用量"),
        F.STEP to listOf("做法", "步骤", "制作步骤", "操作", "制作方法"),
        F.TIP to listOf("小贴士", "贴士", "提示", "技巧", "注意", "注意事项"),
    )
    private val keyToField = HashMap<String, F>().apply { ALIASES.forEach { (f, ks) -> ks.forEach { put(it, f) } } }
    private val keyAlt = ALIASES.flatMap { it.second }.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    // 【菜名】xxx / [菜名] xxx / 菜名：xxx / ## 菜名
    private val HEADER = Regex("^\\s*(?:#{1,4}\\s*)?(?:【\\s*($keyAlt)\\s*】|\\[\\s*($keyAlt)\\s*]|($keyAlt)\\s*[:：])\\s*(.*)$")
    private val SEP = Regex("^\\s*(-{3,}|={3,}|—{2,}|_{3,}|\\*{3,})\\s*$")
    private val BULLET = Regex("^\\s*(?:[-*•·●○◦▪]|\\(?\\d{1,2}[.、)）:：]|[①-⑳]|第[一二三四五六七八九十0-9]{1,3}步[:：、]?)\\s*")
    private val FENCE = Regex("^\\s*```.*$")

    fun looksLikeTemplate(text: String) = text.lines().any { HEADER.containsMatchIn(it) && keyToField[HEADER.find(it)!!.groupValues.drop(1).take(3).firstOrNull { g -> g.isNotEmpty() }] == F.NAME }

    fun parse(raw: String): List<Parsed> {
        val lines = raw.replace('　', ' ').replace("\r", "").lines().filterNot { FENCE.matches(it) }
        // 按 --- 切；没有分隔线但出现多个“菜名”时，在每个菜名前切开
        val chunks = mutableListOf<MutableList<String>>(mutableListOf())
        for (ln in lines) {
            if (SEP.matches(ln)) { chunks += mutableListOf<String>(); continue }
            val h = HEADER.find(ln)
            val f = h?.let { keyToField[it.groupValues.subList(1, 4).first { g -> g.isNotEmpty() }] }
            if (f == F.NAME && chunks.last().any { x -> HEADER.find(x)?.let { m -> keyToField[m.groupValues.subList(1, 4).first { g -> g.isNotEmpty() }] == F.NAME } == true }) {
                chunks += mutableListOf<String>()
            }
            chunks.last() += ln
        }
        return chunks.filter { c -> c.any { it.isNotBlank() } }.mapIndexed { i, c -> parseOne(c, i) }
    }

    private fun parseOne(lines: List<String>, index: Int): Parsed {
        val scalars = HashMap<F, String>()
        val lists = HashMap<F, MutableList<String>>()
        var cur: F? = null
        for (ln in lines) {
            val m = HEADER.find(ln)
            if (m != null) {
                val key = m.groupValues.subList(1, 4).first { it.isNotEmpty() }
                val f = keyToField[key] ?: continue
                val rest = m.groupValues[4].trim()
                cur = f
                if (f in LISTS) {
                    val l = lists.getOrPut(f) { mutableListOf() }
                    if (rest.isNotEmpty()) l += if (f == F.STEP) listOf(rest) else rest.split(Regex("[、，,；;]")).map { it.trim() }.filter { it.isNotEmpty() }
                } else if (rest.isNotEmpty()) scalars[f] = rest
                continue
            }
            val t = ln.trim()
            if (t.isEmpty()) continue
            val item = t.replace(BULLET, "").trim()
            if (item.isEmpty()) continue
            val c = cur ?: continue
            when {
                c in LISTS -> lists.getOrPut(c) { mutableListOf() } += item
                c == F.DESC -> scalars[F.DESC] = listOfNotNull(scalars[F.DESC], item).joinToString("")
                scalars[c].isNullOrBlank() -> scalars[c] = item
            }
        }

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val name = scalars[F.NAME]?.trim().orEmpty()
        if (name.isEmpty()) errors += "没有菜名"

        var ing = lists[F.ING].orEmpty().filter { it != "适量" }
        var amt = lists[F.AMT].orEmpty()
        val steps = lists[F.STEP].orEmpty()
        val tips = lists[F.TIP].orEmpty()
        // 原料里直接写了数量（如“五花肉 300g”）：拆成原料名 + 用量
        if (amt.isEmpty() && ing.any { Regex("\\d|适量|少许|[一二两三四五六七八九十半][个只根片块条把勺]").containsMatchIn(it) }) {
            amt = ing
            ing = ing.mapNotNull { K.tokens(it).firstOrNull() }.distinct()
        }
        // 只给了用量没给原料：从用量里取名字
        if (ing.isEmpty() && amt.isNotEmpty()) ing = amt.mapNotNull { K.tokens(it).firstOrNull() }.distinct()
        if (ing.isEmpty()) errors += "没有原料"
        if (steps.isEmpty()) errors += "没有做法"

        val blob = name + " " + ing.joinToString(" ")
        val (cat, guessed) = parseCat(scalars[F.CAT], blob)
        if (guessed) warnings += if (scalars[F.CAT].isNullOrBlank()) "没写分类，猜的是「$cat」" else "分类「${scalars[F.CAT]}」不认识，猜的是「$cat」"
        val diff = parseDiff(scalars[F.DIFF])
        val minutes = parseMinutes(scalars[F.MIN])
        val spicy = parseBool(scalars[F.SPICY]) ?: SPICY.containsMatchIn(blob)
        val meat = parseBool(scalars[F.MEAT], meatWords = true) ?: (cat == "荤菜" || cat == "水产" || MEAT.containsMatchIn(blob.replace(NOT_MEAT, "")))
        val protein = firstInt(scalars[F.PROTEIN])
        val kcal = firstInt(scalars[F.KCAL])
        if (protein == null) warnings += "没有蛋白质估算"
        if (amt.any { it.contains("适量") }) warnings += "有用量写的是“适量”"
        val source = scalars[F.SOURCE]?.let { s -> Regex("https?://\\S+").find(s)?.value ?: s.takeIf { it.isNotBlank() } }

        val r = Recipe(
            id = "draft-$index", name = name, cat = cat, diff = diff, minutes = minutes, spicy = spicy, meat = meat,
            desc = scalars[F.DESC].orEmpty(), ingredients = ing, amounts = amt, steps = steps, tips = tips,
            src = "u", protein = protein, kcal = kcal, source = source,
        )
        return Parsed(r, warnings, errors, guessed)
    }

    private val LISTS = setOf(F.ING, F.AMT, F.STEP, F.TIP)
    private val SPICY = Regex("辣椒|小米辣|小米椒|干辣椒|辣椒面|剁椒|泡椒|花椒|麻辣|香辣|辣酱|朝天椒|红油|火锅底料")
    private val MEAT = Regex("猪|牛|羊|鸡|鸭|鹅|肉|排骨|培根|火腿|香肠|腊|鱼|虾|蟹|贝|鱿|蛤|海参|鳝")
    private val NOT_MEAT = Regex("鸡蛋|鸡精|鸡粉|蛋|肉桂|牛奶|牛油果|酸奶|蚝油|鱼露|猪油|牛油")

    fun parseCat(v: String?, blob: String): Pair<String, Boolean> {
        val s = v?.trim().orEmpty()
        if (s in K.RCATS) return s to false
        val c = when {
            s.isEmpty() -> null
            Regex("早餐|早饭|早点").containsMatchIn(s) -> "早餐"
            Regex("汤|羹|粥").containsMatchIn(s) -> "汤羹"
            Regex("水产|海鲜|鱼|虾|蟹").containsMatchIn(s) -> "水产"
            Regex("主食|面|饭|粉|饼|饺|包").containsMatchIn(s) -> "主食"
            Regex("素").containsMatchIn(s) -> "素菜"
            Regex("荤|肉").containsMatchIn(s) -> "荤菜"
            else -> null
        }
        if (c != null) return c to false
        val g = when {
            Regex("汤|羹|粥").containsMatchIn(blob) -> "汤羹"
            Regex("鱼|虾|蟹|贝|鱿").containsMatchIn(blob) -> "水产"
            MEAT.containsMatchIn(blob.replace(NOT_MEAT, "")) -> "荤菜"
            Regex("面|饭|饼|饺|包子|馒头|粉$").containsMatchIn(blob) -> "主食"
            else -> "素菜"
        }
        return g to true
    }

    fun parseDiff(v: String?): Int? {
        val s = v?.trim() ?: return null
        val stars = s.count { it == '★' || it == '⭐' }
        if (stars in 1..5) return stars
        firstInt(s)?.let { if (it in 1..5) return it }
        return when {
            s.contains("非常简单") || s.contains("入门") -> 1
            s.contains("简单") || s.contains("容易") -> 2
            s.contains("中") -> 3
            s.contains("较难") || s.contains("困难") || s.contains("难") -> 4
            else -> null
        }
    }

    fun parseMinutes(v: String?): Int? {
        val s = v?.trim() ?: return null
        val h = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:个)?小时").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
        val m = Regex("(\\d+)\\s*分").find(s)?.groupValues?.get(1)?.toIntOrNull()
        val total = when {
            h != null -> (h * 60).toInt() + (m ?: 0)
            s.contains("半小时") -> 30
            s.contains("一小时") || s.contains("一个小时") -> 60
            m != null -> m
            else -> firstInt(s)
        }
        return total?.takeIf { it in 1..1440 }
    }

    fun parseBool(v: String?, meatWords: Boolean = false): Boolean? {
        val s = v?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        if (meatWords) {
            if (s.contains("素")) return false
            if (s.contains("荤")) return true
        }
        return when {
            Regex("^(否|不|无|没有|no|false|0|不辣|不含)").containsMatchIn(s) -> false
            Regex("^(是|有|yes|true|1|辣|微辣|中辣|特辣|含)").containsMatchIn(s) -> true
            else -> null
        }
    }

    private fun firstInt(v: String?): Int? = v?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    /** 把一道菜写成模板文字（复制分享、给 GPT 改都方便） */
    fun format(r: Recipe): String = buildString {
        appendLine("【菜名】${r.name}")
        appendLine("【分类】${r.cat}")
        r.diff?.let { appendLine("【难度】$it") }
        r.minutes?.let { appendLine("【分钟】$it") }
        appendLine("【辣】${if (r.spicy) "是" else "否"}")
        appendLine("【含肉】${if (r.meat) "是" else "否"}")
        r.protein?.let { appendLine("【蛋白质克】$it") }
        r.kcal?.let { appendLine("【热量千卡】$it") }
        r.source?.let { appendLine("【来源】$it") }
        if (r.desc.isNotBlank()) appendLine("【简介】${r.desc}")
        appendLine("【原料】"); r.ingredients.forEach { appendLine("- $it") }
        if (r.amounts.isNotEmpty()) { appendLine("【用量】"); r.amounts.forEach { appendLine("- $it") } }
        appendLine("【做法】"); r.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        if (r.tips.isNotEmpty()) { appendLine("【小贴士】"); r.tips.forEach { appendLine("- $it") } }
    }.trimEnd()
}
