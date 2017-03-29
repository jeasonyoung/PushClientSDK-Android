package com.linkus.push.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.utils.LogWrapper;

import static com.linkus.push.sdk.PushClientService.*;

/**
 * 推送客户端广播接收。
 * Created by jeasonyoung on 2017/3/13.
 */
public abstract class PushClientReceiver extends BroadcastReceiver {
    private final static LogWrapper logger = LogWrapper.getLog(PushClientReceiver.class);
    private Context context;

    /**
     * 获取上下文。
     * @return 上下文。
     */
    protected Context getContext() {
        return context;
    }

    @Override
    public final void onReceive(Context context, Intent intent) {
        this.context = context;
        final String action = intent.getAction();
        if(action == null || action.length() == 0)return;
        final String account = intent.getStringExtra(PUSH_BROADCAST_PARAMS_ACCOUNT);
        logger.info("onReceive["+ account +"]=>" + action);
        switch (action.toLowerCase()){
            case PUSH_BROADCAST_PUBLISH:{//推送消息
                try {
                    if(account == null || account.length() == 0) return;
                    if(!account.equalsIgnoreCase(getAccount())){
                        return;
                    }
                    receiverPublishHandler(intent);
                }catch (Exception e){
                    logger.error("onReceive-["+ PUSH_BROADCAST_PUBLISH +"]-exception:" + e.getMessage(), e);
                }
                break;
            }
            case PUSH_BROADCAST_ERROR:{//错误消息处理
                try {
                    if(account == null || account.length() == 0) return;
                    if(!account.equalsIgnoreCase(getAccount())){
                        return;
                    }
                    receiverErrorHandler(intent);
                }catch (Exception e){
                    logger.error("onReceive-["+ PUSH_BROADCAST_ERROR +"]-exception:" + e.getMessage(), e);
                }
                break;
            }
            default:{//触发激活推送服务
                try {
                    logger.info("激活推送服务广播-接收:action=" + action);
                    if(!action.equalsIgnoreCase(PushClientService.PUSH_BROADCAST_RESTART)){
                        //启动守护服务
                        context.startService(new Intent(context, PushClientDeamonService.class));
                    }
                    //启动通讯服务
                    context.startService(new Intent(context, PushClientService.class));
                }catch (Exception e){
                    logger.error("onReceive-["+ action +"]-exception:" + e.getMessage(), e);
                }
                break;
            }
        }
    }

    //接收推送消息处理
    private void receiverPublishHandler(final Intent intent) throws Exception{
        //获取推送消息内容
        final String content = intent.getStringExtra(PUSH_BROADCAST_PARAMS_CONTENT);
        logger.info("receiverPublishHandler=>" + content);
        if(content == null || content.length() == 0) return;
        //消息解码
        final PublishModel data = new PublishModel(content);
        String title = null;
        if(data.getAps() != null){//获取消息标题
           final Object alert = data.getAps().getAlert();
           if(alert instanceof PublishModel.ApsAlertModel){
               title = ((PublishModel.ApsAlertModel)alert).getBody();
           }else if(alert instanceof String){
               title = alert.toString();
           }
        }
        receiverPublishHandler(title, data.getContent(), data);
    }

    //接收错误消息处理
    private void receiverErrorHandler(final Intent intent) throws Exception{
        final AckResult type = AckResult.parse(intent.getIntExtra(PUSH_BROADCAST_PARAMS_TYPE, 0));
        if(type == null) return;
        final String content = intent.getStringExtra(PUSH_BROADCAST_PARAMS_CONTENT);
        logger.info("receiverErrorHandler["+ type +"]=>" + content);
        receiverErrorHandler(type, content);
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