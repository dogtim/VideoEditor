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

package com.android.videoeditor.widgets;

import com.android.videoeditor.service.MovieMediaItem;
import com.android.videoeditor.service.MovieOverlay;
import com.android.videoeditor.util.ImageUtils;
import com.android.videoeditor.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageView;

/**
 * Overlay view
 */
public class OverlayView extends ImageView {
    // States
    // No visible state
    public static final int STATE_STUB = 0;
    // Display an "Add" button state
    public static final int STATE_ADD_BUTTON = 1;
    // The overlay is displayed
    public static final int STATE_OVERLAY = 2;

    // Instance variables
    private final Drawable mArrowLeft;
    private final Drawable mArrowRight;
    private final GestureDetector mSimpleGestureDetector;
    private ItemMoveGestureListener mGestureListener;
    private int mState;
    private boolean mLongPressMove;
    private MotionEvent mStartScrollEvent;

    public OverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mSimpleGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (mGestureListener != null) {
                            return mGestureListener.onSingleTapConfirmed(OverlayView.this, -1, e);
                        } else {
                            return false;
                        }
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (mGestureListener == null) {
                            return;
                        }

                        mGestureListener.onLongPress(OverlayView.this, e);

                        if (mState == STATE_OVERLAY) {
                            if (mGestureListener.onMoveBegin(OverlayView.this, e)) {
                                mLongPressMove = true;
                                // Draw the 'move' arrows
                                invalidate();

                                mStartScrollEvent = MotionEvent.obtain(e);
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                });

        // Prepare the handle arrows
        final Resources resources = getResources();
        mArrowLeft = resources.getDrawable(R.drawable.ic_drag_clip_left);
        mArrowRight = resources.getDrawable(R.drawable.ic_drag_clip_right);

        mState = STATE_STUB;
    }

    public OverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverlayView(Context context) {
        this(context, null, 0);
    }

    /**
     * @return The state
     */
    public int getState() {
        return mState;
    }

    /**
     * @param state The state
     */
    public void setState(int state) {
        mState = state;

        switch (mState) {
            case STATE_STUB: {
                setBackgroundDrawable(null);
                setImageDrawable(null);
                break;
            }

            case STATE_ADD_BUTTON: {
                setBackgroundDrawable(null);
                setImageResource(R.drawable.ic_menu_add_title);
                break;
            }

            case STATE_OVERLAY: {
                setBackgroundResource(R.drawable.timeline_item_selector);
                setImageDrawable(null);
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * @param listener The gesture listener
     */
    public void setGestureListener(ItemMoveGestureListener listener) {
        mGestureListener = listener;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        if (!selected) {
            if (mState == STATE_ADD_BUTTON) {
                setState(STATE_STUB);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mSimpleGestureDetector.onTouchEvent(ev);

        super.onTouchEvent(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                if (mLongPressMove && mGestureListener != null) {
                    mGestureListener.onMove(this, mStartScrollEvent, ev);
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mLongPressMove) {
                    mLongPressMove = false;
                    // Hide the 'move' arrows
                    invalidate();

                    if (mGestureListener != null) {
                        mGestureListener.onMoveEnd(this);
                    }
                }
                break;
            }
        }

        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        switch (mState) {
            case STATE_STUB: {
                break;
            }

            case STATE_ADD_BUTTON: {
                break;
            }

            case STATE_OVERLAY: {
                final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
                if (mediaItem != null) {
                    final MovieOverlay overlay = mediaItem.getOverlay();
                    if (overlay != null) {
                        ImageUtils.buildOverlayPreview(getContext(), canvas, overlay.getType(),
                                overlay.getTitle(), overlay.getSubtitle(),
                                getPaddingLeft(), getPaddingTop(),
                                getWidth() - getPaddingLeft() - getPaddingRight(),
                                getHeight() - getPaddingTop() - getPaddingBottom());
                    }
                }
                break;
            }

            default: {
                break;
            }
        }

        if (mLongPressMove) {
            final int halfWidth = getWidth() / 2;
            mArrowLeft.setBounds(halfWidth - 4 - mArrowLeft.getIntrinsicWidth(), getPaddingTop(),
                    halfWidth - 4, getPaddingTop() + mArrowLeft.getIntrinsicHeight());
            mArrowLeft.draw(canvas);

            mArrowRight.setBounds(halfWidth + 4, getPaddingTop(), halfWidth + 4
                    + mArrowRight.getIntrinsicWidth(), getPaddingTop()
                    + mArrowRight.getIntrinsicHeight());
            mArrowRight.draw(canvas);
        }
    }
}
