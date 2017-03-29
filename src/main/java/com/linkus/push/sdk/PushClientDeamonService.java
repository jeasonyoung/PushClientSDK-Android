package com.linkus.push.sdk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.linkus.push.sdk.utils.LogWrapper;
import com.linkus.push.sdk.utils.PollingUtils;

/**
 * 推送客户端守护服务。
 * Created by jeasonyoung on 2017/3/29.
 */
public class PushClientDeamonService extends Service {
    private static final LogWrapper logger = LogWrapper.getLog(PushClientDeamonService.class);

    private static final String alarm_action = "push_polling_ping";
    private static final int alarm_interval = 120;//2分钟一次


    @Override
    public void onCreate() {
        logger.info("onCreate...");
        super.onCreate();
        try {
            //启动闹钟定时器
            PollingUtils.startPollingService(this, alarm_interval, PushClientDeamonService.class, alarm_action);
            //
            logger.info("启动AlarmManager定时器完成!=>" + alarm_action);
        }catch (Exception e){
            logger.error("启动AlarmManager定时器异常:" + e.getMessage(), e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("onStartCommand(intent="+ intent +",flags="+ flags +",startId="+ startId +")...");
        if(intent != null){
            try {
                final String action = intent.getAction();
                if (action != null && alarm_action.equalsIgnoreCase(action)) {
                    logger.info("onStartCommand-alarm:" + action);
                    //触发重启广播
                    sendBroadcast(new Intent(PushClientService.PUSH_BROADCAST_RESTART));
                    //触发重启服务
                    startService(new Intent(this, PushClientService.class));
                }
            }catch (Exception e){
                logger.error("onStartCommand[action:"+ intent.getAction() +"]-异常:" + e.getMessage(), e);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        logger.info("onDestroy...");
        super.onDestroy();
        //取消闹钟定时器
        PollingUtils.stopPollingService(this, PushClientDeamonService.class, alarm_action);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}