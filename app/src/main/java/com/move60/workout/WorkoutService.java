package com.move60.workout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkoutService extends Service {
    static final String ACTION_SYNC="com.move60.workout.ACTION_SYNC";
    static final String EXTRA_SNAPSHOT="snapshot";
    static final String CHANNEL_PROGRESS="move60_progress_lockscreen_v7";
    private static final String CHANNEL_PHASE="move60_phase_v7";
    private static final int ONGOING_ID=601;
    private static final int PHASE_ID=602;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String[] stageNames={"준비운동","실내 사이클","근력운동","마무리 스트레칭"};
    private JSONObject snapshot;
    private String lastCue="";
    private final Runnable onBoundary=()->updateAndSchedule(true);

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel on=new NotificationChannel(CHANNEL_PROGRESS,"운동 진행 · 잠금화면",NotificationManager.IMPORTANCE_DEFAULT);
        on.setDescription("현재 운동 구간 및 남은 시간");
        on.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        on.setSound(null,null);
        on.enableVibration(false);
        on.enableLights(false);
        nm.createNotificationChannel(on);
        NotificationChannel changes=new NotificationChannel(CHANNEL_PHASE,"운동 구간 전환 안내",NotificationManager.IMPORTANCE_DEFAULT);
        changes.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(changes);
    }
    private SharedPreferences prefs() {return getSharedPreferences("timer",MODE_PRIVATE);}
    private int seconds(int i) {
        JSONArray data=snapshot.optJSONArray("stageSeconds");
        return data==null?300:data.optInt(i,300);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        try {
            if(intent!=null && ACTION_SYNC.equals(intent.getAction())) {
                snapshot=new JSONObject(intent.getStringExtra(EXTRA_SNAPSHOT));
                lastCue="";
                prefs().edit().putString("snapshot",snapshot.toString()).apply();
            } else {
                snapshot=new JSONObject(prefs().getString("snapshot","{}"));
            }
            if(!snapshot.optBoolean("running",false)||snapshot.optBoolean("finished",false)) {
                stopWork();return START_NOT_STICKY;
            }
            foreground(buildOngoing(System.currentTimeMillis()));
            updateAndSchedule(false);
            return START_STICKY;
        } catch(Exception err) {
            stopWork();return START_NOT_STICKY;
        }
    }
    private void foreground(Notification notification) {
        if(Build.VERSION.SDK_INT>=34)
            startForeground(ONGOING_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ONGOING_ID,notification);
    }
    private void advance(long now) {
        int stage=snapshot.optInt("stage",0);
        long deadline=snapshot.optLong("deadlineAt",0);
        while(deadline>0&&deadline<=now&&stage<4) {
            stage++;
            if(stage>=4) {
                try {snapshot.put("finished",true);snapshot.put("running",false);}catch(Exception ignored){}
                break;
            }
            deadline+=seconds(stage)*1000L;
            try {snapshot.put("stage",stage);snapshot.put("deadlineAt",deadline);}catch(Exception ignored){}
        }
        prefs().edit().putString("snapshot",snapshot.toString()).apply();
    }
    private static class Cue {
        String title,detail,signature,stageLabel,roundLabel,effort;
        long ends;
        int progress;
    }
    private Cue cue(long now) {
        Cue c=new Cue();
        int stage=Math.max(0,Math.min(3,snapshot.optInt("stage",0)));
        long stageEnd=snapshot.optLong("deadlineAt",now+1000);
        long stageStart=stageEnd-seconds(stage)*1000L;
        long previous=0;
        for(int i=0;i<stage;i++)previous+=seconds(i);
        long elapsed=Math.max(0,Math.min(seconds(stage),(now-stageStart)/1000L));
        c.progress=(int)Math.max(0,Math.min(3600,previous+elapsed));
        c.title=stageNames[stage];
        c.stageLabel=stageNames[stage];
        c.roundLabel=(stage+1)+"/4 단계";
        c.effort="운동 진행 중";
        c.detail="운동 중";
        c.ends=stageEnd;
        if(stage==1) {
            JSONArray phases=snapshot.optJSONArray("phases");
            if(phases!=null) {
                long from=stageStart;
                for(int i=0;i<phases.length();i++) {
                    JSONObject phase=phases.optJSONObject(i);
                    if(phase==null)continue;
                    long to=from+phase.optLong("seconds",60)*1000L;
                    if(now<to) {
                        c.title=phase.optString("name","실내 사이클");
                        c.detail=phase.optString("description","페달링 호흡을 유지하세요");
                        c.roundLabel=(i+1)+"/"+phases.length()+" 구간";
                        c.effort="RPE "+phase.optString("rpe","—");
                        c.ends=to;
                        break;
                    }
                    from=to;
                }
            }
        }
        c.signature=stage+":"+c.title+":"+c.ends;
        return c;
    }
    private Notification.Builder builder(String channel) {
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,channel)
            .setSmallIcon(R.drawable.ic_fitness)
            .setColor(Color.rgb(180,245,110))
            .setContentIntent(pi)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true).setSilent(true).setPriority(Notification.PRIORITY_DEFAULT);
    }
    private RemoteViews card(Cue c, long now, boolean expanded) {
        RemoteViews v=new RemoteViews(getPackageName(),expanded ? R.layout.notification_move60_expanded : R.layout.notification_move60_compact);
        v.setTextViewText(R.id.n_brand,"MOVE60");
        v.setTextViewText(R.id.n_title,c.title);
        v.setTextViewText(R.id.n_stage,c.stageLabel);
        v.setTextViewText(R.id.n_badge,c.roundLabel);
        v.setTextViewText(R.id.n_effort,c.effort);
        v.setTextViewText(R.id.n_detail,c.detail);
        v.setTextViewText(R.id.n_progress_text,"전체 "+(c.progress/60)+"/60분");
        v.setProgressBar(R.id.n_progress,3600,c.progress,false);
        long left=Math.max(0,(long)Math.ceil((c.ends-now)/1000.0));
        v.setChronometer(R.id.n_clock,SystemClock.elapsedRealtime()+left*1000,null,true);
        v.setChronometerCountDown(R.id.n_clock,true);
        return v;
    }
    private Notification publicCard(long now) {
        Cue c=cue(now);
        return builder(CHANNEL_PROGRESS)
            .setContentTitle("MOVE60 · "+c.title).setContentText(c.effort+" · "+c.roundLabel)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setCustomContentView(card(c,now,false)).setCustomBigContentView(card(c,now,true))
            .setOngoing(true).setCategory(Notification.CATEGORY_STATUS)
            .setShowWhen(true).setWhen(c.ends)
            .setUsesChronometer(true).setChronometerCountDown(true).build();
    }
    private Notification buildOngoing(long now) {
        Cue c=cue(now);
        return builder(CHANNEL_PROGRESS)
            .setContentTitle("MOVE60 · "+c.title).setContentText(c.effort+" · "+c.roundLabel)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setCustomContentView(card(c,now,false)).setCustomBigContentView(card(c,now,true))
            .setPublicVersion(publicCard(now))
            .setOngoing(true).setCategory(Notification.CATEGORY_STATUS)
            .setShowWhen(true).setWhen(c.ends)
            .setUsesChronometer(true).setChronometerCountDown(true).build();
    }
    private void updateAndSchedule(boolean transition) {
        handler.removeCallbacks(onBoundary);
        if(snapshot==null)return;
        long now=System.currentTimeMillis();
        advance(now);
        if(snapshot.optBoolean("finished",false)||!snapshot.optBoolean("running",false)) {
            finishNotification();stopWork();return;
        }
        Cue c=cue(now);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(ONGOING_ID,buildOngoing(now));
        if(transition&&!c.signature.equals(lastCue)) {
            nm.notify(PHASE_ID,builder(CHANNEL_PHASE)
                .setContentTitle("다음 운동 · "+c.title)
                .setContentText(c.detail)
                .setAutoCancel(true).build());
        }
        lastCue=c.signature;
        long target=Math.min(Math.min(snapshot.optLong("deadlineAt",now+1000),c.ends),now+60000L);
        handler.postDelayed(onBoundary,Math.max(150,Math.min(3600000,target-now+30)));
    }
    private void finishNotification() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(PHASE_ID,builder(CHANNEL_PHASE)
            .setContentTitle("MOVE60 · 60분 운동 완료")
            .setContentText("오늘 운동을 마쳤어요. 체크리스트를 확인해 보세요.")
            .setAutoCancel(true).build());
    }
    private void stopWork() {
        handler.removeCallbacks(onBoundary);
        if(Build.VERSION.SDK_INT>=33)stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }
    @Override public void onDestroy() {handler.removeCallbacks(onBoundary);super.onDestroy();}
    @Override public IBinder onBind(Intent intent) {return null;}
}
