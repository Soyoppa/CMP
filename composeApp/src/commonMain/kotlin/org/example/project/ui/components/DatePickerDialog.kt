package org.example.project.ui.components

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
            selectedDay = date.dayOfMonth
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
                    TextButton(onClick = { selectedYear-- }) {
                        Text("◀")
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = { selectedYear++ }) {
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
                    TextButton(onClick = { 
                        if (selectedMonth > 1) selectedMonth-- 
                        else {
                            selectedMonth = 12
                            selectedYear--
                        }
                    }) {
                        Text("◀")
                    }
                    Text(
                        text = getMonthName(selectedMonth),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { 
                        if (selectedMonth < 12) selectedMonth++ 
                        else {
                            selectedMonth = 1
                            selectedYear++
                        }
                    }) {
                        Text("▶")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Day grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val daysInMonth = getDaysInMonth(selectedYear, selectedMonth)
                    items((1..daysInMonth).toList()) { day ->
                        Surface(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { selectedDay = day },
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
            TextButton(
                onClick = {
                    val formattedDate = "$selectedMonth/$selectedDay/$selectedYear"
                    onDateSelected(formattedDate)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
