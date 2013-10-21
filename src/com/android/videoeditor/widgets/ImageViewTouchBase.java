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

package com.android.videoeditor.widgets;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

/**
 * An image view which can be panned and zoomed.
 */
public class ImageViewTouchBase extends ImageView {
    private static final float SCALE_RATE = 1.25F;
    // Zoom scale is applied after the transform that fits the image screen,
    // so 1.0 is a perfect fit and it doesn't make sense to allow smaller
    // values.
    private static final float MIN_ZOOM_SCALE = 1.0f;

    // This is the base transformation which is used to show the image
    // initially.  The current computation for this shows the image in
    // it's entirety, letterboxing as needed.  One could choose to
    // show the image as cropped instead.
    //
    // This matrix is recomputed when we go from the thumbnail image to
    // the full size image.
    private Matrix mBaseMatrix = new Matrix();

    // This is the supplementary transformation which reflects what
    // the user has done in terms of zooming and panning.
    //
    // This matrix remains the same when we go from the thumbnail image
    // to the full size image.
    private Matrix mSuppMatrix = new Matrix();

    // This is the final matrix which is computed as the concatenation
    // of the base matrix and the supplementary matrix.
    private final Matrix mDisplayMatrix = new Matrix();

    // Temporary buffer used for getting the values out of a matrix.
    private final float[] mMatrixValues = new float[9];

    // The current bitmap being displayed.
    private Bitmap mBitmapDisplayed;

    // The width and height of the view
    private int mThisWidth = -1, mThisHeight = -1;

    private boolean mStretch = true;
    // The zoom scale
    private float mMaxZoom;
    private Runnable mOnLayoutRunnable = null;
    private ImageTouchEventListener mEventListener;

