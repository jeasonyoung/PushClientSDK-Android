package com.linkus.push.sdk.data;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * socket配置数据。
 * Created by jeasonyoung on 2017/3/3.
 */
public class SocketConfig {
    private String server;
    private Integer port, rate, times, reconnect;

    private static final String PARAMS_SERVER    = "serverIP";
    private static final String PARAMS_PORT      = "port";
    private static final String PARAMS_RATE      = "rate";
    private static final String PARAMS_TIMES     = "times";
    private static final String PARAMS_RECONNECT = "reconnect";

    /**
     * 构造函数。
     * @param map
     * map对象数据集合。
     */
    private SocketConfig(final JSONObject map){
        if(map == null || map.size() == 0){
            throw new IllegalArgumentException("map为空!");
        }
        //1.socket服务器地址
        if(map.containsKey(PARAMS_SERVER)){
            this.server = map.getString(PARAMS_SERVER);
        }
        //2.socket服务器端口
        if(map.containsKey(PARAMS_PORT)){
            this.port = map.getInteger(PARAMS_PORT);
        }
        //3.socket心跳间隔(秒)
        if(map.containsKey(PARAMS_RATE)){
            this.rate = map.getInteger(PARAMS_RATE);
        }
        //4.socket丢失心跳间隔次数
        if(map.containsKey(PARAMS_TIMES)){
            this.times = map.getInteger(PARAMS_TIMES);
        }
        //5.socket重连间隔(秒)
        if(map.containsKey(PARAMS_RECONNECT)){
            this.reconnect = map.getInteger(PARAMS_RECONNECT);
        }
    }

    /**
     * 构造函数。
     * @param json
     * json字符串。
     */
    public SocketConfig(final String json){
        this(JSON.parseObject(json));
    }

    /**
     * 获取socket服务器地址。
     * @return socket服务器地址。
     */
    public String getServer() {
        return server;
    }

    /**
     * 获取socket服务器端口。
     * @return socket服务器端口。
     */
    public Integer getPort() {
        return port;
    }

    /**
     * 获取socket心跳间隔。
     * @return socket心跳间隔(秒)。
     */
    public Integer getRate() {
        return rate;
    }

    /**
     * 设置socket心跳间隔。
     * @param rate
     * socket心跳间隔(秒)。
     */
    public void setRate(Integer rate) {
        if(rate != null && rate > 0 && !this.rate.equals(rate)) {
            synchronized (this) {
                this.rate = rate;
            }
        }
    }

    /**
     * 获取socket心跳丢失次数。
     * @return socket心跳丢失次数。
     */
    public Integer getTimes() {
        return times;
    }

    /**
     * 获取socket重连时间间隔。
     * @return socket重连时间间隔(秒)。
     */
    public Integer getReconnect() {
        return reconnect;
    }

    /**
     * socket重连时间间隔。
     * @param reconnect
     * socket重连时间间隔(秒)。
     */
    public void setReconnect(Integer reconnect) {
        if(reconnect != null && reconnect > 0 && !this.reconnect.equals(reconnect)) {
            synchronized (this) {
                this.reconnect = reconnect;
            }
        }
    }
}