@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.chisha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chisha.AppViewModel
import app.chisha.RecipeText

@Composable
fun ImportScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val clip = LocalClipboardManager.current
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    val chosen = drafts.count { it.selected && it.errors.isEmpty() && !(it.conflict == AppViewModel.Conflict.SKIP && vm.draftNameExists(it.recipe.name)) }

    fun recognize(t: String) {
        val n = vm.parseText(t)
        toast.show(if (n == 0) "没认出菜谱。请确认文字里有【菜名】【原料】【做法】" else "认出 $n 道菜，看一眼再导入")
    }

    AppScaffold(
        title = "导入菜谱", snackbar = snackbar, onBack = { vm.clearDrafts(); nav.back() },
        bottomBar = {
            if (drafts.isNotEmpty()) BottomPanel {
                PrimaryButton(
                    if (chosen > 0) "导入选中的 $chosen 道" else "没有选中的菜",
                    {
                        val n = vm.importDrafts()
                        nav.back()
                        toast.show("已导入 $n 道菜") { vm.undoRecipe() }
                    },
                    Modifier.fillMaxWidth(), enabled = chosen > 0,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "1. 点“复制给 GPT 的提示词”，在 GPT 里粘贴提示词，再发视频或字幕给它。\n2. 复制 GPT 的回答，回到这里点“粘贴剪贴板”。\n3. 看一眼识别结果，没问题就导入。",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val t = clip.getText()?.text.orEmpty()
                    if (t.isBlank()) toast.show("剪贴板是空的") else { text = t; recognize(t) }
                }, Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("粘贴剪贴板")
                }
                OutlinedButton(onClick = {
                    clip.setText(AnnotatedString(RecipeText.PROMPT)); toast.show("提示词已复制，去 GPT 里粘贴")
                }, Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("复制 GPT 提示词")
                }
            }
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("也可以直接把菜谱文字粘在这里") }, minLines = 4, maxLines = 10,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (text.isNotBlank()) TextButton(onClick = { recognize(text) }, Modifier.padding(horizontal = 8.dp)) { Text("识别这段文字") }

            if (drafts.isNotEmpty()) SectionTitle("识别结果", drafts.size)
            drafts.forEachIndexed { i, d ->
                val r = d.recipe
                val exists = vm.draftNameExists(r.name)
                Surface(
                    shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(d.selected, { vm.toggleDraft(i) }, enabled = d.errors.isEmpty())
                            Column(Modifier.weight(1f)) {
                                Text(r.name.ifBlank { "（没有菜名）" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        "原料 ${r.ingredients.size} 样", "做法 ${r.steps.size} 步",
                                        r.minutes?.let { "约${it}分钟" }, r.protein?.let { "蛋白 ${it}g" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                )
                            }
                            Tag(r.cat)
                        }
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            d.errors.forEach { Text("✕ $it，改一改才能导入", color = cs.error, style = MaterialTheme.typography.bodySmall) }
                            d.warnings.forEach { Text("· $it", color = cs.tertiary, style = MaterialTheme.typography.bodySmall) }
                            if (exists) {
                                Text("已经有一道「${r.name}」：", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                val cs3 = AppViewModel.Conflict.entries
                                ChoiceGroup(cs3.map { it.label }, cs3.indexOf(d.conflict), { vm.setConflict(i, cs3[it]) }, Modifier.fillMaxWidth())
                            }
                            TextButton(onClick = { nav.open(EditKey("draft:$i")) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (d.errors.isEmpty()) "看一看、改一改" else "去改")
                            }
                        }
                    }
                }
            }
        }
    }
}
