/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.videoeditor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.android.videoeditor.widgets.ImageViewTouchBase;

/**
 * Activity for setting the begin and end Ken Burns viewing rectangles
 */
public class KenBurnsActivity extends Activity {
    // Logging
    private static final String TAG = "KenBurnsActivity";

    // State keys
    private static final String STATE_WHICH_RECTANGLE_ID = "which";
    private static final String STATE_START_RECTANGLE = "start";
    private static final String STATE_END_RECTANGLE = "end";

    // Intent extras
    public static final String PARAM_WIDTH = "width";
    public static final String PARAM_HEIGHT = "height";
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";
    public static final String PARAM_START_RECT = "start_rect";
    public static final String PARAM_END_RECT = "end_rect";

    private static final int MAX_HW_BITMAP_WIDTH = 2048;
    private static final int MAX_HW_BITMAP_HEIGHT = 2048;
    private static final int MAX_WIDTH = 1296;
    private static final int MAX_HEIGHT = 720;
    private static final int MAX_PAN = 3;

    // Instance variables
    private final Rect mStartRect = new Rect(0, 0, 0, 0);
    private final Rect mEndRect = new Rect(0, 0, 0, 0);
    private final RectF mMatrixRect = new RectF(0, 0, 0, 0);
    private RadioGroup mRadioGroup;
    private ImageViewTouchBase mImageView;
    private View mDoneButton;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private boolean mPaused = true;
    private int mMediaItemWidth, mMediaItemHeight;
    private float mImageViewScale;
    private int mImageSubsample;
    private Bitmap mBitmap;

