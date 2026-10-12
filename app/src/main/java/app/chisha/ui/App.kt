@file:OptIn(ExperimentalMaterial3Api::class)


package app.chisha.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chisha.AppViewModel
import app.chisha.InvItem
import app.chisha.K
import app.chisha.Recipe

sealed interface Route {
    data object Settings : Route
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
}

/** 底部弹层：添加/编辑食材、做完扣库存、买到入库 */
sealed interface Sheet {
    data class Item(val item: InvItem?, val presetName: String = "") : Sheet
    data class Deduct(val recipe: Recipe) : Sheet
    data object Stock : Sheet
}

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("冰箱", Icons.Outlined.Kitchen),
    Tab("做什么", Icons.Outlined.RestaurantMenu),
    Tab("清单", Icons.Outlined.ShoppingCart),
    Tab("抽签", Icons.Outlined.Casino),
    Tab("菜谱", Icons.AutoMirrored.Outlined.MenuBook),
)

/** 页面间共享的导航动作 */
class Nav(
    val open: (Route) -> Unit,
    val back: () -> Unit,
    val sheet: (Sheet?) -> Unit,
    val tab: (Int) -> Unit,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }

@Composable
fun ChishaApp(vm: AppViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snackbar, scope) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var cookUrgentOnly by remember { mutableStateOf(false) }

    val nav = remember {
        Nav(
            open = { stack.add(it) },
            back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
            sheet = { sheet = it },
            tab = { tab = it },
        )
    }

    BackHandler(enabled = stack.isNotEmpty()) { nav.back() }

    // 弹层打开时，后面的内容做模糊（安卓 12 及以上生效）
    val blur by animateDpAsState(if (sheet != null) 18.dp else 0.dp, label = "blur")

    CompositionLocalProvider(LocalToaster provides toaster, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().blur(blur)) {
            if (!loaded) {
                Box(Modifier.fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else {
                Home(vm, tab, snackbar, cookUrgentOnly, onCookUrgent = { cookUrgentOnly = it })
                AnimatedContent(
                    targetState = stack.lastOrNull(),
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    },
                    label = "route",
                ) { r ->
                    when (r) {
                        null -> Unit
                        is Route.Detail -> recipes.firstOrNull { it.id == r.id }?.let { RecipeDetailScreen(vm, it, data, snackbar) }
                            ?: LaunchedEffect(r) { nav.back() }
                        is Route.Edit -> RecipeEditScreen(vm, r.id?.let { id -> recipes.firstOrNull { it.id == id } }, snackbar)
                        Route.Settings -> SettingsScreen(vm, data, snackbar)
                    }
                }
            }
        }
        when (val s = sheet) {
            is Sheet.Item -> ItemSheet(vm, data, s.item, s.presetName) { sheet = null }
            is Sheet.Deduct -> DeductSheet(vm, data, s.recipe) { sheet = null }
            Sheet.Stock -> StockSheet(vm, data) { sheet = null }
            null -> Unit
        }
    }
}

/** 列表要留出的上下空白（顶栏、底栏、悬浮按钮） */
val LocalListPad = compositionLocalOf { PaddingValues(bottom = 96.dp) }

@Composable
private fun Home(
    vm: AppViewModel,
    tab: Int,
    snackbar: SnackbarHostState,
    cookUrgentOnly: Boolean,
    onCookUrgent: (Boolean) -> Unit,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val skin = LocalSkin.current
    val urgentCount = data.inv.count { (K.daysLeft(it.exp) ?: 99) <= 3 || (K.supplyDays(it) ?: 99) <= 5 }
    val shopCount = data.shop.count { !it.done }
    val today = remember {
        val d = java.time.LocalDate.now()
        "${d.monthValue}月${d.dayOfMonth}日 周" + "一二三四五六日"[d.dayOfWeek.value - 1]
    }
    val select: (Int) -> Unit = { i -> nav.tab(i); if (i != 1) onCookUrgent(false) }

    SkinScaffold(
        title = if (tab == 0) "今天吃啥" else TABS[tab].title,
        snackbar = snackbar,
        titleExtra = if (tab == 0) ({ Text(today, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        actions = { IconButton(onClick = { nav.open(Route.Settings) }) { Icon(Icons.Outlined.Settings, "设置") } },
        fab = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = { nav.sheet(Sheet.Item(null)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("添加食材") },
                )
                4 -> ExtendedFloatingActionButton(
                    onClick = { nav.open(Route.Edit(null)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("新菜") },
                )
            }
        },
        bottomBar = {
            if (skin == Skin.APPLE) {
                AppleTabBar(
                    TABS.mapIndexed { i, t ->
                        TabSpec(t.title, t.icon, when (i) { 0 -> urgentCount; 2 -> shopCount; else -> 0 },
                            if (i == 2) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                    },
                    tab, select,
                )
            } else NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { select(i) },
                        icon = {
                            val badge = when (i) { 0 -> urgentCount; 2 -> shopCount; else -> 0 }
                            BadgedBox(badge = {
                                if (badge > 0) Badge(containerColor = if (i == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) { Text("$badge") }
                            }) { Icon(t.icon, t.title) }
                        },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        val listPad = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 88.dp)
        CompositionLocalProvider(LocalListPad provides listPad) {
            when (tab) {
                0 -> FridgeTab(vm, data, onGoCookUrgent = { onCookUrgent(true); nav.tab(1) })
                1 -> CookTab(vm, data, cookUrgentOnly, onCookUrgent)
                2 -> ShopTab(vm, data)
                3 -> DrawTab(vm, data)
                4 -> BookTab(vm, data)
            }
        }
    }
}
