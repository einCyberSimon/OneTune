package de.eincybersimon.onetune

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var needsLinkSetup by mutableStateOf(false)

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OnetuneTheme {
                HomeScreen(needsLinkSetup = needsLinkSetup)
            }
        }
    }

    // The user changes the link settings outside the app, so re-check on return.
    override fun onResume() {
        super.onResume()
        needsLinkSetup = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasUnapprovedLinks()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasUnapprovedLinks(): Boolean {
        val state = getSystemService(DomainVerificationManager::class.java)
            .getDomainVerificationUserState(packageName) ?: return false
        // The resolver host is verified automatically; only the third-party hosts need the user's approval.
        return state.hostToStateMap.any { (host, status) ->
            host != BuildConfig.LINK_HOST && status == DomainVerificationUserState.DOMAIN_STATE_NONE
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun HomeScreen(needsLinkSetup: Boolean) {
    val context = LocalContext.current
    var showLinkHelp by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            context,
                            SettingsActivity::class.java
                        )
                    )
                }) {
                    Text(stringResource(R.string.settings_title))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                BrandMark()
                Text(
                    stringResource(R.string.app_name),
                    Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (needsLinkSetup) {
                LinkSetupPrompt(onClick = { showLinkHelp = true }, Modifier.padding(bottom = 12.dp))
            }
            Button(
                onClick = { openClipboardLink(context) },
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    stringResource(R.string.open_clipboard),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    if (showLinkHelp) {
        LinkHelpSheet(
            onOpenSettings = {
                showLinkHelp = false
                openDefaultLinkSettings(context)
            },
            onDismiss = { showLinkHelp = false },
        )
    }
}

@Composable
private fun BrandMark() {
    Box(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            Modifier.requiredSize(150.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun LinkSetupPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardContainer),
        border = CardBorder,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.open_link_settings),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            supportingContent = { Text(stringResource(R.string.link_setup_hint)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkHelpSheet(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Without the partial state the sheet sits at content height instead of half the screen.
        sheetState = rememberBottomSheetState(
            SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val pager = rememberPagerState(pageCount = { HELP_PAGE_COUNT })
        val scope = rememberCoroutineScope()
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.link_help_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Box {
                // Invisible copies size the box to the taller page, so the sheet keeps one height while paging.
                repeat(HELP_PAGE_COUNT) { page ->
                    HelpPage(
                        page,
                        Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {})
                }
                HorizontalPager(
                    pager,
                    Modifier.matchParentSize(),
                    verticalAlignment = Alignment.Top
                ) { page -> HelpPage(page) }
            }
            PageDots(
                pager.currentPage,
                HELP_PAGE_COUNT,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val onLastPage = pager.currentPage == HELP_PAGE_COUNT - 1
                if (pager.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        Modifier.height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            stringResource(R.string.link_help_back),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Button(
                    onClick = {
                        if (onLastPage) onOpenSettings() else scope.launch {
                            pager.animateScrollToPage(
                                pager.currentPage + 1
                            )
                        }
                    },
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(if (onLastPage) R.string.link_help_open_settings else R.string.link_help_next),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private const val HELP_PAGE_COUNT = 2

@Composable
private fun HelpPage(page: Int, modifier: Modifier = Modifier) {
    when (page) {
        0 -> HelpSection(R.string.link_help_problem_header, modifier.padding(top = 20.dp)) {
            Text(
                stringResource(R.string.link_help_problem),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        else -> HelpSection(R.string.link_help_fix_header, modifier.padding(top = 20.dp)) {
            Text(
                stringResource(R.string.link_help_fix_1),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.link_help_fix_2),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val color =
                if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun HelpSection(
    @StringRes header: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

// Unverified domains (Spotify etc.) only reach this app once the user adds them here; Android offers no way to grant it programmatically.
@RequiresApi(Build.VERSION_CODES.S)
private fun openDefaultLinkSettings(context: Context) {
    val settings = Intent(
        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        "package:${context.packageName}".toUri()
    )
    try {
        context.startActivity(settings)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            )
        )
    }
}

private fun openClipboardLink(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    val track = MusicLinks.findIn(text)
    if (track == null) {
        Toast.makeText(context, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
    } else {
        val link = MusicLinks.shareUrl(BuildConfig.LINK_HOST, track).toUri()
        context.startActivity(Intent(context, OpenLinkActivity::class.java).setData(link))
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    OnetuneTheme { HomeScreen(needsLinkSetup = true) }
}
