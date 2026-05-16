package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.util.DateUtils

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
) {
    val repository = remember { TransactionRepository() }
    val coroutineScope = rememberCoroutineScope()
    var result by remember { mutableStateOf(TestResult.Idle) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Connection Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        DarkModeToggleRow(
            isDarkTheme = isDarkTheme,
            onDarkThemeChange = onDarkThemeChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TestActionButton(
                label = "Test Read",
                isLoading = isLoading,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        result = try {
                            val transactions = repository.getFromDataDump()
                            val preview = transactions.take(3).joinToString("\n") {
                                "${it.date}: ${it.description} — ${it.category}"
                            }
                            TestResult(
                                ResultKind.SUCCESS,
                                "Found ${transactions.size} transactions.\n$preview",
                            )
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

        InstructionsCard()
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
) {
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .height(56.dp)
            .then(bounce.modifier),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
        interactionSource = bounce.interactionSource,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
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

@Composable
private fun InstructionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Setup checklist",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(
            "Use the spreadsheet ID from the edit URL (not the published one).",
            "Share the sheet as “Anyone with link can EDIT”.",
            "Columns: Date, Description, Inflow, Outflow, Category, Mode of Payment, Paid.",
            "Sheet structure should match the documented format.",
        ).forEachIndexed { index, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
