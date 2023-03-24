/*
 * Temporary support for styling HTML resource strings in Text compositions.
 * Taken from: https://issuetracker.google.com/issues/139320238#comment11
 */

package fr.acinq.phoenix.android.utils

import android.content.res.Resources
import android.graphics.Typeface
import android.text.Spanned
import android.text.SpannedString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}


// see https://gist.github.com/apkelly/53dfd4debd3e2ad588c2c7ddeebf2756

@Composable
fun annotatedStringResource(@StringRes id: Int, vararg formatArgs: Any): AnnotatedString {
    val resources = resources()
    return remember(id) {
        val text = resources.getText(id, *formatArgs)
        spannableStringToAnnotatedString(text)
    }
}

@Composable
fun annotatedStringResource(@StringRes id: Int): AnnotatedString {
    val resources = resources()
    return remember(id) {
        val text = resources.getText(id)
        spannableStringToAnnotatedString(text)
    }
}

private fun spannableStringToAnnotatedString(
    text: CharSequence
): AnnotatedString {
    return if (text is Spanned) {
        buildAnnotatedString {
            append((text.toString()))
            text.getSpans(0, text.length, Any::class.java).forEach {
                val start = text.getSpanStart(it)
                val end = text.getSpanEnd(it)
                when (it) {
                    is StyleSpan -> when (it.style) {
                        Typeface.NORMAL -> addStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal), start, end)
                        Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Medium, fontStyle = FontStyle.Normal), start, end)
                        Typeface.ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic), start, end)
                        Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic), start, end)
                    }
                    is FontWeight -> addStyle(SpanStyle(fontWeight = FontWeight.Medium, fontStyle = FontStyle.Normal), start, end)
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    else -> addStyle(SpanStyle(), start, end)
                }
            }
        }
    } else {
        AnnotatedString(text.toString())
    }
}

private fun Resources.getText(@StringRes id: Int, vararg args: Any): CharSequence {
    val escapedArgs = args.map {
        if (it is Spanned) it.toHtmlWithoutParagraphs() else it
    }.toTypedArray()
    val resource = SpannedString(getText(id))
    val htmlResource = resource.toHtmlWithoutParagraphs()
    val formattedHtml = String.format(htmlResource, *escapedArgs)
    return HtmlCompat.fromHtml(formattedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

private fun Spanned.toHtmlWithoutParagraphs(): String {
    return HtmlCompat.toHtml(this, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        .substringAfter("<p dir=\"ltr\">").substringBeforeLast("</p>")
}
