package com.banasiak.android.simpleshare.sanitize

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SanitizeActivity : ComponentActivity() {
  private val viewModel: SanitizeViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.effectFlow.collect(::onEffect)
        }
        handleIntent(intent)
      }
    }
    enableEdgeToEdge()
    setContent {
      SimpleShareTheme {
        SanitizeScreen(viewModel)
      }
    }

  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun onEffect(effect: SanitizeEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      is SanitizeEffect.Finish -> finish()
      is SanitizeEffect.OpenUrl -> launchOpenIntent(effect.url)
      is SanitizeEffect.ReturnUrl -> returnToSender(effect.url)
      is SanitizeEffect.ShareUrl -> launchShareIntent(effect.url)
      is SanitizeEffect.ShowErrorAndFinish -> {
        Toast.makeText(this, effect.message, Toast.LENGTH_LONG).show()
        finish()
      }

      is SanitizeEffect.ShowToast -> {
        Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun handleIntent(intent: Intent?) {
    Timber.d("handleIntent(): $intent")
    when (intent?.action) {
      Intent.ACTION_SEND -> {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
          viewModel.postAction(SanitizeAction.IntentReceived(text = it, readOnly = true))
        }
      }

      Intent.ACTION_PROCESS_TEXT -> {
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
          val text = it.toString() // discard any spannable markup, only want the text
          val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
          viewModel.postAction(SanitizeAction.IntentReceived(text = text, readOnly = readOnly))
        }
      }
    }
  }

  private fun launchShareIntent(url: String) {
    val shareIntent = Intent().apply {
      action = Intent.ACTION_SEND
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_sanitized)))
    finish()
  }

  private fun launchOpenIntent(url: String) {
    val uri = Uri.parse(url)
    val openIntent = Intent(Intent.ACTION_VIEW, uri)
    startActivity(openIntent)
    finish()
  }

  private fun returnToSender(url: String) {
    val returnIntent = Intent().apply {
      putExtra(Intent.EXTRA_PROCESS_TEXT, url)
    }
    setResult(RESULT_OK, returnIntent)
    finish()
  }
}
