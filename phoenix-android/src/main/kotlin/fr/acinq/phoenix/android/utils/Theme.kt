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
import androidx.annotation.AttrRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import fr.acinq.phoenix.android.LocalTheme
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.utils.datastore.UserPrefs

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
val gray950 = Color(0xFF171B22)
val gray900 = Color(0xff2b313e)
val gray800 = Color(0xff3e4556)
val gray700 = Color(0xff4e586c)
val gray600 = Color(0xFF5F6A8A)
val gray500 = Color(0xFF73899E)
val gray400 = Color(0xFF8B99AD)
val gray300 = Color(0xff99a2b6)
val gray200 = Color(0xFFB5BBC9)
val gray100 = Color(0xffd1d7e3)
val gray70 = Color(0xffe1eBeD)
val gray50 = Color(0xFFE9F1F3)
val gray30 = Color(0xFFEFF4F5)
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
fun PhoenixAndroidTheme(
    navController: NavController,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val userTheme by UserPrefs.getUserTheme(context).collectAsState(initial = UserTheme.SYSTEM)
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
            val entry = navController.currentBackStackEntryAsState()
            val statusBarColor = systemStatusBarColor
            val navBarColor = systemNavBarColor(entry.value)
            LaunchedEffect(navBarColor, statusBarColor) {
                systemUiController.run {
                    setStatusBarColor(statusBarColor, darkIcons = !isDarkTheme)
                    setNavigationBarColor(navBarColor, darkIcons = !isDarkTheme)
                }
            }
            val rippleIndication = rememberRipple(color = if (isDarkTheme) gray300 else gray600)
            CompositionLocalProvider(LocalIndication provides rippleIndication) {
                content()
            }
        }
    }
}

@Composable
fun mutedItalicTypo(): TextStyle = MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic, color = mutedTextColor)

val monoTypo @Composable get() = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

val warningColor @Composable get() = orange

val negativeColor @Composable get() = if (isDarkTheme) red500 else red300

val positiveColor @Composable get() = if (isDarkTheme) green else applegreen

val mutedTextColor @Composable get() = if (isDarkTheme) gray600 else gray200

val mutedBgColor @Composable get() = if (isDarkTheme) gray950 else gray30

val borderColor @Composable get() = if (isDarkTheme) gray800 else gray70

private val systemStatusBarColor @Composable get() = topGradientColor

/** top gradient is darker/lighter than the background, but not quite black/white */
private val topGradientColor @Composable get() = if (isDarkTheme) gray1000 else gray10
private val bottomGradientColor @Composable get() = MaterialTheme.colors.background

@Composable
fun systemNavBarColor(entry: NavBackStackEntry?): Color {
    return when {
        entry?.destination?.route == Screen.Home.route -> MaterialTheme.colors.surface
        entry?.destination?.route?.startsWith(Screen.PaymentDetails.route) ?: false -> MaterialTheme.colors.surface
        entry?.destination?.route?.startsWith(Screen.ScanData.route) ?: false -> MaterialTheme.colors.surface
        else -> MaterialTheme.colors.background
    }
}

@Composable
fun textFieldColors() = TextFieldDefaults.textFieldColors(
    focusedLabelColor = MaterialTheme.colors.primary,
    backgroundColor = MaterialTheme.colors.surface,
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

@Composable
fun appBackground(): Brush {
    // -- gradient Brush
    val isDark = isDarkTheme
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
