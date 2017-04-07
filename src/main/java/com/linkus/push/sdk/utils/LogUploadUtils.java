package com.linkus.push.sdk.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.linkus.push.sdk.data.IAccessConfig;
import com.linkus.push.sdk.models.AckResult;
import com.linkus.push.sdk.models.RequestModel;
import com.linkus.push.sdk.socket.Codec;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * 日志文件上传工具类。
 * Created by jeasonyoung on 2017/4/5.
 */
final class LogUploadUtils {
    private static final LogWrapper logger = LogWrapper.getLog(LogUploadUtils.class);
    private static final int TIMEOUT_CONNECT = 5000;
    private static final int TIMEOUT_READ    = 30000;

    private static final String UPLOAD_URL_SUFFIX = "/push-http-connect/v1/callback/uploader.do";

    /**
     * 上传日志文件。
     * @param config
     * 配置数据。
     * @param file
     * 日志文件。
     * @return
     * 上传结果。
     */
    static boolean uploader(final IAccessConfig config, final File file) throws Exception{
        if(config == null) throw new IllegalArgumentException("config");
        if(file == null || !file.exists() || !file.isFile()) throw new IllegalArgumentException("file");
        int pos = config.getUrl().indexOf(HttpUtils.SRV_URL_SUFFIX);
        if(pos == -1) throw new IllegalArgumentException("config.url");
        final String url = config.getUrl().substring(0, pos) + UPLOAD_URL_SUFFIX;
        logger.info("uploader-url:" + url);
        //写入设备信息
        if(config.getDeviceName() != null && config.getDeviceName().length() > 0){
            try {
                final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                randomAccessFile.setLength(0);
                randomAccessFile.write(("device-name:" + config.getDeviceName()).getBytes(Codec.UTF8));
                randomAccessFile.close();
            }catch (Exception e){
                logger.error("uploader[file:"+ file.getAbsolutePath() +"]-写入设备信息["+ config.getDeviceName() +"]异常:" + e.getMessage(), e);
            }
        }
        //上传日志文件
        final String result = uploadFile(url, createHeaders(config), file);
        logger.info("uploader["+ file.getAbsolutePath() +"]-result:" + result);
        if(result != null && result.length() > 0){
            final JSONObject obj = JSON.parseObject(result);
            if(obj == null || obj.size() == 0) throw new RuntimeException("数据为空!");
            final AckResult ack = AckResult.parse(obj.getIntValue(HttpUtils.PARAMS_RESULT));
            if(ack == null) throw new RuntimeException("状态未知!=>" + obj.getIntValue(HttpUtils.PARAMS_RESULT));
            if(ack == AckResult.Success) return true;
            throw new RuntimeException(obj.getString(HttpUtils.PARAMS_MESSAGE));
        }
        return false;
    }



    private static String uploadFile(final String url, final Map<String, String> headers, final File file) throws Exception{
        //检查参数
        if(url == null || url.length() == 0) return null;
        if(file == null || !file.exists() || !file.isFile()) return null;
        //初始化分隔符
        final String BOUNDARY = "---------------------------" + System.currentTimeMillis();
        //HTTP
        HttpURLConnection conn = null;
        try{
            conn = (HttpURLConnection) (new URL(url).openConnection());
            conn.setConnectTimeout(TIMEOUT_CONNECT);
            conn.setReadTimeout(TIMEOUT_READ);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            if(headers != null && headers.size() > 0){//设置消息头
                for (Map.Entry<String,String> entry : headers.entrySet()){
                    if(entry.getKey() == null || entry.getKey().length() == 0) continue;
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            //上传文件信息
            final String info = "\r\n" + "--" + BOUNDARY + "\r\n"
                             + "Content-Disposition: form-data; name=\"file\";"
                             + "filename=\"" + file.getName()  + "\"" + "\r\n"
                             + "Content-Type: text/plain" + "\r\n\r\n";
            //上传数据
            final OutputStream out = new DataOutputStream(conn.getOutputStream());
            //写入上传文件信息数据
            out.write(info.getBytes(Codec.UTF8));
            //写入上传文件数据
            byte[] buf = new byte[1024];
            //随机文件读取
            final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            randomAccessFile.seek(0);//定位到文件头
            int count;
            while ((count = randomAccessFile.read(buf,0, buf.length)) != -1){
                out.write(buf, 0, count);
            }
            randomAccessFile.close();//关闭文件
            //文件上传结尾
            final byte[] endData = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(Codec.UTF8);
            out.write(endData);
            out.flush();
            out.close();
            //
            if(conn.getResponseCode() == HttpURLConnection.HTTP_OK) {//上传成功
                //读取返回数据
                final StringBuilder builder = new StringBuilder();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), Codec.UTF8));
                String line;
                while ((line = reader.readLine()) != null){
                    builder.append(line);
                }
                reader.close();
                return builder.toString();
            }
        }catch (Exception e){
            logger.error("上传日志文件["+ file.getAbsolutePath() +"]异常:" + e.getMessage(), e);
        }finally {
            if(conn != null){
                conn.disconnect();
            }
        }
        return null;
    }

    //创建文件上传头集合
    private static Map<String,String> createHeaders(final IAccessConfig config){
        if(config == null) return null;
        final Map<String, String> headers = new HashMap<>();
        headers.put("PUSH_ACCOUNT", config.getAccount());
        headers.put("PUSH_DEVICE_TYPE", RequestModel.CURRENT_DEVICE_TYPE + "");
        headers.put("PUSH_DEVICE_TOKEN", config.getDeviceToken());
        headers.put("PUSH_DEVICE_TAG",config.getTag());
        headers.put("PUSH_RANDON_VALUE", System.currentTimeMillis() + "");
        headers.put("PUSH_SIGN", createHeaderSign(headers, config.getPassword()));
        return headers;
    }

    //头消息参数签名
    private static String createHeaderSign(final Map<String, String> headers,final String token){
        //获取参数值集合
        final List<String> list = new ArrayList<>();
        for(Map.Entry<String, String> entry : headers.entrySet()){
            if(entry.getValue() == null || entry.getValue().length() == 0) continue;
            list.add(entry.getValue());
        }
        //参数值排序
        Collections.sort(list);
        logger.debug("createHeaderSign-排序后参数=>" + DigestUtils.join(list.iterator(), ","));
        //签名计算前数据
        final String source = DigestUtils.join(list.iterator(),"$") + token;
        final String result = DigestUtils.md5Hex(source);
        logger.debug("createHeaderSign-签名计算前字符串[digest:"+ result +"]=>" + source);
        return result;
    }


}