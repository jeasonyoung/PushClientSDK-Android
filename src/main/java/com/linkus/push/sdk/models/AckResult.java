package com.linkus.push.sdk.models;

/**
 * 应答数据状态枚举。
 * Created by jeasonyoung on 2017/3/6.
 */
public enum AckResult {
    /**
     * 成功。
     */
    Success(0),
    /**
     * 接入帐号不存在。
     */
    AccountError(-1),
    /**
     * 校验码错误。
     */
    SignError(-2),
    /**
     * 参数错误。
     */
    ParamError(-3),
    /**
     * 运行错误。
     */
    Runntime(-4);

    private int val;
    /**
     * 构造函数。
     * @param val
     * 枚举值。
     */
    AckResult(final int val){
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
    public static AckResult parse(final int val){
        for(AckResult type : values()){
            if(type.val == val) return type;
        }
        return null;
    }
}