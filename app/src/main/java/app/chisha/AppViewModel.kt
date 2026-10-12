package app.chisha

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val prettyJson = Json { ignoreUnknownKeys = false; encodeDefaults = true; prettyPrint = true }

    private val dataFile = File(app.filesDir, "data.json")
    private val recipeFile = File(app.filesDir, "recipes.json")

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 撤销：保存上一次库存快照 */
    private var undoInv: List<InvItem>? = null
    private var undoShop: List<ShopItem>? = null

    init {
        viewModelScope.launch {
            val (d, r) = withContext(Dispatchers.IO) { loadAll() }
            _data.value = d
            _recipes.value = r
            _loaded.value = true
            tick()
        }
    }

    private fun defaultRecipes(): List<Recipe> {
        val text = getApplication<Application>().assets.open("recipes.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), text)
    }

    private fun loadAll(): Pair<AppData, List<Recipe>> {
        val r = try {
            if (recipeFile.exists()) json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), recipeFile.readText()) else defaultRecipes()
        } catch (e: Exception) {
            defaultRecipes()
        }
        var d = try {
            if (dataFile.exists()) json.decodeFromString(AppData.serializer(), dataFile.readText()) else AppData()
        } catch (e: Exception) {
            AppData()
        }
        if (!d.seeded) {
            d = d.copy(inv = sampleInv(), seeded = true)
        }
        // 旧版保存的菜谱没有蛋白质数据，从自带菜谱补上
        val defs = runCatching { defaultRecipes().associateBy { it.name } }.getOrDefault(emptyMap())
        val r2 = r.map { x -> if (x.protein == null && defs[x.name] != null) x.copy(protein = defs[x.name]!!.protein, kcal = defs[x.name]!!.kcal) else x }
        return d to r2
    }

    private fun sampleInv(): List<InvItem> {
        val s = listOf(
            listOf("鸡蛋", "蛋奶豆", 8.0, "个", "冷藏", 20), listOf("西红柿", "蔬菜", 3.0, "个", "冷藏", 4),
            listOf("土豆", "蔬菜", 4.0, "个", "常温", 14), listOf("菠菜", "蔬菜", 1.0, "把", "冷藏", 1),
            listOf("豆腐", "蛋奶豆", 1.0, "盒", "冷藏", 2), listOf("五花肉", "肉禽", 500.0, "g", "冷冻", 60),
            listOf("青椒", "蔬菜", 2.0, "个", "冷藏", 5), listOf("牛奶", "蛋奶豆", 1.0, "L", "冷藏", -1),
        )
        return s.mapIndexed { i, x ->
            InvItem("s$i", x[0] as String, x[1] as String, x[2] as Double, x[3] as String, x[4] as String, K.ymd(x[5] as Int), System.currentTimeMillis(), sample = true)
        }
    }

    private fun persistData() {
        val snapshot = _data.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dataFile.writeText(json.encodeToString(AppData.serializer(), snapshot)) }
        }
    }

    private fun persistRecipes() {
        val snapshot = _recipes.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { recipeFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), snapshot)) }
        }
    }

    private inline fun edit(f: (AppData) -> AppData) {
        _data.update(f)
        persistData()
    }

    private fun newId(prefix: String) = prefix + System.currentTimeMillis().toString(36) + (0..9999).random().toString(36)

    /* ================= 消耗品：按天自动扣、快用完自动进清单 ================= */

    fun tick() {
        if (!_loaded.value) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        var changed = false
        val toShop = mutableListOf<String>()
        val inv = _data.value.inv.map { it0 ->
            var it = it0
            val dose = it.dose; val per = it.perDay
            if (dose != null && per != null && dose > 0 && per > 0) {
                val since = it.autoAt
                if (since == null) { it = it.copy(autoAt = now); changed = true }
                else {
                    val days = ((now - since) / day).toInt()
                    if (days >= 1) {
                        it = it.copy(qty = round2((it.qty - days * dose * per).coerceAtLeast(0.0)), autoAt = since + days * day)
                        changed = true
                    }
                }
                val left = K.supplyDays(it) ?: 99
                if (left <= 5 && !it.reordered) {
                    toShop += it.name
                    it = it.copy(reordered = true); changed = true
                }
            }
            it
        }
        if (changed) edit { it.copy(inv = inv) }
        if (toShop.isNotEmpty()) addToShop(toShop, "快用完了")
    }

    /** 消耗品“用了一次” */
    fun useOnce(id: String): Boolean {
        val it0 = _data.value.inv.firstOrNull { it.id == id } ?: return false
        val d = it0.dose ?: return false
        edit { dd -> dd.copy(inv = dd.inv.map { if (it.id == id) it.copy(qty = round2((it.qty - d).coerceAtLeast(0.0))) else it }) }
        tick()
        return true
    }

    /* ================= 库存 ================= */

    val hasSample: Boolean get() = _data.value.inv.any { it.sample }

    fun clearSample() {
        undoInv = _data.value.inv
        edit { it.copy(inv = it.inv.filterNot { x -> x.sample }) }
    }

    /** 新增或合并；返回合并到的那一项（新增时为 null） */
    fun addInv(
        name: String, cat: String, qty: Double, unit: String, loc: String, exp: String?,
        dose: Double? = null, perDay: Double? = null, roast: String? = null,
    ): InvItem? {
        var merged: InvItem? = null
        edit { d ->
            val base = d.inv.filterNot { it.sample }
            val same = base.firstOrNull { it.name == name && it.unit == unit && it.loc == loc }
            val list = if (same != null) {
                val nq = ((same.qty + qty) * 100).roundToInt() / 100.0
                val ne = if (exp != null && (same.exp == null || exp < same.exp)) exp else same.exp
                merged = same.copy(
                    qty = nq, exp = ne, reordered = false,
                    dose = dose ?: same.dose, perDay = perDay ?: same.perDay, roast = roast ?: same.roast,
                    autoAt = if ((dose ?: same.dose) != null) (same.autoAt ?: System.currentTimeMillis()) else null,
                )
                base.map { if (it.id == same.id) merged!! else it }
            } else {
                base + InvItem(
                    newId("i"), name, cat, round2(qty), unit, loc, exp, System.currentTimeMillis(),
                    dose = dose, perDay = perDay, roast = roast, autoAt = if (dose != null && perDay != null) System.currentTimeMillis() else null,
                )
            }
            d.copy(inv = list)
        }
        return merged
    }

    fun updateInv(item: InvItem) {
        edit { d ->
            d.copy(inv = d.inv.map { old ->
                if (old.id != item.id) old else {
                    val consumable = item.dose != null && item.perDay != null
                    item.copy(
                        sample = false,
                        reordered = if (item.qty > old.qty) false else old.reordered,
                        autoAt = if (consumable) (old.autoAt ?: System.currentTimeMillis()) else null,
                    )
                }
            })
        }
        tick()
    }

    fun deleteInv(id: String) {
        undoInv = _data.value.inv
        edit { d -> d.copy(inv = d.inv.filterNot { it.id == id }) }
    }

    /** 加减数量；减到 0 时移出冰箱并返回 true */
    fun changeQty(id: String, delta: Double): Boolean {
        val it0 = _data.value.inv.firstOrNull { it.id == id } ?: return false
        val nq = round2((it0.qty + delta).coerceAtLeast(0.0))
        return if (nq <= 0.0 && it0.dose == null) {
            deleteInv(id); true
        } else {
            edit { d -> d.copy(inv = d.inv.map { if (it.id == id) it.copy(qty = nq, reordered = if (delta > 0) false else it.reordered) else it }) }
            if (it0.dose != null) tick()
            false
        }
    }

    fun clearFridge() {
        undoInv = _data.value.inv
        edit { it.copy(inv = emptyList()) }
    }

    fun canUndo() = undoInv != null || undoShop != null

    fun undo() {
        undoInv?.let { u -> edit { it.copy(inv = u) } }
        undoShop?.let { u -> edit { it.copy(shop = u) } }
        undoInv = null; undoShop = null
    }

    /** 做完一道菜：按实际用量扣减，返回用完的食材名 */
    fun deduct(uses: Map<String, Double>, dish: String): List<String> {
        undoInv = _data.value.inv; undoShop = null
        val gone = mutableListOf<String>()
        edit { d ->
            val inv = d.inv.mapNotNull { item ->
                val use = uses[item.id] ?: return@mapNotNull item
                if (use <= 0) return@mapNotNull item
                val nq = round2((item.qty - use).coerceAtLeast(0.0))
                if (nq <= 0) { gone += item.name; null } else item.copy(qty = nq)
            }
            d.copy(inv = inv)
        }
        markEaten(dish)
        return gone
    }

    /* ================= 吃过 & 收藏 ================= */

    fun markEaten(dish: String) = edit { d ->
        val today = java.time.LocalDate.now()
        val dup = d.eaten.any { it.n == dish && java.time.Instant.ofEpochMilli(it.t).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }
        if (dup) d else d.copy(eaten = (d.eaten + Eaten(dish, System.currentTimeMillis())).takeLast(300))
    }

    fun toggleFav(name: String) = edit { d -> d.copy(favs = if (name in d.favs) d.favs - name else d.favs + name) }

    /* ================= 购物清单 ================= */

    fun inShop(name: String): ShopItem? = _data.value.shop.firstOrNull { !it.done && K.sameThing(it.name, name) }

    /** 返回新加了几样 */
    fun addToShop(names: List<String>, dish: String?): Int {
        var n = 0
        edit { d ->
            val list = d.shop.toMutableList()
            for (raw in names) {
                val name = raw.trim()
                if (name.isEmpty()) continue
                val idx = list.indexOfFirst { !it.done && K.sameThing(it.name, name) }
                if (idx >= 0) {
                    val ex = list[idx]
                    if (dish != null && dish !in ex.forDishes) list[idx] = ex.copy(forDishes = ex.forDishes + dish)
                } else {
                    list += ShopItem(newId("p"), name, K.guessCat(name), listOfNotNull(dish), false, System.currentTimeMillis())
                    n++
                }
            }
            d.copy(shop = list)
        }
        return n
    }

    fun toggleShop(id: String) = edit { d -> d.copy(shop = d.shop.map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun removeShop(id: String) {
        undoShop = _data.value.shop; undoInv = null
        edit { d -> d.copy(shop = d.shop.filterNot { it.id == id }) }
    }

    fun clearDoneShop() {
        undoShop = _data.value.shop; undoInv = null
        edit { d -> d.copy(shop = d.shop.filterNot { it.done }) }
    }

    data class StockRow(val item: ShopItem, val qty: Double, val unit: String, val on: Boolean)

    fun stockIn(rows: List<StockRow>): Int {
        undoInv = _data.value.inv; undoShop = _data.value.shop
        var n = 0
        for (r in rows) {
            if (!r.on || r.qty <= 0) continue
            val loc = K.guessLoc(r.item.name, r.item.cat)
            val cd = if (K.unitKind(r.unit) != "count") K.consumableDefaults(r.item.name) else null
            addInv(r.item.name, r.item.cat, r.qty, r.unit, loc, K.ymd(K.guessDays(r.item.name, r.item.cat, loc)),
                dose = cd?.first, perDay = cd?.second, roast = if (K.isCoffee(r.item.name)) K.ymd(0) else null)
            n++
        }
        val ids = rows.map { it.item.id }.toSet()
        edit { d -> d.copy(shop = d.shop.filterNot { it.id in ids }) }
        return n
    }

    fun shopText(): String {
        val todo = _data.value.shop.filter { !it.done }
        val now = java.time.LocalDate.now()
        val sb = StringBuilder("购物清单（${now.monthValue}月${now.dayOfMonth}日）")
        for (c in K.ICATS) {
            val g = todo.filter { it.cat == c }
            if (g.isEmpty()) continue
            sb.append("\n【").append(c).append("】")
            g.forEach { sb.append("\n□ ").append(it.name) }
        }
        return sb.toString()
    }

    /* ================= 设置 & 抽签条件 ================= */

    fun setMode(m: String) = edit { it.copy(settings = it.settings.copy(mode = m)) }
    fun setSeasonHave(v: Boolean) = edit { it.copy(settings = it.settings.copy(seasonHave = v)) }
    fun setSkin(v: String) = edit { it.copy(settings = it.settings.copy(skin = v)) }
    fun setMuscle(v: Boolean) = edit { it.copy(settings = it.settings.copy(muscle = v)) }
    fun setFilters(f: DrawFilters) = edit { it.copy(filters = f) }
    fun setPpl(n: Int) = edit { it.copy(ppl = n.coerceIn(1, 10)) }

    /* ================= 菜谱增删改 ================= */

    fun recipe(id: String): Recipe? = _recipes.value.firstOrNull { it.id == id }

    /** 校验一份菜谱，返回错误提示；没问题返回 null */
    fun validate(r: Recipe, isNew: Boolean): String? {
        if (r.name.isBlank()) return "菜名不能为空"
        if (r.cat !in K.RCATS) return "分类只能是：${K.RCATS.joinToString("、")}"
        if (r.ingredients.none { it.isNotBlank() }) return "至少要有一样原料"
        if (r.steps.none { it.isNotBlank() }) return "至少要有一步做法"
        if (r.diff != null && r.diff !in 1..5) return "难度只能是 1 到 5"
        if (r.minutes != null && r.minutes !in 1..1440) return "时间要在 1 到 1440 分钟之间"
        if (r.protein != null && r.protein !in 0..300) return "蛋白质要在 0 到 300 克之间"
        if (r.kcal != null && r.kcal !in 0..5000) return "热量要在 0 到 5000 千卡之间"
        val clash = _recipes.value.any { it.name == r.name.trim() && it.id != r.id }
        if (clash) return "已经有一道「${r.name.trim()}」了，换个名字"
        return null
    }

    fun saveRecipe(r: Recipe): String? {
        val clean = r.copy(
            name = r.name.trim(), desc = r.desc.trim(),
            ingredients = r.ingredients.map { it.trim() }.filter { it.isNotEmpty() },
            amounts = r.amounts.map { it.trim() }.filter { it.isNotEmpty() },
            steps = r.steps.map { it.trim() }.filter { it.isNotEmpty() },
            tips = r.tips.map { it.trim() }.filter { it.isNotEmpty() },
        )
        val isNew = _recipes.value.none { it.id == clean.id }
        validate(clean, isNew)?.let { return it }
        val old = _recipes.value.firstOrNull { it.id == clean.id }
        _recipes.update { list -> if (isNew) list + clean else list.map { if (it.id == clean.id) clean else it } }
        if (old != null && old.name != clean.name) {
            edit { d ->
                d.copy(
                    favs = if (old.name in d.favs) d.favs - old.name + clean.name else d.favs,
                    eaten = d.eaten.map { if (it.n == old.name) it.copy(n = clean.name) else it },
                )
            }
        }
        persistRecipes()
        return null
    }

    fun newRecipeId() = newId("u")

    private var undoRecipes: List<Recipe>? = null

    fun deleteRecipe(id: String) {
        undoRecipes = _recipes.value
        _recipes.update { list -> list.filterNot { it.id == id } }
        persistRecipes()
    }

    fun undoRecipe() {
        undoRecipes?.let { _recipes.value = it; persistRecipes() }
        undoRecipes = null
    }

    fun resetRecipes() {
        undoRecipes = _recipes.value
        _recipes.value = defaultRecipes()
        persistRecipes()
    }

    /* ---------- 菜谱文件 ---------- */

    fun exportRecipes(uri: Uri): Result<Int> = runCatching {
        val file = RecipeFile(
            format = RECIPE_FILE_FORMAT, version = RECIPE_FILE_VERSION,
            note = "可以修改菜名、分类、难度、分钟、辣、含肉、简介、原料、用量、做法、小贴士、蛋白质克、热量千卡的内容，也可以增删菜。字段名和结构不要改，否则导入会被拒绝。分类只能是：荤菜、素菜、水产、汤羹、主食、早餐。",
            recipes = _recipes.value.map {
                RecipeEntry(it.name, it.cat, it.diff, it.minutes, it.spicy, it.meat, it.desc, it.ingredients, it.amounts, it.steps, it.tips, it.protein, it.kcal)
            },
        )
        val text = prettyJson.encodeToString(RecipeFile.serializer(), file)
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        file.recipes.size
    }

    data class ImportPreview(val entries: List<RecipeEntry>, val added: Int, val updated: Int)

    /** 读文件并逐项校验；任何一处不合格就整份拒绝，返回失败原因 */
    fun readRecipeFile(uri: Uri): Result<ImportPreview> {
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }.removePrefix("﻿")
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("读不了这个文件"))
        }
        val file = try {
            prettyJson.decodeFromString(RecipeFile.serializer(), text)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val hint = Regex("'([^']+)'").find(msg)?.groupValues?.get(1)
            return Result.failure(IllegalArgumentException(
                if (hint != null) "文件格式不对：字段「$hint」有问题。只能改内容，不能改字段名和结构。"
                else "文件格式不对。请用 App 导出的菜谱文件，只改内容，不改结构。"
            ))
        }
        if (file.format != RECIPE_FILE_FORMAT) return Result.failure(IllegalArgumentException("这不是「今天吃啥」的菜谱文件"))
        if (file.version != 1 && file.version != RECIPE_FILE_VERSION) return Result.failure(IllegalArgumentException("菜谱文件版本是 ${file.version}，这个 App 只认版本 $RECIPE_FILE_VERSION"))
        if (file.recipes.isEmpty()) return Result.failure(IllegalArgumentException("文件里一道菜都没有"))
        val seen = HashSet<String>()
        file.recipes.forEachIndexed { i, e ->
            val label = "第 ${i + 1} 道" + if (e.name.isNotBlank()) "「${e.name}」" else ""
            if (e.name.isBlank()) return Result.failure(IllegalArgumentException("$label：菜名不能为空"))
            if (!seen.add(e.name.trim())) return Result.failure(IllegalArgumentException("$label：菜名重复了"))
            if (e.cat !in K.RCATS) return Result.failure(IllegalArgumentException("$label：分类「${e.cat}」不对，只能是${K.RCATS.joinToString("、")}"))
            if (e.ingredients.none { it.isNotBlank() }) return Result.failure(IllegalArgumentException("$label：原料不能为空"))
            if (e.steps.none { it.isNotBlank() }) return Result.failure(IllegalArgumentException("$label：做法不能为空"))
            if (e.diff != null && e.diff !in 1..5) return Result.failure(IllegalArgumentException("$label：难度只能是 1 到 5"))
            if (e.minutes != null && e.minutes !in 1..1440) return Result.failure(IllegalArgumentException("$label：分钟要在 1 到 1440 之间"))
            if (e.protein != null && e.protein !in 0..300) return Result.failure(IllegalArgumentException("$label：蛋白质克要在 0 到 300 之间"))
            if (e.kcal != null && e.kcal !in 0..5000) return Result.failure(IllegalArgumentException("$label：热量千卡要在 0 到 5000 之间"))
        }
        val names = _recipes.value.map { it.name }.toSet()
        val upd = file.recipes.count { it.name.trim() in names }
        return Result.success(ImportPreview(file.recipes, file.recipes.size - upd, upd))
    }

    /** replaceAll=true 用文件替换全部；否则同名覆盖、新菜追加 */
    fun applyImport(p: ImportPreview, replaceAll: Boolean) {
        undoRecipes = _recipes.value
        val byName = _recipes.value.associateBy { it.name }
        val incoming = p.entries.map { e ->
            val n = e.name.trim()
            Recipe(
                id = byName[n]?.id ?: newId("u"), name = n, cat = e.cat, diff = e.diff, minutes = e.minutes,
                spicy = e.spicy, meat = e.meat, desc = e.desc.trim(),
                ingredients = e.ingredients.map { it.trim() }.filter { it.isNotEmpty() },
                amounts = e.amounts.map { it.trim() }.filter { it.isNotEmpty() },
                steps = e.steps.map { it.trim() }.filter { it.isNotEmpty() },
                tips = e.tips.map { it.trim() }.filter { it.isNotEmpty() },
                src = byName[n]?.src ?: "u",
                protein = e.protein, kcal = e.kcal,
            )
        }
        _recipes.value = if (replaceAll) incoming else {
            val inc = incoming.associateBy { it.name }
            _recipes.value.map { inc[it.name] ?: it } + incoming.filter { it.name !in byName }
        }
        persistRecipes()
    }

    /* ---------- 从网页版迁移 ---------- */

    fun importWebBackup(text: String): Result<Int> = runCatching {
        val b = json.decodeFromString(WebBackup.serializer(), text.trim())
        undoInv = _data.value.inv; undoShop = _data.value.shop
        edit { d ->
            d.copy(
                inv = b.inv.filter { it.name.isNotBlank() && it.qty > 0 }.map {
                    InvItem(newId("i"), it.name, if (it.cat in K.ICATS) it.cat else "其他", it.qty, it.unit, if (it.loc in K.LOCS) it.loc else "冷藏", it.exp, System.currentTimeMillis())
                },
                shop = b.shop.filter { it.name.isNotBlank() }.map { ShopItem(newId("p"), it.name, if (it.cat in K.ICATS) it.cat else "其他", it.forDishes, it.done, System.currentTimeMillis()) },
                favs = d.favs + b.favs,
                eaten = (d.eaten + b.eaten).sortedBy { it.t }.takeLast(300),
            )
        }
        b.inv.size
    }

    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0
}
