package com.linkus.push.sdk.socket;

import android.content.Context;
import android.os.AsyncTask;
import com.linkus.push.sdk.PushClientService;
import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.data.HttpRequestData;
import com.linkus.push.sdk.data.SocketConfig;
import com.linkus.push.sdk.models.AckModel;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PingResponseModel;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.utils.HttpUtils;
import com.linkus.push.sdk.utils.LogWrapper;
import com.linkus.push.sdk.utils.NetUtils;
import com.linkus.push.sdk.utils.PollingUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 推送 socket 客户端处理。
 * Created by jeasonyoung on 2017/3/5.
 */
public final class PushSocket implements CodecEncoder.CodecEncoderListener, CodecDecoder.CodecDecoderListener {
    private static final LogWrapper logger = LogWrapper.getLog(PushSocket.class);

    //心跳
    public static final String ACTION_PING = "push_socket_ping";
    //接收消息
    public static final String ACTION_RECEIVE = "push_socket_receive";
    //重连
    public static final String ACTION_RECONNECT = "push_socket_reconnect";

    private static final int BUF_SIZE = 1024, RECEIVE_WAIT_INTERVAL = 2;

    private final PushSocketListener listener;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final AtomicLong lastIdleTime = new AtomicLong(0L);
    private final AtomicInteger reconnectTotal = new AtomicInteger(0);
    private final AtomicReference<SocketConfig> refSocketConfig = new AtomicReference<>();
    private final AtomicReference<Socket> refSocket = new AtomicReference<>();
    private final CopyOnWriteArrayList<String> receiverPushIdsCache = new CopyOnWriteArrayList<>();

    private final CodecDecoder decoder;
    private final CodecEncoder encoder;
    private final Context context;

    /**
     * 构造函数函数。
     * @param context
     * 上下文。
     * @param listener
     * 事件监听器。
     */
    public PushSocket(final Context context, final PushSocketListener listener){
        logger.debug("PushSocket=>" + listener);
        this.context = context;
        this.listener = listener;
        this.decoder = new CodecDecoder(this);
        this.encoder = new CodecEncoder();
    }

    /**
     * 获取socket的运行状态。
     * @return socket的运行状态。
     */
    public boolean getIsRunning() {
        return isRunning.get();
    }

