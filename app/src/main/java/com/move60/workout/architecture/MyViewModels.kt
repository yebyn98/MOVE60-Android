package com.move60.workout.architecture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.WorkoutHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MY tab lifetime number: dates with a v58 record or a stored session count once each (never added together on one date). */
object MyGrowth {
    fun cumulativeDays(legacy: Set<LocalDate>, stored: Set<LocalDate>): Int = (legacy + stored).size
}

data class MyHomeUi(
    val cumulativeDays: Int? = null,
    val defaultSpaceName: String? = null,
    /** Oldest first; null = could not be read (never shown as zeros). */
    val weekly: List<WeekPoint>? = null,
    val sessionCount: Int? = null,
    val completedChallenges: Int? = null,
    val purposeLabel: String? = null
) {
    val milestones: List<Milestone> get() = MyInsights.milestones(cumulativeDays, completedChallenges)
}

private val SEOUL = java.time.ZoneId.of("Asia/Seoul")

/** The last [weeks] weeks (v58 history + stored sessions). Throws on a read failure so callers can show "unknown". */
internal suspend fun loadWeekly(db: WorkoutDatabase, history: WorkoutHistory, weeks: Int): List<WeekPoint> {
    val today = LocalDate.now(SEOUL)
    val from = MyInsights.weekStart(today).minusWeeks((weeks - 1).toLong())
    val legacy = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
        .associateWith { history.record(it).activeSeconds }.filterValues { it / 60 >= 1L }
    val stored = db.routiveDao().sessionSecondsByDate(from.toString(), today.toString())
        .mapNotNull { row -> runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row.seconds } }.toMap()
    return MyInsights.weekly(today, weeks, legacy, stored)
}

@HiltViewModel
class MyHomeViewModel @Inject constructor(
    private val db: WorkoutDatabase,
    private val history: WorkoutHistory,
    private val firstRun: FirstRunStore
) : ViewModel() {
    private val _state = MutableStateFlow(MyHomeUi())
    val state: StateFlow<MyHomeUi> = _state.asStateFlow()

    init { load() }

    /** Each number is read on its own: a failed read shows "unknown" (null), never 0. */
    fun load() {
        viewModelScope.launch {
            val days = try {
                val stored = db.routiveDao().allSessionDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                MyGrowth.cumulativeDays(history.trainedDates(), stored)
            } catch (_: Exception) { null }
            val space = try { db.spaceDao().spaces().firstOrNull { it.isDefault }?.name } catch (_: Exception) { null }
            val weekly = try { loadWeekly(db, history, 4) } catch (_: Exception) { null }
            val sessions = try { db.routiveDao().sessionCount() } catch (_: Exception) { null }
            val challenges = try { db.routiveDao().completedChallengeCount() } catch (_: Exception) { null }
            _state.value = MyHomeUi(days, space, weekly, sessions, challenges, firstRun.purpose?.label)
        }
    }
}

/** Figma 68 data: this week vs last week, and the latest stored session with its real sets. */
data class MyReport(val thisWeek: WeekPoint, val lastWeek: WeekPoint, val latest: SessionDetail?)

@HiltViewModel
class MyReportViewModel @Inject constructor(
    private val db: WorkoutDatabase,
    private val history: WorkoutHistory,
    private val sets: com.move60.workout.SetRecordStore,
    private val data: com.move60.workout.WorkoutData
) : ViewModel() {
    private val _state = MutableStateFlow<RecordLoad<MyReport>>(RecordLoad.Loading)
    val state: StateFlow<RecordLoad<MyReport>> = _state.asStateFlow()

    init { load() }

    fun exerciseName(id: String): String = data.exerciseName(id) ?: id

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val weeks = loadWeekly(db, history, 2)
                val latestId = db.routiveDao().latestSessionId()
                val latest = latestId?.let { RecordReader(db) { sets.completedStoredSets() }.session(it) }
                RecordLoad.Ready(MyReport(weeks[1], weeks[0], latest))
            } catch (_: Exception) { RecordLoad.Failed }
        }
    }
}

data class SpaceUi(val space: WorkoutSpaceEntity, val equipmentCount: Int)

