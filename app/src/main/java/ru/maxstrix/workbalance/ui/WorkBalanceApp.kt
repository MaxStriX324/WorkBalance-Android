package ru.maxstrix.workbalance.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.BuildConfig
import ru.maxstrix.workbalance.AppLanguage
import ru.maxstrix.workbalance.AppLocale
import ru.maxstrix.workbalance.R
import ru.maxstrix.workbalance.domain.CalendarRegion
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayResult
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.ForgottenMarkReminderSettings
import ru.maxstrix.workbalance.domain.MonthResult
import ru.maxstrix.workbalance.domain.ProductionCalendarSettings
import ru.maxstrix.workbalance.domain.ShortenedDayMode
import ru.maxstrix.workbalance.domain.WorkEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle

private const val PROJECT_URL = "https://github.com/MaxStriX324/WorkBalance-Android"

private enum class AppPage(@StringRes val labelRes: Int) {
    TODAY(R.string.nav_today),
    CALENDAR(R.string.nav_calendar),
    FORECAST(R.string.nav_forecast),
    SETTINGS(R.string.nav_settings)
}

private data class WorkIntervalUi(
    val start: WorkEvent,
    val end: WorkEvent?
)

private data class IntervalEditorState(
    val date: LocalDate,
    val interval: WorkIntervalUi?
)

@Composable
fun WorkBalanceApp(viewModel: WorkViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var page by remember { mutableStateOf(AppPage.TODAY) }
    var editedEvent by remember { mutableStateOf<WorkEvent?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    var intervalEditor by remember { mutableStateOf<IntervalEditorState?>(null) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val messageScope = rememberCoroutineScope()

    LaunchedEffect(state.updateMessage) {
        state.updateMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUpdateMessage()
        }
    }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                it.write(viewModel.backupJson())
            } ?: error(context.getString(R.string.error_open_file))
        }.onSuccess { fileMessage = context.getString(R.string.backup_saved) }
            .onFailure { fileMessage = context.getString(R.string.export_failed, it.message.orEmpty()) }
    }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                it.write(viewModel.monthCsv())
            } ?: error(context.getString(R.string.error_open_file))
        }.onSuccess { fileMessage = context.getString(R.string.month_report_saved) }
            .onFailure { fileMessage = context.getString(R.string.export_failed, it.message.orEmpty()) }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use { it.readText() }
                ?: error(context.getString(R.string.error_read_file))
        }.onSuccess { pendingImport = it }
            .onFailure { fileMessage = context.getString(R.string.read_failed, it.message.orEmpty()) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                when (item) {
                                    AppPage.TODAY -> Icons.Default.Home
                                    AppPage.CALENDAR -> Icons.Default.CalendarMonth
                                    AppPage.FORECAST -> Icons.Default.Assessment
                                    AppPage.SETTINGS -> Icons.Default.Settings
                                }, null
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.loading))
            }
        } else {
            when (page) {
                AppPage.TODAY -> TodayScreen(
                    state = state,
                    onToggle = viewModel::togglePresence,
                    onAdd = { addingEvent = true },
                    onEdit = { editedEvent = it },
                    onShortenedDecision = { apply ->
                        viewModel.setShortenedDayDecision(state.now.toLocalDate(), apply)
                    },
                    modifier = Modifier.padding(padding)
                )
                AppPage.CALENDAR -> CalendarScreen(
                    state = state,
                    onMonth = viewModel::setMonth,
                    onSetDay = viewModel::setDay,
                    onAddInterval = { date -> intervalEditor = IntervalEditorState(date, null) },
                    onEditInterval = { date, interval ->
                        intervalEditor = IntervalEditorState(date, interval)
                    },
                    onShortenedDecision = viewModel::setShortenedDayDecision,
                    modifier = Modifier.padding(padding)
                )
                AppPage.FORECAST -> ForecastScreen(state, Modifier.padding(padding))
                AppPage.SETTINGS -> SettingsScreen(
                    state = state,
                    onSave = { name, work, lunch, enabled, lead, updateCheckEnabled, calendarSettings, forgottenSettings ->
                        viewModel.saveSettings(
                            name, work, lunch, enabled, lead, updateCheckEnabled,
                            calendarSettings, forgottenSettings
                        ) {
                            messageScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_saved))
                            }
                        }
                    },
                    onLanguageChange = { language -> AppLocale.set(context, language) },
                    onOpenProject = { uriHandler.openUri(PROJECT_URL) },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onExportBackup = { exportBackup.launch("WorkBalance_backup.json") },
                    onImportBackup = { importBackup.launch(arrayOf("application/json", "text/plain")) },
                    onExportCsv = {
                        val month = state.selectedMonth
                        exportCsv.launch("WorkBalance_${month.year}-%02d.csv".format(month.monthValue))
                    },
                    fileMessage = fileMessage,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (addingEvent) {
        EventEditorDialog(
            initial = null,
            onDismiss = { addingEvent = false },
            onSave = { at, type -> viewModel.addEvent(at, type); addingEvent = false }
        )
    }
    editedEvent?.let { event ->
        EventEditorDialog(
            initial = event,
            onDismiss = { editedEvent = null },
            onSave = { at, type ->
                viewModel.updateEvent(event.copy(at = at, type = type)); editedEvent = null
            },
            onDelete = { viewModel.deleteEvent(event); editedEvent = null }
        )
    }
    intervalEditor?.let { editor ->
        IntervalEditorDialog(
            date = editor.date,
            initial = editor.interval,
            onDismiss = { intervalEditor = null },
            onSave = { start, end ->
                val initial = editor.interval
                if (initial == null) {
                    viewModel.addInterval(start, end)
                } else {
                    viewModel.saveInterval(initial.start, initial.end, start, end)
                }
                intervalEditor = null
            },
            onDelete = editor.interval?.let { interval ->
                {
                    viewModel.deleteInterval(interval.start, interval.end)
                    intervalEditor = null
                }
            }
        )
    }
    pendingImport?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.restore_backup_title)) },
            text = { Text(stringResource(R.string.restore_backup_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importBackup(json) { fileMessage = it }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    state.availableRelease?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAvailableRelease,
            title = { Text(stringResource(R.string.update_available_title, release.version)) },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.installed_version, BuildConfig.VERSION_NAME))
                    Text(
                        stringResource(R.string.manual_update_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (release.notes.isNotBlank()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.whats_new), fontWeight = FontWeight.Bold)
                        Text(release.notes.take(2_000))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAvailableRelease()
                    uriHandler.openUri(release.pageUrl)
                }) { Text(stringResource(R.string.open_release)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAvailableRelease) {
                    Text(stringResource(R.string.common_later))
                }
            }
        )
    }
}

