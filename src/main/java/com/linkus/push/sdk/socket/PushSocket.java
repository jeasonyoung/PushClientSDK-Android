package com.linkus.push.sdk.socket;

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
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 推送 socket 客户端处理。
 * Created by jeasonyoung on 2017/3/5.
 */
public final class PushSocket implements CodecEncoder.CodecEncoderListener, CodecDecoder.CodecDecoderListener {
    private static final LogWrapper logger = LogWrapper.getLog(PushSocket.class);
    private static final int BUF_SIZE = 512, RECEIVE_WAIT_INTERVAL = 2;

    private final Object lock = new Object();
    private final ExecutorService receivePool, reconnectPool;
    private final ScheduledExecutorService pingPool;
    private Future<?> pingFuture = null, reconnectFuture = null;

    private final PushSocketListener listener;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPing = new AtomicBoolean(false);

    private volatile AccessData access;
    private volatile SocketConfig socketConfig;
    private volatile Socket socket;
    private final AtomicLong lastIdleTime = new AtomicLong(0L);
    private final AtomicInteger reconnectTotal = new AtomicInteger(0);

    private CodecDecoder decoder;
    private CodecEncoder encoder;

    /**
     * 构造函数函数。
     * @param listener
     * 事件监听器。
     */
    public PushSocket(final PushSocketListener listener){
        this.receivePool = Executors.newSingleThreadExecutor();
        this.reconnectPool = Executors.newSingleThreadExecutor();
        this.pingPool = Executors.newSingleThreadScheduledExecutor();

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
    public void startHttp(){
        try{
            logger.info("start http request...");
            if(isRunning.get()){
                logger.info("socket already!");
                return;
            }
            changedRunStatus(true);
            //加载访问配置
            access = listener.loadAccessConfig();
            if(access == null)throw new RuntimeException("加载访问配置数据失败!");
            //创建请求数据
            final HttpRequestData data = new HttpRequestData(access);
            //http请求
            final String result = HttpUtils.postJson(access.getUrl(), data.toSignJson());
            if(result == null) throw new RuntimeException("http返回数据失败!");
            //
            logger.info("post-json-result=>\n" + result);
            synchronized (lock) {
                socketConfig = new SocketConfig(result);
            }
            //启动socket
            startSocket();
        }catch (Exception e){
            changedRunStatus(false);
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
        access = listener.loadAccessConfig();
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
        //发送解除用户绑定请求
        encoder.encodeUnsubscribe(access, this);
        return true;
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
        //发送断开socket通知
        encoder.encodeDisconnect(access, this);
        return true;
    }


    //更新运行状态
    private void changedRunStatus(final boolean status){
        if(isRunning.get() != status) {
            isRunning.set(status);
            if(!status){
                if(pingFuture != null){
                    logger.info("ping start ending...");
                    pingFuture.cancel(true);
                }
            }
            if(listener != null){
                listener.socketChangedRunStatus(status);
            }
        }
    }

    //启动socket
    private void startSocket() throws Exception {
        logger.info("start socket...");
        socket = new Socket(socketConfig.getServer(), socketConfig.getPort());
        if (!socket.isConnected()) {//连接服务器失败
            logger.warn("socket connect fail!");
            changedRunStatus(false);
            return;
        }
        logger.info("socket connect success!");
        //启动消息接收子线程
        startReceive();
        //发送连接请求
        encoder.encodeConnectRequest(access, this);
    }

    //启动消息接收子线程
    private void startReceive(){
        if(!isRunning.get()){
            logger.warn("socket running is stop, no start receive!");
            return;
        }
        receivePool.execute(new Runnable() {
            @Override
            public void run() {
                logger.info("start receive...");
                final WeakReference<PushSocket> weakRef = new WeakReference<>(PushSocket.this);
                try{
                    int count;
                    PushSocket ps;
                    byte buf[] = new byte[BUF_SIZE];
                    final ByteArrayOutputStream data = new ByteArrayOutputStream(BUF_SIZE);
                    while ((ps = weakRef.get()) != null && ps.isRunning.get() && ps.socket != null
                            && ps.socket.isConnected() && !ps.socket.isClosed() ){
                        //重置输出
                        if(data.size() > 0) data.reset();
                        //从socket获取数据
                        final DataInputStream inputStream = new DataInputStream(ps.socket.getInputStream());
                        while((count = inputStream.read(buf, 0, buf.length)) != -1){
                            //循环读取数据
                            data.write(buf, 0, count);
                        }
                        if(data.size() > 0){//已读取数据
                            //更新时间戳
                            ps.lastIdleTime.set(System.currentTimeMillis());
                            //解析消息
                            ps.decoder.addDecode(data.toByteArray());
                            //重置消息缓存
                            data.reset();
                        }
                        //线程等待
                        Thread.sleep(RECEIVE_WAIT_INTERVAL);
                    }
                }catch (Exception e){
                    logger.warn("run receive exception =>" + e.getMessage(), e);
                    final PushSocket ps;
                    if((ps = weakRef.get()) != null && ps.isRunning.get()){
                        logger.info("重启消息接收子线程!");
                        ps.startReceive();
                    }
                }
            }
        });
    }

    //消息编码
    @Override
    public void encode(final MessageType type, byte[] data) {
        try {
            if(socket != null && socket.isConnected() && !socket.isClosed()) {
                logger.info("send encode data["+ type +"]...");
                final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(data);
                out.flush();
                //更新时间戳
                lastIdleTime.set(System.currentTimeMillis());
                logger.info("send data successful !");
                if(type == MessageType.Disconnect){
                    logger.info("socket will closed!");
                    //更新运行状态
                    changedRunStatus(false);
                    //关闭套接字
                    socket.close();
                }
            }
        }catch (Exception e){
            logger.error("send data fail["+ type +"]-" + e.getMessage(), e);
        }
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
                    startPing(false);
                }
                break;
            }
            case Publish: {//推送消息下行
                PublishModel data = (PublishModel)model;
                if(data != null){
                    //应答消息反馈
                    encoder.encodePublishAckRequest(access, data.getPushId(), this);
                    //回调处理
                    listener.socketPublish(data);
                }
                break;
            }
            case Pingresp: {//心跳请求应答
                PingResponseModel data = (PingResponseModel)model;
                if(data != null){
                    if(data.getHeartRate() > 0){
                        socketConfig.setRate(data.getHeartRate());
                        //心跳频率发送变化重启心跳
                        startPing(true);
                    }
                    if(data.getAfterConnect() > 0){
                        socketConfig.setReconnect(data.getAfterConnect());
                        if(reconnectFuture != null){
                            reconnectFuture.cancel(true);
                        }
                    }
                }
                break;
            }
        }
    }

    //启动心跳循环线程
    private void startPing(final boolean isRestart){
        if(!isRestart && isPing.get()){
            logger.info("ping has started!");
            return;
        }
        final int rate = socketConfig.getRate();
        if(rate <= 0) return;
        if(isRestart && pingFuture != null){
            pingFuture.cancel(true);
            logger.info("ping is restart...");
        }
        pingFuture = pingPool.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isPing.get()) return;
                    isPing.set(true);
                    //检查是否在运行状态
                    if (!isRunning.get()) {//取消心跳执行
                        logger.warn("socket running is stop so send ping cancel!");
                        return;
                    }
                    //检查是否应该发送心跳数据
                    final long lastIdle = lastIdleTime.get(), current = System.currentTimeMillis();
                    final int interval = socketConfig.getRate() * 1000;
                    if (lastIdle > 0 && (current - lastIdle > interval) && access != null) {
                        logger.info("start send ping...");
                        //发送心跳
                        encoder.encodePingRequest(access, PushSocket.this);
                        logger.info("send ping successful!");
                    }
                } catch (Exception e) {
                    logger.error("send ping exception:" + e.getMessage(), e);
                } finally {
                    isPing.set(false);
                    logger.info("send ping finish!");
                }
            }
        },rate, rate, TimeUnit.SECONDS);
    }

    /**
     * 启动重连子线程。
     */
    public void startReconnect(){
        logger.debug("start reconnect...");
        //重置重连次数计数器
        if(reconnectTotal.get() > 0){
            reconnectTotal.set(0);
        }
        //启动重连子线程
        reconnectFuture = reconnectPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final WeakReference<PushSocket> weakRef = new WeakReference<>(PushSocket.this);
                    PushSocket ps;
                    //检查是否符合重启条件
                    while ((ps = weakRef.get()) != null && !ps.isRunning.get()
                            && ps.reconnectTotal.get() < ps.socketConfig.getTimes() + 1) {
                        try {
                            logger.debug("start reconnect[" + ps.reconnectTotal.get() + "]...");
                            //重启socket连接
                            ps.startSocket();
                            logger.debug("start reconnect successful!");
                        } catch (Exception ex) {
                            logger.warn("reconnect restart fail:" + ex.getMessage(), ex);
                        } finally {
                            ps.reconnectTotal.incrementAndGet();
                            //线程等待
                            final int interval = ps.socketConfig.getReconnect() * 1000;
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