package fr.acinq.phoenix.android.utils

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ContextAmbient


@Composable
val isPreview: Boolean get() = ContextAmbient.current.applicationContext !is Application
