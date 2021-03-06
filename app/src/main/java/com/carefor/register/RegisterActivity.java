package com.carefor.register;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.widget.Toast;

import com.carefor.BaseActivity;
import com.carefor.data.source.Repository;
import com.carefor.data.source.local.LocalRepository;
import com.carefor.mainui.R;
import com.carefor.util.ActivityUtils;


public class RegisterActivity extends BaseActivity {
    private Toast mToast;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_common);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        RegisterFragment registerFragment  = (RegisterFragment) getSupportFragmentManager().findFragmentById(R.id.contentFrame);
        if(registerFragment == null){
            registerFragment = RegisterFragment.newInstance();
            ActivityUtils.addFragmentToActivity(getSupportFragmentManager(), registerFragment, R.id.contentFrame);
        }
        RegisterPresenter registerPresenter = new RegisterPresenter(Repository.getInstance(LocalRepository.getInstance(getApplicationContext())), registerFragment);

    }
    private long clickBackTime = 0;

    @Override
    public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode== KeyEvent.KEYCODE_BACK){
            long now = System.currentTimeMillis();
            if(now - clickBackTime < 800){
                finishAll();
            }else{
                clickBackTime = now;
                showTip("再按一次返回键退出");
            }
            return true;//不执行父类点击事件
        }
        return super.onKeyDown(keyCode, event);//继续执行父类其他点击事件
    }

    private void showTip(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(text);
                mToast.show();
            }
        });
    }
}
