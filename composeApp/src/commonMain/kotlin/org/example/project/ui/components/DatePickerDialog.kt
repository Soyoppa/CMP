package org.example.project.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    currentDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableStateOf(2026) }
    var selectedMonth by remember { mutableStateOf(2) }
    var selectedDay by remember { mutableStateOf(28) }
    
    // Parse current date if valid
    LaunchedEffect(currentDate) {
        DateUtils.parseDate(currentDate)?.let { date ->
            selectedYear = date.year
            selectedMonth = date.month.ordinal + 1
            selectedDay = date.day
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Year selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val yearPrevBounce = rememberPressBounce(pressedScale = 0.85f)
                    TextButton(
                        onClick = { if (selectedYear > 2000) selectedYear-- },
                        modifier = yearPrevBounce.modifier,
                        interactionSource = yearPrevBounce.interactionSource,
                    ) {
                        Text("◀")
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    val yearNextBounce = rememberPressBounce(pressedScale = 0.85f)
                    TextButton(
                        onClick = { if (selectedYear < 2100) selectedYear++ },
                        modifier = yearNextBounce.modifier,
                        interactionSource = yearNextBounce.interactionSource,
                    ) {
                        Text("▶")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Month selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val monthPrevBounce = rememberPressBounce(pressedScale = 0.85f)
                    TextButton(
                        onClick = {
                            if (selectedMonth > 1) selectedMonth--
                            else {
                                selectedMonth = 12
                                selectedYear--
                            }
                        },
                        modifier = monthPrevBounce.modifier,
                        interactionSource = monthPrevBounce.interactionSource,
                    ) {
                        Text("◀")
                    }
                    Text(
                        text = getMonthName(selectedMonth),
                        style = MaterialTheme.typography.titleLarge
                    )
                    val monthNextBounce = rememberPressBounce(pressedScale = 0.85f)
                    TextButton(
                        onClick = {
                            if (selectedMonth < 12) selectedMonth++
                            else {
                                selectedMonth = 1
                                selectedYear++
                            }
                        },
                        modifier = monthNextBounce.modifier,
                        interactionSource = monthNextBounce.interactionSource,
                    ) {
                        Text("▶")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Weekday header — Monday-first, aligned with the grid columns below.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Day grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(248.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val daysInMonth = getDaysInMonth(selectedYear, selectedMonth)
                    // DayOfWeek.ordinal is 0 for Monday; +1 mod 7 shifts to a Sunday-first week
                    // (Sun=0..Sat=6), giving the count of blank leading cells before day 1.
                    val leadingBlanks = (LocalDate(selectedYear, selectedMonth, 1).dayOfWeek.ordinal + 1) % 7
                    val cells: List<Int?> = List(leadingBlanks) { null } + (1..daysInMonth).toList()
                    items(cells) { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.aspectRatio(1f))
                            return@items
                        }
                        val dayBounce = rememberPressBounce(pressedScale = 0.86f)
                        Surface(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .then(dayBounce.modifier)
                                .clickable(
                                    interactionSource = dayBounce.interactionSource,
                                    indication = LocalIndication.current,
                                ) { selectedDay = day },
                            color = if (day == selectedDay)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = day.toString(),
                                    color = if (day == selectedDay)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val okBounce = rememberPressBounce(pressedScale = 0.9f)
            TextButton(
                onClick = {
                    val formattedDate = "$selectedMonth/$selectedDay/$selectedYear"
                    onDateSelected(formattedDate)
                    onDismiss()
                },
                modifier = okBounce.modifier,
                interactionSource = okBounce.interactionSource,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            val cancelBounce = rememberPressBounce(pressedScale = 0.9f)
            TextButton(
                onClick = onDismiss,
                modifier = cancelBounce.modifier,
                interactionSource = cancelBounce.interactionSource,
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun getMonthName(month: Int): String {
    return when (month) {
        1 -> "January"
        2 -> "February"
        3 -> "March"
        4 -> "April"
        5 -> "May"
        6 -> "June"
        7 -> "July"
        8 -> "August"
        9 -> "September"
        10 -> "October"
        11 -> "November"
        12 -> "December"
        else -> "Unknown"
    }
}

private fun getDaysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
