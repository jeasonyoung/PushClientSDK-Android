package com.linkus.push.sdk.utils;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志包装器。
 * Created by jeasonyoung on 2017/3/7.
 */
public final class LogWrapper {
    private static final Object lock = new Object();
    private static final Map<Class<?>, LogWrapper> cache = new HashMap<>();
    private final String tag;

    /**
     * 构造函数。
     * @param clazz
     * 类型。
     */
    private LogWrapper(final Class<?> clazz){
        this.tag = clazz.getSimpleName();
    }

    public void info(final String msg){
        Log.i(tag, msg);
    }

    public void info(final String msg, final Throwable e){
        Log.i(tag, msg, e);
    }

    public void debug(final String msg){
        Log.d(tag, msg);
    }

    public void debug(final String msg, final Throwable e){
        Log.d(tag, msg, e);
    }

    public void warn(final String msg){
        Log.w(tag, msg);
    }

    public void warn(final String msg, final Throwable e){
        Log.w(tag, msg, e);
    }

    public void error(final String msg){
        Log.e(tag, msg);
    }

    public void error(final String msg, final Throwable e){
        Log.e(tag, msg, e);
    }

    /**
     * 日志包装器。
     * @param clazz
     * 类型。
     * @return 日志包装器。
     */
    public static synchronized LogWrapper getLog(final Class<?> clazz){
        LogWrapper wrapper = cache.get(clazz);
        if(wrapper == null){
            return putIfAbsent(clazz, new LogWrapper(clazz));
        }
        return wrapper;
    }

    private static LogWrapper putIfAbsent(final Class<?> clazz, final LogWrapper wrapper){
        synchronized (lock) {
            LogWrapper log = cache.put(clazz, wrapper);
            if (log == null) {
                return wrapper;
            }
            return log;
        }
    }
}
