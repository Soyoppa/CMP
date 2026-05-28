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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.auth.AuthState
import org.example.project.auth.Session
import org.example.project.config.SchemaFeatures
import org.example.project.data.AiRepository
import org.example.project.data.ai.AiPrefs
import org.example.project.data.ai.AiProviderId
import org.example.project.data.ai.AiProviderMode
import org.example.project.data.ai.AiUsageTracker
import org.example.project.data.ai.ProviderUsage
import org.example.project.data.ai.SessionUsage
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.util.DateUtils
import org.example.project.util.FormatUtils
import kotlin.time.TimeSource

private val CardCorner = RoundedCornerShape(20.dp)
private val FieldCorner = RoundedCornerShape(14.dp)

private enum class ResultKind { IDLE, SUCCESS, WARNING, ERROR }

private data class TestResult(val kind: ResultKind, val message: String) {
    companion object {
        val Idle = TestResult(ResultKind.IDLE, "Not tested yet")
    }
}

@Composable
fun TestConnectionScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {},
    accountEmail: String? = null,
    onSignOut: () -> Unit = {},
) {
    val repository = remember { TransactionRepository() }
    val aiRepository = remember { AiRepository() }
    val coroutineScope = rememberCoroutineScope()
    var result by remember { mutableStateOf(TestResult.Idle) }
    var isLoading by remember { mutableStateOf(false) }
    val usage by AiUsageTracker.state.collectAsState()
    val showPerMessageTokens by AiPrefs.showPerMessageTokens.collectAsState()
    val providerMode by AiPrefs.providerMode.collectAsState()
    var switchStatus by remember { mutableStateOf<AiSwitchStatus>(AiSwitchStatus.Idle) }
    val authState by Session.state.collectAsState()
    val isGuest = (authState as? AuthState.Authenticated)?.user?.isGuest == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        AccountRow(
            email = accountEmail,
            isGuest = isGuest,
            onSignOut = onSignOut,
        )

        DarkModeToggleRow(
            isDarkTheme = isDarkTheme,
            onDarkThemeChange = onDarkThemeChange,
        )

        if (isGuest) {
            Text(
                text = "Guest mode — diagnostics are read-only. Sign in to enable them.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB300),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TestActionButton(
                label = "Test Read",
                isLoading = isLoading,
                enabled = !isGuest,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        result = try {
                            val recent = repository.getRecent(3)
                            if (recent.isEmpty()) {
                                TestResult(ResultKind.WARNING, "Read succeeded, but no transactions were found.")
                            } else {
                                val lines = recent.joinToString("\n") { entry ->
                                    val sign = if (entry.isInflow) "+" else "-"
                                    "• ${entry.description} — ${sign}PHP ${FormatUtils.formatPeso(entry.amount)}"
                                }
                                TestResult(ResultKind.SUCCESS, "Last ${recent.size} transactions:\n$lines")
                            }
                        } catch (e: Exception) {
                            TestResult(ResultKind.ERROR, "Read failed: ${e.message}")
                        }
                        isLoading = false
                    }
                },
            )
            TestActionButton(
                label = "Test Write",
                isLoading = isLoading,
                enabled = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        result = try {
                            val testTransaction = Transaction(
                                date = DateUtils.getCurrentDateFormatted(),
                                description = "Test Transaction",
                                outflow = 100.0,
                                category = "Test",
                                modeOfPayment = "Test",
                                isPaid = false,
                            )
                            if (repository.addTransaction(testTransaction)) {
                                TestResult(
                                    ResultKind.SUCCESS,
                                    "Write succeeded. Check your Google Sheet to confirm the row.",
                                )
                            } else {
                                TestResult(
                                    ResultKind.WARNING,
                                    "Write response was unclear. Apps Script sometimes commits even when parsing fails — check your sheet.",
                                )
                            }
                        } catch (e: Exception) {
                            TestResult(
                                ResultKind.ERROR,
                                "Write failed: ${e.message}\nThe row may still have been added — verify in your sheet.",
                            )
                        }
                        isLoading = false
                    }
                },
            )
        }

        ResultCard(result = result)

        AiProviderCard(
            selectedMode = providerMode,
            geminiAvailable = aiRepository.isGeminiAvailable,
            interactive = !isGuest,
            status = switchStatus,
            onSelect = { mode ->
                AiPrefs.setProviderMode(mode)
                switchStatus = AiSwitchStatus.Checking(mode)
                coroutineScope.launch {
                    val mark = TimeSource.Monotonic.markNow()
                    val reply = aiRepository.chat("Reply with the single word: OK.")
                    val ms = mark.elapsedNow().inWholeMilliseconds
                    switchStatus = if (reply.isError) {
                        AiSwitchStatus.Failed(reply.text)
                    } else {
                        AiSwitchStatus.Ok(reply.provider, reply.model, ms)
                    }
                }
            },
        )
        PerMessageTokensToggleRow(
            checked = showPerMessageTokens,
            enabled = !isGuest,
            onCheckedChange = AiPrefs::setShowPerMessageTokens,
        )
        AiUsageCard(usage = usage, canReset = !isGuest, onReset = AiUsageTracker::reset)
    }
}

