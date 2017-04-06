package com.linkus.push.sdk.data;

import java.io.Serializable;

/**
 * 访问配置数据接口。
 * Created by jeasonyoung on 2017/4/6.
 */
public interface IAccessConfig extends Serializable {
    /**
     * 获取接入地址。
     * @return 接入地址。
     */
    String getUrl();

    /**
     * 获取接入帐号。
     * @return 接入帐号。
     */
    String getAccount();

    /**
     * 获取接入密码。
     * @return 接入密码。
     */
    String getPassword();

    /**
     * 获取设备令牌。
     * @return 设备令牌。
     */
    String getDeviceToken();

    /**
     * 获取设备名称。
     * @return 设备名称。
     */
    String getDeviceName();

    /**
     * 获取用户标签。
     * @return 用户标签。
     */
    String getTag();
}
