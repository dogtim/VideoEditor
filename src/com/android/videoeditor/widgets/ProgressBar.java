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

import com.android.videoeditor.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

/**
 * Draw a progress bar for media items, transitions and the audio track
 */
class ProgressBar {
    // The one
    private static ProgressBar mProgressBar;

    // Instance variables
    private final Bitmap mProgressLeftBitmap;
    private final Bitmap mProgressRightBitmap;
    private final Rect mProgressSrcRect;

    /**
     * Get the progress bar singleton
     *
     * @param context The context
     *
     * @return The progress bar
     */
    public static ProgressBar getProgressBar(Context context) {
        if (mProgressBar == null) {
            mProgressBar = new ProgressBar(context);
        }

        return mProgressBar;
    }

    /**
     * Constructor
     *
     * @param context The context
     */
    private ProgressBar(Context context) {
        final Resources resources = context.getResources();

        // Prepare the progress bitmap
        mProgressLeftBitmap = BitmapFactory.decodeResource(resources,
                R.drawable.item_progress_left);
        mProgressRightBitmap = BitmapFactory.decodeResource(resources,
                R.drawable.item_progress_right);

        mProgressSrcRect = new Rect(0, 0, mProgressLeftBitmap.getWidth(),
                mProgressLeftBitmap.getHeight());
    }

    /**
     * @return The height
     */
    public int getHeight() {
        return mProgressLeftBitmap.getHeight();
    }

    /**
     * Draw the progress bar
     *
     * @param canvas The canvas
     * @param progress The progress (between 0 and 100)
     * @param dest The destination rectangle
     * @param left The left offset
     * @param width The width
     */
    public void draw(Canvas canvas, int progress, Rect dest, int left, int width) {
        switch (progress) {
            case 0: {
                dest.left = left;
                dest.right = width;
                canvas.drawBitmap(mProgressRightBitmap, mProgressSrcRect, dest, null);
                break;
            }

            case 100: {
                dest.left = left;
                dest.right = width;
                canvas.drawBitmap(mProgressLeftBitmap, mProgressSrcRect, dest, null);
                break;
            }

            default: {
                dest.right = ((width - left) * progress) / 100;

                // Draw progress
                if (progress > 0) {
                    dest.left = left;
                    canvas.drawBitmap(mProgressLeftBitmap, mProgressSrcRect, dest, null);
                }

                if (progress < 100) {
                    dest.left = dest.right;
                    dest.right = width;
                    canvas.drawBitmap(mProgressRightBitmap, mProgressSrcRect, dest, null);
                }

                break;
            }
        }
    }
}
