package com.linkus.push.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.utils.LogWrapper;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 推送客户端SDK。
 * Created by jeasonyoung on 2017/3/4.
 */
public final class PushClientSDK implements Closeable {
    private static final LogWrapper logger = LogWrapper.getLog(PushClientSDK.class);

    private static final String SRV_URL_PREFIX = "http";
    private static final String SRV_URL_SUFFIX = "/push-http-connect/v1/callback/connect.do";

    private final AccessData access = new AccessData();

    private final Context context;
    private Messenger mService = null;
    private AtomicBoolean mBound = new AtomicBoolean(false);

    /**
     * 构造函数。
     * @param context
     * android context
     */
    public PushClientSDK(final Context context) {
        if (context == null) throw new IllegalArgumentException("context");
        this.context = context;
        LogWrapper.registerRootDir(context.getCacheDir());

        //启动服务
        logger.info("start push service...");
        context.startService(new Intent(context, PushClientService.class));

        //连接服务
        logger.info("connect push service..");
        final Intent intent = new Intent(context, PushClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 启动推送客户端。
     * @param host
     * 服务器地址。
     * @param account
     * 接入帐号。
     * @param password
     * 接入密钥。
     * @param deviceToken
     * 设备令牌(设备唯一标示)。
     */
    public void start(final String host, final String account, final String password, final String deviceToken){
        logger.info("启动推送客户端...");

        if(host == null || host.length() == 0){
            throw new IllegalArgumentException("host");
        }
        //接入帐号
        if(account == null || account.trim().length() == 0){
            throw new IllegalArgumentException("account");
        }
        //接入密钥
        if(password == null || password.trim().length() == 0){
            throw new IllegalArgumentException("password");
        }
        //设备ID
        if(deviceToken == null || deviceToken.trim().length() == 0){
            throw new IllegalArgumentException("deviceToken");
        }
        //检查host格式
        final StringBuilder urlBuilder = new StringBuilder();
        //检查头部
        if(!host.startsWith(SRV_URL_PREFIX)){
            urlBuilder.append(SRV_URL_PREFIX).append("://");
        }
        //添加host
        urlBuilder.append(host);
        //检查尾部
        if(host.endsWith(SRV_URL_SUFFIX)){
            throw new IllegalArgumentException("host不要包含子路径=>" + SRV_URL_SUFFIX);
        }
        if(host.endsWith("/")){
            urlBuilder.append(SRV_URL_SUFFIX.substring(1));
        }else{
            urlBuilder.append(SRV_URL_SUFFIX);
        }
        //设置访问设置数据
        access.setAccessData(urlBuilder.toString(), account.trim(), password.trim(), deviceToken.trim());
        logger.debug("access=>" + account);
        //启动HTTP请求数据
        actionService(PushClientService.SDKAction.Start);
    }

    /**
     * 添加或更新用户标签。
     * @param tag
     * 用户标签。
     */
    public void addOrChangedTage(final String tag){
        logger.info("添加或更新用户标签=>" + tag);
        if(tag == null || tag.trim().length() == 0){
            throw new IllegalArgumentException("tag");
        }
        access.setTag(tag);
        logger.debug("addOrChangedTage=>" + tag);
        actionService(PushClientService.SDKAction.ChangeTag);
    }

    /**
     * 清除用户标签。
     */
    public void clearTag(){
        logger.info("清除用户标签=>" + access.getTag());
        access.setTag(null);
        actionService(PushClientService.SDKAction.ClearTag);
    }

    /**
     * 关闭推送客户端。
     */
    public void stop(){
        logger.info("关闭推送客户端...");
        actionService(PushClientService.SDKAction.Stop);
    }

    //通知服务器执行方法
    private void actionService(final PushClientService.SDKAction action){
        final int interval = 500;
        new AsyncTask<Void,Void,Boolean>(){
            @Override
            protected Boolean doInBackground(Void... voids) {
                while (!mBound.get()){
                    try {
                        Thread.sleep(interval);
                    }catch (Exception e){
                        logger.warn("wait service bind thread exception:" + e.getMessage());
                    }
                }
                return mBound.get();
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if(!result){
                    logger.warn("未关联到推送服务，请稍后再试!");
                    return;
                }
                try{
                    final Message data = Message.obtain(null, action.getVal());
                    data.setData(access.buildBundle());
                    mService.send(data);
                }catch (Exception e){
                    logger.error("actionService["+ action +"]-异常:" + e.getMessage(), e);
                }
            }
        }.execute((Void)null);
    }

    /**
     * 解除与服务的关联。
     */
    @Override
    public void close() {
        logger.info("解除与推送服务的关联!");
        if(mBound.get()){
            context.unbindService(mConnection);
            mBound.set(false);
            logger.info("已解除与推送服务的关联!");
        }
    }

    //服务连接器
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mBound.set(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBound.set(false);
        }
    };
}