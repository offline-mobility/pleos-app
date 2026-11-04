package io.github.offlinemobility.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChallengeScreen(
                    state = state,
                    onRetry = viewModel::connect,
                )
            }
        }
    }
}

@Composable
private fun ChallengeScreen(
    state: MainUiState,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "HMG Pleos App Challenge",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!state.isConnecting && !state.isConnected) {
                Button(
                    modifier = Modifier.padding(top = 24.dp),
                    onClick = onRetry,
                ) {
                    Text("Pleos SDK 다시 연결")
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ChallengeScreenPreview() {
    MaterialTheme {
        ChallengeScreen(
            state = MainUiState(
                isConnected = true,
                message = "Pleos Vehicle SDK가 연결되었습니다.",
            ),
            onRetry = {},
        )
    }
}

