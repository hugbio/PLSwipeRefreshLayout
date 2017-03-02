/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hugbio;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;

/**
 * The SwipeRefreshLayout should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that
 * instantiates this view should add an OnRefreshListener to be notified
 * whenever the swipe to refresh gesture is completed. The SwipeRefreshLayout
 * will notify the listener each and every time the gesture is completed again;
 * the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should
 * not be a refresh, it must call setRefreshing(false) to cancel any visual
 * indication of a refresh. If an activity wishes to show just the progress
 * animation, it should call setRefreshing(true). To disable the gesture and
 * progress animation, call setEnabled(false) on the view.
 * <p>
 * <p>
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu item must be provided to allow
 * refresh of the content wherever this gesture is used.
 * </p>
 */
public class PLSwipeRefreshLayout extends ViewGroup {
    private static final String LOG_TAG = PLSwipeRefreshLayout.class
            .getSimpleName();

    private static final long RETURN_TO_ORIGINAL_POSITION_TIMEOUT = 300;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final float MAX_SWIPE_DISTANCE_FACTOR = .6f;
    private static final int REFRESH_TRIGGER_DISTANCE = 120;
    private static final int INVALID_POINTER = -1;

    private View mTarget; // the content that gets pulled down
    private int mOriginalOffsetTop;  //内容控件的初始位置
    private OnRefreshListener mListener;
    private int mFrom;  //动画用到的开始值
    private boolean mRefreshing = false;  //标记是否正在刷新
    private boolean mLoading = false;  //标记是否正在加载
    private int mTouchSlop;  //手势滑动的最小有效值
    private float mDistanceToTriggerSync = -1;  //松开手指可以刷新的最低滑动距离
    private int mMediumAnimationDuration;
    private int mCurrentTargetOffsetTop;   //当前内容控件的位置

    private float mInitialMotionY;
    private float mLastMotionY;
    private boolean mIsBeingDragged;  //标记开始下拉
    private boolean mIsBeingLoad;  //标记开始上拉
    private int mActivePointerId = INVALID_POINTER;

    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private boolean mReturningToStart;  //标记是否正在返回指定位置的动画（包括内容控件返回初始位置、头部控件返回顶部位置等）
    private final DecelerateInterpolator mDecelerateInterpolator;
    private static final int[] LAYOUT_ATTRS = new int[]{android.R.attr.enabled};

    private View mHeaderView;   //头部控件，后面下拉刷新的头部动画可以通过设置自定义的Drawable来实现
    private int mHeaderHeight;

    //底部View还没实现。可以参照头部View来实现

    private STATUS mStatus = STATUS.NORMAL;
    private boolean mDisable; // 用来控制控件是否允许滚动

    private enum STATUS {
        NORMAL,   //默认状态
        LOOSENREFRESH,  //可以松开刷新状态
        LOOSENLOAD,   //可以松开加载状态
        REFRESHING,  //正在刷新状态
        LOADING  //正在加载状态
    }

    private boolean isRefreshEnabled = true;
    private boolean isLoadEnabled = true;

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int targetTop = 0;
            if (mFrom != mOriginalOffsetTop) {
                targetTop = (mFrom + (int) ((mOriginalOffsetTop - mFrom) * interpolatedTime));
            }

            int offset = targetTop - mTarget.getTop();
            final int currentTop = mTarget.getTop();

