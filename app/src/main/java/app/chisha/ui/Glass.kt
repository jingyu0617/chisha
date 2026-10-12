@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.chisha.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.blur.*

/** 苹果外观：液态玻璃要折射的背景（Kyant Backdrop） */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 苹果外观：顶部渐变模糊的来源（Haze） */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/* ======================= 液态玻璃基础 ======================= */

/**
 * 液态玻璃：饱和度增强 + 模糊 + 边缘折射（lens）+ 高光与阴影（库默认）。
 * 安卓 13+ 有折射；安卓 12 只有模糊；更老的手机退化为半透明底色。
 */
@Composable
fun Modifier.liquidGlass(
    blurDp: Float = 8f,
    refraction: Float = 20f,
    tint: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    chromatic: Boolean = false,
): Modifier {
    val backdrop: Backdrop = LocalBackdrop.current ?: return this.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val dark = isSystemInDarkTheme()
    val surf = if (surface != Color.Unspecified) surface else if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.38f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            vibrancy()
            blur(blurDp.dp.toPx())
            lens(refraction.dp.toPx(), (refraction * 2f).dp.toPx(), chromaticAberration = chromatic)
        },
        onDrawSurface = {
            if (tint != Color.Unspecified) {
                drawRect(tint, blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = 0.78f))
            } else drawRect(surf)
        },
    )
}

/** 按下时轻微放大，像 iOS 26 的液态按钮 */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    return s
}

/** 圆形玻璃按钮（导航栏上的返回、操作按钮） */
@Composable
fun GlassCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val s = pressScale(src)
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayerScale(s)
            .liquidGlass(blurDp = 6f, refraction = 12f)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** 着色的玻璃胶囊按钮（主要操作） */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val s = pressScale(src)
    Box(
        modifier
            .height(54.dp)
            .graphicsLayerScale(s)
            .liquidGlass(blurDp = 4f, refraction = 16f, tint = if (enabled) tint else Color.Gray)
            .clickable(enabled = enabled, interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.graphicsLayerScale(s: Float): Modifier = this.graphicsLayer { scaleX = s; scaleY = s }

/* ======================= 页面骨架 ======================= */

/**
 * 按外观切换的页面骨架。content 收到的 padding 要当作列表的 contentPadding 用，
 * 这样苹果外观下内容能滑到玻璃条下面、被折射和模糊。
 */
@Composable
fun SkinScaffold(
    title: String,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = onBack == null,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    titleExtra: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalSkin.current == Skin.APPLE) {
        val bg = MaterialTheme.colorScheme.background
        val onDraw: ContentDrawScope.() -> Unit = remember(bg) { { drawRect(bg); drawContent() } }
        val backdrop = rememberLayerBackdrop(onDraw = onDraw)
        val haze = rememberHazeState()
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalHaze provides haze) {
            Scaffold(
                containerColor = bg,
                topBar = { AppleTopBar(title, onBack, largeTitle, subtitle, titleModifier, actions) },
                bottomBar = bottomBar,
                floatingActionButton = fab,
                snackbarHost = { SnackbarHost(snackbar) },
            ) { pad ->
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).hazeSource(haze)) { content(pad) }
            }
        }
    } else {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                // 安卓 17：Expressive 的可伸缩大标题栏（带副标题，滚动时收起）
                LargeFlexibleTopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = titleModifier) },
                    subtitle = subtitle?.let { s -> { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
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

/** 顶部从上往下渐隐的模糊（iOS 26 导航栏没有实底，只有渐变模糊） */
@Composable
private fun Modifier.topFade(): Modifier {
    val haze = LocalHaze.current ?: return this
    val bg = MaterialTheme.colorScheme.background
    val dark = isSystemInDarkTheme()
    return this.hazeBlur(
        input = HazeInput.Sources(haze),
        style = HazeBlurStyle {
            blurEnabled(true)
            blurRadius(24.dp)
            backgroundColor(bg)
            colorEffects(listOf(HazeColorEffect.tint(bg.copy(alpha = if (dark) 0.55f else 0.6f))))
            fallbackColorEffect(HazeColorEffect.tint(bg.copy(alpha = 0.92f)))
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
        },
    )
}

/** iOS 26 导航栏：渐变模糊背景 + 玻璃圆形按钮；主页左对齐大标题，子页面居中小标题 */
@Composable
private fun AppleTopBar(
    title: String,
    onBack: (() -> Unit)?,
    large: Boolean,
    subtitle: String?,
    titleModifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().topFade().statusBarsPadding().padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
            if (onBack != null) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    GlassCircleButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "返回", Modifier.size(20.dp).offset(x = 3.dp)) }
                }
            }
            if (!large) Text(
                title, Modifier.align(Alignment.Center).padding(horizontal = 104.dp),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 每个操作图标放进玻璃胶囊里
                Row(
                    Modifier.height(44.dp).liquidGlass(blurDp = 6f, refraction = 12f).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { actions() }
                }
            }
        }
        if (large) {
            Text(
                title, Modifier.padding(start = 20.dp, end = 20.dp).then(titleModifier),
                fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, Modifier.padding(start = 20.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

data class TabSpec(val title: String, val icon: ImageVector, val badge: Int = 0, val badgeColor: Color = Color.Unspecified)

/** iOS 26 风格的悬浮液态玻璃标签栏：选中项是一颗会滑动的、带色散折射的玻璃珠 */
@Composable
fun AppleTabBar(tabs: List<TabSpec>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var barW by remember { mutableIntStateOf(0) }
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = 380f), label = "tab")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 6.dp, top = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .liquidGlass(blurDp = 8f, refraction = 24f)
                .onSizeChanged { barW = it.width }
                .padding(4.dp),
        ) {
            // 选中的玻璃珠
            if (barW > 0) {
                val inner = with(density) { (barW - 8.dp.roundToPx()).toDp() }
                val itemW = inner / tabs.size
                Box(
                    Modifier
                        .offset(x = itemW * pos)
                        .width(itemW)
                        .fillMaxHeight()
                        .liquidGlass(blurDp = 4f, refraction = 14f, surface = cs.onSurface.copy(alpha = 0.06f), chromatic = true),
                )
            }
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selected
                    val color by animateColorAsState(if (sel) cs.primary else cs.onSurface, label = "tabc")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(t.icon, t.title, tint = color, modifier = Modifier.size(24.dp))
                            if (t.badge > 0) Text(
                                "${t.badge}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)
                                    .clip(CircleShape).background(if (t.badgeColor == Color.Unspecified) cs.error else t.badgeColor)
                                    .padding(horizontal = 5.dp),
                            )
                        }
                        Text(t.title, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = color)
                    }
                }
            }
        }
    }
}

/** 底部操作区：安卓是实色面板，苹果是悬浮在内容上的玻璃按钮 */
@Composable
fun SkinBottomPanel(content: @Composable RowScope.() -> Unit) {
    if (LocalSkin.current == Skin.APPLE) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), content = content)
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), content = content)
        }
    }
}

/** 主要按钮：安卓是 Material 按钮，苹果是着色液态玻璃 */
@Composable
fun SkinPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalSkin.current == Skin.APPLE && LocalBackdrop.current != null) {
        GlassButton(text, onClick, modifier, enabled = enabled)
    } else {
        Button(onClick = onClick, modifier = modifier.height(56.dp), enabled = enabled) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
