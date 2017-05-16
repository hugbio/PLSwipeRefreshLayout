package com.hugbio;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;

/**
 * 作者： huangbiao
 * 时间： 2017-03-28
 */
public class NormalHeaderView implements PLHeaderView {

    private ProgressBar mProgressBarRefresh;
    private ImageView mImageViewArrow;
    private RotateAnimation mRotateAnimationFlip;
    private RotateAnimation mRotateAnimationReverseFlip;
    private View refreshView;
    private View bg;
    private int height = 0;
    private int mCurrentTargetOffsetTop = 0;
    private PLSwipeRefreshLayout.STATUS curStatus = PLSwipeRefreshLayout.STATUS.NORMAL;
    private int mProgressBarRefreshStartTop;

    @Override
    public View createHeaderView(Context context, ViewGroup viewGroup) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        refreshView = layoutInflater.inflate(R.layout.refreshview, viewGroup, false);
        mProgressBarRefresh = (ProgressBar) refreshView
                .findViewById(R.id.pbRefresh);
        mImageViewArrow = (ImageView) refreshView
                .findViewById(R.id.ivPullArrow);
        bg = refreshView.findViewById(R.id.pb_bg);
        mProgressBarRefresh.setVisibility(View.VISIBLE);
        init();
        return refreshView;
    }

    @Override
    public int getStartPosition(int headHeight) {
        return 0;
    }

    @Override
    public int getDistanceToTriggerSync(int headHeight) {
        return headHeight;
    }

    private void init() {
        mRotateAnimationFlip = new RotateAnimation(0, -180.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        mRotateAnimationFlip.setDuration(250);
        mRotateAnimationFlip.setFillAfter(true);
        mRotateAnimationFlip.setFillEnabled(true);

        mRotateAnimationReverseFlip = new RotateAnimation(-180.0f, 0,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        mRotateAnimationReverseFlip.setDuration(250);
        mRotateAnimationReverseFlip.setFillAfter(true);
        mRotateAnimationReverseFlip.setFillEnabled(true);
    }

    @Override
    public void statusChange(PLSwipeRefreshLayout.STATUS status) {
        curStatus = status;
    }

    @Override
    public void offsetChange(int mCurrentTargetOffsetTop, int mLastTargetOffsetTop) {
        if (mCurrentTargetOffsetTop >= 0) {
            this.mCurrentTargetOffsetTop = mCurrentTargetOffsetTop;
            int bgOffset = 0;
            int viewOffset = 0;
            if (mCurrentTargetOffsetTop > height) {
                bgOffset = -bg.getTop();
                viewOffset = mCurrentTargetOffsetTop - height - refreshView.getTop();
            } else {
                bgOffset = mCurrentTargetOffsetTop - height - bg.getTop();
                viewOffset = -refreshView.getTop();
            }
            bg.offsetTopAndBottom(bgOffset);
            refreshView.offsetTopAndBottom(viewOffset);
            int pTop;
            if (curStatus == PLSwipeRefreshLayout.STATUS.REFRESHING) {
                pTop = mProgressBarRefreshStartTop;
            }else{
                pTop = bg.getTop() + mProgressBarRefreshStartTop;
                if(pTop > mProgressBarRefreshStartTop){
                    pTop = mProgressBarRefreshStartTop;
                }
            }
            int pOffset  = pTop - mProgressBarRefresh.getTop();
            mProgressBarRefresh.offsetTopAndBottom(pOffset);
//            mImageViewArrow.offsetTopAndBottom(bgOffset);
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            bg.layout(left, top - bottom, right, 0);
            height = bottom - top;
            mProgressBarRefreshStartTop = mProgressBarRefresh.getTop();
            mProgressBarRefresh.offsetTopAndBottom(-height);
        }
    }
}
