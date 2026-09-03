package ru.maxstrix.workbalance.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.BuildConfig
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayResult
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.MonthResult
import ru.maxstrix.workbalance.domain.WorkEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val PROJECT_URL = "https://github.com/MaxStriX324/WorkBalance-Android"

private enum class AppPage(val label: String) {
    TODAY("Сегодня"), CALENDAR("Календарь"), FORECAST("Прогноз"), SETTINGS("Настройки")
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
            } ?: error("Не удалось открыть файл")
        }.onSuccess { fileMessage = "Резервная копия сохранена" }
            .onFailure { fileMessage = "Ошибка экспорта: ${it.message}" }
    }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                it.write(viewModel.monthCsv())
            } ?: error("Не удалось открыть файл")
        }.onSuccess { fileMessage = "Отчёт месяца сохранён" }
            .onFailure { fileMessage = "Ошибка экспорта: ${it.message}" }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Не удалось прочитать файл")
        }.onSuccess { pendingImport = it }
            .onFailure { fileMessage = "Ошибка чтения: ${it.message}" }
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
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Загрузка…")
            }
        } else {
            when (page) {
                AppPage.TODAY -> TodayScreen(
                    state = state,
                    onToggle = viewModel::togglePresence,
                    onAdd = { addingEvent = true },
                    onEdit = { editedEvent = it },
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
                    modifier = Modifier.padding(padding)
                )
                AppPage.FORECAST -> ForecastScreen(state, Modifier.padding(padding))
                AppPage.SETTINGS -> SettingsScreen(
                    state = state,
                    onSave = { name, work, lunch, enabled, lead, updateCheckEnabled ->
                        viewModel.saveSettings(name, work, lunch, enabled, lead, updateCheckEnabled) {
                            messageScope.launch { snackbarHostState.showSnackbar("Настройки сохранены") }
                        }
                    },
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
            title = { Text("Восстановить резервную копию?") },
            text = { Text("Все текущие отметки, особые дни и настройки будут заменены данными из выбранного файла.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importBackup(json) { fileMessage = it }
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } }
        )
    }
    state.availableRelease?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAvailableRelease,
            title = { Text("Доступна версия ${release.version}") },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Установлена версия ${BuildConfig.VERSION_NAME}.")
                    Text(
                        "Обновление устанавливается вручную со страницы проекта. При той же подписи APK ваши данные сохранятся.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (release.notes.isNotBlank()) {
                        HorizontalDivider()
                        Text("Что изменилось", fontWeight = FontWeight.Bold)
                        Text(release.notes.take(2_000))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAvailableRelease()
                    uriHandler.openUri(release.pageUrl)
                }) { Text("Открыть релиз") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAvailableRelease) { Text("Позже") }
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
    modifier: Modifier = Modifier
) {
    val today = state.today ?: return
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(state.workplaceName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                today.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru")) + ", " + today.date.asDate(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                Text(if (today.isCurrentlyInside) "ВЫШЕЛ" else "ВОШЁЛ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Отметки сегодня", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(" Добавить")
                }
            }
        }
        if (today.events.isEmpty()) item {
            Text("Отметок пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(today.events, key = { it.id }) { event ->
            EventRow(event, onEdit)
        }
        if (today.warnings.isNotEmpty()) item {
            InfoCard("Проверьте отметки", today.warnings.joinToString("\n"), MaterialTheme.colorScheme.errorContainer)
        }
    }
}

