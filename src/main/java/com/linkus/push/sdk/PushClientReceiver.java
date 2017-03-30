package com.linkus.push.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.utils.LogWrapper;

import java.util.concurrent.atomic.AtomicReference;

import static com.linkus.push.sdk.PushClientService.*;

/**
 * 推送客户端广播接收。
 * Created by jeasonyoung on 2017/3/13.
 */
public abstract class PushClientReceiver extends BroadcastReceiver {
    private final static LogWrapper logger = LogWrapper.getLog(PushClientReceiver.class);
    private final AtomicReference<Context> refContext = new AtomicReference<>(null);
    private final Object lock = new Object();

    /**
     * 获取上下文。
     * @return 上下文。
     */
    protected Context getContext() {
        return refContext.get();
    }

    @Override
    public final void onReceive(Context context, Intent intent) {
        //更新上下文
        refContext.set(context);
        //业务处理
        synchronized (lock) {
            //打印intent
            logger.debug("onReceive-intent=>" + intent);
            //获取action
            final String action = intent.getAction();
            if (action == null || action.length() == 0) {
                logger.warn("onReceive-[为获得action数据!]=>" + intent);
                return;
            }
            try {
                //判断是否为推送消息
                if (PUSH_BROADCAST_PUBLISH.equalsIgnoreCase(action) || PUSH_BROADCAST_ERROR.equalsIgnoreCase(action)) {
                    //广播所带的帐号
                    final String account = getBroadcastAccount(intent);
                    if(account == null || account.length() == 0){
                        logger.error("onReceive[action="+ action +"]携带的接入帐号不存在!=>" + intent);
                        return;
                    }
                    if(!account.equalsIgnoreCase(getAccount())){
                        logger.warn("onReceive[action="+ action +"]携带的接入帐号["+ account +"]与当前配置的接入帐号["+ getAccount() +"]不一致，消息抛弃!");
                        return;
                    }
                    //推送消息处理
                    if(PUSH_BROADCAST_PUBLISH.equalsIgnoreCase(action)){
                        receiverPublishHandler(intent);
                    }else{//异常消息处理
                        receiverErrorHandler(intent);
                    }
                } else {//保活处理
                    logger.info("激活推送服务广播-接收:action=" + action);
                    if (!PushClientService.PUSH_BROADCAST_RESTART.equalsIgnoreCase(action)) {
                        //启动守护服务
                        context.startService(new Intent(context, PushClientDeamonService.class));
                    }
                    //启动通讯服务
                    context.startService(new Intent(context, PushClientService.class));
                }
            }catch (Exception e){
                logger.error("onReceive-[" + action + "]-exception:" + e.getMessage(), e);
            }
        }
    }

    //获取广播帐号
    private static String getBroadcastAccount(final Intent intent){
        return getIntentStrValue(intent, PUSH_BROADCAST_PARAMS_ACCOUNT);
    }

    private static String getIntentStrValue(final Intent intent,final String key){
        if(intent == null || key == null || key.length() == 0) return null;
        final String val = intent.getStringExtra(key);
        return val == null ? "" : val;
    }

    //获取广播内容。
    private static String getBroadcastContent(final Intent intent){
        return getIntentStrValue(intent, PUSH_BROADCAST_PARAMS_CONTENT);
    }

    //接收推送消息处理
    private void receiverPublishHandler(final Intent intent) throws Exception{
        new AsyncTask<Void,Void,PublishModel>(){
            @Override
            protected PublishModel doInBackground(Void... voids) {
                try {
                    //获取推送消息内容
                    final String content = getBroadcastContent(intent);
                    logger.info("receiverPublishHandler=>" + content);
                    if (content == null || content.length() == 0) {
                        logger.error("receiverPublishHandler-获取推送消息失败!");
                        return null;
                    }
                    //消息解码
                    return new PublishModel(content);
                }catch (Exception e){
                    logger.error("receiverPublishHandler-消息解码异常:" + e.getMessage(), e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(PublishModel data) {
                try {
                    if (data == null) return;
                    String title = null;
                    if(data.getAps() != null) {//获取消息标题
                        final Object alert = data.getAps().getAlert();
                        if (alert instanceof PublishModel.ApsAlertModel) {
                            title = ((PublishModel.ApsAlertModel) alert).getBody();
                        } else if (alert instanceof String) {
                            title = alert.toString();
                        }
                    }
                    receiverPublishHandler(title, data.getContent(), data);
                }catch (Exception e){
                    logger.error("receiverPublishHandler-推送消息处理异常:" + e.getMessage(), e);
                }
            }
        }.execute((Void)null);
    }

    //接收错误消息处理
    private void receiverErrorHandler(final Intent intent) throws Exception{
        new AsyncTask<Void,Void,Object[]>(){
            @Override
            protected Object[] doInBackground(Void... voids) {
                try {
                    final AckResult type = AckResult.parse(intent.getIntExtra(PUSH_BROADCAST_PARAMS_TYPE, 0));
                    if(type != null){
                        final String content = getBroadcastContent(intent);
                        logger.info("receiverErrorHandler[type:"+ type +"]=>" + content);
                        return new Object[]{type, content};
                    }
                }catch (Exception e){
                    logger.error("receiverErrorHandler-消息解码异常:" + e.getMessage(), e);
                }
                return new Object[0];
            }

            @Override
            protected void onPostExecute(Object[] objects) {
                try {
                    if (objects == null || objects.length < 2) return;
                    receiverErrorHandler((AckResult)objects[0], (String) objects[1]);
                }catch (Exception e){
                    logger.error("receiverErrorHandler-错误消息处理异常:" + e.getMessage(), e);
                }
            }
        }.execute((Void)null);
    }

    /**
     * 获取接入帐号。
     * @return 接入帐号。
     */
    protected abstract String getAccount();

    /**
     * 接收推送消息处理。
     * @param title
     * 推送消息标题。
     * @param content
     * 推送消息内容。
     * @param full
     * 推送消息完整数据。
     */
    protected abstract void receiverPublishHandler(final String title, final String content, final PublishModel full);

    /**
     * 接收错误消息处理。
     * @param status
     * 错误消息类型。
     * @param msg
     * 错误消息内容。
     */
    protected abstract void receiverErrorHandler(final AckResult status, final String msg);
}