package com.example

import android.content.Context
import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.PdfExporter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = OTRepository(database)
        val viewModelFactory = OTViewModelFactory(repository)

        setContent {
            val viewModel: OTViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = viewModelFactory)
            val darkThemeSetting by viewModel.darkThemeSetting.collectAsState()
            val useDarkTheme = when (darkThemeSetting) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = useDarkTheme) {
                OTTrackerApp(viewModel)
            }
        }
    }
}

@Composable
fun OTTrackerApp(viewModel: OTViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val weekLabel by viewModel.weekLabelSetting.collectAsState()

    val isDark = com.example.ui.theme.LocalDarkTheme.current
    val colorHeaderBg = if (isDark) Color(0xFF211F26) else Color(0xFFF7F2FA)
    val colorTextPrimary = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B20)
    val colorTextSecondary = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
    val colorBg = if (isDark) Color(0xFF141218) else Color(0xFFFDF8FD)
    val colorAccent = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
    val colorAccentBg = if (isDark) Color(0xFF4F378B) else Color(0xFFE8DEF8)
    val colorAccentText = if (isDark) Color(0xFFEADDFF) else Color(0xFF1D192B)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorHeaderBg) // High Density Theme Header
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ OT Tracker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = colorTextPrimary // Deep dark Slate/Purple or Light Purple
                    )
                    if (weekLabel.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorAccentBg), // Theme-derived pill container
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Week",
                                    tint = colorAccent, // Theme-derived Icon
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = weekLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorAccentText
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = colorHeaderBg, // Dynamic bottom bar background
                modifier = Modifier.navigationBarsPadding()
            ) {
                val tabs = listOf(
                    Triple("Tracker", Icons.Default.List, 0),
                    Triple("Calendar", Icons.Default.DateRange, 1),
                    Triple("Summary", Icons.Default.Info, 2),
                    Triple("Workers", Icons.Default.Person, 3),
                    Triple("Settings", Icons.Default.Settings, 4)
                )
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colorAccentText,
                            selectedTextColor = colorAccentText,
                            unselectedIconColor = colorTextSecondary,
                            unselectedTextColor = colorTextSecondary,
                            indicatorColor = colorAccentBg
                        )
                    )
                }
            }
        },
        containerColor = colorBg // Dynamic background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TrackerScreen(viewModel)
                1 -> CalendarScreen(viewModel)
                2 -> SummaryScreen(viewModel)
                3 -> WorkersScreen(viewModel)
                4 -> SettingsScreen(viewModel)
            }
        }
    }
}

