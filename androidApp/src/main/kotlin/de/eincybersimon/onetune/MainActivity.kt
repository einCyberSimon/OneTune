package de.eincybersimon.onetune

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationUserState
import android.content.pm.verify.domain.DomainVerificationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private var needsLinkSetup by mutableStateOf(false)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(needsLinkSetup: Boolean) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { openClipboardLink(context) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_clipboard))
            }
            if (needsLinkSetup && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var showLinkHelp by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showLinkHelp = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_link_settings))
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkHelpSheet(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.link_help_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.link_help_why))
            Text(stringResource(R.string.link_help_steps))
            Button(onClick = onOpenSettings, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.link_help_open_settings))
            }
        }
    }
}

// Unverified domains (Spotify etc.) only reach this app once the user adds them here; Android offers no way to grant it programmatically.
@RequiresApi(Build.VERSION_CODES.S)
private fun openDefaultLinkSettings(context: Context) {
    val settings = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(settings)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    OnetuneTheme { HomeScreen(needsLinkSetup = true) }
}
