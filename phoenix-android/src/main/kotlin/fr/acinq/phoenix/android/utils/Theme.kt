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

package fr.acinq.phoenix.android

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// primary for testnet
val horizon = Color(0xff91b4d1)

// primary for mainnet light / success color for light theme
val applegreen = Color(0xff50b338)

// primary for mainnet dark / success color for dark theme
val green = Color(0xff1ac486)

// alternative primary for mainnet
val purple = Color(0xff5741d9)

val red500 = Color(0xffd03d33)
val red300 = Color(0xffc76d6d)
val red50 = Color(0xfff9e9ec)

val white = Color.White
val black = Color.Black

val gray900 = Color(0xff2b313e)
val gray800 = Color(0xff3e4556)
val gray700 = Color(0xff4e586c)
val gray600 = Color(0xff5f6b83)
val gray500 = Color(0xff6e7a94)
val gray400 = Color(0xff838da4)
val gray300 = Color(0xff99a2b6)
val gray200 = Color(0xffb5bccc)
val gray100 = Color(0xffd1d7e3)
val gray70 = Color(0xffe1eBeD)
val gray40 = Color(0xfff0f5f7)

private val LightColorPalette = lightColors(
    // primary
    primary = horizon,
    primaryVariant = horizon,
    onPrimary = white,
    // secondary = primary
    secondary = horizon,
    onSecondary = white,
    // app background
    background = white,
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
    primary = horizon,
    primaryVariant = horizon,
    onPrimary = white,
    // secondary = primary
    secondary = horizon,
    onSecondary = white,
    // app background
    background = gray900,
    onBackground = gray100,
    // components background
    surface = gray900,
    onSurface = gray100,
    // errors
    error = red500,
    onError = red50,
)

@Composable
// Set of Material typography styles to start with
fun typography(palette: Colors) = Typography(
    subtitle1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = palette.primary
    ),
    subtitle2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = palette.onSurface,
    ),
    h3 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        color = palette.onSurface,
    ),
    h6 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        color = palette.onSurface,
    ),
    body1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = palette.onSurface,
    ),
    button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 1.15.sp,
        color = palette.onSurface,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = mutedTextColor()
    )
)

val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun PhoenixAndroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colors = palette,
        typography = typography(palette),
        shapes = shapes
    ) {
        val rippleIndication = rememberRipple(color = if (isSystemInDarkTheme()) gray300 else gray600)
        CompositionLocalProvider(LocalIndication provides rippleIndication) {
            content()
        }
    }
}

@Composable
fun mutedTypo(): TextStyle = MaterialTheme.typography.body1.copy(color = mutedTextColor())

@Composable
fun monoTypo(): TextStyle = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
fun negativeColor(): Color = if (isSystemInDarkTheme()) red500 else red300

@Composable
fun positiveColor(): Color = if (isSystemInDarkTheme()) green else applegreen

@Composable
fun mutedTextColor(): Color = if (isSystemInDarkTheme()) gray700 else gray200

@Composable
fun mutedBgColor(): Color = if (isSystemInDarkTheme()) gray800 else gray40

@Composable
fun baseBackgroundColor(): Color = if (isSystemInDarkTheme()) gray700 else gray70

@Composable
fun borderColor(): Color = if (isSystemInDarkTheme()) gray700 else gray70

@Composable
fun whiteLowOp(): Color = Color(0x33ffffff)

@Composable
fun textFieldColors() = TextFieldDefaults.textFieldColors(backgroundColor = mutedBgColor())

/** Get a color using the old way. Use in legacy AndroidView. */
fun getColor(context: Context, @AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

@Composable
fun appBackground(): Brush = Brush.linearGradient(
    0.2f to MaterialTheme.colors.surface,
    1.0f to baseBackgroundColor(),
    start = Offset.Zero, //Offset(80.0f, 80.0f),
    end = Offset.Infinite, //Offset(100.0f, 100.0f)
)
