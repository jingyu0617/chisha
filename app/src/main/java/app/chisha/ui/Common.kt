@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)

package app.chisha.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.chisha.K
import app.chisha.Recipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底部提示条，可带“撤销” */
class Toaster(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(msg: String, undo: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(
                message = msg,
                actionLabel = if (undo != null) "撤销" else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

val LocalToaster = staticCompositionLocalOf<Toaster> { error("no toaster") }

/* ======================= 触感反馈 ======================= */

/** 统一的震动反馈：确认、拒绝/删除、开关、分段切换、手势越过阈值 */
class Haptics(private val h: HapticFeedback) {
    fun confirm() = h.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject() = h.performHapticFeedback(HapticFeedbackType.Reject)
    fun toggle(on: Boolean) = h.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun tick() = h.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun threshold() = h.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

@Composable
fun rememberHaptics(): Haptics {
    val h = LocalHapticFeedback.current
    return remember(h) { Haptics(h) }
}

/* ======================= 共享元素（列表 → 详情） ======================= */

/** 菜名在列表和详情页之间“飞过去” */
@Composable
fun Modifier.sharedRecipe(id: String, part: String = "title"): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedRecipe.sharedBounds(rememberSharedContentState("recipe-$id-$part"), animatedVisibilityScope = anim)
    }
}

/* ======================= 分组标题、列表 ======================= */

@Composable
fun SectionTitle(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** iOS 的“内嵌分组”列表：首尾圆角、中间直角、细分隔线 */
fun iosGroupShape(index: Int, size: Int): RoundedCornerShape {
    val r = 22.dp
    return when {
        size == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == size - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}

/**
 * 分组列表的一行。
 * 安卓外观：官方 Material 3 Expressive 的 SegmentedListItem（分段圆角、按压形变、标准配色）。
 * 苹果外观：iOS 内嵌分组样式。
 */
@Composable
fun GroupItem(
    index: Int,
    size: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current == Skin.APPLE) {
        Surface(
            shape = iosGroupShape(index, size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        return
    }
    val shapes = ListItemDefaults.segmentedShapes(index, size)
    val mod = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 2.dp)
    val body: @Composable () -> Unit = { Row(verticalAlignment = Alignment.CenterVertically, content = content) }
    if (onClick != null) SegmentedListItem(onClick = onClick, shapes = shapes, modifier = mod, content = body)
    else SegmentedListItem(shapes = shapes, modifier = mod, content = body)
}

/* ======================= 芯片 ======================= */

@Composable
fun ChipRow(
    options: List<String>,
    selected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    label: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    val hp = rememberHaptics()
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { o ->
            val sel = selected(o)
            FilterChip(
                selected = sel,
                onClick = { hp.toggle(!sel); onToggle(o) },
                label = { Text(label(o)) },
                leadingIcon = if (sel) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

@Composable
fun ChipFlow(
    options: List<String>,
    selected: (String) -> Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hp = rememberHaptics()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o ->
            FilterChip(selected = selected(o), onClick = { hp.tick(); onClick(o) }, label = { Text(o) })
        }
    }
}

/* ======================= 单选按钮组 ======================= */

/**
 * 单选按钮组。
 * 安卓外观：Expressive 的“连接按钮组”（ToggleButton + 连接形状，按下会挤压变形）。
 * 苹果外观：iOS 分段控件。
 */
@Composable
fun SkinSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hp = rememberHaptics()
    if (LocalSkin.current == Skin.APPLE) {
        IosSegmented(options, selected, { hp.tick(); onSelect(it) }, modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = selected == i,
                onCheckedChange = { if (selected != i) { hp.tick(); onSelect(i) } },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ======================= 搜索 ======================= */

/** 搜索框：安卓用官方 SearchBar；苹果用 iOS 风格的灰色圆角搜索框 */
@Composable
fun SkinSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    if (LocalSkin.current == Skin.APPLE) {
        IosSearchField(query, onQueryChange, placeholder, modifier)
        return
    }
    val tfs = rememberTextFieldState(query)
    val sbs = rememberSearchBarState()
    LaunchedEffect(tfs) { snapshotFlow { tfs.text.toString() }.collectLatest { onQueryChange(it) } }
    SearchBar(
        state = sbs,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = tfs, searchBarState = sbs, onSearch = { onQueryChange(it) },
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
            )
        },
        modifier = modifier,
    )
}

/* ======================= 小部件 ======================= */

@Composable
fun ExpBadge(exp: String?) {
    val info = K.expInfo(K.daysLeft(exp)) ?: return
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (info.second) {
        K.Level.BAD -> cs.errorContainer to cs.onErrorContainer
        K.Level.WARN -> cs.tertiaryContainer to cs.onTertiaryContainer
        K.Level.OK -> Color.Transparent to cs.onSurfaceVariant
    }
    Text(
        info.first,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = if (info.second == K.Level.OK) 0.dp else 6.dp, vertical = 1.dp),
    )
}

@Composable
fun Tag(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

fun recipeMeta(r: Recipe): String = listOfNotNull(
    r.protein?.let { "蛋白${it}g" },
    r.diff?.let { "★".repeat(it) },
    r.minutes?.let { "约${it}分钟" },
    if (r.spicy) "辣" else null,
).joinToString(" · ")

/** 菜谱卡片；菜名带共享元素，点进详情时会飞到标题位置 */
@Composable
fun RecipeRow(
    r: Recipe,
    index: Int,
    size: Int,
    fav: Boolean,
    sub: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    GroupItem(index, size, onClick) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedRecipe(r.id),
                )
                if (fav) {
                    Spacer(Modifier.width(4.dp))
                    Text("★", color = MaterialTheme.colorScheme.tertiary, fontSize = 14.sp)
                }
            }
            if (sub != null) sub() else {
                val m = recipeMeta(r)
                if (m.isNotEmpty()) Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Tag(r.cat)
    }
}

@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(text, Modifier.padding(28.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 数量加减器（图标按钮带读屏说明，每次加减有轻微震动） */
@Composable
fun QtyStepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, what: String = "") {
    val hp = rememberHaptics()
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { hp.tick(); onMinus() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, "减少$what", Modifier.size(18.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 48.dp), maxLines = 1, textAlign = TextAlign.Center)
            IconButton(onClick = { hp.tick(); onPlus() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, "增加$what", Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val hp = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { hp.confirm(); onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 主页加载中：Expressive 的变形加载指示器 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}
