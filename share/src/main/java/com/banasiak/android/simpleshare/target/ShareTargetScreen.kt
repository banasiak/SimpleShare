package com.banasiak.android.simpleshare.target

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

private typealias InputAction = (ShareTargetAction) -> Unit

@Composable
fun ShareTargetScreen(viewModel: ShareTargetViewModel) {
  val state: ShareTargetState by viewModel.stateFlow.collectAsStateWithLifecycle()
  ShareTargetView(state, viewModel::postAction)
}

@Composable
fun ShareTargetView(state: ShareTargetState, postAction: InputAction = { }) {
  SimpleShareTheme {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      topBar = { AppBar() }
    ) { innerPadding ->
      Column(
        modifier =
        Modifier
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
      ) {
        Row {
          Text(
            text = "Query Parameters",
          )
        }
        for (parameter in state.parameters) {
          Row {
            Item(parameter.key, parameter.value, postAction)
          }
        }
        Row {
          Text(
            text = "Sanitized URL"
          )
        }
        Row {
          Text(
            text = state.sanitizedUrl ?: ""
          )
        }
        Row {
          Button(
            enabled = state.sanitizedUrl!=null,
            onClick = { postAction(ShareTargetAction.CopyUrlTapped) }
          ) {
            Text(text = stringResource(R.string.copy))
          }
          Button(
            enabled = state.sanitizedUrl!=null,
            onClick = { postAction(ShareTargetAction.ShareUrlTapped) }
          ) {
            Text(text = stringResource(R.string.share))
          }
        }
      }
    }
  }
}

@Composable
private fun Item(parameter: QueryParam, value: Boolean, postAction: InputAction) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.Start
  ) {
    Row {
      Text(parameter.name)
    }
    Row {
      Text(parameter.value ?: "")
    }

  }
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.End
  ) {
    val checkedState = remember { mutableStateOf(false) }
    Checkbox(
      checked = checkedState.value,
      onCheckedChange = {
        checkedState.value = it
        postAction(ShareTargetAction.ParamToggled(parameter, it))
      }
    )
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
fun ShareTargetViewPreview() {
  val state = ShareTargetState(
    // https://www.banasiak.com/share?utm_source=AAAAA&utm_medium=BBBBBB&utm_campaign=CCCCCC&utm_term=DDDDDD&utm_content=EEEEEE
    sanitizedUrl = "https://www.banasiak.com/share?utm_source=AAAAA&utm_campaign=CCCCCC&utm_content=EEEEEE",
    parameters = mapOf(
      QueryParam("utm_source", "AAAAAA") to true,
      QueryParam("utm_medium", "BBBBBB") to false,
      QueryParam("utm_campaign", "CCCCCC") to true,
      QueryParam("utm_term", "DDDDDD") to false,
      QueryParam("utm_content", "EEEEEE") to true
    )
  )
  ShareTargetView(state)
}