    /**
     * 启动HTTP请求。
     */
    public void startHttp() throws Exception {
        logger.debug("startHttp...");
        if (getIsRunning()) return;
        try {
            logger.debug("start http request...");
            if (getIsRunning()) {
                logger.debug("socket already!");
                return;
            }
            changedRunStatus(true);
            //检查是否已有socket网络配置
            if(refSocketConfig.get() != null){
                logger.debug("socket config already!");
                startSocket();
                return;
            }
            //加载访问配置
            final AccessData access = listener.loadAccessConfig();
            if (access == null) throw new RuntimeException("加载访问配置数据失败!");
            logger.debug("access=>" + access);
            //创建请求数据
            final HttpRequestData data = new HttpRequestData(access);
            //http请求
            HttpUtils.asyncPostJson(access.getUrl(), data.toSignJson(), new HttpUtils.HttpUtilSyncListener() {
                @Override
                public void onResponse(AckResult result, String error, SocketConfig config) {
                    try {
                        if (result != AckResult.Success) {
                            changedRunStatus(false);
                            listener.socketErrorMessage(result, error);
                            return;
                        }
                        logger.debug("socket config=>\n" + config);
                        refSocketConfig.set(config);
                        //启动socket
                        startSocket();
                    }catch (Exception ex){
                        changedRunStatus(false);
                        listener.socketErrorMessage(AckResult.ParamError, ex.getMessage());
                        logger.error("start http parse exception:" + ex.getMessage(), ex);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("start-异常:" + e.getMessage(), e);
        }
    }

    /**
     * 添加或变更用户标签。
     * @return 添加或变更结果。
     */
    public boolean addOrChangeTag(){
        logger.debug("addOrChangeTag....");
        if(!isRunning.get()){
            logger.warn("socket is close！");
            return false;
        }
        //加载配置
        final AccessData access = listener.loadAccessConfig();
        if(access == null)throw new RuntimeException("load access config is null!");
        if(access.getTag() == null || access.getTag().length() == 0){
            throw new RuntimeException("tag is null or length is 0!");
        }
        //发送绑定用户请求
        encoder.encodeSubscribe(access, this);
        return true;
    }

    /**
     * 解除设备与用户标签的绑定。
     * @return 解除结果。
     */
    public boolean clearTag(){
        logger.debug("clearTag...");
        if(!isRunning.get()){
            logger.warn("socket is close！");
            return false;
        }
        //加载配置
        final AccessData access = listener.loadAccessConfig();
        if(access != null) {
            //发送解除用户绑定请求
            encoder.encodeUnsubscribe(access, this);
            return true;
        }
        return false;
    }

    /**
     * 关闭socket。
     * @return 关闭结果。
     */
    public boolean closeSocket(){
        logger.debug("closeSocket...");
        if(!isRunning.get()){
            logger.warn("socket is close！");
            return false;
        }
        //加载配置
        final AccessData access = listener.loadAccessConfig();
        if(access != null) {
            //发送断开socket通知
            encoder.encodeDisconnect(access, this);
            return true;
        }
        return false;
    }

    //更新运行状态
    private void changedRunStatus(final boolean status){
        if(isRunning.get() != status) {
            isRunning.set(status);
            if(!status){
                //停止心跳定时器
                PollingUtils.stopPollingService(context, PushClientService.class, ACTION_PING);
                //停止接收数据定时器
                PollingUtils.stopPollingService(context, PushClientService.class, ACTION_RECEIVE);
            }
            if(listener != null){
                listener.socketChangedRunStatus(status);
            }
        }
    }

    //启动socket
    private void startSocket() {
        if(refSocketConfig.get() == null)return;
        //异步启动socket连接
        new AsyncTask<Void,Void,Boolean>(){
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    final SocketConfig cfg = refSocketConfig.get();
                    if(cfg == null){
                        logger.error("start socket load socket conf fail!");
                        return false;
                    }
                    logger.info("start socket=>" + cfg);
                    if(cfg.getServer() == null || cfg.getServer().length() == 0){
                        logger.error("socket server address is null!");
                        return false;
                    }
                    if(cfg.getPort() <= 0){
                        logger.error("socket port["+ cfg.getPort() +"] is invalid!");
                        return false;
                    }
                    //判断服务器是否为IP
                    final String serverIP = NetUtils.convertToIPAddr(cfg.getServer());
                    if(serverIP == null || serverIP.length() == 0){
                        logger.error("server host to ip fail("+ cfg.getServer() +"=>"+ serverIP +")!");
                        return false;
                    }
                    //连接服务器
                    final Socket socket = new Socket(serverIP, cfg.getPort());
                    if (!socket.isConnected()) {//连接服务器失败
                        logger.warn("socket connect fail!");
                        NetUtils.clearCache(cfg.getServer());
                        changedRunStatus(false);
                        return false;
                    }
                    refSocket.set(socket);
                    //设置运行状态
                    changedRunStatus(true);
                    logger.info("socket connect success!");
                    return true;
                }catch (Exception e){
                    //设置运行状态
                    changedRunStatus(false);
                    logger.error("start socket exception:" + e.getMessage(), e);
                }
                return false;
            }
            @Override
            protected void onPostExecute(Boolean result) {
                if(!result) return;
                try{
                    //加载配置
                    final AccessData access = listener.loadAccessConfig();
                    if(access == null){
                        logger.warn("start socket load access config fail!");
                        return;
                    }
                    //启动消息接收子线程
                    startReceive();
                    //启动接收消息定时器
                    PollingUtils.startPollingService(context, RECEIVE_WAIT_INTERVAL, PushClientService.class, ACTION_RECEIVE);
                    //发送连接请求
                    encoder.encodeConnectRequest(access, PushSocket.this);
                }catch (Exception e){
                    logger.error("start ping exception:" + e.getMessage(), e);
                }
            }
        }.execute((Void)null);
    }

    private final AtomicBoolean isReceive = new AtomicBoolean(false);
    /**
     * 启动socket消息接收。
     */
    public void startReceive() {
        if(!isRunning.get()){
            logger.warn("socket running is stop, no start receive!");
            return;
        }
        if (isReceive.get()) {
            logger.warn("socket receive thread is run!");
            return;
        }
        //启动接收消息线程
        new AsyncTask<Void,Void,byte[]>(){
            @Override
            protected byte[] doInBackground(Void... voids) {
                try {
                    if(isReceive.get()){
                        logger.debug("socket receive thread["+ Thread.currentThread() +"] is run!");
                        return null;
                    }
                    isReceive.set(true);//设置消息正在接收中
                    if(!isRunning.get()){
                        logger.debug("socket is stop!");
                        return null;
                    }
                    //获取套接字
                    final Socket socket = refSocket.get();
                    if(socket == null){
                        logger.debug("socket is null!");
                        return null;
                    }
                    //开始接收数据
                    byte buf[] = new byte[BUF_SIZE];
                    final ByteArrayOutputStream data = new ByteArrayOutputStream(BUF_SIZE);
                    //从socket获取数据
                    final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    logger.debug("socket receive wait data...");
                    //循环读取数据
                    int count;
                    while ((count = inputStream.read(buf, 0, buf.length)) > 0){
                        data.write(buf, 0, count);
                    }
                    //已读取数据
                    if (data.size() > 0) {
                        lastIdleTime.set(System.currentTimeMillis());//更新时间戳
                        logger.info("socket receive read data:" + data.size());
                        return data.toByteArray();
                    }
                }catch (SocketException e){
                    logger.error("receive thread socket exception:" + e.getMessage(), e);
                    changedRunStatus(false);
                }catch (Exception ex){
                    logger.error("receive thread exception:" + ex.getMessage(), ex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(byte[] bytes) {
                try{
                    if(bytes == null || bytes.length == 0) return;
                    //开始解析数据
                    decoder.addDecode(bytes);
                }catch (Exception e){
                    logger.warn("receive data parse exception:" + e.getMessage(), e);
                }finally {
                    isReceive.set(false);//设置消息已接收完成
                }
            }
        }.execute((Void)null);
    }

    private final AtomicBoolean isPing = new AtomicBoolean(false);

    //启动心跳循环线程
    public void startPing(){
        if(!isRunning.get()){
            logger.debug("socket is stop!");
        }
        if(isPing.get()){
            logger.info("ping has started!");
            return;
        }
        //启动心跳线程
        new AsyncTask<Void,Void,Boolean>(){
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    if (isPing.get()) return false;
                    isPing.set(true);
                    //检查是否在运行状态
                    if (!isRunning.get()) {//取消心跳执行
                        logger.warn("socket running is stop so send ping cancel! =>thread:" + Thread.currentThread());
                        return false;
                    }
                    //检查是否应该发送心跳数据
                    final long lastIdle = lastIdleTime.get(), current = System.currentTimeMillis();
                    final int interval = refSocketConfig.get().getRate() * 1000;
                    return  (lastIdle > 0) && (current - lastIdle > interval);
                }catch (Exception e){
                    logger.error("send ping exception[thread:"+ Thread.currentThread() +"]:" + e.getMessage(), e);
                }
                return false;
            }

            @Override
            protected void onPostExecute(final Boolean aBoolean) {
                try{
                    if(aBoolean){
                        logger.debug("start send ping[thread:" + Thread.currentThread() + "]...");
                        encoder.encodePingRequest(listener.loadAccessConfig(), PushSocket.this);
                        logger.debug("send ping successful![thread:" + Thread.currentThread() + "]");
                    }
                }catch (Exception e){
                    logger.error("send ping message exception:"+ e.getMessage(), e);
                }finally {
                    isPing.set(false);
                }
            }
        }.execute((Void)null);
    }

    //消息编码
    @Override
    public void encode(final MessageType type,final byte[] data) {
        if(refSocket.get() == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Socket socket = refSocket.get();
                    if (socket != null && socket.isConnected() && !socket.isClosed()) {
                        logger.info("send encode data[" + type + "]...");
                        final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.write(data);
                        out.flush();
                        //更新时间戳
                        lastIdleTime.set(System.currentTimeMillis());
                        logger.info("send data successful !");
                        if (type == MessageType.Disconnect) {
                            logger.info("socket will closed!");
                            //更新运行状态
                            changedRunStatus(false);
                            //关闭套接字
                            socket.close();
                            refSocket.set(null);
                        }
                    }
                }catch (SocketException e){
                    logger.error("send data socket exception:" + e.getMessage(), e);
                    changedRunStatus(false);
                } catch (Exception e) {
                    logger.error("send data fail[" + type + "]-" + e.getMessage(), e);
                }
            }
        }).start();
    }

