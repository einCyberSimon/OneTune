package de.eincybersimon.onetune

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlin.concurrent.thread

/**
 * Handles verified links to the resolver domain: opens the track in the preferred
 * service, or shows a sheet to choose one. Runs in its own task (see the manifest),
 * so the main screen never appears behind the sheet.
 */
class OpenLinkActivity : ComponentActivity() {
    private var state by mutableStateOf<ChooserState>(ChooserState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Exported activity: only trust links on the resolver host, and rebuild the URL from the parsed track.
        val link = intent.data?.takeIf { it.scheme == "https" && it.host == BuildConfig.LINK_HOST }
        val track = link?.let { MusicLinks.parseSharePath(it.path.orEmpty()) }
        // Debug builds show the local resolver's page, release builds the same page as the link.
        val page = track?.let { Resolver.url(it).toUri() } ?: link
        if (track == null) {
            openInBrowser(page)
            return
        }

        val preferences = PreferredService(this)
        val preferred = preferences.service
        setContent {
            OnetuneTheme {
                // A preferred service usually opens at once; do not flash an empty sheet meanwhile.
                if (preferred == null || state != ChooserState.Loading) {
                    ChooserSheet(
                        state = state,
                        onPick = { chosen, always ->
                            if (always && !chosen.isSearch) preferences.service = chosen.service
                            open(chosen.url.toUri())
                            finish()
                        },
                        onOpenInBrowser = { openInBrowser(page) },
                        onDismiss = ::finish,
                    )
                }
            }
        }

        thread {
            val resolved = Resolver.fetch(track)
            runOnUiThread { onResolved(resolved, preferred) }
        }
    }

    private fun onResolved(resolved: ResolvedTrack?, preferred: StreamingService?) {
        if (resolved == null) {
            state = ChooserState.Failed
            return
        }
        val direct = resolved.links.firstOrNull { it.service == preferred && !it.isSearch }
        if (direct != null && open(direct.url.toUri())) {
            finish()
        } else {
            state = ChooserState.Loaded(resolved)
        }
    }

    private fun open(target: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    // The browser selector keeps Android from routing the link straight back to this app.
    private fun openInBrowser(link: Uri?) {
        if (link != null) {
            val browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                .setData(link)
            try {
                startActivity(browser)
            } catch (_: ActivityNotFoundException) {
            }
        }
        finish()
    }
}
