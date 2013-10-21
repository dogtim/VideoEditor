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

package com.android.videoeditor.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Math;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.util.Log;

import com.android.videoeditor.R;
import com.android.videoeditor.service.MovieOverlay;

/**
 * Image utility methods
 */
public class ImageUtils {
    /**
     *  Logging
     */
    private static final String TAG = "ImageUtils";

    // The resize paint
    private static final Paint sResizePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    // The match aspect ratio mode for scaleImage
    public static int MATCH_SMALLER_DIMENSION = 1;
    public static int MATCH_LARGER_DIMENSION = 2;

    /**
     * It is not possible to instantiate this class
     */
    private ImageUtils() {
    }

    /**
     * Resize a bitmap to the specified width and height.
     *
     * @param filename The filename
     * @param width The thumbnail width
     * @param height The thumbnail height
     * @param match MATCH_SMALLER_DIMENSION or MATCH_LARGER_DIMMENSION
     *
     * @return The resized bitmap
     */
    public static Bitmap scaleImage(String filename, int width, int height, int match)
            throws IOException {
        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, dbo);

        final int nativeWidth = dbo.outWidth;
        final int nativeHeight = dbo.outHeight;

        final Bitmap srcBitmap;
        float scaledWidth, scaledHeight;
        final BitmapFactory.Options options = new BitmapFactory.Options();
        if (nativeWidth > width || nativeHeight > height) {
            float dx = ((float) nativeWidth) / ((float) width);
            float dy = ((float) nativeHeight) / ((float) height);
            float scale = (match == MATCH_SMALLER_DIMENSION) ? Math.max(dx,dy) : Math.min(dx,dy);
            scaledWidth = nativeWidth / scale;
            scaledHeight = nativeHeight / scale;
            // Create the bitmap from file.
            options.inSampleSize = (scale > 1.0f) ? ((int) scale) : 1;
       } else {
            scaledWidth = width;
            scaledHeight = height;
            options.inSampleSize = 1;
       }

       srcBitmap = BitmapFactory.decodeFile(filename, options);
       if (srcBitmap == null) {
         throw new IOException("Cannot decode file: " + filename);
       }

       // Create the canvas bitmap.
       final Bitmap bitmap = Bitmap.createBitmap(Math.round(scaledWidth),
               Math.round(scaledHeight),
               Bitmap.Config.ARGB_8888);
       final Canvas canvas = new Canvas(bitmap);
       canvas.drawBitmap(srcBitmap,
               new Rect(0, 0, srcBitmap.getWidth(), srcBitmap.getHeight()),
               new Rect(0, 0, Math.round(scaledWidth), Math.round(scaledHeight)),
               sResizePaint);

