package com.linkus.push.sdk.socket;

/**
 * 推送消息类型枚举。
 * Created by jeasonyoung on 2017/3/2.
 */
public enum MessageType {
    /**
     * 未知消息类型。
     */
    None(0),
    /**
     * 连接请求。
     */
    Connect(1),
    /**
     * 连接请求应答。
     */
    Connack(2),
    /**
     * 推送消息下行。
     */
    Publish(3),
    /**
     * 推送消息到达请求。
     */
    Puback(4),
    /**
     * 推送消息到达请求应答。
     */
    Pubrel(6),
    /**
     * 用户登录请求。
     */
    Subscribe(8),
    /**
     * 用户登录请求应答。
     */
    Suback(9),
    /**
     * 用户注销请求。
     */
    Unsubscribe(10),
    /**
     * 用户注销请求应答。
     */
    Unsuback(11),
    /**
     * 心跳请求。
     */
    Pingreq(12),
    /**
     * 心跳应答。
     */
    Pingresp(13),
    /**
     * 断开请求。
     */
    Disconnect(14);


    private int val;
    /**
     * 构造函数。
     * @param val
     * 枚举值。
     */
    MessageType(final int val){
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
     * 枚举转换。
     * @param val
     * 枚举值。
     * @return
     * 枚举对象。
     */
    public static MessageType parse(final int val){
        for(MessageType type : values()){
            if(type.val == val) return type;
        }
        return null;
    }
}