/** Account status + sign-out (or "Exit guest"). */
@Composable
private fun AccountRow(email: String?, isGuest: Boolean, onSignOut: () -> Unit) {
    val bounce = rememberPressBounce(pressedScale = 0.95f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isGuest) "Guest mode" else "Signed in",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = email ?: if (isGuest) "Browsing without an account" else "Not signed in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = bounce.interactionSource,
                    indication = null,
                    onClick = onSignOut,
                )
                .then(bounce.modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (isGuest) "Exit guest" else "Sign out",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Session AI usage ledger — requests, tokens, prompt/response split, and per-provider breakdown. */
@Composable
private fun AiUsageCard(usage: SessionUsage, onReset: () -> Unit, canReset: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)),
                ),
                shape = CardCorner,
            )
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
            ProviderSplitRow(
                color = Color(0xFFFFB300),
                label = "Ollama",
                usage = usage.ollama,
                suffix = " (fallback)",
            )
        }

        Text(
            text = "Session totals only — for daily quota & billing, see the Google Cloud console.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                .clip(RoundedCornerShape(50))
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
                .clip(RoundedCornerShape(50))
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
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onReset,
            )
            .then(bounce.modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
            .clip(FieldCorner)
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
                checkedThumbColor = Color.White,
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

/** Outcome of a manual provider switch + verification probe. */
private sealed interface AiSwitchStatus {
    data object Idle : AiSwitchStatus
    data class Checking(val mode: AiProviderMode) : AiSwitchStatus
    data class Ok(val provider: AiProviderId, val model: String, val ms: Long) : AiSwitchStatus
    data class Failed(val message: String) : AiSwitchStatus
}

/** Manual AI provider selector + live "did it switch?" verification. */
@Composable
private fun AiProviderCard(
    selectedMode: AiProviderMode,
    geminiAvailable: Boolean,
    status: AiSwitchStatus,
    onSelect: (AiProviderMode) -> Unit,
    interactive: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)),
                ),
                shape = CardCorner,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "AI Provider",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Pick which assistant answers. Auto uses Firebase AI, then Ollama if it fails.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiProviderMode.entries.forEach { mode ->
                val enabled = interactive && (mode != AiProviderMode.GEMINI || geminiAvailable)
                ProviderModeChip(
                    label = mode.label,
                    selected = selectedMode == mode,
                    enabled = enabled,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!geminiAvailable) {
            Text(
                text = "Firebase AI is unavailable on this build/config.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFB300),
            )
        }
        SwitchStatusRow(status = status)
    }
}

@Composable
private fun ProviderModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipBg",
    )
    val borderColor = if (selected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    Box(
        modifier = modifier
            .clip(FieldCorner)
            .background(bg)
            .border(1.dp, borderColor, FieldCorner)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

@Composable
private fun SwitchStatusRow(status: AiSwitchStatus) {
    when (status) {
        AiSwitchStatus.Idle -> Text(
            text = "Tap a provider to switch and verify it responds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is AiSwitchStatus.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Checking ${status.mode.label}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is AiSwitchStatus.Ok -> SwitchStatusLine(
            color = Color(0xFF00C853),
            text = "Switched to ${status.provider.displayName} · ${status.model.removePrefix("gemini-")} · ${status.ms} ms",
        )

        is AiSwitchStatus.Failed -> SwitchStatusLine(
            color = Color(0xFFE53935),
            text = status.message,
        )
    }
}

@Composable
private fun SwitchStatusLine(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
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
            .clip(FieldCorner)
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
                checkedThumbColor = Color.White,
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
        shape = RoundedCornerShape(20.dp),
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
private fun ResultCard(result: TestResult) {
    val accent = when (result.kind) {
        ResultKind.IDLE -> MaterialTheme.colorScheme.outlineVariant
        ResultKind.SUCCESS -> Color(0xFF00C853)
        ResultKind.WARNING -> Color(0xFFFFB300)
        ResultKind.ERROR -> Color(0xFFE53935)
    }
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "resultAccent",
    )
    val glyph = when (result.kind) {
        ResultKind.IDLE -> "…"
        ResultKind.SUCCESS -> "✓"
        ResultKind.WARNING -> "!"
        ResultKind.ERROR -> "×"
    }
    val title = when (result.kind) {
        ResultKind.IDLE -> "Awaiting test"
        ResultKind.SUCCESS -> "Success"
        ResultKind.WARNING -> "Check your sheet"
        ResultKind.ERROR -> "Failed"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        animatedAccent.copy(alpha = 0.55f),
                        animatedAccent.copy(alpha = 0.18f),
                    ),
                ),
                shape = CardCorner,
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
                    .clip(RoundedCornerShape(50))
                    .background(animatedAccent.copy(alpha = 0.16f))
                    .border(1.dp, animatedAccent.copy(alpha = 0.45f), RoundedCornerShape(50)),
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