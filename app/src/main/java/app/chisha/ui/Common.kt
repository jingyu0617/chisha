@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)

package app.chisha.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Favorite
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

/**
 * 分组列表的一行：官方 Material 3 Expressive 的 SegmentedListItem。
 * 按官方示例：底色用 surfaceContainer（默认是和页面一样的 surface，会看不出分组），行间留 SegmentedGap。
 * 标题、副标题、前后内容都放进官方的槽位，不自己排版。
 * inset = false 时不加左右边距和行间距（外面套了滑动删除时由外层负责）。
 */
@Composable
fun GroupItem(
    index: Int,
    size: Int,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    inset: Boolean = true,
    headline: @Composable () -> Unit,
) {
    val shapes = ListItemDefaults.segmentedShapes(index, size)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val mod = if (inset) Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap)
    else Modifier.fillMaxWidth()
    if (onClick != null) SegmentedListItem(
        onClick = onClick, shapes = shapes, modifier = mod, colors = colors,
        leadingContent = leading, supportingContent = supporting, trailingContent = trailing, content = headline,
    ) else SegmentedListItem(
        shapes = shapes, modifier = mod, colors = colors,
        leadingContent = leading, supportingContent = supporting, trailingContent = trailing, content = headline,
    )
}

/**
 * 带开关的列表行：官方 SegmentedListItem 的“可勾选”形态，整行都能点，右边是官方 Switch。
 */
@Composable
fun SwitchItem(index: Int, size: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, sub: String? = null, inset: Boolean = true) {
    val hp = rememberHaptics()
    SegmentedListItem(
        checked = checked,
        onCheckedChange = { hp.toggle(it); onCheckedChange(it) },
        shapes = ListItemDefaults.segmentedShapes(index, size),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (inset) 16.dp else 0.dp).padding(bottom = ListItemDefaults.SegmentedGap),
    ) { Text(title) }
}

/** 列表行的外形（滑动删除的底色要裁成同样的形状） */
@Composable
fun groupShape(index: Int, size: Int) = ListItemDefaults.segmentedShapes(index, size).shape

/**
 * 提示卡片：官方 Card（默认配色），左边一个图标表示轻重，下面可以放一个按钮。
 * 不再用大块红底/紫底，深色模式下也不刺眼。
 */
@Composable
fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String? = null,
    urgent: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
            }
        }
    }
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
 * 官方 Expressive 的“连接按钮组”（ToggleButton + 连接形状，按下会挤压变形）。
 */
@Composable
fun ChoiceGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hp = rememberHaptics()
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

/** 开关：官方 Switch，切换时有触感反馈 */
@Composable
fun HapticSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hp = rememberHaptics()
    Switch(checked = checked, onCheckedChange = { hp.toggle(it); onCheckedChange(it) })
}

/* ======================= 小部件 ======================= */

/** 保质期的说明文字和颜色（过期红色、快到期用强调色，其余灰色）——放在列表行的副标题里 */
@Composable
fun expColor(level: K.Level?) = when (level) {
    K.Level.BAD -> MaterialTheme.colorScheme.error
    K.Level.WARN -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun recipeMeta(r: Recipe): String = listOfNotNull(
    r.cat,
    r.protein?.let { "蛋白${it}g" },
    r.diff?.let { "★".repeat(it) },
    r.minutes?.let { "约${it}分钟" },
    if (r.spicy) "辣" else null,
).joinToString(" · ")

/** 菜谱一行；菜名带共享元素，点进详情时会飞到标题位置 */
@Composable
fun RecipeRow(
    r: Recipe,
    index: Int,
    size: Int,
    fav: Boolean,
    sub: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    GroupItem(
        index, size, onClick,
        supporting = sub ?: recipeMeta(r).takeIf { it.isNotEmpty() }?.let { m -> { Text(m) } },
        trailing = if (fav) ({ Icon(Icons.Filled.Favorite, "已收藏", tint = MaterialTheme.colorScheme.tertiary) }) else null,
    ) {
        Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedRecipe(r.id))
    }
}

@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth().padding(16.dp)) {
        Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 数量加减。Material 3 没有现成的“加减器”组件，这里用两个官方的 FilledTonalIconButton（Expressive 超小号，
 * 按下会变形）加中间的数字拼成；触摸区域仍是官方保证的 48dp。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QtyControl(text: String, onMinus: () -> Unit, onPlus: () -> Unit, what: String = "") {
    val hp = rememberHaptics()
    val size = IconButtonDefaults.extraSmallContainerSize()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { hp.tick(); onMinus() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(size)) {
            Icon(Icons.Filled.Remove, "减少$what", Modifier.size(IconButtonDefaults.extraSmallIconSize))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 4.dp), maxLines = 1, textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { hp.tick(); onPlus() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(size)) {
            Icon(Icons.Filled.Add, "增加$what", Modifier.size(IconButtonDefaults.extraSmallIconSize))
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
