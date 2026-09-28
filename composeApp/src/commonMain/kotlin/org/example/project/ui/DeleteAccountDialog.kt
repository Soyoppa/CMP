package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.ui.theme.AppShapes
import org.example.project.viewmodel.DeleteAccountEvent
import org.example.project.viewmodel.DeleteAccountViewModel

/**
 * Confirms permanent account deletion (required by the App Store and Google Play for apps that
 * let users create accounts). Email accounts re-enter their password; guests just confirm.
 */
@Composable
fun DeleteAccountDialog(
    isGuest: Boolean,
    onDismiss: () -> Unit,
    viewModel: DeleteAccountViewModel = viewModel { DeleteAccountViewModel() },
) {
    val state by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!state.isDeleting) onDismiss() },
        shape = AppShapes.card,
        title = { Text("Delete account?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isGuest) {
                        "This ends your guest session and removes it permanently."
                    } else {
                        "This permanently deletes your account, transactions, budgets and saved lists. It can't be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!isGuest) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onEvent(DeleteAccountEvent.PasswordChanged(it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !state.isDeleting,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = AppShapes.field,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.error?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.onEvent(DeleteAccountEvent.ConfirmClicked) },
                enabled = !state.isDeleting && (isGuest || state.password.isNotEmpty()),
            ) {
                if (state.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isDeleting) { Text("Cancel") }
        },
    )
}
