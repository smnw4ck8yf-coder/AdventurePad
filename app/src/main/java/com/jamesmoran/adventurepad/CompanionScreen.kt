package com.jamesmoran.adventurepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadDesign

internal data class CompanionStatistics(
    val targetId: String,
    val displayMode: DisplayMode,
    val splitProfileConfigured: Boolean,
    val notesPresent: Boolean,
)

@Composable
internal fun CompanionScreen(
    gameId: String,
    persistedNotes: String,
    selectedSection: CompanionSection,
    statistics: CompanionStatistics,
    onNotesChanged: (String) -> Unit,
    onSectionSelected: (CompanionSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var notesDraft by rememberSaveable(gameId) { mutableStateOf(persistedNotes) }
    var lastPersistedNotes by rememberSaveable(gameId) { mutableStateOf(persistedNotes) }

    LaunchedEffect(gameId, persistedNotes) {
        if (notesDraft == lastPersistedNotes) notesDraft = persistedNotes
        lastPersistedNotes = persistedNotes
    }

    Surface(
        color = AdventurePadDesign.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "COMPANION", onClose = onClose)
            CompanionSectionBar(
                selectedSection = selectedSection,
                onSectionSelected = onSectionSelected,
            )
            HorizontalDivider(color = AdventurePadDesign.outline)
            Box(Modifier.fillMaxSize()) {
                when (selectedSection) {
                    CompanionSection.NOTES -> NotesSection(
                        notes = notesDraft,
                        onNotesChanged = { updated ->
                            notesDraft = updated
                            onNotesChanged(updated)
                        },
                    )
                    CompanionSection.WALKTHROUGH -> PlaceholderSection(
                        title = "Walkthrough",
                        message = "No walkthrough added for this game.",
                        supportingText = "Approved or user-imported walkthrough text can be added here in a future milestone.",
                    )
                    CompanionSection.MANUAL -> PlaceholderSection(
                        title = "Manual",
                        message = "No manual added for this game.",
                        supportingText = "Original manual pages, maps, and preservation material will appear here when supplied by the user.",
                    )
                    CompanionSection.DIALOGUE -> PlaceholderSection(
                        title = "Recent Dialogue",
                        message = "Recent dialogue will appear here when dialogue capture support is added.",
                    )
                    CompanionSection.STATISTICS -> StatisticsSection(statistics)
                }
            }
        }
    }
}

@Composable
internal fun PageHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AdventurePadDesign.contentPadding, vertical = AdventurePadDesign.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClose,
            colors = ButtonDefaults.textButtonColors(contentColor = AdventurePadDesign.textSecondary),
            modifier = Modifier.heightIn(min = AdventurePadDesign.utilityTouchTarget),
        ) {
            Text("CLOSE")
        }
    }
}

@Composable
private fun CompanionSectionBar(
    selectedSection: CompanionSection,
    onSectionSelected: (CompanionSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AdventurePadDesign.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingXs),
    ) {
        CompanionSection.entries.forEach { section ->
            val selected = section == selectedSection
            TextButton(
                onClick = { onSectionSelected(section) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) AdventurePadDesign.surfaceRaised else AdventurePadDesign.background,
                    contentColor = if (selected) AdventurePadDesign.textPrimary else AdventurePadDesign.textSecondary,
                ),
                modifier = Modifier.heightIn(min = AdventurePadDesign.utilityTouchTarget),
            ) {
                Text(section.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun NotesSection(
    notes: String,
    onNotesChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
    ) {
        Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Saved automatically for this game.",
            color = AdventurePadDesign.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { onNotesChanged(it.take(MAX_NOTES_LENGTH)) },
            placeholder = { Text("Write clues, plans, or anything you want to remember…") },
            minLines = 8,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun PlaceholderSection(
    title: String,
    message: String,
    supportingText: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingMd),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AdventurePadDesign.surface, AdventurePadDesign.mediumShape)
                .border(AdventurePadDesign.subtleBorder, AdventurePadDesign.mediumShape)
                .padding(AdventurePadDesign.spacingLg),
            verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            supportingText?.let {
                Text(it, color = AdventurePadDesign.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatisticsSection(statistics: CompanionStatistics) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
    ) {
        Text("Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StatisticRow("Current game / target", statistics.targetId.ifBlank { "Launcher / unknown" })
        StatisticRow(
            "Current mode",
            if (statistics.displayMode == DisplayMode.INTERFACE) "Split View" else "Trackpad",
        )
        StatisticRow("Split View profile", if (statistics.splitProfileConfigured) "Configured" else "Not configured")
        StatisticRow("Notes", if (statistics.notesPresent) "Present" else "None yet")
        StatisticRow("Time played", "Not available")
    }
}

@Composable
private fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdventurePadDesign.surface, AdventurePadDesign.smallShape)
            .padding(horizontal = AdventurePadDesign.spacingLg, vertical = AdventurePadDesign.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AdventurePadDesign.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(1.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
