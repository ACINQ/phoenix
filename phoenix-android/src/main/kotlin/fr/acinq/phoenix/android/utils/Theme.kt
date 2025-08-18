/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.utils

import android.content.Context
import android.util.TypedValue
import android.view.WindowManager
import androidx.annotation.AttrRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import fr.acinq.phoenix.android.LocalTheme
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.utils.extensions.findActivitySafe

// primary for testnet
val horizon = Color(0xff91b4d1)
val azur = Color(0xff25a5ff)

// primary for mainnet light / success color for light theme
val applegreen = Color(0xff50b338)

// primary for mainnet dark / success color for dark theme
val green = Color(0xff1ac486)

// alternative primary for mainnet
val purple = Color(0xff5741d9)

// used for warning
val orange = Color(0xfff3b600)

val red500 = Color(0xffd03d33)
val red300 = Color(0xffc76d6d)
val red50 = Color(0xfff9e9ec)

val white = Color.White
val black = Color.Black

val gray1000 = Color(0xFF111318)
val gray950 = Color(0xFF1B1B29)
val gray900 = Color(0xff2b313e)
val gray800 = Color(0xff3e4556)
val gray700 = Color(0xff4e586c)
val gray600 = Color(0xFF5F6A8A)
val gray500 = Color(0xFF73899E)
val gray400 = Color(0xFF8B99AD)
val gray300 = Color(0xff99a2b6)
val gray200 = Color(0xFFB5BBC9)
val gray100 = Color(0xffd1d7e3)
val gray70 = Color(0xFFDDE8EB)
val gray50 = Color(0xFFE9F1F3)
val gray30 = Color(0xFFF2F6F7)
val gray20 = Color(0xFFF4F7F9)
val gray10 = Color(0xFFF9FAFC)

private val LightColorPalette = lightColors(
    // primary
    primary = azur,
    primaryVariant = azur,
    onPrimary = white,
    // secondary = primary
    secondary = azur,
    secondaryVariant = azur,
    onSecondary = white,
    // app background
    background = gray20,
    onBackground = gray900,
    // components background
    surface = white,
    onSurface = gray900,
    // errors
    error = red300,
    onError = white,
)

private val DarkColorPalette = darkColors(
    // primary
    primary = azur,
    primaryVariant = azur,
    onPrimary = black,
    // secondary = primary
    secondary = azur,
    secondaryVariant = azur,
    onSecondary = black,
    // app background
    background = black,
    onBackground = gray200,
    // components background
    surface = gray950,
    onSurface = gray200,
    // errors
    error = red500,
    onError = red50,
)

@Composable
private fun typography(palette: Colors) = Typography(
    // used by placeholders in input, and for all-caps labels/headers
    subtitle1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = if (isDarkTheme) gray500 else gray300
    ),
    // used for settings' values
    subtitle2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = if (isDarkTheme) gray500 else gray300,
    ),
    h1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        color = palette.onSurface,
    ),
    h2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 38.sp,
        color = palette.onSurface,
    ),
    h3 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        color = palette.onSurface,
    ),
    h4 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = palette.onSurface,
    ),
    h5 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = palette.onSurface,
    ),
    // default style
    body1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = palette.onSurface,
    ),
    // default but bold
    body2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = palette.onSurface,
    ),
    button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        //letterSpacing = 1.15.sp,
        color = palette.onSurface,
    ),
    // basic text but muted
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = mutedTextColor
    )
)

private val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun PhoenixAndroidTheme(content: @Composable () -> Unit) {
    val userTheme = LocalUserPrefs.current?.getUserTheme?.collectAsState(initial = UserTheme.SYSTEM)?.value ?: UserTheme.SYSTEM
    val systemUiController = rememberSystemUiController()

    CompositionLocalProvider(
        LocalTheme provides userTheme
    ) {
        val isDarkTheme = isDarkTheme
        val palette = if (isDarkTheme) DarkColorPalette else LightColorPalette
        MaterialTheme(
            colors = palette,
            typography = typography(palette),
            shapes = shapes
        ) {
            // we must enforce icon colors when the user manually selects the dark theme
            LaunchedEffect(userTheme) {
                systemUiController.run {
                    setStatusBarColor(Color.Transparent, darkIcons = !isDarkTheme)
                }
            }
            val rippleIndication = ripple(color = if (isDarkTheme) gray300 else gray600)
            CompositionLocalProvider(LocalIndication provides rippleIndication) {
                content()
            }
        }
    }
}

