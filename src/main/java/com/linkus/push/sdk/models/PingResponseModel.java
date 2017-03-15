package com.linkus.push.sdk.models;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.linkus.push.sdk.utils.DigestUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 心跳反馈数据。
 * Created by jeasonyoung on 2017/3/7.
 */
public class PingResponseModel {
    private static final Map<String, PingResponseModel> cache = new HashMap<>();
    private static final Object lock = new Object();

    private static final String PARAMS_HEART_RATE = "heartRate";
    private static final String PARAMS_AFTER_CONNECT = "afterConnect";

    private Integer heartRate,afterConnect;

    /**
     * 构造函数。
     * @param model
     * 数据。
     */
    private PingResponseModel(JSONObject model){
        if(model == null || model.size() == 0){
            throw new IllegalArgumentException("model");
        }
        if(model.containsKey(PARAMS_HEART_RATE)){//1
            this.heartRate = model.getInteger(PARAMS_HEART_RATE);
        }
        if(model.containsKey(PARAMS_AFTER_CONNECT)){
            this.afterConnect = model.getInteger(PARAMS_AFTER_CONNECT);
        }
    }

    /**
     * 获取心跳间隔(秒)。
     * @return 心跳间隔(秒)。
     */
    public Integer getHeartRate() {
        return heartRate;
    }

    /**
     * 获取断开重连间隔(秒)。
     * @return 断开重连间隔(秒)。
     */
    public Integer getAfterConnect() {
        return afterConnect;
    }

    /**
     * 解析心跳反馈JSON。
     * @param json
     * json.
     * @return 心跳反馈对象。
     */
    public static PingResponseModel parseJson(final String json){
        if(json == null || json.length() == 0){
            throw new IllegalArgumentException("json");
        }
        final String key = DigestUtils.md5Hex(json);
        PingResponseModel model = cache.get(key);
        if(model == null){
            synchronized (lock){
                model = new PingResponseModel(JSON.parseObject(json));
                cache.put(key, model);
            }
        }
        return model;
    }
}