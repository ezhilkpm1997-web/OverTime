package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed interface ExtractionState {
    object Idle : ExtractionState
    object Loading : ExtractionState
    data class Success(val count: Int, val weekLabel: String) : ExtractionState
    data class Error(val message: String) : ExtractionState
}

class OTViewModel(private val repository: OTRepository) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Tracker UI states
    private val _trackerDate = MutableStateFlow("")
    val trackerDate: StateFlow<String> = _trackerDate.asStateFlow()

    private val _trackerSearchQuery = MutableStateFlow("")
    val trackerSearchQuery: StateFlow<String> = _trackerSearchQuery.asStateFlow()

    private val _trackerShiftFilter = MutableStateFlow("All") // "All", "Day", "Night"
    val trackerShiftFilter: StateFlow<String> = _trackerShiftFilter.asStateFlow()

    private val _trackerSectionFilter = MutableStateFlow("All")
    val trackerSectionFilter: StateFlow<String> = _trackerSectionFilter.asStateFlow()

    // Summary UI filters
    private val _summaryFromDate = MutableStateFlow("")
    val summaryFromDate: StateFlow<String> = _summaryFromDate.asStateFlow()

    private val _summaryToDate = MutableStateFlow("")
    val summaryToDate: StateFlow<String> = _summaryToDate.asStateFlow()

    // Calendar selected monthoffset
    private val _calendarMonthOffset = MutableStateFlow(0)
    val calendarMonthOffset: StateFlow<Int> = _calendarMonthOffset.asStateFlow()

    private val _calendarSelectedDate = MutableStateFlow("")
    val calendarSelectedDate: StateFlow<String> = _calendarSelectedDate.asStateFlow()

    // Extraction State
    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    // Database Flows
    val workers: StateFlow<List<Worker>> = repository.allWorkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<OvertimeLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings
    private val _weekLabelSetting = MutableStateFlow("")
    val weekLabelSetting: StateFlow<String> = _weekLabelSetting.asStateFlow()

    private val _darkThemeSetting = MutableStateFlow("system")
    val darkThemeSetting: StateFlow<String> = _darkThemeSetting.asStateFlow()

    init {
        // Set default tracker date to today
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        _trackerDate.value = todayStr
        _calendarSelectedDate.value = todayStr

        // Set default summary range (past 30 days)
        val cal = Calendar.getInstance()
        val toDateStr = sdf.format(cal.time)
        _summaryToDate.value = toDateStr
        cal.add(Calendar.DAY_OF_YEAR, -14) // default 14 days
        _summaryFromDate.value = sdf.format(cal.time)

        // Observe week label setting
        viewModelScope.launch {
            repository.getSetting("week_label").collect { setting ->
                _weekLabelSetting.value = setting?.value ?: ""
            }
        }

        // Observe dark theme setting
        viewModelScope.launch {
            repository.getSetting("dark_theme").collect { setting ->
                _darkThemeSetting.value = setting?.value ?: "system"
            }
        }
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setTrackerDate(date: String) {
        _trackerDate.value = date
    }

    fun setTrackerSearchQuery(query: String) {
        _trackerSearchQuery.value = query
    }

    fun setTrackerShiftFilter(shift: String) {
        _trackerShiftFilter.value = shift
    }

    fun setTrackerSectionFilter(section: String) {
        _trackerSectionFilter.value = section
    }

    fun setSummaryFromDate(date: String) {
        _summaryFromDate.value = date
    }

    fun setSummaryToDate(date: String) {
        _summaryToDate.value = date
    }

    fun navigateCalendarMonth(delta: Int) {
        _calendarMonthOffset.value += delta
    }

    fun selectCalendarDate(date: String) {
        _calendarSelectedDate.value = date
    }

    fun clearExtractionState() {
        _extractionState.value = ExtractionState.Idle
    }

    // Worker Actions
    fun addWorker(name: String, section: String, shift: String) {
        viewModelScope.launch {
            val id = "w_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            repository.insertWorker(Worker(id, name.trim().uppercase(Locale.US), section.trim().uppercase(Locale.US), shift))
        }
    }

    fun updateWorker(id: String, name: String, section: String, shift: String) {
        viewModelScope.launch {
            repository.insertWorker(Worker(id, name.trim().uppercase(Locale.US), section.trim().uppercase(Locale.US), shift))
        }
    }

    fun updateWorkerShiftDirect(id: String, shift: String) {
        viewModelScope.launch {
            val currentWorkers = workers.value
            val worker = currentWorkers.find { it.id == id }
            if (worker != null) {
                repository.insertWorker(worker.copy(shift = shift))
            }
        }
    }

    fun deleteWorker(id: String) {
        viewModelScope.launch {
            repository.deleteWorkerById(id)
        }
    }

    fun clearAllWorkers() {
        viewModelScope.launch {
            repository.clearAllWorkers()
        }
    }

    // Overtime Log Actions
    fun toggleOvertime(date: String, workerId: String) {
        viewModelScope.launch {
            val logId = "${date}_${workerId}"
            val existing = allLogs.value.find { it.id == logId }
            if (existing != null) {
                // Delete if exists or toggle isChecked
                val newChecked = !existing.isChecked
                repository.insertLog(existing.copy(isChecked = newChecked))
            } else {
                repository.insertLog(OvertimeLog(logId, date, workerId, isChecked = true, mins = 0, note = ""))
            }
        }
    }

    fun adjustOvertimeMinutes(date: String, workerId: String, delta: Int) {
        viewModelScope.launch {
            val logId = "${date}_${workerId}"
            val existing = allLogs.value.find { it.id == logId }
            val currentMins = existing?.mins ?: 0
            val newMins = (currentMins + delta).coerceIn(0, 24 * 60)
            if (existing != null) {
                repository.insertLog(existing.copy(mins = newMins, isChecked = true))
            } else {
                repository.insertLog(OvertimeLog(logId, date, workerId, isChecked = true, mins = newMins, note = ""))
            }
        }
    }

    fun updateOvertimeNote(date: String, workerId: String, note: String) {
        viewModelScope.launch {
            val logId = "${date}_${workerId}"
            val existing = allLogs.value.find { it.id == logId }
            if (existing != null) {
                repository.insertLog(existing.copy(note = note))
            } else {
                repository.insertLog(OvertimeLog(logId, date, workerId, isChecked = true, mins = 0, note = note))
            }
        }
    }

    fun clearAllOT() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    // Week Label
    fun saveWeekLabel(label: String) {
        viewModelScope.launch {
            repository.insertSetting("week_label", label)
        }
    }

    // Dark Theme
    fun setDarkThemeSetting(theme: String) {
        viewModelScope.launch {
            repository.insertSetting("dark_theme", theme)
        }
    }

    // Global reset
    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _weekLabelSetting.value = ""
        }
    }

    // Gemini Parsing
    fun extractRosterFromImages(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        _extractionState.value = ExtractionState.Loading
        viewModelScope.launch {
            try {
                val extracted = GeminiManager.extractRosterFromImages(bitmaps)
                if (extracted != null && extracted.workers != null) {
                    // Save extracted workers
                    val dbWorkers = extracted.workers.map { ext ->
                        Worker(
                            id = "w_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                            name = ext.name.trim().uppercase(Locale.US),
                            section = ext.section.trim().uppercase(Locale.US),
                            shift = if (ext.shift.equals("Night", ignoreCase = true)) "Night" else "Day"
                        )
                    }
                    if (dbWorkers.isNotEmpty()) {
                        repository.insertWorkers(dbWorkers)
                    }

                    // Save extracted week label
                    val label = extracted.weekLabel ?: ""
                    if (label.isNotEmpty()) {
                        repository.insertSetting("week_label", label)
                    }

                    _extractionState.value = ExtractionState.Success(dbWorkers.size, label)
                } else {
                    _extractionState.value = ExtractionState.Error("Failed to extract roster. No workers found in the images.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _extractionState.value = ExtractionState.Error(e.message ?: "An unknown error occurred during extraction.")
            }
        }
    }
}

class OTViewModelFactory(private val repository: OTRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OTViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OTViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
