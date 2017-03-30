package com.linkus.push.sdk;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.socket.PushSocket;
import com.linkus.push.sdk.utils.LogWrapper;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 推送客户端Android服务。
 * Created by jeasonyoung on 2017/3/4.
 */
public final class PushClientService extends Service implements PushSocket.PushSocketListener {
    private static final LogWrapper logger = LogWrapper.getLog(PushClientService.class);

    //重启广播Action
    static final String PUSH_BROADCAST_RESTART = "push_broadcast_restart";
    //错误信息广播Action
    static final String PUSH_BROADCAST_ERROR   = "push_broadcast_error";
    //推送消息广播
    static final String PUSH_BROADCAST_PUBLISH = "push_broadcast_publish";

    //接入帐号
    static final String PUSH_BROADCAST_PARAMS_ACCOUNT = "account";

    //消息类型
    static final String PUSH_BROADCAST_PARAMS_TYPE    = "type";

    //消息内容
    static final String PUSH_BROADCAST_PARAMS_CONTENT = "content";

    private final int GRAY_SERVICE_ID;

    private boolean isStart = false, isRun = false;
    private AtomicReference<AccessData> refAccess = new AtomicReference<>();

    private PushSocket socket;
    private Messenger mMessenger;

    /**
     * 构造函数。
     */
    public PushClientService(){
        GRAY_SERVICE_ID = (int) (System.currentTimeMillis() / 1000);
    }

    @Override
    public void onCreate() {
        logger.info("onCreate...");
        super.onCreate();
        //初始化Messager
        mMessenger = new Messenger(new IncomingHandler());
        //初始化socket对象
        socket = new PushSocket(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("onStartCommand-(intent:"+ intent +",flags:"+ flags +", startId:"+ startId +")...");
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
        logger.info("onStartCommand(start:"+ isStart +",run:"+ isRun +")...");
        if(!isRun && isStart) {
            try {
                //启动socket
                logger.info("onStartCommand-准备启动socket...");
                socket.startHttp();
            }catch (Exception e){
                logger.error("onStartCommand-start http exception:" + e.getMessage(), e);
            }
        }
        //检查定时器
        if(intent != null && socket != null){
            final String action = intent.getAction();
            if(action != null && action.length() > 0) {
                logger.info("onStartCommand-intent-action:" + action);
                try {
                    switch (action) {
                        case PushSocket.ACTION_RECEIVE: {//接收数据定时器
                            socket.startReceive();
                            break;
                        }
                        case PushSocket.ACTION_PING: {//接收心跳
                            socket.startPing();
                            break;
                        }
                        case PushSocket.ACTION_RECONNECT: {//接收重启
                            socket.startReconnect();
                            break;
                        }
                        default:
                            break;
                    }
                }catch (Exception e){
                    logger.error("onStartCommand-intent(action:"+ action +")-异常:" + e.getMessage(), e);
                }
            }
        }
        //返回
        return Service.START_STICKY;
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
        sendBroadcast(new Intent(PUSH_BROADCAST_RESTART));
    }

    @Override
    public void socketChangedRunStatus(boolean isRunning) {
        this.isRun = isRunning;
        logger.info("socketChangedRunStatus=>" + isRunning);
        if(!isRunning && isStart && loadAccessConfig() != null){
            try {
                logger.info("start reconnect socket...");
                socket.startReconnect();
            }catch (Exception e){
                logger.error("reconnect socket exception:" + e.getMessage(), e);
            }
        }
    }

    @Override
    public AccessData loadAccessConfig() {
        logger.info("loadAccessConfig...");
        return refAccess.get();
    }

    @Override
    public void socketErrorMessage(AckResult status, String msg) {
        logger.error("socketErrorMessage["+ status +"]=>" + msg);
        if(status == AckResult.Success) return;
        //加载配置数据
        final AccessData access = loadAccessConfig();
        if(access == null){
            logger.error("socketErrorMessage-加载配置文件失败!");
            return;
        }
        //创建广播意图
        final Intent intent = new Intent();
        //错误广播
        intent.setAction(PUSH_BROADCAST_ERROR);
        //接入帐号
        intent.putExtra(PUSH_BROADCAST_PARAMS_ACCOUNT, access.getAccount());
        //错误类型
        intent.putExtra(PUSH_BROADCAST_PARAMS_TYPE, status.getVal());
        //错误消息内容
        intent.putExtra(PUSH_BROADCAST_PARAMS_CONTENT, msg);
        //发送错误消息广播
        sendBroadcast(intent);
    }

    @Override
    public void socketPublish(PublishModel model) {
        logger.info("socketPublish=>" + model);
        if(model == null) return;
        //加载配置数据
        final AccessData access = loadAccessConfig();
        if(access == null){
            logger.error("socketPublish-加载配置文件失败!");
            return;
        }
        //推送消息广播
        final Intent intent = new Intent(PUSH_BROADCAST_PUBLISH);
        //接入帐号
        intent.putExtra(PUSH_BROADCAST_PARAMS_ACCOUNT, access.getAccount());
        //广播消息内容
        intent.putExtra(PUSH_BROADCAST_PARAMS_CONTENT, model.toJson());
        logger.info("socketPublish[发送推送消息广播]=>" + intent);
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
            //装载配置文件
            final AccessData accessData = AccessData.parseBundle(msg.getData());
            logger.debug("handleMessage-access:" + accessData);
            if(accessData != null){
                refAccess.set(accessData);
            }
            //动作处理
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
}