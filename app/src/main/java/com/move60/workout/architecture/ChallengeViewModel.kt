package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Read model of one challenge for the UI. [creditedDays] counts cumulative credited days (not consecutive). */
data class ChallengeCard(
    val id: String,
    val name: String,
    val status: ChallengeStatus,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val targetDays: Int,
    val creditedDays: Int,
    /** Calendar days left including today; 0 when ended. */
    val daysLeft: Int,
    val targetReachedDate: LocalDate?
) {
    val rate: Float get() = if (targetDays <= 0) 0f else minOf(1f, creditedDays.toFloat() / targetDays)
}

data class ChallengeUiState(
    val loading: Boolean = true,
    val active: ChallengeCard? = null,
    val ended: List<ChallengeCard> = emptyList(),
    /** Last user-visible result; consumed by the screen via [ChallengeViewModel.messageShown]. */
    val message: String? = null
)

@HiltViewModel
class ChallengeViewModel @Inject constructor(committer: SessionCommitter) : ViewModel() {
    private val service = committer.challenges
    private val _state = MutableStateFlow(ChallengeUiState())
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching { service.refreshActive() }
            reload()
        }
    }

    private suspend fun reload(message: String? = _state.value.message) {
        val active = service.active()
        val activeCard = active?.let { card(it) }
        val ended = service.ended().map { card(it) }
        _state.value = ChallengeUiState(loading = false, active = activeCard, ended = ended, message = message)
    }

    /** Start date is always the creation day (confirmed policy); the period end is inclusive. */
    fun create(name: String, targetDays: Int, endDate: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        viewModelScope.launch {
            val today = LocalDate.now(zone)
            val trimmed = name.trim()
            val entity = ChallengeEntity(
                UUID.randomUUID().toString(), trimmed, today.toString(), endDate.toString(), zone.id,
                targetDays, ChallengeStatus.ACTIVE.name, System.currentTimeMillis(), null, null
            )
            val message = when (val r = service.create(entity)) {
                is ChallengeResult.Created -> null
                is ChallengeResult.AlreadyExists, is ChallengeResult.Conflict -> "이미 같은 도전이 있어요."
                is ChallengeResult.Rejected -> when (r.reason) {
                    ChallengeReject.TARGET_EXCEEDS_PERIOD -> "목표 일수는 기간(일수)보다 클 수 없어요."
                    ChallengeReject.ACTIVE_CHALLENGE_EXISTS -> "진행 중인 도전이 있어요. 종료 후 새로 시작할 수 있어요."
                    ChallengeReject.INVALID_FIELD -> "이름과 목표 일수를 확인해 주세요."
                    ChallengeReject.INVALID_DATE, ChallengeReject.START_DATE_NOT_CREATION_DAY -> "기간을 확인해 주세요."
                    ChallengeReject.INVALID_ZONE -> "시간대를 확인해 주세요."
                }
                is ChallengeResult.Failed -> "도전을 만들지 못했어요. 다시 시도해 주세요."
            }
            reload(message)
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val ok = service.rename(id, name)
            reload(if (ok) null else "이름은 1~${ChallengeService.MAX_NAME}자로 입력해 주세요.")
        }
    }

    /** Only the confirm dialog calls this with confirmed = true. */
    fun abandon(id: String) {
        viewModelScope.launch {
            val message = when (service.abandon(id, confirmed = true)) {
                is AbandonResult.Abandoned -> null
                is AbandonResult.AlreadyEnded -> "이미 종료된 도전이에요."
                AbandonResult.NotFound -> "도전을 찾지 못했어요."
                AbandonResult.NotConfirmed -> "포기를 확인해 주세요."
            }
            reload(message)
        }
    }

    fun messageShown() { _state.value = _state.value.copy(message = null) }

    private suspend fun card(c: ChallengeEntity): ChallengeCard {
        val zone = ZoneId.of(c.timeZoneId)
        val status = ChallengeStatus.valueOf(c.status)
        val snap = c.endSnapshotJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val credited = snap?.optInt("creditedDays", -1)?.takeIf { it >= 0 }
            ?: service.credits(c.challengeId).count { it.credited }
        val today = Instant.now().atZone(zone).toLocalDate()
        val end = LocalDate.parse(c.endDate)
        val left = if (status == ChallengeStatus.ACTIVE) maxOf(0, java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt() + 1) else 0
        val reached = snap?.optString("targetReachedDate", "")?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse)
        return ChallengeCard(c.challengeId, c.name, status, LocalDate.parse(c.startDate), end, c.targetDays, credited, left, reached)
    }
}
