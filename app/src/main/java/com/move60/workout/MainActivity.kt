package com.move60.workout

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val background = Color.rgb(11, 17, 32)
    private val panel = Color.rgb(21, 31, 50)
    private val panelLight = Color.rgb(27, 42, 64)
    private val subtle = Color.rgb(37, 54, 77)
    private val mint = Color.rgb(180, 245, 110)
    private val textPrimary = Color.rgb(237, 245, 255)
    private val textSecondary = Color.rgb(166, 185, 205)
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
    private lateinit var progress: ProgressBar
    private lateinit var play: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var trackingSummary: TextView
    private lateinit var sectionCount: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhaseKey = ""
    private var lastDay = -1
    private var lastStage = -1
    private var lastChecked = -1
    private var soundEnabled = true
    private var calorieWeightKg = 63.0
    private var recoveryMode = false
    private val ticker = object : Runnable {
        override fun run() {
            val changed = session.reconcile()
            if (changed) {
                session.save()
                syncService()
                if (soundEnabled) playTone()
            }
            val phase = session.phase()?.first ?: -1
            val phaseKey = "${session.day}:${session.stage}:$phase"
            if (phaseKey != lastPhaseKey || changed || lastDay != session.day || lastStage != session.stage) {
                val previousPhase = lastPhaseKey
                lastPhaseKey = phaseKey
                if (previousPhase.isNotEmpty() && previousPhase != phaseKey && !changed && session.running && soundEnabled) playTone()
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 25)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; setBackgroundColor(this@MainActivity.background) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(52), dp(18), dp(90))
        }
        scroll.addView(content)
        setContentView(scroll)
        try {
            constructInterface()
        } catch (e: Exception) {
            CrashReporter.log(this, "화면 구성 오류: " + e.stackTraceToString().take(4000))
            showRecoveryScreen("화면을 표시하는 중 문제가 발생했어요.")
            return
        }
        handler.post(ticker)
        if (!recoveryMode) {
            if (session.running) syncService()
            updateManager.check(false)
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
                            updateManager.check(false)
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
        val estimated = (minutes * 4.5 * 3.5 * calorieWeightKg / 200.0).toInt()
        val panel = column().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }
        panel.addView(label("${month.year}년 ${month.monthValue}월 운동 요약", 19, true, mint))
        addGap(panel, 12)
        panel.addView(label("운동한 날  ${trained}일", 17, true))
        panel.addView(label("누적 운동  ${minutes}분", 17, true))
        panel.addView(label("추정 소모  약 ${estimated} kcal", 17, true))
        addGap(panel, 10)
        panel.addView(label("월간 실제 활동시간 기준, 평균 MET 4.5로 계산한 참고값입니다. 심박수 측정값이 아닙니다.", 12, false, textSecondary))
        AlertDialog.Builder(this).setTitle("운동 성과").setView(panel).setPositiveButton("닫기", null).show()
    }

    private fun showWorkoutCalendar() {
        val history = WorkoutHistory(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val totals = TextView(this).apply { textSize = 16f }
        val selected = TextView(this).apply { textSize = 15f }
        val calendar = CalendarView(this)
        root.addView(totals)
        root.addView(calendar)
        root.addView(selected)
        fun render(date: java.time.LocalDate) {
            val month = java.time.YearMonth.from(date)
            val seconds = history.totalActiveSeconds(month)
            totals.text = "${month.year}년 ${month.monthValue}월 · ${history.trainedDays(month)}일 · ${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
            selected.text = "${date} · 운동 ${history.record(date).activeMinutes}분"
        }
        calendar.setOnDateChangeListener { _, year, month, day ->
            render(java.time.LocalDate.of(year, month + 1, day))
        }
        render(data.localToday())
        AlertDialog.Builder(this).setTitle("운동 달력").setView(root)
            .setPositiveButton("닫기", null).show()
    }

    private fun constructInterface() {
        content.removeAllViews()
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("MOVE", 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("60", 24, true, mint))
        header.addView(actionButton("성과", false) { showPerformanceDashboard() }.apply { textSize = 11f }, LinearLayout.LayoutParams(-2, dp(35)).apply { leftMargin = dp(6) })
        header.addView(actionButton("운동 달력", false) { showWorkoutCalendar() }.apply { textSize = 11f }, LinearLayout.LayoutParams(-2, dp(35)).apply { leftMargin = dp(10) })
        header.addView(actionButton("업데이트 ↗", false) { updateManager.check(true) }.apply {
            textSize = 11f
            minHeight = dp(34)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        }, LinearLayout.LayoutParams(-2, dp(35)).apply { leftMargin = dp(13) })
        content.addView(header)
        addGap(content, 21)
        content.addView(label("WEEKDAY WORKOUT", 11, true, mint))
        addGap(content, 7)
        content.addView(label("나의 평일 운동 60분", 29, true))
        addGap(content, 4)
        content.addView(label("다양한 유산소 머신 + 머신·프리웨이트 근력 · 월~금", 13, false, textSecondary))
        addGap(content, 18)

        dayStrip = row()
        content.addView(dayStrip)
        addGap(content, 10)
        val weekEnd = data.weekMonday().plusDays(4)
        content.addView(label("${data.weekMonday().format(DateTimeFormatter.ofPattern("M/d"))} ~ ${weekEnd.format(DateTimeFormatter.ofPattern("M/d"))}  |  매주 월요일 운동 조합 자동 변경", 12, false, textSecondary))
        addGap(content, 17)

        summary = column()
        content.addView(summary)
        addGap(content, 18)
        content.addView(label("운동 타이머", 19, true))
        addGap(content, 11)
        content.addView(makeTimerCard())
        addGap(content, 18)
        val completedCard = card()
        completedCard.addView(label("오늘의 운동 진행 현황", 17, true))
        addGap(completedCard, 12)
        trackingSummary = label("", 14, false, textSecondary)
        completedCard.addView(trackingSummary)
        content.addView(completedCard)
        addGap(content, 18)
        val stageHeading = row().apply { gravity = Gravity.CENTER_VERTICAL }
        stageHeading.addView(label("운동 순서 · 상세 안내", 19, true), LinearLayout.LayoutParams(0, -2, 1f))
        sectionCount = label("", 12, false, textSecondary)
        stageHeading.addView(sectionCount)
        content.addView(stageHeading)
        addGap(content, 10)
        stageStack = column()
        content.addView(stageStack)
        addGap(content, 18)
        val settings = column().apply { setPadding(dp(5), dp(4), dp(5), dp(4)) }
        settings.addView(label("앱 설정", 13, true, textSecondary))
        addGap(settings, 8)
        val preferenceRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val soundSwitch = Switch(this).apply {
            text = "구간 전환 알림음"
            textSize = 12f
            setTextColor(textSecondary)
            isChecked = soundEnabled
            setOnCheckedChangeListener { _, enabled ->
                soundEnabled = enabled
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putBoolean("sound", enabled).apply()
            }
        }
        preferenceRow.addView(soundSwitch, LinearLayout.LayoutParams(0, -2, 1f))
        preferenceRow.addView(actionButton("알림 진단 ↗", false) { showNotificationDiagnostics() },
            LinearLayout.LayoutParams(-2, dp(40)))
        settings.addView(preferenceRow)
        addGap(settings, 8)
        settings.addView(actionButton("칼로리 계산 체중 설정 ↗", false) {
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(calorieWeightKg.toString())
            }
            AlertDialog.Builder(this).setTitle("칼로리 추정용 체중 (kg)")
                .setMessage("운동 칼로리는 추정치이며 실제 소모량과 다를 수 있어요.")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장") { _, _ ->
                    val value = input.text.toString().toDoubleOrNull()
                    if (value != null && value in 25.0..300.0) {
                        calorieWeightKg = value
                        getSharedPreferences("preferences", MODE_PRIVATE).edit().putFloat("calorie_weight_kg", value.toFloat()).apply()
                        renderSummary()
                    } else Toast.makeText(this, "25~300kg 범위로 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }.show()
        })
        settings.addView(label("MOVE60 15.0 · 안정형 기본 알림을 사용하며 카드형 알림은 분리되어 있어요.", 11, false, textSecondary))
        addGap(settings, 8)
        settings.addView(actionButton("오류 기록 확인 · 복사", false) { CrashReporter.showDiagnostics(this) })
        content.addView(settings)
        renderDayStrip()
        renderSummary()
        renderStages()
        refreshTimer()
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
        summary.removeAllViews()
        summary.background = rounded(panelLight, 20, Color.rgb(59, 91, 96))
        summary.setPadding(dp(17), dp(18), dp(17), dp(18))
        val r = session.routine
        val bar = row().apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(label(r.name, 13, true, mint), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(label(r.tag, 11, true, mint))
        summary.addView(bar)
        addGap(summary, 7)
        summary.addView(label(r.focus, 22, true))
        addGap(summary, 5)
        summary.addView(label(r.desc, 12, false, textSecondary))
        addGap(summary, 14)
        val stages = row()
        for ((index, stage) in r.stages.withIndex()) {
            val box = column().apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(19, 32, 50), 11, subtle)
                setPadding(dp(2), dp(9), dp(2), dp(9))
            }
            box.addView(label("${stage.minutes}분", 17, true, mint).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, dp(25)))
            box.addView(label(listOf("준비", "유산소", "근력", "정리")[index], 10, false, textSecondary).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(17)))
            stages.addView(box, LinearLayout.LayoutParams(0, dp(68), 1f).apply { if (index > 0) leftMargin = dp(5) })
        }
        summary.addView(stages)
        addGap(summary, 13)
        val kcal = CalorieEstimator.totalKcal(r, calorieWeightKg)
        summary.addView(label("예상 소모 칼로리  약 ${kcal} kcal", 18, true, mint))
        summary.addView(label("체중 ${calorieWeightKg.toInt()}kg 기준 · 준비 ${CalorieEstimator.stageKcal(r.stages[0], calorieWeightKg)} / 유산소 ${CalorieEstimator.stageKcal(r.stages[1], calorieWeightKg)} / 근력 ${CalorieEstimator.stageKcal(r.stages[2], calorieWeightKg)} / 정리 ${CalorieEstimator.stageKcal(r.stages[3], calorieWeightKg)} kcal", 11, false, textSecondary))
        summary.addView(label("MET 기반 60분 추정치 · 실제 소모량은 운동 강도와 휴식 시간에 따라 달라져요.", 11, false, textSecondary))
        summary.requestLayout()
    }

    private fun makeTimerCard(): LinearLayout {
        val box = card()
        val head = row().apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("MOVE60 TIMER", 13, true), LinearLayout.LayoutParams(0, -2, 1f))
        timerBadge = label("대기", 11, true, mint)
        head.addView(timerBadge)
        box.addView(head)
        addGap(box, 20)
        timerLabel = label("준비운동 · 남은 시간", 12, true, textSecondary)
        timerLabel.gravity = Gravity.CENTER
        box.addView(timerLabel)
        timerValue = label("05:00", 61, true)
        timerValue.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        timerValue.gravity = Gravity.CENTER
        box.addView(timerValue)
        addGap(box, 9)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 3600
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(subtle)
        }
        box.addView(progress, LinearLayout.LayoutParams(-1, dp(8)))
        addGap(box, 7)
        timeMeta = label("진행 0분 · 남은 60분", 12, false, textSecondary)
        box.addView(timeMeta)
        addGap(box, 15)
        val cue = column().apply {
            background = rounded(Color.rgb(13, 25, 41), 14, subtle)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        cue.addView(label("지금 할 운동", 11, true, textSecondary))
        addGap(cue, 5)
        timerCueTitle = label("워밍업으로 몸 깨우기", 16, true, mint)
        timerCueDetail = label("관절을 부드럽게 움직이며 시작하세요.", 12, false, textSecondary)
        cue.addView(timerCueTitle)
        addGap(cue, 6)
        cue.addView(timerCueDetail)
        box.addView(cue)
        addGap(box, 13)
        play = actionButton("▶ 운동 시작", true) {
            if (session.running) session.pause() else session.start()
            syncService()
            renderSummary()
            renderStages()
            refreshTimer()
        }
        box.addView(play)
        addGap(box, 9)
        val buttons = row()
        previous = actionButton("← 이전 단계", false) { session.jumpTo(session.stage - 1); afterAction() }
        next = actionButton("다음 단계 →", false) { session.nextStage(); afterAction() }
        buttons.addView(previous, LinearLayout.LayoutParams(0, dp(47), 1f))
        buttons.addView(next, LinearLayout.LayoutParams(0, dp(47), 1f).apply { leftMargin = dp(7) })
        box.addView(buttons)
        addGap(box, 8)
        val secondary = row()
        secondary.addView(actionButton("단계 시간 초기화", false) { session.resetStage(); afterAction() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        secondary.addView(actionButton("오늘 전체 초기화", false) {
            AlertDialog.Builder(this).setTitle("오늘 운동 초기화")
                .setMessage("오늘 운동 진행 상태를 초기화할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화") { _, _ -> session.resetAll(); afterAction() }
                .show()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(7) })
        box.addView(secondary)
        return box
    }

    private fun renderStages() {
        if (!::stageStack.isInitialized) return
        stageStack.removeAllViews()
        lastDay = session.day
        lastStage = session.stage
        val selectedPhase = session.phase()?.first ?: -1
        sectionCount.text = "${session.completed.size} / 4 단계"
        for ((si, stage) in session.routine.stages.withIndex()) {
            val isCurrent = session.stage == si && !session.finished
            val done = si in session.completed || (session.finished && si <= session.stage)
            val block = column().apply {
                background = rounded(if (isCurrent) Color.rgb(28, 44, 57) else panel, 16, if (isCurrent) mint else if (done) Color.rgb(66, 99, 74) else subtle)
                setPadding(dp(12), dp(13), dp(12), dp(13))
            }
            val top = row().apply { gravity = Gravity.CENTER_VERTICAL }
            val number = label(if (done) "✓" else "${si + 1}", 15, true, if (isCurrent) background else mint).apply {
                gravity = Gravity.CENTER
                background = rounded(if (isCurrent) mint else subtle, 10)
            }
            top.addView(number, LinearLayout.LayoutParams(dp(35), dp(35)))
            val titles = column().apply { setPadding(dp(9), 0, 0, 0) }
            titles.addView(label(stage.name, 15, true))
            titles.addView(label(stage.subtitle, 11, false, textSecondary))
            top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label("${stage.minutes}분", 13, true, mint))
            block.addView(top)
            addGap(block, 10)
            for ((ti, task) in stage.tasks.withIndex()) {
                val isPhase = si == 1 && session.stage == 1 && selectedPhase == ti && session.running
                val item = row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(5), dp(4), dp(3), dp(4))
                    if (isPhase) background = rounded(Color.rgb(33, 69, 53), 10, mint)
                }
                val textBox = column()
                textBox.addView(label(task.name, 13, true, if (isPhase) mint else textPrimary))
                addGap(textBox, 3)
                textBox.addView(label(task.detail, 11, false, textSecondary))
                textBox.setPadding(dp(5), dp(4), dp(4), dp(4))
                textBox.setOnClickListener { showDetails(si, task) }
                item.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
                val more = label("상세 ›", 11, true, mint)
                more.setPadding(dp(3), dp(10), dp(3), dp(10))
                more.setOnClickListener { showDetails(si, task) }
                item.addView(more)
                block.addView(item)
            }
            addGap(block, 6)
            block.addView(actionButton("이 단계로 타이머 이동 ↗", false) { session.jumpTo(si); afterAction() })
            stageStack.addView(block)
            addGap(stageStack, 9)
        }
        if (::trackingSummary.isInitialized) trackingSummary.text = "${session.completed.size} / 4 단계 완료 · 체크 없이 타이머 자동 진행"
    }

    private fun showDetails(stage: Int, task: WorkoutTask) {
        val info = data.detail(task.id)
        val steps = when {
            info != null -> info.steps
            stage == 1 -> when (task.kind) {
                "로잉머신" -> listOf("발을 고정하고 허리를 중립으로 세웁니다.", "다리로 밀고 몸통을 살짝 젖힌 뒤 손잡이를 아랫가슴 쪽으로 당깁니다.", "팔·몸통·다리 순서로 돌아오며 ${task.seconds / 60}분간 RPE ${task.rpe}를 유지합니다.")
                "일립티컬" -> listOf("발판에 발 전체를 올리고 손잡이를 가볍게 잡습니다.", "상체를 세운 채 팔과 다리를 함께 부드럽게 움직입니다.", "${task.seconds / 60}분간 RPE ${task.rpe}를 유지하며 어깨에 힘을 빼세요.")
                "스텝밀" -> listOf("발바닥 전체로 계단을 밟고 상체를 세웁니다.", "손잡이에 체중을 싣지 말고 일정한 리듬을 유지합니다.", "${task.seconds / 60}분간 RPE ${task.rpe}를 유지하며 무릎 방향을 확인하세요.")
                "평지 걷기" -> listOf("러닝머신의 경사를 낮게 설정하고 안전한 속도로 시작합니다.", "손잡이에 기대지 않고 자연스럽게 팔을 흔듭니다.", "${task.seconds / 60}분간 RPE ${task.rpe}를 유지하세요.")
                else -> listOf("안장을 맞추고 페달 아래 지점에서 무릎을 살짝 굽힙니다.", "${task.seconds / 60}분간 RPE ${task.rpe}를 유지합니다.", "상체를 편안하게 유지하고 부드럽게 페달링합니다.")
            }
            stage == 0 -> listOf("작은 움직임부터 서서히 가동 범위를 넓힙니다.", "관절에 통증이 없는 범위에서 호흡을 유지합니다.")
            else -> listOf("한 번에 20~40초 편안하게 유지합니다.", "반동 없이 천천히 늘리고 호흡합니다.")
        }
        val caution = info?.caution ?: if (stage == 1) "숨이 지나치게 차거나 어지러우면 강도를 낮추세요." else "통증이 생기면 강도를 줄이거나 중단하세요."
        val alternative = info?.alternative ?: if (stage == 1) "실내 사이클 또는 일립티컬 RPE 3~4" else "무리 없는 가벼운 동작"
        val body = StringBuilder()
            .append("${task.detail}\n")
            .append("\n운동 부위  ${info?.area ?: task.kind}\n")
            .append("사용 장비  ${info?.equipment ?: if (stage == 1) task.kind else "필요에 따라 맨몸"}\n")
            .append("\n동작 순서\n")
        steps.forEachIndexed { index, step -> body.append("${index + 1}. $step\n") }
        body.append("\n주의할 점\n$caution\n\n대체 운동\n$alternative")
        val panel = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        if (stage == 2 && task.id.isNotEmpty()) {
            panel.addView(label("동작 개념도 · 실제 머신별 자세는 설명을 확인하세요", 11, false, textSecondary))
            panel.addView(ExerciseDiagram(this, task.id))
            addGap(panel, 8)
        }
        panel.addView(ScrollView(this).apply {
            addView(label(body.toString(), 13, false))
        }, LinearLayout.LayoutParams(-1, dp(225)))
        if (stage == 2 && task.id.isNotEmpty()) {
            val slot = session.routine.stages[2].tasks.indexOfFirst { it.id == task.id }
            panel.addView(label("최근 기록: ${exercisePrefs.log(task.id)}", 12, false, mint))
            addGap(panel, 8)
            panel.addView(actionButton("중량 · 세트 기록", false) { showSetLogger(task) })
            addGap(panel, 6)
            if (slot >= 0) panel.addView(actionButton("같은 부위 운동으로 교체", false) { showExerciseSwap(slot) })
        }
        AlertDialog.Builder(this).setTitle(task.name).setView(panel)
            .setPositiveButton("닫기", null).show()
    }

    private fun showSetLogger(task: WorkoutTask) {
        val fields = column().apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }
        fun field(hint: String): EditText = EditText(this).apply {
            this.hint = hint
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            fields.addView(this)
        }
        val weight = field("중량 kg (맨몸이면 0)")
        val reps = field("횟수")
        val sets = field("세트")
        AlertDialog.Builder(this).setTitle(task.name + " 수행 기록").setView(fields)
            .setNegativeButton("취소", null).setPositiveButton("저장") { _, _ ->
                val kg = weight.text.toString().toDoubleOrNull()
                val r = reps.text.toString().toIntOrNull()
                val n = sets.text.toString().toIntOrNull()
                if (kg != null && kg in 0.0..500.0 && r != null && r in 1..100 && n != null && n in 1..20) {
                    exercisePrefs.setLog(task.id, kg, r, n)
                    Toast.makeText(this, "운동 기록 저장 완료", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "중량·횟수·세트를 확인해 주세요.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showExerciseSwap(slot: Int) {
        val options = data.alternatives(session.day, slot)
        AlertDialog.Builder(this).setTitle("대체 운동 선택")
            .setItems(options.map { it.second }.toTypedArray()) { _, index ->
                if (data.replaceExercise(session.day, slot, options[index].first)) {
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
        timerLabel.text = if (session.finished) "오늘 운동 완료" else "$stageName · 남은 시간"
        timerBadge.text = if (session.finished) "완료" else if (session.running) "● 진행 중" else "대기 / 일시정지"
        val elapsed = session.elapsed()
        progress.progress = elapsed
        timeMeta.text = "진행 ${elapsed / 60}분 · 남은 ${formatTime(3600 - elapsed)} / 60분"
        val phase = session.phase()
        when {
            session.finished -> {
                timerCueTitle.text = "오늘 운동 60분 달성!"
                timerCueDetail.text = "수분을 보충하고 충분히 쉬세요."
            }
            phase != null -> {
                timerCueTitle.text = "${phase.second.name} · RPE ${phase.second.rpe}"
                timerCueDetail.text = "${phase.second.description} · ${formatTime(session.phaseSecondsRemaining())} 남음 · ${phase.first + 1}/${session.routine.stages[1].tasks.size} 구간"
            }
            else -> {
                timerCueTitle.text = when (session.stage) {
                    0 -> "워밍업으로 몸 깨우기"
                    2 -> "오늘의 근력운동"
                    else -> "마무리 스트레칭"
                }
                timerCueDetail.text = when (session.stage) {
                    0 -> "관절 가동성을 천천히 높이고 호흡을 안정시키세요."
                    2 -> "운동 이름을 눌러 자세를 확인하고 세트 완료 후 체크하세요."
                    else -> "반동 없이 편안하게 늘리고 호흡을 정리하세요."
                }
            }
        }
        play.text = if (session.finished) "↻ 새 운동 시작" else if (session.running) "Ⅱ 일시정지" else "▶ 운동 시작"
        previous.alpha = if (session.stage == 0) .4f else 1f
        next.alpha = if (session.stage == 3) .4f else 1f
    }

    private fun afterAction() { syncService(); renderStages(); renderSummary(); refreshTimer() }

    private fun syncService() {
        if (recoveryMode) return
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
        } else stopService(intent)
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
        textSize = size.toFloat()
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun card() = column().apply {
        background = rounded(panel, 20, subtle)
        setPadding(dp(17), dp(18), dp(17), dp(18))
    }
    private fun actionButton(title: String, main: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = title
        textSize = if (main) 15f else 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (main) Color.rgb(22, 37, 26) else textPrimary)
        background = rounded(if (main) mint else panelLight, 12, if (main) mint else subtle)
        gravity = Gravity.CENTER
        minHeight = dp(if (main) 53 else 43)
        setPadding(dp(8), dp(9), dp(8), dp(9))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(number: Int) = (resources.displayMetrics.density * number + 0.5f).toInt()
    private fun addGap(container: LinearLayout, height: Int) {
        container.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun formatTime(seconds: Int): String {
        val n = max(0, seconds)
        return "%02d:%02d".format(n / 60, n % 60)
    }
    override fun onResume() {
        super.onResume()
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
