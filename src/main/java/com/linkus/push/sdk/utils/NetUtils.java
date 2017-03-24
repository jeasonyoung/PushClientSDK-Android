package com.linkus.push.sdk.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网络工具类。
 * Created by jeasonyoung on 2017/3/24.
 */
public final class NetUtils {
    private static final LogWrapper logger = LogWrapper.getLog(NetUtils.class);

    private static final String regex_ip = "(\\d{1,3}\\.){3}\\d{1,3}";
    private static final Map<String,String> ip_cache = new HashMap<>();
    private static final Object lock = new Object();

    private static boolean hasIPAddress(final String ipAddr){
        final Pattern pattern = Pattern.compile(regex_ip);
        final Matcher matcher = pattern.matcher(ipAddr);
        return matcher.matches();
    }

    public static String convertToIPAddr(final String server){
        logger.debug("convertToIPAddr(server="+ server +")...");
        if(server == null || server.length() == 0) return null;
        if(hasIPAddress(server)) return server;
        String ip = ip_cache.get(server);
        if(ip != null && ip.length() > 0) return ip;
        try {
            final InetAddress inetAddress = InetAddress.getByName(server);
            ip = inetAddress.getHostAddress();
            if(hasIPAddress(ip)){
                synchronized (lock){
                    ip_cache.put(server, ip);
                }
            }
        }catch (UnknownHostException e){
            logger.error("convertToIPAddr(server:"+ server +")-unknown:" + e.getMessage(), e);
            return null;
        }
        return ip;
    }

    public static void clearCache(final String server){
        logger.debug("clearCache(server="+ server+")..");
        if(server == null || server.length() == 0) return;
        if(!ip_cache.containsKey(server)) return;
        synchronized (lock){
            ip_cache.remove(server);
        }
    }
}
