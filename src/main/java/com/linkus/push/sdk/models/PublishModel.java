package com.linkus.push.sdk.models;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送消息数据模型。
 * Created by jeasonyoung on 2017/3/6.
 */
public class PublishModel {
    private static final String PUBLISH_PUSH_ID = "pushId";
    private static final String PUBLISH_CONTENT_ID = "contentId";
    private static final String PUBLISH_CONTENT = "content";
    private static final String PUBLISH_APS = "aps";

    private ApsModel aps;
    private String pushId,contentId,content,json;

    /**
     * 构造函数。
     * @param model
     * 消息数据模型。
     */
    private PublishModel(final JSONObject model){
        if(model.size() > 0) {
            if (model.containsKey(PUBLISH_PUSH_ID)) {//1
                this.pushId = model.getString(PUBLISH_PUSH_ID);
            }
            if (model.containsKey(PUBLISH_CONTENT_ID)) {//2
                this.contentId = model.getString(PUBLISH_CONTENT_ID);
            }
            if (model.containsKey(PUBLISH_CONTENT)) {//3
                final Object obj = model.get(PUBLISH_CONTENT);
                if (obj instanceof String) {
                    this.content = obj.toString();
                } else if (obj instanceof JSONObject) {
                    this.content = JSON.toJSONString(obj);
                }
            }
            if (model.containsKey(PUBLISH_APS)) {//4
                this.aps = new ApsModel(model.getJSONObject(PUBLISH_APS));
            }
        }
    }

    /**
     * 构造函数。
     * @param json
     * json字符串
     */
    public PublishModel(final String json){
        this(JSON.parseObject(json));
        this.json = json;
    }

    /**
     * 获取aps格式消息。
     * @return aps格式消息。
     */
    public ApsModel getAps() {
        return aps;
    }

    /**
     * 获取推送消息ID。
     * @return 推送消息ID。
     */
    public String getPushId() {
        return pushId;
    }

    /**
     * 获取推送消息内容ID。
     * @return 推送消息内容ID。
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * 获取推送消息内容。
     * @return 推送消息内容。
     */
    public String getContent() {
        return content;
    }

    /**
     * json字符串。
     * @return json字符串。
     */
    public String toJson(){
        return this.json;
    }

    @Override
    public String toString() {
        return toJson();
    }

    /**
     * Apns消息格式。
     */
    public class ApsModel{
        private static final String APS_BADGE = "badge";
        private static final String APS_SOUND = "sound";
        private static final String APS_CONTENT_AVAILABLE = "content-available";
        private static final String APS_ALERT = "alert";

        private Object alert;
        private Integer badge,contentAvailable;
        private String sound;

        /**
         * 构造函数。
         * @param model
         * aps格式数据模型。
         */
        ApsModel(final JSONObject model){
            if(model.size() > 0) {
                if (model.containsKey(APS_BADGE)) {//1
                    this.badge = model.getInteger(APS_BADGE);
                }
                if (model.containsKey(APS_SOUND)) {//2
                    this.sound = model.getString(APS_SOUND);
                }
                if (model.containsKey(APS_CONTENT_AVAILABLE)) {//3
                    this.contentAvailable = model.getInteger(APS_CONTENT_AVAILABLE);
                }
                if (model.containsKey(APS_ALERT)) {
                    final Object obj = model.get(APS_ALERT);
                    if (obj instanceof JSONObject) {
                        this.alert = new ApsAlertModel((JSONObject) obj);
                    } else if (obj instanceof String) {
                        this.alert = obj.toString();
                    }
                }
            }
        }

        /**
         * 获取alert数据对象。
         * @return alert数据对象。
         */
        public Object getAlert() {
            return alert;
        }

        /**
         * 获取App右角数字。
         * @return App右角数字。
         */
        public Integer getBadge() {
            return badge;
        }

        public Integer getContentAvailable() {
            return contentAvailable;
        }

        public String getSound() {
            return sound;
        }
    }

    /**
     * 推送消息弹出模型
     */
    public class ApsAlertModel{
        private static final String APS_ALERT_BODY = "body";
        private static final String APS_ALERT_ACTION_LOC_KEY = "action-loc-key";
        private static final String APS_ALERT_LOC_KEY = "loc-key";
        private static final String APS_ALERT_LAUNCH_IMAGE = "launch-image";
        private static final String APS_ALERT_LOC_ARGS = "loc-args";

        private String body,actionLocKey,locKey,launchImage;
        private String[] locArgs;

        /**
         * 构造函数。
         * @param model
         * 模型数据。
         */
        ApsAlertModel(final JSONObject model){
            if(model == null || model.size() == 0){
                throw new IllegalArgumentException("model");
            }
            if(model.containsKey(APS_ALERT_BODY)){
                this.body = model.getString(APS_ALERT_BODY);
            }
            if(model.containsKey(APS_ALERT_ACTION_LOC_KEY)){
                this.actionLocKey = model.getString(APS_ALERT_ACTION_LOC_KEY);
            }
            if(model.containsKey(APS_ALERT_LOC_KEY)){
                this.locKey = model.getString(APS_ALERT_LOC_KEY);
            }
            if(model.containsKey(APS_ALERT_LAUNCH_IMAGE)){
                this.launchImage = model.getString(APS_ALERT_LAUNCH_IMAGE);
            }
            if(model.containsKey(APS_ALERT_LOC_ARGS)){
                final List<Object> list = model.getJSONArray(APS_ALERT_LOC_ARGS);
                final int size;
                if(list != null && (size = list.size()) > 0){
                    final List<String> result = new ArrayList<>(size);
                    for(Object obj : list){
                        if(obj == null || !(obj instanceof String)) continue;
                        result.add(obj.toString());
                    }
                    this.locArgs = result.toArray(new String[result.size()]);
                }
            }
        }

        /**
         * 获取弹出消息内容。
         * @return 弹出消息内容。
         */
        public String getBody() {
            return body;
        }

        public String getActionLocKey() {
            return actionLocKey;
        }

        public String getLocKey() {
            return locKey;
        }

        public String getLaunchImage() {
            return launchImage;
        }

        public String[] getLocArgs() {
            return locArgs;
        }
    }
}