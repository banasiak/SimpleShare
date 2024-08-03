package com.banasiak.android.simpleshare.target

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

@Composable
fun ShareTargetScreen(
  viewModel: ShareTargetViewModel
) {
  val state: ShareTargetState by viewModel.stateFlow.collectAsStateWithLifecycle()

  SimpleShareTheme {
    Scaffold(
      modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
      Column(
        modifier =
        Modifier
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
      ) {
        Row {
          Text(
            text = state.originalUrl ?: "empty url",
          )
        }
        Row {
          Text(
            text = state.sanitizedUrl ?: "uanble to sanitize"
          )
        }
        Row {
          Button(
            onClick = { viewModel.postAction(ShareTargetAction.ShareUrlTapped) }
          ) {
            Text("Share Sanitized URL")
          }
        }
      }
    }
  }
}
