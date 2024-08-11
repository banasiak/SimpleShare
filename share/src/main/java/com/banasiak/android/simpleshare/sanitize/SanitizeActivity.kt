package com.banasiak.android.simpleshare.sanitize

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
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
    // handle NavigationBar window insets manually in the BottomSheet so this transparent activity cleanly overlays the app that calls our intent
    WindowCompat.setDecorFitsSystemWindows(window, true)
    setContent {
      LocalLifecycleOwner.current.lifecycle.addObserver(viewModel)
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
      is SanitizeEffect.ShowErrorAndFinish -> showErrorAndFinish(effect.message)
      is SanitizeEffect.ShowRateAppDialog -> showRateAppDialog()
      is SanitizeEffect.ShowToast -> Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun handleIntent(intent: Intent?) {
    Timber.d("handleIntent(): $intent")
    when (intent?.action) {
      Intent.ACTION_SEND -> {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        viewModel.postAction(SanitizeAction.IntentReceived(text = text, readOnly = true))
      }
      Intent.ACTION_PROCESS_TEXT -> {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() // discard any spannable markup
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        viewModel.postAction(SanitizeAction.IntentReceived(text = text, readOnly = readOnly))
      }
      else -> showErrorAndFinish(R.string.url_not_detected)
    }
  }

  private fun launchShareIntent(url: String) {
    val shareIntent =
      Intent().apply {
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
    val returnIntent =
      intent.apply {
        putExtra(Intent.EXTRA_PROCESS_TEXT, url)
      }
    setResult(RESULT_OK, returnIntent)
    finish()
  }

  private fun showRateAppDialog() {
    lifecycleScope.launch {
      try {
        val context = this@SanitizeActivity
        val reviewManager = ReviewManagerFactory.create(context)
        val reviewInfo = reviewManager.requestReview()
        reviewManager.launchReview(context, reviewInfo)
      } catch (e: Exception) {
        Timber.e(e, "Unable to show Google Play rate app dialog")
      }
    }
  }

  private fun showErrorAndFinish(@StringRes message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    finish()
  }
}