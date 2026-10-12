@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.chisha.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chisha.*

/* ======================= 菜谱详情 ======================= */

@Composable
fun RecipeDetailScreen(vm: AppViewModel, r: Recipe, data: AppData, snackbar: SnackbarHostState) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var menu by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val live = remember(data.inv) { liveInv(data.inv) }
    val miss = missingFor(r, live, data.settings.seasonHave)
    val fav = r.name in data.favs
    val cs = MaterialTheme.colorScheme

    SkinScaffold(
        title = r.name,
        snackbar = snackbar,
        onBack = nav.back,
        actions = {
                    IconButton(onClick = { vm.toggleFav(r.name); toast.show(if (fav) "已取消收藏" else "已收藏") }) {
                        if (fav) Icon(Icons.Filled.Favorite, "取消收藏", tint = cs.tertiary) else Icon(Icons.Outlined.FavoriteBorder, "收藏")
                    }
                    IconButton(onClick = { nav.open(EditKey(r.id)) }) { Icon(Icons.Outlined.Edit, "编辑") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("删除这道菜") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmDel = true })
                }
            }
        },
        bottomBar = {
            SkinBottomPanel {
                SkinPrimaryButton("做了这道", { nav.sheet(Sheet.Deduct(r)) }, Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        val apple = LocalSkin.current == Skin.APPLE
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
            if (apple) item {
                Text(r.name, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(r.cat)
                    r.protein?.let {
                        if (it >= K.HIGH_PROTEIN) Tag("高蛋白 ${it}g", cs.primaryContainer, cs.onPrimaryContainer)
                        else Tag("蛋白 ${it}g", cs.surfaceContainerHighest, cs.onSurface)
                    }
                    r.kcal?.let { Tag("约 $it 千卡", cs.surfaceContainerHighest, cs.onSurface) }
                    r.diff?.let { Tag("难度 " + "★".repeat(it), cs.tertiaryContainer, cs.onTertiaryContainer) }
                    r.minutes?.let { Tag("约 $it 分钟", cs.surfaceContainerHighest, cs.onSurface) }
                    if (r.spicy) Tag("辣", cs.errorContainer, cs.onErrorContainer)
                    if (r.meat) Tag("含肉", cs.surfaceContainerHighest, cs.onSurface)
                }
            }
            if (r.desc.isNotBlank()) item {
                Text(r.desc, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
            item { SectionTitle("对照冰箱") }
            itemsIndexed(r.ingredients) { i, line ->
                GroupItem(i, r.ingredients.size) {
                    Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    IngStatus(line, live)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    if (miss.isEmpty()) Text("主料齐了，可以直接做。", color = cs.primary, style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text("要买：" + miss.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                        val left = miss.filter { vm.inShop(it) == null }
                        FilledTonalButton(onClick = {
                            val n = vm.addToShop(miss, r.name)
                            toast.show(if (n > 0) "已加入清单 $n 样" else "这些已经在清单里了")
                        }, enabled = left.isNotEmpty(), modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (left.isEmpty()) "都已在购物清单里" else "把缺的 ${left.size} 样加入清单")
                        }
                    }
                }
            }
            if (r.amounts.isNotEmpty()) {
                item { SectionTitle("用量") }
                itemsIndexed(r.amounts) { i, line -> GroupItem(i, r.amounts.size) { Text(line, style = MaterialTheme.typography.bodyLarge) } }
            }
            item { SectionTitle("做法") }
            itemsIndexed(r.steps) { i, s ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Surface(shape = CircleShape, color = cs.primaryContainer, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${i + 1}", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (r.tips.isNotEmpty()) {
                item { SectionTitle("小贴士") }
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = cs.secondaryContainer, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.tips.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer) }
                        }
                    }
                }
            }
            item {
                val le = data.eaten.filter { it.n == r.name }.maxOfOrNull { it.t }
                Text(
                    listOfNotNull(
                        le?.let { "上次吃：" + agoText(it) },
                        if (r.protein != null) "蛋白质和热量是每人份估算值（一盘菜按两人分，不含米饭），仅供参考" else null,
                        when (r.src) { "h" -> "菜谱来自开源项目 HowToCook"; "u" -> "自己添加的菜"; else -> "家常做法整理" },
                    ).joinToString("\n"),
                    Modifier.padding(horizontal = 20.dp, vertical = 24.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDel) ConfirmDialog("删除「${r.name}」？", "删除后可以在提示条上撤销。", "删除", onConfirm = {
        nav.back(); vm.deleteRecipe(r.id); toast.show("已删除 ${r.name}") { vm.undoRecipe() }
    }, onDismiss = { confirmDel = false })
}

@Composable
private fun IngStatus(line: String, live: List<InvItem>) {
    val cs = MaterialTheme.colorScheme
    val it = live.firstOrNull { K.matchItem(it.name, line) }
    if (it != null) {
        val ei = K.expInfo(K.daysLeft(it.exp))
        Column(horizontalAlignment = Alignment.End) {
            Text("有 ${K.fmtQ(it.qty)}${it.unit}", color = cs.primary, style = MaterialTheme.typography.labelLarge)
            if (ei != null && ei.second != K.Level.OK) Text(ei.first, color = cs.error, style = MaterialTheme.typography.labelSmall)
        }
        return
    }
    val nm = K.tokens(line).firstOrNull() ?: ""
    when {
        nm.isEmpty() || K.isNonFood(nm) -> Unit
        K.isSeason(nm) -> Text("调料", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        else -> Tag("缺", cs.errorContainer, cs.onErrorContainer)
    }
}

/* ======================= 编辑菜谱 ======================= */

@Composable
fun RecipeEditScreen(vm: AppViewModel, r: Recipe?, snackbar: SnackbarHostState) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    var name by remember { mutableStateOf(r?.name ?: "") }
    var cat by remember { mutableStateOf(r?.cat ?: "荤菜") }
    var diff by remember { mutableStateOf(r?.diff) }
    var minutes by remember { mutableStateOf(r?.minutes?.toString() ?: "") }
    var spicy by remember { mutableStateOf(r?.spicy ?: false) }
    var meat by remember { mutableStateOf(r?.meat ?: false) }
    var desc by remember { mutableStateOf(r?.desc ?: "") }
    var protein by remember { mutableStateOf(r?.protein?.toString() ?: "") }
    var kcal by remember { mutableStateOf(r?.kcal?.toString() ?: "") }
    val ings = remember { (r?.ingredients ?: listOf("")).toMutableStateList() }
    val amts = remember { (r?.amounts ?: emptyList()).toMutableStateList() }
    val steps = remember { (r?.steps ?: listOf("")).toMutableStateList() }
    val tips = remember { (r?.tips ?: emptyList()).toMutableStateList() }
    var error by remember { mutableStateOf<String?>(null) }
    var askLeave by remember { mutableStateOf(false) }

    fun build() = Recipe(
        id = r?.id ?: "", name = name, cat = cat, diff = diff, minutes = minutes.trim().toIntOrNull(),
        spicy = spicy, meat = meat, desc = desc, ingredients = ings.toList(), amounts = amts.toList(),
        steps = steps.toList(), tips = tips.toList(), src = r?.src ?: "u",
        protein = protein.trim().toIntOrNull(), kcal = kcal.trim().toIntOrNull(),
    )
    val original = remember { build() }
    val dirty = build() != original

    BackHandler(enabled = dirty) { askLeave = true }

    fun save() {
        if (minutes.isNotBlank() && minutes.trim().toIntOrNull() == null) { error = "时间请填数字（分钟）"; return }
        val rec = build().let { if (it.id.isEmpty()) it.copy(id = vm.newRecipeId()) else it }
        val err = vm.saveRecipe(rec)
        if (err != null) { error = err; return }
        nav.back()
        toast.show(if (r == null) "已添加 ${rec.name.trim()}" else "已保存")
    }

    SkinScaffold(
        title = if (r == null) "新菜" else "编辑菜谱",
        snackbar = snackbar,
        onBack = { if (dirty) askLeave = true else nav.back() },
        largeTitle = false,
        actions = { Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text("保存") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            OutlinedTextField(name, { name = it; error = null }, label = { Text("菜名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("分类", style = MaterialTheme.typography.labelLarge)
            ChipFlow(K.RCATS, { it == cat }, { cat = it })
            Text("难度", style = MaterialTheme.typography.labelLarge)
            ChipFlow(listOf("不填", "★", "★★", "★★★", "★★★★", "★★★★★"), { (diff ?: 0) == if (it == "不填") 0 else it.length }, {
                diff = if (it == "不填") null else it.length
            })
            OutlinedTextField(
                minutes, { minutes = it.filter { c -> c.isDigit() } }, label = { Text("大约几分钟") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("辣", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Switch(spicy, { spicy = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("含肉（“只要素的”会排除）", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Switch(meat, { meat = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    protein, { protein = it.filter { c -> c.isDigit() } }, label = { Text("蛋白质 克/人份") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("热量 千卡/人份") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(desc, { desc = it }, label = { Text("简介（可不填）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            ListEditor("原料", "一行一样，如：五花肉", ings, numbered = false)
            ListEditor("用量", "如：五花肉 200g（可不填）", amts, numbered = false)
            ListEditor("做法", "这一步做什么", steps, numbered = true)
            ListEditor("小贴士", "可不填", tips, numbered = false)
            Spacer(Modifier.height(48.dp))
        }
    }
    if (askLeave) AlertDialog(
        onDismissRequest = { askLeave = false },
        title = { Text("还没保存") },
        text = { Text("改动要保存吗？") },
        confirmButton = { TextButton(onClick = { askLeave = false; save() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { askLeave = false; nav.back() }) { Text("不保存") } },
    )
}

@Composable
private fun ListEditor(title: String, hint: String, list: SnapshotStateList<String>, numbered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        list.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (numbered) Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                OutlinedTextField(
                    value = v, onValueChange = { list[i] = it },
                    placeholder = { Text(hint) }, modifier = Modifier.weight(1f),
                    singleLine = !numbered, shape = RoundedCornerShape(16.dp),
                )
                IconButton(onClick = { list.removeAt(i) }) { Icon(Icons.Outlined.RemoveCircleOutline, "删掉这一行") }
            }
        }
        TextButton(onClick = { list.add("") }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("加一行$title") }
    }
}
