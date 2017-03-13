package com.linkus.push.sdk;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.socket.PushSocket;
import com.linkus.push.sdk.utils.LogWrapper;

/**
 * 推送客户端Android服务。
 * Created by jeasonyoung on 2017/3/4.
 */
public final class PushClientService extends Service implements PushSocket.PushSocketListener {
    private static final LogWrapper logger = LogWrapper.getLog(PushClientService.class);
    private static final PushClientRestartServiceReceiver restartServiceReceiver = new PushClientRestartServiceReceiver();

    //接入帐号
    static final String PUSH_BROADCAST_PARAMS_ACCOUNT = "account";
    //重启广播Action
    static final String PUSH_BROADCAST_RESTART = "push_broadcast_restart";
    //错误信息广播Action
    static final String PUSH_BROADCAST_ERROR   = "push_broadcast_error";
    //错误类型
    static final String PUSH_BROADCAST_ERROR_TYPE    = "type";
    //错误消息内容
    static final String PUSH_BROADCAST_ERROR_CONTENT = "content";
    //推送消息广播
    static final String PUSH_BROADCAST_PUBLISH = "push_broadcast_publish";
    //推送消息内容
    static final String PUSH_BROADCAST_PUBLISH_CONTENT = "content";

    private final Messenger mMessenger;
    private final int GRAY_SERVICE_ID;

    private boolean isStart = false, isRun = false;

    private PushSocket socket;

    /**
     * 构造函数。
     */
    public PushClientService(){
        GRAY_SERVICE_ID = (int) (System.currentTimeMillis() / 1000);
        mMessenger = new Messenger(new IncomingHandler());
    }

