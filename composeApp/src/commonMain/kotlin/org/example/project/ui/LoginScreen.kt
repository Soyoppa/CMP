package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.app_logo
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.viewmodel.AuthViewModel
import org.jetbrains.compose.resources.painterResource

private val CardCorner = RoundedCornerShape(24.dp)
private val FieldCorner = RoundedCornerShape(14.dp)

/**
 * Sign-in / sign-up gate with a guest path. Stateless — observes [AuthViewModel]; success flips
 * Session and the App gate swaps this screen out.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val isSignUp = state.mode == AuthViewModel.Mode.SIGN_UP
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = if (isSignUp) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Sign in to your finance tracker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            AuthField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                enabled = !state.isSubmitting,
            )
            AuthField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password (min 6 characters)",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
                enabled = !state.isSubmitting,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        enabled = !state.isSubmitting,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PrimaryButton(
                label = if (isSignUp) "Create account" else "Sign in",
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                onClick = viewModel::submit,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "No account yet?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isSignUp) "Sign in" else "Sign up",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !state.isSubmitting) { viewModel.toggleMode() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            DividerOr()

            GuestButton(
                enabled = !state.isSubmitting,
                onClick = viewModel::continueAsGuest,
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    enabled: Boolean,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() }),
        shape = FieldCorner,
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * Eye affordance for the password field. Open eye = password visible (tap to hide);
 * closed eye with lashes = password hidden (tap to reveal). Drawn as a vector so it needs no asset.
 */
@Composable
private fun PasswordVisibilityToggle(
    visible: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val description = if (visible) "Hide password" else "Show password"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val strokeWidthPx = 1.8.dp.toPx()
            val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            if (visible) {
                // Open eye: almond outline (top + bottom lid) with a pupil.
                val eye = Path().apply {
                    moveTo(w * 0.08f, cy)
                    quadraticBezierTo(w * 0.5f, h * 0.10f, w * 0.92f, cy)
                    quadraticBezierTo(w * 0.5f, h * 0.90f, w * 0.08f, cy)
                    close()
                }
                drawPath(eye, color = tint, style = stroke)
                drawCircle(color = tint, radius = w * 0.15f, center = Offset(w * 0.5f, cy), style = stroke)
            } else {
                // Closed eye: a single downward-bowing lid with three short lashes.
                val lid = Path().apply {
                    moveTo(w * 0.10f, h * 0.42f)
                    quadraticBezierTo(w * 0.5f, h * 0.80f, w * 0.90f, h * 0.42f)
                }
                drawPath(lid, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.28f, h * 0.60f), Offset(w * 0.22f, h * 0.74f), strokeWidthPx, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.82f), strokeWidthPx, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.60f), Offset(w * 0.78f, h * 0.74f), strokeWidthPx, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    val primary = MaterialTheme.colorScheme.primary
    val bg = if (enabled) primary else primary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(FieldCorner)
            .background(bg)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(bounce.modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun DividerOr() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.surfaceContainer))
        Text("or", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.surfaceContainer))
    }
}

@Composable
private fun GuestButton(enabled: Boolean, onClick: () -> Unit) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(FieldCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(bounce.modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Continue as guest",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
