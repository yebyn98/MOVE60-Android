package com.move60.watch

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures

/** A glanceable Watch4 tile. Tapping it opens controls; stages still advance manually. */
class WorkoutTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = Futures.immediateFuture(
        TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(30_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder().setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(tileText()).build()
                    ).build()
                ).build()
            ).build()
    )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())

    private fun tileText(): LayoutElementBuilders.Text {
        val open = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(WatchActivity::class.java.name)
                .build()
        ).build()
        val clickable = ModifiersBuilders.Clickable.Builder().setId("open_move60").setOnClick(open).build()
        return LayoutElementBuilders.Text.Builder()
            .setText(WatchWorkoutState.load(this).let { if (it.finished) "MOVE60\n운동 완료" else "MOVE60\n"+it.stageLabel+"\n"+(if (it.stageIndex == 2) it.setLabel else it.detail)+"\n눌러서 운동 열기" })
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()
    }
}