// ── TRACKER SCREEN ──
@Composable
fun TrackerScreen(viewModel: OTViewModel) {
    val context = LocalContext.current
    val trackerDate by viewModel.trackerDate.collectAsState()
    val searchQuery by viewModel.trackerSearchQuery.collectAsState()
    val shiftFilter by viewModel.trackerShiftFilter.collectAsState()
    val sectionFilter by viewModel.trackerSectionFilter.collectAsState()

    val workers by viewModel.workers.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()

    // Get unique sections for dropdown
    val uniqueSections = remember(workers) {
        listOf("All") + workers.map { it.section }.distinct().sorted()
    }

    // Filter workers
    val filteredWorkers = remember(workers, searchQuery, shiftFilter, sectionFilter) {
        workers.filter { worker ->
            val matchesSearch = worker.name.contains(searchQuery, ignoreCase = true)
            val matchesShift = shiftFilter == "All" || worker.shift == shiftFilter
            val matchesSection = sectionFilter == "All" || worker.section == sectionFilter
            matchesSearch && matchesShift && matchesSection
        }
    }

    // Group filtered workers by section
    val groupedWorkers = remember(filteredWorkers) {
        filteredWorkers.groupBy { it.section }
    }

    // Overtime logs for the selected date
    val dayLogs = remember(allLogs, trackerDate) {
        allLogs.filter { it.date == trackerDate }.associateBy { it.workerId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Date and Search Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showDatePicker(context, trackerDate) { viewModel.setTrackerDate(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = Color(0xFF6750A4))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = formatDateDisplay(trackerDate), color = Color(0xFF1D192B), fontWeight = FontWeight.Bold)
            }
        }

        // Search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setTrackerSearchQuery(it) },
            placeholder = { Text("🔍 Search worker...", color = Color(0xFF49454F)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF1D1B20),
                unfocusedTextColor = Color(0xFF1D1B20),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF6750A4),
                unfocusedBorderColor = Color(0xFFCAC4D0)
            ),
            singleLine = true
        )

        // Filters row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift Filters
            listOf("All", "Day", "Night").forEach { shift ->
                val isSelected = shiftFilter == shift
                val label = when (shift) {
                    "Day" -> "☀️ Day"
                    "Night" -> "🌙 Night"
                    else -> "All"
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFF6750A4) else Color(0xFFE7E0EC),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.setTrackerShiftFilter(shift) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF49454F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Section Filter Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sec: " + if (sectionFilter == "All") "All" else sectionFilter.take(8) + "..",
                        fontSize = 11.sp,
                        color = Color(0xFF1D192B)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.size(16.dp), tint = Color(0xFF6750A4))
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFFF3EDF7))
                ) {
                    uniqueSections.forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(sec, color = Color(0xFF1D1B20), fontSize = 13.sp) },
                            onClick = {
                                viewModel.setTrackerSectionFilter(sec)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (workers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👷", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No workers registered yet", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.selectTab(3) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Add Workers Now")
                    }
                }
            }
        } else if (groupedWorkers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching workers found.", color = Color(0xFF64748B))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                groupedWorkers.forEach { (section, sectionWorkers) ->
                    item {
                        SectionHeader(section = section)
                    }
                    items(sectionWorkers, key = { it.id }) { worker ->
                        val log = dayLogs[worker.id]
                        val isChecked = log?.isChecked == true
                        val mins = log?.mins ?: 0
                        val note = log?.note ?: ""

                        TrackerWorkerRow(
                            worker = worker,
                            isChecked = isChecked,
                            mins = mins,
                            note = note,
                            onToggle = { viewModel.toggleOvertime(trackerDate, worker.id) },
                            onShiftToggle = { newShift -> viewModel.updateWorkerShiftDirect(worker.id, newShift) },
                            onMinsAdjust = { delta -> viewModel.adjustOvertimeMinutes(trackerDate, worker.id, delta) },
                            onNoteChange = { newNote -> viewModel.updateOvertimeNote(trackerDate, worker.id, newNote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(section: String) {
    val isQuality = isQualitySection(section)
    val icon = if (isQuality) "⛑️" else "🛠️"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3EDF7), RoundedCornerShape(8.dp)) // Soft purple header background
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF6750A4), RoundedCornerShape(4.dp)) // Deep primary purple tag
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("SEC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$icon $section",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20)
        )
    }
}

