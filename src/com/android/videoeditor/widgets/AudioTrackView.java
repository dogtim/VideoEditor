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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.videoeditor.WaveformData;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.android.videoeditor.service.MovieAudioTrack;
import com.android.videoeditor.R;

/**
 * Audio track view
 */
public class AudioTrackView extends View {
    // Instance variables
    private final GestureDetector mSimpleGestureDetector;
    private final Paint mLinePaint;
    private final Paint mLoopPaint;
    private final Rect mProgressDestRect;
    private final ScrollViewListener mScrollListener;

    private double[] mNormalizedGains;
    private long mTimelineDurationMs;
    private int mProgress;
    private ItemSimpleGestureListener mGestureListener;
    private WaveformData mWaveformData;
    private int mScrollX;
    private int mScreenWidth;

    /*
     * {@inheritDoc}
     */
    public AudioTrackView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = getResources();

        // Use this Paint for drawing the audio samples
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(false);
        mLinePaint.setStrokeWidth(1);
        mLinePaint.setColor(resources.getColor(R.color.audio_waveform));

        // Use this Paint to draw the loop separator
        mLoopPaint = new Paint();
        mLoopPaint.setAntiAlias(false);
        mLoopPaint.setStrokeWidth(1);
        mLoopPaint.setColor(resources.getColor(R.color.audio_loop_separator));

        // Prepare the bitmap rectangles
        final ProgressBar progressBar = ProgressBar.getProgressBar(context);
        final int layoutHeight = (int)resources.getDimension(R.dimen.audio_layout_height);
        mProgressDestRect = new Rect(getPaddingLeft(),
                layoutHeight - progressBar.getHeight() - getPaddingBottom(), 0,
                layoutHeight - getPaddingBottom());

