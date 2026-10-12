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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch

/**
 * 顶栏搜索（官方 AppBarWithSearch + ExpandedFullScreenContainedSearchBar）。
 * 平时顶栏就是一条搜索框，点进去展开成全屏搜索；results 收到输入的文字和“收起”动作。
 */
class SearchSpec(
    val placeholder: String,
    val results: @Composable (query: String, close: () -> Unit) -> Unit,
)

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
    search: SearchSpec? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (search != null) {
        SearchScaffold(search, snackbar, actions, bottomBar, fab, content)
        return
    }
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

@Composable
private fun SearchScaffold(
    search: SearchSpec,
    snackbar: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val text = rememberTextFieldState()
    val state = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    // 顶栏底色明确用 surface（这个测试版默认是透明的，滚动时列表会从后面透出来）
    val colors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.containedColors(state = state),
        appBarContainerColor = MaterialTheme.colorScheme.surface,
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.surface,
    )
    val close: () -> Unit = { scope.launch { state.animateToCollapsed() } }
    val input = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = state,
            onSearch = { },
            colors = colors.searchBarColors.inputFieldColors,
            placeholder = { Text(search.placeholder, Modifier.clearAndSetSemantics {}) },
            leadingIcon = {
                if (state.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                } else Icon(Icons.Outlined.Search, null)
            },
            trailingIcon = {
                if (text.text.isNotEmpty()) IconButton(onClick = { text.clearText() }) { Icon(Icons.Outlined.Close, "清空") }
            },
        )
    }
    // 搜索栏固定在顶上，不随滚动藏起来（藏起来时内容会透到状态栏下面）
    Scaffold(
        topBar = {
            AppBarWithSearch(state = state, inputField = input, colors = colors, actions = actions)
            ExpandedFullScreenContainedSearchBar(state = state, inputField = input, colors = colors.searchBarColors) {
                search.results(text.text.toString(), close)
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad -> Box(Modifier.fillMaxSize()) { content(pad) } }
}
