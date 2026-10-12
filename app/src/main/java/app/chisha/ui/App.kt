@file:OptIn(ExperimentalMaterial3Api::class)

package app.chisha.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chisha.AppViewModel
import app.chisha.InvItem
import app.chisha.K
import app.chisha.Recipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

/** 页面栈里的一页；appear 从 0 到 1 表示进入动画 */
private class Entry(val route: Route, val id: Long) {
    val appear = Animatable(0f)
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
    val stack = remember { mutableStateListOf<Entry>() }
    var nextId by remember { mutableLongStateOf(1L) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var cookUrgentOnly by remember { mutableStateOf(false) }

    // 预见性返回：手势进度（0~1）和从哪边滑
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var closing by remember { mutableStateOf(false) }

    val nav = remember {
        Nav(
            open = { r ->
                val e = Entry(r, nextId++)
                stack.add(e)
                scope.launch { e.appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) }
            },
            back = {
                val e = stack.lastOrNull()
                if (e != null && !closing) {
                    closing = true
                    scope.launch {
                        e.appear.animateTo(0f, tween(260))
                        stack.remove(e); closing = false
                    }
                }
            },
            sheet = { sheet = it },
            tab = { tab = it },
        )
    }

    PredictiveBackHandler(enabled = stack.isNotEmpty() && !closing) { events ->
        try {
            events.collect { ev ->
                backProgress = ev.progress
                backEdge = ev.swipeEdge
            }
            // 松手确认返回：从当前进度接着滑完
            val e = stack.lastOrNull()
            closing = true
            scope.launch {
                animate(backProgress, 1f, animationSpec = tween(180)) { v, _ -> backProgress = v }
                if (e != null) { e.appear.snapTo(0f); stack.remove(e) }
                backProgress = 0f; closing = false
            }
        } catch (c: CancellationException) {
            // 取消返回：弹回原位
            scope.launch { animate(backProgress, 0f, animationSpec = spring()) { v, _ -> backProgress = v } }
            throw c
        }
    }

    // 弹层打开时，后面的内容做模糊（安卓 12 及以上生效）
    val blur by animateDpAsState(if (sheet != null) 18.dp else 0.dp, label = "blur")

    CompositionLocalProvider(LocalToaster provides toaster, LocalNav provides nav) {
        BoxWithConstraints(Modifier.fillMaxSize().blur(blur)) {
            val w = constraints.maxWidth.toFloat()
            if (!loaded) {
                Box(Modifier.fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else {
                val top = stack.lastOrNull()
                val tAppear = top?.appear?.value ?: 0f
                // 下面那一层（首页或上一页）：苹果外观有视差，安卓外观轻微缩放 + 变暗
                fun Modifier.under(): Modifier = this.graphicsLayer {
                    if (top == null) return@graphicsLayer
                    val cover = tAppear * (1f - backProgress)
                    if (skin == Skin.APPLE) translationX = -0.3f * w * cover
                    else { val s = 1f - 0.04f * cover; scaleX = s; scaleY = s }
                }
                Box(Modifier.fillMaxSize().under()) {
                    Home(vm, tab, snackbar, cookUrgentOnly, onCookUrgent = { cookUrgentOnly = it })
                }
                if (top != null) {
                    // 变暗遮罩
                    val dim = (if (skin == Skin.APPLE) 0.12f else 0.32f) * tAppear * (1f - backProgress)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                }
                stack.forEachIndexed { i, e ->
                    if (i < stack.size - 2) return@forEachIndexed
                    val isTop = i == stack.lastIndex
                    key(e.id) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val t = e.appear.value
                                if (isTop) {
                                    val p = backProgress
                                    if (skin == Skin.APPLE) {
                                        // iOS：整页从右滑入，返回时跟着手指往右滑
                                        translationX = (1f - t) * w + p * w
                                        shadowElevation = 24f
                                    } else {
                                        // 安卓 17：进入是淡入 + 小位移；返回手势时缩小、圆角、贴向一侧
                                        alpha = t
                                        val sign = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                                        translationX = (1f - t) * 0.12f * w + sign * p * (w * 0.06f)
                                        val s = 1f - 0.1f * p
                                        scaleX = s; scaleY = s
                                        shape = RoundedCornerShape((32f * p).dp)
                                        clip = p > 0f
                                    }
                                } else if (skin == Skin.APPLE) {
                                    val tt = top?.appear?.value ?: 0f
                                    translationX = -0.3f * w * tt * (1f - backProgress)
                                }
                            },
                        ) {
                            when (val r = e.route) {
                                is Route.Detail -> recipes.firstOrNull { it.id == r.id }?.let { RecipeDetailScreen(vm, it, data, snackbar) }
                                    ?: LaunchedEffect(r) { stack.remove(e) }
                                is Route.Edit -> RecipeEditScreen(vm, r.id?.let { id -> recipes.firstOrNull { it.id == id } }, snackbar)
                                Route.Settings -> SettingsScreen(vm, data, snackbar)
                            }
                        }
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
