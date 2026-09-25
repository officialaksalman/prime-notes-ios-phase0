package phase0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Spike B. The smallest screen that still proves something: shared UI code, real state,
 * real Material 3, and a real control.
 *
 * This is deliberately NOT any Prime Notes screen — the production UI is untouched.
 */
@Composable
fun Phase0SpikeScreen() {
    var taps by remember { mutableStateOf(0) }

    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Prime Notes — Phase 0 spike",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Taps: $taps")
            Button(onClick = { taps++ }) {
                Text("Tap")
            }
        }
    }
}
