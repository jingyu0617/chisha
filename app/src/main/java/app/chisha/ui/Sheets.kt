@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.chisha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.chisha.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 带收起动画的关闭 */
@Composable
private fun rememberCloser(state: SheetState, onDismiss: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember<() -> Unit>(state) {
        {
            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
            Unit
        }
    }
}

/* ======================= 添加 / 编辑食材 ======================= */

@Composable
fun ItemSheet(vm: AppViewModel, data: AppData, item: InvItem?, presetName: String, onDismiss: () -> Unit) {
    val toast = LocalToaster.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberCloser(state, onDismiss)

    var name by remember { mutableStateOf(item?.name ?: presetName) }
    var cat by remember { mutableStateOf(item?.cat ?: K.guessCat(presetName)) }
    var loc by remember { mutableStateOf(item?.loc ?: K.guessLoc(presetName, K.guessCat(presetName))) }
    // 单位方式：录入这一样时自己选（按个/份 或 按克/毫升）
    var umode by remember { mutableStateOf(item?.let { K.unitMode(it.unit) } ?: "rough") }
    var unit by remember { mutableStateOf(item?.unit ?: "个") }
    var qty by remember { mutableStateOf(item?.let { K.fmtQ(it.qty) } ?: "1") }
    var qtyTouched by remember { mutableStateOf(item != null) }
    var exp by remember { mutableStateOf(item?.exp) }
    var catTouched by remember { mutableStateOf(item != null) }
    var locTouched by remember { mutableStateOf(item != null) }
    var expTouched by remember { mutableStateOf(item != null) }
    // 消耗品
    var consumable by remember { mutableStateOf(item?.dose != null) }
    var dose by remember { mutableStateOf(item?.dose?.let { K.fmtQ(it) } ?: "") }
    var perDay by remember { mutableStateOf(item?.perDay?.let { K.fmtQ(it) } ?: "1") }
    var roast by remember { mutableStateOf(item?.roast) }
    var dateTarget by remember { mutableStateOf<String?>(null) } // "exp" 或 "roast"
    var confirmDel by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun applyMode(m: String) {
        umode = m
        unit = K.defUnit(name.trim(), cat, m)
        if (!qtyTouched) qty = K.fmtQ(K.defQty(unit, name.trim()))
    }

    fun autofill(nameChanged: Boolean) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (!catTouched) cat = K.guessCat(n)
        if (!locTouched) loc = K.guessLoc(n, cat)
        if (item == null && nameChanged) {
            val cd = K.consumableDefaults(n)
            if (cd != null) {
                consumable = true; dose = K.fmtQ(cd.first); perDay = K.fmtQ(cd.second)
                if (K.isCoffee(n) && roast == null) roast = K.ymd(0)
                applyMode("exact")
            } else {
                consumable = false
                applyMode(if (cat == "补剂冲饮") "exact" else umode)
            }
        }
        if (!expTouched && item == null) exp = K.ymd(K.guessDays(n, cat, loc))
    }
    LaunchedEffect(Unit) { if (presetName.isNotBlank()) autofill(true) }

    fun save(keepOpen: Boolean) {
        val n = name.trim()
        val q = qty.toDoubleOrNull()
        if (n.isEmpty()) { err = "先填食材名称"; return }
        if (q == null || q < 0 || (q == 0.0 && !consumable)) { err = "数量要大于 0"; return }
        val d = if (consumable) dose.toDoubleOrNull() else null
        val p = if (consumable) perDay.toDoubleOrNull() else null
        if (consumable && (d == null || d <= 0)) { err = "填一下每次用多少"; return }
        if (consumable && (p == null || p <= 0)) { err = "填一下每天用几次"; return }
        val rst = if (consumable && K.isCoffee(n)) roast else null
        if (item != null) {
            vm.updateInv(item.copy(name = n, cat = cat, qty = q, unit = unit, loc = loc, exp = exp, dose = d, perDay = p, roast = rst))
            toast.show("已保存 $n"); close(); return
        }
        val merged = vm.addInv(n, cat, q, unit, loc, exp, d, p, rst)
        toast.show(if (merged != null) "$n 已合并，共 ${K.fmtQ(merged.qty)} ${merged.unit}" else "已添加 $n")
        if (keepOpen) {
            name = ""; qty = "1"; qtyTouched = false; exp = null; err = null; consumable = false; dose = ""; perDay = "1"; roast = null
            catTouched = false; locTouched = false; expTouched = false
        } else close()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (item == null) "添加食材" else "编辑食材", style = MaterialTheme.typography.headlineSmall)
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                name, { name = it; err = null; autofill(true) }, label = { Text("名称") },
                placeholder = { Text("如 西红柿、蛋白粉、咖啡豆") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (item == null) {
                val have = data.inv.map { it.name }.toSet()
                val sugg = (K.COMMON + listOf("蛋白粉", "咖啡豆")).filter { (name.isBlank() || it.contains(name.trim())) && it != name.trim() && it !in have }.take(10)
                if (sugg.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sugg.forEach { s -> SuggestionChip(onClick = { name = s; catTouched = false; locTouched = false; autofill(true) }, label = { Text(s) }) }
                }
            }
            Text("分类", style = MaterialTheme.typography.labelLarge)
            ChipFlow(K.ICATS, { it == cat }, { cat = it; catTouched = true; autofill(false) })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数量", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                val modes = listOf("rough", "exact")
                ChoiceGroup(listOf("按个/份", "按克/毫升"), modes.indexOf(umode).coerceAtLeast(0), { applyMode(modes[it]) }, Modifier.width(220.dp))
            }
            OutlinedTextField(
                qty, { qty = it.filter { c -> c.isDigit() || c == '.' }; qtyTouched = true }, singleLine = true,
                suffix = { Text(unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val units = (if (umode == "exact") K.UNITS_EXACT else K.UNITS_ROUGH).let { if (unit in it) it else listOf(unit) + it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u -> FilterChip(selected = u == unit, onClick = { unit = u }, label = { Text(u) }) }
            }

            // 消耗品：官方开关行
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchItem(0, 1, consumable, {
                        consumable = it
                        if (it) {
                            val cd = K.consumableDefaults(name.trim())
                            if (dose.isBlank()) dose = K.fmtQ(cd?.first ?: K.stepOf(unit))
                            if (cd != null) perDay = K.fmtQ(cd.second)
                            if (K.isCoffee(name.trim()) && roast == null) roast = K.ymd(0)
                        }
                    }, "每天固定用", "蛋白粉、咖啡豆这类：按天自动扣，快用完自动进清单", inset = false)
                    if (consumable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                dose, { dose = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("每次用") }, singleLine = true,
                                suffix = { Text(unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                perDay, { perDay = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("每天几次") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                            )
                        }
                        val dd = dose.toDoubleOrNull(); val pp = perDay.toDoubleOrNull(); val qq = qty.toDoubleOrNull()
                        if (dd != null && pp != null && qq != null && dd > 0 && pp > 0) {
                            Text("按这个用量，还能用约 ${(qq / (dd * pp)).toInt()} 天；剩 5 天时自动加进购物清单。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (K.isCoffee(name.trim())) {
                            OutlinedButton(onClick = { dateTarget = "roast" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                                Text("烘焙日期：" + (roast?.let { r -> r + (K.coffeeFresh(r)?.let { "（${it.first}）" } ?: "") } ?: "不记"))
                            }
                        }
                    }
            }

            Text("放在哪", style = MaterialTheme.typography.labelLarge)
            ChoiceGroup(K.LOCS, K.LOCS.indexOf(loc).coerceAtLeast(0), { loc = K.LOCS[it]; locTouched = true; autofill(false) }, Modifier.fillMaxWidth())
            Text("保质期到", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { dateTarget = "exp" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                Text(exp?.let { e -> "$e（" + (K.expInfo(K.daysLeft(e))?.first ?: "") + "）" } ?: "不记")
            }
            val quick = listOf(1 to "明天", 3 to "3天", 7 to "1周", 30 to "1个月", 90 to "3个月")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { (d, l) -> FilterChip(selected = exp == K.ymd(d), onClick = { exp = K.ymd(d); expTouched = true }, label = { Text(l) }) }
                FilterChip(selected = exp == null, onClick = { exp = null; expTouched = true }, label = { Text("不记") })
            }
            if (name.isNotBlank()) {
                val days = K.guessDays(name.trim(), cat, loc)
                Text("${loc}的${name.trim()}一般放 ${K.daysText(days)}左右，仅供参考。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            if (item == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { save(true) }, Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("保存，继续添加") }
                    Button(onClick = { save(false) }, Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("保存") }
                }
            } else {
                Button(onClick = { save(false) }, Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("保存") }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        val n = vm.addToShop(listOf(item.name), null)
                        toast.show(if (n > 0) "已把 ${item.name} 加入购物清单" else "${item.name} 已经在清单里了")
                    }, Modifier.weight(1f)) { Text("加入购物清单") }
                    OutlinedButton(
                        onClick = { confirmDel = true }, Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("删除") }
                }
            }
        }
    }

    dateTarget?.let { target ->
        val cur = if (target == "roast") roast else exp
        val init = cur?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
        val ds = rememberDatePickerState(initialSelectedDateMillis = init ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let {
                        val v = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (target == "roast") roast = v else { exp = v; expTouched = true }
                    }
                    dateTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text("取消") } },
        ) { DatePicker(state = ds) }
    }
    if (confirmDel && item != null) ConfirmDialog("删除 ${item.name}？", "从冰箱里移除这样食材。", "删除", onConfirm = {
        vm.deleteInv(item.id); toast.show("已删除 ${item.name}") { vm.undo() }; close()
    }, onDismiss = { confirmDel = false })
}

