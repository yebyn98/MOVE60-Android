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

data class MyHomeUi(val cumulativeDays: Int?, val defaultSpaceName: String?)

@HiltViewModel
class MyHomeViewModel @Inject constructor(
    private val db: WorkoutDatabase,
    private val history: WorkoutHistory
) : ViewModel() {
    private val _state = MutableStateFlow(MyHomeUi(null, null))
    val state: StateFlow<MyHomeUi> = _state.asStateFlow()

    init { load() }

    /** A failed read shows "no value" (null), never 0. */
    fun load() {
        viewModelScope.launch {
            val days = try {
                val stored = db.routiveDao().allSessionDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                MyGrowth.cumulativeDays(history.trainedDates(), stored)
            } catch (_: Exception) { null }
            val space = try { db.spaceDao().spaces().firstOrNull { it.isDefault }?.name } catch (_: Exception) { null }
            _state.value = MyHomeUi(days, space)
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
