package com.banasiak.android.simpleshare.sanitize

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme
import kotlinx.coroutines.launch

private typealias InputAction = (SanitizeAction) -> Unit

@Composable
fun SanitizeScreen(viewModel: SanitizeViewModel) {
  val state: SanitizeState by viewModel.stateFlow.collectAsStateWithLifecycle()
  SanitizeViewBottomSheet(state, viewModel::postAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanitizeViewBottomSheet(state: SanitizeState, postAction: InputAction) {
  SimpleShareTheme {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      sheetState = sheetState,
      onDismissRequest = {
        postAction(SanitizeAction.Dismiss)
      },
      windowInsets = WindowInsets.navigationBars
    ) {
      BottomSheetContent(state, postAction, sheetState)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetContent(
  state: SanitizeState,
  postAction: InputAction,
  sheetState: SheetState
) {
  Surface {
    Column(
      modifier =
      Modifier
        .padding(horizontal = 8.dp)
        .verticalScroll(rememberScrollState())
    ) {
      TopHeader(title = R.string.title_activity_sanitize)
      if (state.parameters.isNotEmpty()) {
        SectionHeader(title = R.string.query_parameters)
        for (parameter in state.parameters) {
          ParameterItem(parameter.key, parameter.value, postAction)
        }
      }
      SectionHeader(title = R.string.sanitized_url)
      TextField(
        modifier =
        Modifier
          .padding(4.dp)
          .fillMaxSize(),
        value = state.sanitizedUrl,
        readOnly = true,
        trailingIcon = {
          IconButton(
            enabled = !state.loading,
            onClick = { postAction(SanitizeAction.FetchRedirect) }
          ) {
            Icon(
              painter = painterResource(id = R.drawable.cloud_download),
              contentDescription = stringResource(id = R.string.follow_redirect),
              tint = MaterialTheme.colorScheme.secondary
            )
          }
        },
        onValueChange = { /* NO-OP */ }
      )
      if (state.loading) {
        LinearProgressIndicator(
          modifier =
          Modifier
            .fillMaxWidth()
            .height(2.dp),
          color = MaterialTheme.colorScheme.secondary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      } else {
        Spacer(
          modifier =
          Modifier
            .fillMaxWidth()
            .height(2.dp)
        )
      }
      Buttons(
        enabled = state.sanitizedUrl.isNotEmpty(),
        readOnly = state.readOnly,
        sheetState = sheetState,
        postAction = postAction
      )
    }
  }
}

@Composable
private fun TopHeader(@StringRes title: Int) {
  Row(
    modifier =
    Modifier
      .fillMaxSize()
      .padding(bottom = 16.dp)
      .wrapContentSize(Alignment.Center)
  ) {
    Text(
      text = stringResource(id = title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
  Text(
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    text = stringResource(id = title),
    style = MaterialTheme.typography.labelLarge
  )
}

@Composable
private fun ParameterItem(parameter: QueryParam, value: Boolean, postAction: InputAction) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.Start
  ) {
    Row {
      TextField(
        modifier =
        Modifier
          .padding(4.dp)
          .fillMaxSize(0.8f),
        value = parameter.value ?: "",
        label = { Text(parameter.name) },
        maxLines = 1,
        readOnly = true,
        onValueChange = { /* NO-OP */ }
      )

      val checkedState = remember { mutableStateOf(value) }
      Checkbox(
        modifier =
        Modifier
          .padding(8.dp)
          .fillMaxSize(),
        checked = checkedState.value,
        onCheckedChange = {
          checkedState.value = it
          postAction(SanitizeAction.ParamToggled(parameter, it))
        }
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Buttons(
  enabled: Boolean,
  readOnly: Boolean,
  sheetState: SheetState,
  postAction: InputAction
) {
  val scope = rememberCoroutineScope()
  Column(
    modifier =
    Modifier
      .padding(vertical = 16.dp)
      .fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row {
      if (readOnly) {
        ActionButton(title = R.string.button_share, enabled = enabled) {
          scope.launch {
            sheetState.hide()
            postAction(SanitizeAction.ButtonTapped(ButtonType.SHARE))
          }
        }
        ActionButton(title = R.string.button_copy, enabled = enabled) {
          scope.launch {
            sheetState.hide()
            postAction(SanitizeAction.ButtonTapped(ButtonType.COPY))
          }
        }
        ActionButton(title = R.string.button_open, enabled = enabled, color = MaterialTheme.colorScheme.tertiary) {
          scope.launch {
            sheetState.hide()
            postAction(SanitizeAction.ButtonTapped(ButtonType.OPEN))
          }
        }
      } else {
        ActionButton(title = R.string.button_sanitize, enabled = enabled) {
          scope.launch {
            sheetState.hide()
            postAction(SanitizeAction.ButtonTapped(ButtonType.SANITIZE))
          }
        }
      }
    }
  }
}

@Composable
private fun ActionButton(@StringRes title: Int, enabled: Boolean, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
  Button(
    modifier = Modifier.padding(horizontal = 8.dp),
    enabled = enabled,
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = color)
  ) {
    Text(text = stringResource(title))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SanitizeViewPreview() {
  val state =
    SanitizeState(
      // https://www.banasiak.com/share?utm_source=AAAAA&utm_medium=BBBBBB&utm_campaign=CCCCCC&utm_term=DDDDDD&utm_content=EEEEEE
      sanitizedUrl = "https://www.banasiak.com/share?utm_source=AAAAA&utm_campaign=CCCCCC&utm_content=EEEEEE",
      parameters =
        mapOf(
          QueryParam("utm_source", "AAAAAA") to true,
          QueryParam("utm_medium", "BBBBBB") to false,
          QueryParam("utm_campaign", "CCCCCC") to true,
          QueryParam("utm_term", "DDDDDD") to false,
          QueryParam("utm_content", "EEEEEE") to true
        ),
      readOnly = true,
      loading = false
    )
  BottomSheetContent(state = state, sheetState = rememberModalBottomSheetState(), postAction = { })
}