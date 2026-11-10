package de.eincybersimon.onetune

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal sealed interface ChooserState {
    data object Loading : ChooserState
    data object Failed : ChooserState
    data class Loaded(val track: ResolvedTrack) : ChooserState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChooserSheet(
    state: ChooserState,
    onPick: (ServiceLink) -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Without the partial state the sheet sits at content height instead of half the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        when (state) {
            ChooserState.Loading -> LoadingContent()
            ChooserState.Failed -> FailedContent(onOpenInBrowser)
            is ChooserState.Loaded -> LoadedContent(state.track, onPick, onOpenSettings)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.finding_song), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun FailedContent(onOpenInBrowser: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.load_failed), style = MaterialTheme.typography.bodyLarge)
        TextButton(onOpenInBrowser) { Text(stringResource(R.string.open_in_browser)) }
    }
}

@Composable
private fun LoadedContent(
    track: ResolvedTrack,
    onPick: (ServiceLink) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier
        .padding(horizontal = 24.dp)
        .padding(top = 4.dp, bottom = 16.dp)) {
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Exact matches first: a search fallback should never sit above a direct link.
    val links = track.links.sortedBy { it.isSearch }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.chooser_prompt),
            Modifier.padding(start = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = CardContainer),
            border = CardBorder,
        ) {
            Spacer(Modifier.height(4.dp))
            links.forEachIndexed { index, link ->
                if (index > 0) HorizontalDivider(
                    Modifier.padding(start = 76.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ListItem(
                    headlineContent = {
                        Text(
                            link.service.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (link.isSearch) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        )
                    },
                    supportingContent = if (link.isSearch) ({ Text(stringResource(R.string.search_only)) }) else null,
                    leadingContent = { ServiceBadge(link.service, muted = link.isSearch) },
                    modifier = Modifier.clickable { onPick(link) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        DefaultServiceHint(
            onOpenSettings,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun DefaultServiceHint(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val settings = stringResource(R.string.settings_title)
    val hint = stringResource(R.string.chooser_default_hint, settings)
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    val text = buildAnnotatedString {
        append(hint)
        val start = hint.indexOf(settings)
        if (start >= 0) addLink(
            LinkAnnotation.Clickable(
                "settings",
                linkStyle
            ) { onOpenSettings() }, start, start + settings.length
        )
    }
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// The service's logo in white on its brand color, like an app icon; search-only rows go neutral.
@Composable
private fun ServiceBadge(service: StreamingService, muted: Boolean) {
    val (background, glyph) = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        when (service) {
            StreamingService.Spotify -> Color(0xFF1DB954)
            StreamingService.AppleMusic -> Color(0xFFFA243C)
            StreamingService.Tidal -> Color(0xFF111111)
            StreamingService.Deezer -> Color(0xFFA238FF)
            StreamingService.YouTubeMusic -> Color(0xFFE6002E)
        } to Color.White
    }
    val logo = when (service) {
        StreamingService.Spotify -> R.drawable.ic_service_spotify
        StreamingService.AppleMusic -> R.drawable.ic_service_apple_music
        StreamingService.Tidal -> R.drawable.ic_service_tidal
        StreamingService.Deezer -> R.drawable.ic_service_deezer
        StreamingService.YouTubeMusic -> R.drawable.ic_service_youtube_music
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(logo), contentDescription = null, Modifier.size(26.dp), tint = glyph)
    }
}

// ChooserSheet itself is not previewed: ModalBottomSheet renders in its own window, which previews do not show.
@Composable
private fun SheetPreview(content: @Composable () -> Unit) {
    OnetuneTheme {
        Surface { Column { content() } }
    }
}

private val previewTrack = ResolvedTrack(
    title = "Go Back",
    artist = "Velogic",
    links = StreamingService.entries.mapIndexed { index, service ->
        ServiceLink(
            service,
            "https://${service.webHost}",
            isSearch = index == 1
        )
    },
)

@Preview(showBackground = true)
@Composable
private fun LoadingContentPreview() = SheetPreview { LoadingContent() }

@Preview(showBackground = true)
@Composable
private fun FailedContentPreview() = SheetPreview { FailedContent(onOpenInBrowser = {}) }

@Preview(showBackground = true)
@Composable
private fun LoadedContentPreview() =
    SheetPreview { LoadedContent(previewTrack, onPick = {}, onOpenSettings = {}) }

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServiceBadgePreview() = SheetPreview {
    Row {
        for (service in StreamingService.entries) ServiceBadge(service, muted = false)
        ServiceBadge(StreamingService.Spotify, muted = true)
    }
}
