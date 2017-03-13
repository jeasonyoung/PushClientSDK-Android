package com.linkus.push.sdk.models;

import com.linkus.push.sdk.socket.MessageType;

import java.util.Map;

/**
 * 推送消息收到反馈请求数据。
 * Created by jeasonyoung on 2017/3/7.
 */
public class PubAckRequestModel extends RequestModel {
    private static final String PARAMS_PUSH_ID = "pushId";

    private String pushId;

    /**
     * 构造函数。
     */
    public PubAckRequestModel() {
        super(MessageType.Puback);
    }

    @Override
    protected Map<String, Object> createParameters() {
        params.put(PARAMS_ACCOUNT, getAccount());
        params.put(PARAMS_DEVICE_ID, getDeviceId());
        params.put(PARAMS_PUSH_ID, getPushId());
        return params;
    }

    /**
     * 获取推送消息ID。
     * @return 推送消息ID。
     */
    public String getPushId() {
        return pushId;
    }

    /**
     * 设置推送消息ID。
     * @param pushId
     * 推送消息ID。
     */
    public void setPushId(String pushId) {
        this.pushId = pushId;
    }
}