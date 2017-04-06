package com.linkus.push.sdk.utils;

import android.os.AsyncTask;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.linkus.push.sdk.data.SocketConfig;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.socket.Codec;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Http工具类。
 * Created by jeasonyoung on 2017/3/8.
 */
public final class HttpUtils {
    private static final LogWrapper logger = LogWrapper.getLog(HttpUtils.class);
    private static final String POST_METHOD  = "POST";
    private static final int    POST_TIMEOUT = 5000;
    private static final String APPLICATION_JSON = "application/json";

    public static final String PARAMS_RESULT    = "result";
    public static final String PARAMS_MESSAGE   = "message";
    private static final String PARAMS_SETTING   = "setting";

    public static final String SRV_URL_PREFIX = "http";
    public static final String SRV_URL_SUFFIX = "/push-http-connect/v1/callback/connect.do";

    /**
     * 异步提交请求。
     * @param url
     * URL
     * @param paramJson
     * JSON请求参数。
     * @param listener
     * 回调处理
     */
    public static void asyncPostJson(final String url, final String paramJson, final HttpUtilSyncListener listener){
        new AsyncTask<Void,Void,String>(){
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    return postJson(url, paramJson);
                }catch (Exception ex){
                    logger.error("postJson-exception:" + ex.getMessage(), ex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                try {
                    logger.debug("postJson-result=>" + result);
                    final JSONObject obj = JSON.parseObject(result);
                    if(obj == null || obj.size() == 0) throw new RuntimeException("数据为空!");
                    final AckResult ack = AckResult.parse(obj.getIntValue(PARAMS_RESULT));
                    if(ack == null) throw new RuntimeException("状态未知!=>" + obj.getIntValue(PARAMS_RESULT));
                    if(ack != AckResult.Success){
                        listener.onResponse(ack, obj.getString(PARAMS_MESSAGE), null);
                        return;
                    }
                    listener.onResponse(ack, null, new SocketConfig(obj.getJSONObject(PARAMS_SETTING)));

                }catch (Exception e){
                    logger.error("解析数据异常:" + e.getMessage(), e);
                    listener.onResponse(AckResult.Runntime, e.getMessage(), null);
                }
            }
        }.execute((Void)null);
    }

    private static String postJson(final String url, final String json) throws Exception{
        if(url == null || url.length() == 0){
            throw new IllegalArgumentException("url");
        }
        if(json == null || json.length() == 0){
            throw new IllegalArgumentException("json");
        }
        String result = null;
        try{
            logger.info("post-url:" + url);
            logger.info("post-json:" + json);
            //初始化连接
            final HttpURLConnection connection = (HttpURLConnection) (new URL(url).openConnection());
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(POST_METHOD);
            connection.setConnectTimeout(POST_TIMEOUT);
            //设置接收数据的格式
            connection.setRequestProperty("Accept", APPLICATION_JSON);
            //设置发送数据的格式
            connection.setRequestProperty("Content-Type", APPLICATION_JSON);
            //发起连接
            connection.connect();
            //写入发送数据
            final OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), Codec.UTF8);
            out.append(json);
            out.flush();
            closeQuietly(out);//关闭写入
            //读取响应
            final int code = connection.getResponseCode();
            logger.info("响应代码:" + code);
            if(code != 200){
                throw new Exception("网络异常:" + code);
            }
            //读取响应流
            final byte data[] = toByteArray(connection.getInputStream());
            connection.disconnect();//关闭连接
            if(data != null && data.length > 0){
                result = new String(data, Codec.UTF8);
                logger.info("post-response:\n" + result);
            }
        }catch (Exception e){
            logger.error("post-json 异常:" + e.getMessage(), e);
            throw e;
        }
        return result;
    }

    //流转换为数组
    private static byte[] toByteArray(final InputStream input) throws Exception{
        if(input == null) return null;
        byte buf[] = new byte[512];
        final ByteArrayOutputStream output = new ByteArrayOutputStream(buf.length);
        int count;
        while ((count = input.read(buf, 0, buf.length)) != -1){
            output.write(buf, 0, count);
        }
        return output.toByteArray();
    }

    //关闭流
    private static void closeQuietly(final Closeable closeable){
        try{
            if(closeable != null){
                closeable.close();
            }
        }catch (Exception e){
            logger.warn("closeQuietly-异常:" + e.getMessage(), e);
        }
    }

    /**
     * 异步消息处理监听器。
     */
    public interface HttpUtilSyncListener{

        void onResponse(final AckResult result, final String error, final SocketConfig config);
    }
}