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
    private lateinit var data: WorkoutData
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
        data = WorkoutData(this)
        session = SessionStore(this, data)
        soundEnabled = getSharedPreferences("preferences", MODE_PRIVATE).getBoolean("sound", true)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 25)
        }
        val scroll = ScrollView(this).apply { fillViewport = true; isVerticalScrollBarEnabled = false; setBackgroundColor(background) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(110))
        }
        scroll.addView(content)
        setContentView(scroll)
        constructInterface()
        handler.post(ticker)
        if (session.running) syncService()
    }

    private fun constructInterface() {
        content.removeAllViews()
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("MOVE", 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("60", 24, true, mint))
        content.addView(header)
        addGap(content, 21)
        content.addView(label("WEEKDAY WORKOUT", 11, true, mint))
        addGap(content, 7)
        content.addView(label("나의 평일 운동 60분", 29, true))
        addGap(content, 4)
        content.addView(label("실내 사이클 중심 · 유산소 + 근력 · 월~금", 13, false, textSecondary))
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
        val stageHeading = row().apply { gravity = Gravity.CENTER_VERTICAL }
        stageHeading.addView(label("운동 순서 · 체크리스트", 19, true), LinearLayout.LayoutParams(0, -2, 1f))
        sectionCount = label("", 12, false, textSecondary)
        stageHeading.addView(sectionCount)
        content.addView(stageHeading)
        addGap(content, 10)
        stageStack = column()
        content.addView(stageStack)
        addGap(content, 18)
        val completedCard = card()
        completedCard.addView(label("오늘의 운동 체크 현황", 17, true))
        addGap(completedCard, 12)
        trackingSummary = label("", 14, false, textSecondary)
        completedCard.addView(trackingSummary)
        content.addView(completedCard)
        addGap(content, 18)
        val settings = card()
        settings.addView(label("설정 · 업데이트", 17, true))
        addGap(settings, 12)
        val check = Switch(this).apply {
            text = "구간 전환 알림음"
            setTextColor(textPrimary)
            isChecked = soundEnabled
            setOnCheckedChangeListener { _, enabled ->
                soundEnabled = enabled
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putBoolean("sound", enabled).apply()
            }
        }
        settings.addView(check)
        addGap(settings, 9)
        settings.addView(actionButton("잠금화면 알림 설정 열기", false) { openLockscreenSettings() })
        addGap(settings, 8)
        settings.addView(actionButton("새 버전 확인 · GitHub", false) { showUpdateInfo() })
        addGap(settings, 12)
        settings.addView(label("Kotlin 네이티브 앱 · 운동 시간은 화면을 꺼도 계속 계산돼요. 앱 삭제 시 운동 기록은 없어질 수 있어요.", 12, false, textSecondary))
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
                setTextColor(if (active) background else textSecondary)
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
            box.addView(label("${stage.minutes}분", 17, true, mint))
            box.addView(label(listOf("준비", "사이클", "근력", "정리")[index], 10, false, textSecondary))
            stages.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(5) })
        }
        summary.addView(stages)
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
                .setMessage("체크리스트와 운동 시간을 모두 초기화할까요?")
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
        val count = session.routine.stages.sumOf { it.tasks.size }
        val finished = session.checked.size
        sectionCount.text = "$finished / $count 체크"
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
                val checkedKey = session.taskKey(si, ti)
                val isPhase = si == 1 && session.stage == 1 && selectedPhase == ti && session.running
                val item = row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(5), dp(4), dp(3), dp(4))
                    if (isPhase) background = rounded(Color.rgb(33, 69, 53), 10, mint)
                }
                val checkbox = CheckBox(this).apply {
                    buttonTintList = android.content.res.ColorStateList.valueOf(mint)
                    isChecked = checkedKey in session.checked
                    contentDescription = "${task.name} 완료"
                    setOnCheckedChangeListener { _, checked ->
                        session.toggleChecked(checkedKey, checked)
                        sectionCount.text = "${session.checked.size} / $count 체크"
                        trackingSummary.text = "${session.checked.size} / $count 항목 완료 · ${session.completed.size} / 4 단계 완료"
                    }
                }
                item.addView(checkbox)
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
        if (::trackingSummary.isInitialized) trackingSummary.text = "$finished / $count 항목 완료 · ${session.completed.size} / 4 단계 완료"
    }

    private fun showDetails(stage: Int, task: WorkoutTask) {
        val info = data.detail(task.id)
        val steps = when {
            info != null -> info.steps
            stage == 1 -> listOf(
                "안장을 맞추고 페달 아래 지점에서 무릎을 살짝 굽힙니다.",
                "이 구간 ${task.seconds / 60}분, RPE ${task.rpe}: ${task.description}",
                "상체를 편안하게 유지하고 페달을 부드럽게 밟습니다.",
                "구간이 끝나면 다음 체크리스트 운동 강도로 전환합니다."
            )
            stage == 0 -> listOf("작은 움직임부터 서서히 가동 범위를 넓힙니다.", "관절에 통증이 없는 범위에서 호흡을 유지합니다.")
            else -> listOf("한 번에 20~40초 편안하게 유지합니다.", "반동 없이 천천히 늘리고 호흡합니다.")
        }
        val caution = info?.caution ?: if (stage == 1) "숨이 지나치게 차거나 어지러우면 강도를 낮추세요." else "통증이 생기면 강도를 줄이거나 중단하세요."
        val alternative = info?.alternative ?: if (stage == 1) "실내 사이클 RPE 3~4" else "무리 없는 가벼운 동작"
        val body = StringBuilder()
            .append("${task.detail}\n")
            .append("\n운동 부위  ${info?.area ?: task.kind}\n")
            .append("사용 장비  ${info?.equipment ?: if (stage == 1) "실내 사이클" else "필요에 따라 맨몸"}\n")
            .append("\n동작 순서\n")
        steps.forEachIndexed { index, step -> body.append("${index + 1}. $step\n") }
        body.append("\n주의할 점\n$caution\n\n대체 운동\n$alternative")
        AlertDialog.Builder(this).setTitle(task.name).setMessage(body.toString()).setPositiveButton("확인", null).show()
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
        val intent = Intent(this, WorkoutService::class.java)
        if (session.running && !session.finished) {
            intent.action = WorkoutService.ACTION_SYNC
            intent.putExtra(WorkoutService.EXTRA_SNAPSHOT, session.snapshot())
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "알림 서비스를 시작하지 못했어요. 권한을 확인하세요.", Toast.LENGTH_LONG).show()
            }
        } else stopService(intent)
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

    private fun showUpdateInfo() {
        AlertDialog.Builder(this)
            .setTitle("MOVE60 업데이트 확인")
            .setMessage("현재 소스 저장소는 비공개라서 앱에서 APK를 자동 다운로드할 수 없어요. GitHub Actions에 로그인해 최신 성공 빌드를 확인할 수 있어요. 자동 업데이트 알림과 앱 내 설치에는 별도 다운로드 배포 경로가 필요해요.")
            .setNegativeButton("닫기", null)
            .setPositiveButton("GitHub에서 확인") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yebyn98/MOVE60-Android/actions/workflows/build-apk.yml")))
            }.show()
    }

    private fun playTone() {
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 35)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
            handler.postDelayed({ tone.release() }, 220)
        } catch (ignored: Exception) {}
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
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
