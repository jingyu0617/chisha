package app.chisha

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 每天早上 9 点左右检查一次：快到期、快用完、过期、咖啡豆不新鲜了，就发一条通知。
 * 只读数据、不改数据；按天扣量和自动进清单在打开 App 时进行。
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val d = applicationContext.appStore.data.first()
        if (!d.settings.notify) return Result.success()
        val projected = Logic.applyConsumption(d).first   // 估算今天的剩余量
        val r = Logic.reminders(projected) ?: return Result.success()
        Reminders.show(applicationContext, r)
        return Result.success()
    }
}

object Reminders {
    private const val CHANNEL = "fridge"
    private const val WORK = "daily-reminder"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "冰箱提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "食材快到期、蛋白粉咖啡豆快用完时提醒"
            },
        )
    }

    /** 安排每天 9 点左右的检查（已安排过就不重复） */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, r: Logic.Reminder) {
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(r.title)
            .setContentText(r.lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1, n)
        } catch (e: SecurityException) {
            // 用户关了通知权限
        }
    }
}
