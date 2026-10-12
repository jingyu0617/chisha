@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.chisha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 页面骨架：官方 Scaffold + Expressive 的可伸缩大标题栏（带副标题，滚动时收起）。
 * content 收到的 padding 要当作列表的 contentPadding 用。
 */
@Composable
fun AppScaffold(
    title: String,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = titleModifier) },
                subtitle = subtitle?.let { s -> { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = actions,
                scrollBehavior = scroll,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad -> Box(Modifier.fillMaxSize()) { content(pad) } }
}

/** 底部操作区：官方 BottomAppBar 里放一个主要按钮 */
@Composable
fun BottomPanel(content: @Composable RowScope.() -> Unit) {
    BottomAppBar(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content = content)
}

/** 主要按钮：官方 Button（Expressive 中号尺寸） */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Text(text, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}
