package com.carefor.about;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.carefor.BaseActivity;
import com.carefor.mainui.R;

public class AboutActivity extends BaseActivity {

    private Toolbar mToolbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle("关于");
        //为activity窗口设置活动栏
        setSupportActionBar(mToolbar);
        final ActionBar actionBar = getSupportActionBar();
        //设置返回图标
        actionBar.setHomeAsUpIndicator(0);
        actionBar.setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }
}
