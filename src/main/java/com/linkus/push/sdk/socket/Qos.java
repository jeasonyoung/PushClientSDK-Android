package com.linkus.push.sdk.socket;

/**
 * socket 消息Qos枚举
 * Created by jeasonyoung on 2017/3/2.
 */
enum Qos {
    /**
     * 无应答。
     */
    None(0),
    /**
     * 不处置。
     */
    Not(1),
    /**
     * 必须应答。
     */
    Ack(2);

    private int val;
    /**
     * 构造函数。
     * @param val
     * 枚举值。
     */
    Qos(final int val){
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
     * 消息Qos枚举转换。
     * @param val
     * Qos枚举值。
     * @return
     * 消息Qos枚举对象。
     */
    public static Qos parse(final int val){
        for(Qos qos : values()){
            if(qos.val == val) return qos;
        }
        return null;
    }
}
