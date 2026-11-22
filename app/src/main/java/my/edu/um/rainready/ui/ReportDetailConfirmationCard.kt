package my.edu.um.rainready.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import my.edu.um.rainready.data.ReportItem
import my.edu.um.rainready.data.ReportRepository

/**
 * FR3.2 Safe Report Confirmation Card/Sheet.
 * - Shows live confirmCount (e.g., 2 / 3 confirmations).
 * - Disables the button while confirming or if this user already confirmed.
 * - Uses ReportRepository.confirmReport() Firestore Transaction so simultaneous
 *   taps never lose a count and 1 account = 1 vote.
 */
@Composable
fun ReportDetailConfirmationCard(
    report: ReportItem,
    repository: ReportRepository = remember { ReportRepository() },
    onUpdated: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var confirmCount by remember(report.reportId) { mutableIntStateOf(report.confirmCount) }
    var status by remember(report.reportId) { mutableStateOf(report.status) }
    var alreadyConfirmed by remember(report.reportId) { mutableStateOf(false) }
    var isConfirming by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(report.reportId) {
        alreadyConfirmed = repository.hasUserConfirmed(report.reportId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${report.zoneId} • ${report.category.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.titleMedium
            )
            if (report.note.isNotBlank()) {
                Text(text = report.note, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Status: $status ($confirmCount / 3 confirmations)",
                style = MaterialTheme.typography.labelLarge
            )

            feedbackMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            isConfirming = true
                            feedbackMessage = null
                            val res = repository.confirmReport(report.reportId)
                            isConfirming = false
                            res.onSuccess { outcome ->
                                confirmCount = outcome.newConfirmCount
                                status = outcome.newStatus
                                alreadyConfirmed = true
                                feedbackMessage = if (outcome.confirmedByStaff) {
                                    "Confirmed by staff."
                                } else {
                                    "Thanks! Confirmation recorded ($confirmCount/3)."
                                }
                                onUpdated()
                            }.onFailure { err ->
                                feedbackMessage = err.message ?: "Could not confirm report."
                            }
                        }
                    },
                    enabled = !isConfirming && !alreadyConfirmed && status != "fixed",
                    modifier = Modifier.weight(1f)
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(if (alreadyConfirmed) "Confirmed" else "Still flooded")
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val res = repository.flagReportPhoto(report.reportId, "face visible")
                            feedbackMessage = res.fold(
                                onSuccess = { "Photo flagged for staff review." },
                                onFailure = { it.message ?: "Could not flag photo." }
                            )
                        }
                    }
                ) {
                    Text("Report a problem")
                }
            }
        }
    }
}