@Composable
private fun TodayScreen(
    state: WorkUiState,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (WorkEvent) -> Unit,
    onShortenedDecision: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = state.today ?: return
    val calendarNote = today.calendarNote
    val localizedCalendarNote = calendarNote?.let { it.localizedCalendarNote() }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(state.workplaceName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                today.date.asWeekday(TextStyle.FULL) + ", " + today.date.asDate(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (today.shortenedDecisionNeeded) {
            item { ShortenedDayDecisionCard(today, onShortenedDecision) }
        } else if (!localizedCalendarNote.isNullOrBlank()) {
            item {
                val detail = when {
                    today.requiredMinutes == 0L -> stringResource(R.string.today_calendar_day_off)
                    today.shortenedApplied -> stringResource(
                        R.string.daily_target_reduced,
                        today.shortenedByMinutes.toLong().asDuration()
                    )
                    today.shortenedByMinutes > 0 -> stringResource(R.string.reduction_not_applied)
                    else -> stringResource(R.string.daily_target_uses_calendar)
                }
                InfoCard(localizedCalendarNote, detail, MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item { StatusCard(state) }
        item {
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(78.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (today.isCurrentlyInside) Color(0xFFBE3A31) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    stringResource(
                        if (today.isCurrentlyInside) R.string.checked_out_button else R.string.checked_in_button
                    ),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.records_today), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.common_add))
                }
            }
        }
        if (today.events.isEmpty()) item {
            Text(stringResource(R.string.no_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(today.events, key = { it.id }) { event ->
            EventRow(event, onEdit)
        }
        if (today.warnings.isNotEmpty()) item {
            InfoCard(
                stringResource(R.string.check_records),
                localizedWarnings(today.warnings),
                MaterialTheme.colorScheme.errorContainer
            )
        }
    }
}

@Composable
private fun ShortenedDayDecisionCard(day: DayResult, onDecision: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                day.calendarNote?.localizedCalendarNote() ?: stringResource(R.string.shortened_day),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(
                    R.string.shortened_day_question,
                    day.shortenedByMinutes.toLong().asDuration(),
                    (day.requiredMinutes - day.shortenedByMinutes).coerceAtLeast(0).asDuration()
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onDecision(false) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.do_not_apply))
                }
                Button(onClick = { onDecision(true) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: WorkUiState) {
    val day = state.today ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (day.isCurrentlyInside) R.string.status_at_work else R.string.status_away),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(day.creditedMinutes.asDuration(), fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.credited_today_of, day.requiredMinutes.asDuration()),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (day.isCurrentlyInside) {
                Spacer(Modifier.height(20.dp)); HorizontalDivider()
                MetricRow(stringResource(R.string.normal_leave_time), state.normalExit?.asTime() ?: stringResource(R.string.common_not_available))
                MetricRow(stringResource(R.string.monthly_plan_leave_time), state.recommendedExit?.asTime() ?: stringResource(R.string.common_not_available))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MetricRow(stringResource(R.string.balance_before_today), state.previousBalanceMinutes.asSignedDuration())
            when {
                state.balanceIncludingTodayMinutes < 0 -> {
                    val hint = if (day.isCurrentlyInside && state.balanceZeroExit != null) {
                        stringResource(
                            R.string.balance_zero_stay_until,
                            (-state.balanceIncludingTodayMinutes).asDuration(),
                            state.balanceZeroExit.asTime()
                        )
                    } else {
                        stringResource(
                            R.string.balance_zero_missing,
                            (-state.balanceIncludingTodayMinutes).asDuration()
                        )
                    }
                    Text(
                        hint,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                state.balanceIncludingTodayMinutes > 0 -> Text(
                    stringResource(R.string.current_surplus, state.balanceIncludingTodayMinutes.asSignedDuration()),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                day.events.isNotEmpty() -> Text(
                    stringResource(R.string.balance_cleared),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MetricRow(stringResource(R.string.on_site), day.presenceMinutes.asDuration())
            if (state.schedule.lunchMinutes > 0) {
                MetricRow(stringResource(R.string.off_site_total), day.outsideMinutes.asDuration())
                MetricRow(stringResource(R.string.official_break_off_site), day.lunchOutsideMinutes.asDuration())
                if (day.deductedLunchMinutes > 0) {
                    MetricRow(stringResource(R.string.official_break_on_site), day.deductedLunchMinutes.asDuration())
                }
                if (day.extraOutsideMinutes > 0) {
                    MetricRow(stringResource(R.string.additional_absence), day.extraOutsideMinutes.asDuration())
                }
            } else {
                MetricRow(stringResource(R.string.off_site), day.outsideMinutes.asDuration())
            }
        }
    }
}

@Composable
private fun EventRow(event: WorkEvent, onEdit: (WorkEvent) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(event) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    stringResource(if (event.type == EventType.IN) R.string.entry else R.string.exit),
                    fontWeight = FontWeight.SemiBold
                )
                Text(event.at.asTime(), style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarScreen(
    state: WorkUiState,
    onMonth: (YearMonth) -> Unit,
    onSetDay: (LocalDate, DayKind) -> Unit,
    onAddInterval: (LocalDate) -> Unit,
    onEditInterval: (LocalDate, WorkIntervalUi) -> Unit,
    onShortenedDecision: (LocalDate, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val month = state.month ?: return
    var selectedDay by remember { mutableStateOf<DayResult?>(null) }
    var dayForKind by remember { mutableStateOf<DayResult?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onMonth(state.selectedMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_month))
            }
            Text(state.selectedMonth.asMonthTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onMonth(state.selectedMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.next_month))
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { MonthSummary(month) }
            if (state.productionCalendar.settings.federalEnabled &&
                !state.productionCalendar.hasYear(state.selectedMonth.year)
            ) {
                item {
                    InfoCard(
                        stringResource(R.string.no_official_calendar_title, state.selectedMonth.year),
                        stringResource(R.string.no_official_calendar_body),
                        MaterialTheme.colorScheme.errorContainer
                    )
                }
            }
            items(month.days, key = { it.date.toEpochDay() }) { day ->
                DayRow(day, state.data?.overrides?.get(day.date)?.kind) { selectedDay = day }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
    selectedDay?.let { day ->
        DayDetailsDialog(
            day = day,
            kind = state.data?.overrides?.get(day.date)?.kind ?: DayKind.AUTO,
            showLunchBreakdown = state.schedule.lunchMinutes > 0,
            onDismiss = { selectedDay = null },
            onAddInterval = {
                selectedDay = null
                onAddInterval(day.date)
            },
            onEditInterval = { interval ->
                selectedDay = null
                onEditInterval(day.date, interval)
            },
            onShortenedDecision = { apply ->
                selectedDay = null
                onShortenedDecision(day.date, apply)
            },
            onChangeKind = {
                selectedDay = null
                dayForKind = day
            }
        )
    }
    dayForKind?.let { day ->
        DayKindDialog(day.date, onDismiss = { dayForKind = null }) { kind ->
            onSetDay(day.date, kind); dayForKind = null
        }
    }
}

@Composable
private fun MonthSummary(month: MonthResult) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            MetricRow(stringResource(R.string.month_target), month.planMinutes.asDuration())
            MetricRow(stringResource(R.string.credited), month.creditedMinutes.asDuration())
            MetricRow(stringResource(R.string.balance_to_date), month.balanceToDateMinutes.asSignedDuration())
        }
    }
}

@Composable
private fun DayRow(day: DayResult, kind: DayKind?, onClick: () -> Unit) {
    val isFree = day.requiredMinutes == 0L
    val calendarNote = day.calendarNote?.localizedCalendarNote()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isFree) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${day.date.dayOfMonth}, ${day.date.asWeekday(TextStyle.SHORT)}", fontWeight = FontWeight.Bold)
                Text(
                    when {
                        kind == DayKind.PLANNED_ABSENCE -> stringResource(R.string.planned_absence_row)
                        isFree -> calendarNote ?: stringResource(R.string.day_off_or_special)
                        else -> stringResource(
                            R.string.credited_of_required,
                            day.creditedMinutes.asDuration(),
                            day.requiredMinutes.asDuration()
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isFree && !calendarNote.isNullOrBlank()) {
                    Text(calendarNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    day.shortenedDecisionNeeded -> Text(
                        stringResource(R.string.shortening_decision_needed),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    day.shortenedApplied -> Text(
                        stringResource(R.string.target_reduced_by, day.shortenedByMinutes.toLong().asDuration()),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!isFree || day.creditedMinutes > 0) {
                Text(day.balanceMinutes.asSignedDuration(), fontWeight = FontWeight.Bold, color = balanceColor(day.balanceMinutes))
            }
        }
    }
}

@Composable
private fun ForecastScreen(state: WorkUiState, modifier: Modifier = Modifier) {
    val month = state.month ?: return
    val progress = if (month.planMinutes == 0L) 0f else {
        (month.creditedMinutes.toFloat() / month.planMinutes).coerceIn(0f, 1f)
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.forecast_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(month.month.asMonthTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.productionCalendar.settings.federalEnabled &&
            !state.productionCalendar.hasYear(month.month.year)
        ) {
            item {
                InfoCard(
                    stringResource(R.string.estimated_plan),
                    stringResource(R.string.calendar_not_installed_forecast, month.month.year),
                    MaterialTheme.colorScheme.errorContainer
                )
            }
        }
        item {
            InfoCard(
                stringResource(R.string.remaining_to_complete),
                month.remainingMinutes.asDuration(),
                MaterialTheme.colorScheme.primaryContainer,
                large = true
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.month_completion), fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.progress_percent, (progress * 100).toInt()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            InfoCard(
                stringResource(R.string.average_needed),
                if (month.remainingWorkDays > 0) {
                    stringResource(
                        R.string.average_per_day,
                        month.averageMinutesPerRemainingDay.asDuration(),
                        month.remainingWorkDays
                    )
                } else {
                    stringResource(R.string.no_workdays_remaining)
                },
                MaterialTheme.colorScheme.secondaryContainer,
                large = true
            )
        }
        item {
            val currentMonth = month.month == YearMonth.from(state.now)
            val paceBalance = if (currentMonth) state.previousBalanceMinutes else month.balanceToDateMinutes
            val message = when {
                paceBalance > 0 -> stringResource(R.string.pace_surplus, paceBalance.asDuration())
                paceBalance < 0 -> stringResource(R.string.pace_deficit, (-paceBalance).asDuration())
                else -> stringResource(R.string.pace_on_plan)
            }
            InfoCard(stringResource(R.string.current_pace), message, MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    MetricRow(stringResource(R.string.target), month.planMinutes.asDuration())
                    MetricRow(stringResource(R.string.credited), month.creditedMinutes.asDuration())
                    MetricRow(stringResource(R.string.remaining), month.remainingMinutes.asDuration())
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.monthly_statistics), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    MetricRow(stringResource(R.string.on_site), month.presenceMinutes.asDuration())
                    MetricRow(stringResource(R.string.off_site_total), month.outsideMinutes.asDuration())
                    if (state.schedule.lunchMinutes > 0) {
                        MetricRow(stringResource(R.string.official_break_off_site), month.lunchOutsideMinutes.asDuration())
                        MetricRow(stringResource(R.string.official_break_on_site), month.deductedLunchMinutes.asDuration())
                        MetricRow(stringResource(R.string.additional_absence), month.extraOutsideMinutes.asDuration())
                    }
                    MetricRow(stringResource(R.string.days_with_records), month.workedDays.toString())
                    MetricRow(stringResource(R.string.average_per_worked_day), month.averageCreditedPerWorkedDay.asDuration())
                }
            }
        }
        item {
            InfoCard(
                stringResource(R.string.planned_absences),
                stringResource(R.string.planned_absences_help),
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: WorkUiState,
    onSave: (
        String,
        Int,
        Int,
        Boolean,
        Int,
        Boolean,
        ProductionCalendarSettings,
        ForgottenMarkReminderSettings
    ) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onOpenProject: () -> Unit,
    onCheckUpdates: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    fileMessage: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(AppLocale.current(context)) }
    var name by remember(state.workplaceName) { mutableStateOf(state.workplaceName) }
    var workHours by remember(state.schedule.workMinutes) { mutableStateOf((state.schedule.workMinutes / 60).toString()) }
    var workMinutes by remember(state.schedule.workMinutes) { mutableStateOf((state.schedule.workMinutes % 60).toString()) }
    var lunchMinutes by remember(state.schedule.lunchMinutes) { mutableStateOf(state.schedule.lunchMinutes.toString()) }
    var reminderEnabled by remember(state.lunchReminderEnabled) { mutableStateOf(state.lunchReminderEnabled) }
    var reminderLead by remember(state.lunchReminderLeadMinutes) { mutableIntStateOf(state.lunchReminderLeadMinutes) }
    var forgottenReminderEnabled by remember(state.forgottenMarkReminderSettings.enabled) {
        mutableStateOf(state.forgottenMarkReminderSettings.enabled)
    }
    var forgottenEntryTime by remember(state.forgottenMarkReminderSettings.entryCheckTime) {
        mutableStateOf(state.forgottenMarkReminderSettings.entryCheckTime)
    }
    var forgottenExitGrace by remember(state.forgottenMarkReminderSettings.exitGraceMinutes) {
        mutableIntStateOf(state.forgottenMarkReminderSettings.exitGraceMinutes)
    }
    var forgottenSnooze by remember(state.forgottenMarkReminderSettings.snoozeMinutes) {
        mutableIntStateOf(state.forgottenMarkReminderSettings.snoozeMinutes)
    }
    var automaticUpdateCheckEnabled by remember(state.automaticUpdateCheckEnabled) {
        mutableStateOf(state.automaticUpdateCheckEnabled)
    }
    var federalCalendarEnabled by remember(state.productionCalendar.settings.federalEnabled) {
        mutableStateOf(state.productionCalendar.settings.federalEnabled)
    }
    var regionalCalendarEnabled by remember(state.productionCalendar.settings.regionalEnabled) {
        mutableStateOf(state.productionCalendar.settings.regionalEnabled)
    }
    var calendarRegion by remember(state.productionCalendar.settings.region) {
        mutableStateOf(state.productionCalendar.settings.region)
    }
    var shortenedDayMode by remember(state.productionCalendar.settings.shortenedDayMode) {
        mutableStateOf(state.productionCalendar.settings.shortenedDayMode)
    }
    var showInstructions by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.app_language), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.app_language_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        AppLanguage.SYSTEM to R.string.language_system,
                        AppLanguage.ENGLISH to R.string.language_english,
                        AppLanguage.RUSSIAN to R.string.language_russian
                    ).forEach { (language, labelRes) ->
                        if (selectedLanguage == language) {
                            Button(
                                onClick = { selectedLanguage = language; onLanguageChange(language) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(labelRes)) }
                        } else {
                            OutlinedButton(
                                onClick = { selectedLanguage = language; onLanguageChange(language) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.workplace_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(stringResource(R.string.presets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    workHours = "8"; workMinutes = "0"; lunchMinutes = "60"
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preset_full_time)) }
                OutlinedButton(onClick = {
                    workHours = "4"; workMinutes = "0"; lunchMinutes = "30"
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preset_half_time)) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.custom_schedule), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            workHours,
                            { workHours = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.hours)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            workMinutes,
                            { workMinutes = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.minutes)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        lunchMinutes,
                        { lunchMinutes = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.required_break_minutes)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.production_calendar), fontWeight = FontWeight.Bold)
                    val years = state.availableCalendarYears.sorted().joinToString()
                    val yearsLabel = if (years.isBlank()) stringResource(R.string.none) else years
                    Text(
                        stringResource(R.string.bundled_calendar_years, yearsLabel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.russian_holidays), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.non_working_and_transfers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = federalCalendarEnabled, onCheckedChange = { federalCalendarEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.regional_holidays), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.regional_holidays_example), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = regionalCalendarEnabled,
                            onCheckedChange = { enabled ->
                                regionalCalendarEnabled = enabled
                                if (enabled && calendarRegion == CalendarRegion.NONE) {
                                    calendarRegion = CalendarRegion.SARATOV
                                }
                            }
                        )
                    }
                    if (regionalCalendarEnabled) {
                        Text(stringResource(R.string.region), fontWeight = FontWeight.SemiBold)
                        CalendarRegion.entries.filter { it != CalendarRegion.NONE }.forEach { region ->
                            if (calendarRegion == region) {
                                Button(onClick = { calendarRegion = region }, modifier = Modifier.fillMaxWidth()) {
                                    Text(region.localizedTitle())
                                }
                            } else {
                                OutlinedButton(onClick = { calendarRegion = region }, modifier = Modifier.fillMaxWidth()) {
                                    Text(region.localizedTitle())
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.shortened_days), fontWeight = FontWeight.SemiBold)
                    ShortenedDayMode.entries.forEach { mode ->
                        if (shortenedDayMode == mode) {
                            Button(onClick = { shortenedDayMode = mode }, modifier = Modifier.fillMaxWidth()) {
                                Text(mode.localizedTitle())
                            }
                        } else {
                            OutlinedButton(onClick = { shortenedDayMode = mode }, modifier = Modifier.fillMaxWidth()) {
                                Text(mode.localizedTitle())
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.shortened_mode_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.lunch_reminder), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.while_off_site), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    }
                    if (reminderEnabled) {
                        Text(stringResource(R.string.remind_before))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (reminderLead == 10) Button(onClick = { reminderLead = 10 }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.minutes_short, 10))
                            } else OutlinedButton(onClick = { reminderLead = 10 }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.minutes_short, 10))
                            }
                            if (reminderLead == 15) Button(onClick = { reminderLead = 15 }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.minutes_short, 15))
                            } else OutlinedButton(onClick = { reminderLead = 15 }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.minutes_short, 15))
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.forgotten_records), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.forgotten_records_help),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = forgottenReminderEnabled,
                            onCheckedChange = { forgottenReminderEnabled = it }
                        )
                    }
                    if (forgottenReminderEnabled) {
                        Text(stringResource(R.string.check_missing_entry), fontWeight = FontWeight.SemiBold)
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> forgottenEntryTime = LocalTime.of(hour, minute) },
                                    forgottenEntryTime.hour,
                                    forgottenEntryTime.minute,
                                    true
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Schedule, null)
                            Text(
                                "  " + stringResource(
                                    R.string.at_time,
                                    "%02d:%02d".format(forgottenEntryTime.hour, forgottenEntryTime.minute)
                                )
                            )
                        }

                        Text(stringResource(R.string.exit_reminder_after_shift))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 120).forEach { minutes ->
                                val label = if (minutes < 60) {
                                    stringResource(R.string.minutes_short, minutes)
                                } else {
                                    stringResource(R.string.hours_short, minutes / 60)
                                }
                                if (forgottenExitGrace == minutes) {
                                    Button(
                                        onClick = { forgottenExitGrace = minutes },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { forgottenExitGrace = minutes },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                }
                            }
                        }

                        Text(stringResource(R.string.snooze_button_setting))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60).forEach { minutes ->
                                val label = if (minutes < 60) {
                                    stringResource(R.string.minutes_short, minutes)
                                } else {
                                    stringResource(R.string.hours_short, 1)
                                }
                                if (forgottenSnooze == minutes) {
                                    Button(
                                        onClick = { forgottenSnooze = minutes },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { forgottenSnooze = minutes },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.forgotten_skip_days),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showInstructions = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.HelpOutline, null)
                Text("  " + stringResource(R.string.instructions_button))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.about_app), fontWeight = FontWeight.Bold)
                    MetricRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                    Text(stringResource(R.string.project_author))
                    Text(
                        stringResource(R.string.github_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenProject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_github))
                    }
                    if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            enabled = !state.checkingForUpdates,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (state.checkingForUpdates) {
                                        R.string.checking_updates
                                    } else {
                                        R.string.check_updates
                                    }
                                )
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.automatic_update_check), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.automatic_update_check_help),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = automaticUpdateCheckEnabled,
                                onCheckedChange = { automaticUpdateCheckEnabled = it }
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = {
                val totalWork = (workHours.toIntOrNull() ?: 0) * 60 + (workMinutes.toIntOrNull() ?: 0)
                onSave(
                    name,
                    totalWork,
                    lunchMinutes.toIntOrNull() ?: 0,
                    reminderEnabled,
                    reminderLead,
                    automaticUpdateCheckEnabled,
                    ProductionCalendarSettings(
                        federalEnabled = federalCalendarEnabled,
                        regionalEnabled = regionalCalendarEnabled,
                        region = calendarRegion,
                        shortenedDayMode = shortenedDayMode
                    ),
                    ForgottenMarkReminderSettings(
                        enabled = forgottenReminderEnabled,
                        entryCheckTime = forgottenEntryTime,
                        exitGraceMinutes = forgottenExitGrace,
                        snoozeMinutes = forgottenSnooze
                    )
                )
            }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.save_settings)) }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_and_reports), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.backup_and_reports_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.export_json))
                    }
                    OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore_json))
                    }
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.export_month_csv, state.selectedMonth.asMonthTitle()))
                    }
                    fileMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showInstructions) {
        InstructionsDialog(onDismiss = { showInstructions = false })
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(title: String, body: String, color: Color, large: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(body, fontSize = if (large) 22.sp else 15.sp, fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun InstructionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instructions_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InstructionSection(
                    stringResource(R.string.instruction_1_title),
                    stringResource(R.string.instruction_1_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_2_title),
                    stringResource(R.string.instruction_2_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_3_title),
                    stringResource(R.string.instruction_3_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_4_title),
                    stringResource(R.string.instruction_4_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_5_title),
                    stringResource(R.string.instruction_5_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_6_title),
                    stringResource(R.string.instruction_6_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_7_title),
                    stringResource(R.string.instruction_7_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_8_title),
                    stringResource(R.string.instruction_8_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_9_title),
                    stringResource(R.string.instruction_9_body)
                )
                InstructionSection(
                    stringResource(R.string.instruction_10_title),
                    stringResource(R.string.instruction_10_body)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } }
    )
}