        // Setup the gesture listener
        mSimpleGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (mGestureListener != null) {
                            return mGestureListener.onSingleTapConfirmed(AudioTrackView.this, -1,
                                    e);
                        } else {
                            return false;
                        }
                    }

                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public void onLongPress (MotionEvent e) {
                        if (mGestureListener != null) {
                            mGestureListener.onLongPress(AudioTrackView.this, e);
                        }
                    }
                });

        mScrollListener = new ScrollViewListener() {
            @Override
            public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
            }

            @Override
            public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
            }

            @Override
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                mScrollX = scrollX;
                invalidate();
            }
        };

        // Get the screen width
        final Display display = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

        mProgress = -1;
    }

    public AudioTrackView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioTrackView(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)((View)getParent()).getParent()).getParent();
        mScrollX = scrollView.getScrollX();
        scrollView.addScrollListener(mScrollListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)((View)getParent()).getParent()).getParent();
        scrollView.removeScrollListener(mScrollListener);
    }

    /**
     * @param listener The gesture listener
     */
    public void setGestureListener(ItemSimpleGestureListener listener) {
        mGestureListener = listener;
    }

    /**
     * Set the waveform data
     *
     * @param waveformData The waveform data
     */
    public void setWaveformData(WaveformData waveformData) {
        mWaveformData = waveformData;
        final int numFrames = mWaveformData.getFramesCount();
        final short[] frameGains = mWaveformData.getFrameGains();
        final double[] smoothedGains = new double[numFrames];

        if (numFrames == 1) {
            smoothedGains[0] = frameGains[0];
        } else if (numFrames == 2) {
            smoothedGains[0] = frameGains[0];
            smoothedGains[1] = frameGains[1];
        } else if (numFrames > 2) {
            smoothedGains[0] = (frameGains[0] / 2.0) + (frameGains[1] / 2.0);
            for (int i = 1; i < numFrames - 1; i++) {
                smoothedGains[i] =
                    (frameGains[i - 1] / 3.0) + (frameGains[i] / 3.0) + (frameGains[i + 1] / 3.0);
            }
            smoothedGains[numFrames - 1] = (frameGains[numFrames - 2] / 2.0) +
                (frameGains[numFrames - 1] / 2.0);
        }

        // Make sure the range is no more than 0 - 255
        double maxGain = 1.0;
        for (int i = 0; i < numFrames; i++) {
            if (smoothedGains[i] > maxGain) {
                maxGain = smoothedGains[i];
            }
        }

        double scaleFactor = 1.0;
        if (maxGain > 255.0) {
            scaleFactor = 255 / maxGain;
        }

        // Build histogram of 256 bins and figure out the new scaled max
        maxGain = 0;
        final int gainHist[] = new int[256];
        for (int i = 0; i < numFrames; i++) {
            int smoothedGain = (int)(smoothedGains[i] * scaleFactor);
            if (smoothedGain < 0) {
                smoothedGain = 0;
            }
            if (smoothedGain > 255) {
                smoothedGain = 255;
            }

            if (smoothedGain > maxGain) {
                maxGain = smoothedGain;
            }

            gainHist[smoothedGain]++;
        }

        // Re-calibrate the minimum to be 5%
        double minGain = 0;
        int sum = 0;
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[(int)minGain];
            minGain++;
        }

        // Re-calibrate the max to be 99%
        sum = 0;
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[(int)maxGain];
            maxGain--;
        }

        // Compute the normalized heights
        final int halfHeight =
            (int)((getResources().getDimension(R.dimen.audio_layout_height) - getPaddingTop() -
                    getPaddingBottom() - 4) / 2);
        final MovieAudioTrack audioTrack = (MovieAudioTrack)getTag();

        final int numFramesComp = (int)audioTrack.getDuration() / mWaveformData.getFrameDuration();
        mNormalizedGains = new double[Math.max(numFramesComp, numFrames)];
        final double range = maxGain - minGain;
        for (int i = 0; i < numFrames; i++) {
            double value = (smoothedGains[i] * scaleFactor - minGain) / range;
            if (value < 0.0) {
                value = 0.0;
            }

            if (value > 1.0) {
                value = 1.0;
            }

            mNormalizedGains[i] = value * value * halfHeight;
        }
    }

    /**
     * The project duration has changed
     *
     * @param timelineDurationMs The new timeline duration
     */
    public void updateTimelineDuration(long timelineDurationMs) {
        mTimelineDurationMs = timelineDurationMs;
    }

    /**
     * The audio track processing progress
     *
     * @param progress The progress
     */
    public void setProgress(int progress) {
        mProgress = progress;

        invalidate();
    }

    /**
     * @return The waveform data
     */
    public WaveformData getWaveformData() {
        return mWaveformData;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mWaveformData == null) {
            if (mProgress >= 0) {
                ProgressBar.getProgressBar(getContext()).draw(canvas, mProgress,
                        mProgressDestRect, getPaddingLeft(), getWidth() - getPaddingRight());
            }
        } else if (mTimelineDurationMs > 0) { // Draw waveform
            // Compute the number of frames in the trimmed audio track
            final MovieAudioTrack audioTrack = (MovieAudioTrack)getTag();
            final int startFrame = (int)(audioTrack.getBoundaryBeginTime() /
                    mWaveformData.getFrameDuration());
            final int numFrames =
                (int)(audioTrack.getTimelineDuration() / mWaveformData.getFrameDuration());

            final int ctr = getHeight() / 2;
            short value;
            int index;
            final int start = Math.max(mScrollX - mScreenWidth / 2, getPaddingLeft());
            final int limit = Math.min(mScrollX + mScreenWidth, getWidth() - getPaddingRight());
            if (audioTrack.isAppLooping()) {
                // Compute the milliseconds / pixel at the current zoom level
                final float framesPerPixel = mTimelineDurationMs /
                    ((float)(mWaveformData.getFrameDuration() *
                            (((View)getParent()).getWidth() - mScreenWidth)));

                for (int i = start; i < limit; i++) {
                    index = startFrame + (int)(framesPerPixel * i);
                    index = index % numFrames;
                    value = (short)mNormalizedGains[index];
                    canvas.drawLine(i, ctr - value, i, ctr + 1 + value, mLinePaint);

                    if (index == startFrame) { // Draw the loop delineation
                        canvas.drawLine(i, getPaddingTop(), i,
                                getHeight() - getPaddingBottom(), mLinePaint);
                    }
                }
            } else {
                // Compute the milliseconds / pixel at the current zoom level
                final float framesPerPixel =  audioTrack.getTimelineDuration() /
                    ((float)(mWaveformData.getFrameDuration() * getWidth()));

                for (int i = start; i < limit; i++) {
                    index = startFrame + (int)(framesPerPixel * i);
                    value = (short)(mNormalizedGains[index]);
                    canvas.drawLine(i, ctr - value, i, ctr + 1 + value, mLinePaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the gesture detector inspect all events.
        mSimpleGestureDetector.onTouchEvent(ev);

        super.onTouchEvent(ev);
        return true;
    }
}
