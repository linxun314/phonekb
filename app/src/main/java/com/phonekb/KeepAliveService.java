package com.phonekb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps the process (and therefore the HID registration) alive while the app is in
 * the background. Without this, MIUI destroys the activity and the HID device gets
 * unregistered, which silently kills the keyboard.
 */
public class KeepAliveService extends Service {
    private static final String TAG = "PhoneKB-KeepAlive";
    private static final String CHANNEL_ID = "phonekb_keepalive";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("PhoneKB 已就绪")
                    .setContentText("蓝牙键盘 / 触控板保持连接中")
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .setOngoing(true);
            startForeground(1, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed", e);
        }
        try {
            HidManager.get(this).start();
        } catch (Exception e) {
            Log.w(TAG, "hid start failed", e);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "PhoneKB 运行状态", NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("保持蓝牙 HID 键盘在后台运行");
        nm.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
