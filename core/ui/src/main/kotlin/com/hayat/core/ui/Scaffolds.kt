package com.hayat.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.Flow

/** Standard secondary-screen scaffold with a back arrow, actions, FAB and snackbar host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HayatScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    // The bar tints softly once content scrolls beneath it.
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) },
                navigationIcon = {
                    if (onBack != null) {
                        val ext = com.hayat.core.designsystem.theme.HayatThemeExt.colors
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp(), end = 4.dp())
                                .border(1.dp(), ext.hairline, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                    }
                },
                actions = { actions() },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent, // the root backdrop shows through
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    // Skeleton placeholders instead of a spinner: the layout does not jump when content arrives.
    com.hayat.core.designsystem.component.SkeletonPage(modifier)
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** Collects one-off UI messages (string resource ids) into the snackbar. */
@Composable
fun MessageEffect(messages: Flow<UiMessage>, host: SnackbarHostState) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(messages, resources) {
        messages.collect { host.showSnackbar(resources.getString(it.res, *it.args.toTypedArray())) }
    }
}

data class UiMessage(val res: Int, val args: List<Any> = emptyList())
