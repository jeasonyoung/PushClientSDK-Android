package com.linkus.push.sdk.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * socket 编码/解码器。
 * Created by jeasonyoung on 2017/3/2.
 */
public abstract class Codec {
    /**
     * 编码字符集。
     */
    public static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * 消息编码。
     * @param header
     * 消息头。
     * @param payload
     * 消息体。
     * @return
     * 编码后的字节数组。
     */
     byte[] encode(final FixedHeader header, final String payload){
        if(header == null) return null;
        byte[] data = null;
        if(payload != null && payload.length() > 0){
            data = payload.getBytes(UTF8);
        }
        header.setRemainingLength(data == null ? 0 : data.length);
        final int size = this.calcHeaderSize(header.getRemainingLength());
        if(size < 0){
            throw new RuntimeException("消息长度超过了预定的长度!=>" + header.getRemainingLength());
        }
        //输出数据流
        final ByteArrayOutputStream buf = new ByteArrayOutputStream(size + 1);
        //写入消息头
        this.writeHeader(buf, header);
        //写入消息体
        if(data != null && data.length > 0){
            buf.write(data, 0, data.length);
        }
        //返回
        return buf.toByteArray();
    }

    /**
     * 写入消息头数据。
     * @param out
     * 写入的输出数据流。
     * @param header
     * 消息头。
     */
    private void writeHeader(final ByteArrayOutputStream out, final FixedHeader header){
        //第一个字节
        byte ret = 0;
        //消息类型(高四位)
        ret |= header.getType().getVal() << 4;
        //dup
        if(header.getDup()) ret |= 0x08;
        //qos
        ret |= header.getQos().getVal() << 1;
        //retain
        if(header.getRetain()) ret |= 0x01;
        ret &= 0xFF;
        //写入第一个字节
        out.write(ret);
        //消息体长度
        int num = header.getRemainingLength();
        if(num == 0){
            out.write(0);
            return;
        }
        do{
            int digit = (num & 0x7f);
            num = num >>> 7;
            if(num > 0){
                digit |= 0x80;
            }
            digit &= 0xff;
            out.write(digit);
        }while (num > 0);
    }

    /**
     * 计算消息体长度所占字节长度。
     * @param remainingLength
     * 消息体长度。
     * @return
     * 占字节长度。
     */
    private int calcHeaderSize(final Integer remainingLength){
        int size = 1;
        if(remainingLength == null || remainingLength <= 0) return size;
        int len = remainingLength >>> 7;
        while(len > 0){
            len = len >>> 7;
            size++;
        }
        if(size > 4) return -1;
        return size;
    }

    /**
     * 消息头解码。
     * @param stream
     * 消息数据流。
     * @return
     * 消息头。
     */
     FixedHeader decodeHeader(final ByteArrayInputStream stream){
        //读取第一个字节
        final short b1 = (short) stream.read();
        if(b1 <= 0) throw new RuntimeException("读取消息头数据不符合通讯协议!");
        //消息类型
        final MessageType type = MessageType.parse(b1 >>> 4);
        if(type == null) throw new RuntimeException("读取消息头数据消息类型不符合通讯协议!");
        //qos
        final Qos qos = Qos.parse((b1 & 0x06) >>> 1);
        if(qos == null) throw new RuntimeException("读取消息头数据qos不符合通讯协议!");
        //读取消息体长度
        int remainingLength = 0, multiplier = 1, loops = 0;
        short digit;
        do{
            digit = (short) stream.read();
            //转换为十进制
            remainingLength += (digit & 0x7f) * multiplier;
            multiplier *= 128;
            //计数
            loops++;
        }while (((digit & 0x80) != 0) && loops < 4);
        if(loops >= 4 && (digit & 0x80) != 0){
            throw new RuntimeException("消息长度大于4个字节，不符合通讯协议!");
        }
        //构造消息头
        return new FixedHeader(type, (qos == Qos.Ack), remainingLength);
    }

}