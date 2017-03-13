package com.linkus.push.sdk.utils;

import com.linkus.push.sdk.socket.Codec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 签名加密工具类。
 * Created by jeasonyoung on 2017/3/3.
 */
public class DigestUtils {
    private static final char[] DIGITS_LOWER;

    static {
        DIGITS_LOWER = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    }

    public static String md5Hex(final String source){
        if(source == null || source.length() == 0) return null;
        return encodeHexString(md5(source.getBytes(Codec.UTF8)));
    }

    private static byte[] md5(byte[] data) {
        return getDigest().digest(data);
    }

    private static MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException var2) {
            throw new IllegalArgumentException(var2);
        }
    }

    private static String encodeHexString(byte[] data) {
        return new String(encodeHex(data, DIGITS_LOWER));
    }

    private static char[] encodeHex(byte[] data, char[] toDigits) {
        int l = data.length;
        char[] out = new char[l << 1];
        int i = 0;

        for(int j = 0; i < l; ++i) {
            out[j++] = toDigits[(240 & data[i]) >>> 4];
            out[j++] = toDigits[15 & data[i]];
        }

        return out;
    }
}