package com.move60.workout

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.move60.workout.Move60Theme as Theme
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.format.DateTimeFormatter
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
    private lateinit var stageStack: LinearLayout
    private lateinit var dayStrip: LinearLayout
    private lateinit var summary: LinearLayout
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
    private lateinit var dockTime: TextView
    private lateinit var dockCalories: TextView
    private lateinit var dockHeartRate: TextView
    private lateinit var play: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var trackingSummary: TextView
    private lateinit var sectionCount: TextView
    private val publishDraft = Runnable { if (::session.isInitialized) syncService() }
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhaseKey = ""
    private lateinit var focusRow: LinearLayout
    private var lastDay = -1
    private var lastStage = -1
    private var lastChecked = -1
    private var soundEnabled = true
    private var calorieWeightKg = 63.0
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
                renderDayStrip()
                renderStages()
                renderSummary()
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
                renderStages()
                renderSummary()
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
        calorieWeightKg = getSharedPreferences("preferences", MODE_PRIVATE).getFloat("calorie_weight_kg", 63f).toDouble()
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
                    AlertDialog.Builder(this)
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


    private fun showPerformanceDashboard() {
        val history = WorkoutHistory(this)
        val month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"))
        val days = history.month(month)
        val trained = days.count { it.activeSeconds > 0L }
        val minutes = days.sumOf { it.activeMinutes }
        val estimated = days.sumOf { EnergyLedger(this).estimate(it.date) }.toInt()
        val panel = card()
        panel.addView(label("${month.year}년 ${month.monthValue}월 운동 요약", 19, true, mint))
        addGap(panel, 12)
        panel.addView(label("운동한 날  ${trained}일", 17, true))
        panel.addView(label("누적 운동  ${minutes}분", 17, true))
        panel.addView(label("추정 소모  약 ${estimated} kcal", 17, true))
        addGap(panel, 12)
        panel.addView(label("일별 운동 시간", 13, true, textSecondary))
        panel.addView(MonthlyActivityChart(this, days.map { it.activeMinutes }))
        addGap(panel, 10)
        panel.addView(label("실제 활동시간과 입력한 완료 세트 기반 추정치입니다. 이전 기록은 평균 MET 4.5를 사용합니다.", 12, false, textSecondary))
        AlertDialog.Builder(this).setTitle("운동 성과")
            .setView(ScrollView(this).apply { isFillViewport = false; addView(panel) })
            .setPositiveButton("닫기", null).show()
    }

    private fun showWorkoutCalendar() {
        val history = WorkoutHistory(this)
        val ledger = EnergyLedger(this)
        val root = card()
        val navigation = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val monthLabel = label("", 18, true, mint).apply { gravity = Gravity.CENTER }
        var month = java.time.YearMonth.from(data.localToday())
        var selectedDate = data.localToday()
        val grid = GridLayout(this).apply { columnCount = 7; contentDescription = "운동 기록 달력" }
        val totals = label("", 13, false, textSecondary)
        val selected = label("", 14, true)
        fun render() {
            monthLabel.text = "${month.year}년 ${month.monthValue}월"
            grid.removeAllViews()
            listOf("월", "화", "수", "목", "금", "토", "일").forEach { name ->
                grid.addView(label(name, 12, true, textSecondary).apply { gravity = Gravity.CENTER },
                    GridLayout.LayoutParams().apply { width = 0; height = dp(30); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
            repeat(month.atDay(1).dayOfWeek.value - 1) {
                grid.addView(View(this), GridLayout.LayoutParams().apply { width = 0; height = dp(46); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val trained = history.record(date).activeSeconds > 0L
                val cell = column().apply {
                    gravity = Gravity.CENTER
                    contentDescription = "$date · ${if (trained) "운동 기록 있음" else "운동 기록 없음"}"
                    if (date == selectedDate) background = rounded(subtle, 8)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { selectedDate = date; render() }
                }
                cell.addView(label(day.toString(), 13, date == selectedDate).apply { gravity = Gravity.CENTER })
                cell.addView(View(this).apply { tag = "calendar-dot-$date"; background = rounded(mint, 4); visibility = if (trained) View.VISIBLE else View.INVISIBLE }, LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(3) })
                grid.addView(cell, GridLayout.LayoutParams().apply { width = 0; height = dp(46); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
            val days = history.month(month)
            val kcal = days.sumOf { ledger.estimate(it.date) }.toInt()
            totals.text = "${history.trainedDays(month)}일 · ${history.totalActiveSeconds(month) / 60}분 · 약 ${kcal} kcal\n● 운동한 날"
            selected.text = "$selectedDate · ${history.record(selectedDate).activeMinutes}분 · 약 ${ledger.estimate(selectedDate).toInt()} kcal"
        }
        navigation.addView(actionButton("‹", false) { month = month.minusMonths(1); selectedDate = month.atDay(1); render() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        navigation.addView(monthLabel, LinearLayout.LayoutParams(0, dp(44), 1f))
        navigation.addView(actionButton("›", false) { month = month.plusMonths(1); selectedDate = month.atDay(1); render() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(navigation)
        root.addView(grid)
        addGap(root, 12)
        root.addView(totals)
        addGap(root, 8)
        root.addView(selected)
        render()
        AlertDialog.Builder(this).setTitle("운동 달력")
            .setView(ScrollView(this).apply { isFillViewport = false; addView(root) })
            .setPositiveButton("닫기", null).show()
    }

    private fun constructInterface() {
        content.removeAllViews()
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("MOVE", 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("60", 24, true, mint))
        header.addView(actionButton("운동 달력", false) { showWorkoutCalendar() }.apply { textSize = 11f; tag = "phone_calendar" }, LinearLayout.LayoutParams(-2, dp(40)).apply { leftMargin = dp(8) })
        header.addView(actionButton("앱 설정", false) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra("day", session.day))
        }.apply { textSize = 11f; tag = "phone_settings" }, LinearLayout.LayoutParams(-2, dp(40)).apply { leftMargin = dp(8) })
        content.addView(header)
        addGap(content, 16)
        content.addView(label(data.localToday().format(DateTimeFormatter.ofPattern("M월 d일"))+" · 오늘 루틴", 20, true))
        addGap(content, 12)

        dayStrip = row()
        content.addView(dayStrip)
        addGap(content, 12)
        focusRow = row().apply { tag = "body_focus_selector" }
        content.addView(focusRow)
        addGap(content, 12)
        // Keep detailed timer information above the long daily summary.
        // Workout actions live outside the ScrollView in the bottom dock.
        content.addView(label("지금 할 운동", 19, true))
        addGap(content, Theme.Space.two)
        content.addView(makeTimerCard())
        restPanel = card().apply { tag = "phone_rest"; visibility = View.GONE }
        restValue = label("", 23, true, mint).apply { tag = "phone_rest_time" }
        restPanel.addView(label("세트 휴식", 12, true, textSecondary))
        restPanel.addView(restValue)
        val restActions = row()
        restActions.addView(actionButton("+15초", false) { session.adjustRest(15); syncService(); refreshTimer() }.apply { tag="phone_rest_add" },LinearLayout.LayoutParams(0,dp(48),1f))
        restActions.addView(actionButton("건너뛰기", false) { session.skipRest(); syncService(); refreshTimer() }.apply { tag="phone_rest_skip" },LinearLayout.LayoutParams(0,dp(48),1f).apply { leftMargin=dp(6) })
        restPanel.addView(restActions)
        content.addView(restPanel)
        addGap(content, 14)
        val heading = row()
        heading.addView(label("운동 · 세트 기록",18,true).apply { tag = "phone_training_heading" },LinearLayout.LayoutParams(0,-2,1f))
        sectionCount = label("",11,false,textSecondary)
        heading.addView(sectionCount)
        content.addView(heading)
        addGap(content, Theme.Space.two)
        stageStack = column().apply { tag = "phone_stage_stack" }
        content.addView(stageStack)
        addGap(content, Theme.Space.two)
        summary = card().apply { tag = "phone_dashboard_summary" }
        content.addView(summary)
        addGap(content, Theme.Space.one)
        trackingSummary = label("",12,false,textSecondary)
        renderDayStrip()
        renderSummary()
        renderStages()
        refreshTimer()
    }

    private fun renderBodyFocus() {
        if (!::focusRow.isInitialized) return
        focusRow.removeAllViews()
        listOf("upper" to "상체", "lower" to "하체").forEachIndexed { index, (value, title) ->
            val selected = session.bodyFocus == value
            val button = actionButton((if (selected) "✓ " else "") + title + " 운동", selected) {
                if (!session.selectBodyFocus(value)) {
                    Toast.makeText(this, "운동 중에는 부위를 바꿀 수 없어요. 운동을 종료한 뒤 선택해 주세요.", Toast.LENGTH_LONG).show()
                } else {
                    afterAction()
                }
            }.apply { tag = "body_focus_"+value; contentDescription = title + " 운동 선택"; isSelected = selected }
            focusRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin=dp(8) })
        }
    }

    private fun renderDayStrip() {
        dayStrip.removeAllViews()
        val labels = listOf("월", "화", "수", "목", "금")
        for (d in 1..5) {
            val active = session.day == d
            val day = TextView(this).apply {
                text = labels[d - 1] + if (data.localToday().dayOfWeek.value == d) " •" else ""
                setTextSize(16f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) this@MainActivity.background else textSecondary)
                gravity = Gravity.CENTER
                background = rounded(if (active) mint else panel, 13, if (active) mint else subtle)
                setOnClickListener {
                    if (session.day != d) {
                        if (session.running) { session.pause(); syncService() }
                        session.setDay(d)
                        renderDayStrip()
                        renderSummary()
                        renderStages()
                        refreshTimer()
                        syncService()
                    }
                }
            }
            dayStrip.addView(day, LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                if (d > 1) leftMargin = dp(6)
            })
        }
    }

    private fun renderSummary() {
        if (!::summary.isInitialized) return
        summary.removeAllViews()
        val routine = session.routine
        summary.addView(label("60분 운동 계획", 20, true))
        addGap(summary, Theme.Space.one)
        summary.addView(label("준비 5분 · 유산소 "+routine.stages[1].minutes+"분 · 근력 "+routine.stages[2].minutes+"분 · 정리 5분",12,false,textSecondary))
        addGap(summary, Theme.Space.one)
        val kcal = CalorieEstimator.totalKcal(routine,calorieWeightKg)
        summary.addView(label("예상 소모량  약 "+kcal+" kcal",24,true,mint))
        addGap(summary, Theme.Space.one)
        summary.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "phone_dashboard_progress"
            max = 4
            progress = session.completed.size.coerceIn(0, 4)
            progressTintList = Theme.progressTint()
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Theme.Colors.outline)
        }, LinearLayout.LayoutParams(-1, dp(8)))
        if (::trackingSummary.isInitialized) {
            addGap(summary, Theme.Space.one)
            summary.addView(trackingSummary)
        }
    }

    /** A sibling of the weighted ScrollView, so controls never scroll or cover content. */
    private fun makeWorkoutControls(): LinearLayout {
        val dock = column().apply {
            contentDescription = "고정 운동 컨트롤"
            setBackgroundColor(Theme.Colors.surface)
            setPadding(dp(Theme.Space.two), dp(Theme.Space.one),
                dp(Theme.Space.two), dp(Theme.Space.two))
            elevation = dp(8).toFloat()
        }
        val state = row().apply { gravity = Gravity.CENTER_VERTICAL }
        dockStage = label("준비운동", 14, true, mint)
        dockStage.setSingleLine(true)
        dockStage.ellipsize = android.text.TextUtils.TruncateAt.END
        dockTime = label("", 12, false, textSecondary)
        state.addView(dockStage, LinearLayout.LayoutParams(0, -2, 1f))
        state.addView(dockTime, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        dockHeartRate = label("♥ 연결대기", 12, true, mint).apply {
            contentDescription = "하단 실시간 심박수"
            gravity = Gravity.CENTER
            minHeight = dp(40)
            minWidth = dp(76)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(panelLight, 9, subtle)
        }
        state.addView(dockHeartRate, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        dock.addView(state)
        dockCalories = label("오늘 누적 0.0 kcal · 추정", 12, true, mint).apply {
            contentDescription = "오늘 날짜에 실제 운동한 시간의 누적 소모 칼로리 추정치"
            tag = "phone_session_calories"
        }
        dock.addView(dockCalories)
        addGap(dock, 8)
        play = actionButton("▶ 운동 시작", true) {
            if (session.running) session.pause() else {
                if (session.finished || session.elapsed() == 0) session.refreshRoutine()
                session.start()
                SetRecordStore(this).beginSession(session.day, session.sessionStartedAt)
            }
            syncService()
            renderSummary()
            renderStages()
            refreshTimer()
        }.apply { contentDescription = "운동 시작 또는 일시정지"; tag = "phone_play" }
        val buttons = row()
        previous = actionButton("← 이전 단계", false) { session.jumpTo(session.stage - 1); afterAction() }
        next = actionButton("다음 단계 →", false) { session.nextStage(); afterAction() }.apply { tag = "phone_next" }
        previous.textSize = 11f
        next.textSize = 11f
        play.textSize = 12f
        buttons.addView(previous, LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(play, LinearLayout.LayoutParams(0, dp(52), 1.35f).apply { leftMargin = dp(5) })
        buttons.addView(next, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(5) })
        dock.addView(buttons)
        return dock
    }

    private fun makeTimerCard(): LinearLayout {
        val box = card()
        val top = row()
        timerBadge = label("대기",11,true,mint)
        timerLabel = label("",12,true,textSecondary)
        top.addView(timerLabel,LinearLayout.LayoutParams(0,-2,1f))
        top.addView(timerBadge)
        box.addView(top)
        timerCueTitle = label("",22,true)
        timerCueDetail = label("",12,false,textSecondary)
        addGap(box,10)
        box.addView(timerCueTitle)
        box.addView(timerCueDetail)
        timerValue = label("",24,true)
        heartRateValue = label("연결대기",12,true).apply { contentDescription = "실시간 심박수" }
        timeMeta = label("",12,false,textSecondary)
        progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList=android.content.res.ColorStateList.valueOf(mint)
        }
        addGap(box,10)
        box.addView(progress,LinearLayout.LayoutParams(-1,dp(5)))
        box.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle("운동 초기화")
                .setItems(arrayOf("현재 단계 초기화","오늘 진행 초기화")) { _, which ->
                    if (which == 0) session.resetStage() else session.resetAll()
                    afterAction()
                }.setNegativeButton("취소",null).show()
            true
        }
        return box
    }

    private fun renderStages() {
        if (!::stageStack.isInitialized) return
        stageStack.removeAllViews()
        renderBodyFocus()
        lastDay = session.day
        lastStage = session.stage
        val selectedPhase = session.phase()?.first ?: -1
        val strengthTasks = session.routine.stages[2].tasks
        val totalStrengthSets = strengthTasks.sumOf { task ->
            Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 3
        }
        val completedStrengthSets = strengthTasks.sumOf { task ->
            val sets = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 3
            (1..sets).count { set -> "strength-${session.day}-${task.id}-${set}" in session.checked }
        }
        sectionCount.text = "근력 세트 ${completedStrengthSets} / ${totalStrengthSets} · 단계 ${session.completed.size}/4"
        for ((si, stage) in session.routine.stages.withIndex().sortedBy { if (it.index == session.stage) 0 else it.index+1 }) {
            val isCurrent = session.stage == si && !session.finished
            val done = si in session.completed || (session.finished && si <= session.stage)
            val block = card().apply {
                background = rounded(if (isCurrent) Theme.Colors.surfaceElevated else panel,
                    Theme.Radius.card, if (isCurrent) mint else Theme.Colors.outline)
            }
            val top = row().apply { gravity = Gravity.CENTER_VERTICAL }
            val number = label(if (done) "✓" else "${si + 1}", 15, true, if (isCurrent) background else mint).apply {
                gravity = Gravity.CENTER
                background = rounded(if (isCurrent) mint else subtle, 10)
            }
            top.addView(number, LinearLayout.LayoutParams(dp(35), dp(35)))
            val titles = column().apply { setPadding(dp(9), 0, 0, 0) }
            titles.addView(label(stage.name, 15, true))
            titles.addView(label(displayDetail(stage.subtitle), 11, false, textSecondary))
            top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label("${stage.minutes}분", 13, true, mint))
            block.addView(top)
            val detailBlock = column()
            detailBlock.visibility = if (isCurrent) View.VISIBLE else View.GONE
            val chevron = label(if (isCurrent) "⌃" else "⌄",16,true,textSecondary).apply { setPadding(dp(12),0,0,0) }
            top.addView(chevron)
            top.minimumHeight = dp(48)
            top.contentDescription = stage.name + " 상세 펼치기 또는 접기"
            top.setOnClickListener {
                detailBlock.visibility = if (detailBlock.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                chevron.text = if (detailBlock.visibility == View.VISIBLE) "⌃" else "⌄"
            }
            block.addView(detailBlock)
            val taskContainer = detailBlock
            addGap(taskContainer, Theme.Space.two)
            for ((ti, task) in stage.tasks.withIndex()) {
                val isPhase = si == 1 && session.stage == 1 && selectedPhase == ti && session.running
                val item = row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(Theme.Space.one), dp(Theme.Space.one), dp(Theme.Space.one), dp(Theme.Space.one))
                    if (isPhase) background = rounded(Theme.Colors.surfaceElevated, 10, mint)
                }
                val textBox = column()
                textBox.addView(label(task.name, 13, true, if (isPhase) mint else textPrimary))
                addGap(textBox, Theme.Space.one)
                textBox.addView(label(displayDetail(task.detail), 11, false, textSecondary))
                textBox.setPadding(dp(Theme.Space.one), dp(Theme.Space.one), dp(Theme.Space.one), dp(Theme.Space.one))
                if (si == 2) textBox.setOnClickListener { showDetails(si, task) }
                item.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
                if (si == 2) {
                val more = label("상세 ›", 11, true, mint).apply { tag = "strength_detail_"+task.id }
                more.setPadding(dp(3), dp(10), dp(3), dp(10))
                more.setOnClickListener { showDetails(si, task) }
                item.addView(more)
                }
                taskContainer.addView(item)
                if (si == 2) {
                    val count = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 3
                    val checkedCount = (1..count).count { set -> "strength-${session.day}-${task.id}-${set}" in session.checked }
                    val progress = label("세트 완료  ${checkedCount} / ${count}", 11, true, if (checkedCount == count) mint else textSecondary)
                    progress.setPadding(dp(12), dp(4), 0, dp(3))
                    taskContainer.addView(progress)
                    val setRecords = SetRecordStore(this)
                    val labels = row()
                    listOf("세트", "중량 kg", "횟수", "완료").forEachIndexed { index, title ->
                        labels.addView(label(title, 10, true, textSecondary).apply { gravity = Gravity.CENTER },
                            LinearLayout.LayoutParams(0, dp(28), if (index == 1) 1.3f else 1f))
                    }
                    taskContainer.addView(labels)
                    for (set in 1..count) {
                        val key = "strength-${session.day}-${task.id}-${set}"
                        val saved = setRecords.read(session.day, task.id, set, token = session.sessionStartedAt)
                        val fields = row().apply { gravity = Gravity.CENTER_VERTICAL }
                        fields.addView(label("${set}", 12, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(48), 1f))
                        fun input(value: String, hint: String, decimal: Boolean = false): EditText = EditText(this).apply {
                            setText(value)
                            this.hint = hint
                            textSize = 13f
                            setTextColor(textPrimary)
                            setHintTextColor(textSecondary)
                            setSingleLine(true)
                            setSelectAllOnFocus(true)
                            setPadding(dp(3), dp(4), dp(3), dp(4))
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or if (decimal) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                            contentDescription = "${task.name} ${set}세트 $hint"
                        }
                        val kg = input(saved.kg, "kg", true).apply { tag = "set-${task.id}-$set-kg" }
                        val reps = input(saved.reps, "회").apply { tag = "set-${task.id}-$set-reps" }
                        // Old per-set intensity is retained in storage for compatibility.
                        // New UI exposes only equipment load and repetitions.
                        val retainedEffort = saved.rpe.ifBlank { "5" }
                        fields.addView(kg, LinearLayout.LayoutParams(0, dp(48), 1.3f))
                        fields.addView(reps, LinearLayout.LayoutParams(0, dp(48), 1f))
                        val check = CheckBox(this).apply {
                            tag = "set-${task.id}-$set-check"
                            isChecked = key in session.checked || saved.completed
                            buttonTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(mint, textSecondary))
                            contentDescription = "${task.name} ${set}세트 완료"
                            gravity = Gravity.CENTER
                            setPadding(0, 0, 0, 0)
                        }
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
                            val record = SetRecordStore.Record(kg.text.toString(), reps.text.toString(), retainedEffort, checked)
                            if (checked && !record.valid) {
                                check.isChecked = false
                                Toast.makeText(this, "중량(맨몸 0kg)과 횟수를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                            } else {
                                session.toggleChecked(key, checked)
                                setRecords.write(session.day, task.id, set, record, token = session.sessionStartedAt)
                                val doneSets = (1..count).count { n -> "strength-${session.day}-${task.id}-$n" in session.checked }
                                progress.text = "세트 완료  ${doneSets} / ${count}"
                                progress.setTextColor(if (doneSets == count) mint else textSecondary)
                                if (checked) session.startRest(TrainingProgress.restDuration(task))
                                else session.skipRest()
                                syncService()
                                refreshTimer()
                            }
                        }
                        fields.addView(check, LinearLayout.LayoutParams(0, dp(48), 1f))
                        taskContainer.addView(fields)
                        val prior = setRecords.previous(task.id,set,session.sessionStartedAt)
                        if (prior != null) taskContainer.addView(label("지난 기록  "+prior.kg+" kg × "+prior.reps+"회",10,false,textSecondary).apply { setPadding(dp(12),0,0,dp(4)) })
                    }
                    taskContainer.addView(label("맨몸은 0kg · 중량과 횟수 자동 저장", 10, false, textSecondary))
                    addGap(taskContainer, Theme.Space.one)
                }
            }
            addGap(taskContainer, Theme.Space.one)
            taskContainer.addView(actionButton("이 단계로 타이머 이동 ↗", false) { session.jumpTo(si); afterAction() })
            stageStack.addView(block)
            addGap(stageStack, Theme.Space.two)
        }
        if (::trackingSummary.isInitialized) trackingSummary.text = "근력 세트 ${completedStrengthSets}/${totalStrengthSets} 완료 · 체크와 타이머는 별도로 작동"
    }

    private fun displayDetail(raw: String): String =
        raw.replace(Regex("(?i)\\s*[·,]?\\s*RPE\\s*\\d+(?:\\s*[-~]\\s*\\d+)?"), "")
            .replace(Regex("\\s*·\\s*·"), " · ")
            .trim(' ', '·', ',')

    private fun showDetails(stage: Int, task: WorkoutTask) {
        if (stage != 2) return
        val info = data.detail(task.id)
        val steps = info?.steps ?: listOf("운동 범위를 천천히 확인하고 무리 없는 자세를 유지하세요.")
        val caution = info?.caution ?: "통증이 생기면 강도를 줄이거나 중단하세요."
        val alternative = info?.alternative ?: "같은 부위의 다른 운동 기구"

        val panel = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val scroll = ScrollView(this)
        val detailContent = column()
        scroll.addView(detailContent)
        if (task.id.isNotEmpty()) {
            detailContent.addView(label("운동 가이드", 17, true, mint))
            addGap(detailContent, 12)
            detailContent.addView(actionButton("버추얼메이트Me 열기 ↗", false) { openVirtualMate() }.apply { tag = "virtual_mate_open" })
            addGap(detailContent, 6)
            detailContent.addView(label("별도 앱으로 이동합니다. 운동 영상은 해당 앱에서 선택해 주세요.", 12, false, textSecondary))
            addGap(detailContent, 16)
            detailContent.addView(label("부위: ${info?.area ?: "근력"}  ·  기구: ${info?.equipment ?: "운동 기구"}", 12, false, textSecondary))
            addGap(detailContent, 10)
            if (ExercisePhotos.hasPhotos(task.id)) {
                ExercisePhotos.addTo(this, detailContent, task.id) { caption ->
                    label(caption, 12, false, textSecondary)
                }
            }
            addGap(detailContent, 12)
            detailContent.addView(label("단계별 동작", 15, true, mint))
            for ((index, step) in steps.withIndex()) {
                val stepBox = column().apply {
                    background = rounded(panelLight, 10, subtle)
                    setPadding(dp(11), dp(10), dp(11), dp(10))
                }
                stepBox.addView(label("${index + 1}단계", 12, true, mint))
                stepBox.addView(label(step, 13, false))
                detailContent.addView(stepBox)
                addGap(detailContent, 7)
            }
            detailContent.addView(label("주의 사항", 15, true, Theme.Colors.heart))
            detailContent.addView(label(displayDetail(caution), 13, false))
            addGap(detailContent, 8)
            detailContent.addView(label("대체 운동", 15, true, mint))
            detailContent.addView(label(displayDetail(alternative), 13, false))
            addGap(detailContent, 12)
            val slot = session.routine.stages[2].tasks.indexOfFirst { it.id == task.id }
            detailContent.addView(label("최근 기록: ${exercisePrefs.log(task.id)}", 12, false, mint))
            addGap(detailContent, 8)
            detailContent.addView(label("중량·횟수는 운동 화면의 각 세트에서 입력하세요.", 12, false, textSecondary))
            addGap(detailContent, 6)
            if (slot >= 0) detailContent.addView(actionButton("같은 부위 운동으로 교체", false) { showExerciseSwap(slot) })
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(460)))
        AlertDialog.Builder(this).setTitle(task.name).setView(panel)
            .setPositiveButton("닫기", null).show()
    }

    private fun openVirtualMate() {
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.mybenefit.vmme.senior")
            startActivity(launch ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.mybenefit.vmme.senior")))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "버추얼메이트Me를 열 수 없어요. Play 스토어에서 앱을 확인해 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExerciseSwap(slot: Int) {
        val options = data.alternatives(session.day, slot, session.bodyFocus)
        AlertDialog.Builder(this).setTitle("대체 운동 선택")
            .setItems(options.map { it.second }.toTypedArray()) { _, index ->
                if (data.replaceExercise(session.day, slot, options[index].first, session.bodyFocus)) {
                    session.refreshRoutine()
                    renderStages()
                    renderSummary()
                }
            }.setNegativeButton("취소", null).show()
    }

    private fun refreshTimer() {
        if (!::timerValue.isInitialized) return
        session.reconcile()
        val stageName = session.routine.stages[session.stage].name
        timerValue.text = formatTime(session.remaining)
        val finishLabel = if (session.elapsed() > 0) "오늘 운동 완료" else "운동 종료 · 기록 없음"
        timerLabel.text = if (session.finished) finishLabel else "$stageName · 남은 시간"
        timerBadge.text = if (session.finished) "종료" else if (session.running) "● 진행 중" else "대기 / 일시정지"
        val elapsed = session.elapsed()
        progress.max = max(3600, elapsed)
        progress.progress = elapsed
        timeMeta.text = "총 운동 ${formatTime(elapsed)} · 기본 목표 60분${if (elapsed > 3600) " · ${formatTime(elapsed - 3600)} 초과" else ""}"
        val heart = getSharedPreferences("preferences", MODE_PRIVATE).getInt("watch_heart_rate", 0)
        val heartAt = getSharedPreferences("preferences", MODE_PRIVATE).getLong("watch_heart_rate_at", 0L)
        val heartAge = System.currentTimeMillis() - heartAt
        heartRateValue.text = if (heart in 30..240 && heartAge in 0L..10_000L) "${heart} bpm" else "연결대기"
        dockHeartRate.text = "♥ ${heartRateValue.text}"
        val phase = session.phase()
        when {
            session.finished -> {
                timerCueTitle.text = finishLabel
                timerCueDetail.text = if (session.elapsed() > 0) "수분을 보충하고 충분히 쉬세요." else "운동 시작을 누르면 활동 시간이 기록됩니다."
            }
            phase != null -> {
                timerCueTitle.text = phase.second.name
                timerCueDetail.text = "${displayDetail(phase.second.description)} · ${formatTime(session.phaseSecondsRemaining())} 남음 · ${phase.first + 1}/${session.routine.stages[1].tasks.size} 구간"
            }
            else -> {
                timerCueTitle.text = when (session.stage) {
                    0 -> "워밍업으로 몸 깨우기"
                    2 -> TrainingProgress.next(session)?.label ?: "근력 세트 완료"
                    else -> "마무리 스트레칭"
                }
                timerCueDetail.text = when (session.stage) {
                    0 -> "관절 가동성을 천천히 높이고 호흡을 안정시키세요."
                    2 -> "세트 입력 후 체크 · 휴식 자동 시작 · 워치에서도 완료 가능"
                    else -> "반동 없이 편안하게 늘리고 호흡을 정리하세요."
                }
            }
        }
        if (::restPanel.isInitialized) {
            val rest = session.restSeconds()
            restPanel.visibility = if (rest > 0 && session.stage == 2 && !session.finished) View.VISIBLE else View.GONE
            restValue.text = formatTime(rest) + if (!session.running) " · 일시정지" else " 남음"
        }
        dockStage.text = if (session.finished) finishLabel else stageName
        dockTime.text = "${formatTime(session.remaining)} 남음\n${formatTime(elapsed)} 누적"
        play.text = if (session.finished) "↻ 새 운동 시작" else if (session.running) "Ⅱ 일시정지" else "▶ 운동 시작"
        previous.alpha = if (session.stage == 0) .4f else 1f
        next.text = if (session.stage == 3) "운동 종료" else "다음 단계 →"
        next.isEnabled = !session.finished
        next.alpha = if (session.finished) .4f else 1f
        val strengthTasks = session.routine.stages[2].tasks
        val totalSets = strengthTasks.sumOf { task -> Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3 }
        val doneSets = strengthTasks.sumOf { task ->
            val count = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3
            (1..count).count { n -> "strength-${session.day}-${task.id}-$n" in session.checked }
        }
        sectionCount.text = "근력 세트 $doneSets / $totalSets · 단계 ${session.completed.size}/4"
        val volume = SetRecordStore(this).completed(data.localToday()).sumOf { it.volume }.toInt()
        trackingSummary.text = "근력 세트 $doneSets/$totalSets 완료 · 오늘 운동량 ${volume} kg·회"
        // Daily, not per-session: includes multiple workouts on the same Korean calendar day.
        // Previous dates are never combined with today's displayed total.
        dockCalories.text = "오늘 누적 ${"%.1f".format(EnergyLedger(this).estimate(data.localToday(), org.json.JSONObject(session.snapshot())))} kcal · 추정"
    }

    private fun afterAction() { syncService(); renderStages(); renderSummary(); refreshTimer() }

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
        AlertDialog.Builder(this).setTitle("운동 알림 진단").setMessage(diagnostic)
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
            setPadding(dp(22), dp(65), dp(22), dp(24))
            setBackgroundColor(this@MainActivity.background)
        }
        val title = TextView(this).apply {
            text = "MOVE60 · 안전 복구"
            textSize = 24f
            setTextColor(mint)
        }
        val description = TextView(this).apply {
            text = message + "\n앱을 삭제하지 마세요. 오류 기록을 복사해 알려주면 원인을 확인할 수 있어요."
            textSize = 15f
            setTextColor(textPrimary)
            setPadding(0, dp(22), 0, dp(22))
        }
        layout.addView(title)
        layout.addView(description)
        layout.addView(actionButton("오류 기록 확인 · 복사", false) {
            CrashReporter.showDiagnostics(this)
        })
        setContentView(layout)
    }

    private fun label(text: String, size: Int, bold: Boolean, color: Int = textPrimary) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = when {
            size >= 28 -> Theme.Type.display
            size >= 23 -> Theme.Type.title
            size >= 18 -> Theme.Type.heading
            size >= 14 -> Theme.Type.body
            else -> Theme.Type.caption
        }
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
        setLineSpacing(Theme.dp(this@MainActivity, 2).toFloat(), 1.04f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun card() = column().apply {
        background = Theme.shape(this@MainActivity)
        setPadding(dp(Theme.Space.two), dp(Theme.Space.two),
            dp(Theme.Space.two), dp(Theme.Space.two))
        clipToPadding = false
    }
    private fun actionButton(title: String, main: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = title
        textSize = if (main) Theme.Type.body else Theme.Type.caption
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (main) Theme.Colors.onPrimary else textPrimary)
        background = Theme.shape(this@MainActivity, if (main) mint else panelLight,
            Theme.Radius.control, if (main) mint else subtle)
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) =
        Theme.shape(this, color,
            when { radiusDp < 8 -> radiusDp; radiusDp < Theme.Radius.control -> Theme.Radius.control
                else -> Theme.Radius.card }, stroke)
    private fun dp(number: Int) = Theme.dp(this, number)
    private fun addGap(container: LinearLayout, height: Int) {
        // The global 8dp spacing grid controls even legacy widget builders.
        val aligned = ((height + 4) / Theme.Space.one * Theme.Space.one)
            .coerceAtLeast(Theme.Space.one)
        container.addView(View(this), LinearLayout.LayoutParams(1, dp(aligned)))
    }
    private fun formatTime(seconds: Int): String {
        val n = max(0, seconds)
        return "%02d:%02d".format(n / 60, n % 60)
    }
    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) session.reloadIfChanged()
        if (::session.isInitialized && ::summary.isInitialized && ::stageStack.isInitialized && ::timerValue.isInitialized && ::sectionCount.isInitialized) {
            soundEnabled = getSharedPreferences("preferences", MODE_PRIVATE).getBoolean("sound", true)
            calorieWeightKg = getSharedPreferences("preferences", MODE_PRIVATE).getFloat("calorie_weight_kg", 63f).toDouble()
            if (!session.running && session.elapsed() == 0 && data.routine(session.day, focus = session.bodyFocus) != session.routine) {
                session.refreshRoutine()
                session.resetAll()
            }
            renderSummary()
            renderStages()
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


