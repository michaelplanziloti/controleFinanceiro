package com.controlefinanceiro.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow

/** Free text about an entry, purchase or spending. */
@Composable
fun NoteField(note: String, onChange: (String) -> Unit) {
    OutlinedTextField(note, onChange, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") }, minLines = 1, maxLines = 4)
}

/** The note under a row's details, when there is one. */
@Composable
fun NoteLine(note: String) {
    if (note.isBlank()) return
    Text(
        note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}
