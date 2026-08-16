package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.config.SchemaFeatures
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.PaymentBadge
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.theme.AppShapes
import org.example.project.viewmodel.CategoryListViewModel
import org.example.project.viewmodel.ManagedListUiState
import org.example.project.viewmodel.PaymentModeListViewModel
import org.example.project.viewmodel.createCategoryListViewModel
import org.example.project.viewmodel.createPaymentModeListViewModel

/**
 * Full-screen add/delete editor for a user-owned string list (categories, payment modes).
 * Every add/delete persists immediately via the owning ViewModel — see [CategoryListViewModel] /
 * [PaymentModeListViewModel] — so there's no separate save step, unlike [BudgetScreen].
 */
@Composable
fun ManageListScreen(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    inputPlaceholder: String,
    uiState: ManagedListUiState,
    onClose: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    leadingIcon: @Composable (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BounceSurface(
                onClick = onClose,
                shape = AppShapes.pill,
                color = MaterialTheme.colorScheme.surfaceContainer,
                pressedScale = 0.9f,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AddItemRow(
                value = uiState.draftInput,
                placeholder = inputPlaceholder,
                onValueChange = onDraftChange,
                onAdd = onAdd,
            )

            AnimatedVisibility(visible = uiState.error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = uiState.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = "${uiState.items.size} ${if (uiState.items.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            uiState.items.forEach { item ->
                ManagedListRow(
                    name = item,
                    onDelete = { onDelete(item) },
                    leadingIcon = { leadingIcon(item) },
                )
            }

            Spacer(Modifier.height(120.dp)) // clears the floating nav pill
        }
    }
}

@Composable
private fun AddItemRow(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            shape = AppShapes.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.weight(1f),
        )
        BounceSurface(
            onClick = onAdd,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ManagedListRow(
    name: String,
    onDelete: () -> Unit,
    leadingIcon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leadingIcon()
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BounceSurface(
            onClick = onDelete,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surface,
            pressedScale = 0.92f,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Manage the primary category list — tracker_1 expense categories, or tracker_2 care-of. */
@Composable
fun CategoryManagementScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: CategoryListViewModel = createCategoryListViewModel(),
) {
    val features = remember { SchemaFeatures.current() }
    val label = features.categoryLabel.lowercase()
    val uiState by viewModel.uiState.collectAsState()
    ManageListScreen(
        modifier = modifier,
        title = "Manage ${label}s",
        subtitle = "Add or remove options shown in the $label picker",
        inputPlaceholder = "New $label",
        uiState = uiState,
        onClose = onClose,
        onDraftChange = viewModel::updateDraft,
        onAdd = viewModel::addItem,
        onDelete = viewModel::deleteItem,
        leadingIcon = { name ->
            CategoryGlyph(
                kind = categoryGlyphKind(name),
                color = MaterialTheme.colorScheme.primary,
                size = 22.dp,
            )
        },
    )
}

/** Manage the payment-mode list (how a transaction was paid). */
@Composable
fun PaymentModeManagementScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: PaymentModeListViewModel = createPaymentModeListViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ManageListScreen(
        modifier = modifier,
        title = "Manage payment modes",
        subtitle = "Add or remove options shown in the payment picker",
        inputPlaceholder = "New payment mode",
        uiState = uiState,
        onClose = onClose,
        onDraftChange = viewModel::updateDraft,
        onAdd = viewModel::addItem,
        onDelete = viewModel::deleteItem,
        leadingIcon = { name -> PaymentBadge(name = name, size = 32.dp) },
    )
}