@Composable
private fun InstructionSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun balanceColor(minutes: Long): Color = when {
    minutes > 0 -> Color(0xFF237A3B)
    minutes < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DayDetailsDialog(
    day: DayResult,
    kind: DayKind,
    showLunchBreakdown: Boolean,
    onDismiss: () -> Unit,
    onAddInterval: () -> Unit,
    onEditInterval: (WorkIntervalUi) -> Unit,
    onShortenedDecision: (Boolean) -> Unit,
    onChangeKind: () -> Unit
) {
    val intervals = remember(day.events) { pairIntervals(day.events) }
    val calendarNote = day.calendarNote?.localizedCalendarNote()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.asDate()) },
        text = {
            Column(
                Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(kind.localizedTitle(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (!calendarNote.isNullOrBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(calendarNote, fontWeight = FontWeight.Bold)
                            when {
                                day.shortenedDecisionNeeded -> {
                                    Text(
                                        stringResource(
                                            R.string.official_shortening_question,
                                            day.shortenedByMinutes.toLong().asDuration()
                                        )
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onShortenedDecision(false) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.common_no)) }
                                        Button(
                                            onClick = { onShortenedDecision(true) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.common_yes)) }
                                    }
                                }
                                day.shortenedApplied -> Text(
                                    stringResource(
                                        R.string.target_reduced_by,
                                        day.shortenedByMinutes.toLong().asDuration()
                                    )
                                )
                                day.requiredMinutes == 0L -> Text(stringResource(R.string.calendar_non_working_day))
                                day.shortenedByMinutes > 0 -> Text(stringResource(R.string.reduction_not_applied))
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        MetricRow(stringResource(R.string.day_target), day.requiredMinutes.asDuration())
                        MetricRow(stringResource(R.string.on_site), day.presenceMinutes.asDuration())
                        MetricRow(stringResource(R.string.off_site_total), day.outsideMinutes.asDuration())
                        if (showLunchBreakdown) {
                            MetricRow(stringResource(R.string.official_break_off_site), day.lunchOutsideMinutes.asDuration())
                            MetricRow(stringResource(R.string.official_break_on_site), day.deductedLunchMinutes.asDuration())
                            if (day.extraOutsideMinutes > 0) {
                                MetricRow(stringResource(R.string.additional_absence), day.extraOutsideMinutes.asDuration())
                            }
                        }
                        MetricRow(stringResource(R.string.credited), day.creditedMinutes.asDuration())
                        MetricRow(stringResource(R.string.day_balance), day.balanceMinutes.asSignedDuration())
                    }
                }
                Text(stringResource(R.string.work_intervals), fontWeight = FontWeight.Bold)
                if (intervals.isEmpty()) {
                    Text(stringResource(R.string.no_intervals), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                intervals.forEach { interval ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { onEditInterval(interval) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "${interval.start.at.asTime()} — ${interval.end?.at?.asTime() ?: stringResource(R.string.open_interval)}",
                                Modifier.weight(1f).padding(start = 12.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.Edit, stringResource(R.string.common_edit))
                        }
                    }
                }
                if (day.warnings.isNotEmpty()) {
                    Text(
                        localizedWarnings(day.warnings),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(onClick = onAddInterval, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Text(" " + stringResource(R.string.add_interval))
                }
                OutlinedButton(onClick = onChangeKind, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.day_type, kind.localizedTitle()))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

private fun pairIntervals(events: List<WorkEvent>): List<WorkIntervalUi> {
    val result = mutableListOf<WorkIntervalUi>()
    var open: WorkEvent? = null
    events.sortedWith(compareBy<WorkEvent> { it.at }.thenBy { it.id }).forEach { event ->
        when (event.type) {
            EventType.IN -> {
                if (open != null) result += WorkIntervalUi(open!!, null)
                open = event
            }
            EventType.OUT -> {
                open?.let { result += WorkIntervalUi(it, event) }
                open = null
            }
        }
    }
    open?.let { result += WorkIntervalUi(it, null) }
    return result
}

@Composable
private fun IntervalEditorDialog(
    date: LocalDate,
    initial: WorkIntervalUi?,
    onDismiss: () -> Unit,
    onSave: (LocalDateTime, LocalDateTime) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var start by remember(initial) {
        mutableStateOf(initial?.start?.at?.toLocalTime() ?: LocalTime.of(9, 0))
    }
    var end by remember(initial) {
        mutableStateOf(
            initial?.end?.at?.toLocalTime()
                ?: if (date == LocalDate.now()) LocalTime.now().withSecond(0).withNano(0) else LocalTime.of(18, 0)
        )
    }
    val valid = end.isAfter(start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.add_interval_title else R.string.edit_interval_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date.asDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> start = LocalTime.of(hour, minute) }, start.hour, start.minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.entry_time, LocalDateTime.of(date, start).asTime())) }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> end = LocalTime.of(hour, minute) }, end.hour, end.minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.exit_time, LocalDateTime.of(date, end).asTime())) }
                if (!valid) {
                    Text(stringResource(R.string.exit_after_entry_error), color = MaterialTheme.colorScheme.error)
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Text(" " + stringResource(R.string.delete_interval))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(LocalDateTime.of(date, start), LocalDateTime.of(date, end)) },
                enabled = valid
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun DayKindDialog(date: LocalDate, onDismiss: () -> Unit, onSelect: (DayKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.asDate()) },
        text = {
            Column {
                DayKind.entries.forEach { kind ->
                    Text(
                        kind.localizedTitle(),
                        Modifier.fillMaxWidth().clickable { onSelect(kind) }.padding(vertical = 11.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    initial: WorkEvent?,
    onDismiss: () -> Unit,
    onSave: (LocalDateTime, EventType) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initial?.at?.toLocalDate() ?: LocalDate.now()) }
    var time by remember { mutableStateOf(initial?.at?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.now().withSecond(0).withNano(0)) }
    var type by remember { mutableStateOf(initial?.type ?: EventType.IN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.add_record_title else R.string.edit_record_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (type == EventType.IN) Button(onClick = { type = EventType.IN }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.entry))
                    } else OutlinedButton(onClick = { type = EventType.IN }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.entry))
                    }
                    if (type == EventType.OUT) Button(onClick = { type = EventType.OUT }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.exit))
                    } else OutlinedButton(onClick = { type = EventType.OUT }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.exit))
                    }
                }
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.date_value, date.asDate())) }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, true).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.time_value, LocalDateTime.of(date, time).asTime())) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null); Text(" " + stringResource(R.string.delete_record))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(LocalDateTime.of(date, time), type) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
