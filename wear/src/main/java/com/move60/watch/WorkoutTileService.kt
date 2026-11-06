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
import com.move60.watch.Move60Theme as Theme

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

    private fun ring(degrees: Float, color: Int): LayoutElementBuilders.Arc =
        LayoutElementBuilders.Arc.Builder().addContent(
            LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.degrees(degrees.coerceIn(0f, 360f)))
                .setThickness(DimensionBuilders.dp(4f))
                .setColor(ColorBuilders.argb(color))
                .build()
        ).build()

    private fun tileLayout(): LayoutElementBuilders.Box {
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
            (state.stageIndex == 1 || state.stageIndex == 3) -> state.detail.ifBlank { "운동 진행" }
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
        val ratio = when {
            state.finished -> 1f
            state.stageIndex == 2 && state.totalSets > 0 -> state.completedSets.toFloat() / state.totalSets
            state.running -> (state.stageIndex.coerceIn(0, 3) + 1) / 4f
            else -> 0f
        }
        val center = LayoutElementBuilders.Column.Builder()
            .addContent(tileLine("MOVE60", Theme.Type.wearTitle, Theme.Colors.primary))
            .addContent(tileLine(status, Theme.Type.wearBody, Theme.Colors.textHigh))
            .addContent(tileLine(info, Theme.Type.wearBody, Theme.Colors.textMedium))
            .addContent(tileLine("눌러서 열기", Theme.Type.wearCaption, Theme.Colors.textMuted))
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .addContent(ring(360f, Theme.Colors.outline))
            .addContent(ring(360f * ratio, Theme.Colors.primary))
            .addContent(center)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()
    }
}
