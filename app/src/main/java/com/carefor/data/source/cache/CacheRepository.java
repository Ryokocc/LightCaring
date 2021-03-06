package com.carefor.data.source.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.baidu.mapapi.model.LatLng;
import com.carefor.connect.Connector;
import com.carefor.data.entity.DeviceModel;
import com.carefor.data.entity.Location;
import com.carefor.data.entity.User;
import com.carefor.membermanage.MemberInfo;
import com.carefor.setting.SettingActivity;
import com.carefor.util.JPushTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.jpush.android.api.JPushInterface;

/**
 * Created by baige on 2017/12/21.
 */

public class CacheRepository {

    private static final String TAG = CacheRepository.class.getCanonicalName();

    private static CacheRepository INSTANCE = null;

    public static boolean isNeedOpenGuidepage = true;

    private String mDeviceId = "1104a89792acc0236ac"; //极光推送的设备ID

    private String mServerIp = null;

    private int mServerPort = 12056;

    private int mServerUdpPort = 12059;

    private String mTalkWith = ""; //测试要打给谁
    private DeviceModel mTalkWithDevice = null;

    //紧急联系人 TODO 取消上面的mTalkWith
    private User mSelectUser = null; //应至少保证id、name、tel 、device_id准确

    private boolean mP2PConnectSuccess = false;
    private String mP2PIp = "";
    private int mP2PPort = 0;


    private String mLocalIp = null;

    private int mLocalPort = 0;

    private String mRemoteIp = null;

    private int mRemotePort = 0;

    private int mRemoteUdpPort = 0;

    private int mLocalUdpPort = 0;


    private boolean mIsLogin = false;
    private User mLoginUser = null;

    //闹铃URI
    private String mRingUri;


    private Map<String, DeviceModel> mCacheddevices;

    //监护人或被监护人列表
    private List<MemberInfo> mMemberInfoList;



    //定位相关数据
    private float mCurrentAccracy; //精度(不需要保存)
    private double mCurrentLat = 0.0;//维度
    private double mCurrentLon = 0.0; //经度

    private List<Location> mDesLocation = new ArrayList<>(); //服务器返回的位置信息，最多保存500个
    private List<LatLng> mSelPoints = new ArrayList<>(); //目标轨迹
    private List<LatLng> mMyPoints = new ArrayList<>();//自己的轨迹




    private CacheRepository() {
        mCacheddevices = Collections.synchronizedMap(new LinkedHashMap<String, DeviceModel>());
    }