    /**
     * Touch interface
     */
    public interface ImageTouchEventListener {
        public boolean onImageTouchEvent(MotionEvent ev);
    }
    /**
     * Constructor
     *
     * @param context The context
     */
    public ImageViewTouchBase(Context context) {
        super(context);
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    /**
     * Constructor
     *
     * @param context The context
     * @param attrs The attributes
     */
    public ImageViewTouchBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    /**
     * Constructor
     *
     * @param context The context
     * @param attrs The attributes
     * @param defStyle The default style
     */
    public ImageViewTouchBase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        mThisWidth = right - left;
        mThisHeight = bottom - top;
        final Runnable r = mOnLayoutRunnable;
        if (r != null) {
            mOnLayoutRunnable = null;
            r.run();
        } else {
            if (mBitmapDisplayed != null) {
                getProperBaseMatrix(mBitmapDisplayed, mBaseMatrix);
                setImageMatrix(getImageViewMatrix());
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mEventListener != null) {
            return mEventListener.onImageTouchEvent(ev);
        } else {
            return false;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);

        final Drawable d = getDrawable();
        if (d != null) {
            d.setDither(true);
        }

        mBitmapDisplayed = bitmap;
    }

    /**
     * @param listener The listener
     */
    public void setEventListener(ImageTouchEventListener listener) {
        mEventListener = listener;
    }

    /**
     * @return The image bitmap
     */
    public Bitmap getImageBitmap() {
        return mBitmapDisplayed;
    }

    /**
     * If the view has not yet been measured delay the method
     *
     * @param bitmap The bitmap
     * @param resetSupp true to reset the transform matrix
     */
    public void setImageBitmapResetBase(final Bitmap bitmap, final boolean resetSupp) {
        mStretch = true;
        final int viewWidth = getWidth();
        if (viewWidth <= 0) {
            mOnLayoutRunnable = new Runnable() {
                @Override
                public void run() {
                    setImageBitmapResetBase(bitmap, resetSupp);
                }
            };
            return;
        }

        if (bitmap != null) {
            getProperBaseMatrix(bitmap, mBaseMatrix);
            setImageBitmap(bitmap);
        } else {
            mBaseMatrix.reset();
            setImageBitmap(null);
        }

        if (resetSupp) {
            mSuppMatrix.reset();
        }

        setImageMatrix(getImageViewMatrix());
        mMaxZoom = maxZoom();
    }

    /**
     * Reset the transform of the current image
     */
    public void reset() {
        if (mBitmapDisplayed != null) {
            setImageBitmapResetBase(mBitmapDisplayed, true);
        }
    }

    /**
     * Pan
     *
     * @param dx The horizontal offset
     * @param dy The vertical offset
     */
    public void postTranslateCenter(float dx, float dy) {
        mSuppMatrix.postTranslate(dx, dy);

        center(true, true);
    }

    /**
     * Pan by the specified horizontal and vertical amount
     *
     * @param dx Pan by this horizontal amount
     * @param dy Pan by this vertical amount
     */
    private void panBy(float dx, float dy) {
        mSuppMatrix.postTranslate(dx, dy);

        setImageMatrix(getImageViewMatrix());
    }

    /**
     * @return The scale
     */
    public float getScale() {
        return getValue(mSuppMatrix, Matrix.MSCALE_X);
    }

    /**
     * @param rect The input/output rectangle
     */
    public void mapRect(RectF rect) {
        mSuppMatrix.mapRect(rect);
    }

    /**
     * Setup the base matrix so that the image is centered and scaled properly.
     *
     * @param bitmap The bitmap
     * @param matrix The matrix
     */
    private void getProperBaseMatrix(Bitmap bitmap, Matrix matrix) {
        final float viewWidth = getWidth();
        final float viewHeight = getHeight();

        final float w = bitmap.getWidth();
        final float h = bitmap.getHeight();
        matrix.reset();

        if (mStretch) {
            // We limit up-scaling to 10x otherwise the result may look bad if
            // it's a small icon.
            float widthScale = Math.min(viewWidth / w, 10.0f);
            float heightScale = Math.min(viewHeight / h, 10.0f);
            float scale = Math.min(widthScale, heightScale);
            matrix.postScale(scale, scale);
            matrix.postTranslate((viewWidth - w * scale) / 2F, (viewHeight - h * scale) / 2F);
        } else {
            matrix.postTranslate((viewWidth - w) / 2F, (viewHeight - h) / 2F);
        }
    }

    /**
     * Combine the base matrix and the supp matrix to make the final matrix.
     */
    private Matrix getImageViewMatrix() {
        // The final matrix is computed as the concatenation of the base matrix
        // and the supplementary matrix.
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    /**
     * @return The maximum zoom
     */
    public float getMaxZoom() {
        return mMaxZoom;
    }

    /**
     * Sets the maximum zoom, which is a scale relative to the base matrix. It
     * is calculated to show the image at 400% zoom regardless of screen or
     * image orientation. If in the future we decode the full 3 megapixel
     * image, rather than the current 1024x768, this should be changed down
     * to 200%.
     */
    private float maxZoom() {
        if (mBitmapDisplayed == null) {
            return 1F;
        }

        final float fw = (float)mBitmapDisplayed.getWidth() / mThisWidth;
        final float fh = (float)mBitmapDisplayed.getHeight() / mThisHeight;

        return Math.max(fw, fh) * 4;
    }

    /**
     * Sets the maximum zoom, which is a scale relative to the base matrix. It
     * is calculated to show the image at 400% zoom regardless of screen or
     * image orientation. If in the future we decode the full 3 megapixel
     * image, rather than the current 1024x768, this should be changed down
     * to 200%.
     */
    public static float maxZoom(int bitmapWidth, int bitmapHeight, int viewWidth, int viewHeight) {
        final float fw = (float)bitmapWidth / viewWidth;
        final float fh = (float)bitmapHeight / viewHeight;

        return Math.max(fw, fh) * 4;
    }

    /**
     * Ensure the scale factor is within limits
     *
     * @param scale The scale factor
     *
     * @return The corrected scaled factor
     */
    private float correctedZoomScale(float scale) {
        float result = scale;
        if (result > mMaxZoom) {
            result = mMaxZoom;
        } else if (result < MIN_ZOOM_SCALE) {
            result = MIN_ZOOM_SCALE;
        }

        return result;
    }

    /**
     * Zoom to the specified scale factor
     *
     * @param scale The scale factor
     * @param centerX The horizontal center
     * @param centerY The vertical center
     */
    public void zoomTo(float scale, float centerX, float centerY) {
        float correctedScale = correctedZoomScale(scale);

        float oldScale = getScale();
        float deltaScale = correctedScale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center(true, true);
    }

    /**
     * Zoom to the specified scale factor
     *
     * @param scale The scale factor
     */
    public void zoomTo(float scale) {
        final float cx = getWidth() / 2F;
        final float cy = getHeight() / 2F;

        zoomTo(scale, cx, cy);
    }

    /**
     * Zoom to the specified scale factor and center point
     *
     * @param scale The scale factor
     * @param pointX The horizontal position
     * @param pointY The vertical position
     */
    public void zoomToPoint(float scale, float pointX, float pointY) {
        final float cx = getWidth() / 2F;
        final float cy = getHeight() / 2F;

        panBy(cx - pointX, cy - pointY);
        zoomTo(scale, cx, cy);
    }

    /**
     * Zoom to the specified scale factor and point
     *
     * @param scale The scale factor
     * @param pointX The horizontal position
     * @param pointY The vertical position
     */
    public void zoomToOffset(float scale, float pointX, float pointY) {

        float correctedScale = correctedZoomScale(scale);

        float oldScale = getScale();
        float deltaScale = correctedScale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale);
        setImageMatrix(getImageViewMatrix());

        panBy(-pointX, -pointY);
    }

    /**
     * Zoom in by a preset scale rate
     */
    public void zoomIn() {
        zoomIn(SCALE_RATE);
    }

    /**
     * Zoom in by the specified scale rate
     *
     * @param rate The scale rate
     */
    public void zoomIn(float rate) {
        if (getScale() < mMaxZoom && mBitmapDisplayed != null) {
            float cx = getWidth() / 2F;
            float cy = getHeight() / 2F;

            mSuppMatrix.postScale(rate, rate, cx, cy);
            setImageMatrix(getImageViewMatrix());
        }
    }

    /**
     * Zoom out by a preset scale rate
     */
    public void zoomOut() {
        zoomOut(SCALE_RATE);
    }

    /**
     * Zoom out by the specified scale rate
     *
     * @param rate The scale rate
     */
    public void zoomOut(float rate) {
        if (getScale() > MIN_ZOOM_SCALE && mBitmapDisplayed != null) {
            float cx = getWidth() / 2F;
            float cy = getHeight() / 2F;

            // Zoom out to at most 1x.
            Matrix tmp = new Matrix(mSuppMatrix);
            tmp.postScale(1F / rate, 1F / rate, cx, cy);

            if (getValue(tmp, Matrix.MSCALE_X) < 1F) {
                mSuppMatrix.setScale(1F, 1F, cx, cy);
            } else {
                mSuppMatrix.postScale(1F / rate, 1F / rate, cx, cy);
            }
            setImageMatrix(getImageViewMatrix());
            center(true, true);
        }
    }

    /**
     * Center as much as possible in one or both axis. Centering is
     * defined as follows: if the image is scaled down below the
     * view's dimensions then center it (literally). If the image
     * is scaled larger than the view and is translated out of view
     * then translate it back into view (i.e. eliminate black bars).
     */
    private void center(boolean horizontal, boolean vertical) {
        if (mBitmapDisplayed == null) {
            return;
        }

        final Matrix m = getImageViewMatrix();
        final RectF rect = new RectF(0, 0, mBitmapDisplayed.getWidth(),
                mBitmapDisplayed.getHeight());

        m.mapRect(rect);

        final float height = rect.height();
        final float width = rect.width();
        float deltaX = 0, deltaY = 0;

        if (vertical) {
            int viewHeight = getHeight();
            if (height < viewHeight) {
                deltaY = (viewHeight - height) / 2 - rect.top;
            } else if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = getHeight() - rect.bottom;
            }
        }

        if (horizontal) {
            int viewWidth = getWidth();
            if (width < viewWidth) {
                deltaX = (viewWidth - width) / 2 - rect.left;
            } else if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }
        }

        mSuppMatrix.postTranslate(deltaX, deltaY);

        setImageMatrix(getImageViewMatrix());
    }

    /**
     * Get a matrix transform value
     *
     * @param matrix The matrix
     * @param whichValue Which value
     * @return The value
     */
    private float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }
}
