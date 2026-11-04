package com.move60.watch

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures

/** Read-only glanceable tile; phone remains the single authority for workout controls. */
class WorkoutTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = Futures.immediateFuture(
        TileBuilders.Tile.Builder()
            .setResourcesVersion("2")
            .setFreshnessIntervalMillis(30_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder().setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(tileLayout()).build()
                    ).build()
                ).build()
            ).build()
    )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("2").build())

    private fun tileLine(value: String, sizeSp: Float, color: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value.take(24))
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.argb(color))
                    .build()
            ).build()

    private fun tileLayout(): LayoutElementBuilders.Column {
        val state = WatchWorkoutState.load(this)
        val rest = state.restSeconds() > 0 && state.stageIndex == 2 && !state.finished
        val status = when {
            state.finished -> "오늘 운동 완료"
            rest -> "세트 간 휴식"
            state.running -> state.stageLabel
            state.hasSession -> "일시정지 · " + state.stageName
            else -> "운동 대기"
        }
        val info = when {
            state.finished -> "${state.completedSets}/${state.totalSets}세트 · 약 ${state.estimatedKcal.toInt()}kcal"
            rest -> "남은 약 ${(state.restSeconds()+59)/60}분"
            state.stageIndex == 2 -> state.exerciseName.ifBlank { state.setLabel }.take(15)
            state.stageIndex == 1 && state.intervalTotal > 0 -> "${state.intervalIndex}/${state.intervalTotal}구간 · ${state.detail}"
            state.running -> state.detail
            else -> "휴대폰에서 운동을 시작"
        }
        val action = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(WatchActivity::class.java.name)
                .build()
        ).build()
        val clickable = ModifiersBuilders.Clickable.Builder().setId("open_move60")
            .setOnClick(action).build()
        return LayoutElementBuilders.Column.Builder()
            .addContent(tileLine("MOVE60", 15f, 0xFFB4F56E.toInt()))
            .addContent(tileLine(status, 13f, 0xFFFFFFFF.toInt()))
            .addContent(tileLine(info, 11f, 0xFFCFD8E3.toInt()))
            .addContent(tileLine("눌러서 운동 열기", 10f, 0xFFAAB9C9.toInt()))
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()
    }
}
