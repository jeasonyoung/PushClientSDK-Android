package com.linkus.push.sdk.data;

import com.linkus.push.sdk.models.BaseModel;

import java.util.Map;

/**
 * HTTP请求数据类。
 * Created by jeasonyoung on 2017/3/4.
 */
public class HttpRequestData extends BaseModel {
    /**
     * 构造函数。
     * @param access
     * 访问配置数据。
     */
    public HttpRequestData(final AccessData access){
        super();
        if(access == null) throw new IllegalArgumentException("access");
        //接入帐号
        setAccount(access.getAccount());
        //接入密钥
        setToken(access.getPassword());
    }

    @Override
    protected Map<String, Object> createParameters() {
        params.put(PARAMS_ACCOUNT, getAccount());
        return params;
    }
}