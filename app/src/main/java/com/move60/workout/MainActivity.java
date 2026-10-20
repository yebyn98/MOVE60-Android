package com.move60.workout;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String OFFLINE_APP = "file:///android_asset/MOVE60.html";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 25);
        }
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new TimerBridge(), "Move60Native");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return !OFFLINE_APP.equals(request.getUrl().toString());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("확인", (dialog, id) -> result.confirm())
                    .setNegativeButton("취소", (dialog, id) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
        });
        webView.loadUrl(OFFLINE_APP);
    }

    private class TimerBridge {
        @JavascriptInterface public void openLockscreenNotificationSettings() {
            runOnUiThread(() -> {
                try {
                    Intent settings=new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                    settings.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,WorkoutService.CHANNEL_PROGRESS);
                    startActivity(settings);
                } catch(Exception e) {
                    try {
                        Intent fallback=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        fallback.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                        startActivity(fallback);
                    } catch(Exception ignored) {}
                }
            });
        }
        @JavascriptInterface public void updateWorkout(String snapshot) {
            try {
                org.json.JSONObject state = new org.json.JSONObject(snapshot);
                Intent intent = new Intent(MainActivity.this, WorkoutService.class);
                if (state.optBoolean("running", false) && !state.optBoolean("finished", false)) {
                    intent.setAction(WorkoutService.ACTION_SYNC);
                    intent.putExtra(WorkoutService.EXTRA_SNAPSHOT, snapshot);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
                    else startService(intent);
                } else {
                    stopService(intent);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Move60Native");
            webView.destroy();
        }
        super.onDestroy();
    }
}
