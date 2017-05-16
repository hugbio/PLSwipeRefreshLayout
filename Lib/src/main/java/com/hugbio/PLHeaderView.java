package com.hugbio;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * 作者： huangbiao
 * 时间： 2017-03-28
 */
public interface  PLHeaderView {

    View createHeaderView(Context context, ViewGroup viewGroup);

    int getStartPosition(int headHeight);

    int getDistanceToTriggerSync(int headHeight);

    /**
     * 刷新状态发生变化监听
     * @param status
     */
    void statusChange(PLSwipeRefreshLayout.STATUS status);

    /**
     * 内容View位置发生变化监听
     * @param mCurrentTargetOffsetTop  内容View当前的位置（相对于顶部的偏移量）
     * @param mLastTargetOffsetTop   内容View上一个位置
     */
    void offsetChange(int mCurrentTargetOffsetTop,int mLastTargetOffsetTop);

    void onLayout(boolean changed, int left, int top, int right, int bottom);
}
