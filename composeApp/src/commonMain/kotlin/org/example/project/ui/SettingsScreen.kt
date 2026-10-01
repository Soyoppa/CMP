package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.config.FeatureFlagStore
import org.example.project.config.LedgerProfile
import org.example.project.data.ai.AiPrefs
import org.example.project.data.ai.AiUsageTracker
import org.example.project.data.ai.ProviderUsage
import org.example.project.data.ai.SessionUsage
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.IncomeGreen
import org.example.project.viewmodel.DiagnosticKind
import org.example.project.viewmodel.DiagnosticResult
import org.example.project.viewmodel.SettingsEvent
import org.example.project.viewmodel.AuthMode
import org.example.project.viewmodel.SettingsViewModel
import org.example.project.viewmodel.createSettingsViewModel


@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {},
    /** The AI assistant is on for this session (account + a platform with Gemini). */
    aiAvailable: Boolean = false,
    /** Opens sign-in / sign-up from no-account mode. */
    onOpenAccount: (AuthMode) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenPaymentModes: () -> Unit = {},
    /** null on ledgers without a Paid column, which hides the row entirely. */
    onOpenPaymentStatus: (() -> Unit)? = null,
    onOpenTransactions: () -> Unit = {},
    viewModel: SettingsViewModel = createSettingsViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val usage by AiUsageTracker.state.collectAsState()
    val showPerMessageTokens by AiPrefs.showPerMessageTokens.collectAsState()
    val signupEnabled = FeatureFlagStore.state.collectAsState().value.signupEnabled
    val profile = remember { LedgerProfile.current() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        uiState.error?.let { error ->
            ErrorBanner(message = error, onDismiss = { viewModel.onEvent(SettingsEvent.ErrorShown) })
        }

        if (uiState.hasAccount) {
            SettingsSection(title = "Account") {
                AccountRow(email = uiState.email, onSignOut = { viewModel.onEvent(SettingsEvent.SignOutClicked) })
            }
        } else {
            DeviceModeCard(
                signupEnabled = signupEnabled,
                onCreateAccount = { onOpenAccount(AuthMode.SIGN_UP) },
                onSignIn = { onOpenAccount(AuthMode.SIGN_IN) },
            )
        }

        SettingsSection(title = "Your setup") {
            NavigationRow(
                title = profile.categoryListTitle,
                subtitle = when {
                    uiState.expenseCategoryCount + uiState.incomeCategoryCount == 0 -> "None yet — add the ones you use"
                    profile.showIncomeOption ->
                        "${uiState.expenseCategoryCount} for expenses · ${uiState.incomeCategoryCount} for income"
                    else -> countLabel(uiState.expenseCategoryCount, "option")
                },
                onClick = onOpenCategories,
            )
            NavigationRow(
                title = "Payment modes",
                subtitle = if (uiState.paymentModeCount == 0) "None yet — cash, cards, e-wallets…"
                else countLabel(uiState.paymentModeCount, "payment mode"),
                onClick = onOpenPaymentModes,
            )
            if (profile.summaryAvailable) {
                NavigationRow(
                    title = "Budgets",
                    subtitle = "Set what you can spend each cut-off",
                    onClick = onOpenBudgets,
                )
            }
        }

        SettingsSection(title = "Ledger") {
            NavigationRow(
                title = "Transactions",
                subtitle = "Review or delete what you've logged",
                onClick = onOpenTransactions,
            )
            if (onOpenPaymentStatus != null) {
                NavigationRow(
                    title = "Paid & unpaid",
                    subtitle = "See what's settled and what's outstanding, per payment mode",
                    onClick = onOpenPaymentStatus,
                )
            }
        }

        SettingsSection(title = "Appearance") {
            DarkModeToggleRow(
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
            )
        }

        if (aiAvailable && profile.summaryAvailable) {
            SettingsSection(title = "AI Assistant") {
                PerMessageTokensToggleRow(
                    checked = showPerMessageTokens,
                    onCheckedChange = AiPrefs::setShowPerMessageTokens,
                )
                AiUsageCard(usage = usage, onReset = AiUsageTracker::reset)
                AiTokenInfoCard()
            }
        }

        // Only accounts the project owner granted the household sheet ever see this.
        if (uiState.sheetsGranted) {
            SettingsSection(title = "Developer") {
                SwitchRow(
                    title = "Household sheet ledger",
                    subtitle = if (uiState.sheetsEnabled) "Transactions read from and save to the household Google Sheet."
                    else "Transactions use this account's cloud ledger.",
                    checked = uiState.sheetsEnabled,
                    enabled = !uiState.isSwitchingSheets,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SheetsToggled(it)) },
                )
                TestActionButton(
                    label = "Test read",
                    isLoading = uiState.isTestingRead,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.onEvent(SettingsEvent.TestReadClicked) },
                )
                ResultCard(result = uiState.readResult)
            }
        }

        SettingsSection(title = "Data") {
            if (uiState.hasAccount) {
                NavigationRow(
                    title = "Delete account",
                    subtitle = "Permanently delete your account and all of its data",
                    onClick = onDeleteAccount,
                    destructive = true,
                )
            } else {
                NavigationRow(
                    title = "Erase data on this phone",
                    subtitle = "Delete every transaction, list and budget stored here",
                    onClick = { viewModel.onEvent(SettingsEvent.EraseClicked) },
                    destructive = true,
                )
            }
        }
    }

    if (uiState.confirmErase) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isErasing) viewModel.onEvent(SettingsEvent.EraseDismissed) },
            shape = AppShapes.card,
            title = { Text("Erase everything?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "This deletes every transaction, list and budget on this phone. There's no backup, so it can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onEvent(SettingsEvent.EraseConfirmed) },
                    enabled = !uiState.isErasing,
                ) {
                    if (uiState.isErasing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Erase", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.EraseDismissed) }, enabled = !uiState.isErasing) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun countLabel(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

/** No-account mode: say plainly where the data lives, and offer the way to back it up. */
@Composable
private fun DeviceModeCard(signupEnabled: Boolean, onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Saved on this phone only",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Nothing is backed up yet. With an account your data is kept safe in the cloud and you can use it on the web too — everything you've logged comes along.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (signupEnabled) {
                PillButton(label = "Create account", primary = true, onClick = onCreateAccount, modifier = Modifier.weight(1f))
            }
            PillButton(label = "Sign in", primary = !signupEnabled, onClick = onSignIn, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.onErrorContainer) }
    }
}

