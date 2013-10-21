/*

 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieMediaItem;
import com.android.videoeditor.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

/**
 * Media item preview view on the timeline. This class assumes the media item is always put on a
 * MediaLinearLayout and is wrapped with a timeline scroll view.
 */
public class MediaItemView extends View {
    private static final String TAG = "MediaItemView";

    // Static variables
    private static Drawable sAddTransitionDrawable;
    private static Drawable sEmptyFrameDrawable;
    private static ThumbnailCache sThumbnailCache;

    // Because MediaItemView may be recreated for the same MediaItem (it happens
    // when the device orientation is changed), we use a globally unique
    // generation counter to reject thumbnail results (passed to setBitmap())
    // requested by a previous incarnation of MediaItemView.
    private static int sGenerationCounter;

    // Instance variables
    private final GestureDetector mGestureDetector;
    private final ScrollViewListener mScrollListener;
    private final Rect mGeneratingEffectProgressDestRect;

    private boolean mIsScrolling;
    private boolean mIsPlaying;

    // Progress of generation of the effect applied on this media item view.
    // -1 indicates the generation is not in progress. 0-100 indicates the
    // generation is in progress. Currently only Ken Burns effect is used with
    // the progress bar.
    private int mGeneratingEffectProgress;

    // The scrolled left pixels of this view.
    private int mScrollX;

    private String mProjectPath;
    private MovieMediaItem mMediaItem;
    // Convenient handle to the parent timeline scroll view.
    private TimelineHorizontalScrollView mScrollView;
    // Convenient handle to the parent timeline linear layout.
    private MediaLinearLayout mTimeline;
    private ItemSimpleGestureListener mGestureListener;
    private int[] mLeftState, mRightState;

    private int mScreenWidth;
    private int mThumbnailWidth, mThumbnailHeight;
    private int mNumberOfThumbnails;
    private long mBeginTimeMs, mEndTimeMs;

    private int mGeneration;
    private HashSet<Integer> mPending;
    private ArrayList<Integer> mWantThumbnails;

    public MediaItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaItemView(Context context) {
        this(context, null, 0);
    }

    public MediaItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Initialize static data
        if (sAddTransitionDrawable == null) {
            sAddTransitionDrawable = getResources().getDrawable(
                    R.drawable.add_transition_selector);
            sEmptyFrameDrawable = getResources().getDrawable(
                    R.drawable.timeline_loading);

            // Initialize the thumbnail cache, limit the memory usage to 3MB
            sThumbnailCache = new ThumbnailCache(3*1024*1024);
        }

        // Get the screen width
        final Display display = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

        // Setup our gesture detector and scroll listener
        mGestureDetector = new GestureDetector(context, new MyGestureListener());
        mScrollListener = new MyScrollViewListener();

        // Prepare the progress bar rectangles
        final ProgressBar progressBar = ProgressBar.getProgressBar(context);
        final int layoutHeight = (int)(
                getResources().getDimension(R.dimen.media_layout_height) -
                getResources().getDimension(R.dimen.media_layout_padding));
        mGeneratingEffectProgressDestRect = new Rect(getPaddingLeft(),
                layoutHeight - progressBar.getHeight() - getPaddingBottom(), 0,
                layoutHeight - getPaddingBottom());

        // Initialize the progress value
        mGeneratingEffectProgress = -1;

        // Initialize the "Add transition" indicators state
        mLeftState = View.EMPTY_STATE_SET;
        mRightState = View.EMPTY_STATE_SET;

        // Initialize the thumbnail indices we want to request
        mWantThumbnails = new ArrayList<Integer>();

        // Initialize the set of indices we are waiting
        mPending = new HashSet<Integer>();

