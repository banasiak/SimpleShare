package com.banasiak.android.simpleshare

import android.content.Intent
import android.os.Bundle
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
class MainActivity : ComponentActivity() {

  private val viewModel: ShareTargetViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.action==Intent.ACTION_SEND) {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
        Timber.wtf(it)
        viewModel.postAction(ShareTargetAction.IntentReceived(it))
      }
    }
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

  private fun onEffect(effect: ShareTargetEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      is ShareTargetEffect.ShareUrl -> launchShareIntent(effect.url)
    }
  }

  private fun launchShareIntent(url: String) {
    val shareIntent = Intent().apply {
      action = Intent.ACTION_SEND
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    Timber.wtf("shareIntent: $shareIntent")
    startActivity(Intent.createChooser(shareIntent, null))
  }
}
