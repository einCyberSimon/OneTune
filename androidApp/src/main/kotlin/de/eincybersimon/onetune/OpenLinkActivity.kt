package de.eincybersimon.onetune

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
        val link = intent.data?.takeIf { it.scheme == "https" }
        val track = link?.let {
            if (it.host == BuildConfig.LINK_HOST) MusicLinks.parseSharePath(it.path.orEmpty()) else MusicLinks.parse(
                it.toString()
            )
        }
        // Debug builds show the local resolver's page, release builds the same page as the link.
        val baseUrl = ServerPreference(this).baseUrl
        val isResolverLink = link?.host == BuildConfig.LINK_HOST
        val page =
            if (isResolverLink && track != null) Resolver.url(baseUrl, track).toUri() else link
        val preferences = PreferredService(this)
        val preferred = preferences.service
        if (track == null) {
            openInBrowser(page)
            return
        }


        setContent {
            OnetuneTheme {
                // A preferred service usually opens at once; do not flash an empty sheet meanwhile.
                if (preferred == null || state != ChooserState.Loading) {
                    ChooserSheet(
                        state = state,
                        onPick = { chosen ->
                            val target = chosen.url.toUri()
                            if (open(target)) finish() else openInBrowser(target)
                        },
                        onOpenInBrowser = { openInBrowser(page) },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                            finish()
                        },
                        onDismiss = ::finish,
                    )
                }
            }
        }

        thread {
            val resolved = Resolver.fetch(baseUrl, track)
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

    // Targets another app's handler explicitly: if Onetune is the default for this host, a plain VIEW would loop back here.
    private fun open(target: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE)
        val handler = packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName != packageName }
            ?: return false
        return try {
            startActivity(view.setPackage(handler.activityInfo.packageName))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    // The browser selector keeps Android from routing the link straight back to this app.
    private fun openInBrowser(link: Uri?) {
        if (link != null) {
            val browser =
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                    .setData(link)
            try {
                startActivity(browser)
            } catch (_: ActivityNotFoundException) {
            }
        }
        finish()
    }
}