@HiltViewModel
class MySpacesViewModel @Inject constructor(private val db: WorkoutDatabase, private val service: SpaceService) : ViewModel() {
    /** null = still loading; an empty list = the user has not registered a space yet (nothing is seeded). */
    val spaces: StateFlow<List<SpaceUi>?> = combine(db.spaceDao().observeSpaces(), db.spaceDao().observeEquipment()) { spaces, eq ->
        spaces.map { s -> SpaceUi(s, eq.count { it.spaceId == s.spaceId }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun addHome() = create("home", SpaceKind.HOME, HOME_NAME)
    fun addGym(name: String) = create("gym-" + UUID.randomUUID(), SpaceKind.GYM, name)
    fun setDefault(spaceId: String) { viewModelScope.launch { report(service.setDefault(spaceId)) } }
    fun rename(spaceId: String, name: String) { viewModelScope.launch { report(service.rename(spaceId, name)) } }

    private fun create(id: String, kind: SpaceKind, name: String) {
        viewModelScope.launch { report(service.create(id, kind, name)) }
    }
    private fun report(r: SpaceResult) {
        if (r is SpaceResult.Rejected) _message.value = when (r.reason) {
            "name already used" -> "같은 이름의 운동 공간이 이미 있어요."
            "too many spaces" -> "운동 공간은 ${SpaceService.MAX_SPACES}개까지 등록할 수 있어요."
            else -> "운동 공간 이름은 1~${SpaceService.MAX_NAME}자로 입력해 주세요."
        }
    }
    companion object { const val HOME_NAME = "집 · 개인 운동 공간" }
}

sealed interface EquipmentLoad {
    data object Loading : EquipmentLoad
    data object Missing : EquipmentLoad
    data class Ready(val kind: SpaceKind, val name: String) : EquipmentLoad
}

/** Edits one space's equipment. Nothing is written until [save]; a failed save keeps the edits on screen. */
@HiltViewModel
class SpaceEquipmentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: WorkoutDatabase,
    private val service: SpaceService
) : ViewModel() {
    private val spaceId: String = savedState.get<String>("spaceId") ?: ""
    private val _load = MutableStateFlow<EquipmentLoad>(EquipmentLoad.Loading)
    val load: StateFlow<EquipmentLoad> = _load.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()
    /** equipment key -> (brand, model) free text. */
    private val _models = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val models: StateFlow<Map<String, Pair<String, String>>> = _models.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val space = try { db.spaceDao().space(spaceId) } catch (_: Exception) { null }
            if (space == null) { _load.value = EquipmentLoad.Missing; return@launch }
            val rows = try { db.spaceDao().equipment(spaceId) } catch (_: Exception) { emptyList() }
            _selected.value = rows.map { it.equipmentKey }.toSet()
            _models.value = rows.filter { it.brand != null || it.model != null }.associate { it.equipmentKey to ((it.brand ?: "") to (it.model ?: "")) }
            _load.value = EquipmentLoad.Ready(SpaceKind.valueOf(space.kind), space.name)
        }
    }

    fun toggle(key: String) { _selected.value = _selected.value.let { if (key in it) it - key else it + key }; _error.value = false }
    /** The user pressed "전체 선택": every choice of this kind is ticked. Nothing is ever selected without that press. */
    fun selectAll() {
        val kind = (_load.value as? EquipmentLoad.Ready)?.kind ?: return
        _selected.value = _selected.value + Equipment.choices(kind).map { it.name }; _error.value = false
    }
    fun setModel(key: String, brand: String, model: String) { _models.value = _models.value + (key to (brand to model)) }

    fun save() {
        val kind = (_load.value as? EquipmentLoad.Ready)?.kind ?: return
        val choices = Equipment.choices(kind).filter { it.name in _selected.value }.map {
            val m = _models.value[it.name]
            EquipmentChoice(it.name, m?.first, m?.second)
        }
        viewModelScope.launch {
            val r = try { service.saveEquipment(spaceId, choices) } catch (_: Exception) { SpaceResult.Rejected("error") }
            if (r is SpaceResult.Saved) _saved.value = true else _error.value = true
        }
    }
}

/** 최초 운동 설정 흐름의 상태. 목적은 계속하기를 누를 때 저장하고, 공간을 만든 뒤에야 설정 완료로 표시한다. */
@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val db: WorkoutDatabase,
    private val history: WorkoutHistory,
    private val store: FirstRunStore,
    private val spaces: SpaceService
) : ViewModel() {
    /** null = still deciding (the app shows its normal start meanwhile). */
    private val _needsSetup = MutableStateFlow<Boolean?>(null)
    val needsSetup: StateFlow<Boolean?> = _needsSetup.asStateFlow()

    private val _purpose = MutableStateFlow(store.purpose)
    val purpose: StateFlow<WorkoutPurpose?> = _purpose.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun decide(forced: Boolean) {
        viewModelScope.launch { _needsSetup.value = FirstRunGate(db, history, store).needsSetup(forced) }
    }

    fun choose(purpose: WorkoutPurpose) { _purpose.value = purpose }

    /** Saves the chosen purpose. Returns false when nothing is chosen (the button is disabled then, this is a guard). */
    fun savePurpose(): Boolean {
        val p = _purpose.value ?: return false
        store.savePurpose(p)
        return true
    }

    /** Creates the first space as the default one, then marks the setup done. [onSaved] gets the new space id. */
    fun finishWithSpace(kind: SpaceKind, gymName: String = "", onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = if (kind == SpaceKind.HOME) "home" else "gym-" + UUID.randomUUID()
            val name = if (kind == SpaceKind.HOME) MySpacesViewModel.HOME_NAME else gymName
            val created = try { spaces.create(id, kind, name) } catch (_: Exception) { SpaceResult.Rejected("error") }
            val defaulted = if (created is SpaceResult.Saved) try { spaces.setDefault(id) } catch (_: Exception) { SpaceResult.Rejected("error") } else created
            if (defaulted is SpaceResult.Saved) {
                store.markDone(); _needsSetup.value = false; _error.value = null; onSaved(id)
            } else {
                _error.value = if ((created as? SpaceResult.Rejected)?.reason == "name already used") "같은 이름의 운동 공간이 이미 있어요."
                    else "운동 공간을 저장하지 못했어요. 다시 시도해 주세요."
            }
        }
    }
}
