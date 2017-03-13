package com.linkus.push.sdk.data;

import android.os.Build;

/**
 * 接入数据。
 * Created by jeasonyoung on 2017/3/3.
 */
public class AccessData {
    private String url, account, password, deviceToken, deviceName, tag;

    /**
     * 构造函数。
     */
    public AccessData(){
        deviceName = Build.MODEL + "("+ Build.BRAND +")";
    }

    /**
     * 设置接入数据。
     * @param url
     * 接入地址。
     * @param account
     * 接入帐号。
     * @param password
     * 接入密码。
     * @param deviceToken
     * 设备令牌。
     */
    public synchronized void setAccessData(final String url, final String account, final String password, final String deviceToken){
        this.url = url;
        this.account = account;
        this.password = password;
        this.deviceToken = deviceToken;
    }

    /**
     * 获取接入地址。
     * @return 接入地址。
     */
    public String getUrl() {
        return url;
    }

    /**
     * 获取接入帐号。
     * @return 接入帐号。
     */
    public String getAccount() {
        return account;
    }

    /**
     * 获取接入密码。
     * @return 接入密码。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 获取设备令牌。
     * @return 设备令牌。
     */
    public String getDeviceToken() {
        return deviceToken;
    }

    /**
     * 获取设备名称。
     * @return 设备名称。
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * 获取用户标签。
     * @return 用户标签。
     */
    public String getTag() {
        return tag;
    }

    /**
     * 设置用户标签。
     * @param tag
     * 用户标签。
     */
    public synchronized void setTag(String tag) {
        this.tag = tag;
    }
}