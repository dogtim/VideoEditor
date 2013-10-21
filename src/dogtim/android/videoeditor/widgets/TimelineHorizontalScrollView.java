/*
 * Copyright (C) 2010 The Android Open Source Project
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

package dogtim.android.videoeditor.widgets;

import java.util.ArrayList;
import java.util.List;

import dogtim.android.videoeditor.R;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.HorizontalScrollView;

/**
 * The timeline scroll view
 */
public class TimelineHorizontalScrollView extends HorizontalScrollView {
    public final static int PLAYHEAD_NORMAL = 1;
    public final static int PLAYHEAD_MOVE_OK = 2;
    public final static int PLAYHEAD_MOVE_NOT_OK = 3;

    // Instance variables
    private final List<ScrollViewListener> mScrollListenerList;
    private final Handler mHandler;
    private final int mPlayheadMarginTop;
    private final int mPlayheadMarginTopOk;
    private final int mPlayheadMarginTopNotOk;
    private final int mPlayheadMarginBottom;
    private final Drawable mNormalPlayheadDrawable;
    private final Drawable mMoveOkPlayheadDrawable;
    private final Drawable mMoveNotOkPlayheadDrawable;
    private final int mHalfParentWidth;
    private ScaleGestureDetector mScaleDetector;
    private int mLastScrollX;
    private boolean mIsScrolling;
    private boolean mAppScroll;
    private boolean mEnableUserScrolling;

    // The runnable which executes when the scrolling ends
    private Runnable mScrollEndedRunnable = new Runnable() {
        @Override
        public void run() {
            mIsScrolling = false;

            for (ScrollViewListener listener : mScrollListenerList) {
                listener.onScrollEnd(TimelineHorizontalScrollView.this, getScrollX(),
                        getScrollY(), mAppScroll);
            }

            mAppScroll = false;
        }
    };

    public TimelineHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mEnableUserScrolling = true;
        mScrollListenerList = new ArrayList<ScrollViewListener>();
        mHandler = new Handler();

        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        // This value is shared by all children. It represents the width of
        // the left empty view.
        setTag(R.id.left_view_width, mHalfParentWidth);
        setTag(R.id.playhead_offset, -1);
        setTag(R.id.playhead_type, PLAYHEAD_NORMAL);

        final Resources resources = context.getResources();

        // Get the playhead margins
        mPlayheadMarginTop = (int)resources.getDimension(R.dimen.playhead_margin_top);
        mPlayheadMarginBottom = (int)resources.getDimension(R.dimen.playhead_margin_bottom);
        mPlayheadMarginTopOk = (int)resources.getDimension(R.dimen.playhead_margin_top_ok);
        mPlayheadMarginTopNotOk = (int)resources.getDimension(R.dimen.playhead_margin_top_not_ok);

        // Prepare the playhead drawable
        mNormalPlayheadDrawable = resources.getDrawable(R.drawable.ic_playhead);
        mMoveOkPlayheadDrawable = resources.getDrawable(R.drawable.playhead_move_ok);
        mMoveNotOkPlayheadDrawable = resources.getDrawable(R.drawable.playhead_move_not_ok);
    }

    public TimelineHorizontalScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineHorizontalScrollView(Context context) {
        this(context, null, 0);
    }

    /**
     * Invoked to enable/disable user scrolling (as opposed to programmatic scrolling)
     * @param enable true to enable user scrolling
     */
    public void enableUserScrolling(boolean enable) {
        mEnableUserScrolling = enable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnableUserScrolling) {
            mScaleDetector.onTouchEvent(ev);
            return super.onTouchEvent(ev);
        } else {
            if (mScaleDetector.isInProgress()) {
                final MotionEvent cancelEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
                mScaleDetector.onTouchEvent(cancelEvent);
                cancelEvent.recycle();
            }
            return true;
        }
    }

    /**
     * @param listener The scale listener
     */
    public void setScaleListener(ScaleGestureDetector.SimpleOnScaleGestureListener listener) {
        mScaleDetector = new ScaleGestureDetector(getContext(), listener);
    }

    /**
     * @param listener The listener
     */
    public void addScrollListener(ScrollViewListener listener) {
        mScrollListenerList.add(listener);
    }

    /**
     * @param listener The listener
     */
    public void removeScrollListener(ScrollViewListener listener) {
        mScrollListenerList.remove(listener);
    }

    /**
     * @return true if scrolling is in progress
     */
    public boolean isScrolling() {
        return mIsScrolling;
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll position
     * @param smooth true to scroll smoothly
     */
    public void appScrollTo(int scrollX, boolean smooth) {
        if (getScrollX() == scrollX) {
            return;
        }

        mAppScroll = true;

        if (smooth) {
            smoothScrollTo(scrollX, 0);
        } else {
            scrollTo(scrollX, 0);
        }
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll offset
     * @param smooth true to scroll smoothly
     */
    public void appScrollBy(int scrollX, boolean smooth) {
        mAppScroll = true;

        if (smooth) {
            smoothScrollBy(scrollX, 0);
        } else {
            scrollBy(scrollX, 0);
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        final int scrollX = getScrollX();
        if (mLastScrollX != scrollX) {
            mLastScrollX = scrollX;

            // Cancel the previous event
            mHandler.removeCallbacks(mScrollEndedRunnable);

            // Post a new event
            mHandler.postDelayed(mScrollEndedRunnable, 300);

            final int scrollY = getScrollY();
            if (mIsScrolling) {
                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollProgress(this, scrollX, scrollY, mAppScroll);
                }
            } else {
                mIsScrolling = true;

                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollBegin(this, scrollX, scrollY, mAppScroll);
                }
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        final int playheadOffset = (Integer)getTag(R.id.playhead_offset);
        final int startX;
        if (playheadOffset < 0) {
            // Draw the playhead in the middle of the screen
            startX = mHalfParentWidth + getScrollX();
        } else {
            // Draw the playhead at the specified position (during trimming)
            startX = playheadOffset;
        }

        final int playheadType = (Integer)getTag(R.id.playhead_type);
        final int halfPlayheadWidth = mNormalPlayheadDrawable.getIntrinsicWidth() / 2;
        switch (playheadType) {
            case PLAYHEAD_NORMAL: {
                // Draw the normal playhead
                mNormalPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTop,
                        startX + halfPlayheadWidth,
                        getHeight() - mPlayheadMarginBottom);
                mNormalPlayheadDrawable.draw(canvas);
                break;
            }

            case PLAYHEAD_MOVE_OK: {
                // Draw the move playhead
                mMoveOkPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTopOk,
                        startX + halfPlayheadWidth,
                        mPlayheadMarginTopOk + mMoveOkPlayheadDrawable.getIntrinsicHeight());
                mMoveOkPlayheadDrawable.draw(canvas);
                break;
            }

            case PLAYHEAD_MOVE_NOT_OK: {
                // Draw the move playhead
                mMoveNotOkPlayheadDrawable.setBounds(
                        startX - halfPlayheadWidth,
                        mPlayheadMarginTopNotOk,
                        startX + halfPlayheadWidth,
                        mPlayheadMarginTopNotOk + mMoveNotOkPlayheadDrawable.getIntrinsicHeight());
                mMoveNotOkPlayheadDrawable.draw(canvas);
                break;
            }

            default: {
                break;
            }
        }
    }
}
