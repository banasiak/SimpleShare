package com.banasiak.android.simpleshare.sanitize

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme
import kotlinx.coroutines.CoroutineScope
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  SimpleShareTheme {
    ModalBottomSheet(
      modifier = Modifier.clearOfSideInsets(WindowInsets.safeDrawing),
      sheetState = sheetState,
      onDismissRequest = { dismissScreen(scope, sheetState, postAction) }
    ) {
      BottomSheetContent(state, postAction, sheetState)
    }
  }
}

// the sheet is centered and at most SheetMaxWidth wide, so a side inset reaches it only by however much
// the inset is wider than the margin beside it. Narrowing by that overlap alone keeps the sheet clear of
// a side navigation bar or cutout without pushing it off-center when there is room
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.clearOfSideInsets(insets: WindowInsets): Modifier =
  layout { measurable, constraints ->
    val margin = (constraints.maxWidth - BottomSheetDefaults.SheetMaxWidth.roundToPx()).coerceAtLeast(0) / 2
    val left = (insets.getLeft(this, layoutDirection) - margin).coerceAtLeast(0)
    val right = (insets.getRight(this, layoutDirection) - margin).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(horizontal = -(left + right)))
    layout(placeable.width + left + right, placeable.height) { placeable.place(left, 0) }
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetContent(
  state: SanitizeState,
  postAction: InputAction,
  sheetState: SheetState
) {
  Column(
    modifier =
      Modifier
        .padding(horizontal = 8.dp)
        .verticalScroll(rememberScrollState())
  ) {
    TopHeader(title = R.string.title_activity_sanitize)
    AnimatedQueryParameters(state, postAction)
    SectionHeader(title = R.string.sanitized_url)
    TextField(
      modifier =
        Modifier
          .padding(4.dp)
          .fillMaxWidth()
          .animateContentSize(
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
              )
          ),
      value = state.sanitizedUrl,
      supportingText = {
        Crossfade(targetState = state.hint, label = "hint") { hint ->
          Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            text = stringResource(id = hint)
          )
        }
      },
      readOnly = true,
      trailingIcon = {
        IconButton(
          enabled = !state.loading && state.canFetchRedirect,
          onClick = { postAction(SanitizeAction.FetchRedirect) }
        ) {
          Icon(
            painter = painterResource(id = R.drawable.cloud_download),
            contentDescription = stringResource(id = R.string.follow_redirect)
          )
        }
      },
      onValueChange = { /* NO-OP */ }
    )
    LoadingIndicator(isLoading = state.loading)
    Buttons(
      enabled = state.sanitizedUrl.isNotEmpty(),
      sheetState = sheetState,
      postAction = postAction
    )
  }
}

@Composable
private fun TopHeader(@StringRes title: Int) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
    horizontalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(id = title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
private fun AnimatedQueryParameters(state: SanitizeState, postAction: InputAction) {
  // everything is going to listen to this flag; flipping it from a LaunchedEffect means the whole
  // list is measured and laid out before it animates in, without writing to state during composition
  var visible: Boolean by remember { mutableStateOf(false) }
  LaunchedEffect(state.parameters) {
    visible = state.parameters.isNotEmpty()
  }

  AnimatedVisibility(
    visible = visible,
    enter = expandIn()
  ) {
    SectionHeader(title = R.string.query_parameters)
  }

  state.parameters.forEachIndexed { index: Int, parameter: QueryParam ->
    AnimatedVisibility(
      visible = visible,
      enter = expandIn()
    ) {
      ParameterItem(
        parameter = parameter,
        onToggle = { enabled -> postAction(SanitizeAction.ParamToggled(index, enabled)) }
      )
    }
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
private fun ParameterItem(parameter: QueryParam, onToggle: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextField(
      modifier =
        Modifier
          .padding(4.dp)
          .weight(1f),
      value = parameter.value ?: "",
      label = { Text(parameter.name) },
      maxLines = 1,
      readOnly = true,
      onValueChange = { /* NO-OP */ }
    )

    // drive the checkbox straight from the hoisted state, otherwise it keeps showing the previous
    // URL's values when the parameter list is replaced (e.g. after decoding a short URL)
    Checkbox(
      modifier =
        Modifier
          .padding(8.dp)
          .semantics { contentDescription = parameter.name },
      checked = parameter.enabled,
      onCheckedChange = onToggle
    )
  }
}

@Composable
private fun LoadingIndicator(isLoading: Boolean) {
  // always reserve the track height so the buttons below don't shift when loading starts and stops
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(2.dp)
  ) {
    AnimatedVisibility(visible = isLoading) {
      LinearProgressIndicator(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(2.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Buttons(
  enabled: Boolean,
  sheetState: SheetState,
  postAction: InputAction
) {
  val scope = rememberCoroutineScope()
  Row(
    modifier =
      Modifier
        .padding(vertical = 16.dp)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.Center
  ) {
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
private fun dismissScreen(scope: CoroutineScope, sheetState: SheetState, postAction: InputAction) {
  scope.launch {
    // trigger the hide sheet animation, then post the Dismiss action to finish the activity once the animation completes
    sheetState.hide()
    postAction(SanitizeAction.Dismiss)
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
        listOf(
          QueryParam("utm_source", "AAAAAA", enabled = true),
          QueryParam("utm_medium", "BBBBBB", enabled = false),
          QueryParam("utm_campaign", "CCCCCC", enabled = true),
          QueryParam("utm_term", "DDDDDD", enabled = false),
          QueryParam("utm_content", "EEEEEE", enabled = true)
        ),
      loading = false
    )
  Surface {
    BottomSheetContent(state = state, sheetState = rememberModalBottomSheetState(), postAction = { })
  }
}