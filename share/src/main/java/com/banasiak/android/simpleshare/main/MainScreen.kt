package com.banasiak.android.simpleshare.main

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.Constants
import com.banasiak.android.simpleshare.sanitize.SanitizeActivity
import com.banasiak.android.simpleshare.ui.slideInUp
import com.banasiak.android.simpleshare.ui.slideOutDown
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

// the form is unreadable if it is allowed to stretch the full width of a tablet or an unfolded device
private val MAX_CONTENT_WIDTH = 600.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
  val context = LocalContext.current
  val focus = LocalFocusManager.current
  // survives rotation and process death; a plain remember{} loses whatever was typed
  var textValue by rememberSaveable { mutableStateOf("") }

  val onSubmit = {
    focus.clearFocus(force = true)
    launchIntent(context, textValue)
  }

  SimpleShareTheme {
    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxSize()
          .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
      // a landscape phone has nowhere near enough height to stack the logo on top of the form,
      // so sit them side by side and shrink the logo to fit whichever dimension is scarcer
      val landscape = maxWidth > maxHeight
      val logoSize = if (landscape) minOf(maxWidth * 0.3f, maxHeight * 0.65f) else maxWidth * 0.5f
      val spacing = if (landscape) 16.dp else 64.dp

      val insetModifier =
        Modifier
          .fillMaxSize()
          // safeDrawing covers the status bar, navigation bar, display cutout and the IME
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 16.dp)

      if (landscape) {
        Row(
          modifier = insetModifier,
          verticalAlignment = Alignment.CenterVertically
        ) {
          AnimatedVisibility(visible = !WindowInsets.isImeVisible) {
            Logo(size = logoSize)
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(
            modifier =
              Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            SanitizeForm(textValue, { textValue = it }, onSubmit, spacing)
          }
        }
      } else {
        Column(
          modifier =
            insetModifier
              .verticalScroll(rememberScrollState())
              .padding(top = spacing),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Top
        ) {
          AnimatedVisibility(visible = !WindowInsets.isImeVisible) {
            Logo(size = logoSize)
          }
          SanitizeForm(textValue, { textValue = it }, onSubmit, spacing)
        }
      }
    }
  }
}

@Composable
private fun Logo(size: Dp) {
  Icon(
    modifier = Modifier.size(size),
    painter = painterResource(id = R.drawable.sanitize),
    tint = MaterialTheme.colorScheme.primary,
    contentDescription = null
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SanitizeForm(
  textValue: String,
  onTextChange: (String) -> Unit,
  onSubmit: () -> Unit,
  spacing: Dp
) {
  TextField(
    modifier =
      Modifier
        .widthIn(max = MAX_CONTENT_WIDTH)
        .fillMaxWidth()
        .padding(top = spacing, bottom = 16.dp),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    minLines = 4,
    maxLines = 4,
    label = { Text(stringResource(id = R.string.title_activity_sanitize)) },
    supportingText = { Text(stringResource(id = R.string.hint_paste_text_here)) },
    value = textValue,
    onValueChange = onTextChange
  )
  Button(
    modifier = Modifier.padding(top = spacing / 2, bottom = 16.dp),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    enabled = textValue.isNotEmpty(),
    onClick = onSubmit
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
      onClick = { onTextChange(Constants.DEMO_URL_LIST.random()) }
    ) {
      Text(text = stringResource(id = R.string.feeling_lucky))
    }
  }
  AnimatedVisibility(visible = WindowInsets.isImeVisible) {
    Column(
      modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH),
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

@Preview(showBackground = true, showSystemUi = true, device = "spec:parent=pixel_9,orientation=landscape")
@Composable
fun MainScreenLandscapePreview() {
  MainScreen()
}