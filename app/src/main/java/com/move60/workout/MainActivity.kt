package com.move60.workout

import android.Manifest
import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.move60.workout.Move60Theme as Theme
import com.move60.workout.DashboardUi
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max

/** Fully native Android/Kotlin interface. No WebView, JavaScript, or HTML UI. */
class MainActivity : Activity() {
    private val background = Theme.Colors.background
    private val panel = Theme.Colors.surface
    private val panelLight = Theme.Colors.surfaceElevated
    private val subtle = Theme.Colors.outline
    private val mint = Theme.Colors.primary
    private val textPrimary = Theme.Colors.textHigh
    private val textSecondary = Theme.Colors.textMedium
    private lateinit var updateManager: UpdateManager
    private lateinit var data: WorkoutData
    private lateinit var exercisePrefs: ExercisePreferences
    private lateinit var session: SessionStore
    private lateinit var content: LinearLayout
    private lateinit var stageAccordion: LinearLayout
    private lateinit var activeStageContent: LinearLayout
    private var expandedStage = -1
    private var lastAccordionStage = -1
    private var lastAccordionWasStarted = false
    private lateinit var dayInfo: TextView
    private lateinit var currentSetPanel: LinearLayout
    private lateinit var timerValue: TextView
    private lateinit var timerLabel: TextView
    private lateinit var timeMeta: TextView
    private lateinit var timerBadge: TextView
    private lateinit var timerCueTitle: TextView
    private lateinit var timerCueDetail: TextView
    private lateinit var heartRateValue: TextView
    private lateinit var progress: ProgressBar
    private lateinit var restPanel: LinearLayout
    private lateinit var restValue: TextView
    private lateinit var dockStage: TextView
    private lateinit var dockProgressFill: View
    private lateinit var dockTime: TextView
    private lateinit var dockCalories: TextView
    private lateinit var dockWatchKcal: TextView
    private lateinit var dockHeartRate: TextView
    private lateinit var play: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var phaseList: LinearLayout
    private lateinit var phaseRpe: TextView
    private lateinit var phaseTimeRow: LinearLayout
    private lateinit var phaseTime: TextView
    private lateinit var phaseBar: ProgressBar
    private var currentPhaseTime: TextView? = null
    private val publishDraft = Runnable { if (::session.isInitialized) syncService() }
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhaseKey = ""
    private var lastDay = -1
    private var lastStage = -1
    private var lastChecked = -1
    private var soundEnabled = true
    private var recoveryMode = false
    private val ticker = object : Runnable {
        override fun run() {
            val previousDate = session.recordDate
            val previousChecks = session.checked.toSet()
            val previousStage = session.stage
            val previousDay = session.day
            val external = session.reloadIfChanged()
            if (previousDate != session.recordDate) { constructInterface(); syncService() }
            if (external && (previousChecks != session.checked || previousStage != session.stage || previousDay != session.day)) {
                renderCurrent()
            }
            if (external && (previousStage != session.stage || previousDay != session.day)) syncService()
            val changed = session.reconcile()
            if (changed) {
                session.save()
                syncService()
            }
            val phase = session.phase()?.first ?: -1
            val phaseKey = "${session.day}:${session.stage}:$phase"
            if (phaseKey != lastPhaseKey || lastDay != session.day || lastStage != session.stage) {
                val previousPhase = lastPhaseKey
                lastPhaseKey = phaseKey
                if (previousPhase.isNotEmpty() && previousPhase != phaseKey && session.running && soundEnabled) playTone()
                renderCurrent()
            }
            refreshTimer()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        CrashReporter.install(this)
        recoveryMode = CrashReporter.needsRecovery(this)
        try {
            data = WorkoutData(this)
            exercisePrefs = ExercisePreferences(this)
            session = SessionStore(this, data)
            updateManager = UpdateManager(this)
        } catch (e: Exception) {
            CrashReporter.log(this, "앱 초기화 오류: " + e.stackTraceToString().take(4000))
            showRecoveryScreen("운동 기록을 불러오는 중 문제가 발생했어요.")
            return
        }
        soundEnabled = getSharedPreferences("preferences", MODE_PRIVATE).getBoolean("sound", true)
        val root = column().apply {
            setBackgroundColor(this@MainActivity.background)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            contentDescription = "운동 내용 스크롤"
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.Space.two), dp(Theme.Space.three),
                dp(Theme.Space.two), dp(Theme.Space.three))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
        try {
            root.addView(makeWorkoutControls(), LinearLayout.LayoutParams(-1, -2))
            constructInterface()
        } catch (e: Exception) {
            CrashReporter.log(this, "화면 구성 오류: " + e.stackTraceToString().take(4000))
            showRecoveryScreen("화면을 표시하는 중 문제가 발생했어요.")
            return
        }
        handler.post(ticker)
        if (!recoveryMode) {
            if (session.running) syncService()
            // Updates are opened from the dedicated settings screen.
        } else {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("MOVE60 안전 복구 모드")
                        .setMessage("이전 실행의 종료 오류가 기록되어 자동 알림 재시작을 중지했어요. 운동 기록은 그대로 남아 있어요. 오류 기록을 확인한 뒤 알림을 다시 켤 수 있어요.")
                        .setNegativeButton("오류 기록 확인") { _, _ -> CrashReporter.showDiagnostics(this) }
                        .setPositiveButton("알림 재시도") { _, _ ->
                            CrashReporter.acknowledge(this)
                            recoveryMode = false
                            if (session.running) syncService()
                            // Updates are opened from the dedicated settings screen.
                        }.show()
                }
            }, 750L)
        }
    }


    /** Returns to the Compose overview even when this screen is the task root (e.g. opened from a notification). */
    private fun leaveToOverview() {
        if (isTaskRoot) {
            startActivity(Intent(this, com.move60.workout.architecture.ComposeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }

    private fun constructInterface() {
        content.removeAllViews()
        // Detail screen: back button + title replace the old app bar so home <-> detail reads as one flow.
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; tag = "detail_header" }
        // Figma icon/arrow_back: bare 24dp arrow inside a 48dp touch target.
        header.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER
            tag = "detail_back"
            contentDescription = "홈으로 돌아가기"
            setOnClickListener { leaveToOverview() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label("오늘의 운동", 24, true).apply {
            tag = "detail_title"
            isSingleLine = true
            contentDescription = "운동 상세 화면 제목"
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        content.addView(header)
        addGap(content, 16)
        // Day and body-part selection live on the Compose overview; this screen only shows the result.
        dayInfo = label("", 12, true, mint).apply { tag = "phone_day_info"; gravity = Gravity.END }
        val nowHeading = row().apply { gravity = Gravity.CENTER_VERTICAL }
        nowHeading.addView(label("오늘의 운동", 20, true), LinearLayout.LayoutParams(0, -2, 1f))
        nowHeading.addView(dayInfo, LinearLayout.LayoutParams(-2, -2))
        content.addView(nowHeading)
        addGap(content, Theme.Space.two)
        // Figma order: the set-rest card sits above the current-exercise card; its gap is a margin so a hidden card leaves no hole.
        restPanel = card().apply { tag = "phone_rest"; visibility = View.GONE }
        restValue = label("", 28, true, mint).apply { tag = "phone_rest_time" }
        restPanel.addView(label("세트 휴식", 16, true, textSecondary))
        restPanel.addView(restValue)
        val restActions = row().apply { isBaselineAligned = false }
        restActions.addView(actionButton("+15초", false) { session.adjustRest(15); syncService(); refreshTimer() }.apply { tag="phone_rest_add" },LinearLayout.LayoutParams(0,dp(48),1f))
        restActions.addView(actionButton("건너뛰기", false) { session.skipRest(); syncService(); refreshTimer() }.apply { tag="phone_rest_skip" },LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin = dp(8) })
        restPanel.addView(restActions)
        content.addView(restPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.Space.two) })
        // Every step stays on this screen. The active step expands automatically, while
        // tapping another header reveals a read-only preview without jumping the timer.
        stageAccordion = column().apply { tag = "phone_stage_stack" }
        activeStageContent = makeTimerCard()
        content.addView(stageAccordion)
        renderCurrent()
        refreshTimer()
    }

    /** A sibling of the weighted ScrollView, so controls never scroll or cover content. */
    private fun makeWorkoutControls(): LinearLayout {
        val dock = column().apply {
            contentDescription = "고정 운동 컨트롤"
            setBackgroundColor(Theme.Colors.surface)
            setPadding(dp(Theme.Space.two), 0, dp(Theme.Space.two), dp(Theme.Space.two))
            elevation = dp(8).toFloat()
        }
        // Figma dock has a hairline on top; the fixed dock never overlaps scrolling content.
        // The 4dp top edge doubles as today's 60-minute progress bar (replaces the old hairline).
        dockProgressFill = View(this).apply { setBackgroundColor(Theme.Colors.primary) }
        val progressBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Theme.Colors.outline)
            tag = "phone_dock_progress"
            addView(dockProgressFill, LinearLayout.LayoutParams(0, -1, 0f))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, -1, 1f))
        }
        dock.addView(progressBar,
            LinearLayout.LayoutParams(-1, dp(4)).apply { leftMargin = -dp(Theme.Space.two); rightMargin = -dp(Theme.Space.two) })
        addGap(dock, Theme.Space.one + 4)
        // Figma "FixedBottomDock": stage + elapsed on the left, 24dp heart pill on the right, kcal on its own line.
        val state = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val info = column()
        dockStage = label("준비운동", 16, true)
        dockStage.setSingleLine(true)
        dockStage.ellipsize = android.text.TextUtils.TruncateAt.END
        dockTime = label("", 12, false, textSecondary)
        dockCalories = label("오늘 누적 0.0 kcal · 추정", 12, true, mint).apply {
            contentDescription = "오늘 날짜에 실제 운동한 시간의 누적 소모 칼로리 추정치"
            tag = "phone_session_calories"
        }
        // Opt-in watch measurement: its own line, only while a fresh sample exists for this workout.
        dockWatchKcal = label("", 12, false, textSecondary).apply {
            tag = "phone_watch_kcal"
            visibility = View.GONE
            contentDescription = "워치가 측정한 이번 운동 총 칼로리, 기초대사 포함"
        }
        info.addView(dockStage)
        info.addView(dockTime)
        state.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        dockHeartRate = label("♥ 연결대기", 12, true, Theme.Colors.heart).apply {
            contentDescription = "하단 실시간 심박수"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            // Fully rounded 24dp pill (not a card-radius rectangle).
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panelLight)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), subtle)
            }
        }
        state.addView(dockHeartRate, LinearLayout.LayoutParams(-2, dp(24)).apply { leftMargin = dp(8) })
        dock.addView(state)
        addGap(dock, 8)
        dock.addView(dockCalories)
        dock.addView(dockWatchKcal)
        addGap(dock, 8)
        play = actionButton("운동 시작", true) {
            if (session.running) session.pause() else {
                if (session.finished || session.elapsed() == 0) session.refreshRoutine()
                session.start()
                SetRecordStore(this).beginSession(session.day, session.sessionStartedAt)
            }
            syncService()
            renderCurrent()
            refreshTimer()
        }.apply { contentDescription = "운동 시작 또는 일시정지"; tag = "phone_play" }
        // Baseline alignment shifts buttons with different text sizes out of the row and clips their outline.
        val buttons = row().apply { isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
        previous = actionButton("이전", false) { session.jumpTo(session.stage - 1); afterAction() }.apply { tag = "phone_prev" }
        next = actionButton("다음", false) {
            if (session.stage == LAST_STAGE && !session.finished) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("운동을 종료할까요?")
                    .setMessage("현재까지 수행한 시간을 기록하고 운동 결과를 보여드려요.")
                    .setNegativeButton("돌아가기", null)
                    .setPositiveButton("기록하고 종료") { _, _ ->
                        session.nextStage()
                        afterAction()
                        showWorkoutResult()
                    }.show()
            } else {
                session.nextStage()
                afterAction()
            }
        }.apply { tag = "phone_next" }
        listOf(previous, next, play).forEach { it.minHeight = dp(48); it.minimumHeight = dp(48); it.maxLines = 1 }
        previous.textSize = Theme.Type.caption
        next.textSize = Theme.Type.caption
        play.textSize = Theme.Type.body
        buttons.addView(previous, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(play, LinearLayout.LayoutParams(0, dp(48), 1.35f).apply { leftMargin = dp(8) })
        buttons.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        dock.addView(buttons)
        return dock
    }

    /** Content for the expanded active step, not a separate "current stage" card. */
    private fun makeTimerCard(): LinearLayout {
        val box = column().apply {
            tag = "phone_timer_card"
            setPadding(0, dp(8), 0, dp(8))
        }
        timerBadge = label("", 12, true, mint)
        timerLabel = label("", 12, false, textSecondary)
        timerCueTitle = label("", 18, true)
        timerCueDetail = label("", 12, false, textSecondary)
        timerValue = label("", 26, true)
        heartRateValue = label("연결대기", 12, true)
        timeMeta = label("", 12, false, textSecondary)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
            visibility = View.GONE // Stage time now lives in the fixed bottom dock.
        }
        phaseList = column().apply { tag = "phone_phase_list"; visibility = View.GONE }
        box.addView(phaseList)
        phaseRpe = label("", 12, true, mint).apply {
            tag = "phone_phase_rpe"
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(Color.TRANSPARENT, 24, mint)
            visibility = View.GONE
        }
        val titleRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(timerCueTitle, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(phaseRpe)
        box.addView(titleRow)
        phaseTimeRow = row().apply { gravity = Gravity.BOTTOM; visibility = View.GONE; tag = "phone_phase_time_row" }
        phaseTime = label("", 26, true, mint).apply { tag = "phone_phase_time" }
        phaseTimeRow.addView(phaseTime, LinearLayout.LayoutParams(0, -2, 1f))
        phaseTimeRow.addView(label("남은 시간", 12, false, textSecondary))
        box.addView(phaseTimeRow)
        phaseBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Theme.Colors.outline)
            max = 1000
            visibility = View.GONE
            tag = "phone_phase_bar"
        }
        box.addView(phaseBar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(8); bottomMargin = dp(4) })
        addGap(box, Theme.Space.one)
        box.addView(timerCueDetail)
        currentSetPanel = column().apply { tag = "phone_current_exercise"; visibility = View.GONE }
        box.addView(currentSetPanel)
        box.setOnLongClickListener {
            MaterialAlertDialogBuilder(this).setTitle("운동 초기화")
                .setItems(arrayOf("현재 단계 초기화", "오늘 진행 초기화")) { _, which ->
                    if (which == 0) session.resetStage() else session.resetAll()
                    afterAction()
                }.setNegativeButton("취소", null).show()
            true
        }
        return box
    }

    /** Single scrolling accordion: opening a different step never changes the real workout. */
    private fun renderStageAccordion() {
        if (!::stageAccordion.isInitialized) return
        val started = session.running || session.elapsed() > 0
        if (lastAccordionStage != session.stage || started != lastAccordionWasStarted) {
            // Before workout every step is collapsed; when started, focus the actual stage.
            expandedStage = if (started && !session.finished) session.stage else -1
            lastAccordionStage = session.stage
        }
        lastAccordionWasStarted = started
        stageAccordion.removeAllViews()
        session.routine.stages.forEachIndexed { index, step ->
            val active = index == session.stage && !session.finished && started
            val opened = index == expandedStage
            val done = session.finished || index in session.completed || index < session.stage
            val cell = card().apply {
                tag = "phone_step_$index"
                contentDescription = "${index + 1}단계 ${step.name}"
            }
            val header = row().apply {
                tag = "phone_step_header_$index"
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    expandedStage = if (opened) -1 else index
                    renderStageAccordion()
                    renderCurrentExercise()
                }
            }
            // Figma: the step name is a pill chip, no running number.
            val chipText = step.name + when {
                index == 2 && session.bodyFocus == "lower" -> " · 하체"
                index == 2 && session.bodyFocus == "upper" -> " · 상체"
                else -> ""
            }
            val titleBox = row().apply { gravity = Gravity.CENTER_VERTICAL }
            titleBox.addView(label(chipText, 16, true, mint).apply {
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = rounded(panelLight, 24)
                tag = "phone_step_chip_$index"
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2))
            header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
            val state = when {
                active && !session.running -> "일시정지"
                active -> "진행 중"
                done -> "완료"
                else -> "예정"
            }
            header.addView(label(state, 11, true, if (active || done) mint else textSecondary))
            // Vector chevron (Figma icon), never a text glyph: glyph shape/baseline differs per font.
            header.addView(android.widget.ImageView(this).apply {
                setImageResource(if (opened) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
                imageTintList = android.content.res.ColorStateList.valueOf(textSecondary)
                scaleType = android.widget.ImageView.ScaleType.CENTER
                tag = "phone_step_chevron_$index"
                contentDescription = if (opened) "접기" else "펼치기"
            }, LinearLayout.LayoutParams(dp(34), dp(48)))
            cell.addView(header)
            if (opened) {
                addGap(cell, Theme.Space.one)
                if (active) {
                    (activeStageContent.parent as? android.view.ViewGroup)?.removeView(activeStageContent)
                    cell.addView(activeStageContent)
                } else {
                    cell.addView(buildStageRows(index, step, done))
                }
            }
            stageAccordion.addView(cell, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(Theme.Space.two)
            })
        }
    }

    /**
     * Read-only exercise rows for a stage that is not running (upcoming or completed). Same row anatomy as the
     * running stage (Figma 08): status icon, name, trailing meta. Never truncated; no input controls.
     */
    private fun buildStageRows(index: Int, step: WorkoutStage, done: Boolean): LinearLayout {
        val box = column().apply { tag = "phone_step_preview_$index" }
        val timedStage = phasesKeyForStage(index) != null && step.tasks.all { it.seconds > 0 }
        step.tasks.forEachIndexed { i, task ->
            val meta = when {
                timedStage -> formatTime(task.seconds)
                index == 2 -> {
                    val reps = Regex("×\\s*([^·]+)").find(task.detail)?.groupValues?.get(1)?.trim()
                    if (reps.isNullOrEmpty()) "${strengthSetCount(task)}세트" else "${strengthSetCount(task)}세트 · $reps"
                }
                else -> task.detail
            }
            val rowView = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                tag = "phone_step_row_${index}_$i"
                setPadding(dp(8), 0, dp(8), 0)
                contentDescription = "${task.name} $meta ${if (done) "완료" else "예정"}"
            }
            rowView.addView(android.widget.ImageView(this).apply {
                setImageResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            rowView.addView(label(task.name, 15, false, if (done) textSecondary else textPrimary).apply {
                setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            rowView.addView(label(meta, 12, false, textSecondary).apply {
                tag = "phone_step_row_meta_${index}_$i"
                setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(150); gravity = Gravity.END
            })
            box.addView(rowView, LinearLayout.LayoutParams(-1, -2))
        }
        if (index == 2 && !done) {
            box.addView(label("세트 기록은 근력운동이 진행 중일 때 입력해요.", 11, false, textSecondary)
                .apply { setPadding(dp(8), dp(4), dp(8), dp(8)) })
        }
        return box
    }

    /** Figma phase rows: done = check, current = spinning ring + highlighted, upcoming = outline; 48dp tall. */
    private fun renderPhaseList() {
        if (!::phaseList.isInitialized) return
        phaseList.removeAllViews()
        currentPhaseTime = null
        val tasks = session.routine.stages[session.stage].tasks
        val timed = phasesKeyForStage(session.stage) != null && tasks.all { it.seconds > 0 }
        val started = session.running || session.elapsed() > 0
        if (!timed || tasks.isEmpty() || session.finished || !started) { phaseList.visibility = View.GONE; return }
        phaseList.visibility = View.VISIBLE
        val current = session.phase()?.first ?: -1
        tasks.forEachIndexed { i, task ->
            val done = current < 0 || i < current
            val now = i == current
            val rowView = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                tag = "phone_phase_row_$i"
                setPadding(dp(8), 0, dp(8), 0)
                if (now) background = rounded(panelLight, 12)
                contentDescription = "${task.name} ${if (done) "완료" else if (now) "진행 중" else "예정"}"
            }
            val icon = android.widget.ImageView(this).apply {
                setImageResource(if (done) R.drawable.ic_check_circle else if (now) R.drawable.ic_progress_ring else R.drawable.ic_circle_outline)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // Spins only while the phase runs; respects the system "remove animations" setting.
                if (now && session.running && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    val spin = android.animation.ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
                        duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
                        interpolator = android.view.animation.LinearInterpolator()
                    }
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) { if (!spin.isStarted) spin.start() }
                        override fun onViewDetachedFromWindow(v: View) { spin.cancel() }
                    })
                }
            }
            rowView.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
            rowView.addView(label(task.name, 15, now, if (done) textSecondary else textPrimary).apply {
                setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            val time = label(formatTime(if (now) session.phaseSecondsRemaining() else task.seconds), 12, false,
                if (now) mint else textSecondary).apply { tag = "phone_phase_row_time_$i" }
            if (now) currentPhaseTime = time
            rowView.addView(time)
            phaseList.addView(rowView, LinearLayout.LayoutParams(-1, -2))
        }
        addGap(phaseList, Theme.Space.one)
    }

    private fun strengthSetCount(task: WorkoutTask): Int =
        Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 3

    private fun doneSets(task: WorkoutTask): Int =
        (1..strengthSetCount(task)).count { set -> "strength-${session.day}-${task.id}-${set}" in session.checked }

    /** First strength exercise with unfinished sets; the last one once everything is done. */
    private fun currentStrengthTask(): WorkoutTask? {
        val tasks = session.routine.stages[2].tasks
        return tasks.firstOrNull { doneSets(it) < strengthSetCount(it) } ?: tasks.lastOrNull()
    }

    /** "지금 할 운동" card: set entry for the current strength exercise (strength stage only). */
    private fun renderCurrentExercise() {
        if (!::currentSetPanel.isInitialized) return
        currentSetPanel.removeAllViews()
        val task = if (session.stage == 2 && !session.finished) currentStrengthTask() else null
        if (task == null) { currentSetPanel.visibility = View.GONE; return }
        currentSetPanel.visibility = View.VISIBLE
        addGap(currentSetPanel, Theme.Space.two)
        currentSetPanel.addView(View(this).apply { setBackgroundColor(Theme.Colors.outline) },
            LinearLayout.LayoutParams(-1, dp(1)))
        addGap(currentSetPanel, Theme.Space.two)
        currentSetPanel.addView(buildSetTable(task, showHint = true))
        val guide = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            tag = "phone_current_guide"
            contentDescription = "${task.name} 자세 안내 및 대체 운동"
            setOnClickListener { showDetails(2, task) }
        }
        guide.addView(label("자세 안내 · 대체 운동", 16, true, mint), LinearLayout.LayoutParams(0, -2, 1f))
        guide.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        currentSetPanel.addView(guide)
    }

    /** Per-set weight/reps/done rows for one exercise; shared by the card and the exercise popup. */
    private fun buildSetTable(task: WorkoutTask, showHint: Boolean = false): LinearLayout {
        val count = strengthSetCount(task)
        val table = column()
        val setRecords = SetRecordStore(this)
        val progress = label("세트 완료  ${doneSets(task)} / ${count}", 16, true,
            if (doneSets(task) == count) mint else textSecondary)
        progress.setPadding(0, dp(8), 0, dp(4))
        table.addView(progress)
        // Figma: the hint sits right under the heading, before the set blocks.
        table.addView(label(if (showHint) "맨몸은 0kg · 체크하면 휴식이 자동으로 시작돼요"
            else "맨몸은 0kg · 중량과 횟수 자동 저장", 12, false, textSecondary).apply { setPadding(0, 0, 0, dp(8)) })
        for (set in 1..count) {
            val key = "strength-${session.day}-${task.id}-${set}"
            val saved = setRecords.read(session.day, task.id, set, token = session.sessionStartedAt)
            val prior = setRecords.previous(task.id, set, session.sessionStartedAt)
            val isCurrentSet = key !in session.checked && (1 until set).all { "strength-${session.day}-${task.id}-$it" in session.checked }
            // One block per set: header, +/- steppers for kg and reps, and a full-width complete button.
            val block = column().apply {
                background = rounded(Theme.Colors.surfaceElevated, 16, Theme.Colors.outline)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val head = row().apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(label("${set}세트", 12, true, if (isCurrentSet) mint else textPrimary), LinearLayout.LayoutParams(0, -2, 1f))
            if (prior != null) head.addView(label("지난 기록 ${prior.kg} kg × ${prior.reps}회", 10, false, textSecondary))
            block.addView(head)
            fun input(value: String, hint: String, decimal: Boolean = false): EditText = EditText(this).apply {
                setText(value)
                this.hint = hint
                textSize = Theme.Type.body
                gravity = Gravity.CENTER
                setTextColor(textPrimary)
                setHintTextColor(textSecondary)
                setSingleLine(true)
                setSelectAllOnFocus(true)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or if (decimal) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                contentDescription = "${task.name} ${set}세트 $hint"
            }
            val kg = input(saved.kg, "kg", true).apply { tag = "set-${task.id}-$set-kg" }
            val reps = input(saved.reps, "회").apply { tag = "set-${task.id}-$set-reps" }
            fun stepper(field: EditText, unit: String, step: Double, decimal: Boolean, priorValue: String?): LinearLayout {
                fun adjust(delta: Double) {
                    val current = field.text.toString().replace(',', '.').toDoubleOrNull()
                    val next = when {
                        current != null -> current + delta
                        delta > 0 -> priorValue?.replace(',', '.')?.toDoubleOrNull() ?: delta
                        else -> return
                    }.coerceAtLeast(0.0)
                    field.setText(if (decimal && next % 1.0 != 0.0) String.format(java.util.Locale.US, "%.1f", next) else next.toLong().toString())
                    field.setSelection(field.text.length)
                }
                fun stepButton(symbol: String, delta: Double, action: String) = actionButton(symbol, false) { adjust(delta) }.apply {
                    textSize = Theme.Type.body
                    setPadding(0, 0, 0, 0)
                    contentDescription = "${task.name} ${set}세트 $unit $action"
                    tag = "set-${task.id}-$set-" + (if (unit == "중량") "kg" else "reps") + (if (delta < 0) "-minus" else "-plus")
                    // Looks 32dp but keeps the full 48dp touch target: the inset only shrinks the drawn background.
                    background = android.graphics.drawable.InsetDrawable(background, dp(8))
                }
                // Stacked rows keep the value field wide enough for "122.5" even in the narrow popup.
                return row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(if (decimal) "kg" else "회", 11, true, textSecondary).apply { gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(dp(32), dp(48)))
                    addView(stepButton("−", -step, "줄이기"), LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(field, LinearLayout.LayoutParams(0, dp(48), 1f))
                    addView(stepButton("+", step, "늘리기"), LinearLayout.LayoutParams(dp(48), dp(48)))
                }
            }
            block.addView(stepper(kg, "중량", 2.5, true, prior?.kg), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            block.addView(stepper(reps, "횟수", 1.0, false, prior?.reps), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            // Old per-set intensity is retained in storage for compatibility.
            // New UI exposes only equipment load and repetitions.
            val retainedEffort = saved.rpe.ifBlank { "5" }
            val check = CheckBox(this).apply {
                tag = "set-${task.id}-$set-check"
                isChecked = key in session.checked || saved.completed
                buttonDrawable = null
                gravity = Gravity.CENTER
                textSize = Theme.Type.body
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_checked), rounded(mint, 16))
                    addState(intArrayOf(), rounded(Theme.Colors.surface, 16, Theme.Colors.outline))
                }
                contentDescription = "${task.name} ${set}세트 완료"
                setPadding(dp(8), 0, dp(8), 0)
            }
            fun styleCheck() {
                check.text = if (check.isChecked) "✓ ${set}세트 완료" else "${set}세트 완료"
                check.setTextColor(if (check.isChecked) Theme.Colors.onPrimary else textPrimary)
            }
            styleCheck()
            fun saveEntry() {
                val record = SetRecordStore.Record(kg.text.toString(), reps.text.toString(), retainedEffort, check.isChecked)
                setRecords.write(session.day, task.id, set, record, token = session.sessionStartedAt)
                if (!record.valid && check.isChecked) check.isChecked = false
                refreshTimer()
                handler.removeCallbacks(publishDraft)
                handler.postDelayed(publishDraft,400L)
            }
            listOf(kg, reps).forEach { field ->
                field.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { saveEntry() }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
            check.setOnCheckedChangeListener { _, checked ->
                styleCheck()
                val record = SetRecordStore.Record(kg.text.toString(), reps.text.toString(), retainedEffort, checked)
                if (checked && !record.valid) {
                    check.isChecked = false
                    Toast.makeText(this, "중량(맨몸 0kg)과 횟수를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    session.toggleChecked(key, checked)
                    setRecords.write(session.day, task.id, set, record, token = session.sessionStartedAt)
                    val done = doneSets(task)
                    progress.text = "세트 완료  ${done} / ${count}"
                    progress.setTextColor(if (done == count) mint else textSecondary)
                    if (checked) session.startRest(TrainingProgress.restDuration(task))
                    else session.skipRest()
                    syncService()
                    refreshTimer()
                    // The last set moves the "지금 할 운동" card on to the next exercise, announced by a toast and a vibration.
                    if (checked && done == count) {
                        val next = TrainingProgress.next(session)
                        if (next != null && next.task.id != task.id) {
                            Toast.makeText(this, "${task.name} 완료 · ${next.task.name}${movedSuffix(next.task.name)} 이동했어요",
                                Toast.LENGTH_SHORT).show()
                            Haptics.exerciseChanged(this)
                        }
                        handler.post { renderCurrent() }
                    }
                }
            }
            block.addView(check, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            table.addView(block, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        return table
    }

    /** "로" after a vowel or ㄹ final, "으로" after any other final consonant. */
    private fun movedSuffix(name: String): String {
        val last = name.trim().lastOrNull() ?: return "로"
        if (last !in '가'..'힣') return "로"
        val final = (last - '가') % 28
        return if (final == 0 || final == 8) "로" else "으로"
    }

    private fun renderCurrent() {
        lastDay = session.day
        lastStage = session.stage
        val dayNames = listOf("월", "화", "수", "목", "금")
        dayInfo.text = "${dayNames.getOrElse(session.day - 1) { "" }}요일 루틴 · " +
            if (session.bodyFocus == "lower") "하체" else if (session.bodyFocus == "upper") "상체" else "부위 미선택"
        renderStageAccordion()
        renderPhaseList()
        renderCurrentExercise()
    }

    private fun displayDetail(raw: String): String =
        raw.replace(Regex("(?i)\\s*[·,]?\\s*RPE\\s*\\d+(?:\\s*[-~]\\s*\\d+)?"), "")
            .replace(Regex("\\s*·\\s*·"), " · ")
            .trim(' ', '·', ',')

    private fun showDetails(stage: Int, task: WorkoutTask) {
        if (stage != 2) return // Guidance is only meaningful for strength exercises.
        val info = data.detail(task.id)
        val steps = info?.steps ?: listOf("운동 범위를 천천히 확인하고 무리 없는 자세를 유지하세요.")
        val panel = column().apply {
            setPadding(dp(Theme.Space.two), dp(Theme.Space.one),
                dp(Theme.Space.two), dp(Theme.Space.one))
        }
        // Height follows the content but never exceeds the room left under the dialog title and above
        // its buttons (55% of the screen), so the last block is reachable instead of being cut off.
        val maxScrollHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val scroll = object : androidx.core.widget.NestedScrollView(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxScrollHeight, MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(Theme.Space.two))
        }
        val detailContent = column()
        scroll.addView(detailContent)
        val strengthTasks = session.routine.stages[2].tasks
        val order = strengthTasks.indexOfFirst { it.id == task.id } + 1
        detailContent.addView(label("근력운동 · ${order}/${strengthTasks.size}종목 · 세트 완료 ${doneSets(task)} / ${strengthSetCount(task)}",
            12, false, textSecondary))
        addGap(detailContent, 12)
        if (task.id.isNotEmpty() && info != null) {
            // Two public-domain frames loop like a short clip; stick figure only when no image exists.
            detailContent.addView(ExerciseGuide.create(this, task.id),
                LinearLayout.LayoutParams(-1, -2))
            addGap(detailContent, 4)
            detailContent.addView(label(
                "${info.area} · ${info.equipment} · 시작·끝 자세 반복 이미지",
                11, false, textSecondary))
            addGap(detailContent, 16)
        }
        detailContent.addView(label("세트 기록", 15, true))
        detailContent.addView(buildSetTable(task))
        addGap(detailContent, 12)
        val slot = strengthTasks.indexOfFirst { it.id == task.id }
        if (slot >= 0) {
            detailContent.addView(actionButton("대체 운동 선택", false) { showExerciseSwap(slot) }
                .apply { tag = "popup_swap" })
            addGap(detailContent, 16)
        }
        detailContent.addView(label("동작 순서", 15, true))
        addGap(detailContent, 8)
        steps.forEachIndexed { index, step ->
            val stepBox = card()
            stepBox.addView(label("${index + 1}. " + step, 13, false))
            detailContent.addView(stepBox)
            addGap(detailContent, 8)
        }
        if (task.id.isNotEmpty() && info != null) {
            detailContent.addView(actionButton("운동 가이드 영상 검색 ↗", false) {
                val query = Uri.encode(task.name + " 운동 자세 가이드")
                val videoSearch = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + query))
                try {
                    startActivity(videoSearch)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "영상을 열 수 있는 브라우저가 필요해요.",
                        Toast.LENGTH_LONG).show()
                }
            }.apply { contentDescription = task.name + " 운동 가이드 영상 검색" })
            addGap(detailContent, 12)
        }
        if (slot >= 0) {
            detailContent.addView(label("지난 기록: ${exercisePrefs.log(task.id)}",
                12, false, textSecondary))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        showGuideSheet(task.name, panel)
    }

    /**
     * Figma 10: the exercise guide is a bottom sheet without a close button. The 48dp handle row closes it
     * on tap, and dragging the sheet down (BottomSheetBehavior hideable) closes it as well.
     */
    private fun showGuideSheet(title: String, content: View) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val root = column().apply {
            background = GradientDrawable().apply {
                setColor(Theme.Colors.surface)
                val r = dp(Theme.Radius.card + 8).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(0, 0, 0, dp(Theme.Space.three))
        }
        val handle = FrameLayout(this).apply {
            tag = "guide_sheet_handle"
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = "동작 가이드 닫기"
            setOnClickListener { sheet.dismiss() }
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Theme.Colors.outline); cornerRadius = dp(2).toFloat()
                }
            }, FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
        }
        root.addView(handle, LinearLayout.LayoutParams(-1, dp(48)))
        val titleView = label(title, 20, true).apply {
            setPadding(dp(Theme.Space.two), 0, dp(Theme.Space.two), dp(Theme.Space.one))
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, -2))
        sheet.setContentView(root)
        sheet.setOnShowListener {
            val frame = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            frame?.setBackgroundColor(Color.TRANSPARENT)
            if (frame != null) {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(frame)
                behavior.isHideable = true
                behavior.skipCollapsed = true
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }
        sheet.setOnDismissListener { renderCurrent(); refreshTimer() }
        sheet.show()
    }

    private fun showExerciseSwap(slot: Int) {
        val options = data.alternatives(session.day, slot, session.bodyFocus)
        MaterialAlertDialogBuilder(this)
            .setTitle("대체 운동 선택")
            .setItems(options.map { it.second }.toTypedArray()) { _, index ->
                if (data.replaceExercise(session.day, slot, options[index].first, session.bodyFocus)) {
                    session.refreshRoutine()
                    renderCurrent()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshTimer() {
        if (!::timerValue.isInitialized) return
        session.reconcile()
        val stageName = session.routine.stages[session.stage].name
        timerValue.text = formatTime(session.remaining)
        val finishLabel = if (session.elapsed() > 0) "오늘 운동 완료" else "운동 종료 · 기록 없음"
        timerLabel.text = if (session.finished) finishLabel else "$stageName · 남은 시간"
        timerBadge.text = if (session.finished) "종료" else stageName
        val elapsed = session.elapsed()
        progress.max = max(3600, elapsed)
        progress.progress = elapsed
        if (::dockProgressFill.isInitialized) {
            val fraction = (elapsed / 3600f).coerceIn(0f, 1f)
            val fillParams = dockProgressFill.layoutParams as LinearLayout.LayoutParams
            val restView = (dockProgressFill.parent as LinearLayout).getChildAt(1)
            if (fillParams.weight != fraction) {
                fillParams.weight = fraction
                (restView.layoutParams as LinearLayout.LayoutParams).weight = 1f - fraction
                dockProgressFill.requestLayout()
            }
        }
        timeMeta.text = "총 운동 ${formatTime(elapsed)} · 기본 목표 60분${if (elapsed > 3600) " · ${formatTime(elapsed - 3600)} 초과" else ""}"
        val heart = getSharedPreferences("preferences", MODE_PRIVATE).getInt("watch_heart_rate", 0)
        val heartAt = getSharedPreferences("preferences", MODE_PRIVATE).getLong("watch_heart_rate_at", 0L)
        val heartAge = System.currentTimeMillis() - heartAt
        heartRateValue.text = if (heart in 30..240 && heartAge in 0L..10_000L) "${heart} bpm" else "연결대기"
        dockHeartRate.text = "♥ ${heartRateValue.text}"
        val phase = session.phase()
        if (phase == null) {
            phaseRpe.visibility = View.GONE; phaseTimeRow.visibility = View.GONE; phaseBar.visibility = View.GONE
        }
        when {
            session.finished -> {
                timerCueTitle.text = finishLabel
                timerCueDetail.text = if (session.elapsed() > 0) "수분을 보충하고 충분히 쉬세요." else "운동 시작을 누르면 활동 시간이 기록됩니다."
            }
            phase != null -> {
                val task = phase.second
                val left = session.phaseSecondsRemaining()
                timerCueTitle.text = task.name
                phaseRpe.text = "RPE ${task.rpe}"
                phaseRpe.visibility = if (task.rpe.isBlank()) View.GONE else View.VISIBLE
                phaseTime.text = formatTime(left)
                phaseTimeRow.visibility = View.VISIBLE
                phaseBar.visibility = View.VISIBLE
                phaseBar.progress = if (task.seconds > 0) ((task.seconds - left) * 1000 / task.seconds).coerceIn(0, 1000) else 0
                currentPhaseTime?.text = formatTime(left)
                val upcoming = session.routine.stages[session.stage].tasks.getOrNull(phase.first + 1)
                timerCueDetail.text = (if (upcoming != null) "다음 · ${upcoming.name}\n" else "") + displayDetail(task.description)
            }
            else -> {
                val strengthTask = if (session.stage == 2) currentStrengthTask() else null
                timerCueTitle.text = when (session.stage) {
                    0 -> "워밍업으로 몸 깨우기"
                    2 -> strengthTask?.name ?: "근력 세트 완료"
                    else -> "유산소 정리"
                }
                timerCueDetail.text = when (session.stage) {
                    0 -> "관절 가동성을 천천히 높이고 호흡을 안정시키세요."
                    2 -> strengthTask?.let { displayDetail(it.detail) } ?: "세트 입력 후 체크 · 휴식 자동 시작 · 워치에서도 완료 가능"
                    else -> "반동 없이 편안하게 늘리고 호흡을 정리하세요."
                }
            }
        }
        if (::restPanel.isInitialized) {
            val rest = session.restSeconds()
            restPanel.visibility = if (rest > 0 && session.stage == 2 && !session.finished) View.VISIBLE else View.GONE
            restValue.text = formatTime(rest) + if (!session.running) " · 일시정지" else " 남음"
        }
        // Dock: "대분류 · 현재 항목". Remaining time is the current item's (same number as the big timer);
        // strength has no allotted time per machine, so it only shows the overall flow.
        val dockItem = when {
            session.finished -> null
            phase != null -> phase.second.name
            session.stage == 2 -> currentStrengthTask()?.name
            else -> null
        }
        dockStage.text = if (session.finished) finishLabel else listOfNotNull(stageName, dockItem).joinToString(" · ")
        val dockTotal = "전체시간 ${formatTime(elapsed)} / 60:00"
        dockTime.text = when {
            session.finished -> "운동 완료 · $dockTotal"
            phase != null -> "남은시간 ${formatTime(session.phaseSecondsRemaining())} · $dockTotal"
            session.stage == 2 -> dockTotal
            else -> "남은시간 ${formatTime(session.remaining)} · $dockTotal"
        }
        // Figma dock: pause/play 20dp and arrows 18dp icons instead of text glyphs.
        dockButton(play, if (session.finished) "새 운동 시작" else if (session.running) "일시정지" else "운동 시작",
            if (session.finished) R.drawable.ic_replay else if (session.running) R.drawable.ic_pause else R.drawable.ic_play, 20, true)
        dockButton(previous, "이전", R.drawable.ic_arrow_back, 18, true)
        previous.alpha = if (session.stage == 0) .4f else 1f
        if (session.stage == LAST_STAGE) dockButton(next, "운동 종료", 0, 18, false)
        else dockButton(next, "다음", R.drawable.ic_arrow_forward, 18, false)
        next.isEnabled = !session.finished
        next.alpha = if (session.finished) .4f else 1f
        // Before starting, the primary start button is the only exposed action.
        val starting = !session.finished && !session.running && session.elapsed() == 0
        previous.visibility = if (starting || session.finished) View.GONE else View.VISIBLE
        next.visibility = if (starting || session.finished) View.GONE else View.VISIBLE
        val strengthTasks = session.routine.stages[2].tasks
        val totalSets = strengthTasks.sumOf { task -> Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3 }
        val doneSets = strengthTasks.sumOf { task ->
            val count = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3
            (1..count).count { n -> "strength-${session.day}-${task.id}-$n" in session.checked }
        }
        val volume = SetRecordStore(this).completed(data.localToday()).sumOf { it.volume }.toInt()
        // Daily, not per-session: includes multiple workouts on the same Korean calendar day.
        // Previous dates are never combined with today's displayed total.
        dockCalories.text = "오늘 누적 ${"%.1f".format(EnergyLedger(this).estimate(data.localToday(), org.json.JSONObject(session.snapshot())))} kcal · 추정"
        val watchKcal = WatchKcal.fresh(this, session.sessionStartedAt)
        dockWatchKcal.visibility = if (watchKcal != null) View.VISIBLE else View.GONE
        if (watchKcal != null) dockWatchKcal.text = "워치 측정 ${"%.0f".format(watchKcal.totalKcal)} kcal · 기초대사 포함"
    }

    private fun showWorkoutResult() {
        val date = data.localToday()
        val entries = StageLog(this).entries(date)
        val totalKcal = EnergyLedger(this)
            .estimate(date, org.json.JSONObject(session.snapshot())).coerceAtLeast(0.0)
        val sumMet = entries.sumOf { it.metSeconds }
        val names = listOf("준비운동", "유산소 ① · 사이클", "근력운동", "유산소 ② · 일립티컬", "스트레칭")
        val result = column().apply {
            setPadding(dp(Theme.Space.two), dp(Theme.Space.one),
                dp(Theme.Space.two), dp(Theme.Space.one))
        }
        result.addView(label("실제 운동 ${formatTime(session.elapsed())} · 약 ${totalKcal.toInt()} kcal",
            17, true, mint))
        if (entries.isEmpty()) {
            result.addView(label("단계별 기록이 없는 이전 운동은 합계만 표시돼요.", 12, false, textSecondary))
        } else {
            entries.forEach { entry ->
                val kcal = if (sumMet > 0) (totalKcal * entry.metSeconds / sumMet).toInt() else 0
                val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(label(names[entry.stage], 13, false), LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(label("${formatTime(entry.seconds.toInt())} · 약 ${kcal} kcal",
                    12, true, mint))
                result.addView(line)
                addGap(result, 8)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("운동 결과")
            .setView(result)
            .setNegativeButton("닫기", null)
            .setPositiveButton("달력에서 기록 보기") { _, _ ->
                startActivity(Intent(this, com.move60.workout.architecture.ComposeActivity::class.java)
                    .putExtra("move60_open_calendar", true))
                finish()
            }.show()
    }

    private fun afterAction() { syncService(); renderCurrent(); refreshTimer() }

    private fun syncService() {
        session.reloadIfChanged()
        if (recoveryMode) return
        WatchStatePublisher.publishSnapshot(this, session.snapshot())
        val intent = Intent(this, WorkoutService::class.java)
        if (session.running && !session.finished) {
            intent.action = WorkoutService.ACTION_SYNC
            intent.putExtra(WorkoutService.EXTRA_SNAPSHOT, session.snapshot())
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            } catch (e: Exception) {
                CrashReporter.log(this, "서비스 시작 오류: " + e.javaClass.simpleName + ": " + e.message)
                Toast.makeText(this, "알림 서비스를 시작하지 못했어요. 알림 진단을 확인하세요.", Toast.LENGTH_LONG).show()
            }
        } else {
            // Pausing or finishing stops the service; persist the new UI snapshot and
            // refresh pinned widgets immediately instead of leaving a live countdown.
            getSharedPreferences("timer", MODE_PRIVATE).edit()
                .putString("snapshot", session.snapshot()).apply()
            WorkoutWidget.refresh(this)
            stopService(intent)
        }
    }

    private fun showNotificationDiagnostics() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = manager.getNotificationChannel(WorkoutService.CHANNEL_PROGRESS)
        val allowed = manager.areNotificationsEnabled() &&
            (channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE) &&
            (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val lastError = getSharedPreferences("move60_service_diagnostics", MODE_PRIVATE).getString("latest_error", null)
        val actualActive = try {
            manager.activeNotifications.any { it.id == 601 && it.packageName == packageName }
        } catch (_: Exception) { false }
        val cardStatus = getSharedPreferences("timer", MODE_PRIVATE).getString("card_status", "아직 운동 알림 게시 기록 없음")
        val diagnostic = (if (allowed) "알림 권한: 허용" else "알림 권한: 확인 필요") +
            "\n운동 알림 채널: " + (if (channel == null) "운동 시작 시 생성" else if (channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) "차단됨" else "허용됨") +
            "\n운동 상태: " + (if (session.running) "진행 중" else "대기 또는 정지") +
            "\n실제 알림 게시 여부: " + (if (actualActive) "게시됨" else "게시되지 않음") +
            "\n카드형 알림 상태: " + cardStatus +
            "\n잠금화면 표시: 기기 설정에서도 '알림 내용 표시'를 허용해야 해요" +
            (if (lastError == null) "" else "\n최근 알림 오류: " + lastError) +
            "\n\n운동 시작 버튼을 누른 상태에서만 진행 알림이 표시돼요."
        MaterialAlertDialogBuilder(this).setTitle("운동 알림 진단").setMessage(diagnostic)
            .setNegativeButton("닫기", null)
            .setPositiveButton("알림 설정 열기") { _, _ -> openLockscreenSettings() }.show()
    }

    private fun openLockscreenSettings() {
        try {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, WorkoutService.CHANNEL_PROGRESS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun playTone() {
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 35)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
            handler.postDelayed({ tone.release() }, 220)
        } catch (ignored: Exception) {}
    }

    private fun showRecoveryScreen(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(24))
            setBackgroundColor(this@MainActivity.background)
        }
        val title = TextView(this).apply {
            text = "MOVE60 · 안전 복구"
            textSize = Theme.Type.title
            setTextColor(mint)
        }
        val description = TextView(this).apply {
            text = message + "\n앱을 삭제하지 마세요. 오류 기록을 복사해 알려주면 원인을 확인할 수 있어요."
            textSize = Theme.Type.body
            setTextColor(textPrimary)
            setPadding(0, dp(24), 0, dp(24))
        }
        layout.addView(title)
        layout.addView(description)
        layout.addView(actionButton("오류 기록 확인 · 복사", false) {
            CrashReporter.showDiagnostics(this)
        })
        setContentView(layout)
    }

    private fun label(text: String, size: Int, bold: Boolean, color: Int = textPrimary) =
        DashboardUi.label(this, text, size, bold, color)
    private fun column() = DashboardUi.column(this)
    private fun row() = DashboardUi.row(this)
    private fun card() = DashboardUi.card(this)
    private fun actionButton(title: String, main: Boolean, onClick: () -> Unit) =
        DashboardUi.actionButton(this, title, main, compact = true, onClick = onClick)
    private val dockKeys = HashMap<TextView, String>()
    /**
     * Sets a dock label with its tinted icon right next to it (Figma: icon + label centred). The view is only
     * rewritten when the label or icon changes: the dock refreshes every tick, and re-setting identical text
     * would keep emitting accessibility events and stop UI automation from ever seeing an idle screen.
     */
    private fun dockButton(button: TextView, label: String, res: Int, sizeDp: Int, start: Boolean) {
        val key = "$label|$res|${button.currentTextColor}"
        if (dockKeys[button] == key) return
        dockKeys[button] = key
        val icon = if (res == 0) null else androidx.core.content.ContextCompat.getDrawable(this, res)?.mutate()
        if (icon == null) { button.text = label; return }
        icon.setBounds(0, 0, dp(sizeDp), dp(sizeDp))
        icon.setTint(button.currentTextColor)
        val span = android.text.SpannableStringBuilder(if (start) "  $label" else "$label  ")
        val at = if (start) 0 else label.length + 1
        span.setSpan(android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_CENTER), at, at + 1,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        button.text = span
    }
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) =
        DashboardUi.rounded(this, color, radiusDp, stroke)
    private fun dp(number: Int) = Theme.dp(this, number)
    private fun addGap(container: LinearLayout, height: Int) =
        DashboardUi.gap(this, container, height)
    private fun formatTime(seconds: Int): String {
        val n = max(0, seconds)
        return "%02d:%02d".format(n / 60, n % 60)
    }
    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) session.reloadIfChanged()
        if (::session.isInitialized && ::timerValue.isInitialized) {
            soundEnabled = getSharedPreferences("preferences", MODE_PRIVATE).getBoolean("sound", true)
            if (!session.running && session.elapsed() == 0 && data.routine(session.day, focus = session.bodyFocus) != session.routine) {
                session.refreshRoutine()
                session.resetAll()
            }
            renderCurrent()
            refreshTimer()
        }
        if (::updateManager.isInitialized && !recoveryMode) {
            try { updateManager.resumePendingInstall() }
            catch (e: Exception) { CrashReporter.log(this, "업데이트 복원 오류: " + e.javaClass.simpleName + ": " + e.message) }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::updateManager.isInitialized) updateManager.onDestroy()
        super.onDestroy()
    }
}


