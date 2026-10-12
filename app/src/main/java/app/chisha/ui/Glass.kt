@file:OptIn(ExperimentalMaterial3Api::class)

package app.chisha.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.blur.*

/** 苹果外观下，玻璃条要模糊的背景来源 */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** 液态玻璃材质：强模糊 + 半透明白（深色模式半透明黑）+ 一点颗粒 */
@Composable
fun glassStyle(): HazeBlurStyle {
    val dark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    return HazeBlurStyle {
        blurEnabled(true)
        blurRadius(28.dp)
        noiseFactor(0.08f)
        backgroundColor(bg)
        colorEffects(listOf(HazeColorEffect.tint(if (dark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f))))
        fallbackColorEffect(HazeColorEffect.tint(if (dark) Color(0xEE1C1C1E) else Color(0xEEF9F9F9)))
    }
}

@Composable
fun Modifier.glass(): Modifier {
    val state = LocalHaze.current ?: return this.background(MaterialTheme.colorScheme.surfaceContainer)
    return this.hazeBlur(input = HazeInput.Sources(state), style = glassStyle())
}

/**
 * 按外观切换的页面骨架。content 收到的 padding 要当作列表的 contentPadding 用，
 * 这样苹果外观下内容能滑到玻璃条下面、被模糊。
 */
@Composable
fun SkinScaffold(
    title: String,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = onBack == null,
    titleExtra: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalSkin.current == Skin.APPLE) {
        val haze = rememberHazeState()
        CompositionLocalProvider(LocalHaze provides haze) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { AppleTopBar(title, onBack, largeTitle, titleExtra, actions) },
                bottomBar = bottomBar,
                floatingActionButton = fab,
                snackbarHost = { SnackbarHost(snackbar) },
            ) { pad ->
                Box(Modifier.fillMaxSize().hazeSource(haze)) { content(pad) }
            }
        }
    } else {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = { titleExtra?.invoke(); actions() },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = fab,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad -> Box(Modifier.fillMaxSize()) { content(pad) } }
    }
}

/** iOS 导航栏：主页是左对齐大标题，子页面是“‹ 返回 + 居中标题” */
@Composable
private fun AppleTopBar(
    title: String,
    onBack: (() -> Unit)?,
    large: Boolean,
    extra: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().glass().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
            if (onBack != null) {
                Row(
                    Modifier.align(Alignment.CenterStart).clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "返回", tint = cs.primary, modifier = Modifier.size(20.dp))
                    Text("返回", color = cs.primary, fontSize = 17.sp)
                }
            }
            if (!large) Text(
                title, Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (large) extra?.invoke()
                CompositionLocalProvider(LocalContentColor provides cs.primary) { actions() }
            }
        }
        if (large) Text(
            title, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.6f))
    }
}

data class TabSpec(val title: String, val icon: ImageVector, val badge: Int = 0, val badgeColor: Color = Color.Unspecified)

/** iOS 26 风格的悬浮玻璃标签栏 */
@Composable
fun AppleTabBar(tabs: List<TabSpec>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(CircleShape)
                .glass()
                .border(0.5.dp, (if (dark) Color.White else Color.White).copy(alpha = if (dark) 0.18f else 0.7f), CircleShape)
                .padding(4.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                val pill by animateColorAsState(if (sel) cs.onSurface.copy(alpha = if (dark) 0.14f else 0.07f) else Color.Transparent, label = "pill")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(pill)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Icon(t.icon, t.title, tint = if (sel) cs.primary else cs.onSurface, modifier = Modifier.size(24.dp))
                        if (t.badge > 0) Text(
                            "${t.badge}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)
                                .clip(CircleShape).background(if (t.badgeColor == Color.Unspecified) cs.error else t.badgeColor)
                                .padding(horizontal = 5.dp),
                        )
                    }
                    Text(t.title, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) cs.primary else cs.onSurface)
                }
            }
        }
    }
}

/** 底部操作条：安卓是实色面板，苹果是玻璃 */
@Composable
fun SkinBottomPanel(content: @Composable RowScope.() -> Unit) {
    if (LocalSkin.current == Skin.APPLE) {
        Row(Modifier.fillMaxWidth().glass().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), content = content)
        }
    }
}