    public static CacheRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (CacheRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CacheRepository();
                }
            }
        }
        return INSTANCE;
    }

    public void clearConfig(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
        readConfig(context);
    }


    public void readConfig(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        String ip = preferences.getString(SettingActivity.KEY_PHONE_SERVER_IP, SettingActivity.DEFAULT_PHONE_SERVER_IP);

        mServerPort = Integer.valueOf(preferences.getString(SettingActivity.KEY_TCP_PORT, SettingActivity.DEFAULT_TCP_PORT));
        mServerUdpPort = Integer.valueOf(preferences.getString(SettingActivity.KEY_UDP_PORT, SettingActivity.DEFAULT_UDP_PORT));
        if(!JPushTools.isEmpty(ip) && !ip.equals(mServerIp)){
            mServerIp = ip;
            Connector.getInstance().afxConnectServer();
        }
        if (mSelectUser == null) {
            String tel = preferences.getString(SettingActivity.KEY_PHONE, "");
            String em_name = preferences.getString("emergency_name", "");
            mTalkWith = preferences.getString("emergency_device_id", "");
            mSelectUser = new User();
            mSelectUser.setDeviceId(mTalkWith);
            mSelectUser.setName(em_name);
            mSelectUser.setTel(tel);
            if(preferences.contains("select_id")){
                int id = preferences.getInt("select_id", 0);
                mSelectUser.setUid(id);
            }
        }
        isNeedOpenGuidepage = preferences.getBoolean("guide_page", true);
        String name = preferences.getString(SettingActivity.KEY_NAME, "");
        String pas = preferences.getString("password", "");
        mDeviceId = JPushInterface.getRegistrationID(context);
        if (isLogin() && mLoginUser != null) {
            mLoginUser.setName(name);
            mLoginUser.setPsw(pas);
        } else {//覆盖其他多有的信息
            mLoginUser = new User(name, pas);
        }
        mLoginUser.setDeviceId(mDeviceId);
        mRingUri = preferences.getString(SettingActivity.KEY_ALERT, "");
        //定位
        mCurrentLat = preferences.getFloat("current_lat", 116.40399f);
        mCurrentLon = preferences.getFloat("current_lon", 39.915087f);
        Log.d("save", "读取配置" + mLoginUser);
    }

    public void saveConfig(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        if (mLoginUser != null) {
            editor.putString(SettingActivity.KEY_NAME, mLoginUser.getName());
            editor.putString("password", mLoginUser.getPsw());
        }
        editor.putBoolean("guide_page", isNeedOpenGuidepage);
        //不能在这里改
        // editor.putString(SettingActivity.KEY_PHONE_SERVER_IP, mServerIp);
        // editor.putString(SettingActivity.KEY_TCP_PORT, ""+mServerPort);
        // editor.putString(SettingActivity.KEY_UDP_PORT, ""+mServerUdpPort);
        //后面换成紧急联系人
        editor.putString(SettingActivity.KEY_PHONE, mTalkWith);
        if (mSelectUser != null) {
            editor.putInt("select_id", mSelectUser.getUid());
            editor.putString(SettingActivity.KEY_PHONE, mSelectUser.getTel());
            editor.putString("emergency_name", mSelectUser.getName());
            editor.putString("emergency_device_id", mSelectUser.getDeviceId());
        }

        //定位
        editor.putFloat("current_lat", (float) mCurrentLat);
        editor.putFloat("current_lon", (float) mCurrentLon);

        editor.commit();
        Log.d("save", "保存配置" + mLoginUser);
        Log.d("save", "紧急联系人" + mSelectUser);
    }

    public String getServerIp() {
        return mServerIp;
    }

    public void setServerIp(String mServerIp) {
        this.mServerIp = mServerIp;
    }

    public int getServerPort() {
        return mServerPort;
    }

    public void setServerPort(int mServerPort) {
        this.mServerPort = mServerPort;
    }

    public int getServerUdpPort() {
        return mServerUdpPort;
    }

    public void setServerUdpPort(int mServerUdpPort) {
        this.mServerUdpPort = mServerUdpPort;
    }

    public String getLocalIp() {
        return mLocalIp;
    }

    public void setLocalIp(String mLocalIp) {
        this.mLocalIp = mLocalIp;
    }

    public int getLocalPort() {
        return mLocalPort;
    }

    public void setLocalPort(int mLocalPort) {
        this.mLocalPort = mLocalPort;
    }

    public int getLocalUdpPort() {
        return mLocalUdpPort;
    }

    public void setLocalUdpPort(int mLocalUdpPort) {
        this.mLocalUdpPort = mLocalUdpPort;
    }

    public String getRemoteIp() {
        return mRemoteIp;
    }

    public void setRemoteIp(String mRemoteIp) {
        this.mRemoteIp = mRemoteIp;
    }

    public int getRemotePort() {
        return mRemotePort;
    }

    public void setRemotePort(int mRemotePort) {
        this.mRemotePort = mRemotePort;
    }

    public int getRemoteUdpPort() {
        return mRemoteUdpPort;
    }

    public void setRemoteUdpPort(int mRemoteUdpPort) {
        this.mRemoteUdpPort = mRemoteUdpPort;
    }

    public boolean isLogin() {
        return mIsLogin;
    }

    public void setLogin(boolean mIsLogin) {
        this.mIsLogin = mIsLogin;
    }

    public String getDeviceId() {
        return mDeviceId;
    }

    public String getTalkWith() {
        return mTalkWith;
    }

    public void setTalkWith(String mTalkWith) {
        this.mTalkWith = mTalkWith;
    }

    public boolean isP2PConnectSuccess() {
        return mP2PConnectSuccess;
    }

    public void setP2PConnectSuccess(boolean mP2PConnectSuccess) {
        this.mP2PConnectSuccess = mP2PConnectSuccess;
    }

    public String getP2PIp() {
        return mP2PIp;
    }

    public void setP2PIp(String mP2PIp) {
        this.mP2PIp = mP2PIp;
    }

    public int getP2PPort() {
        return mP2PPort;
    }

    public void setP2PPort(int mP2PPort) {
        this.mP2PPort = mP2PPort;
    }

    public DeviceModel getTalkWithDevice() {
        return mTalkWithDevice;
    }

    public String getRingUri() {
        return mRingUri;
    }

    public void setTalkWithDevice(DeviceModel mTalkWithDevice) {
        this.mTalkWithDevice = mTalkWithDevice;
    }

    //监护人或被监护人列表 TODO 删除
    public List<MemberInfo> getmMemberInfoList() {
        return mMemberInfoList;
    }

    public void setmMemberInfoList(List<MemberInfo> mMemberInfoList) {
        this.mMemberInfoList = mMemberInfoList;
    }

    public User getSelectUser() {
        return mSelectUser;
    }

    public void setSelectUser(User mEmergencyUser) {
        this.mSelectUser = mEmergencyUser;
    }


    /*
    * ****************************** 定位相关
    * */
    public float getCurrentAccracy() {
        return mCurrentAccracy;
    }

    public void setCurrentAccracy(float accracy) {
        this.mCurrentAccracy = accracy;
    }

    public double getCurrentLat() {
        return mCurrentLat;
    }

    public void setCurrentLat(double lat) {
        this.mCurrentLat = lat;
    }

    public double getCurrentLon() {
        return mCurrentLon;
    }

    public void setCurrentLon(double lon) {
        this.mCurrentLon = lon;
    }

    public List<Location> getDesLocation() {
        return mDesLocation;
    }

    public void setDesLocation(List<Location> mDesLocation) {
        this.mDesLocation = mDesLocation;
    }

    public List<LatLng> getSelPoints() {
        return mSelPoints;
    }

    public void setSelPoints(List<LatLng> mSelPoints) {
        this.mSelPoints = mSelPoints;
    }

    public List<LatLng> getMyPoints() {
        return mMyPoints;
    }

    public void setMyPoints(List<LatLng> mMyPoints) {
        this.mMyPoints = mMyPoints;
    }

    public User who() {
        return mLoginUser;
    }

    /**
     * 登录成功之后不能覆盖该对象，只能单独设置某些属性
     *
     * @param user
     */
    public void setYouself(User user) {
        this.mLoginUser = user;
    }


}
