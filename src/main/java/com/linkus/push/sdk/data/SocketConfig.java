package com.linkus.push.sdk.data;

import com.alibaba.fastjson.JSONObject;

/**
 * socket配置数据。
 * Created by jeasonyoung on 2017/3/3.
 */
public class SocketConfig {
    private String server;
    private int port, rate, times, reconnect;

    private static final String PARAMS_SERVER    = "serverIP";
    private static final String PARAMS_PORT      = "port";
    private static final String PARAMS_RATE      = "rate";
    private static final String PARAMS_TIMES     = "times";
    private static final String PARAMS_RECONNECT = "reconnect";

    /**
     * 构造函数。
     * @param setting
     * map对象数据集合。
     */
    public SocketConfig(final JSONObject setting) {
        if (setting == null || setting.size() == 0) {
            throw new IllegalArgumentException("setting is null!");
        }
        //1.socket服务器地址
        if (setting.containsKey(PARAMS_SERVER)) {
            this.server = setting.getString(PARAMS_SERVER);
        }
        //2.socket服务器端口
        if (setting.containsKey(PARAMS_PORT)) {
            this.port = setting.getIntValue(PARAMS_PORT);
        }
        //3.socket心跳间隔(秒)
        if (setting.containsKey(PARAMS_RATE)) {
            this.rate = setting.getIntValue(PARAMS_RATE);
        }
        //4.socket丢失心跳间隔次数
        if (setting.containsKey(PARAMS_TIMES)) {
            this.times = setting.getIntValue(PARAMS_TIMES);
        }
        //5.socket重连间隔(秒)
        if (setting.containsKey(PARAMS_RECONNECT)) {
            this.reconnect = setting.getIntValue(PARAMS_RECONNECT);
        }
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
    public int getPort() {
        return port;
    }

    /**
     * 获取socket心跳间隔。
     * @return socket心跳间隔(秒)。
     */
    public int getRate() {
        return rate;
    }

    /**
     * 设置socket心跳间隔。
     * @param rate
     * socket心跳间隔(秒)。
     */
    public void setRate(int rate) {
        if(rate > 0 && this.rate != rate) {
            this.rate = rate;
        }
    }

    /**
     * 获取socket心跳丢失次数。
     * @return socket心跳丢失次数。
     */
    public int getTimes() {
        return times;
    }

    /**
     * 获取socket重连时间间隔。
     * @return socket重连时间间隔(秒)。
     */
    public int getReconnect() {
        return reconnect;
    }

    /**
     * socket重连时间间隔。
     * @param reconnect
     * socket重连时间间隔(秒)。
     */
    public void setReconnect(int reconnect) {
        if(reconnect > 0 && this.reconnect != reconnect) {
            this.reconnect = reconnect;
        }
    }

    @Override
    public String toString() {
        return "{server="+ getServer() +",port="+ getPort() +",rate="+ getRate() +",times="+ getTimes() +",reconnect="+ getReconnect() +"}";
    }
}