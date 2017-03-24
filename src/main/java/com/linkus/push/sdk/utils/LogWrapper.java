package com.linkus.push.sdk.utils;

import android.os.AsyncTask;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志包装器。
 * Created by jeasonyoung on 2017/3/7.
 */
public final class LogWrapper {
    private static final Object lock = new Object();
    private static final String TAG = LogWrapper.class.getSimpleName();

    private static final Map<Class<?>, LogWrapper> cache = new HashMap<>();
    private final String tag;

    private static final String def_prefix = "pushclient";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static File root = null;

    /**
     * 构造函数。
     * @param clazz
     * 类型。
     */
    private LogWrapper(final Class<?> clazz){
        this.tag = clazz.getSimpleName();
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

    /**
     * 注册日志文件根目录。
     * @param dir
     * 日志文件根目录。
     */
    public static void registerRootDir(final File dir){
        if(dir == null) return;
        synchronized (lock) {
            root = new File(dir, def_prefix);
        }
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

    public void info(final String msg){
        write("info", msg);
        Log.i(tag, msg);
    }

    public void debug(final String msg){
        write("debug", msg);
        Log.d(tag, msg);
    }

    public void warn(final String msg){
        write("warn", msg);
        Log.w(tag, msg);
    }

    public void warn(final String msg, final Throwable e){
        write("warn", msg, e);
        Log.w(tag, msg, e);
    }

    public void error(final String msg){
        write("error", msg);
        Log.e(tag, msg);
    }

    public void error(final String msg, final Throwable e){
        write("error", msg, e);
        Log.e(tag, msg, e);
    }

    private static void write(final String prefix,final String msg){
        write(prefix, msg, null);
    }

    private static void write(final String prefix,final String msg, final Throwable e){
        new AsyncTask<Void,Void,Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    //判断目录是否存在
                    if(!checkLogDirs()) return null;
                    //日志记录文件
                    final File path = createLogFile(prefix);
                    if(path == null) return null;
                    final StringBuilder sb = new StringBuilder();
                    sb.append("[").append(dateFormat.format(new Date())).append("]")
                            .append("[").append(prefix).append("]")
                            .append(msg);
                    if(e != null){
                        sb.append("=>").append(e.getMessage()).append("\n");
                        final StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        sb.append(sw.toString());
                    }
                    sb.append("\n");
                    writeLocalFile(path, sb.toString());
                } catch (Exception ex) {
                    Log.e(TAG, "日志持久化异常:" + ex.getMessage(), ex);
                }
                return null;
            }
        }.execute((Void)null);
    }

    //日志文件名前缀
    private static String LogFileNamePrefix(final String prefix){
        synchronized (lock) {
            final SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHH");
            return def_prefix + "_" + prefix + sf.format(new Date()) + ".log";
        }
    }

    //检查日志保存目录
    private static boolean checkLogDirs(){
        if(root == null) return false;
        if(!root.exists()){
            synchronized (lock) {
                final boolean result = root.mkdirs();
                Log.d(TAG, "创建日志保存目录[" + result + "]=>" + root.getAbsolutePath());
                return result;
            }
        }
        return true;
    }

    //创建日志记录文件
    private static File createLogFile(final String prefix) {
        synchronized (lock) {
            final File path = new File(root, LogFileNamePrefix(prefix));
            if (path.exists()) return path;
            try {
                final boolean result = path.createNewFile();
                Log.d(TAG, "创建日志记录文件[" + result + "]=>" + path.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "创建日志记录文件异常:" + e.getMessage());
                return null;
            }
            return path;
        }
    }

    //写入文件内容
    private static void writeLocalFile(final File path, final String message)
            throws IOException{
        synchronized (lock) {
            //初始化读写随机文件
            final RandomAccessFile randomAccessFile = new RandomAccessFile(path, "rw");
            //定位到文件尾部
            randomAccessFile.seek(randomAccessFile.length());
            //写入消息
            randomAccessFile.writeUTF(message);
        }
    }
}