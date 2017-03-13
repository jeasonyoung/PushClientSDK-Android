package com.linkus.push.sdk.models;

import com.linkus.push.sdk.socket.MessageType;

import java.util.Map;

/**
 * 断开连接请求消息数据。
 * Created by jeasonyoung on 2017/3/7.
 */
public class DisconnectModel extends RequestModel {

    /**
     * 构造函数。
     */
    public DisconnectModel() {
        super(MessageType.Disconnect);
    }

    @Override
    protected Map<String, Object> createParameters() {
        params.put(PARAMS_ACCOUNT, getAccount());
        params.put(PARAMS_DEVICE_ID, getDeviceId());
        return params;
    }
}