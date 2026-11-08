package de.eincybersimon.onetune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    onPick: (ServiceLink, Boolean) -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    var always by remember { mutableStateOf(false) }
    // Skipping the partial state keeps the sheet at content height instead of half the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent,
    ) {
        when (state) {
            ChooserState.Loading -> LoadingContent()
            ChooserState.Failed -> FailedContent(onOpenInBrowser)
            is ChooserState.Loaded -> LoadedContent(state.track, always, { always = it }, onPick)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.finding_song), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp))
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
    always: Boolean,
    onAlwaysChange: (Boolean) -> Unit,
    onPick: (ServiceLink, Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
        Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    for (link in track.links) {
        ListItem(
            headlineContent = { Text(link.service.label) },
            supportingContent = if (link.isSearch) ({ Text(stringResource(R.string.search_only)) }) else null,
            leadingContent = { ServiceBadge(link.service.label) },
            modifier = Modifier.clickable { onPick(link, always) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Row(
        Modifier.fillMaxWidth().clickable { onAlwaysChange(!always) }.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(always, onCheckedChange = null)
        Text(stringResource(R.string.always_use_choice), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ServiceBadge(label: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
