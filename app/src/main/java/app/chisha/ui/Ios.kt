package app.chisha.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* iOS 风格的控件：苹果外观下替换安卓的开关、分段按钮、搜索框 */

private val IosGreen = Color(0xFF34C759)
private val IosGreenDark = Color(0xFF30D158)

/** iOS 开关：51×31，绿色轨道，白色圆形滑块 */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val track by animateColorAsState(
        if (checked) (if (dark) IosGreenDark else IosGreen) else (if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA)),
        label = "track",
    )
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "thumb")
    Box(
        modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .semantics { role = Role.Switch; stateDescription = if (checked) "开" else "关" },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 按外观切换的开关 */
@Composable
fun SkinSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hp = rememberHaptics()
    val cb: (Boolean) -> Unit = { hp.toggle(it); onCheckedChange(it) }
    if (LocalSkin.current == Skin.APPLE) IosSwitch(checked, cb) else Switch(checked, cb)
}

/** iOS 分段控件：灰色轨道 + 滑动的白色滑块 */
@Composable
fun IosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var w by remember { mutableIntStateOf(0) }
    val itemW = with(density) { ((w - 4.dp.roundToPx()).coerceAtLeast(0) / options.size.coerceAtLeast(1)).toDp() }
    val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFEEEEEF))
            .onSizeChanged { w = it.width }
            .padding(2.dp),
    ) {
        if (w > 0) Box(
            Modifier
                .offset(x = x)
                .width(itemW)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(if (dark) Color(0xFF636366) else Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (i != selected) onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = i == selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** iOS 搜索框：灰色圆角 + 放大镜 + 清除按钮 */
@Composable
fun IosSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (dark) Color(0xFF1C1C1E) else Color(0xFFE3E3E8))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, fontSize = 17.sp)
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 17.sp),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant) {
                Icon(
                    Icons.Filled.Cancel, "清除", Modifier.size(18.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onQueryChange("") },
                )
            }
        }
    }
}