@Composable
fun TrackerWorkerRow(
    worker: Worker,
    isChecked: Boolean,
    mins: Int,
    note: String,
    onToggle: () -> Unit,
    onShiftToggle: (String) -> Unit,
    onMinsAdjust: (Int) -> Unit,
    onNoteChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isQuality = isQualitySection(worker.section)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) Color(0xFFFEF7FF) else Color.White
        ),
        border = BorderStroke(1.dp, if (isChecked) Color(0xFF6750A4) else Color(0xFFEADDFF)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tick box / Checkbox
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isChecked) Color(0xFF6750A4) else Color.Transparent)
                        .border(2.dp, if (isChecked) Color(0xFF6750A4) else Color(0xFFCAC4D0), RoundedCornerShape(6.dp))
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(Icons.Default.Check, contentDescription = "Active", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Shift toggle beside tick box (Custom High Density Pill Toggle)
                Row(
                    modifier = Modifier
                        .background(Color(0xFFE7E0EC), RoundedCornerShape(16.dp))
                        .padding(2.dp)
                        .clickable {
                            val nextShift = if (worker.shift == "Day") "Night" else "Day"
                            onShiftToggle(nextShift)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (worker.shift == "Day") Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (worker.shift == "Day") Color(0xFF6750A4) else Color(0xFF49454F)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(if (worker.shift == "Night") Color(0xFF1D1B20) else Color.Transparent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (worker.shift == "Night") Color.White else Color(0xFF49454F)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Worker Name and Section
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isQuality) {
                            // Custom white helmet indicator as requested by the user
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(Color.White, CircleShape)
                                    .border(1.dp, Color(0xFFCAC4D0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = "White Helmet",
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = worker.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = worker.section,
                        fontSize = 10.sp,
                        color = Color(0xFF49454F)
                    )
                }

                if (isChecked && mins > 0) {
                    Text(
                        text = formatHours(mins),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Details",
                        tint = Color(0xFF6750A4)
                    )
                }
            }

            // Expandable details (adjust minutes and notes)
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFFEADDFF)) // Soft border/divider color
                Spacer(modifier = Modifier.height(8.dp))

                Text("⏱ Overtime Adjuster", fontSize = 11.sp, color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onMinsAdjust(-30) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7), contentColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF3EDF7), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatHours(mins),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6750A4)
                        )
                    }

                    Button(
                        onClick = { onMinsAdjust(30) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7), contentColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { onNoteChange(it) },
                    placeholder = { Text("📝 Add note...", fontSize = 12.sp, color = Color(0xFF49454F)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1D1B20)),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
            }
        }
    }
}

