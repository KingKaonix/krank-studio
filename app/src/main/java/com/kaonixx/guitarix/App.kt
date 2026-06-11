package com.kaonixx.guitarix

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaonixx.guitarix.ui.MainScreen

private val DarkBg = Color(0xFF0F0F14)
private val CyanAccent = Color(0xFF00E5FF)
private val RoseAccent = Color(0xFFFF4D6D)

private val GuitarixColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = RoseAccent,
    tertiary = CyanAccent,
    background = DarkBg,
    surface = Color(0xFF1A1A24),
    surfaceVariant = Color(0xFF252530),
    onPrimary = DarkBg,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF88889E)
)

@Composable
fun GuitarixApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    if (!LocalView.current.isInEditMode) {
        val activity = context as? Activity
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            window.statusBarColor = DarkBg.toArgb()
            window.navigationBarColor = DarkBg.toArgb()
        }
    }

    MaterialTheme(colorScheme = GuitarixColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
            MainScreen(vm = viewModel)
        }
    }
}