/* ======================= 做完扣库存 ======================= */

@Composable
fun DeductSheet(vm: AppViewModel, data: AppData, r: Recipe, onDismiss: () -> Unit) {
    val toast = LocalToaster.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberCloser(state, onDismiss)
    val rows = remember { data.inv.filter { it.qty > 0 && r.ingredients.any { line -> K.matchItem(it.name, line) } }.map { it to K.suggestUse(it, r) } }
    var servings by remember { mutableIntStateOf(1) }
    val values = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(servings) {
        rows.forEach { (it, base) -> values[it.id] = K.fmtQ(minOf(it.qty, base * servings)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("用了多少", style = MaterialTheme.typography.headlineSmall)
            Text(r.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column {
                GroupItem(0, 1, inset = false, trailing = {
                    QtyControl("$servings 份", { if (servings > 1) servings-- }, { if (servings < 10) servings++ }, "份数")
                }) { Text("做了几份") }
            }
            if (rows.isEmpty()) {
                Text("这道菜没用到冰箱里记着的食材，点“只记录”就好。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("按菜谱估了用量，改成实际用的就行。填 0 表示没用这样，用完的会移出冰箱。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 每样一个官方输入框：标签是名称，下方写现有多少
                rows.forEach { (it, _) ->
                    OutlinedTextField(
                        value = values[it.id] ?: "", onValueChange = { v -> values[it.id] = v.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("${it.name} 用了") }, supportingText = { Text("现有 ${K.fmtQ(it.qty)} ${it.unit}") },
                        singleLine = true, suffix = { Text(it.unit) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { vm.markEaten(r.name); toast.show("记下了：今天吃${r.name}"); close() }, Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("只记录，不扣") }
                Button(onClick = {
                    val uses = rows.associate { (it, _) -> it.id to (values[it.id]?.toDoubleOrNull() ?: 0.0) }
                    val gone = vm.deduct(uses, r.name)
                    toast.show(if (gone.isNotEmpty()) "已扣库存，${gone.joinToString("、")} 用完了" else "已扣库存") { vm.undo() }
                    close()
                }, Modifier.weight(1.3f).heightIn(min = ButtonDefaults.MediumContainerHeight), enabled = rows.isNotEmpty()) { Text("确认扣减") }
            }
        }
    }
}

/* ======================= 买到的放进冰箱 ======================= */

@Composable
fun StockSheet(vm: AppViewModel, data: AppData, onDismiss: () -> Unit) {
    val toast = LocalToaster.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberCloser(state, onDismiss)
    val mode = "rough"
    val rows = remember {
        data.shop.filter { it.done }.map {
            val u = K.defUnit(it.name, it.cat, mode)
            AppViewModel.StockRow(it, K.defQty(u, it.name), u, true)
        }.toMutableStateList()
    }
    val texts = remember { mutableStateMapOf<String, String>().apply { rows.forEach { put(it.item.id, K.fmtQ(it.qty)) } } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("放进冰箱", style = MaterialTheme.typography.headlineSmall)
            Text("填一下买了多少。分类、放哪、保质期会自动估一个，之后在冰箱里能改。不想记的点 ×。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEachIndexed { i, r ->
                val loc = K.guessLoc(r.item.name, r.item.cat)
                var menu by remember(r.item.id) { mutableStateOf(false) }
                GroupItem(i, rows.size, inset = false,
                    supporting = { Text(if (r.on) "${r.item.cat} · $loc · 约${K.daysText(K.guessDays(r.item.name, r.item.cat, loc))}" else "不放进冰箱") },
                    trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.on) {
                        OutlinedTextField(
                            value = texts[r.item.id] ?: "", onValueChange = { v -> texts[r.item.id] = v.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true, modifier = Modifier.width(84.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Box {
                            TextButton(onClick = { menu = true }) { Text(r.unit) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                (K.UNITS_ROUGH + K.UNITS_EXACT).forEach { u ->
                                    DropdownMenuItem(text = { Text(u) }, onClick = { rows[i] = r.copy(unit = u); menu = false })
                                }
                            }
                        }
                    }
                    IconButton(onClick = { rows[i] = r.copy(on = !r.on) }) {
                        Icon(if (r.on) Icons.Outlined.Close else Icons.Outlined.Undo, if (r.on) "不放进冰箱" else "放进冰箱")
                    }
                } },
                ) {
                    Text(r.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (r.on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = close, Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("取消") }
                Button(onClick = {
                    val final = rows.map { it.copy(qty = texts[it.item.id]?.toDoubleOrNull() ?: 0.0) }
                    val n = vm.stockIn(final)
                    toast.show(if (n > 0) "$n 样已放进冰箱" else "已清掉已买") { vm.undo() }
                    close()
                }, Modifier.weight(1.4f).heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("放进冰箱") }
            }
        }
    }
}
