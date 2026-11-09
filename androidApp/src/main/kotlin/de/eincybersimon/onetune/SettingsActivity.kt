package de.eincybersimon.onetune

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferences = PreferredService(this)
        val server = ServerPreference(this)
        setContent {
            OnetuneTheme {
                var service by remember { mutableStateOf(preferences.service) }
                var customServer by remember { mutableStateOf(server.custom) }
                SettingsScreen(
                    service = service,
                    onServiceChange = {
                        service = it
                        preferences.service = it
                    },
                    customServer = customServer,
                    onCustomServerChange = {
                        customServer = it
                        server.custom = it
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    service: StreamingService?,
    onServiceChange: (StreamingService?) -> Unit,
    customServer: String?,
    onCustomServerChange: (String?) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val page = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = page,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServiceSection(service, onServiceChange)
            ServerSection(customServer, onCustomServerChange)
        }
    }
}

@Composable
private fun ServiceSection(selected: StreamingService?, onSelect: (StreamingService?) -> Unit) {
    SectionHeader(stringResource(R.string.settings_service_header))
    Card(colors = CardDefaults.cardColors(containerColor = CardContainer), border = CardBorder) {
        Column(Modifier.selectableGroup()) {
            ServiceOption(stringResource(R.string.settings_ask), selected == null) { onSelect(null) }
            for (service in StreamingService.entries) {
                ServiceOption(service.label, selected == service) { onSelect(service) }
            }
        }
    }
}

/** Experimental: the switch is on exactly while a custom server may be active; turning it off restores the default. */
@Composable
private fun ServerSection(saved: String?, onSave: (String?) -> Unit) {
    var enabled by remember { mutableStateOf(saved != null) }
    SectionHeader(stringResource(R.string.settings_experimental_header), Modifier.padding(top = 16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = CardContainer), border = CardBorder) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_server_toggle)) },
            supportingContent = { Text(stringResource(R.string.settings_server_toggle_summary)) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = {
                    enabled = it
                    if (!it && saved != null) onSave(null)
                },
            ),
        )
        if (enabled) {
            ServerField(saved, onSave, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun ServerField(saved: String?, onSave: (String?) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(saved) { mutableStateOf(saved.orEmpty()) }
    val parsed = ServerPreference.normalize(draft)
    val isInvalid = draft.isNotBlank() && parsed == null
    val newValue = if (draft.isBlank()) null else parsed
    val canSave = !isInvalid && newValue != saved
    val save = { if (canSave) onSave(newValue) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_server_label)) },
            placeholder = { Text(BuildConfig.LINK_HOST) },
            supportingText = {
                Text(stringResource(if (isInvalid) R.string.settings_server_invalid else R.string.settings_server_hint))
            },
            isError = isInvalid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Button(onClick = { save() }, enabled = canSave, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.settings_server_save))
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ServiceOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = CardContainer),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    OnetuneTheme {
        SettingsScreen(service = null, onServiceChange = {}, customServer = null, onCustomServerChange = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenCustomServerDarkPreview() {
    OnetuneTheme {
        SettingsScreen(
            service = StreamingService.entries.first(),
            onServiceChange = {},
            customServer = "https://onetune.example.org",
            onCustomServerChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServiceSectionPreview() {
    OnetuneTheme {
        var selected: StreamingService? by remember { mutableStateOf(null) }
        Column(Modifier.padding(16.dp)) { ServiceSection(selected) { selected = it } }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerSectionOffPreview() {
    OnetuneTheme {
        Column(Modifier.padding(16.dp)) { ServerSection(saved = null, onSave = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerSectionOnPreview() {
    OnetuneTheme {
        Column(Modifier.padding(16.dp)) { ServerSection(saved = "https://onetune.example.org", onSave = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerFieldPreview() {
    OnetuneTheme {
        var saved: String? by remember { mutableStateOf(null) }
        ServerField(saved, { saved = it }, Modifier.padding(16.dp))
    }
}
