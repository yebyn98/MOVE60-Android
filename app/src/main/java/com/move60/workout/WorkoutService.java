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

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkoutService extends Service {
    static final String ACTION_SYNC="com.move60.workout.ACTION_SYNC";
    static final String EXTRA_SNAPSHOT="snapshot";
    private static final String CHANNEL_PROGRESS="move60_progress_v5";
    private static final String CHANNEL_PHASE="move60_phase_v5";
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
        NotificationChannel on=new NotificationChannel(CHANNEL_PROGRESS,"운동 진행 · 잠금화면",NotificationManager.IMPORTANCE_LOW);
        on.setDescription("현재 운동 구간 및 남은 시간");
        on.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
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
        String title,detail,signature;
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
                        c.detail="실내 사이클 · RPE "+phase.optString("rpe","—")+" · "+(i+1)+"/"+phases.length()+"구간";
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
            .setVisibility(Notification.VISIBILITY_PUBLIC);
    }
    private Notification buildOngoing(long now) {
        Cue c=cue(now);
        return builder(CHANNEL_PROGRESS)
            .setContentTitle("MOVE60 · "+c.title)
            .setContentText(c.detail+" · 전체 "+(c.progress/60)+"/60분")
            .setStyle(new Notification.BigTextStyle().bigText(c.title+"\n"+c.detail+"\n전체 "+(c.progress/60)+"/60분 진행"))
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true).setWhen(c.ends)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setProgress(3600,c.progress,false)
            .build();
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
        long target=Math.min(snapshot.optLong("deadlineAt",now+1000),c.ends);
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
