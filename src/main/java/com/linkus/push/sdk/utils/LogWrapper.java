package com.linkus.push.sdk.utils;

import android.os.AsyncTask;
import android.util.Log;
import com.linkus.push.sdk.data.IAccessConfig;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志包装器。
 * Created by jeasonyoung on 2017/3/7.
 */
public final class LogWrapper {
    private static final Object lock = new Object();
    private static final String TAG = LogWrapper.class.getSimpleName();

    private static final Map<Class<?>, LogWrapper> cache = new HashMap<>();
    private final String tag;

    private static final String def_prefix = "pushSDK";
    private static final SimpleDateFormat sdf_content = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat sdf_file = new SimpleDateFormat("yyyyMMddHH");

    private static File root = null;

    private static final int uploader_log_file_interval = 300000;
    private final AtomicLong lastUploaderTime = new AtomicLong(0L);//最后一次上传日志时间

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
        Log.i(tag, msg);
        write("info", msg);
    }

    public void debug(final String msg){
        Log.d(tag, msg);
        write("debug", msg);
    }

    public void warn(final String msg){
        Log.w(tag, msg);
        write("warn", msg);
    }

    public void warn(final String msg, final Throwable e){
        Log.w(tag, msg, e);
        write("warn", msg, e);
    }

    public void error(final String msg){
        Log.e(tag, msg);
        write("error", msg);
    }

    public void error(final String msg, final Throwable e){
        Log.e(tag, msg, e);
        write("error", msg, e);
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
                    sb.append("[").append(sdf_content.format(new Date())).append("]")
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
            return def_prefix + "_" + prefix + "_" + sdf_file.format(new Date()) + ".log";
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
        if(root == null) return null;
        synchronized (lock) {
            final File path = new File(root, LogFileNamePrefix(prefix));
            if (path.exists()) return path;
            try {
                final boolean result = path.createNewFile();
                if(result){
                    String initMessage = "-------------------------------------\n push sdk ["+ prefix +"] log \n-------------------------------------\n";
                    writeLocalFile(path, initMessage);
                }
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
            RandomAccessFile randomAccessFile = null;
            try {
                //初始化读写随机文件
                randomAccessFile = new RandomAccessFile(path, "rw");
                //定位到文件尾部
                randomAccessFile.seek(randomAccessFile.length());
                //写入消息
                randomAccessFile.writeUTF("\n\n" + message);
            }catch (Exception e){
                Log.e(TAG,"writeLocalFile[path:"+path+",message:"+ message +"]-写入文件异常:\n" + e.getMessage());
            }finally {
                if(randomAccessFile != null){
                    //关闭文件
                    randomAccessFile.close();
                }
            }
        }
    }

    private static final AtomicBoolean atomIsUploadFiles = new AtomicBoolean(false);
    /**
     * 上传日志文件。
     */
    public void uploadLogFiles(final UploaderLogAccessListener listener){
        //判断是否已运行
        if(atomIsUploadFiles.get()) return;
        //获取当前时间戳
        final long current = System.currentTimeMillis(), diff;
        if((diff = (current - lastUploaderTime.get())) < uploader_log_file_interval){
            Log.d(TAG,"uploadLogFiles-日志上传未到时间周期["+ diff +" < "+ uploader_log_file_interval +"]!");
            return;
        }
        //更新时间戳
        lastUploaderTime.set(current);
        //开始执行日志文件上传
        new AsyncTask<Void,Void,Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                //重置开始上传标示
                atomIsUploadFiles.set(true);
                try {
                    if(root != null && root.exists()) {//检查日志存储根目录是否存在
                        final String current = sdf_file.format(new Date());
                        //搜索目录下的日志文件
                        final File[] lists = root.listFiles();
                        if(lists != null && lists.length > 0){
                            final List<File> deleteFiles = new ArrayList<>();
                            //加载配置
                            if(listener != null) {
                                final IAccessConfig config = listener.loadAccessConfig();
                                if(config != null) {
                                    //上传文件
                                    for (File file : lists) {
                                        if (!file.exists() || file.getName().contains(current)) continue;
                                        try {
                                            if (LogUploadUtils.uploader(config, file)) {
                                                deleteFiles.add(file);
                                            }
                                        } catch (Exception ex) {
                                            Log.e(TAG, "uploadLogFiles-上传文件[" + file.getAbsolutePath() + "]异常:" + ex.getMessage());
                                        }
                                    }
                                }
                            }
                            //删除已上传成功的文件
                            if(deleteFiles.size() > 0){
                                for (File file : deleteFiles){
                                    try {
                                        if (!file.exists()) continue;
                                        boolean result = file.delete();
                                        Log.d(TAG,"uploadLogFiles-删除日志文件["+ result +"]=>" + file.getAbsolutePath());
                                    }catch (Exception ex){
                                        Log.e(TAG, "uploadLogFiles-删除日志文件["+ file.getAbsolutePath() +"]失败-异常:" + ex.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }catch (Exception e){
                    Log.e(TAG,"uploadLogFiles-日志文件上传异常[root:"+ root +"]:" + e.getMessage());
                }finally {
                    //重置开始上传标示
                    atomIsUploadFiles.set(false);
                }
                return null;
            }
        }.execute((Void)null);
    }

    /**
     * 日志上传加载访问配置监听器。
     */
    public interface UploaderLogAccessListener{
        /**
         * 加载访问配置。
         * @return 访问配置
         */
        IAccessConfig loadAccessConfig();
    }
}