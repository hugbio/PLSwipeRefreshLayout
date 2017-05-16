package com.hugbio.plswiperefreshlayout;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.hugbio.PLSwipeRefreshLayout;

public class MainActivity extends Activity implements
		PLSwipeRefreshLayout.OnRefreshListener {
	private PLSwipeRefreshLayout mSwipeLayout;
	private WebView mPage;
    private View viewById;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mSwipeLayout = (PLSwipeRefreshLayout) findViewById(R.id.swipe_container);
		mPage = (WebView) findViewById(R.id.page);
		mSwipeLayout.setRefreshEnabled(true);
		mSwipeLayout.setLoadEnabled(false);

		WebSettings webSettings = mPage.getSettings();
		webSettings.setBuiltInZoomControls(false);
		webSettings.setSupportZoom(false);
		webSettings.setJavaScriptEnabled(true);

		mPage.setWebViewClient(new WebViewClient());

		mPage.loadUrl("http://wap.qq.com");

		mSwipeLayout.setOnRefreshListener(this);
    }

	@Override
	public void onRefresh() {

		new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // 停止刷新
                mSwipeLayout.stopRefresh();
                mPage.loadUrl("http://www.baidu.com");
            }
        }, 3000); // 3秒后发送消息，停止刷新
	}
	
	@Override
	public void onNormal() {

	}
	
	@Override
	public void onLoose() {

	}
}
