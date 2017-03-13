package com.linkus.push.sdk.socket;

/**
 * 固定消息头部。
 * Created by jeasonyoung on 2017/3/2.
 */
class FixedHeader {
    private MessageType type;
    private Boolean isDup, isRetain;
    private Qos qos;
    private Integer remainingLength;

    /**
     * 构造函数。
     * @param type
     * 消息类型。
     * @param ack
     * 是否须要应答。
     * @param remainingLength
     * 消息体长度。
     */
    public FixedHeader(final MessageType type, final Boolean ack, final Integer remainingLength){
        this.type = type;
        this.qos = (ack ? Qos.Ack : Qos.None);
        this.isRetain = false;
        this.isDup = false;
        this.remainingLength = remainingLength;
    }

    /**
     * 构造函数。
     * @param type
     * 消息类型。
     * @param ack
     * 是否须要应答。
     */
    public FixedHeader(final MessageType type, final Boolean ack){
        this(type, ack, 0);
    }

    /**
     * 获取消息类型。
     * @return 消息类型。
     */
    public MessageType getType() {
        return type;
    }

    /**
     * 获取标示。
     * @return 标示。
     */
    public Boolean getDup() {
        return isDup;
    }

    /**
     * 获取保持标示。
     * @return 保持标示。
     */
    public Boolean getRetain() {
        return isRetain;
    }

    /**
     * 获取Qos。
     * @return Qos。
     */
    public Qos getQos() {
        return qos;
    }

    /**
     * 获取消息体长度。
     * @return 消息体长度。
     */
    public Integer getRemainingLength() {
        return remainingLength;
    }

    /**
     * 设置消息体长度。
     * @param remainingLength
     * 消息体长度。
     */
    public void setRemainingLength(Integer remainingLength) {
        this.remainingLength = remainingLength;
    }
}