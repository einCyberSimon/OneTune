package de.eincybersimon.onetune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferences = PreferredService(this)
        setContent {
            OnetuneTheme {
                var selected by remember { mutableStateOf(preferences.service) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeContentPadding().padding(16.dp).selectableGroup()) {
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
                        ServiceOption(stringResource(R.string.settings_ask), selected == null) {
                            selected = null
                            preferences.service = null
                        }
                        for (service in StreamingService.entries) {
                            ServiceOption(service.label, selected == service) {
                                selected = service
                                preferences.service = service
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 16.dp))
    }
}
