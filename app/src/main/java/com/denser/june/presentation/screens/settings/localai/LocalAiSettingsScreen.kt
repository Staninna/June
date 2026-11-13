package com.denser.june.presentation.screens.settings.localai

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.localai.DictationPrompt
import com.denser.june.presentation.components.JuneBackButton
import com.denser.june.presentation.components.JuneAppBarType
import com.denser.june.presentation.components.JuneConfirmationDialog
import com.denser.june.presentation.components.JuneTopAppBar
import com.denser.june.presentation.navigation.AppNavigator
import com.denser.june.presentation.screens.settings.components.SettingSection
import com.denser.june.presentation.screens.settings.components.SettingsItem
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalAiSettingsScreen(viewModel: LocalAiVM = koinViewModel()) {
    val navigator = koinInject<AppNavigator>()
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showRestoreDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val pairLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val paired = viewModel.onPairResult(result.resultCode, result.data)
            Toast.makeText(
                context,
                context.getString(if (paired) R.string.local_ai_paired_toast else R.string.local_ai_pair_cancelled),
                Toast.LENGTH_SHORT,
            ).show()
        }
    val pair = {
        try {
            pairLauncher.launch(viewModel.pairIntent())
        } catch (_: ActivityNotFoundException) {
            viewModel.refresh()
        }
    }
    val openServer = {
        LocalAiVM.serverLaunchIntent(context, settings.serverPackage)?.let { context.startActivity(it) }
        Unit
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            JuneTopAppBar(
                type = JuneAppBarType.Large,
                scrollBehavior = scrollBehavior,
                title = { Text(text = stringResource(R.string.local_ai)) },
                navigationIcon = { JuneBackButton(onClick = { navigator.navigateBack() }) },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 32.dp),
            ) {
                item {
                    StatusCard(
                        status = status,
                        onPair = pair,
                        onOpenServer = openServer,
                        onRefresh = viewModel::refresh,
                    )
                }

                item {
                    SettingSection(title = stringResource(R.string.local_ai_section_connection)) {
                        SettingsItem(
                            title = stringResource(R.string.local_ai_enable),
                            subtitle = stringResource(R.string.local_ai_enable_desc),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.auto_awesome_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            trailingContent = {
                                Switch(checked = settings.enabled, onCheckedChange = { viewModel.setEnabled(it) })
                            },
                            onClick = { viewModel.setEnabled(!settings.enabled) },
                        )
                        if (settings.isPaired) {
                            SettingsItem(
                                title = stringResource(R.string.local_ai_unpair),
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.close_24px),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                },
                                onClick = viewModel::unpair,
                            )
                        }
                    }
                }

                item {
                    SettingSection(title = stringResource(R.string.local_ai_section_prompt)) {
                        PromptEditor(
                            template = settings.systemPromptTemplate,
                            isCustom = settings.customSystemPrompt != null,
                            peopleSummary =
                                if (people.isEmpty()) stringResource(R.string.local_ai_prompt_no_people)
                                else DictationPrompt.peopleList(people),
                            onSave = viewModel::saveSystemPrompt,
                        )
                        SettingsItem(
                            title = stringResource(R.string.local_ai_restore_defaults),
                            subtitle = stringResource(R.string.local_ai_restore_defaults_desc),
                            enabled = settings.customSystemPrompt != null,
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.replay_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            onClick = { showRestoreDialog = true },
                        )
                    }
                }

                item {
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
                        Icon(
                            painterResource(R.drawable.info_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.local_ai_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        JuneConfirmationDialog(
            title = stringResource(R.string.local_ai_restore_confirm_title),
            description = stringResource(R.string.local_ai_restore_confirm_desc),
            confirmButtonText = stringResource(R.string.local_ai_restore_defaults),
            isDestructive = false,
            icon = R.drawable.replay_24px,
            onConfirm = {
                viewModel.restoreDefaults()
                showRestoreDialog = false
            },
            onDismiss = { showRestoreDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(
    status: LocalAiStatus,
    onPair: () -> Unit,
    onOpenServer: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ready = status is LocalAiStatus.Ready
    val container by animateColorAsState(
        when (status) {
            is LocalAiStatus.Ready -> scheme.primaryContainer
            LocalAiStatus.Checking -> scheme.surfaceContainerHigh
            else -> scheme.secondaryContainer
        },
        label = "localAiStatus",
    )
    val content = if (ready) scheme.onPrimaryContainer else scheme.onSecondaryContainer

    val (title, body) =
        when (status) {
            LocalAiStatus.Checking -> stringResource(R.string.local_ai_status_checking) to null
            LocalAiStatus.NotInstalled ->
                stringResource(R.string.local_ai_status_not_installed) to stringResource(R.string.local_ai_status_not_installed_desc)
            LocalAiStatus.NotRunning ->
                stringResource(R.string.local_ai_status_not_running) to stringResource(R.string.local_ai_status_not_running_desc)
            LocalAiStatus.NotPaired ->
                stringResource(R.string.local_ai_status_not_paired) to stringResource(R.string.local_ai_status_not_paired_desc)
            LocalAiStatus.Revoked ->
                stringResource(R.string.local_ai_status_revoked) to stringResource(R.string.local_ai_status_revoked_desc)
            LocalAiStatus.NoModel ->
                stringResource(R.string.local_ai_status_no_model) to stringResource(R.string.local_ai_status_no_model_desc)
            is LocalAiStatus.Ready ->
                (status.model?.let { stringResource(R.string.local_ai_status_ready_model, it) }
                    ?: stringResource(R.string.local_ai_status_ready)) to null
        }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(status is LocalAiStatus.Checking, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "statusIcon") { checking ->
                        if (checking) {
                            ContainedLoadingIndicator(Modifier.size(56.dp))
                        } else {
                            Surface(
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                                color = content.copy(alpha = 0.12f),
                                modifier = Modifier.size(56.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painterResource(if (ready) R.drawable.check_circle_24px else R.drawable.auto_awesome_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (body != null) {
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
                    }
                }
            }
            AnimatedVisibility(status !is LocalAiStatus.Checking) {
                FlowRow(
                    Modifier.padding(top = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (status) {
                        LocalAiStatus.NotPaired -> Button(onClick = onPair, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.local_ai_pair)) }
                        LocalAiStatus.Revoked -> Button(onClick = onPair, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.local_ai_pair_again)) }
                        LocalAiStatus.NotRunning, LocalAiStatus.NoModel -> {
                            TextButton(onClick = onRefresh) { Text(stringResource(R.string.local_ai_refresh)) }
                            Button(onClick = onOpenServer, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(painterResource(R.drawable.open_in_new_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.local_ai_open_server))
                            }
                        }
                        LocalAiStatus.NotInstalled -> TextButton(onClick = onRefresh) { Text(stringResource(R.string.local_ai_refresh)) }
                        is LocalAiStatus.Ready -> {
                            TextButton(onClick = onPair) { Text(stringResource(R.string.local_ai_pair_again)) }
                            FilledTonalButton(onClick = onOpenServer) { Text(stringResource(R.string.local_ai_open_server)) }
                        }
                        LocalAiStatus.Checking -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptEditor(
    template: String,
    isCustom: Boolean,
    peopleSummary: String,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(template) { mutableStateOf(template) }
    val dirty = text.trim() != template.trim()
    SettingsItem(
        title = stringResource(R.string.local_ai_prompt_title),
        subtitle = stringResource(R.string.local_ai_prompt_desc, peopleSummary),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.edit_note_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        trailingContent = {
            Text(
                stringResource(if (isCustom) R.string.local_ai_prompt_custom else R.string.local_ai_prompt_default),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodySmall,
            minLines = 6,
            maxLines = 18,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.local_ai_prompt_title)) },
        )
        AnimatedVisibility(dirty) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { text = template }) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
