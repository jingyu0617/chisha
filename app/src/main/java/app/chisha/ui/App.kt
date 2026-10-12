@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.chisha.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.chisha.AppViewModel
import app.chisha.InvItem
import app.chisha.K
import app.chisha.Recipe
import kotlinx.serialization.Serializable

/* 页面（官方 Navigation 3 的导航键，可序列化：旋转屏幕、App 被回收后都能恢复） */
@Serializable data object HomeKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class DetailKey(val id: String) : NavKey
@Serializable data class EditKey(val id: String?) : NavKey
@Serializable data object ImportKey : NavKey

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
    val open: (NavKey) -> Unit,
    val back: () -> Unit,
    val sheet: (Sheet?) -> Unit,
    val tab: (Int) -> Unit,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }

/** 共享元素动画用：列表卡片 → 详情页 */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * 预见性返回时，被拖走的页面四角逐渐变圆（安卓 17 规范的效果）。
 * 进度来自 Navigation 3 的转场，手势拖多少，圆角就变多少。
 */
@Composable
private fun Modifier.predictiveCorners(): Modifier {
    if (LocalSkin.current == Skin.APPLE) return this
    val scope = LocalNavAnimatedContentScope.current
    val r by scope.transition.animateDp(label = "corner") { if (it == EnterExitState.PostExit) 32.dp else 0.dp }
    return if (r > 0.dp) this.clip(RoundedCornerShape(r)) else this
}

@Composable
fun ChishaApp(vm: AppViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val skin = LocalSkin.current

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snackbar, scope) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val backStack = rememberNavBackStack(HomeKey)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var cookUrgentOnly by rememberSaveable { mutableStateOf(false) }

    val nav = remember(backStack) {
        Nav(
            open = { backStack.add(it) },
            back = { if (backStack.size > 1) backStack.removeLastOrNull() },
            sheet = { sheet = it },
            tab = { tab = it },
        )
    }

    // 弹层打开时，后面的内容做模糊（安卓 12 及以上生效）
    val blur by animateDpAsState(if (sheet != null) 18.dp else 0.dp, label = "blur")

    CompositionLocalProvider(LocalToaster provides toaster, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().blur(blur)) {
            if (!loaded) {
                Box(Modifier.fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        sharedTransitionScope = this,
                        transitionSpec = {
                            if (skin == Skin.APPLE) {
                                // iOS：新页从右边推入，旧页向左视差
                                slideInHorizontally(tween(380)) { it } togetherWith slideOutHorizontally(tween(380)) { -it / 3 }
                            } else {
                                // 安卓：共享 X 轴（小位移 + 淡入淡出）
                                (slideInHorizontally(tween(300)) { it / 8 } + fadeIn(tween(240, delayMillis = 60))) togetherWith
                                    (slideOutHorizontally(tween(300)) { -it / 8 } + fadeOut(tween(90)))
                            }
                        },
                        popTransitionSpec = {
                            if (skin == Skin.APPLE) {
                                ContentTransform(
                                    slideInHorizontally(tween(340)) { -it / 3 }, slideOutHorizontally(tween(340)) { it }, targetContentZIndex = -1f,
                                )
                            } else {
                                (slideInHorizontally(tween(300)) { -it / 8 } + fadeIn(tween(240, delayMillis = 60))) togetherWith
                                    (slideOutHorizontally(tween(300)) { it / 8 } + fadeOut(tween(90)))
                            }
                        },
                        predictivePopTransitionSpec = {
                            if (skin == Skin.APPLE) {
                                // iOS：跟着手指整页右滑，下面一页从左边视差滑入
                                ContentTransform(
                                    slideInHorizontally { -it / 3 }, slideOutHorizontally { it }, targetContentZIndex = -1f,
                                )
                            } else {
                                // 安卓 17：被拖走的页面缩小、四角变圆（见 predictiveCorners），下面一页淡入
                                ContentTransform(
                                    fadeIn(initialAlpha = 0.6f) + scaleIn(initialScale = 0.96f),
                                    scaleOut(targetScale = 0.9f) + fadeOut(targetAlpha = 0.4f),
                                    targetContentZIndex = -1f,
                                )
                            }
                        },
                        entryProvider = entryProvider {
                            entry<HomeKey> {
                                Box(Modifier.predictiveCorners()) {
                                    Home(vm, tab, snackbar, cookUrgentOnly, onCookUrgent = { cookUrgentOnly = it })
                                }
                            }
                            entry<DetailKey> { k ->
                                Box(Modifier.predictiveCorners()) {
                                    val r = recipes.firstOrNull { it.id == k.id }
                                    if (r != null) RecipeDetailScreen(vm, r, data, snackbar)
                                    else LaunchedEffect(k) { nav.back() }
                                }
                            }
                            entry<EditKey> { k ->
                                Box(Modifier.predictiveCorners()) {
                                    val id = k.id
                                    if (id != null && id.startsWith("draft:")) {
                                        // 导入预览里的草稿：改完回到预览，不直接存进菜谱
                                        val i = id.removePrefix("draft:").toIntOrNull() ?: -1
                                        val drafts by vm.drafts.collectAsStateWithLifecycle()
                                        val d = drafts.getOrNull(i)
                                        if (d != null) RecipeEditScreen(vm, d.recipe, snackbar, title = "检查这道菜", onSave = { vm.updateDraft(i, it) })
                                        else LaunchedEffect(k) { nav.back() }
                                    } else {
                                        RecipeEditScreen(vm, id?.let { x -> recipes.firstOrNull { it.id == x } }, snackbar)
                                    }
                                }
                            }
                            entry<ImportKey> {
                                Box(Modifier.predictiveCorners()) { ImportScreen(vm, snackbar) }
                            }
                            entry<SettingsKey> {
                                Box(Modifier.predictiveCorners()) { SettingsScreen(vm, data, snackbar) }
                            }
                        },
                    )
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
        actions = { IconButton(onClick = { nav.open(SettingsKey) }) { Icon(Icons.Outlined.Settings, "设置") } },
        fab = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = { nav.sheet(Sheet.Item(null)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("添加食材") },
                )
                4 -> ExtendedFloatingActionButton(
                    onClick = { nav.open(EditKey(null)) },
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
