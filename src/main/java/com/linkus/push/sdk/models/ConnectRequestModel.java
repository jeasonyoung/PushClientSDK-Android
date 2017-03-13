package com.linkus.push.sdk.models;

import com.linkus.push.sdk.socket.MessageType;

import java.util.Map;

/**
 * 连接请求数据模型。
 * Created by jeasonyoung on 2017/3/6.
 */
public class ConnectRequestModel extends RequestModel {
    /**
     * 参数-设备名称字段。
     */
    private static final String PARAMS_DEVICE_NAME    = "deviceName";
    /**
     * 参数-设备类型字段。
     */
    private static final String PARAMS_DEVICE_TYPE    = "deviceType";
    /**
     * 参数-设备帐号字段。
     */
    private static final String PARAMS_DEVICE_ACCOUNT = "deviceAccount";

    //设备类型-2 android
    private static final int CURRENT_DEVICE_TYPE = 2;//android

    private String deviceName,deviceAccount;
    private final Integer deviceType;

    /**
     * 构造函数。
     */
    public ConnectRequestModel() {
        super(MessageType.Connect);
        this.deviceType = CURRENT_DEVICE_TYPE;
    }

    /**
     * 创建参数集合。
     * @return
     * 参数集合。
     */
    @Override
    protected Map<String, Object> createParameters() {
        //1.接入帐号
        params.put(PARAMS_ACCOUNT, getAccount());
        //2.设备ID
        params.put(PARAMS_DEVICE_ID, getDeviceId());
        //3.设备名称
        params.put(PARAMS_DEVICE_NAME, getDeviceName());
        //4.设备类型
        params.put(PARAMS_DEVICE_TYPE,getDeviceType());
        //5.设备帐号
        params.put(PARAMS_DEVICE_ACCOUNT, getDeviceAccount());
        //
        return params;
    }

    /**
     * 获取设备名称。
     * @return 设备名称。
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * 设置设备名称。
     * @param deviceName
     * 设备名称。
     */
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    /**
     * 获取设备用户帐号(tag)。
     * @return 设备用户帐号(tag)。
     */
    public String getDeviceAccount() {
        return deviceAccount;
    }

    /**
     * 设置设备用户帐号(tag)。
     * @param deviceAccount
     * 设备用户帐号(tag)。
     */
    public void setDeviceAccount(String deviceAccount) {
        this.deviceAccount = deviceAccount;
    }

    /**
     * 获取设备类型代码。
     * @return 设备类型代码。
     */
    public Integer getDeviceType() {
        return deviceType;
    }
}