/**
 * A titled group of settings rows. The header label + tighter intra-group spacing (vs. the
 * 24dp between sections) is what lets the eye chunk the screen by topic.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/** Account email + sign-out. */
@Composable
private fun AccountRow(email: String?, onSignOut: () -> Unit) {
    val bounce = rememberPressBounce(pressedScale = 0.95f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = email ?: "Signed in",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Synced across your devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = bounce.interactionSource,
                    indication = null,
                    onClick = onSignOut,
                )
                .then(bounce.modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tappable settings row that navigates elsewhere (title + subtitle + chevron). */
@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit, destructive: Boolean = false) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A labelled switch row (title + subtitle + Switch), the whole row toggles. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = bounce.interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

/** Session AI usage ledger — requests, tokens, prompt/response split, and per-provider breakdown. */
@Composable
private fun AiUsageCard(usage: SessionUsage, onReset: () -> Unit, canReset: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "AI Usage · this session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!usage.isEmpty && canReset) ResetChip(onReset = onReset)
        }

        if (usage.isEmpty) {
            Text(
                text = "No requests yet this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatColumn(
                    value = animatedCount(usage.totalRequests).toString(),
                    label = "requests",
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = groupThousands(animatedCount(usage.totalTokens)),
                    label = "total tokens",
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = usage.lastModel?.removePrefix("gemini-") ?: "—",
                    label = "active model",
                    modifier = Modifier.weight(1f),
                )
            }

            TokenSplitBar(
                promptTokens = usage.totalPromptTokens,
                responseTokens = usage.totalResponseTokens,
                accent = accent,
            )

            Text(
                text = "Provider split",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ProviderSplitRow(
                color = accent,
                label = "Gemini",
                usage = usage.gemini,
            )
        }

        Text(
            text = "Session totals only — for daily quota & billing, see the Google Cloud console.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Quiet explainer for the AI section: what a Gemini token is, what spends them, and the
 * (often surprising) fact that voice category detection also calls Gemini. Informational only —
 * no actions, low visual weight, so it sits below the live usage numbers without competing.
 */
@Composable
private fun AiTokenInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "i",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Understanding Gemini tokens",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        TokenInfoLine(
            heading = "What a token is",
            body = "Gemini measures text in tokens — roughly 4 characters (about ¾ of a word). Longer messages cost more tokens.",
        )
        TokenInfoLine(
            heading = "Prompt + reply both count",
            body = "Every request spends prompt tokens (what you and the app send) plus reply tokens (Gemini's answer). The split bar above shows the balance.",
        )
        TokenInfoLine(
            heading = "Voice entry uses Gemini too",
            body = "When voice can't recognise a category, the app asks Gemini to choose one — a small extra prompt and reply per unclear transaction.",
        )

        Text(
            text = "Firebase AI Logic keeps usage on the free Gemini Developer tier. For hard quotas and billing, open the Google AI Studio / Cloud console.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TokenInfoLine(heading: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Thin rounded bar showing prompt vs response token share. */
@Composable
private fun TokenSplitBar(promptTokens: Int, responseTokens: Int, accent: Color) {
    val total = (promptTokens + responseTokens).coerceAtLeast(1)
    val promptFraction = promptTokens.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(AppShapes.pill)
                .background(accent.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(promptFraction)
                    .height(6.dp)
                    .background(accent),
            )
        }
        Text(
            text = "prompt ${groupThousands(promptTokens)} · reply ${groupThousands(responseTokens)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderSplitRow(
    color: Color,
    label: String,
    usage: ProviderUsage,
    suffix: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(AppShapes.pill)
                .background(color),
        )
        Text(
            text = "$label$suffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${usage.requests} reqs · ${groupThousands(usage.totalTokens)} tok",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResetChip(onReset: () -> Unit) {
    val bounce = rememberPressBounce(pressedScale = 0.92f)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onReset,
            )
            .then(bounce.modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Reset",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PerMessageTokensToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = bounce.interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Show per-message tokens",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Adds a faint model · token count under each AI reply in chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

/** Springy roll-up for a counter value. */
@Composable
private fun animatedCount(target: Int): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "usageCount",
    )
    return value
}

/** 11480 → "11,480". */
private fun groupThousands(n: Int): String {
    val s = n.toString()
    val sb = StringBuilder()
    val len = s.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return sb.toString()
}

@Composable
private fun DarkModeToggleRow(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = isDarkTheme,
                interactionSource = bounce.interactionSource,
                indication = null,
                onValueChange = onDarkThemeChange,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dark mode",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (isDarkTheme) "Dark surfaces, low-light friendly." else "Light surfaces follow your system default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isDarkTheme,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
private fun TestActionButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(56.dp)
            .then(bounce.modifier),
        shape = AppShapes.card,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        interactionSource = bounce.interactionSource,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text("Testing…", fontWeight = FontWeight.SemiBold)
        } else {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ResultCard(result: DiagnosticResult) {
    val accent = when (result.kind) {
        DiagnosticKind.IDLE -> MaterialTheme.colorScheme.outlineVariant
        DiagnosticKind.SUCCESS -> IncomeGreen
        DiagnosticKind.WARNING -> MaterialTheme.colorScheme.tertiary
        DiagnosticKind.ERROR -> MaterialTheme.colorScheme.error
    }
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "resultAccent",
    )
    val glyph = when (result.kind) {
        DiagnosticKind.IDLE -> "…"
        DiagnosticKind.SUCCESS -> "✓"
        DiagnosticKind.WARNING -> "!"
        DiagnosticKind.ERROR -> "×"
    }
    val title = when (result.kind) {
        DiagnosticKind.IDLE -> "Awaiting test"
        DiagnosticKind.SUCCESS -> "Success"
        DiagnosticKind.WARNING -> "Nothing to show"
        DiagnosticKind.ERROR -> "Failed"
    }


    val borderBrush = remember(animatedAccent) {
        Brush.verticalGradient(
            listOf(
                animatedAccent.copy(alpha = 0.55f),
                animatedAccent.copy(alpha = 0.18f),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = AppShapes.card,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(AppShapes.pill)
                    .background(animatedAccent.copy(alpha = 0.16f))
                    .border(1.dp, animatedAccent.copy(alpha = 0.45f), AppShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    color = animatedAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}