    //消息解码
    @Override
    public void decode(final MessageType type, final Object model) {
        logger.info("receive message("+ type +")=>" + model);
        switch (type){
            case Connack://连接请求应答
            case Pubrel://推送消息到达请求应答
            case Suback://用户登录请求应答
            case Unsuback://用户注销请求应答
            {
                final AckModel data = (AckModel)model;
                if(data.getResult() != AckResult.Success){
                    logger.error("["+ type +"]消息应答["+ data.getResult() +"]=>" + data.getMsg());
                    listener.socketErrorMessage(data.getResult(), data.getMsg());
                    return;
                }
                //连接应答,启动心跳
                if(type == MessageType.Connack){
                    try {
                        //获取心跳配置
                        final SocketConfig sc = refSocketConfig.get();
                        if(sc == null) throw new Exception("获取socket配置数据失败!");
                        if(sc.getRate() > 0) {
                            //启动心跳循环处理
                            PollingUtils.startPollingService(context, sc.getRate(), PushClientService.class, ACTION_PING);
                        }
                    }catch (Exception e){
                        logger.error("start ping exception:" + e.getMessage(), e);
                        listener.socketErrorMessage(AckResult.Runntime, e.getMessage());
                    }
                }
                break;
            }
            case Publish: {//推送消息下行
                final PublishModel data = (PublishModel)model;
                if(data != null){
                    logger.debug("decode-publish=>" + data);
                    //应答消息反馈
                    encoder.encodePublishAckRequest(listener.loadAccessConfig(), data.getPushId(), this);
                    //判断是否重复
                    if(receiverPushIdsCache.contains(data.getPushId())){
                        logger.warn("decode-消息["+ data.getPushId()+"]已接收过,忽略!");
                        return;
                    }
                    //添加到缓存
                    receiverPushIdsCache.add(data.getPushId());
                    //回调处理
                    listener.socketPublish(data);
                }
                break;
            }
            case Pingresp: {//心跳请求应答
                PingResponseModel data = (PingResponseModel)model;
                if(data != null){
                    final int rate, after;
                    if((rate = data.getHeartRate()) > 0) {
                        final SocketConfig socketConfig = refSocketConfig.get();
                        if (socketConfig == null) return;
                        socketConfig.setRate(rate);
                        refSocketConfig.set(socketConfig);
                        try {
                            //心跳频率发送变化重启心跳
                            //关闭心跳定时
                            PollingUtils.stopPollingService(context, PushClientService.class, ACTION_PING);
                            //重启心跳定时器
                            PollingUtils.startPollingService(context, rate, PushClientService.class, ACTION_PING);
                        } catch (Exception e) {
                            logger.error("restart ping exception:" + e.getMessage(), e);
                            listener.socketErrorMessage(AckResult.Runntime, e.getMessage());
                        }
                    }
                    if((after = data.getAfterConnect()) > 0){
                        isRunning.set(true);//关闭运行状态
                        final SocketConfig socketConfig = refSocketConfig.get();
                        if(socketConfig == null) return;
                        //设置数据
                        socketConfig.setReconnect(after);
                        refSocketConfig.set(socketConfig);
                        //关闭socket
                        final Socket socket;
                        if((socket = refSocket.get()) != null){
                            try {
                                changedRunStatus(false);
                                socket.close();
                                refSocket.set(null);
                            }catch (Exception e){
                                logger.warn("shutdown socket exception:" + e.getMessage(), e);
                            }finally {
                                //启动重连定时器
                                PollingUtils.startPollingService(context, after, PushClientService.class, ACTION_RECONNECT);
                            }
                        }
                    }
                }
                break;
            }
        }
    }


