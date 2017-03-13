package com.linkus.push.sdk.models;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 应答数据模型。
 * Created by jeasonyoung on 2017/3/6.
 */
public class AckModel {
    private static final String MODEL_RESULT = "result";
    private static final String MODEL_MSG    = "msg";

    private AckResult result;
    private String msg;

    /**
     * 构造函数。
     * @param ackJson
     * 应答消息JSON。
     */
    public AckModel(final String ackJson){
        if(ackJson == null || ackJson.length() == 0){
            throw new IllegalArgumentException("ackJson");
        }
        final JSONObject obj = JSON.parseObject(ackJson);
        if(obj != null){
            if(obj.containsKey(MODEL_RESULT)) {
                this.result = AckResult.parse(obj.getIntValue(MODEL_RESULT));
            }
            if(obj.containsKey(MODEL_MSG)) {
                this.msg = obj.getString(MODEL_MSG);
            }
        }
    }

    /**
     * 获取应答结果枚举。
     * @return 应答结果枚举。
     */
    public AckResult getResult() {
        return result;
    }

    /**
     * 获取应答消息。
     * @return 应答消息。
     */
    public String getMsg() {
        return msg;
    }
}