    /**
     * The simple gestures listener
     */
    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mImageView.getScale() > 1F) {
                mImageView.postTranslateCenter(-distanceX, -distanceY);
                saveBitmapRectangle();
            }

            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Switch between the original scale and 3x scale.
            if (mImageView.getScale() > 2F) {
                mImageView.zoomTo(1F);
            } else {
                mImageView.zoomTo(3F, e.getX(), e.getY());
            }

            saveBitmapRectangle();
            return true;
        }
    }

    /**
     * Scale gesture listener
     */
    private class MyScaleGestureListener implements OnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float relativeScaleFactor = detector.getScaleFactor();
            final float newAbsoluteScale = relativeScaleFactor * mImageView.getScale();
            if (newAbsoluteScale < 1.0F) {
                return false;
            }

            mImageView.zoomTo(newAbsoluteScale, detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            saveBitmapRectangle();
        }
    }

    /**
     * Image loader class
     */
    private class ImageLoaderAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        // Instance variables
        private final String mFilename;

        /**
         * Constructor
         *
         * @param filename The filename
         */
        public ImageLoaderAsyncTask(String filename) {
            mFilename = filename;
            showProgress(true);
        }

        @Override
        protected Bitmap doInBackground(Void... zzz) {
            if (mPaused) {
                return null;
            }

            // Wait for the layout to complete
            while (mImageView.getWidth() <= 0) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ex) {
                }
            }

            if (mBitmap != null) {
                return mBitmap;
            } else {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = mImageSubsample;
                return BitmapFactory.decodeFile(mFilename, options);
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                if (!mPaused) {
                    finish();
                }
                return;
            }

            if (!mPaused) {
                showProgress(false);
                mRadioGroup.setEnabled(true);
                mImageView.setImageBitmapResetBase(bitmap, true);
                mBitmap = bitmap;
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + ", bytes: " + (bitmap.getRowBytes() * bitmap.getHeight()));
                }

                showBitmapRectangle();
            }
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.ken_burns_layout);
        setFinishOnTouchOutside(true);

        mMediaItemWidth = getIntent().getIntExtra(PARAM_WIDTH, 0);
        mMediaItemHeight = getIntent().getIntExtra(PARAM_HEIGHT, 0);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Media item size: " + mMediaItemWidth + "x" + mMediaItemHeight);
        }

        // Setup the image view
        mImageView = (ImageViewTouchBase)findViewById(R.id.ken_burns_image);

        // Set the width and height of the image view
        final FrameLayout.LayoutParams lp =
            (FrameLayout.LayoutParams)mImageView.getLayoutParams();
        if (mMediaItemWidth >= mMediaItemHeight) {
            lp.width = Math.min(mMediaItemWidth, MAX_WIDTH) / MAX_PAN;
            // Compute the height by preserving the aspect ratio
            lp.height = (lp.width * mMediaItemHeight) / mMediaItemWidth;
            mImageSubsample = mMediaItemWidth / (lp.width * MAX_PAN);
        } else {
            lp.height = Math.min(mMediaItemHeight, MAX_HEIGHT) / MAX_PAN;
            // Compute the width by preserving the aspect ratio
            lp.width = (lp.height * mMediaItemWidth) / mMediaItemHeight;
            mImageSubsample = mMediaItemHeight / (lp.height * MAX_PAN);
        }

        // Ensure that the size of the bitmap will not exceed the size supported
        // by HW vendors
        while ((mMediaItemWidth / mImageSubsample > MAX_HW_BITMAP_WIDTH) ||
                (mMediaItemHeight / mImageSubsample > MAX_HW_BITMAP_HEIGHT)) {
            mImageSubsample++;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "View size: " + lp.width + "x" + lp.height
                    + ", subsample: " + mImageSubsample);
        }

        // If the image is too small the image view may be too small to pinch
        if (lp.width < 120 || lp.height < 120) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Image is too small: " + lp.width + "x" + lp.height);
            }

            Toast.makeText(this, getString(R.string.pan_zoom_small_image_error),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mImageView.setLayoutParams(lp);
        mImageViewScale = ((float)lp.width) / ((float)mMediaItemWidth);

        mGestureDetector = new GestureDetector(this, new MyGestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(this, new MyScaleGestureListener());

        mRadioGroup = (RadioGroup)findViewById(R.id.which_rectangle);
        if (state != null) {
            mRadioGroup.check(state.getInt(STATE_WHICH_RECTANGLE_ID));
            mStartRect.set((Rect)state.getParcelable(STATE_START_RECTANGLE));
            mEndRect.set((Rect)state.getParcelable(STATE_END_RECTANGLE));
        } else {
            mRadioGroup.check(R.id.start_rectangle);
            final Rect startRect = (Rect)getIntent().getParcelableExtra(PARAM_START_RECT);
            if (startRect != null) {
                mStartRect.set(startRect);
            } else {
                mStartRect.set(0, 0, mMediaItemWidth, mMediaItemHeight);
            }

            final Rect endRect = (Rect)getIntent().getParcelableExtra(PARAM_END_RECT);
            if (endRect != null) {
                mEndRect.set(endRect);
            } else {
                mEndRect.set(0, 0, mMediaItemWidth, mMediaItemHeight);
            }
        }

        mDoneButton = findViewById(R.id.done);
        enableDoneButton();

        // Disable the ratio buttons until we load the image
        mRadioGroup.setEnabled(false);

        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.start_rectangle: {
                        showBitmapRectangle();
                        break;
                    }

                    case R.id.end_rectangle: {
                        showBitmapRectangle();
                        break;
                    }

                    case R.id.done: {
                        final Intent extra = new Intent();
                        extra.putExtra(PARAM_MEDIA_ITEM_ID,
                                getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));
                        extra.putExtra(PARAM_START_RECT, mStartRect);
                        extra.putExtra(PARAM_END_RECT, mEndRect);
                        setResult(RESULT_OK, extra);
                        finish();
                        break;
                    }

                    default: {
                        break;
                    }
                }
            }
        });

        mBitmap = (Bitmap) getLastNonConfigurationInstance();

        mImageView.setEventListener(new ImageViewTouchBase.ImageTouchEventListener() {
            @Override
            public boolean onImageTouchEvent(MotionEvent ev) {
                mScaleGestureDetector.onTouchEvent(ev);
                mGestureDetector.onTouchEvent(ev);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mPaused = false;
        // Load the image
        new ImageLoaderAsyncTask(getIntent().getStringExtra(PARAM_FILENAME)).execute();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mPaused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }

            System.gc();
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mBitmap;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        final RadioGroup radioGroup = (RadioGroup)findViewById(R.id.which_rectangle);

        outState.putInt(STATE_WHICH_RECTANGLE_ID, radioGroup.getCheckedRadioButtonId());
        outState.putParcelable(STATE_START_RECTANGLE, mStartRect);
        outState.putParcelable(STATE_END_RECTANGLE, mEndRect);
    }

    public void onClickHandler(View target) {
        switch (target.getId()) {
            case R.id.done: {
                final Intent extra = new Intent();
                extra.putExtra(PARAM_MEDIA_ITEM_ID,
                        getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));
                extra.putExtra(PARAM_START_RECT, mStartRect);
                extra.putExtra(PARAM_END_RECT, mEndRect);
                setResult(RESULT_OK, extra);
                finish();
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Show/hide the progress bar
     *
     * @param show true to show the progress
     */
    private void showProgress(boolean show) {
        if (show) {
            findViewById(R.id.image_loading).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.image_loading).setVisibility(View.GONE);
        }
    }

    /**
     * Enable the "Done" button if both rectangles are set
     */
    private void enableDoneButton() {
        mDoneButton.setEnabled(!mStartRect.isEmpty() && !mEndRect.isEmpty());
    }

    /**
     * Show the bitmap rectangle
     */
    private void showBitmapRectangle() {
        final int checkedRect = mRadioGroup.getCheckedRadioButtonId();
        switch (checkedRect) {
            case R.id.start_rectangle: {
                if (!mStartRect.isEmpty()) {
                    mImageView.reset();
                    final float scale = ((float)mMediaItemWidth)
                            / ((float)(mStartRect.right - mStartRect.left));
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "showBitmapRectangle START: " + scale + " "
                                + mStartRect.left + ", " + mStartRect.top + ", "
                                + mStartRect.right + ", " + mStartRect.bottom);
                    }
                    if (scale > 1F) {
                        mImageView.zoomToOffset(scale, mStartRect.left * scale * mImageViewScale,
                                mStartRect.top * scale * mImageViewScale);
                    }
                }
                break;
            }

            case R.id.end_rectangle: {
                if (!mEndRect.isEmpty()) {
                    mImageView.reset();
                    final float scale = ((float)mMediaItemWidth)
                            / ((float)(mEndRect.right - mEndRect.left));
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "showBitmapRectangle END: " + scale + " "
                                + mEndRect.left + ", " + mEndRect.top + ", "
                                + mEndRect.right + ", " + mEndRect.bottom);
                    }
                    if (scale > 1F) {
                        mImageView.zoomToOffset(scale, mEndRect.left * scale * mImageViewScale,
                                mEndRect.top * scale * mImageViewScale);
                    }
                }
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Show the bitmap rectangle
     */
    private void saveBitmapRectangle() {
        final int checkedRect = mRadioGroup.getCheckedRadioButtonId();
        final FrameLayout.LayoutParams lp =
            (FrameLayout.LayoutParams)mImageView.getLayoutParams();
        switch (checkedRect) {
            case R.id.start_rectangle: {
                mMatrixRect.set(0, 0, lp.width, lp.height);

                mImageView.mapRect(mMatrixRect);
                final float scale = mImageView.getScale();

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "START RAW: " + scale + ", rect: " + mMatrixRect.left
                            + ", " + mMatrixRect.top + ", " + mMatrixRect.right
                            + ", " + mMatrixRect.bottom);
                }

                final int left = (int)((-mMatrixRect.left/scale) / mImageViewScale);
                final int top = (int)((-mMatrixRect.top/scale) / mImageViewScale);
                final int right = (int)(((-mMatrixRect.left + lp.width)/scale) / mImageViewScale);
                final int bottom = (int)(((-mMatrixRect.top + lp.height)/scale) / mImageViewScale);

                mStartRect.set(left, top, right, bottom);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "START: " + mStartRect.left + ", " + mStartRect.top + ", "
                            + mStartRect.right + ", " + mStartRect.bottom);
                }

                enableDoneButton();
                break;
            }

            case R.id.end_rectangle: {
                mMatrixRect.set(0, 0, lp.width, lp.height);

                mImageView.mapRect(mMatrixRect);
                final float scale = mImageView.getScale();

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "END RAW: " + scale + ", rect: " + mMatrixRect.left
                            + ", " + mMatrixRect.top + ", " + mMatrixRect.right
                            + ", " + mMatrixRect.bottom);
                }

                final int left = (int)((-mMatrixRect.left/scale) / mImageViewScale);
                final int top = (int)((-mMatrixRect.top/scale) / mImageViewScale);
                final int right = (int)(((-mMatrixRect.left + lp.width)/scale) / mImageViewScale);
                final int bottom = (int)(((-mMatrixRect.top + lp.height)/scale) / mImageViewScale);

                mEndRect.set(left, top, right, bottom);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "END: " + mEndRect.left + ", " + mEndRect.top + ", "
                            + mEndRect.right + ", " + mEndRect.bottom);
                }

                enableDoneButton();
                break;
            }

            default: {
                break;
            }
        }
    }
}
