package ru.maxstrix.workbalance.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private enum class AppPage(val label: String) {
    TODAY("Сегодня"), CALENDAR("Календарь"), FORECAST("Прогноз"), SETTINGS("Настройки")
}

@Composable
fun WorkBalanceApp(viewModel: WorkViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by remember { mutableStateOf(AppPage.TODAY) }
    var editedEvent by remember { mutableStateOf<WorkEvent?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

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
                    modifier = Modifier.padding(padding)
                )
                AppPage.FORECAST -> ForecastScreen(state, Modifier.padding(padding))
                AppPage.SETTINGS -> SettingsScreen(
                    state = state,
                    onSchedule = viewModel::setSchedule,
                    onName = viewModel::setWorkplaceName,
                    onReminder = viewModel::setLunchReminder,
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
                MetricRow("Минимальный выход", state.earliestExit?.asTime() ?: "—")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MetricRow("Баланс до сегодня", state.previousBalanceMinutes.asSignedDuration())
            MetricRow("Обед вне территории", day.outsideMinutes.asDuration())
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
    modifier: Modifier = Modifier
) {
    val month = state.month ?: return
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
                DayRow(day) { dayForKind = day }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
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
private fun DayRow(day: DayResult, onClick: () -> Unit) {
    val isFree = day.requiredMinutes == 0L
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isFree) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${day.date.dayOfMonth}, ${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))}", fontWeight = FontWeight.Bold)
                Text(if (isFree) "Выходной / особый день" else "${day.creditedMinutes.asDuration()} из ${day.requiredMinutes.asDuration()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Прогноз", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(month.month.asMonthTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { InfoCard("Осталось закрыть", month.remainingMinutes.asDuration(), MaterialTheme.colorScheme.primaryContainer, large = true) }
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
    }
}

@Composable
private fun SettingsScreen(
    state: WorkUiState,
    onSchedule: (Int, Int) -> Unit,
    onName: (String) -> Unit,
    onReminder: (Boolean, Int) -> Unit,
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название работы") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Text("Готовые режимы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    workHours = "8"; workMinutes = "0"; lunchMinutes = "60"; onSchedule(480, 60)
                }, modifier = Modifier.weight(1f)) { Text("8 ч + 1 ч") }
                OutlinedButton(onClick = {
                    workHours = "4"; workMinutes = "0"; lunchMinutes = "30"; onSchedule(240, 30)
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
            Button(onClick = {
                val totalWork = (workHours.toIntOrNull() ?: 0) * 60 + (workMinutes.toIntOrNull() ?: 0)
                onName(name)
                onSchedule(totalWork, lunchMinutes.toIntOrNull() ?: 0)
                onReminder(reminderEnabled, reminderLead)
            }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Сохранить") }
        }
        item {
            InfoCard("Как считается обед", "Из нахождения на территории вычитается только та часть обязательного обеда, которую вы не провели за территорией.", MaterialTheme.colorScheme.surfaceVariant)
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
private fun balanceColor(minutes: Long): Color = when {
    minutes > 0 -> Color(0xFF237A3B)
    minutes < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
