package com.linkus.push.sdk.data;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * 接入数据。
 * Created by jeasonyoung on 2017/3/3.
 */
public final class AccessData implements IAccessConfig, Parcelable {
    private static final String bundle_key = AccessData.class.getSimpleName();

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

    @Override
    public String toString() {
        return "{url="+ getUrl() +",account="+ getAccount() +", password="+getPassword()+", deviceToken="+getDeviceToken()+", deviceName="+ getDeviceName()+", tag="+ getTag() +"}";
    }

    /**
     * 构建Bundle数据。
     * @return Bundle数据。
     */
    public Bundle buildBundle(){
        final Bundle data = new Bundle(AccessData.class.getClassLoader());
        data.putParcelable(bundle_key, this);
        return data;
    }

    /**
     * 从bundle中解析数据。
     * @param data
     * bundle数据。
     * @return 解析后数据
     */
    public static AccessData parseBundle(final Bundle data){
        data.setClassLoader(AccessData.class.getClassLoader());
        return data.getParcelable(bundle_key);
    }


    private AccessData(Parcel source){
        //1.url
        url = source.readString();
        //2.account
        account = source.readString();
        //3.password
        password = source.readString();
        //4.deviceToken
        deviceToken = source.readString();
        //5.deviceName
        deviceName = source.readString();
        //6.tag
        tag = source.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        //1.url
        dest.writeString(getUrl());
        //2.account
        dest.writeString(getAccount());
        //3.password
        dest.writeString(getPassword());
        //4.deviceToken
        dest.writeString(getDeviceToken());
        //5.deviceName
        dest.writeString(getDeviceName());
        //6.tag
        dest.writeString(getTag());
    }

    public static final Parcelable.Creator<AccessData> CREATOR = new Parcelable.Creator<AccessData>(){
        @Override
        public AccessData createFromParcel(Parcel parcel) {
            return new AccessData(parcel);
        }

        @Override
        public AccessData[] newArray(int size) {
            return new AccessData[size];
        }
    };
}