package com.linkus.push.sdk.socket;

import com.linkus.push.sdk.data.AccessData;
import com.linkus.push.sdk.models.*;
import com.linkus.push.sdk.utils.LogWrapper;

/**
 * 消息编码器。
 * Created by jeasonyoung on 2017/3/5.
 */
public final class CodecEncoder extends Codec {
    private static final LogWrapper logger = LogWrapper.getLog(CodecEncoder.class);

    /**
     * 连接请求编码处理。
     * @param access
     * 接入配置数据。
     * @param handler
     * 编码处理监听器。
     */
    void encodeConnectRequest(final AccessData access, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[connect]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        //创建消息数据
        final ConnectRequestModel model = new ConnectRequestModel();
        buildCommonParams(access, model);
        model.setDeviceAccount(access.getTag());//1.设备用户帐号
        model.setDeviceName(access.getDeviceName());//2.设备名称
        //消息编码处理
        encodeHandler(model, true, handler);
    }

    /**
     * 推送消息到达请求编码处理。
     * @param access
     * 接入配置数据。
     * @param pushId
     * 推送ID。
     * @param handler
     * 编码处理监听器。
     */
    void encodePublishAckRequest(final AccessData access, final String pushId, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[publish-request]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        if(pushId == null || pushId.trim().length() <= 0){
            logger.error("推送ID不存在!");
            return;
        }
        //创建消息到达请求数据
        final PubAckRequestModel model = new PubAckRequestModel();
        buildCommonParams(access, model);
        model.setPushId(pushId);//1.推送ID
        //消息编码
        encodeHandler(model, true, handler);
    }

    /**
     * 用户登录请求消息编码处理。
     * @param access
     * 接入配置数据。
     * @param handler
     * 编码处理监听器。
     */
    void encodeSubscribe(final AccessData access, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[Subscribe-request]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        if(access.getTag() == null || access.getTag().length() <= 0){
            logger.error("获取设备标签(tag)不存在!");
            return;
        }
        //创建用户登录请求消息数据
        final SubscribeRequestModel model = new SubscribeRequestModel();
        buildCommonParams(access, model);
        model.setDeviceAccount(access.getTag());//1.设备帐号用户
        //消息编码
        encodeHandler(model, true, handler);
    }

    /**
     * 用户注销请求消息编码处理。
     * @param access
     * 接入配置数据。
     * @param handler
     * 编码处理监听器。
     */
    void encodeUnsubscribe(final AccessData access, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[Unsubscribe-request]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        //创建用户注销请求消息数据
        final UnsubscribeRequestModel model = new UnsubscribeRequestModel();
        buildCommonParams(access, model);
        //消息编码
        encodeHandler(model, true, handler);
    }

    /**
     * 心跳请求消息编码处理。
     * @param access
     * 接入配置数据。
     * @param handler
     * 编码处理监听器。
     */
    void encodePingRequest(final AccessData access, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[Ping-request]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        //创建心跳请求消息数据
        final PingRequestModel model = new PingRequestModel();
        buildCommonParams(access, model);
        //消息编码处理
        encodeHandler(model,true, handler);
    }

    /**
     * 断开连接请求消息编码处理。
     * @param access
     * 接入配置数据。
     * @param handler
     * 编码处理监听器。
     */
    void encodeDisconnect(final AccessData access, final CodecEncoderListener handler){
        logger.debug("开始客户端发起[Disconnect-request]请求处理...");
        if(access == null){
            logger.error("获取配置数据失败!");
            return;
        }
        //创建断开连接请求消息数据
        final DisconnectModel model = new DisconnectModel();
        buildCommonParams(access, model);
        //消息编码处理
        encodeHandler(model, false, handler);
    }

    /**
     * 构建请求数据的通用函数。
     * @param access
     * 接入配置数据。
     * @param requestModel
     * 请求数据。
     */
    private void buildCommonParams(final AccessData access, final RequestModel requestModel){
        if(access == null || requestModel == null){
            logger.error("buildCommonParams 配置数据或请求数据为空!");
            return;
        }
        //1.接入帐号
        requestModel.setAccount(access.getAccount());
        //2.接入密码
        requestModel.setToken(access.getPassword());
        //3.设备ID
        requestModel.setDeviceId(access.getDeviceToken());
    }

    /**
     * 请求数据模型编码处理。
     * @param model
     * 请求数据模型。
     * @param ack
     * 是否需要应答。
     * @param handler
     * 编码后处理。
     */
    private void encodeHandler(final RequestModel model, final boolean ack, final CodecEncoderListener handler){
        if(model == null || handler == null){
            logger.error("encodeHandler-请求数据或编码处理为空!");
            return;
        }
        //创建消息头
        final FixedHeader header = new FixedHeader(model.getType(), ack);
        final byte[] data = encode(header, model.toSignJson());
        if(data != null && data.length > 0){
            handler.encode(header.getType(), data);
        }
    }

    /**
     * 消息编码事件处理器。
     */
    public interface CodecEncoderListener{
        /**
         * 编码处理。
         * @param type
         * 发送消息类型。
         * @param data
         * 编码处理后的数据。
         */
        void encode(final MessageType type, final byte[] data);
    }
}