            if (offset + currentTop < -200) {  //这里200需要修改，应改成底部View的高度（底部View还没实现。可以参照头部View来实现）
                offset = -200 - currentTop;
            }
            setTargetOffsetTopAndBottom(offset);
        }
    };

    private final Animation mAnimateToHeaderPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int targetTop = 0;
            if (mFrom != mHeaderHeight) {
                targetTop = (mFrom + (int) ((mHeaderHeight - mFrom) * interpolatedTime));
            }

            int offset = targetTop - mTarget.getTop();
            final int currentTop = mTarget.getTop();

            if (offset + currentTop < 0) {
                offset = 0 - currentTop;
            }
            setTargetOffsetTopAndBottom(offset);
        }
    };

    private final AnimationListener mReturnToStartPositionListener = new BaseAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Once the target content has returned to its start position, reset
            // the target offset to 0
            mCurrentTargetOffsetTop = 0;
            mStatus = STATUS.NORMAL;
            mDisable = false;
        }
    };

    private final AnimationListener mReturnToHeaderPositionListener = new BaseAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Once the target content has returned to its start position, reset
            // the target offset to 0
            mCurrentTargetOffsetTop = mHeaderHeight;
            mStatus = STATUS.REFRESHING;
        }
    };

    private final Runnable mReturnToStartPosition = new Runnable() {
        @Override
        public void run() {
            mReturningToStart = true;
            animateOffsetToStartPosition(mCurrentTargetOffsetTop
                    + getPaddingTop(), mReturnToStartPositionListener);
        }
    };

    private final Runnable mReturnToHeaderPosition = new Runnable() {
        @Override
        public void run() {
            mReturningToStart = true;
            animateOffsetToHeaderPosition(mCurrentTargetOffsetTop
                    + getPaddingTop(), mReturnToHeaderPositionListener);
        }
    };

    // Cancel the refresh gesture and animate everything back to its original
    // state.
    private final Runnable mCancel = new Runnable() {
        @Override
        public void run() {
            mReturningToStart = true;
            animateOffsetToStartPosition(mCurrentTargetOffsetTop
                    + getPaddingTop(), mReturnToStartPositionListener);
        }
    };

    /**
     * Simple constructor to use when creating a SwipeRefreshLayout from code.
     *
     * @param context
     */
    public PLSwipeRefreshLayout(Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     *
     * @param context
     * @param attrs
     */
    public PLSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mMediumAnimationDuration = getResources().getInteger(
                android.R.integer.config_mediumAnimTime);

        mDecelerateInterpolator = new DecelerateInterpolator(
                DECELERATE_INTERPOLATION_FACTOR);

        final TypedArray a = context
                .obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        a.recycle();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(mCancel);
        removeCallbacks(mReturnToStartPosition);
        removeCallbacks(mReturnToHeaderPosition);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mReturnToStartPosition);
        removeCallbacks(mCancel);
        removeCallbacks(mReturnToHeaderPosition);
    }

    private void animateOffsetToStartPosition(int from,
                                              AnimationListener listener) {
        mFrom = from;
        mAnimateToStartPosition.reset();
        mAnimateToStartPosition.setDuration(mMediumAnimationDuration);
        mAnimateToStartPosition.setAnimationListener(listener);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        mTarget.startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToHeaderPosition(int from,
                                               AnimationListener listener) {
        mFrom = from;
        mAnimateToHeaderPosition.reset();
        mAnimateToHeaderPosition.setDuration(mMediumAnimationDuration);
        mAnimateToHeaderPosition.setAnimationListener(listener);
        mAnimateToHeaderPosition.setInterpolator(mDecelerateInterpolator);
        mTarget.startAnimation(mAnimateToHeaderPosition);
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        isRefreshEnabled = refreshEnabled;
    }

    public void setLoadEnabled(boolean loadEnabled) {
        isLoadEnabled = loadEnabled;
    }

    /**
     * 通知控件刷新状态已更改。 当通过滑动手势触发刷新时，不要调用此方法。
     *
     * @param refreshing Whether or not the view should show refresh progress.
     */
    public void setRefreshing(boolean refreshing) {
        if (mRefreshing != refreshing) {
            ensureTarget();
            mRefreshing = refreshing;
        }
        if(mRefreshing){
            mReturnToHeaderPosition.run();
        }else {
            mReturnToStartPosition.run();
        }
    }

    /**
     * 通知控件加载状态已更改。 当通过滑动手势触发加载时，不要调用此方法。
     * @param loading
     */
    public void setLoading(boolean loading) {
        if (mLoading != loading) {
            ensureTarget();
            mLoading = loading;
        }
        if(mLoading){
            mReturnToStartPosition.run();  //需要修改
        }else {
            mReturnToStartPosition.run();
        }
    }

    /**
     * @return Whether the SwipeRefreshWidget is actively showing refresh
     * progress.
     */
    public boolean isRefreshing() {
        return mRefreshing;
    }

    public boolean isLoading() {
        return mLoading;
    }

    /**
     * 确保内容控件存在，否则抛出异常
     */
    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid out yet.
        if (mTarget == null) {
            if (getChildCount() > 2 && !isInEditMode()) {
                throw new IllegalStateException(
                        "SwipeRefreshLayout can only host two children");
            }
            mTarget = getChildAt(1);

            // 控制是否允许滚动
            mTarget.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return mDisable;
                }
            });

            mOriginalOffsetTop = mTarget.getTop() + getPaddingTop();
        }
        if (mDistanceToTriggerSync == -1) {
            if (getParent() != null && ((View) getParent()).getHeight() > 0) {
                final DisplayMetrics metrics = getResources()
                        .getDisplayMetrics();
                mDistanceToTriggerSync = (int) Math.min(
                        ((View) getParent()).getHeight()
                                * MAX_SWIPE_DISTANCE_FACTOR,
                        REFRESH_TRIGGER_DISTANCE * metrics.density);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0 || getChildCount() == 1) {
            return;
        }
        final View child = getChildAt(1);
        final int childLeft = getPaddingLeft();
        final int childTop = mCurrentTargetOffsetTop + getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop
                + childHeight);

        //头部的位置，后面添加底部View时可以参考
        mHeaderView.layout(childLeft, childTop - mHeaderHeight, childLeft
                + childWidth, childTop);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() <= 1) {
            throw new IllegalStateException(
                    "SwipeRefreshLayout must have the headerview and contentview");
        }

        if (getChildCount() > 2 && !isInEditMode()) {
            throw new IllegalStateException(
                    "SwipeRefreshLayout can only host two children");
        }

        if (mHeaderView == null) {
            mHeaderView = getChildAt(0);
            measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
            mHeaderHeight = mHeaderView.getMeasuredHeight();

            mDistanceToTriggerSync = mHeaderHeight;
        }

        getChildAt(1).measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth()
                                - getPaddingLeft() - getPaddingRight(),
                        MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight()
                                - getPaddingTop() - getPaddingBottom(),
                        MeasureSpec.EXACTLY));
    }

    /**
     * @return 内容控件是否可以向上滑动
     */
    public boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView
                        .getChildAt(0).getTop() < absListView
                        .getPaddingTop());
            } else {
                return mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1);
        }
    }

    /**
     * 内容控件是否可以向下滑动
     * @return
     */
    public boolean canChildScrollDown() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                View lastChild = absListView.getChildAt(absListView.getChildCount() - 1);
                if (lastChild != null) {
                    return (absListView.getLastVisiblePosition() == (absListView.getCount() - 1))
                            && lastChild.getBottom() > absListView.getPaddingBottom();
                } else {
                    return false;
                }
            } else {
                return mTarget.getHeight() - mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, 1);
        }
    }

    /**
     * 分发事件。只关心需要的事件。
     * @param ev
     * @return
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();  //再次确保内容控件存在

        final int action = MotionEventCompat.getActionMasked(ev);

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;  //表示新的一轮手势滑动 ，mReturningToStart在动画开始时赋值，结束时不会赋值
        }

        if (!isEnabled() || mReturningToStart || (canChildScrollUp() && canChildScrollDown()) || mStatus == STATUS.REFRESHING || mStatus == STATUS.LOADING) {
            // 如果控件不可用 或者正在动画 或者子控件可以滑动 或者当前状态为刷新或者加载 则直接返回
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);  //获取按下手指的id
                mIsBeingDragged = false;
                mIsBeingLoad = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {  //手指id不可用，说明已经抬起或者超出范围 下面的ACTION_UP或者ACTION_CANCEL会赋值为INVALID_POINTER
                    Log.e(LOG_TAG,
                            "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
                        mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG,
                            "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = y - mInitialMotionY;
                if (yDiff > mTouchSlop && !canChildScrollUp() && isRefreshEnabled) {  //如果是向下滑动并且可以下拉则准备下拉刷新处理
                    mLastMotionY = y;
                    mIsBeingDragged = true;
                    mIsBeingLoad = false;
                }else if(-yDiff > mTouchSlop && !canChildScrollDown() && isLoadEnabled){ //如果是向上滑动并且可以上拉则准备上拉加载处理
                    mLastMotionY = y;
                    mIsBeingDragged = false;
                    mIsBeingLoad = true;
                }
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev); //多个手指并且有手指抬起时调用
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mIsBeingLoad = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged || mIsBeingLoad;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // Nope.
    }

    /**
     * 处理具体的上拉下拉效果（只有需要关心的事件才会调用此方法），部分代码跟上面的分发事件一样。可以参考上面的注释
     * @param ev
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || (canChildScrollUp() && canChildScrollDown()) || mStatus == STATUS.REFRESHING || mStatus == STATUS.LOADING) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsBeingDragged = false;
                mIsBeingLoad = false;
                break;

            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
                        mActivePointerId);

                if (pointerIndex < 0) {
                    Log.e(LOG_TAG,
                            "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = MotionEventCompat.getY(ev, pointerIndex);

                final float yDiff = y - mInitialMotionY;

                if (!mIsBeingDragged && !mIsBeingLoad && yDiff > mTouchSlop &&  !canChildScrollUp() && isRefreshEnabled) {
                    mIsBeingDragged = true;
                    mIsBeingLoad = false;
                }else if(!mIsBeingDragged && !mIsBeingLoad && -yDiff > mTouchSlop && !canChildScrollDown() && isLoadEnabled) {
                    mIsBeingDragged = false;
                    mIsBeingLoad = true;
                }

                if (mIsBeingDragged) {  //下拉处理
                    // User velocity passed min velocity; trigger a refresh
                    if (yDiff > mDistanceToTriggerSync) { //如果下拉距离大于临界值则进入松开下拉刷新状态
                        if (mStatus == STATUS.NORMAL) {
                            mStatus = STATUS.LOOSENREFRESH;

                            if (mListener != null) {
                                mListener.onLoose();
                            }
                        }
                        updateContentOffsetTop((int) (yDiff));  //更新内容控件的位置
                    } else {  //
                        if (mStatus == STATUS.LOOSENREFRESH) {  //用户手指又往回滑动时恢复默认状态
                            mStatus = STATUS.NORMAL;
                            if (mListener != null) {
                                mListener.onNormal();
                            }
                        }

                        updateContentOffsetTop((int) (yDiff));  //更新内容控件的位置
                        if (mLastMotionY > y && mTarget.getTop() == getPaddingTop()) {  //用户手指返回到初始位置时的处理。原因可以查看ACTION_CANCEL事件
                            // If the user puts the view back at the top, we
                            // don't need to. This shouldn't be considered
                            // cancelling the gesture as the user can restart from
                            // the top.
                            removeCallbacks(mCancel);
                        }
                    }
                    mLastMotionY = y;
                }else if(mIsBeingLoad){   //上拉处理
                    // User velocity passed min velocity; trigger a refresh
                    if (-yDiff > mDistanceToTriggerSync) {  //需要修改
                        if (mStatus == STATUS.NORMAL) {
                            mStatus = STATUS.LOOSENLOAD;

                            if (mListener != null) {
                                mListener.onLoose();
                            }
                        }

                        updateContentOffsetTop((int) (yDiff));
                    } else {
                        if (mStatus == STATUS.LOOSENLOAD) {
                            mStatus = STATUS.NORMAL;

                            if (mListener != null) {
                                mListener.onNormal();
                            }
                        }

                        updateContentOffsetTop((int) (yDiff));
                        if (mLastMotionY < y && mTarget.getBottom() == getPaddingBottom()) {
                            // If the user puts the view back at the bottom, we
                            // don't need to. This shouldn't be considered
                            // cancelling the gesture as the user can restart from
                            // the top.
                            removeCallbacks(mCancel);
                        }
                    }
                    mLastMotionY = y;
                }
                break;

            case MotionEventCompat.ACTION_POINTER_DOWN: {  //切换手指
                final int index = MotionEventCompat.getActionIndex(ev);
                mLastMotionY = MotionEventCompat.getY(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
                if (mStatus == STATUS.LOOSENREFRESH) {
                    startRefresh();
                } else if(mStatus == STATUS.LOOSENLOAD){
                    startLoad();
                }else {
                    updatePositionTimeout();  //取消手势
                }

                mIsBeingDragged = false;
                mIsBeingLoad = false;
                mActivePointerId = INVALID_POINTER;
                return false;
            case MotionEvent.ACTION_CANCEL:
                updatePositionTimeout();  //用户手指滑动到控件范围外时调用（超过300毫秒取消手势）

                mIsBeingDragged = false;
                mIsBeingLoad = false;
                mActivePointerId = INVALID_POINTER;
                return false;
        }

        return true;
    }

    private void startRefresh() {
        removeCallbacks(mCancel);
        setRefreshing(true);
        mDisable = true;
        if (mListener != null) {
            mListener.onRefresh();
        }
    }

    private void startLoad() {  //需要修改
        removeCallbacks(mCancel);
        setLoading(true);
        mDisable = true;

//        if (mListener != null) {
//            mListener.onRefresh();
//        }
    }

    public void stopRefresh() {
        setRefreshing(false);
    }
    public void stopLoad() {
        setLoading(false);
    }

    private void updateContentOffsetTop(int targetTop) {
        final int currentTop = mTarget.getTop();
        if(mIsBeingDragged){
            if (targetTop > mDistanceToTriggerSync) {  // 超过触发刷新的临界值时取 临界值+超过临界值的一半
                targetTop = (int) mDistanceToTriggerSync + (int) (targetTop - mDistanceToTriggerSync) / 2;
            } else if (targetTop < 0) {
                targetTop = 0;
            }
        }else {
            if (-targetTop > mDistanceToTriggerSync) {  //需要修改
                targetTop = (int) -mDistanceToTriggerSync + (int) (targetTop + mDistanceToTriggerSync) / 2;
            } else if (targetTop >0) {
                targetTop = 0;
            }
        }
        setTargetOffsetTopAndBottom(targetTop - currentTop);
    }

    private void setTargetOffsetTopAndBottom(int offset) {
        mTarget.offsetTopAndBottom(offset);
        mHeaderView.offsetTopAndBottom(offset);
        mCurrentTargetOffsetTop = mTarget.getTop();
        invalidate();
    }

    private void updatePositionTimeout() {  //取消手势
        removeCallbacks(mCancel);
        postDelayed(mCancel, RETURN_TO_ORIGINAL_POSITION_TIMEOUT);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

        if (pointerId == mActivePointerId) {  //如果抬起的手指是活动的手指则将活动手指切换到第二手指进行滑动
            // This was our active pointer going up. Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev,
                    newPointerIndex);
        }
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a normal/ready-refresh/refresh should implement this interface.
     */
    public interface OnRefreshListener {
        public void onNormal();

        public void onLoose();

        public void onRefresh();
    }

    /**
     * Simple AnimationListener to avoid having to implement unneeded methods in
     * AnimationListeners.
     */
    private class BaseAnimationListener implements AnimationListener {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    }
}
