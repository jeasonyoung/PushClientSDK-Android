package com.linkus.push.sdk.models;

import com.alibaba.fastjson.JSON;
import com.linkus.push.sdk.utils.DigestUtils;
import java.util.*;

/**
 * 数据模型基类。
 * Created by jeasonyoung on 2017/3/3.
 */
public abstract class BaseModel {
    /**
     * 参数-接入帐号字段。
     */
    protected static final String PARAMS_ACCOUNT = "account";
    /**
     * 参数-参数签名字段。
     */
    private static final String PARAMS_SIGN    = "sign";

    private String account,token;

    /**
     * 参数集合。
     */
    protected final Map<String, Object> params;

    /**
     * 构造函数。
     */
    public BaseModel(){
        this.params = new HashMap<>();
    }

    /**
     * 获取接入帐号。
     * @return 接入帐号。
     */
    protected String getAccount() {
        return account;
    }

    /**
     * 设置接入帐号。
     * @param account
     * 接入帐号。
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * 获取接入密钥。
     * @return 接入密钥。
     */
    private String getToken() {
        return token;
    }

    /**
     * 设置接入密钥。
     * @param token
     * 接入密钥。
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 创建签名参数集合。
     * @return 参数集合。
     */
    protected abstract Map<String, Object> createParameters();

    /**
     * 参数签名并转换为JSON字符串。
     * @return json字符串。
     */
    public final synchronized String toSignJson(){
        final Map<String, Object> params = this.createParameters();
        if(params == null || params.size() == 0){
            throw new RuntimeException("未创建参数集合数据!");
        }
        if(!params.containsKey(PARAMS_ACCOUNT)){
            throw new RuntimeException("未设置["+ PARAMS_ACCOUNT +"]参数数据!");
        }
        if(getToken() == null || getToken().length() == 0){
            throw new IllegalArgumentException("未设置接入密钥!");
        }
        //参数签名
        final String sign = createSign(params, getToken());
        params.put(PARAMS_SIGN, sign);
        //json
        return JSON.toJSONString(params);
    }

    /**
     * 创建参数签名。
     * @param params
     * 参数集合。
     * @param token
     * 签名令牌。
     * @return 签名后的值。
     */
    private static String createSign(final Map<String,?> params,final String token){
        if(params == null || params.size() == 0){
            throw new IllegalArgumentException("参加签名的参数集合为空!");
        }
        if(token == null || token.length() == 0){
            throw new IllegalArgumentException("签名令牌为空!");
        }
        //参数集合(键值对)
        final List<String> kv = new ArrayList<>();
        Object value;
        for (Map.Entry<String, ?> entry : params.entrySet()){
            //0.排除字段名为空的字段数据
            if(entry.getKey() == null || entry.getKey().length() == 0) continue;
            //0.1 排除字段名为签名字段数据
            if(entry.getKey().equalsIgnoreCase(PARAMS_SIGN)) continue;
            //获取字段值
            value = entry.getValue();
            //1.排除参数值为NULL的
            if(value == null) continue;
            //2.排除参数值为false的
            if((value instanceof Boolean) && Boolean.FALSE.equals(value)){
                continue;
            }
            //3.排除参数值为0的
            if((value instanceof Number) && ((Number)value).floatValue() == 0){
                continue;
            }
            //4.数组类型
            if((value instanceof Collection)) {
                if(((Collection)value).size() == 0) continue;
                kv.add(entry.getKey() + "=" + join(((Collection)value).iterator(), ","));
                continue;
            }
            //5.字符串为空
            if((value instanceof String) && (((String)value).trim()).length() == 0){
                continue;
            }
            //6.拼接键值对
            kv.add(entry.getKey() +"=" + value);
        }
        if(kv.size() == 0) throw new RuntimeException("没有符合筛选后条件的参数!");
        //排序处理
        if(kv.size() > 1){
            Collections.sort(kv);//排序
        }
        //签名前参数字符串
        final String source = join(kv.iterator(), "&") + token;
        return DigestUtils.md5Hex(source);
    }

    /**
     * 将集合拼接为字符。
     * @param iterator
     * 集合iterator。
     * @param sep
     * 拼接分隔符。
     * @return
     * 拼接后的字符串。
     */
    private static String join(final Iterator<?> iterator,final String sep){
        if(iterator == null || !iterator.hasNext()) return null;
        final StringBuilder buf = new StringBuilder();
        Object obj;
        while (iterator.hasNext()){
            obj = iterator.next();
            if(obj == null) continue;
            buf.append(obj).append(sep);
        }
        String result = buf.toString();
        if(result.length() > 0 && result.endsWith(sep)){
            return result.substring(0, result.length() - 1);
        }
        return result;
    }
}
