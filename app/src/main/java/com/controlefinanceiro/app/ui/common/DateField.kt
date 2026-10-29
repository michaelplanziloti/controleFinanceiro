package com.controlefinanceiro.app.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.data.sheets.parseDate

/** Digits of a date as the field keeps them: "21122026" for 21/12/2026. */
fun dateDigits(epochDay: Long?): String = epochDay?.let { formatDate(it).filter(Char::isDigit) }.orEmpty()

/** The date typed in a [DateField], or null while it is incomplete or does not exist (e.g. 31/02). */
fun parseDateDigits(digits: String): Long? =
    if (digits.length == 8) parseDate("${digits.take(2)}/${digits.substring(2, 4)}/${digits.drop(4)}") else null

/** Shows "21122026" as "21/12/2026": a slash goes in after the day and after the month as they are typed. */
private object DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (offset > 2) 1 else 0) + (if (offset > 4) 1 else 0)
            override fun transformedToOriginal(offset: Int) =
                (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/**
 * A date typed with numbers only; the slashes of dd/mm/aaaa appear by themselves. [digits] holds
 * up to 8 digits (see [dateDigits] and [parseDateDigits]).
 */
@Composable
fun DateField(label: String, digits: String, onDigits: (String) -> Unit, modifier: Modifier = Modifier) {
    val invalid = digits.isNotEmpty() && parseDateDigits(digits) == null
    OutlinedTextField(
        value = digits,
        onValueChange = { onDigits(it.filter(Char::isDigit).take(8)) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("dd/mm/aaaa") },
        singleLine = true,
        isError = invalid && digits.length == 8,
        supportingText = if (invalid && digits.length == 8) ({ Text("Data que não existe") }) else null,
        visualTransformation = DateMask,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