    private final AtomicBoolean isRestart = new AtomicBoolean(false);
    /**
     * 启动重连子线程。
     */
    public void startReconnect(){
        logger.debug("start reconnect...");
        if(isRunning.get() || refSocketConfig.get() == null) return;
        //重置重连次数计数器
        if(reconnectTotal.get() > 0){
            reconnectTotal.set(0);
        }
        if(isRestart.get()){
            logger.debug("start reconnect is start...");
            return;
        }
        //创建重连子线程
        final Thread tReconnect = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(isRestart.get()) return;
                    isRestart.set(true);
                    //
                    SocketConfig config;
                    //检查是否符合重启条件
                    while (!isRunning.get() && (config = refSocketConfig.get()) != null
                            && reconnectTotal.get() < config.getTimes() + 1) {
                        try {
                            logger.info("start reconnect[" + reconnectTotal.get() + "]...");
                            //重启socket连接
                            startSocket();
                            logger.info("start reconnect successful!");
                        } catch (Exception ex) {
                            logger.warn("reconnect restart fail:" + ex.getMessage(), ex);
                        } finally {
                            reconnectTotal.incrementAndGet();
                            //线程等待
                            final int interval = config.getReconnect() * 1000;
                            if (interval > 0) {
                                Thread.sleep(interval);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("start reconnect exception:" + e.getMessage(), e);
                }finally {
                    isRestart.set(false);
                    if(isRunning.get()) {//关闭重连定时器
                        PollingUtils.stopPollingService(context, PushClientService.class, ACTION_RECONNECT);
                    }
                }
            }
        });
        //设置重连为守候线程
        tReconnect.setDaemon(true);
        //启动重连线程
        tReconnect.start();
    }

    /**
     * socket 事件监听器。
     */
    public interface PushSocketListener{
        /**
         * socket运行状态改变。
         * @param isRunning
         * 是否运行中
         */
        void socketChangedRunStatus(final boolean isRunning);

        /**
         * socket错误消息
         * @param status
         * 错误消息类型。
         * @param msg
         * 错误消息详情。
         */
        void socketErrorMessage(final AckResult status,final String msg);

        /**
         * 接收到的推送消息。
         * @param model
         * 推送消息数据模型。
         */
        void socketPublish(final PublishModel model);

        /**
         * 获取访问配置数据。
         * @return 访问配置数据。
         */
        AccessData loadAccessConfig();
    }
}