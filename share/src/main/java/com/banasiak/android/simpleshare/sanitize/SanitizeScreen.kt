package com.banasiak.android.simpleshare.sanitize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

private typealias InputAction = (SanitizeAction) -> Unit

@Composable
fun SanitizeScreen(viewModel: SanitizeViewModel) {
  val state: SanitizeState by viewModel.stateFlow.collectAsStateWithLifecycle()
  SanitizeView(state, viewModel::postAction)
}

@Composable
fun SanitizeView(state: SanitizeState, postAction: InputAction = { }) {
  SimpleShareTheme {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      topBar = { AppBar() }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .padding(innerPadding)
          .padding(horizontal = 8.dp)
          .verticalScroll(rememberScrollState())
      ) {
        if (state.parameters.isNotEmpty()) {
          Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            text = stringResource(id = R.string.query_parameters),
            style = MaterialTheme.typography.labelLarge
          )
        }
        for (parameter in state.parameters) {
          ParameterItem(parameter.key, parameter.value, postAction)
        }
        Text(
          modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
          text = stringResource(id = R.string.sanitized_url),
          style = MaterialTheme.typography.labelLarge
        )
        TextField(
          modifier = Modifier
            .padding(4.dp)
            .fillMaxSize(),
          value = state.sanitizedUrl,
          onValueChange = { }
        )
        Column(
          modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row {
            if (state.readOnly) {
              Button(
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = state.sanitizedUrl.isNotEmpty(),
                onClick = { postAction(SanitizeAction.ButtonTapped(ButtonType.COPY)) }
              ) {
                Text(text = stringResource(R.string.button_copy))
              }
              Button(
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = state.sanitizedUrl.isNotEmpty(),
                onClick = { postAction(SanitizeAction.ButtonTapped(ButtonType.OPEN)) }
              ) {
                Text(text = stringResource(R.string.button_open))
              }
              Button(
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = state.sanitizedUrl.isNotEmpty(),
                onClick = { postAction(SanitizeAction.ButtonTapped(ButtonType.SHARE)) }
              ) {
                Text(text = stringResource(R.string.button_share))
              }
            } else {
              Button(
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = state.sanitizedUrl.isNotEmpty(),
                onClick = { postAction(SanitizeAction.ButtonTapped(ButtonType.SANITIZE)) }
              ) {
                Text(text = stringResource(R.string.button_sanitize))
              }
            }
          }
        }
      }
    }
  }
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
        modifier = Modifier
          .padding(4.dp)
          .fillMaxSize(0.8f),
        value = parameter.value ?: "",
        label = { Text(parameter.name) },
        maxLines = 1,
        onValueChange = { }
      )

      val checkedState = remember { mutableStateOf(value) }
      Checkbox(
        modifier = Modifier
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
private fun AppBar() {
  CenterAlignedTopAppBar(
    title = {
      Text(
        modifier =
        Modifier
          .fillMaxSize()
          .wrapContentSize(Alignment.Center),
        text = stringResource(id = R.string.title_activity_sanitize),
        color = MaterialTheme.colorScheme.primary
      )
    }
  )
}

@Preview
@Composable
fun SanitizeViewPreview() {
  val state = SanitizeState(
    // https://www.banasiak.com/share?utm_source=AAAAA&utm_medium=BBBBBB&utm_campaign=CCCCCC&utm_term=DDDDDD&utm_content=EEEEEE
    sanitizedUrl = "https://www.banasiak.com/share?utm_source=AAAAA&utm_campaign=CCCCCC&utm_content=EEEEEE",
    parameters = mapOf(
      QueryParam("utm_source", "AAAAAA") to true,
      QueryParam("utm_medium", "BBBBBB") to false,
      QueryParam("utm_campaign", "CCCCCC") to true,
      QueryParam("utm_term", "DDDDDD") to false,
      QueryParam("utm_content", "EEEEEE") to true
    ),
    readOnly = true
  )
  SanitizeView(state)
}
