@file:OptIn(ExperimentalMaterial3Api::class)

package app.chisha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.chisha.AppData
import app.chisha.AppViewModel
import app.chisha.K

@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData, snackbar: SnackbarHostState) {
    val nav = LocalNav.current
    val toast = LocalToaster.current
    val recipes by vm.recipes.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var preview by remember { mutableStateOf<AppViewModel.ImportPreview?>(null) }
    var importErr by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var webText by remember { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportRecipes(uri).fold(
            onSuccess = { toast.show("已导出 $it 道菜") },
            onFailure = { toast.show("导出失败：${it.message ?: "写不进这个位置"}") },
        )
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readRecipeFile(uri).fold(
            onSuccess = { preview = it },
            onFailure = { importErr = it.message ?: "导入失败" },
        )
    }

    SkinScaffold(title = "设置", snackbar = snackbar, onBack = nav.back) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle("外观")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("android" to "安卓 17", "apple" to "苹果 Liquid Glass").forEachIndexed { i, (k, l) ->
                    SegmentedButton(selected = data.settings.skin == k, onClick = { vm.setSkin(k) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            Hint("安卓 17：配色跟随壁纸，Material 3 Expressive。苹果：iOS 系统色，导航栏和底栏是磨砂玻璃（安卓 12 及以上才有模糊效果）。")

            SectionTitle("健身")
            GroupItem(0, 1, onClick = { vm.setMuscle(!data.settings.muscle) }) {
                Column(Modifier.weight(1f)) {
                    Text("增肌：优先推荐高蛋白", style = MaterialTheme.typography.bodyLarge)
                    Text("“做什么”和抽签会把蛋白质多的菜排前面。每人份 ${K.HIGH_PROTEIN} 克以上算高蛋白（估算值）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(data.settings.muscle, { vm.setMuscle(it) })
            }

            SectionTitle("调料")
            GroupItem(0, 1, onClick = { vm.setSeasonHave(!data.settings.seasonHave) }) {
                Column(Modifier.weight(1f)) {
                    Text("调料默认家里有", style = MaterialTheme.typography.bodyLarge)
                    Text("盐、生抽、葱姜蒜、花椒这类不算“缺”，也不进购物清单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(data.settings.seasonHave, { vm.setSeasonHave(it) })
            }

            SectionTitle("菜谱文件", recipes.size)
            Row3(Icons.Outlined.FileDownload, "导出菜谱文件", "存成 .json 文件，可以发到电脑或微信里改", 0, 3) {
                exporter.launch("今天吃啥菜谱.json")
            }
            Row3(Icons.Outlined.FileUpload, "导入菜谱文件", "格式不对会整份拒绝，并告诉你哪道菜哪里有问题", 1, 3) {
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
            Row3(Icons.Outlined.RestartAlt, "恢复默认菜谱", "丢掉所有改动，回到自带的 198 道菜", 2, 3) { confirmReset = true }
            Hint("文件里只能改每道菜的内容：菜名、分类、难度、分钟、辣、含肉、简介、原料、用量、做法、小贴士、蛋白质克、热量千卡，也可以增删菜。字段名和结构不能改。分类只能是荤菜、素菜、水产、汤羹、主食、早餐。")

            SectionTitle("从网页版搬过来")
            Hint("在网页版的“设置与备份”里点“生成备份文本”并复制，粘贴到下面。会替换这里冰箱和清单的内容。")
            OutlinedTextField(
                webText, { webText = it }, placeholder = { Text("粘贴备份文本") }, minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            FilledTonalButton(onClick = {
                vm.importWebBackup(webText).fold(
                    onSuccess = { webText = ""; toast.show("已搬过来 $it 样食材") { vm.undo() } },
                    onFailure = { toast.show("这段文本不是有效的网页版备份") },
                )
            }, enabled = webText.isNotBlank(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text("导入") }

            SectionTitle("冰箱")
            Row3(Icons.Outlined.DeleteSweep, "清空冰箱", "删掉所有食材记录", 0, 1) { confirmClear = true }

            Hint("数据只存在这台手机上。卸载 App 会丢失，换机前先导出菜谱文件。\n部分菜谱来自开源项目 HowToCook。")
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("导入 ${p.entries.size} 道菜") },
            text = { Text("检查通过。和现在的菜谱比：新增 ${p.added} 道，同名的 ${p.updated} 道会被文件里的内容覆盖。\n\n“合并”保留文件里没有的菜；“全部替换”只留文件里的菜。") },
            confirmButton = {
                TextButton(onClick = { vm.applyImport(p, false); preview = null; toast.show("已合并导入") { vm.undoRecipe() } }) { Text("合并") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { preview = null }) { Text("取消") }
                    TextButton(onClick = { vm.applyImport(p, true); preview = null; toast.show("已全部替换") { vm.undoRecipe() } }) { Text("全部替换") }
                }
            },
        )
    }
    importErr?.let { e ->
        AlertDialog(
            onDismissRequest = { importErr = null },
            title = { Text("导入失败") },
            text = { Text(e + "\n\n现在的菜谱没有任何改动。") },
            confirmButton = { TextButton(onClick = { importErr = null }) { Text("知道了") } },
        )
    }
    if (confirmReset) ConfirmDialog("恢复默认菜谱？", "你新增、修改的菜都会被替换成自带的版本。", "恢复", onConfirm = {
        vm.resetRecipes(); toast.show("已恢复默认菜谱") { vm.undoRecipe() }
    }, onDismiss = { confirmReset = false })
    if (confirmClear) ConfirmDialog("清空冰箱？", "所有食材记录都会删掉。", "清空", onConfirm = {
        vm.clearFridge(); toast.show("冰箱已清空") { vm.undo() }
    }, onDismiss = { confirmClear = false })
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Row3(icon: ImageVector, title: String, sub: String, i: Int, n: Int, onClick: () -> Unit) {
    GroupItem(i, n, onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
