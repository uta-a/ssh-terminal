package com.uta.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.uta.terminal.MainActivity
import com.uta.terminal.R

/**
 * SSH セッションが生きている間だけ常駐通知を出すフォアグラウンドサービス。
 * バックグラウンドでも接続中だと分かり、プロセスも生存しやすくなる（keepalive 補助）。
 * 接続時に [start]、切断時に [stop] を呼ぶ（[com.uta.terminal.session.SessionController] から）。
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "セッション"
        val notification = buildNotification(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "ssh_session"
        private const val NOTIF_ID = 1
        private const val EXTRA_LABEL = "label"

        /** 接続中通知を開始/更新する。 */
        fun start(context: Context, label: String) {
            ensureChannel(context)
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_LABEL, label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 表示中の通知だけ更新する（バックグラウンドからも安全。startForegroundService は使わない）。 */
        fun update(context: Context, label: String) {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, buildNotification(context, label))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "SSH セッション",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "接続中のセッションを表示します" }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        private fun buildNotification(context: Context, label: String): Notification {
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_terminal)
                .setContentTitle("SSH 接続中")
                .setContentText(label)
                .setOngoing(true)
                .setContentIntent(tap)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
