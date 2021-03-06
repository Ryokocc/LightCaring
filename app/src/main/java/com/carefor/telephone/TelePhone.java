package com.carefor.telephone;


import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.carefor.connect.Connector;
import com.carefor.connect.MessageManager;
import com.carefor.data.entity.DeviceModel;
import com.carefor.data.source.cache.CacheRepository;
import com.carefor.util.Tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by baige on 2017/10/30.
 */

public class TelePhone implements SpeexTalkRecorder.OnRecorderListener, SpeexTalkPlayer.OnPlayerListener, TelePhoneAPI {

    private static final String TAG = TelePhone.class.getCanonicalName();

    private static TelePhone INSTANCE = null;

    private SpeexTalkRecorder recorder;

    private SpeexTalkPlayer player;

    private OnTelePhoneListener mListener;

    private int mStatus;

    private String mTalkWith;

    private boolean isConnectServer;

    private static ExecutorService fixedThreadPool = null;

    private long mDelayTime;

    private long mPlayTime;

    private ByteBuffer voiceBuf;

    private static MediaPlayer mMediaPlayer;

    private Context mContext;

    private TelePhone() {
        mStatus = Status.LEISURE;
        isConnectServer = false;
        voiceBuf = ByteBuffer.allocate(20 * 10);
        mMediaPlayer = new MediaPlayer();
        fixedThreadPool = Executors.newFixedThreadPool(5);//创建最多能并发运行5个线程的线程池

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mMediaPlayer.start();
            }
        });
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mMediaPlayer.start();
            }
        });
    }

    public static TelePhone getInstance() {
        if (INSTANCE == null) {
            synchronized (TelePhone.class) { //对获取实例的方法进行同步
                if (INSTANCE == null) {
                    INSTANCE = new TelePhone();
                }
            }
        }
        return INSTANCE;
    }

    public void init(Context context){
        mContext = checkNotNull(context);
    }

    public void setOnTelePhoneListener(OnTelePhoneListener listener) {
        this.mListener = listener;
    }

    public void play(byte[] recordData) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(recordData);
        byte[] voice = new byte[20];
        if (player != null) {
            mPlayTime = System.currentTimeMillis();
            while (byteBuffer.remaining() >= voice.length) {
                byteBuffer.get(voice);
                synchronized (TelePhone.class) {
                    if (player != null) {
                        player.play(voice);
                    }else {
                        return;
                    }
                }
            }
        }
        if(mListener != null){
           mListener.showDelay(mDelayTime);
        }
    }

    public void ring(Context context, Uri uri){
        mMediaPlayer.reset();
        try {
            mMediaPlayer.setDataSource(context, uri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            ring();
        }
    }
    public void ring(){
        mMediaPlayer.reset();
        try {
            if(mContext == null){
                new IllegalStateException("Telephone is not init");
            }
            AssetManager assetManager = mContext.getAssets();
            AssetFileDescriptor fileDescriptor = assetManager.openFd("MI.ogg");
            mMediaPlayer.setDataSource(fileDescriptor.getFileDescriptor(), fileDescriptor.getStartOffset(), fileDescriptor.getLength());
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void stopRing(){
        if(mMediaPlayer != null && mMediaPlayer.isPlaying()){
            mMediaPlayer.stop();
        }
    }
    public int getStatus() {
        return mStatus;
    }

    private void setStatus(int status) {
        //TODO 进行严格的状态转换
        mStatus = status;
    }

    public long getDelayTime() {
        return mDelayTime;
    }

    public void setDelayTime(long mDelayTime) {
        this.mDelayTime = mDelayTime;
    }

    @Override
    public void exceptionCaught(Throwable cause) {
        if (mListener != null) {
            mListener.exceptionCaught(cause);
        }
    }

    @Override
    public void handleRecordData(byte[] recordData) {
//        if (mStatus == Status.IS_WORKING && mConnectedByUDP != null) {
//            mConnectedByUDP.send(ConnectedByUDP.MessageTag.VOICE, recordData);
//        }
        Log.d(TAG, "录音数据" + recordData.length);
        Connector connector = Connector.getInstance();
        CacheRepository cacheRepository = CacheRepository.getInstance();
        if(System.currentTimeMillis() - mPlayTime >= 10 * 1000){
            //掉线了
            showTip("对方已经掉线");
            if(mListener != null){
                mListener.exceptionCaught(new IOException());
            }
            stop();
        }
        if(voiceBuf.remaining() == recordData.length){
            voiceBuf.put(recordData);
            byte[] buf;
            if (cacheRepository.isP2PConnectSuccess()) {
                buf = MessageManager.udpData(true, MessageManager.TYPE_VOICE, MessageManager.TAG_VOICE, voiceBuf.array());
                if (buf != null) {
                    long startTime = System.currentTimeMillis();
                    connector.sendMessage(cacheRepository.getP2PIp(), cacheRepository.getP2PPort(), buf);
                    Log.d(TAG, "UDP 数据发送时间："+String.valueOf(System.currentTimeMillis() - startTime)+", 数据长度："+buf.length );
                } else {
                    Log.d(TAG, "语音数据打包失败");
                }
            } else {
                buf = MessageManager.udpServerTranf(true, cacheRepository.getDeviceId(),
                        cacheRepository.getTalkWith(),
                        MessageManager.TYPE_TRANF,
                        MessageManager.TAG_VOICE,
                        voiceBuf.array());
                if (buf != null) {
                    connector.afxSendMessage(cacheRepository.getServerIp(), cacheRepository.getServerUdpPort(), buf);
                } else {
                    Log.d(TAG, "语音数据打包失败");
                }
            }

            voiceBuf.position(0);
        }else if(voiceBuf.remaining() < recordData.length){
            int pos = voiceBuf.position();
            byte[] voice = new byte[pos];
            voiceBuf.position(0);
            voiceBuf.get(voice);
            byte[] buf;
            if (cacheRepository.isP2PConnectSuccess()) {
                buf = MessageManager.udpData(true, MessageManager.TYPE_VOICE, MessageManager.TAG_VOICE, voice);
                if (buf != null) {
                    long startTime = System.currentTimeMillis();
                    connector.sendMessage(cacheRepository.getP2PIp(), cacheRepository.getP2PPort(), buf);
                    Log.d(TAG, "UDP 数据发送时间："+String.valueOf(System.currentTimeMillis() - startTime)+", 数据长度："+buf.length );
                } else {
                    Log.d(TAG, "语音数据打包失败");
                }
            } else {
                buf = MessageManager.udpServerTranf(true, cacheRepository.getDeviceId(),
                        cacheRepository.getTalkWith(),
                        MessageManager.TYPE_TRANF,
                        MessageManager.TAG_VOICE,
                        voice);
                if (buf != null) {

                    connector.afxSendMessage(cacheRepository.getServerIp(), cacheRepository.getServerUdpPort(), buf);

                } else {
                    Log.d(TAG, "语音数据打包失败");
                }
            }
            voiceBuf.position(0);
            voiceBuf.put(recordData);
        }else {
            voiceBuf.put(recordData);
        }
      //  buf = null;//手动释放，数据量太大了
    }


    private void afxCheckUdpConnector() {
        fixedThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                checkUdpConnector();
            }
        });
    }

    private void checkUdpConnector() {
        Connector connector = Connector.getInstance();
        CacheRepository cacheRepository = CacheRepository.getInstance();
        byte[] buf = MessageManager.udpData(MessageManager.TYPE_CHECK, MessageManager.TAG_DEVICE_ID, cacheRepository.getDeviceId().getBytes());
        if (buf != null) {
            connector.sendMessage(cacheRepository.getServerIp(), cacheRepository.getServerUdpPort(), buf);
            //TODO 检测NAT类型
            connector.sendMessage("120.78.148.180", 12059, buf);
            showLog("UDP 检查网络状态");
        }else {
            showLog("UDP 检查网络数据包发送失败");
        }
    }

    @Override
    public void afxBeCall(final String deviceId, final BaseCallBack callBack) {
        fixedThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                beCall(deviceId, callBack);
            }
        });
    }

    @Override
    public void beCall(String deviceId, BaseCallBack callBack) {
        checkNotNull(deviceId);
        checkNotNull(callBack);
        if (mStatus == Status.LEISURE) {
            mTalkWith = deviceId;
            setStatus(Status.CALLED);
            String ringUri = CacheRepository.getInstance().getRingUri();
            if(!Tools.isEmpty(ringUri)){
                ring(mContext, Uri.parse(ringUri));
            }else{
                ring();
            }
            checkUdpConnector();
        } else {
            showLog("错误状态, 当前" + mStatus);
            callBack.isBusy();
        }
    }

    @Override
    public void afxCallTo(final String deviceId, final BaseCallBack callBack) {
        fixedThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                callTo(deviceId, callBack);
            }
        });
    }

    @Override
    public synchronized void callTo(String deviceId, BaseCallBack callBack) {
        checkNotNull(deviceId);
        checkNotNull(callBack);
        if (mStatus == Status.LEISURE) {
            mTalkWith = deviceId;
            setStatus(Status.CALLING);
            checkUdpConnector();
        } else {
            showLog("错误状态, 当前" + mStatus);
            callBack.isBusy();
        }
    }

    @Override
    public void checkUdpSuccess() {
        if (mStatus == Status.CALLING) {
            //TODO 呼叫用户
            if (mTalkWith == null || mTalkWith.isEmpty()) {
                showTip("未知呼叫的用户");
            } else {
                showLog("TCP 发送呼叫信息");
                String msg = MessageManager.callTo(mTalkWith);
                Connector connector = Connector.getInstance();
                connector.sendMessage(msg);
            }
        } else if (mStatus == Status.CALLED) {
            //反馈用户的呼叫
            showLog("TCP 发送呼叫反馈信息");
            String msg = MessageManager.replyCallTo(mTalkWith);
            Connector connector = Connector.getInstance();
            connector.sendMessage(msg);
            //尝试p2p连接
            DeviceModel deviceModel = CacheRepository.getInstance().getTalkWithDevice();
            showLog("UDP 尝试p2p连接");
            showLog("对方设备ID"+deviceModel.getDeviceidId());
            showLog("远程地址：" + deviceModel.getRemoteIp() + ":" + deviceModel.getRemoteUdpPort());
            showLog("本地地址：" + deviceModel.getLocalIp() + ":" + deviceModel.getLocalUdpPort());
            connector.tryP2PConnect(CacheRepository.getInstance().getTalkWithDevice());
        }
    }

    @Override
    public void onHangUp(BaseCallBack callBack) {
        checkNotNull(callBack);
        stopRing();
        stop();
        Connector.getInstance().afxSendMessage(MessageManager.onHangUp(mTalkWith));
    }

    @Override
    public void onPickUp(BaseCallBack callBack) {
        checkNotNull(callBack);
        stopRing();
        canTalk();
        Connector.getInstance().afxSendMessage(MessageManager.onPickUp(mTalkWith));
    }

    @Override
    public void canTalk() {
        mPlayTime = System.currentTimeMillis();
        if (recorder == null) {
            synchronized (TelePhone.class) {
                if (recorder == null) {
                    recorder = new SpeexTalkRecorder(TelePhone.this); // 创建录音对象
                    recorder.start(); // 开始录音
                    setStatus(Status.BUSY);
                }
            }
        }
        if (player == null) {
            synchronized (TelePhone.class) {
                if (player == null) {
                    player = new SpeexTalkPlayer();         // 创建播放器对象
                    player.setOnPlayerListener(this);
                    setStatus(Status.BUSY);
                }
            }
        }
       showLog("开始通话");
    }
    public void stop() {
        synchronized (TelePhone.class) {
            stopRing();
            if (mStatus == Status.BUSY) {
                if (recorder != null) {
                    recorder.stop();    // 停止录音
                }
                if (player != null) {
                    player.stop();      // 停止播放
                }
                recorder = null;
                player = null;
            }
            setStatus(Status.LEISURE);
            mDelayTime = 0;
            showLog("停止通话");
            CacheRepository.getInstance().setTalkWithDevice(null);
            CacheRepository.getInstance().setP2PConnectSuccess(false);
        }
    }


    public void showLog(String text) {
        Log.d(TAG, text);
        if (mListener != null) {
            mListener.showLog(text);
        }
    }

    @Override
    public void showTip(String text) {
        if (mListener != null) {
            mListener.showTip(text);
        }
    }

    public interface OnTelePhoneListener {

        void showTip(String text);

        void showLog(String text);

        void showDelay(long delay);

        void exceptionCaught(Throwable cause);
    }


    public class Status {
        public static final int LEISURE = 0;
        public static final int CALLING = 1;
        public static final int CALLED = 2;
        public static final int BUSY = 3;
        public static final int ERROR = 5;
    }
}
