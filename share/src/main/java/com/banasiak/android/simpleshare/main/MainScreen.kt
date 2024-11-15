package com.banasiak.android.simpleshare.main

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.Constants
import com.banasiak.android.simpleshare.sanitize.SanitizeActivity
import com.banasiak.android.simpleshare.ui.slideInUp
import com.banasiak.android.simpleshare.ui.slideOutDown
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
  val context = LocalContext.current
  val focus = LocalFocusManager.current
  var textValue by remember { mutableStateOf("") }
  var focusValue by remember { mutableStateOf(true) }

  SimpleShareTheme {
    Column(
      modifier =
        Modifier
          .consumeWindowInsets(WindowInsets.systemBars)
          .fillMaxSize()
          .background(color = MaterialTheme.colorScheme.primaryContainer)
          .padding(top = 64.dp)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top
    ) {
      AnimatedVisibility(visible = !WindowInsets.isImeVisible) {
        Icon(
          modifier =
            Modifier
              .fillMaxWidth(0.5f)
              .aspectRatio(1.0f),
          painter = painterResource(id = R.drawable.sanitize),
          tint = MaterialTheme.colorScheme.primary,
          contentDescription = null
        )
      }
      TextField(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp)
            .onFocusChanged { focusValue = it.isFocused },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions =
          KeyboardActions(
            onGo = {
              focus.clearFocus(force = true)
              launchIntent(context, textValue)
            }
          ),
        minLines = 4,
        maxLines = 4,
        label = { Text(stringResource(id = R.string.title_activity_sanitize)) },
        supportingText = { Text(stringResource(id = R.string.hint_paste_text_here)) },
        value = textValue,
        onValueChange = { textValue = it }
      )
      Button(
        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        enabled = textValue.isNotEmpty(),
        onClick = {
          focus.clearFocus(force = true)
          launchIntent(context, textValue)
        }
      ) {
        Text(text = stringResource(id = R.string.remove_tracking))
      }
      AnimatedVisibility(
        visible = !WindowInsets.isImeVisible,
        enter = fadeIn() + slideInUp(),
        exit = slideOutDown()
      ) {
        Button(
          modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
          onClick = { textValue = Constants.DEMO_URL_LIST.random() }
        ) {
          Text(text = stringResource(id = R.string.feeling_lucky))
        }
      }
      AnimatedVisibility(visible = WindowInsets.isImeVisible) {
        Column(
          modifier = Modifier.wrapContentSize(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(id = R.string.hint_did_you_know),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Text(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            text = stringResource(id = R.string.hint_use_sharesheet_instead),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }
    }
  }
}

private fun launchIntent(context: Context, text: String) {
  context.startActivity(
    Intent(context, SanitizeActivity::class.java)
      .setAction(Intent.ACTION_SEND)
      .putExtra(Intent.EXTRA_TEXT, text)
  )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
  MainScreen()
}