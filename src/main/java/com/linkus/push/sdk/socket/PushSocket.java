package com.linkus.push.sdk.socket;

import android.os.AsyncTask;
import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.data.HttpRequestData;
import com.linkus.push.sdk.data.SocketConfig;
import com.linkus.push.sdk.models.AckModel;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.PingResponseModel;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.utils.HttpUtils;
import com.linkus.push.sdk.utils.LogWrapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
    private static final int BUF_SIZE = 1024, RECEIVE_WAIT_INTERVAL = 500;

    private final PushSocketListener listener;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPing = new AtomicBoolean(false);
    private final AtomicBoolean isReceive = new AtomicBoolean(false);

    private Timer tPing;

    private final AtomicLong lastIdleTime = new AtomicLong(0L);
    private final AtomicInteger reconnectTotal = new AtomicInteger(0);
    private final AtomicReference<SocketConfig> refSocketConfig = new AtomicReference<>();
    private final AtomicReference<Socket> refSocket = new AtomicReference<>();

    private CodecDecoder decoder;
    private CodecEncoder encoder;

    private final Object lock = new Object();
    private final List<String> receiverPushIdCache = new ArrayList<>();

    /**
     * 构造函数函数。
     * @param listener
     * 事件监听器。
     */
    public PushSocket(final PushSocketListener listener){
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
        if (getIsRunning()) return;
        try {
            logger.info("start http request...");
            if (isRunning.get()) {
                logger.info("socket already!");
                return;
            }
            changedRunStatus(true);
            //加载访问配置
            final AccessData access = listener.loadAccessConfig();
            if (access == null) throw new RuntimeException("加载访问配置数据失败!");
            logger.info("access=>" + access);
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
            if(!status && isPing.get()){
                //停止心跳
                isPing.set(false);
                synchronized (lock) {//清空推送消息ID缓存
                    if(receiverPushIdCache.size() > 0)
                        receiverPushIdCache.clear();
                }
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
                    final Socket socket = new Socket(cfg.getServer(), cfg.getPort());
                    if (!socket.isConnected()) {//连接服务器失败
                        logger.warn("socket connect fail!");
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
                    //发送连接请求
                    encoder.encodeConnectRequest(access, PushSocket.this);
                }catch (Exception e){
                    logger.error("start ping exception:" + e.getMessage(), e);
                }
            }
        }.execute((Void)null);
    }

    //启动消息接收子线程
    private synchronized void startReceive() throws Exception {
        if (!isRunning.get()) {
            logger.warn("socket running is stop, no start receive!");
            return;
        }
        if (isReceive.get()) {
            logger.warn("socket receive thread is run!");
            return;
        }
        //创建接收消息子线程
        final Thread tReceive = new Thread(new Runnable() {
            @Override
            public void run() {
                logger.debug("start receive run[" + Thread.currentThread() + "] ...");
                isReceive.set(true);
                try {
                    int count;
                    final Socket socket = refSocket.get();
                    if(socket == null){
                        logger.debug("socket is null !");
                        return;
                    }
                    byte buf[] = new byte[BUF_SIZE];
                    final ByteArrayOutputStream data = new ByteArrayOutputStream(BUF_SIZE);
                    //从socket获取数据
                    final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    while (isRunning.get() && socket.isConnected() && !socket.isClosed()) {
                        try {
                            //重置输出
                            if (data.size() > 0) data.reset();
                            logger.debug("socket receive wait data...");
                            //循环读取数据
                            count = inputStream.read(buf, 0, buf.length);
                            if (count > 0) {
                                data.write(buf, 0, count);
                            }
                            //已读取数据
                            if (data.size() > 0) {
                                logger.info("socket receive read data:" + data.size());
                                //更新时间戳
                                lastIdleTime.set(System.currentTimeMillis());
                                try {
                                    //解析消息
                                    decoder.addDecode(data.toByteArray());
                                } catch (Exception e) {
                                    logger.warn("receive data parse exception:" + e.getMessage(), e);
                                }
                            }
                            //线程等待
                            logger.debug("receive thread wait[" + RECEIVE_WAIT_INTERVAL + " ms]=>" + lastIdleTime.get());
                            Thread.sleep(RECEIVE_WAIT_INTERVAL);
                            //检查接收socket消息是否继续
                            if (!isReceive.get()) break;
                        }catch (SocketException ex){
                            changedRunStatus(false);
                            logger.error("receive thread socket exception:" + ex.getMessage(), ex);
                        } catch (Exception ex) {
                            logger.error("receive thread exception:" + ex.getMessage(), ex);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("run receive exception =>" + e.getMessage(), e);
                } finally {
                    isReceive.set(false);
                    logger.info("receive thread finish!");
                }
            }
        });
        //设置线程为守候线程
        tReceive.setDaemon(true);
        //启动线程
        tReceive.start();
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
    public void decode(MessageType type, Object model) {
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
                        startPing(false);
                    }catch (Exception e){
                        logger.error("start ping exception:" + e.getMessage(), e);
                        listener.socketErrorMessage(AckResult.Runntime, e.getMessage());
                    }
                }
                break;
            }
            case Publish: {//推送消息下行
                PublishModel data = (PublishModel)model;
                if(data != null){
                    //应答消息反馈
                    encoder.encodePublishAckRequest(listener.loadAccessConfig(), data.getPushId(), this);
                    //判断是否重复
                    synchronized (lock){
                        if(receiverPushIdCache.contains(data.getPushId())){
                            logger.warn("decode-消息["+ data.getPushId()+"]已接收过,忽略!");
                            break;
                        }
                        //添加缓存
                        receiverPushIdCache.add(data.getPushId());
                    }
                    //回调处理
                    listener.socketPublish(data);
                }
                break;
            }
            case Pingresp: {//心跳请求应答
                PingResponseModel data = (PingResponseModel)model;
                if(data != null){
                    if(data.getHeartRate() > 0) {
                        final SocketConfig socketConfig = refSocketConfig.get();
                        if (socketConfig == null) return;
                        socketConfig.setRate(data.getHeartRate());
                        refSocketConfig.set(socketConfig);
                        try {
                            //心跳频率发送变化重启心跳
                            startPing(true);
                        } catch (Exception e) {
                            logger.error("restart ping exception:" + e.getMessage(), e);
                            listener.socketErrorMessage(AckResult.Runntime, e.getMessage());
                        }
                    }
                    if(data.getAfterConnect() > 0){
                        isRunning.set(true);//关闭运行状态
                        final SocketConfig socketConfig = refSocketConfig.get();
                        if(socketConfig == null) return;
                        //设置数据
                        socketConfig.setReconnect(data.getAfterConnect());
                        refSocketConfig.set(socketConfig);
                        //关闭socket
                        final Socket socket;
                        if((socket = refSocket.get()) != null){
                            try {
                                socket.close();
                                refSocket.set(null);
                            }catch (Exception e){
                                logger.warn("shutdown socket exception:" + e.getMessage(), e);
                            }finally {
                                try {
                                    startReconnect();
                                }catch (Exception e){
                                    logger.warn("reconnect socket exception:" + e.getMessage(), e);
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    //启动心跳循环线程
    private void startPing(final boolean isRestart) throws Exception{
        if(!isRestart && isPing.get()){
            logger.info("ping has started!");
            return;
        }
        final SocketConfig socketConfig = refSocketConfig.get();
        if(socketConfig == null){
            logger.error("load socket config fail!");
            return;
        }
        final int rate = socketConfig.getRate() * 1000;
        if(rate <= 0) return;
        if(isRestart && tPing != null && isPing.get()){
            isPing.set(false);//停止心跳状态
            tPing.cancel();//取消心跳定时器
            logger.info("start restart ping...");
        }
        //创建心跳定时器(守候线程)
        tPing = new Timer(true);
        tPing.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (isPing.get()) return;
                    isPing.set(true);
                    //检查是否在运行状态
                    if (!isRunning.get()) {//取消心跳执行
                        logger.warn("socket running is stop so send ping cancel! =>thread:" + Thread.currentThread());
                        return;
                    }
                    //检查是否应该发送心跳数据
                    final long lastIdle = lastIdleTime.get(), current = System.currentTimeMillis();
                    final int interval = socketConfig.getRate() * 1000;
                    final AccessData access = listener.loadAccessConfig();
                    if (lastIdle > 0 && (current - lastIdle > interval) && access != null) {
                        logger.info("start send ping[thread:"+ Thread.currentThread() +"]...");
                        //发送心跳
                        encoder.encodePingRequest(access, PushSocket.this);
                        logger.info("send ping successful![thread:"+ Thread.currentThread() +"]");
                    }
                } catch (Exception e) {
                    logger.error("send ping exception[thread:"+ Thread.currentThread() +"]:" + e.getMessage(), e);
                } finally {
                    isPing.set(false);
                    logger.info("send ping finish[thread:"+ Thread.currentThread() +"]!");
                }
            }
        }, rate, rate);
    }

    /**
     * 启动重连子线程。
     */
    public synchronized void startReconnect() throws Exception{
        if(isRunning.get() || refSocketConfig.get() == null) return;
        logger.debug("start reconnect...");
        //重置重连次数计数器
        if(reconnectTotal.get() > 0){
            reconnectTotal.set(0);
        }
        //创建重连子线程
        final Thread tReconnect = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
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