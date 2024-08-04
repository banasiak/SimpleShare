package com.banasiak.android.simpleshare

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.android.simpleshare.target.ShareTargetAction
import com.banasiak.android.simpleshare.target.ShareTargetEffect
import com.banasiak.android.simpleshare.target.ShareTargetScreen
import com.banasiak.android.simpleshare.target.ShareTargetViewModel
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SanitizeActivity : ComponentActivity() {

  private val viewModel: ShareTargetViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.effectFlow.collect(::onEffect) }
      }
    }
    enableEdgeToEdge()
    setContent {
      SimpleShareTheme {
        ShareTargetScreen(viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun onEffect(effect: ShareTargetEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      is ShareTargetEffect.Finish -> finish()
      is ShareTargetEffect.ShareUrl -> launchShareIntent(effect.url)
      is ShareTargetEffect.ShowToast -> Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun handleIntent(intent: Intent?) {
    Timber.d("handleIntent(): $intent")
    if (intent?.action==Intent.ACTION_SEND) {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
        viewModel.postAction(ShareTargetAction.IntentReceived(it))
      }
    }
  }

  private fun launchShareIntent(url: String) {
    val shareIntent = Intent().apply {
      action = Intent.ACTION_SEND
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(shareIntent, null))
  }
}
