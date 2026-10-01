package org.example.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.app_logo
import org.example.project.config.FeatureFlagStore
import org.example.project.ui.components.AppSheet
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.theme.AppShapes
import org.example.project.viewmodel.AuthEvent
import org.example.project.viewmodel.AuthMode
import org.example.project.viewmodel.AuthUiState
import org.example.project.viewmodel.AuthViewModel
import org.example.project.viewmodel.createAuthViewModel
import org.jetbrains.compose.resources.painterResource

/**
 * The signed-out gate. On a phone it opens on a choice — start right away without an account, or
 * sign in to sync — and the email form is one tap away. The web always needs an account, so it
 * goes straight to the form. Stateless: renders [AuthViewModel]; success flips the session and
 * the App gate swaps this screen out.
 */
@Composable
fun WelcomeScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val signupEnabled = FeatureFlagStore.state.collectAsState().value.signupEnabled

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.showChoice,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            // Centred when it fits; scrolls (e.g. with the keyboard up) when it doesn't.
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            label = "welcomeStep",
        ) { showChoice ->
            if (showChoice) {
                WelcomeChoice(state = state, signupEnabled = signupEnabled, onEvent = viewModel::onEvent)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.deviceModeAvailable) {
                        TextLink(label = "‹ Back", enabled = !state.isSubmitting) {
                            viewModel.onEvent(AuthEvent.BackToChoiceClicked)
                        }
                    }
                    AuthHeader(
                        title = if (state.mode == AuthMode.SIGN_UP) "Create your account" else "Welcome back",
                        subtitle = when {
                            state.mode == AuthMode.SIGN_UP -> "Your transactions, lists and budgets sync between the web and your phone."
                            state.deviceModeAvailable -> "Sign in to pick up where you left off on any device."
                            else -> "Sign in to see your finances on any device."
                        },
                    )
                    AuthForm(state = state, signupEnabled = signupEnabled, onEvent = viewModel::onEvent)
                }
            }
        }
    }
}

/** Phone-only first step: use the app right away, or sign in to sync. */
@Composable
private fun WelcomeChoice(state: AuthUiState, signupEnabled: Boolean, onEvent: (AuthEvent) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuthHeader(
            title = "Your money, one cut-off at a time",
            subtitle = "Log what you spend, budget each payday, and always know what's left.",
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = "Start without an account",
            enabled = !state.isSubmitting,
            loading = state.isSubmitting,
            onClick = { onEvent(AuthEvent.UseWithoutAccountClicked) },
        )
        Text(
            text = "Everything stays on this phone. Add an account any time to back it up and use it on the web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        state.error?.let { ErrorText(it) }
        DividerOr()
        SecondaryButton(
            label = "Sign in",
            enabled = !state.isSubmitting,
            onClick = { onEvent(AuthEvent.FormRequested(AuthMode.SIGN_IN)) },
        )
        if (signupEnabled) {
            TextLink(label = "New here? Create an account", enabled = !state.isSubmitting) {
                onEvent(AuthEvent.FormRequested(AuthMode.SIGN_UP))
            }
        }
    }
}

/**
 * "Back up & sync": adding an account from inside the app (no-account mode). Whatever is on the
 * phone moves into the account once it's signed in.
 */
@Composable
fun AccountSheet(
    initialMode: AuthMode,
    onClose: () -> Unit,
    viewModel: AuthViewModel = createAuthViewModel(startWithForm = true, initialMode = initialMode),
) {
    val state by viewModel.uiState.collectAsState()
    val signupEnabled = FeatureFlagStore.state.collectAsState().value.signupEnabled
    AppSheet(
        title = if (state.mode == AuthMode.SIGN_UP) "Create an account" else "Sign in",
        subtitle = "Back up your data and use it on the web",
        onClose = onClose,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.hasDeviceData) {
                Text(
                    text = "Everything you've logged on this phone — transactions, lists and budgets — moves into the account.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.field)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            AuthForm(state = state, signupEnabled = signupEnabled, onEvent = viewModel::onEvent)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(AppShapes.pill)
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
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Email + password with a sign-in / sign-up switch. */
@Composable
private fun AuthForm(state: AuthUiState, signupEnabled: Boolean, onEvent: (AuthEvent) -> Unit) {
    val isSignUp = state.mode == AuthMode.SIGN_UP
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuthField(
            value = state.email,
            onValueChange = { onEvent(AuthEvent.EmailChanged(it)) },
            label = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !state.isSubmitting,
        )
        AuthField(
            value = state.password,
            onValueChange = { onEvent(AuthEvent.PasswordChanged(it)) },
            label = "Password (min 6 characters)",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { onEvent(AuthEvent.SubmitClicked) },
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

        state.error?.let { ErrorText(it) }

        PrimaryButton(
            label = if (isSignUp) "Create account" else "Sign in",
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            onClick = { onEvent(AuthEvent.SubmitClicked) },
        )

        if (signupEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "No account yet?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextLink(label = if (isSignUp) "Sign in" else "Sign up", enabled = !state.isSubmitting) {
                    onEvent(AuthEvent.ModeToggled)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLink(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(AppShapes.pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
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
        shape = AppShapes.field,
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
            .size(48.dp)
            .clip(AppShapes.pill)
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
    val primary = MaterialTheme.colorScheme.primary
    BounceSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = AppShapes.field,
        color = if (enabled) primary else primary.copy(alpha = 0.4f),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun SecondaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    BounceSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = AppShapes.field,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