val monoTypo @Composable get() = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

val warningColor @Composable get() = orange

val negativeColor @Composable get() = if (isDarkTheme) red500 else red300

val positiveColor @Composable get() = if (isDarkTheme) green else applegreen

val mutedTextColor @Composable get() = if (isDarkTheme) gray600 else gray200

val mutedBgColor @Composable get() = if (isDarkTheme) gray950 else gray30

val borderColor @Composable get() = if (isDarkTheme) gray800 else gray50

fun Color.darken(factor: Float = 0.8f): Color = Color(red = red * factor, green = green * factor, blue = blue * factor, alpha = alpha)

/** top gradient is darker/lighter than the background, but not quite black/white */
private val topGradientColor @Composable get() = if (isDarkTheme) gray1000 else gray10
private val bottomGradientColor @Composable get() = MaterialTheme.colors.background

@Composable
fun invisibleOutlinedTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedLabelColor = MaterialTheme.colors.primary,
    unfocusedLabelColor = MaterialTheme.typography.body1.color,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = Color.Transparent,
    disabledTextColor = MaterialTheme.colors.onSurface,
    disabledBorderColor = Color.Transparent,
    disabledLabelColor = mutedTextColor,
    disabledPlaceholderColor = mutedTextColor,
)

@Composable
fun outlinedTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedLabelColor = MaterialTheme.colors.primary,
    unfocusedLabelColor = MaterialTheme.typography.body1.color,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = MaterialTheme.colors.primary,
    disabledTextColor = MaterialTheme.colors.onSurface,
    disabledBorderColor = mutedBgColor,
    disabledLabelColor = mutedTextColor,
    disabledPlaceholderColor = mutedTextColor,
)

@Composable
fun mutedTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    backgroundColor = mutedTextColor.copy(alpha = 0.12f),
    focusedLabelColor = MaterialTheme.colors.primary,
    unfocusedLabelColor = MaterialTheme.typography.body1.color,
    focusedBorderColor = mutedTextColor.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.Transparent,
    disabledTextColor = MaterialTheme.colors.onSurface,
    disabledBorderColor = Color.Transparent,
    disabledLabelColor = mutedTextColor,
    disabledPlaceholderColor = mutedTextColor,
)

@Composable
fun errorOutlinedTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedLabelColor = negativeColor,
    unfocusedLabelColor = negativeColor,
    focusedBorderColor = negativeColor.copy(alpha = 0.8f),
    unfocusedBorderColor = negativeColor.copy(alpha = 0.8f),
    disabledTextColor = MaterialTheme.colors.onSurface,
    disabledBorderColor = mutedBgColor,
    disabledLabelColor = mutedTextColor,
    disabledPlaceholderColor = mutedTextColor,
)

/** Get a color using the old way. Use in legacy AndroidView. */
fun getColor(context: Context, @AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

fun updateScreenBrightnesss(context: Context, toMax: Boolean) {
    val activity = context.findActivitySafe() ?: return
    activity.window.attributes.apply {
        screenBrightness = if (toMax) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }.let {
        activity.window.attributes = it
    }
}

@Composable
fun appBackground(): Brush {
    // -- gradient Brush
    return Brush.linearGradient(
        0.2f to topGradientColor,
        1.0f to bottomGradientColor,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}

enum class UserTheme {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun safeValueOf(value: String?): UserTheme = when (value) {
            LIGHT.name -> LIGHT
            DARK.name -> DARK
            SYSTEM.name -> SYSTEM
            else -> SYSTEM
        }
    }
}
