package com.linkus.push.sdk.models;

import com.linkus.push.sdk.socket.MessageType;

import java.util.Map;

/**
 * 用户登录请求数据。
 * Created by jeasonyoung on 2017/3/7.
 */
public class SubscribeRequestModel extends RequestModel {
    private static final String PARAMS_DEVICE_ACCOUNT = "deviceAccount";

    private String deviceAccount;

    /**
     * 构造函数。
     */
    public SubscribeRequestModel() {
        super(MessageType.Subscribe);
    }

    @Override
    protected Map<String, Object> createParameters() {
        params.put(PARAMS_ACCOUNT, getAccount());
        params.put(PARAMS_DEVICE_ID, getDeviceId());
        params.put(PARAMS_DEVICE_ACCOUNT, getDeviceAccount());
        return params;
    }

    /**
     * 获取设备用户名(tag)。
     * @return 设备用户名(tag)。
     */
    public String getDeviceAccount() {
        return deviceAccount;
    }

    /**
     * 设置设备用户名(tag)。
     * @param deviceAccount
     * 设备用户名(tag)。
     */
    public void setDeviceAccount(String deviceAccount) {
        this.deviceAccount = deviceAccount;
    }
}