// ── CALENDAR SCREEN ──
@Composable
fun CalendarScreen(viewModel: OTViewModel) {
    val monthOffset by viewModel.calendarMonthOffset.collectAsState()
    val selectedDate by viewModel.calendarSelectedDate.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val workers by viewModel.workers.collectAsState()

    val currentMonthCalendar = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val year = currentMonthCalendar.get(Calendar.YEAR)
    val month = currentMonthCalendar.get(Calendar.MONTH)
    val daysInMonth = currentMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = currentMonthCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed Sun-Sat

    val sdfMonthName = SimpleDateFormat("MMMM yyyy", Locale.US)
    val monthLabel = sdfMonthName.format(currentMonthCalendar.time)

    // Log counts for calendar dots
    val logCounts = remember(allLogs) {
        allLogs.filter { it.isChecked && it.mins > 0 }
            .groupBy { it.date }
            .mapValues { it.value.size }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Month Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateCalendarMonth(-1) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Prev Month", tint = Color(0xFF6750A4))
            }
            Text(
                text = monthLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1D1B20)
            )
            IconButton(onClick = { viewModel.navigateCalendarMonth(1) }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = Color(0xFF6750A4))
            }
        }

        // Week Headers
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Calendar Grid
        val totalCells = daysInMonth + firstDayOfWeek
        val rows = (totalCells + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0..6) {
                        val index = r * 7 + c
                        val dayNum = index - firstDayOfWeek + 1
                        val isValidDay = dayNum in 1..daysInMonth

                        val cellDate = if (isValidDay) {
                            String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                        } else ""

                        val hasOT = isValidDay && (logCounts[cellDate] ?: 0) > 0
                        val isSelected = cellDate == selectedDate

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFF6750A4)
                                    else if (hasOT) Color(0xFFE8DEF8)
                                    else Color.White
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isValidDay) {
                                    viewModel.selectCalendarDate(cellDate)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isValidDay) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color(0xFF1D1B20)
                                    )
                                    if (hasOT) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(5.dp)
                                                .background(Color(0xFF6750A4), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Day Details
        Text(
            text = "📋 Overtime on " + formatDateDisplay(selectedDate),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val activeLogs = remember(allLogs, selectedDate) {
            allLogs.filter { it.date == selectedDate && it.isChecked && it.mins > 0 }
        }

        if (activeLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No overtime recorded for this date.", color = Color(0xFF49454F), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(activeLogs) { log ->
                    val worker = workers.find { it.id == log.workerId }
                    if (worker != null) {
                        val isQual = isQualitySection(worker.section)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEADDFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isQual) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(Color.White, CircleShape)
                                                    .border(1.dp, Color(0xFFCAC4D0), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Build,
                                                    contentDescription = "White Helmet",
                                                    tint = Color(0xFF6750A4),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(worker.name, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 13.sp)
                                    }
                                    Text(worker.section, color = Color(0xFF49454F), fontSize = 10.sp)
                                    if (log.note.isNotEmpty()) {
                                        Text("📝 Note: ${log.note}", color = Color(0xFF6750A4), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                Text(
                                    text = formatHours(log.mins),
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF6750A4),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── SUMMARY SCREEN ──
@Composable
fun SummaryScreen(viewModel: OTViewModel) {
    val context = LocalContext.current
    val fromDate by viewModel.summaryFromDate.collectAsState()
    val toDate by viewModel.summaryToDate.collectAsState()
    val weekLabel by viewModel.weekLabelSetting.collectAsState()

    val workers by viewModel.workers.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()

    // Calculate logs within fromDate and toDate
    val filteredLogs = remember(allLogs, fromDate, toDate) {
        allLogs.filter { it.date in fromDate..toDate && it.isChecked }
    }

    // Process worker summaries: display workers with checked logs in the filtered range
    val workerSummaries = remember(workers, filteredLogs) {
        workers.map { worker ->
            val workerLogs = filteredLogs.filter { it.workerId == worker.id }
            val totalMins = workerLogs.sumOf { it.mins }
            Triple(worker, totalMins, workerLogs)
        }.filter { it.third.isNotEmpty() }
    }

    val totalHrs = remember(workerSummaries) {
        workerSummaries.sumOf { it.second } / 60
    }
    val otDaysCount = remember(filteredLogs) {
        filteredLogs.map { it.date }.distinct().size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Date Range Selectors (From Date and To Date)
        // "summary la filter pannura option venum intha date la erunthu intha date varikum nu"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showDatePicker(context, fromDate) { viewModel.setSummaryFromDate(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(text = "From: " + formatDateDisplay(fromDate), fontSize = 11.sp, color = Color(0xFF1D192B), fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showDatePicker(context, toDate) { viewModel.setSummaryToDate(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(text = "To: " + formatDateDisplay(toDate), fontSize = 11.sp, color = Color(0xFF1D192B), fontWeight = FontWeight.Bold)
            }
        }

        // Share Options Buttons (WhatsApp and PDF)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    PdfExporter.shareReportToWhatsApp(
                        context = context,
                        weekLabel = weekLabel,
                        fromDate = fromDate,
                        toDate = toDate,
                        workers = workers,
                        logs = allLogs
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }

            Button(
                onClick = {
                    PdfExporter.exportReportToPdfAndShare(
                        context = context,
                        weekLabel = weekLabel,
                        fromDate = fromDate,
                        toDate = toDate,
                        workers = workers,
                        logs = allLogs
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)), // Rose Red
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Export PDF", modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PDF Report", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }

        // Stats Dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryStatCard(
                value = workerSummaries.size.toString(),
                label = "Workers",
                color = Color(0xFF6750A4),
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                value = "${totalHrs}h",
                label = "Total Hours",
                color = Color(0xFF6750A4),
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                value = otDaysCount.toString(),
                label = "Active Days",
                color = Color(0xFF6750A4),
                modifier = Modifier.weight(1f)
            )
        }

        if (workerSummaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No overtime recorded for the selected range.", color = Color(0xFF49454F), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(workerSummaries) { (worker, totalMins, workerLogs) ->
                    val isQual = isQualitySection(worker.section)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEADDFF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isQual) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(Color.White, CircleShape)
                                                    .border(1.dp, Color(0xFFCAC4D0), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Build,
                                                    contentDescription = "White Helmet",
                                                    tint = Color(0xFF6750A4),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(worker.name, fontWeight = FontWeight.Black, color = Color(0xFF1D1B20), fontSize = 14.sp)
                                    }
                                    Text(worker.section, color = Color(0xFF49454F), fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatHours(totalMins),
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF6750A4),
                                        fontSize = 15.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (worker.shift == "Day") Color(0xFFE8DEF8) else Color(0xFF1D1B20),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (worker.shift == "Day") "☀️ Day" else "🌙 Night",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (worker.shift == "Day") Color(0xFF6750A4) else Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Individual Dates
                            workerLogs.sortedBy { it.date }.forEach { e ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.dp, Color.Transparent)
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📅 " + formatDateDisplay(e.date), fontSize = 12.sp, color = Color(0xFF1D1B20), fontWeight = FontWeight.Bold)
                                    Text(formatHours(e.mins), fontSize = 11.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                                    if (e.note.isNotEmpty()) {
                                        Text("📝 ${e.note}", fontSize = 11.sp, color = Color(0xFF6750A4), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEADDFF)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 10.sp, color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
        }
    }
}

// ── WORKERS SCREEN ──
@Composable
fun WorkersScreen(viewModel: OTViewModel) {
    val context = LocalContext.current
    val workers by viewModel.workers.collectAsState()
    val extractionState by viewModel.extractionState.collectAsState()

    var nName by remember { mutableStateOf("") }
    var nSec by remember { mutableStateOf("") }
    var nShift by remember { mutableStateOf("Day") }

    var editingWorkerId by remember { mutableStateOf<String?>(null) }
    var eName by remember { mutableStateOf("") }
    var eSec by remember { mutableStateOf("") }
    var eShift by remember { mutableStateOf("Day") }

    var mSearch by remember { mutableStateOf("") }

    // Roster file picker
    val rosterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.mapNotNull { uri ->
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(stream)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmaps.isNotEmpty()) {
                viewModel.extractRosterFromImages(bitmaps)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // AI Import Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)), // Soft purple container
            border = BorderStroke(1.dp, Color(0xFFEADDFF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🤖 AI Week Roster Import",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upload 1 or 2 images of the 'Workers Shift Allocation' roster to automatically extract and register all workers and sections.",
                    fontSize = 11.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(10.dp))

                when (extractionState) {
                    is ExtractionState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF6750A4), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Processing roster. Please wait...", fontSize = 11.sp, color = Color(0xFF6750A4))
                        }
                    }
                    is ExtractionState.Success -> {
                        val state = extractionState as ExtractionState.Success
                        Text(
                            text = "✅ Successfully imported ${state.count} workers! Week: ${state.weekLabel}",
                            fontSize = 11.sp,
                            color = Color(0xFF6750A4),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    is ExtractionState.Error -> {
                        val state = extractionState as ExtractionState.Error
                        Text(
                            text = "❌ Error: ${state.message}",
                            fontSize = 11.sp,
                            color = Color(0xFFBA1A1A),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    else -> {}
                }

                Button(
                    onClick = {
                        viewModel.clearExtractionState()
                        rosterPickerLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload Images", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select 1-2 Roster Photos", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Add Worker Form
        Text(text = "➕ Add Worker Manually", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), modifier = Modifier.padding(vertical = 4.dp))
        OutlinedTextField(
            value = nName,
            onValueChange = { nName = it },
            placeholder = { Text("Worker name (e.g. RAJESH KUMAR)", color = Color(0xFF49454F)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF1D1B20),
                unfocusedTextColor = Color(0xFF1D1B20),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF6750A4),
                unfocusedBorderColor = Color(0xFFCAC4D0)
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = nSec,
            onValueChange = { nSec = it },
            placeholder = { Text("Section (e.g. GRINDING SEC)", color = Color(0xFF49454F)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF1D1B20),
                unfocusedTextColor = Color(0xFF1D1B20),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF6750A4),
                unfocusedBorderColor = Color(0xFFCAC4D0)
            ),
            singleLine = true
        )

        // Shift dropdown inside Manual registration
        var isShiftDropdownExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Button(
                onClick = { isShiftDropdownExpanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1D1B20)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Text(
                    text = if (nShift == "Day") "☀️ Day Shift" else "🌙 Night Shift",
                    color = Color(0xFF1D1B20)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color(0xFF6750A4))
            }
            DropdownMenu(
                expanded = isShiftDropdownExpanded,
                onDismissRequest = { isShiftDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF3EDF7))
            ) {
                DropdownMenuItem(
                    text = { Text("☀️ Day Shift", color = Color(0xFF1D1B20)) },
                    onClick = { nShift = "Day"; isShiftDropdownExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("🌙 Night Shift", color = Color(0xFF1D1B20)) },
                    onClick = { nShift = "Night"; isShiftDropdownExpanded = false }
                )
            }
        }

        Button(
            onClick = {
                if (nName.trim().isEmpty() || nSec.trim().isEmpty()) {
                    Toast.makeText(context, "Please enter both name and section!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addWorker(nName, nSec, nShift)
                    nName = ""
                    nSec = ""
                    Toast.makeText(context, "Worker added!", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register Worker", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Divider(color = Color(0xFFEADDFF))
        Spacer(modifier = Modifier.height(10.dp))

        // Search Workers List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Roster Workers (${workers.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
            TextButton(onClick = { viewModel.clearAllWorkers() }) {
                Text("Clear All", color = Color(0xFFBA1A1A), fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = mSearch,
            onValueChange = { mSearch = it },
            placeholder = { Text("🔍 Search registered roster...", color = Color(0xFF49454F)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF1D1B20),
                unfocusedTextColor = Color(0xFF1D1B20),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF6750A4),
                unfocusedBorderColor = Color(0xFFCAC4D0)
            ),
            singleLine = true
        )

        val filteredRoster = workers.filter {
            it.name.contains(mSearch, ignoreCase = true) || it.section.contains(mSearch, ignoreCase = true)
        }

        if (filteredRoster.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No workers registered.", color = Color(0xFF49454F))
            }
        } else {
            // Group by section inside worker management
            val rosterGrouped = filteredRoster.groupBy { it.section }
            rosterGrouped.forEach { (sec, ws) ->
                SectionHeader(section = sec)
                Spacer(modifier = Modifier.height(4.dp))
                ws.forEach { worker ->
                    val isQual = isQualitySection(worker.section)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEADDFF)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isQual) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(Color.White, CircleShape)
                                                .border(1.dp, Color(0xFFCAC4D0), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = "White Helmet",
                                                tint = Color(0xFF6750A4),
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(worker.name, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 13.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(worker.section, color = Color(0xFF49454F), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (worker.shift == "Day") Color(0xFFE8DEF8) else Color(0xFF1D1B20),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = worker.shift,
                                            fontSize = 8.sp,
                                            color = if (worker.shift == "Day") Color(0xFF6750A4) else Color.White
                                        )
                                    }
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingWorkerId = worker.id
                                        eName = worker.name
                                        eSec = worker.section
                                        eShift = worker.shift
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteWorker(worker.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFBA1A1A), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Edit Dialog
    if (editingWorkerId != null) {
        Dialog(onDismissRequest = { editingWorkerId = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("✏️ Edit Worker Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = eName,
                        onValueChange = { eName = it },
                        placeholder = { Text("Name", color = Color(0xFF49454F)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1D1B20),
                            unfocusedTextColor = Color(0xFF1D1B20),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF6750A4),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = eSec,
                        onValueChange = { eSec = it },
                        placeholder = { Text("Section", color = Color(0xFF49454F)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1D1B20),
                            unfocusedTextColor = Color(0xFF1D1B20),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF6750A4),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        singleLine = true
                    )

                    var isEditShiftDropdownExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Button(
                            onClick = { isEditShiftDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1D1B20)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                        ) {
                            Text(if (eShift == "Day") "☀️ Day Shift" else "🌙 Night Shift", color = Color(0xFF1D1B20))
                        }
                        DropdownMenu(
                            expanded = isEditShiftDropdownExpanded,
                            onDismissRequest = { isEditShiftDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF3EDF7))
                        ) {
                            DropdownMenuItem(
                                text = { Text("☀️ Day Shift", color = Color(0xFF1D1B20)) },
                                onClick = { eShift = "Day"; isEditShiftDropdownExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🌙 Night Shift", color = Color(0xFF1D1B20)) },
                                onClick = { eShift = "Night"; isEditShiftDropdownExpanded = false }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { editingWorkerId = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color(0xFF49454F))
                        }
                        Button(
                            onClick = {
                                val id = editingWorkerId ?: return@Button
                                viewModel.updateWorker(id, eName, eSec, eShift)
                                editingWorkerId = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

// ── SETTINGS SCREEN ──
@Composable
fun SettingsScreen(viewModel: OTViewModel) {
    val context = LocalContext.current
    val weekLabel by viewModel.weekLabelSetting.collectAsState()
    val darkThemeSetting by viewModel.darkThemeSetting.collectAsState()
    val isDark = com.example.ui.theme.LocalDarkTheme.current

    var tempLabel by remember(weekLabel) { mutableStateOf(weekLabel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else Color.White),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF49454F) else Color(0xFFEADDFF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📅 Display Week Label",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This label will display at the top header of the application (e.g., '22.06.2026 to 27.06.2026').",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tempLabel,
                    onValueChange = { tempLabel = it },
                    placeholder = { Text("e.g. 22.06.2026 to 27.06.2026", color = if (isDark) Color(0xFF8B8890) else Color(0xFF49454F)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B20),
                        unfocusedTextColor = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B20),
                        focusedContainerColor = if (isDark) Color(0xFF1D1B20) else Color.White,
                        unfocusedContainerColor = if (isDark) Color(0xFF1D1B20) else Color.White,
                        focusedBorderColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                        unfocusedBorderColor = if (isDark) Color(0xFF49454F) else Color(0xFFCAC4D0)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.saveWeekLabel(tempLabel)
                        Toast.makeText(context, "Week label saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                        contentColor = if (isDark) Color(0xFF381E72) else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Label", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else Color.White),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF49454F) else Color(0xFFEADDFF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎨 UI Theme (Dark Mode)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose light mode, dark mode, or follow system default settings.",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        "system" to "System",
                        "light" to "Light",
                        "dark" to "Dark"
                    )
                    themeOptions.forEach { (key, label) ->
                        val isSelected = darkThemeSetting == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        if (isDark) Color(0xFF4F378B) else Color(0xFFE8DEF8)
                                    } else {
                                        if (isDark) Color(0xFF1D1B20) else Color(0xFFF3EDF7)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) {
                                        if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.setDarkThemeSetting(key)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    if (isDark) Color(0xFFEADDFF) else Color(0xFF1D192B)
                                } else {
                                    if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else Color.White),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF8C1D18) else Color(0xFFF9DEDC)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⚠️ Reset Operations",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Be careful! Wiping database tables is irreversible.",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.clearAllOT()
                        Toast.makeText(context, "Overtime log data wiped successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF93000A) else Color(0xFFFFDAD9),
                        contentColor = if (isDark) Color(0xFFFFDAD9) else Color(0xFF410002)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wipe OT Data Only", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.clearAll()
                        Toast.makeText(context, "All data wiped successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFFBA1A1A) else Color(0xFFBA1A1A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hard Reset Everything", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── UTILS ──
fun formatDateDisplay(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val date = parser.parse(dateStr)
        if (date != null) formatter.format(date) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

fun formatHours(mins: Int): String {
    return "${mins / 60}h ${mins % 60}m"
}

fun isQualitySection(section: String): Boolean {
    val s = section.uppercase(Locale.US)
    return s.contains("QUALITY") || s.contains("QC") || s.contains("Q/C") || 
           s.contains("INSPECTION") || s.contains("LAB") || s.contains("INCOMING")
}

fun showDatePicker(context: Context, initialDate: String, onDateSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(initialDate)
        if (date != null) {
            calendar.time = date
        }
    } catch (e: Exception) {}

    val picker = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selected = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(selected)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    picker.show()
}
