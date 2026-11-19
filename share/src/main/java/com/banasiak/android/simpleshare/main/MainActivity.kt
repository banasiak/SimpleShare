package com.banasiak.android.simpleshare.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.banasiak.android.simpleshare.ui.theme.SimpleShareTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SimpleShareTheme {
        MainScreen()
      }
    }
  }
}