       // Release the source bitmap
       srcBitmap.recycle();
       return bitmap;
    }

    /**
     * Rotate a JPEG according to the EXIF data
     *
     * @param inputFilename The name of the input file (must be a JPEG filename)
     * @param outputFile The rotated file
     *
     * @return true if the image was rotated
     */
    public static boolean transformJpeg(String inputFilename, File outputFile)
            throws IOException {
        final ExifInterface exif = new ExifInterface(inputFilename);
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Exif orientation: " + orientation);
        }

        // Degrees by which we rotate the image.
        int degrees = 0;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: {
                degrees = 90;
                break;
            }

            case ExifInterface.ORIENTATION_ROTATE_180: {
                degrees = 180;
                break;
            }

            case ExifInterface.ORIENTATION_ROTATE_270: {
                degrees = 270;
                break;
            }
        }
        rotateAndScaleImage(inputFilename, degrees, outputFile);
        return degrees != 0;
    }

    /**
     * Rotates an image according to the specified {@code orientation}.
     * We limit the number of pixels of the scaled image. Thus the image
     * will typically be downsampled.
     *
     * @param inputFilename The input filename
     * @param orientation The rotation angle
     * @param outputFile The output file
     */
    private static void rotateAndScaleImage(String inputFilename, int orientation, File outputFile)
            throws FileNotFoundException, IOException {
        // In order to avoid OutOfMemoryError when rotating the image, we scale down the size of the
        // input image. We set the maxmimum number of allowed pixels to 2M and scale down the image
        // accordingly.

        // Determine width and height of the original bitmap without allocating memory for it,
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(inputFilename, opt);

        // Determine the scale factor based on the ratio of pixel count over max allowed pixels.
        final int width = opt.outWidth;
        final int height = opt.outHeight;
        final int pixelCount = width * height;
        final int MAX_PIXELS_FOR_SCALED_IMAGE = 2000000;
        double scale = Math.sqrt( (double) pixelCount / MAX_PIXELS_FOR_SCALED_IMAGE);
        if (scale <= 1) {
          scale = 1;
        } else {
          // Make the scale factor a power of 2 for faster processing. Also the resulting bitmap may
          // have different dimensions than what has been requested if the scale factor is not a
          // power of 2.
          scale = nextPowerOf2((int) Math.ceil(scale));
        }

        // Load the scaled image.
        BitmapFactory.Options opt2 = new BitmapFactory.Options();
        opt2.inSampleSize = (int) scale;
        final Bitmap scaledBmp = BitmapFactory.decodeFile(inputFilename, opt2);

        // Rotation matrix used to rotate the image.
        final Matrix mtx = new Matrix();
        mtx.postRotate(orientation);

        final Bitmap rotatedBmp = Bitmap.createBitmap(scaledBmp, 0, 0,
                scaledBmp.getWidth(), scaledBmp.getHeight(), mtx, true);
        scaledBmp.recycle();

        // Save the rotated image to a file in the current project folder
        final FileOutputStream fos = new FileOutputStream(outputFile);
        rotatedBmp.compress(CompressFormat.JPEG, 100, fos);
        fos.close();

        rotatedBmp.recycle();
    }

    /**
     * Returns the next power of two.
     * Returns the input if it is already power of 2.
     * Throws IllegalArgumentException if the input is <= 0 or the answer overflows.
     */
    private static int nextPowerOf2(int n) {
        if (n <= 0 || n > (1 << 30)) throw new IllegalArgumentException();
        n -= 1;
        n |= n >> 16;
        n |= n >> 8;
        n |= n >> 4;
        n |= n >> 2;
        n |= n >> 1;
        return n + 1;
    }

    /**
     * Build an overlay image
     *
     * @param context The context
     * @param inputBitmap If the bitmap is provided no not create a new one
     * @param overlayType The overlay type
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     *
     * @return The bitmap
     */
    public static Bitmap buildOverlayBitmap(Context context, Bitmap inputBitmap, int overlayType,
            String title, String subTitle, int width, int height) {
        final Bitmap overlayBitmap;
        if (inputBitmap == null) {
            overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } else {
            overlayBitmap = inputBitmap;
        }

        overlayBitmap.eraseColor(Color.TRANSPARENT);
        final Canvas canvas = new Canvas(overlayBitmap);

        switch (overlayType) {
            case MovieOverlay.OVERLAY_TYPE_CENTER_1: {
                drawCenterOverlay(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_BOTTOM_1: {
                drawBottomOverlay(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_CENTER_2: {
                drawCenterOverlay(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_BOTTOM_2: {
                drawBottomOverlay(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, width, height);
                break;
            }

            default: {
                throw new IllegalArgumentException("Unsupported overlay type: " + overlayType);
            }
        }

        return overlayBitmap;
    }

    /**
     * Build an overlay image in the center third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param textColor The text color
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawCenterOverlay(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int width, int height) {
        final int INSET = width / 72;
        final int startHeight = (height / 3) + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(INSET, startHeight, width - INSET,
                ((2 * height) / 3) - INSET);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 12;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 6);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }

    /**
     * Build an overlay image in the lower third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param textColor The text color
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawBottomOverlay(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int width, int height) {
        final int INSET = width / 72;
        final int startHeight = ((2 * height) / 3) + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(INSET, startHeight, width - INSET, height - INSET);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 12;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 6);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }

    /**
     * Build an overlay preview image
     *
     * @param context The context
     * @param canvas The canvas
     * @param overlayType The overlay type
     * @param title The title
     * @param subTitle The subtitle
     * @param startX The start horizontal position
     * @param startY The start vertical position
     * @param width The width
     * @param height The height
     */
    public static void buildOverlayPreview(Context context, Canvas canvas, int overlayType,
            String title, String subTitle, int startX, int startY, int width, int height) {
        switch (overlayType) {
            case MovieOverlay.OVERLAY_TYPE_CENTER_1:
            case MovieOverlay.OVERLAY_TYPE_BOTTOM_1: {
                drawOverlayPreview(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, startX, startY, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_CENTER_2:
            case MovieOverlay.OVERLAY_TYPE_BOTTOM_2: {
                drawOverlayPreview(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, startX, startY, width, height);
                break;
            }

            default: {
                throw new IllegalArgumentException("Unsupported overlay type: " + overlayType);
            }
        }
    }

    /**
     * Build an overlay image in the lower third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawOverlayPreview(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int startX, int startY, int width,
            int height) {
        final int INSET = 0;
        final int startHeight = startY + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(startX + INSET, startHeight, startX + width - INSET,
                height - INSET + startY);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 4;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 2);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }
}
