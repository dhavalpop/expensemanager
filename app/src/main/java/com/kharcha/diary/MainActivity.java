package com.kharcha.diary;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int RC_FILE = 101;
    private static final int RC_SOUND = 102;
    private static final int RC_PERM = 103;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pickKind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0F5C63);

        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF2F0);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                handleIntent(getIntent());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), RC_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("https://" + HOST + "/assets/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_PERM);
        }
        for (String k : Sounds.KINDS) {
            Sounds.ensureChannel(this, k);
        }
    }

    private void handleIntent(Intent i) {
        if (i == null || web == null) return;
        String tab = i.getStringExtra("tab");
        if ("rem".equals(tab)) {
            web.evaluateJavascript("window.__go&&window.__go('rem')", null);
            i.removeExtra("tab");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.__refresh&&window.__refresh()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!"\"1\"".equals(v)) moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == RC_SOUND) {
            if (resultCode == RESULT_OK && data != null && pickKind != null) {
                Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (u != null) {
                    Sounds.set(this, pickKind, u);
                    Notifier.show(this, pickKind, pickKind + " sound changed", "This is how your " + pickKind + " reminders will sound.", 9001);
                }
            }
            web.evaluateJavascript("window.__soundChanged&&window.__soundChanged()", null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERM) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings();
            }
            web.evaluateJavascript("window.__refresh&&window.__refresh()", null);
        }
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /** Methods the web page calls as window.AndroidBridge.* */
    private class Bridge {

        @JavascriptInterface
        public String info() {
            try {
                JSONObject o = new JSONObject();
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                o.put("notif", nm.areNotificationsEnabled());
                o.put("hour", Scheduler.hour(MainActivity.this));
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                o.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
                JSONObject sounds = new JSONObject();
                for (String k : Sounds.KINDS) {
                    JSONObject s = new JSONObject();
                    s.put("name", Sounds.name(MainActivity.this, k));
                    s.put("custom", Sounds.isCustom(MainActivity.this, k));
                    sounds.put(k, s);
                }
                o.put("sounds", sounds);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void schedule(String json) {
            Scheduler.schedule(MainActivity.this, json);
        }

        @JavascriptInterface
        public void setHour(int h) {
            if (h >= 0 && h <= 23) Scheduler.setHour(MainActivity.this, h);
        }

        @JavascriptInterface
        public void pickSound(final String kind) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pickKind = kind;
                    Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sound for " + kind);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Sounds.uri(MainActivity.this, kind));
                    try {
                        startActivityForResult(i, RC_SOUND);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void resetSound(String kind) {
            Sounds.reset(MainActivity.this, kind);
        }

        @JavascriptInterface
        public void testSound(String kind) {
            Notifier.show(MainActivity.this, kind, kind + " reminder", "Test alert. This is the sound for " + kind + ".", 9000);
        }

        @JavascriptInterface
        public void askNotif() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_PERM);
                    } else {
                        openNotificationSettings();
                    }
                }
            });
        }

        @JavascriptInterface
        public void openBattery() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isNight() {
            int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return m == Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public String saveFile(String name, String mime, String data) {
            return writeDownload(name, mime, data, false);
        }

        /** Keeps one backup copy in the Downloads folder, so data can be restored after an uninstall. */
        @JavascriptInterface
        public String autoBackup(String json) {
            return writeDownload("KharchaDiary-backup.json", "application/json", json, true);
        }
    }

    private String writeDownload(String name, String mime, String data, boolean overwrite) {
        try {
            byte[] bytes = data.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                Uri base = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri target = null;
                if (overwrite) {
                    Cursor c = getContentResolver().query(base, new String[]{MediaStore.MediaColumns._ID},
                            MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{name}, null);
                    if (c != null) {
                        try {
                            if (c.moveToFirst()) target = ContentUris.withAppendedId(base, c.getLong(0));
                        } finally {
                            c.close();
                        }
                    }
                }
                if (target != null) {
                    try {
                        OutputStream os = getContentResolver().openOutputStream(target, "wt");
                        if (os != null) {
                            try {
                                os.write(bytes);
                            } finally {
                                os.close();
                            }
                            return "ok";
                        }
                    } catch (Exception ignored) {
                        // fall through and create a new file
                    }
                }
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(base, v);
                if (u == null) return "error";
                OutputStream os = getContentResolver().openOutputStream(u);
                if (os == null) return "error";
                try {
                    os.write(bytes);
                } finally {
                    os.close();
                }
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return "error";
                dir.mkdirs();
                FileOutputStream fo = new FileOutputStream(new File(dir, name));
                try {
                    fo.write(bytes);
                } finally {
                    fo.close();
                }
            }
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }
}
