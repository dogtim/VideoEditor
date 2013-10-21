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

package com.android.videoeditor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.media.videoeditor.VideoEditor;
import android.os.AsyncTask;
import android.text.format.DateUtils;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.ImageUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;


public class ProjectPickerAdapter extends BaseAdapter {
    private Context mContext;
    private Resources mResources;
    private LayoutInflater mInflater;
    private List<VideoEditorProject> mProjects;
    private int mItemWidth;
    private int mItemHeight;
    private int mOverlayHeight;
    private int mOverlayVerticalInset;
    private int mOverlayHorizontalInset;
    private LruCache<String, Bitmap> mPreviewBitmapCache;

    public ProjectPickerAdapter(Context context, LayoutInflater inflater,
            List<VideoEditorProject> projects) {
        mContext = context;
        mResources = context.getResources();
        mInflater = inflater;
        mProjects = projects;
        mItemWidth = (int) mResources.getDimension(R.dimen.project_picker_item_width);
        mItemHeight = (int) mResources.getDimension(R.dimen.project_picker_item_height);
        mOverlayHeight = (int) mResources.getDimension(
                R.dimen.project_picker_item_overlay_height);
        mOverlayVerticalInset = (int) mResources.getDimension(
                R.dimen.project_picker_item_overlay_vertical_inset);
        mOverlayHorizontalInset = (int) mResources.getDimension(
                R.dimen.project_picker_item_overlay_horizontal_inset);
        // Limit the cache size to 15 thumbnails.
        mPreviewBitmapCache = new LruCache<String, Bitmap>(15);
    }

    /**
     * Clears project list and update display.
     */
    public void clear() {
        mPreviewBitmapCache.evictAll();
        mProjects.clear();
        notifyDataSetChanged();
    }

