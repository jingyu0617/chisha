@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.chisha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 分组标题 */
@Composable
fun SectionTitle(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Expressive 风格的“连在一起”的列表：同组的项目上下紧挨，
 * 首尾大圆角、中间小圆角（和安卓 16/17 系统设置里的列表一致）。
 */
fun groupShape(index: Int, size: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return when {
        size == 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        index == size - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
        else -> RoundedCornerShape(small)
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

@Composable
fun GroupItem(
    index: Int,
    size: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val apple = LocalSkin.current == Skin.APPLE
    Surface(
        shape = if (apple) iosGroupShape(index, size) else groupShape(index, size),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = if (apple) 0.dp else 2.dp),
    ) {
        if (apple && index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 一排可横滑的筛选芯片 */
@Composable
fun ChipRow(
    options: List<String>,
    selected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    label: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { o ->
            val sel = selected(o)
            FilterChip(
                selected = sel,
                onClick = { onToggle(o) },
                label = { Text(label(o)) },
                leadingIcon = if (sel) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

/** 换行的芯片（表单里用） */
@Composable
fun ChipFlow(
    options: List<String>,
    selected: (String) -> Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o ->
            FilterChip(selected = selected(o), onClick = { onClick(o) }, label = { Text(o) })
        }
    }
}

/** 保质期小标签 */
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

/** 菜谱卡片 */
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
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** 数量加减器 */
@Composable
fun QtyStepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) { Text("−", fontSize = 18.sp) }
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 48.dp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) { Text("+", fontSize = 18.sp) }
        }
    }
}

/** 二次确认对话框 */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
