@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.chisha.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.chisha.*

private val BOTTOM = PaddingValues(bottom = 96.dp)

/* ======================= 冰箱 ======================= */

@Composable
fun FridgeTab(vm: AppViewModel, data: AppData, onGoCookUrgent: () -> Unit) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    var cat by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    val inv = data.inv
    val urgent = inv.filter { (K.daysLeft(it.exp) ?: 99) <= 3 }.sortedBy { K.daysLeft(it.exp) }
    val counts = inv.groupingBy { it.cat }.eachCount()
    val list = inv.filter { (cat.isEmpty() || it.cat == cat) && (q.isBlank() || it.name.contains(q.trim())) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        if (urgent.isNotEmpty()) item {
            val soon = urgent.filter { (K.daysLeft(it.exp) ?: 0) >= 0 }
            val expired = urgent.filter { (K.daysLeft(it.exp) ?: 0) < 0 }
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        listOfNotNull(
                            if (soon.isNotEmpty()) "${soon.size} 样快到期，先吃掉" else null,
                            if (expired.isNotEmpty()) "${expired.size} 样已过期" else null,
                        ).joinToString("，"),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (soon.isNotEmpty()) Text(
                        soon.joinToString("、") { "${it.name}（${K.expInfo(K.daysLeft(it.exp))!!.first}）" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (expired.isNotEmpty()) Text(
                        "已过期：" + expired.joinToString("、") { it.name } + "，看看还能不能吃，不能就删掉。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (soon.isNotEmpty()) FilledTonalButton(onClick = onGoCookUrgent, modifier = Modifier.padding(top = 12.dp)) {
                        Text("用快过期的做什么")
                    }
                }
            }
        }
        if (inv.any { it.sample }) item {
            Surface(
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("现在是示例数据，添加第一样食材后会自动清掉。", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    TextButton(onClick = { vm.clearSample(); toast.show("示例已清空") { vm.undo() } }) { Text("现在清空") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("找食材") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (inv.isNotEmpty()) item {
            val opts = listOf("") + K.ICATS.filter { (counts[it] ?: 0) > 0 }
            ChipRow(opts, { it == cat }, { cat = if (cat == it) "" else it }, label = { if (it.isEmpty()) "全部 ${inv.size}" else "$it ${counts[it]}" })
        }
        if (inv.isEmpty()) item { EmptyCard("冰箱还是空的。点右下角“添加食材”，把买回来的菜录进来。") }
        else if (list.isEmpty()) item { EmptyCard("没找到这样食材。") }
        K.ICATS.forEach { c ->
            val g = list.filter { it.cat == c }.sortedWith(compareBy<InvItem> { K.daysLeft(it.exp) ?: 9999 }.thenBy { it.name })
            if (g.isEmpty()) return@forEach
            item(key = "h-$c") { SectionTitle(c, g.size) }
            itemsIndexed(g, key = { _, it -> it.id }) { i, it -> InvRow(vm, it, i, g.size) }
        }
    }
}

@Composable
private fun InvRow(vm: AppViewModel, it: InvItem, i: Int, n: Int) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    GroupItem(i, n, onClick = { nav.sheet(Sheet.Item(it)) }) {
        Column(Modifier.weight(1f)) {
            Text(it.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(it.loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExpBadge(it.exp)
            }
        }
        QtyStepper(
            "${K.fmtQ(it.qty)} ${it.unit}",
            onMinus = { if (vm.changeQty(it.id, -K.stepOf(it.unit))) toast.show("${it.name} 用完了，已移出冰箱") { vm.undo() } },
            onPlus = { vm.changeQty(it.id, K.stepOf(it.unit)) },
        )
    }
}

/* ======================= 做什么 ======================= */

@Composable
fun CookTab(vm: AppViewModel, data: AppData, urgentOnly: Boolean, setUrgentOnly: (Boolean) -> Unit) {
    val recipes by vm.recipes.collectAsState()
    val nav = LocalNav.current
    var onlyFull by remember { mutableStateOf(false) }
    var noSpicy by remember { mutableStateOf(false) }
    val live = remember(data.inv) { liveInv(data.inv) }
    val urgent = data.inv.filter { val d = K.daysLeft(it.exp); d != null && d in 0..3 }
    val res = remember(recipes, live, onlyFull, noSpicy, urgentOnly, data.settings.seasonHave) {
        recipes.asSequence()
            .filter { !(noSpicy && it.spicy) }
            .mapNotNull { r -> analyze(r, live, data.settings.seasonHave)?.let { r to it } }
            .filter { !(onlyFull && it.second.missing.isNotEmpty()) }
            .filter { !(urgentOnly && urgent.isNotEmpty() && it.second.urgent.isEmpty()) }
            .sortedWith(compareByDescending<Pair<Recipe, Analysis>> { it.second.score }.thenBy { it.first.diff ?: 3 })
            .toList()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Text("按冰箱里现有的食材排序，快过期的优先用掉。", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (urgent.isNotEmpty()) item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("优先用掉", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(urgent.joinToString("、") { "${it.name}（${K.expInfo(K.daysLeft(it.exp))!!.first}）" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        item {
            val opts = buildList { if (urgent.isNotEmpty()) add("只看用快过期的"); add("只看不缺主料的"); add("不吃辣") }
            ChipRow(opts, {
                when (it) { "只看用快过期的" -> urgentOnly; "只看不缺主料的" -> onlyFull; else -> noSpicy }
            }, {
                when (it) { "只看用快过期的" -> setUrgentOnly(!urgentOnly); "只看不缺主料的" -> onlyFull = !onlyFull; else -> noSpicy = !noSpicy }
            }, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (live.isEmpty()) { item { EmptyCard("冰箱里还没有能用的食材。先去“冰箱”录几样。") }; return@LazyColumn }
        item { SectionTitle("能做", res.size) }
        if (res.isEmpty()) item { EmptyCard("没有符合条件的菜，关掉上面的筛选试试。") }
        val shown = res.take(80)
        itemsIndexed(shown, key = { _, p -> p.first.id }) { i, (r, a) ->
            RecipeRow(r, i, shown.size, r.name in data.favs, sub = { AnalysisLine(r, a) }) { nav.open(Route.Detail(r.id)) }
        }
    }
}

@Composable
fun AnalysisLine(r: Recipe, a: Analysis) {
    val cs = MaterialTheme.colorScheme
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = cs.primary)) { append("用上：") }
        a.used.forEachIndexed { i, it ->
            if (i > 0) append("、")
            if (it in a.urgent) withStyle(SpanStyle(color = cs.error, fontWeight = FontWeight.SemiBold)) { append(it.name) } else append(it.name)
        }
        append("\n")
        if (a.missing.isEmpty()) withStyle(SpanStyle(color = cs.primary)) { append("主料都有") }
        else append("还缺：" + a.missing.take(4).joinToString("、") + if (a.missing.size > 4) " 等" else "")
        r.minutes?.let { append(" · 约${it}分钟") }
    }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
}

/* ======================= 清单 ======================= */

@Composable
fun ShopTab(vm: AppViewModel, data: AppData) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val clip = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    val todo = data.shop.filter { !it.done }
    val done = data.shop.filter { it.done }
    fun add() {
        val parts = input.split(Regex("[，,、\\s]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        val n = vm.addToShop(parts, null)
        input = ""
        if (n == 0) toast.show("已经在清单里了")
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    placeholder = { Text("要买什么，多样用空格隔开") }, shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { add() }, modifier = Modifier.size(52.dp)) { Text("加", style = MaterialTheme.typography.titleMedium) }
            }
        }
        item {
            Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (todo.isEmpty()) "菜谱里点“把缺的加入清单”也会加到这里" else "要买 ${todo.size} 样",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (todo.isNotEmpty()) TextButton(onClick = { clip.setText(AnnotatedString(vm.shopText())); toast.show("已复制，可以粘贴到微信") }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("复制清单")
                }
            }
        }
        if (data.shop.isEmpty()) item { EmptyCard("清单是空的。在菜谱里点“把缺的加入清单”，或者在上面直接输入。") }
        else if (todo.isEmpty()) item { EmptyCard("都买齐了。") }
        K.ICATS.forEach { c ->
            val g = todo.filter { it.cat == c }
            if (g.isEmpty()) return@forEach
            item(key = "sh-$c") { SectionTitle(c, g.size) }
            itemsIndexed(g, key = { _, it -> it.id }) { i, it -> ShopRow(vm, it, i, g.size) }
        }
        if (done.isNotEmpty()) {
            item(key = "done-h") { SectionTitle("已买", done.size) }
            itemsIndexed(done, key = { _, it -> it.id }) { i, it -> ShopRow(vm, it, i, done.size) }
            item(key = "done-btn") {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.clearDoneShop(); toast.show("已清掉已买") { vm.undo() } }, Modifier.weight(1f)) { Text("清掉已买") }
                    Button(onClick = { nav.sheet(Sheet.Stock) }, Modifier.weight(1.4f)) { Text("已买的放进冰箱") }
                }
            }
        }
    }
}

