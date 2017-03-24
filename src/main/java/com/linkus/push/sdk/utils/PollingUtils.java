package com.linkus.push.sdk.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * 轮询服务。
 * Created by jeasonyoung on 2017/3/24.
 */
public final class PollingUtils {

    /**
     * 开启轮询服务。
     * @param context
     * 上下文。
     * @param seconds
     * 轮询间隔时间(秒)。
     * @param cls
     * 轮询目标服务。
     * @param action
     * action。
     */
    public static void startPollingService(final Context context, final int seconds, final Class<?> cls, final String action){
        //获取AlarmManager
        final AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //包装要执行Service的Intent
        final Intent intent = new Intent(context, cls);
        intent.setAction(action);
        final PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //触发服务的起始时间
        final long triggerAtTime = SystemClock.elapsedRealtime();
        //使用AlarmManger的setRepeating方法设置定期执行的时间间隔（seconds秒）和需要执行的Service
        manager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtTime,
                seconds * 1000,
                pendingIntent);
    }

    /**
     * 取消轮询服务。
     * @param context
     * 上下文。
     * @param cls
     * 轮询目标服务。
     * @param action
     * action。
     */
    public static void stopPollingService(final Context context, final Class<?> cls, final String action){
        //获取AlarmManager
        final AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //包装要执行Service的Intent
        final Intent intent = new Intent(context, cls);
        intent.setAction(action);
        //
        final PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //取消正在执行的服务
        manager.cancel(pendingIntent);
    }
}