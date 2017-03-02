package com.hugbio.plswiperefreshlayout;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.hugbio.PLSwipeRefreshLayout;

public class MainActivity extends Activity implements
		PLSwipeRefreshLayout.OnRefreshListener {
	private PLSwipeRefreshLayout mSwipeLayout;
	private WebView mPage;
	private TextView mHint;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mSwipeLayout = (PLSwipeRefreshLayout) findViewById(R.id.swipe_container);
		mPage = (WebView) findViewById(R.id.page);
		mHint = (TextView) findViewById(R.id.hint);
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
		mHint.setText("正在刷新，请等待");
		
		new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // 停止刷新
                mSwipeLayout.setRefreshing(false);
                mSwipeLayout.stopRefresh();
                mHint.setText("下拉刷新");
                mPage.loadUrl("http://wap.163.com");
            }
        }, 3000); // 3秒后发送消息，停止刷新
	}
	
	@Override
	public void onNormal() {
		mHint.setText("下拉刷新");
	}
	
	@Override
	public void onLoose() {
		mHint.setText("松手刷新");
	}
}