    /**
     * Removes the project with specified {@code projectPath} from the project list and updates the
     * display.
     *
     * @param projectPath The project path of the to-be-removed project
     * @return {@code true} if the project is successfully removed,
     *      {@code false} if no removal happened
     */
    public boolean remove(String projectPath) {
        for (VideoEditorProject project : mProjects) {
            if (project.getPath().equals(projectPath)) {
                if (mProjects.remove(project)) {
                    notifyDataSetChanged();
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public int getCount() {
        // Add one to represent an additional dummy project for "create new project" option.
        return mProjects.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == mProjects.size()) {
            return null;
        }
        return mProjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Inflate a new view with project thumbnail and information.
        // We never reuse convertView because we load thumbnails asynchronously
        // and hook an async task with the new view. If the new view is reused
        // as a convertView, the async task might put a wrong thumbnail on it.
        View v = mInflater.inflate(R.layout.project_picker_item, null);
        ImageView iv = (ImageView) v.findViewById(R.id.thumbnail);
        Bitmap thumbnail;
        String title;
        String duration;
        if (position == mProjects.size()) {
            title = mContext.getString(R.string.projects_new_project);
            duration = "";
            thumbnail = renderNewProjectThumbnail();
        } else {
            VideoEditorProject project = mProjects.get(position);
            title = project.getName();
            if (title == null) {
                title = "";
            }
            duration = millisecondsToTimeString(project.getProjectDuration());
            thumbnail = getThumbnail(project.getPath(), iv, title, duration);
        }

        if (thumbnail != null) {
            drawBottomOverlay(thumbnail, title, duration);
            iv.setImageBitmap(thumbnail);
        }

        return v;
    }

    /**
     * Draws transparent black bottom overlay with movie title and duration on the bitmap.
     */
    public void drawBottomOverlay(Bitmap bitmap, String title, String duration) {
        // Draw overlay at the bottom of the canvas.
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setAlpha(128);
        final int left = 0, top = bitmap.getHeight() - mOverlayHeight,
                right = bitmap.getWidth(), bottom = bitmap.getHeight();
        canvas.drawRect(left, top, right, bottom, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize((int) mResources.getDimension(R.dimen.project_picker_item_font_size));

        // Draw movie title at the left of the overlay. Trim title if it is going to overlap with
        // duration text.
        final int availableTitleWidth = bitmap.getWidth() - (int) paint.measureText(duration);
        title = TextUtils.ellipsize(title, new TextPaint(paint), availableTitleWidth,
                TextUtils.TruncateAt.END).toString();
        canvas.drawText(title, mOverlayHorizontalInset,
                bitmap.getHeight() - mOverlayHeight + mOverlayVerticalInset,
                paint);

        // Draw movie duration at the right of the overlay.
        canvas.drawText(duration,
                bitmap.getWidth() - paint.measureText(duration) - mOverlayHorizontalInset,
                bitmap.getHeight() - mOverlayHeight + mOverlayVerticalInset,
                paint);
    }

    private Bitmap getThumbnail(String projectPath, ImageView imageView, String title,
            String duration) {
        Bitmap previewBitmap = mPreviewBitmapCache.get(projectPath);
        if (previewBitmap == null) {
            // Cache miss: asynchronously load bitmap to avoid scroll stuttering
            // in the project picker.
            new LoadPreviewBitmapTask(this, projectPath, imageView, mItemWidth, mItemHeight,
                    title, duration, mPreviewBitmapCache).execute();
        } else {
            return previewBitmap;
        }

        return null;
    }

    private Bitmap renderNewProjectThumbnail() {
        final Bitmap bitmap = Bitmap.createBitmap(mItemWidth, mItemHeight,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        canvas.drawRect(0, 0, mItemWidth, mItemHeight, paint);

        paint.setTextSize(18.0f);
        paint.setAlpha(255);
        final Bitmap newProjectIcon = BitmapFactory.decodeResource(mResources,
                R.drawable.add_video_project_big);
        final int x = (mItemWidth - newProjectIcon.getWidth()) / 2;
        final int y = (mItemHeight - newProjectIcon.getHeight()) / 2;
        canvas.drawBitmap(newProjectIcon, x, y, paint);
        newProjectIcon.recycle();

        return bitmap;
    }

    /**
     * Converts milliseconds into the string time format HH:mm:ss.
     */
    private String millisecondsToTimeString(long milliseconds) {
        return DateUtils.formatElapsedTime(milliseconds / 1000);
    }
}

/**
 * Worker that loads preview bitmap for a project,
 */
class LoadPreviewBitmapTask extends AsyncTask<Void, Void, Bitmap> {
    // Handle to the adapter that initiates this async task.
    private ProjectPickerAdapter mContextAdapter;
    private String mProjectPath;
    // Handle to the image view we should update when the preview bitmap is loaded.
    private ImageView mImageView;
    private int mWidth;
    private int mHeight;
    private String mTitle;
    private String mDuration;
    private LruCache<String, Bitmap> mPreviewBitmapCache;

    public LoadPreviewBitmapTask(ProjectPickerAdapter contextAdapter, String projectPath,
            ImageView imageView, int width, int height, String title, String duration,
            LruCache<String, Bitmap> previewBitmapCache) {
        mContextAdapter = contextAdapter;
        mProjectPath = projectPath;
        mImageView = imageView;
        mWidth = width;
        mHeight = height;
        mTitle = title;
        mDuration = duration;
        mPreviewBitmapCache = previewBitmapCache;
    }

    @Override
    protected Bitmap doInBackground(Void... param) {
        final File thumbnail = new File(mProjectPath, VideoEditor.THUMBNAIL_FILENAME);
        // Return early if thumbnail does not exist.
        if (!thumbnail.exists()) {
            return null;
        }

        try {
            final Bitmap previewBitmap = ImageUtils.scaleImage(
                    thumbnail.getAbsolutePath(),
                    mWidth,
                    mHeight,
                    ImageUtils.MATCH_LARGER_DIMENSION);
            if (previewBitmap != null) {
                final Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

                // Draw bitmap at the center of the canvas.
                canvas.drawBitmap(previewBitmap,
                        (mWidth - previewBitmap.getWidth()) / 2,
                        (mHeight - previewBitmap.getHeight()) / 2,
                        paint);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (result == null) {
            // If we don't have thumbnail, default to a black canvas.
            result = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            result.eraseColor(Color.BLACK);
        } else {
            mPreviewBitmapCache.put(mProjectPath, result);
        }

        // Update the image view.
        mContextAdapter.drawBottomOverlay(result, mTitle, mDuration);
        mImageView.setImageBitmap(result);
    }
}
