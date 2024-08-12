package com.banasiak.android.simpleshare.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

fun slideInUp(): EnterTransition = slideInVertically { 1 }
fun slideOutDown(): ExitTransition = slideOutVertically { -1 }