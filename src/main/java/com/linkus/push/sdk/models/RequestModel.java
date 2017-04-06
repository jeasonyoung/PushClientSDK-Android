package com.linkus.push.sdk.models;

import com.linkus.push.sdk.socket.MessageType;

/**
 * 请求参数模型。
 * Created by jeasonyoung on 2017/3/5.
 */
public abstract class RequestModel extends BaseModel {
    //设备类型-2 android
    public static final int CURRENT_DEVICE_TYPE = 2;//android

    /**
     * 请求参数-设备ID字段名。
     */
    static final String PARAMS_DEVICE_ID = "deviceId";

    private final MessageType type;
    private String deviceId;

    /**
     * 构造函数。
     * @param type
     * 消息类型。
     */
    RequestModel(final MessageType type){
        super();
        this.type = type;
    }

    /**
     * 获取消息类型。
     * @return 消息类型。
     */
    public MessageType getType() {
        return type;
    }

    /**
     * 获取设备唯一标示。
     * @return 设备唯一标示。
     */
    protected String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置设备唯一标示。
     * @param deviceId
     * 设备唯一标示。
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}