        // Initialize the generation number
        mGeneration = sGenerationCounter++;
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mGestureListener == null) {
                return false;
            }

            int tappedArea = ItemSimpleGestureListener.CENTER_AREA;

            if (hasSpaceForAddTransitionIcons()) {
                if (mMediaItem.getBeginTransition() == null &&
                        e.getX() < sAddTransitionDrawable.getIntrinsicWidth() +
                        getPaddingLeft()) {
                    tappedArea = ItemSimpleGestureListener.LEFT_AREA;
                } else if (mMediaItem.getEndTransition() == null &&
                        e.getX() >= getWidth() - getPaddingRight() -
                        sAddTransitionDrawable.getIntrinsicWidth()) {
                    tappedArea = ItemSimpleGestureListener.RIGHT_AREA;
                }
            }
            return mGestureListener.onSingleTapConfirmed(
                    MediaItemView.this, tappedArea, e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mGestureListener != null) {
                mGestureListener.onLongPress(MediaItemView.this, e);
            }
        }
    }

    private class MyScrollViewListener implements ScrollViewListener {
        @Override
        public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
            mIsScrolling = true;
        }

        @Override
        public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
            mScrollX = scrollX;
            invalidate();
        }

        @Override
        public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
            mIsScrolling = false;
            mScrollX = scrollX;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        mMediaItem = (MovieMediaItem) getTag();

        mScrollView = (TimelineHorizontalScrollView) getRootView().findViewById(
                R.id.timeline_scroller);
        mScrollView.addScrollListener(mScrollListener);
        // Add the horizontal scroll view listener
        mScrollX = mScrollView.getScrollX();

        mTimeline = (MediaLinearLayout) getRootView().findViewById(R.id.timeline_media);
    }

    @Override
    protected void onDetachedFromWindow() {
        mScrollView.removeScrollListener(mScrollListener);
        // Release the cached bitmaps
        releaseBitmapsAndClear();
    }

    /**
     * @return The shadow builder
     */
    public DragShadowBuilder getShadowBuilder() {
        return new MediaItemShadowBuilder(this);
    }

    /**
     * Shadow builder for the media item
     */
    private class MediaItemShadowBuilder extends DragShadowBuilder {
        private final Drawable mFrame;

        public MediaItemShadowBuilder(View view) {
            super(view);
            mFrame = view.getContext().getResources().getDrawable(
                    R.drawable.timeline_item_pressed);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            shadowSize.set(getShadowWidth(), getShadowHeight());
            shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            mFrame.setBounds(0, 0, getShadowWidth(), getShadowHeight());
            mFrame.draw(canvas);

            Bitmap bitmap = getOneThumbnail();
            if (bitmap != null) {
                final View view = getView();
                canvas.drawBitmap(bitmap, view.getPaddingLeft(),
                        view.getPaddingTop(), null);
            }
        }
    }

    /**
     * @return The shadow width
     */
    private int getShadowWidth() {
        final int thumbnailHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        final int thumbnailWidth = (thumbnailHeight * mMediaItem.getWidth()) /
            mMediaItem.getHeight();
        return thumbnailWidth + getPaddingLeft() + getPaddingRight();
    }

    /**
     * @return The shadow height
     */
    private int getShadowHeight() {
        return getHeight();
    }

    private Bitmap getOneThumbnail() {
        ThumbnailKey key = new ThumbnailKey();
        key.mediaItemId = mMediaItem.getId();

        // Find any one cached thumbnail
        for (int i = 0; i < mNumberOfThumbnails; i++) {
            key.index = i;
            Bitmap bitmap = sThumbnailCache.get(key);
            if (bitmap != null) {
                return bitmap;
            }
        }

        return null;
    }

    /**
     * @param projectPath The project path
     */
    public void setProjectPath(String projectPath) {
        mProjectPath = projectPath;
    }

    /**
     * @param listener The gesture listener
     */
    public void setGestureListener(ItemSimpleGestureListener listener) {
        mGestureListener = listener;
    }

    /**
     * A view enters or exits the playback mode
     *
     * @param playback true if playback is in progress
     */
    public void setPlaybackMode(boolean playback) {
        mIsPlaying = playback;
        invalidate();
    }

    /**
     * Resets the effect generation progress status.
     */
    public void resetGeneratingEffectProgress() {
        setGeneratingEffectProgress(-1);
    }

    /**
     * Sets the effect generation progress of this view.
     */
    public void setGeneratingEffectProgress(int progress) {
        if (progress == 0) {
            mGeneratingEffectProgress = progress;
            // Release the current set of bitmaps. New content is being generated.
            releaseBitmapsAndClear();
        } else if (progress == 100) {
            mGeneratingEffectProgress = -1;
        } else {
            mGeneratingEffectProgress = progress;
        }

        invalidate();
    }

    /**
     * The view has been layout out.
     *
     * @param oldLeft The old left position
     * @param oldRight The old right position
     */
    public void onLayoutPerformed(int oldLeft, int oldRight) {
        // Compute the thumbnail width and height
        mThumbnailHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        mThumbnailWidth = (mThumbnailHeight * mMediaItem.getWidth()) / mMediaItem.getHeight();

        // We are not able to display a bitmap with width or height > 2048.
        while (mThumbnailWidth > 2048 || mThumbnailHeight > 2048) {
            mThumbnailHeight /= 2;
            mThumbnailWidth /= 2;
        }

        int usableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        // Compute the ceiling of (usableWidth / mThumbnailWidth).
        mNumberOfThumbnails = (usableWidth + mThumbnailWidth - 1) / mThumbnailWidth;
        mBeginTimeMs = mMediaItem.getAppBoundaryBeginTime();
        mEndTimeMs = mMediaItem.getAppBoundaryEndTime();

        releaseBitmapsAndClear();
        invalidate();
    }

    /**
     * @return True if the effect generation is in progress
     */
    public boolean isGeneratingEffect() {
        return (mGeneratingEffectProgress >= 0);
    }

    public boolean setBitmap(Bitmap bitmap, int index, int token) {
        // Ignore results from previous requests
        if (token != mGeneration) {
            return false;
        }
        if (!mPending.contains(index)) {
            Log.e(TAG, "received unasked bitmap, index = " + index);
            return false;
        }
        if (bitmap == null) {
            Log.w(TAG, "receive null bitmap for index = " + index);
            // We keep this request in mPending, so we won't request it again.
            return false;
        }
        mPending.remove(index);
        ThumbnailKey key = new ThumbnailKey(mMediaItem.getId(), index);
        sThumbnailCache.put(key, bitmap);

        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mGeneratingEffectProgress >= 0) {
            ProgressBar.getProgressBar(getContext()).draw(
                    canvas, mGeneratingEffectProgress, mGeneratingEffectProgressDestRect,
                    getPaddingLeft(), getWidth() - getPaddingRight());
        } else {
            // Do not draw in the padding area
            canvas.clipRect(getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(),
                    getHeight() - getPaddingBottom());

            // Draw thumbnails
            drawThumbnails(canvas);

            // Draw the "Add transition" indicators
            if (isSelected()) {
                drawAddTransitionIcons(canvas);
            } else if (mTimeline.hasItemSelected()) {
                // Dim myself if some view on the timeline is selected but not me
                // by drawing a transparent black overlay.
                final Paint paint = new Paint();
                paint.setColor(Color.BLACK);
                paint.setAlpha(192);
                canvas.drawPaint(paint);
            }

            // Request thumbnails if things are not moving
            boolean isBusy = mIsPlaying || mTimeline.isTrimming() || mIsScrolling;
            if (!isBusy && !mWantThumbnails.isEmpty()) {
                requestThumbnails();
            }
        }
    }

    // Draws the thumbnails, also put unavailable thumbnail indices in
    // mWantThumbnails.
    private void drawThumbnails(Canvas canvas) {
        mWantThumbnails.clear();

        // The screen coordinate of the left edge of the usable area.
        int left = getLeft() + getPaddingLeft() - mScrollX;
        // The screen coordinate of the right edge of the usable area.
        int right = getRight() - getPaddingRight() - mScrollX;
        // Return if the usable area is not on screen.
        if (left >= mScreenWidth || right <= 0 || left >= right) {
            return;
        }

        // Map [0, mScreenWidth - 1] to the indices of the thumbnail.
        int startIdx = (0 - left) / mThumbnailWidth;
        int endIdx = (mScreenWidth - 1 - left) / mThumbnailWidth;

        startIdx = clamp(startIdx, 0, mNumberOfThumbnails - 1);
        endIdx = clamp(endIdx, 0, mNumberOfThumbnails - 1);

        // Prepare variables used in the loop
        ThumbnailKey key = new ThumbnailKey();
        key.mediaItemId = mMediaItem.getId();
        int x = getPaddingLeft() + startIdx * mThumbnailWidth;
        int y = getPaddingTop();

        // Center the thumbnail vertically
        int spacing = (getHeight() - getPaddingTop() - getPaddingBottom() -
                mThumbnailHeight) / 2;
        y += spacing;

        // Loop through the thumbnails on screen and draw it
        for (int i = startIdx; i <= endIdx; i++) {
            key.index = i;
            Bitmap bitmap = sThumbnailCache.get(key);
            if (bitmap == null) {
                // Draw a frame placeholder
                sEmptyFrameDrawable.setBounds(
                        x, y, x + mThumbnailWidth, y + mThumbnailHeight);
                sEmptyFrameDrawable.draw(canvas);
                if (!mPending.contains(i)) {
                    mWantThumbnails.add(Integer.valueOf(i));
                }
            } else {
                canvas.drawBitmap(bitmap, x, y, null);
            }
            x += mThumbnailWidth;
        }
    }

    /**
     * Draws the "Add transition" icons at the beginning and end of the media item.
     *
     * @param canvas Canvas to be drawn
     */
    private void drawAddTransitionIcons(Canvas canvas) {
        if (hasSpaceForAddTransitionIcons()) {
            if (mMediaItem.getBeginTransition() == null) {
                sAddTransitionDrawable.setState(mLeftState);
                sAddTransitionDrawable.setBounds(getPaddingLeft(), getPaddingTop(),
                        sAddTransitionDrawable.getIntrinsicWidth() + getPaddingLeft(),
                        getPaddingTop() + sAddTransitionDrawable.getIntrinsicHeight());
                sAddTransitionDrawable.draw(canvas);
            }

            if (mMediaItem.getEndTransition() == null) {
                sAddTransitionDrawable.setState(mRightState);
                sAddTransitionDrawable.setBounds(
                        getWidth() - getPaddingRight() -
                        sAddTransitionDrawable.getIntrinsicWidth(),
                        getPaddingTop(), getWidth() - getPaddingRight(),
                        getPaddingTop() + sAddTransitionDrawable.getIntrinsicHeight());
                sAddTransitionDrawable.draw(canvas);
            }
        }
    }

    /**
     * @return true if the visible area of this view is big enough to display
     *      "add transition" icons on both sides; false otherwise.
     */
    private boolean hasSpaceForAddTransitionIcons() {
        if (mTimeline.isTrimming()) {
            return false;
        }

        return (getWidth() - getPaddingLeft() - getPaddingRight() >=
                2 * sAddTransitionDrawable.getIntrinsicWidth());
    }

    /**
     * Clamps the input value v to the range [low, high].
     */
    private static int clamp(int v, int low, int high) {
        return Math.min(Math.max(v, low), high);
    }

    /**
     * Requests the thumbnails in mWantThumbnails (which is filled by onDraw).
     */
    private void requestThumbnails() {
        // Copy mWantThumbnails to an array
        int indices[] = new int[mWantThumbnails.size()];
        for (int i = 0; i < mWantThumbnails.size(); i++) {
            indices[i] = mWantThumbnails.get(i);
        }

        // Put them in the pending set
        mPending.addAll(mWantThumbnails);

        ApiService.getMediaItemThumbnails(getContext(), mProjectPath,
                mMediaItem.getId(), mThumbnailWidth, mThumbnailHeight,
                mBeginTimeMs, mEndTimeMs, mNumberOfThumbnails, mGeneration,
                indices);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the gesture detector inspect all events.
        mGestureDetector.onTouchEvent(ev);
        super.onTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mLeftState = View.EMPTY_STATE_SET;
                mRightState = View.EMPTY_STATE_SET;
                if (isSelected() && hasSpaceForAddTransitionIcons()) {
                    if (ev.getX() < sAddTransitionDrawable.getIntrinsicWidth() +
                            getPaddingLeft()) {
                        if (mMediaItem.getBeginTransition() == null) {
                            mLeftState = View.PRESSED_WINDOW_FOCUSED_STATE_SET;
                        }
                    } else if (ev.getX() >= getWidth() - getPaddingRight() -
                            sAddTransitionDrawable.getIntrinsicWidth()) {
                        if (mMediaItem.getEndTransition() == null) {
                            mRightState = View.PRESSED_WINDOW_FOCUSED_STATE_SET;
                        }
                    }
                }
                invalidate();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mRightState = View.EMPTY_STATE_SET;
                mLeftState = View.EMPTY_STATE_SET;
                invalidate();
                break;
            }

            default: {
                break;
            }
        }

        return true;
    }

    private void releaseBitmapsAndClear() {
        sThumbnailCache.clearForMediaItemId(mMediaItem.getId());
        mPending.clear();
        mGeneration = sGenerationCounter++;
    }
}

class ThumbnailKey {
    public String mediaItemId;
    public int index;

    public ThumbnailKey() {
    }

    public ThumbnailKey(String id, int idx) {
        mediaItemId = id;
        index = idx;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ThumbnailKey)) {
            return false;
        }
        ThumbnailKey key = (ThumbnailKey) o;
        return index == key.index && mediaItemId.equals(key.mediaItemId);
    }

    @Override
    public int hashCode() {
        return mediaItemId.hashCode() ^ index;
    }
}

class ThumbnailCache {
    private LruCache<ThumbnailKey, Bitmap> mCache;

    public ThumbnailCache(int size) {
        mCache = new LruCache<ThumbnailKey, Bitmap>(size) {
            @Override
            protected int sizeOf(ThumbnailKey key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    void put(ThumbnailKey key, Bitmap value) {
        mCache.put(key, value);
    }

    Bitmap get(ThumbnailKey key) {
        return mCache.get(key);
    }

    void clearForMediaItemId(String id) {
        Map<ThumbnailKey, Bitmap> map = mCache.snapshot();
        for (ThumbnailKey key : map.keySet()) {
            if (key.mediaItemId.equals(id)) {
                mCache.remove(key);
            }
        }
    }
}