@Composable
private fun StatusCard(state: WorkUiState) {
    val day = state.today ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day.isCurrentlyInside) "НА РАБОТЕ" else "НЕ НА РАБОТЕ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(day.creditedMinutes.asDuration(), fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Text("зачтено сегодня из ${day.requiredMinutes.asDuration()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (day.isCurrentlyInside) {
                Spacer(Modifier.height(20.dp)); HorizontalDivider()
                MetricRow("Выход по норме", state.normalExit?.asTime() ?: "—")
                MetricRow("Баланс в ноль", state.balanceZeroExit?.asTime() ?: "—")
                MetricRow("По плану месяца", state.recommendedExit?.asTime() ?: "—")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MetricRow("Баланс до сегодня", state.previousBalanceMinutes.asSignedDuration())
            MetricRow("С учётом сегодня", state.balanceIncludingTodayMinutes.asSignedDuration())
            if (state.balanceIncludingTodayMinutes < 0) {
                val hint = if (day.isCurrentlyInside && state.balanceZeroExit != null) {
                    "До нулевого баланса ${(-state.balanceIncludingTodayMinutes).asDuration()}: оставайтесь до ${state.balanceZeroExit.asTime()}"
                } else {
                    "До нулевого баланса не хватает ${(-state.balanceIncludingTodayMinutes).asDuration()}"
                }
                Text(
                    hint,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MetricRow("На территории", day.presenceMinutes.asDuration())
            MetricRow("Вне территории всего", day.outsideMinutes.asDuration())
            MetricRow("Обед вне территории", day.lunchOutsideMinutes.asDuration())
            if (day.deductedLunchMinutes > 0) {
                MetricRow("Обед на территории", day.deductedLunchMinutes.asDuration())
            }
            if (day.extraOutsideMinutes > 0) {
                MetricRow("Доп. отсутствие", day.extraOutsideMinutes.asDuration())
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
                Text(if (event.type == EventType.IN) "Вход" else "Выход", fontWeight = FontWeight.SemiBold)
                Text(event.at.asTime(), style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Default.Edit, "Исправить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    modifier: Modifier = Modifier
) {
    val month = state.month ?: return
    var selectedDay by remember { mutableStateOf<DayResult?>(null) }
    var dayForKind by remember { mutableStateOf<DayResult?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onMonth(state.selectedMonth.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "Предыдущий месяц") }
            Text(state.selectedMonth.asMonthTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onMonth(state.selectedMonth.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "Следующий месяц") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { MonthSummary(month) }
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
            onDismiss = { selectedDay = null },
            onAddInterval = {
                selectedDay = null
                onAddInterval(day.date)
            },
            onEditInterval = { interval ->
                selectedDay = null
                onEditInterval(day.date, interval)
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
            MetricRow("План месяца", month.planMinutes.asDuration())
            MetricRow("Зачтено", month.creditedMinutes.asDuration())
            MetricRow("Баланс на сегодня", month.balanceToDateMinutes.asSignedDuration())
        }
    }
}

@Composable
private fun DayRow(day: DayResult, kind: DayKind?, onClick: () -> Unit) {
    val isFree = day.requiredMinutes == 0L
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isFree) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${day.date.dayOfMonth}, ${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))}", fontWeight = FontWeight.Bold)
                Text(
                    when {
                        kind == DayKind.PLANNED_ABSENCE -> "Не буду — норму отработать заранее"
                        isFree -> "Выходной / особый день"
                        else -> "${day.creditedMinutes.asDuration()} из ${day.requiredMinutes.asDuration()}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Text("Прогноз", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(month.month.asMonthTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { InfoCard("Осталось закрыть", month.remainingMinutes.asDuration(), MaterialTheme.colorScheme.primaryContainer, large = true) }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выполнение месяца", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            InfoCard(
                "Нужно в среднем",
                if (month.remainingWorkDays > 0) "${month.averageMinutesPerRemainingDay.asDuration()} в день\nОсталось рабочих дней: ${month.remainingWorkDays}" else "Рабочих дней не осталось",
                MaterialTheme.colorScheme.secondaryContainer,
                large = true
            )
        }
        item {
            val message = when {
                month.balanceToDateMinutes > 0 -> "Накоплен запас ${month.balanceToDateMinutes.asDuration()}. Его можно использовать для более раннего ухода."
                month.balanceToDateMinutes < 0 -> "Нужно компенсировать ${(-month.balanceToDateMinutes).asDuration()} в оставшиеся рабочие дни."
                else -> "Идёте точно по месячному плану."
            }
            InfoCard("Текущий темп", message, MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    MetricRow("План", month.planMinutes.asDuration())
                    MetricRow("Зачтено", month.creditedMinutes.asDuration())
                    MetricRow("Осталось", month.remainingMinutes.asDuration())
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Статистика месяца", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    MetricRow("На территории", month.presenceMinutes.asDuration())
                    MetricRow("Вне территории", month.outsideMinutes.asDuration())
                    MetricRow("Обед вне территории", month.lunchOutsideMinutes.asDuration())
                    MetricRow("Обед на территории", month.deductedLunchMinutes.asDuration())
                    MetricRow("Доп. отсутствие", month.extraOutsideMinutes.asDuration())
                    MetricRow("Дней с отметками", month.workedDays.toString())
                    MetricRow("Среднее за день", month.averageCreditedPerWorkedDay.asDuration())
                }
            }
        }
        item {
            InfoCard(
                "Планируемые отсутствия",
                "Отметьте в календаре «Не буду, отработаю заранее». Месячная норма сохранится, а среднее будет рассчитано только по доступным дням.",
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: WorkUiState,
    onSave: (String, Int, Int, Boolean, Int, Boolean) -> Unit,
    onOpenProject: () -> Unit,
    onCheckUpdates: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    fileMessage: String?,
    modifier: Modifier = Modifier
) {
    var name by remember(state.workplaceName) { mutableStateOf(state.workplaceName) }
    var workHours by remember(state.schedule.workMinutes) { mutableStateOf((state.schedule.workMinutes / 60).toString()) }
    var workMinutes by remember(state.schedule.workMinutes) { mutableStateOf((state.schedule.workMinutes % 60).toString()) }
    var lunchMinutes by remember(state.schedule.lunchMinutes) { mutableStateOf(state.schedule.lunchMinutes.toString()) }
    var reminderEnabled by remember(state.lunchReminderEnabled) { mutableStateOf(state.lunchReminderEnabled) }
    var reminderLead by remember(state.lunchReminderLeadMinutes) { mutableIntStateOf(state.lunchReminderLeadMinutes) }
    var automaticUpdateCheckEnabled by remember(state.automaticUpdateCheckEnabled) {
        mutableStateOf(state.automaticUpdateCheckEnabled)
    }
    var showInstructions by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название работы") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Text("Готовые режимы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    workHours = "8"; workMinutes = "0"; lunchMinutes = "60"
                }, modifier = Modifier.weight(1f)) { Text("8 ч + 1 ч") }
                OutlinedButton(onClick = {
                    workHours = "4"; workMinutes = "0"; lunchMinutes = "30"
                }, modifier = Modifier.weight(1f)) { Text("4 ч + 30 мин") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Свой график", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(workHours, { workHours = it.filter(Char::isDigit).take(2) }, label = { Text("Часы") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(workMinutes, { workMinutes = it.filter(Char::isDigit).take(2) }, label = { Text("Минуты") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(lunchMinutes, { lunchMinutes = it.filter(Char::isDigit).take(3) }, label = { Text("Обязательный обед, минут") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Напоминание об обеде", fontWeight = FontWeight.Bold)
                            Text("Пока вы отмечены за территорией", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    }
                    if (reminderEnabled) {
                        Text("Предупредить заранее")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (reminderLead == 10) Button(onClick = { reminderLead = 10 }, modifier = Modifier.weight(1f)) { Text("10 минут") }
                            else OutlinedButton(onClick = { reminderLead = 10 }, modifier = Modifier.weight(1f)) { Text("10 минут") }
                            if (reminderLead == 15) Button(onClick = { reminderLead = 15 }, modifier = Modifier.weight(1f)) { Text("15 минут") }
                            else OutlinedButton(onClick = { reminderLead = 15 }, modifier = Modifier.weight(1f)) { Text("15 минут") }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showInstructions = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.HelpOutline, null)
                Text("  Инструкция")
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("О приложении", fontWeight = FontWeight.Bold)
                    MetricRow("Версия", BuildConfig.VERSION_NAME)
                    Text("Автор проекта: Максим (MaxStriX324)")
                    Text(
                        "Исходный код, история изменений и установочные APK публикуются на GitHub.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenProject, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть проект на GitHub")
                    }
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        enabled = !state.checkingForUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.checkingForUpdates) "Проверяем…" else "Проверить обновления")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Автоматическая проверка", fontWeight = FontWeight.SemiBold)
                            Text(
                                "При запуске, не чаще одного раза в сутки. Рабочие данные не отправляются.",
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
        item {
            Button(onClick = {
                val totalWork = (workHours.toIntOrNull() ?: 0) * 60 + (workMinutes.toIntOrNull() ?: 0)
                onSave(
                    name,
                    totalWork,
                    lunchMinutes.toIntOrNull() ?: 0,
                    reminderEnabled,
                    reminderLead,
                    automaticUpdateCheckEnabled
                )
            }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Сохранить настройки") }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Резервная копия и отчёты", fontWeight = FontWeight.Bold)
                    Text(
                        "JSON полностью сохраняет данные приложения. CSV содержит расчёт по выбранному в календаре месяцу.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                        Text("Экспорт резервной копии JSON")
                    }
                    OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                        Text("Восстановить из JSON")
                    }
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Text("Экспорт месяца ${state.selectedMonth.asMonthTitle()} в CSV")
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
        title = { Text("Как пользоваться WorkBalance") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InstructionSection(
                    "1. Вход и выход",
                    "При проходе на территорию нажмите «Вошёл», при любом выходе — «Вышел». Повторный вход продолжит расчёт этого же дня."
                )
                InstructionSection(
                    "2. Обед",
                    "Время вне территории автоматически покрывает обязательный обед. Если выходы короче нормы, недостающая часть вычитается из присутствия. Всё сверх нормы показывается как дополнительное отсутствие."
                )
                InstructionSection(
                    "3. Исправление отметок",
                    "Нажмите отметку на главном экране или выберите день в календаре. Можно изменить время, удалить ошибочную запись или добавить пропущенный интервал."
                )
                InstructionSection(
                    "4. Баланс",
                    "«Баланс до сегодня» — сумма прошлых дней. «С учётом сегодня» меняется в реальном времени. Время «Баланс в ноль» показывает, когда сегодняшний день полностью компенсирует накопленный минус."
                )
                InstructionSection(
                    "5. План месяца",
                    "В календаре можно отметить день «Не буду, отработаю заранее». Его норма останется в месячном плане, но часы распределятся между доступными днями."
                )
                InstructionSection(
                    "6. Резервная копия",
                    "Перед обновлением или переносом телефона сохраните JSON. CSV предназначен для сверки выбранного месяца с выгрузкой проходной."
                )
                InstructionSection(
                    "7. Быстрый доступ",
                    "Добавьте виджет на домашний экран или плитку WorkBalance в шторку Android. Все кнопки используют одну базу данных."
                )
                InstructionSection(
                    "8. Обновления",
                    "В разделе «О приложении» можно открыть GitHub и проверить новую версию. Автоматическая проверка выполняется при запуске не чаще одного раза в сутки и не отправляет рабочие отметки."
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
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
    onDismiss: () -> Unit,
    onAddInterval: () -> Unit,
    onEditInterval: (WorkIntervalUi) -> Unit,
    onChangeKind: () -> Unit
) {
    val intervals = remember(day.events) { pairIntervals(day.events) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.asDate()) },
        text = {
            Column(
                Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(kind.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        MetricRow("На территории", day.presenceMinutes.asDuration())
                        MetricRow("Вне территории", day.outsideMinutes.asDuration())
                        MetricRow("Обед вне территории", day.lunchOutsideMinutes.asDuration())
                        MetricRow("Обед на территории", day.deductedLunchMinutes.asDuration())
                        if (day.extraOutsideMinutes > 0) {
                            MetricRow("Доп. отсутствие", day.extraOutsideMinutes.asDuration())
                        }
                        MetricRow("Зачтено", day.creditedMinutes.asDuration())
                        MetricRow("Баланс дня", day.balanceMinutes.asSignedDuration())
                    }
                }
                Text("Рабочие интервалы", fontWeight = FontWeight.Bold)
                if (intervals.isEmpty()) {
                    Text("Интервалов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                "${interval.start.at.asTime()} — ${interval.end?.at?.asTime() ?: "не закрыт"}",
                                Modifier.weight(1f).padding(start = 12.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.Edit, "Исправить")
                        }
                    }
                }
                if (day.warnings.isNotEmpty()) {
                    Text(
                        day.warnings.joinToString("\n"),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(onClick = onAddInterval, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Text(" Добавить интервал")
                }
                OutlinedButton(onClick = onChangeKind, modifier = Modifier.fillMaxWidth()) {
                    Text("Тип дня: ${kind.title}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
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
        title = { Text(if (initial == null) "Добавить интервал" else "Исправить интервал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date.asDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> start = LocalTime.of(hour, minute) }, start.hour, start.minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Вход: ${LocalDateTime.of(date, start).asTime()}") }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> end = LocalTime.of(hour, minute) }, end.hour, end.minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Выход: ${LocalDateTime.of(date, end).asTime()}") }
                if (!valid) {
                    Text("Выход должен быть позже входа", color = MaterialTheme.colorScheme.error)
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Text(" Удалить интервал")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(LocalDateTime.of(date, start), LocalDateTime.of(date, end)) },
                enabled = valid
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
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
                        kind.title,
                        Modifier.fillMaxWidth().clickable { onSelect(kind) }.padding(vertical = 11.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
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
        title = { Text(if (initial == null) "Добавить отметку" else "Исправить отметку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (type == EventType.IN) Button(onClick = { type = EventType.IN }, modifier = Modifier.weight(1f)) { Text("Вход") }
                    else OutlinedButton(onClick = { type = EventType.IN }, modifier = Modifier.weight(1f)) { Text("Вход") }
                    if (type == EventType.OUT) Button(onClick = { type = EventType.OUT }, modifier = Modifier.weight(1f)) { Text("Выход") }
                    else OutlinedButton(onClick = { type = EventType.OUT }, modifier = Modifier.weight(1f)) { Text("Выход") }
                }
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Дата: ${date.asDate()}") }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, true).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Время: ${LocalDateTime.of(date, time).asTime()}") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null); Text(" Удалить отметку")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(LocalDateTime.of(date, time), type) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