    @Override
    public void onCreate() {
        logger.info("onCreate...");
        super.onCreate();
        //初始化socket对象
        socket = new PushSocket(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("onStartCommand-(flags:"+ flags +", startId:"+ startId +")...");
        try {
            //前台服务模式处理
            if (Build.VERSION.SDK_INT < 18) {
                startForeground(GRAY_SERVICE_ID, new Notification());//API < 18,此方法能有效隐藏Notification上的图标
            } else {
                startService(new Intent(this, GrayInnerService.class));
                startForeground(GRAY_SERVICE_ID, new Notification());
            }
        }catch (Exception e){
            logger.warn("onStartCommand-启动前台守候服务模式异常:" + e.getMessage(), e);
        }
        //注册广播
        logger.info("onStartCommand-启动广播注册接收器...");
        try {
            addRegisterBroadcastReceiver(this, PUSH_BROADCAST_RESTART);//推送重启广播
        }catch (Exception e){
            logger.warn("onStartCommand-注册广播接收器异常:" + e.getMessage(), e);
        }
        logger.info("onStartCommand(start:"+ isStart +",run:"+ isRun +")...");
        if(!isRun && isStart) {
            //启动socket
            logger.info("onStartCommand-准备启动socket...");
            socket.startHttp();
        }
        //返回
        return Service.START_STICKY;
    }

    /**
     * 添加注册广播接收器。
     * @param context
     * 上下文。
     * @param action
     * 广播action。
     */
    static void addRegisterBroadcastReceiver(final Context context, final String action){
        logger.info("addRegisterBroadcastReceiver-action:" + action);
        if(context == null){
            logger.warn("addRegisterBroadcastReceiver-context为空!");
            return;
        }
        if(action == null || action.trim().length() == 0){
            logger.warn("addRegisterBroadcastReceiver-action为空!");
            return;
        }
        context.registerReceiver(restartServiceReceiver, new IntentFilter(action));
    }

    @Override
    public IBinder onBind(Intent intent) {
        logger.info("PushClientService-onBind...");
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        logger.info("onDestroy...");
        super.onDestroy();
        //发送重启广播
        Intent intent = new Intent();
        intent.setAction(PUSH_BROADCAST_RESTART);
        sendBroadcast(intent);
    }

    @Override
    public void socketChangedRunStatus(boolean isRunning) {
        this.isRun = isRunning;
        logger.info("socketChangedRunStatus=>" + isRunning);
        if(!isRunning && isStart){
            logger.info("start reconnect socket...");
            socket.startReconnect();
        }
    }

    @Override
    public AccessData loadAccessConfig() {
        logger.info("loadAccessConfig...");
        return PushClientSDK.access;
    }

    @Override
    public void socketErrorMessage(AckResult status, String msg) {
        logger.error("socketErrorMessage["+ status +"]=>" + msg);
        if(status == AckResult.Success) return;
        //创建广播意图
        final Intent intent = new Intent();
        //错误广播
        intent.setAction(PUSH_BROADCAST_ERROR);
        //加载配置数据
        final AccessData access = loadAccessConfig();
        //接入帐号
        intent.putExtra(PUSH_BROADCAST_PARAMS_ACCOUNT, access.getAccount());
        //错误类型
        intent.putExtra(PUSH_BROADCAST_ERROR_TYPE, status.getVal());
        //错误消息内容
        intent.putExtra(PUSH_BROADCAST_ERROR_CONTENT, msg);
        //发送错误消息广播
        sendBroadcast(intent);
    }

    @Override
    public void socketPublish(PublishModel model) {
        logger.info("socketPublish=>" + model);
        if(model == null) return;
        final Intent intent = new Intent();
        //推送消息广播
        intent.setAction(PUSH_BROADCAST_PUBLISH);
        //加载配置数据
        final AccessData access = loadAccessConfig();
        //接入帐号
        intent.putExtra(PUSH_BROADCAST_PARAMS_ACCOUNT, access.getAccount());
        //广播消息内容
        intent.putExtra(PUSH_BROADCAST_PUBLISH_CONTENT, model.toJson());
        //发送推送消息广播
        sendBroadcast(intent);
    }

    //handler of incoming messages from clients
    private class IncomingHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            final SDKAction action = SDKAction.parse(msg.what);
            if(action == null){
                logger.warn("IncomingHandler-消息类型未知=>" + msg.what);
                return;
            }
            switch (action){
                case Start:{//启动HTTP
                    try {
                        isStart = true;
                        if (socket.getIsRunning()) {
                            logger.info("socket is running...");
                            return;
                        }
                        //启动http访问
                        socket.startHttp();
                    }catch (Exception e){
                        isStart = false;
                        logger.error("start http exception:" + e.getMessage(), e);
                    }
                    break;
                }
                case ChangeTag:{//绑定用户
                    try {
                        logger.info("start add or change tag....");
                        boolean result = socket.addOrChangeTag();
                        logger.info("add or change tag send request result =>" + result);
                    }catch (Exception e){
                        logger.error("add or change tag exception:" + e.getMessage(), e);
                    }
                    break;
                }
                case ClearTag:{//解绑用户
                    try {
                        logger.info("start clear tag...");
                        boolean result = socket.clearTag();
                        logger.info("start clear tag send request result =>" + result);
                    }catch (Exception e){
                        logger.error("clear tag exception:" + e.getMessage(), e);
                    }
                    break;
                }
                case Stop:{//关闭服务
                    try {
                        isStart = false;
                        logger.info("start stop client....");
                        boolean result = socket.closeSocket();
                        logger.info("start stop client send request result =>" + result);
                    }catch (Exception e){
                        logger.error("stop client exception:" + e.getMessage(), e);
                    }
                    break;
                }
            }
        }
    }

    /**
     * SDK动作枚举
     */
    enum SDKAction{
        /**
         * 启动推送。
         */
        Start(1),
        /**
         * 变更用户标签。
         */
        ChangeTag(2),
        /**
         * 取消用户标签
         */
        ClearTag(3),
        /**
         * 关闭服务。
         */
        Stop(4);

        private int val;
        /**
         * 构造函数。
         * @param val
         * 枚举值。
         */
        SDKAction(final int val){
            this.val = val;
        }

        /**
         * 获取枚举值。
         * @return 枚举值。
         */
        public int getVal() {
            return val;
        }

        /**
         * 枚举值类型转换。
         * @param val
         * 枚举值。
         * @return 枚举对象。
         */
        public static SDKAction parse(final int val){
            for(SDKAction sdk : values()){
                if(sdk.val == val) return sdk;
            }
            return null;
        }
    }

    //给API >= 18的平台上用的灰色保活手段
    class GrayInnerService extends Service{

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(GRAY_SERVICE_ID, new Notification());//设置为前台服务
            stopForeground(true);//停止前台服务
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    //重启服务广播接收器
    private static class PushClientRestartServiceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            logger.info("PushClientBroadcastReceiver-接收:action=" + action);
            context.startService(new Intent(context, PushClientService.class));
        }
    }
}