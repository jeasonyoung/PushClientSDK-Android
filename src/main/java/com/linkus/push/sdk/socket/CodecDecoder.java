package com.linkus.push.sdk.socket;

import com.linkus.push.sdk.models.AckModel;
import com.linkus.push.sdk.models.PingResponseModel;
import com.linkus.push.sdk.models.PublishModel;
import com.linkus.push.sdk.utils.LogWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * socket消息解码器
 * Created by jeasonyoung on 2017/3/7.
 */
public final class CodecDecoder extends Codec {
    private static final LogWrapper logger = LogWrapper.getLog(CodecDecoder.class);
    private static final int HEAD_DATA_MIN_LEN = 5;

    private volatile FixedHeader header = null;
    private final ByteArrayOutputStream buffer;

    private final CodecDecoderListener listener;

    /**
     * 构造函数。
     *
     * @param listener 消息解码监听器。
     */
    CodecDecoder(final CodecDecoderListener listener) {
        this.listener = listener;
        this.buffer = new ByteArrayOutputStream();
    }

    /**
     * 添加需要解码的数据。
     *
     * @param data 原始字节数据。
     */
    void addDecode(final byte[] data) {
        if (data == null || data.length == 0) return;
        if (header == null) {//无消息头
            synchronized (this) {
                buffer.write(data, 0, data.length);
            }
            if (buffer.size() < HEAD_DATA_MIN_LEN) {
                logger.warn("接收数据小于最小解析数据[" + HEAD_DATA_MIN_LEN + "]，继续接收...");
                return;
            }
            //数据缓存
            final ByteArrayInputStream input = new ByteArrayInputStream(buffer.toByteArray());
            synchronized (this) {
                buffer.reset();
            }
            try {
                synchronized (this) {
                    //解析消息头
                    header = decodeHeader(input);
                }
            } catch (Exception e) {
                logger.error("解析消息头异常:" + e.getMessage(), e);
                return;
            }
            //剩余消息长度
            final int available = input.available();
            if (available > 0) {
                byte[] buf = new byte[available];
                final ByteArrayOutputStream output = new ByteArrayOutputStream(available);
                int count;
                while ((count = input.read(buf, 0, buf.length)) > 0) {
                    output.write(buf, 0, count);
                }
                if (header.getRemainingLength() == 0) {//无消息体
                    decodeMessageHandler(header.getType(), null);
                    synchronized (this) {
                        header = null;
                    }
                }
                addDecode(output.toByteArray());
            } else if (header.getRemainingLength() == 0) {//无剩余长度，无消息体
                decodeMessageHandler(header.getType(), null);
                synchronized (this) {
                    header = null;
                }
            }
        } else {//解析消息体
            synchronized (this) {//写入缓存流
                buffer.write(data, 0, data.length);
            }
            //检查数据是否满足消息体长度
            if (buffer.size() >= header.getRemainingLength()) {
                //读取数据
                final byte buf[] = buffer.toByteArray();
                synchronized (this) {
                    buffer.reset();
                }
                //消息体数据
                final byte payload[] = Arrays.copyOf(buf, header.getRemainingLength());
                //消息处理
                try {
                    decodeMessageHandler(header.getType(), payload);
                } catch (Exception e) {
                    logger.warn("解析消息处理异常:" + e.getMessage(), e);
                }
                //销毁缓存对象
                synchronized (this) {
                    header = null;
                }
                //剩余长度
                final int available = buf.length - payload.length;
                if (available > 0) {//有剩余长度
                    addDecode(Arrays.copyOfRange(buf, payload.length, buf.length));
                }
            }
        }
    }

    //解析消息数据处理
    private void decodeMessageHandler(final MessageType type, final byte[] payload) {
        try {
            if (listener == null) {
                logger.warn("未设置消息解码监听器!");
                return;
            }
            if (payload == null) {
                listener.decode(type, null);
                return;
            }
            final String json = new String(payload, Codec.UTF8);
            logger.info("decode[" + type + "]=>\n" + json);
            switch (type) {
                case None: {//0.未知消息类型
                    logger.warn("未知消息类型=>" + type);
                    break;
                }
                case Connack://连接请求应答
                case Pubrel://推送消息到达请求应答
                case Suback://用户登录请求应答
                case Unsuback://用户注销请求应答
                {
                    listener.decode(type, new AckModel(json));
                    break;
                }
                case Publish: {//推送消息下行
                    listener.decode(type, new PublishModel(json));
                    break;
                }
                case Pingresp: {//心跳请求应答
                    listener.decode(type, PingResponseModel.parseJson(json));
                    break;
                }
                default: {
                    logger.warn("消息类型[" + type + "]不能被解析!\n" + json);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("解析消息[" + type + "]数据处理异常:" + e.getMessage(), e);
        }
    }

    /**
     * socket 消息解码器监听器。
     */
    public interface CodecDecoderListener{
        /**
         * 解码数据处理。
         * @param type
         * 消息类型。
         * @param model
         * 数据模型。
         */
        void decode(final MessageType type, final Object model);
    }
}