@Composable
private fun ShopRow(vm: AppViewModel, it: ShopItem, i: Int, n: Int) {
    val toast = LocalToaster.current
    GroupItem(i, n, onClick = { vm.toggleShop(it.id) }) {
        Checkbox(checked = it.done, onCheckedChange = { _ -> vm.toggleShop(it.id) })
        Column(Modifier.weight(1f)) {
            Text(
                it.name, style = MaterialTheme.typography.titleMedium,
                color = if (it.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (it.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            if (it.forDishes.isNotEmpty()) Text("做 ${it.forDishes.joinToString("、")} 用", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.removeShop(it.id); toast.show("已删掉 ${it.name}") { vm.undo() } }) { Icon(Icons.Outlined.Close, "删掉") }
    }
}

/* ======================= 抽签 ======================= */

@Composable
fun DrawTab(vm: AppViewModel, data: AppData) {
    val recipes by vm.recipes.collectAsState()
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val f = data.filters
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    val live = remember(data.inv) { liveInv(data.inv) }

    fun pool(cats: List<String>): List<Recipe> {
        val cut = System.currentTimeMillis() - 7L * 86_400_000
        val recent = if (f.avoidRecent) data.eaten.filter { it.t >= cut }.map { it.n }.toSet() else emptySet()
        return recipes.filter {
            it.cat in cats && it.name !in recent &&
                !(f.maxDiff < 5 && (it.diff ?: 3) > f.maxDiff) &&
                !(f.maxTime > 0 && (it.minutes == null || it.minutes > f.maxTime)) &&
                !(f.noSpicy && it.spicy) && !(f.vegOnly && it.meat)
        }
    }
    fun weighted(p: List<Recipe>): Recipe? {
        if (p.isEmpty()) return null
        val w = p.map { r -> 1.0 + (analyze(r, live, data.settings.seasonHave)?.used?.size ?: 0) * 2.0 }
        var x = Math.random() * w.sum()
        for (i in p.indices) { x -= w[i]; if (x <= 0) return p[i] }
        return p.last()
    }

    var current by remember { mutableStateOf<Recipe?>(null) }
    val drawPool = pool(f.cats)
    LaunchedEffect(Unit) { if (current == null) current = drawPool.randomOrNull() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                listOf("抽一道", "配一桌").forEachIndexed { i, s ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(s) }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
                Column {
                    ListItem(
                        headlineContent = { Text("条件") },
                        supportingContent = {
                            Text(listOfNotNull(
                                if (f.cats.size == K.RCATS.size) "全部类别" else f.cats.joinToString("·"),
                                if (f.maxDiff < 5) "难度≤${f.maxDiff}" else null,
                                if (f.maxTime > 0) "${f.maxTime}分钟内" else null,
                                if (f.noSpicy) "不辣" else null, if (f.vegOnly) "素" else null,
                            ).joinToString(" / "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Icon(if (showFilters) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickableNoRipple { showFilters = !showFilters },
                    )
                    if (showFilters) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("吃哪类", style = MaterialTheme.typography.labelLarge)
                        ChipFlow(K.RCATS, { it in f.cats }, { c ->
                            val n = if (c in f.cats) f.cats - c else f.cats + c
                            vm.setFilters(f.copy(cats = n.ifEmpty { listOf(c) }))
                        })
                        Text("难度", style = MaterialTheme.typography.labelLarge)
                        ChipFlow(listOf("简单", "中等以下", "不限"), {
                            (it == "简单" && f.maxDiff == 2) || (it == "中等以下" && f.maxDiff == 3) || (it == "不限" && f.maxDiff == 5)
                        }, { vm.setFilters(f.copy(maxDiff = when (it) { "简单" -> 2; "中等以下" -> 3; else -> 5 })) })
                        Text("时间", style = MaterialTheme.typography.labelLarge)
                        val times = listOf(15 to "15分钟内", 30 to "半小时内", 60 to "1小时内", 0 to "不限")
                        ChipFlow(times.map { it.second }, { l -> times.first { it.second == l }.first == f.maxTime }, { l ->
                            vm.setFilters(f.copy(maxTime = times.first { it.second == l }.first))
                        })
                        Text("口味", style = MaterialTheme.typography.labelLarge)
                        ChipFlow(listOf("不吃辣", "只要素的", "避开7天内吃过的"), {
                            when (it) { "不吃辣" -> f.noSpicy; "只要素的" -> f.vegOnly; else -> f.avoidRecent }
                        }, {
                            vm.setFilters(when (it) {
                                "不吃辣" -> f.copy(noSpicy = !f.noSpicy)
                                "只要素的" -> f.copy(vegOnly = !f.vegOnly)
                                else -> f.copy(avoidRecent = !f.avoidRecent)
                            })
                        })
                    }
                }
            }
        }
        if (mode == 0) {
            item {
                val r = current
                ElevatedCard(
                    onClick = { r?.let { nav.open(Route.Detail(it.id)) } },
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 36.dp, bottomEnd = 36.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 240.dp),
                ) {
                    if (r == null) {
                        Text("没有符合条件的菜了，放宽一下条件试试。", Modifier.padding(28.dp))
                    } else Column(Modifier.padding(24.dp).animateContentSize()) {
                        Text("今日签", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                        Text(r.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(r.cat); val m = recipeMeta(r); if (m.isNotEmpty()) Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (r.desc.isNotBlank()) Text(r.desc, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                        val a = analyze(r, live, data.settings.seasonHave)
                        Text(
                            (if (a != null) "冰箱里有：${a.used.joinToString("、") { it.name }}" + (if (a.missing.isEmpty()) " · 主料齐了" else " · 缺 ${a.missing.size} 样") + "\n" else "") +
                                "当前条件下有 ${drawPool.size} 道可抽 · 点签看做法",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = {
                        val p = drawPool.filter { it.id != current?.id }
                        current = p.randomOrNull() ?: drawPool.randomOrNull()
                    }, Modifier.weight(1f).height(56.dp)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("换一个") }
                    Button(onClick = { current?.let { nav.open(Route.Detail(it.id)) } }, Modifier.weight(1.3f).height(56.dp), enabled = current != null) { Text("看做法") }
                }
            }
        } else {
            item {
                val ppl = data.ppl
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    QtyStepper("$ppl 人", { vm.setPpl(ppl - 1) }, { vm.setPpl(ppl + 1) })
                    Spacer(Modifier.weight(1f))
                }
            }
            item {
                MealPlan(vm, data, live, ::pool, ::weighted)
            }
        }
    }
}

@Composable
private fun MealPlan(
    vm: AppViewModel, data: AppData, live: List<InvItem>,
    pool: (List<String>) -> List<Recipe>, weighted: (List<Recipe>) -> Recipe?,
) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val f = data.filters
    val ppl = data.ppl
    val meat = (Math.round(ppl / 2.0).toInt()).coerceIn(1, 4)
    val veg = (ppl / 2).coerceIn(1, 3)
    val soup = if (ppl >= 3) 1 else 0
    val kinds = (if (f.vegOnly) emptyList() else List(meat) { "荤" }) + List(veg + if (f.vegOnly) meat else 0) { "素" } + List(soup) { "汤" }

    fun pick(kind: String, chosen: List<Recipe?>): Recipe? {
        val cats = when (kind) { "荤" -> listOf("荤菜", "水产"); "素" -> listOf("素菜"); else -> listOf("汤羹") }
        val taken = chosen.filterNotNull().map { it.name }.toSet()
        val fish = chosen.count { it?.cat == "水产" }
        val hot = chosen.count { it?.spicy == true }
        val p = pool(cats).filter { it.name !in taken }
        val q = p.filter { !(it.cat == "水产" && fish >= 1) && !(it.spicy && hot >= 2) }.ifEmpty { p }
        return weighted(q)
    }
    fun build(): List<Recipe?> {
        val out = mutableListOf<Recipe?>()
        kinds.forEach { out += pick(it, out) }
        return out
    }
    var meal by remember(kinds.joinToString(), f) { mutableStateOf(build()) }

    Column {
        Text("${if (f.vegOnly) "全素" else "$meat 荤 $veg 素"}${if (soup > 0) " 1 汤" else ""}，冰箱里有料的菜更容易被抽到",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        meal.forEachIndexed { i, r ->
            GroupItem(i, meal.size, onClick = { r?.let { nav.open(Route.Detail(it.id)) } }) {
                Text(kinds.getOrElse(i) { "" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    if (r == null) Text("没有符合条件的菜", style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val a = analyze(r, live, data.settings.seasonHave)
                        Text((if (a != null) "有 ${a.used.joinToString("、") { it.name }}" else "冰箱里没有相关食材") + (r.minutes?.let { " · 约${it}分钟" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalIconButton(onClick = {
                    val others = meal.toMutableList(); others[i] = null
                    meal = meal.toMutableList().also { it[i] = pick(kinds[i], others) }
                }) { Icon(Icons.Outlined.Refresh, "换这道") }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                var n = 0
                meal.filterNotNull().forEach { r -> n += vm.addToShop(missingFor(r, live, data.settings.seasonHave), r.name) }
                toast.show(if (n > 0) "已加入清单 $n 样" else "这桌不缺主料，或者都已在清单里")
            }, Modifier.weight(1f).height(56.dp)) { Text("缺的加入清单") }
            Button(onClick = { meal = build() }, Modifier.weight(1f).height(56.dp)) { Text("整桌换") }
        }
    }
}

/* ======================= 菜谱 ======================= */

@Composable
fun BookTab(vm: AppViewModel, data: AppData) {
    val recipes by vm.recipes.collectAsState()
    val nav = LocalNav.current
    var q by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableStateOf("") }
    fun lastEaten(n: String) = data.eaten.filter { it.n == n }.maxOfOrNull { it.t } ?: 0L
    var list = recipes
    if (mode == 1) list = list.filter { it.name in data.favs }
    if (mode == 2) list = list.filter { lastEaten(it.name) > 0 }.sortedByDescending { lastEaten(it.name) }
    if (cat.isNotEmpty()) list = list.filter { it.cat == cat }
    if (q.isNotBlank()) list = list.filter { r -> r.name.contains(q.trim()) || r.ingredients.any { it.contains(q.trim()) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("搜菜名或食材") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("全部", "收藏", "吃过").forEachIndexed { i, s ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(s) }
                }
            }
        }
        item { ChipRow(listOf("") + K.RCATS, { it == cat }, { cat = if (cat == it) "" else it }, label = { it.ifEmpty { "全部" } }, modifier = Modifier.padding(top = 8.dp)) }
        item { SectionTitle("${list.size} 道") }
        if (list.isEmpty()) item {
            EmptyCard(when (mode) {
                1 -> "还没有收藏。打开一道菜，点右上角的心。"
                2 -> "还没有记录。做完一道菜点“做了这道”会记在这里。"
                else -> "没找到，换个词试试，或者点右下角“新菜”自己加。"
            })
        }
        itemsIndexed(list, key = { _, r -> r.id }) { i, r ->
            val le = lastEaten(r.name)
            RecipeRow(r, i, list.size, r.name in data.favs, sub = if (mode == 2 && le > 0) ({
                Text(agoText(le) + "吃过", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) else null) { nav.open(Route.Detail(r.id)) }
        }
    }
}

fun agoText(t: Long): String {
    val d = java.time.temporal.ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now(),
    )
    return when { d <= 0 -> "今天"; d == 1L -> "昨天"; else -> "${d}天前" }
}

/** 不带水波纹的点击（用在列表标题行上） */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick,
    )
