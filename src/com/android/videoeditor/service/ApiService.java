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

package com.android.videoeditor.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.videoeditor.AudioTrack;
import android.media.videoeditor.Effect;
import android.media.videoeditor.EffectColor;
import android.media.videoeditor.EffectKenBurns;
import android.media.videoeditor.ExtractAudioWaveformProgressListener;
import android.media.videoeditor.MediaImageItem;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.MediaProperties;
import android.media.videoeditor.MediaVideoItem;
import android.media.videoeditor.Overlay;
import android.media.videoeditor.OverlayFrame;
import android.media.videoeditor.Transition;
import android.media.videoeditor.TransitionAlpha;
import android.media.videoeditor.TransitionCrossfade;
import android.media.videoeditor.TransitionFadeBlack;
import android.media.videoeditor.TransitionSliding;
import android.media.videoeditor.VideoEditor;
import android.media.videoeditor.VideoEditorFactory;
import android.media.videoeditor.WaveformData;
import android.media.videoeditor.MediaItem.GetThumbnailListCallback;
import android.media.videoeditor.VideoEditor.ExportProgressListener;
import android.media.videoeditor.VideoEditor.MediaProcessingProgressListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;

import com.android.videoeditor.R;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.util.ImageUtils;
import com.android.videoeditor.util.MediaItemUtils;
import com.android.videoeditor.util.StringUtils;

/**
 * VideoEditor service API
 */
public class ApiService extends Service {
    // Logging
    private static final String TAG = "VEApiService";

    // Additional updates
    public static final int ACTION_UPDATE_FRAME = MediaProcessingProgressListener.ACTION_DECODE + 100;
    public static final int ACTION_NO_FRAME_UPDATE = MediaProcessingProgressListener.ACTION_DECODE + 101;

    // Parameters
    private static final String PARAM_OP = "op";
    private static final String PARAM_REQUEST_ID = "rid";
    private static final String PARAM_PROJECT_PATH = "project";
    private static final String PARAM_FILENAME = "filename";
    private static final String PARAM_STORYBOARD_ITEM_ID = "item_id";
    private static final String PARAM_RELATIVE_STORYBOARD_ITEM_ID = "r_item_id";
    private static final String PARAM_PROGRESS_VALUE = "prog_value";
    private static final String PARAM_EXCEPTION = "ex";
    private static final String PARAM_START_TIME = "s_time";
    private static final String PARAM_END_TIME = "e_time";
    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_WIDTH = "width";
    private static final String PARAM_HEIGHT = "height";
    private static final String PARAM_BITRATE = "bitrate";
    private static final String PARAM_MEDIA_ITEM_RENDERING_MODE = "rm";
    private static final String PARAM_MEDIA_ITEM_START_RECT = "start_rect";
    private static final String PARAM_MEDIA_ITEM_END_RECT = "end_rect";
    private static final String PARAM_EFFECT_TYPE = "e_type";
    private static final String PARAM_EFFECT_PARAM = "e_param";
    private static final String PARAM_TRANSITION_BEHAVIOR = "behavior";
    private static final String PARAM_TRANSITION_MASK = "t_mask";
    private static final String PARAM_TRANSITION_BLENDING = "t_blending";
    private static final String PARAM_TRANSITION_INVERT = "t_invert";
    private static final String PARAM_TRANSITION_DIRECTION = "t_dir";
    private static final String PARAM_INTENT = "req_intent";
    private static final String PARAM_PROJECT_NAME = "name";
    private static final String PARAM_MOVIES_FILENAMES = "movies";
    private static final String PARAM_PHOTOS_FILENAMES = "images";
    private static final String PARAM_ASPECT_RATIO = "aspect_ratio";
    private static final String PARAM_BEGIN_BOUNDARY = "b_boundary";
    private static final String PARAM_END_BOUNDARY = "e_boundary";
    private static final String PARAM_ATTRIBUTES = "attributes";
    private static final String PARAM_VOLUME = "volume";
    private static final String PARAM_LOOP = "loop";
    private static final String PARAM_MUTE = "mute";
    private static final String PARAM_DUCK = "duck";
    private static final String PARAM_MOVIE_URI = "uri";
    private static final String PARAM_THEME = "theme";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_COUNT = "count";
    private static final String PARAM_TOKEN = "token";
    private static final String PARAM_INDICES = "indices";
    private static final String PARAM_CANCELLED = "cancelled";

    // Operations
    private static final int OP_VIDEO_EDITOR_CREATE = 1;
    private static final int OP_VIDEO_EDITOR_LOAD = 2;
    private static final int OP_VIDEO_EDITOR_SAVE = 3;
    private static final int OP_VIDEO_EDITOR_EXPORT = 4;
    private static final int OP_VIDEO_EDITOR_CANCEL_EXPORT = 5;
    private static final int OP_VIDEO_EDITOR_EXPORT_STATUS = 6;
    private static final int OP_VIDEO_EDITOR_RELEASE = 8;
    private static final int OP_VIDEO_EDITOR_DELETE = 9;
    private static final int OP_VIDEO_EDITOR_SET_ASPECT_RATIO = 10;
    private static final int OP_VIDEO_EDITOR_APPLY_THEME = 11;
    private static final int OP_VIDEO_EDITOR_GENERATE_PREVIEW_PROGRESS = 12;
    private static final int OP_VIDEO_EDITOR_LOAD_PROJECTS = 13;

    private static final int OP_MEDIA_ITEM_ADD_VIDEO_URI = 100;
    private static final int OP_MEDIA_ITEM_ADD_IMAGE_URI = 101;
    private static final int OP_MEDIA_ITEM_MOVE = 102;
    private static final int OP_MEDIA_ITEM_REMOVE = 103;
    private static final int OP_MEDIA_ITEM_SET_RENDERING_MODE = 104;
    private static final int OP_MEDIA_ITEM_SET_DURATION = 105;
    private static final int OP_MEDIA_ITEM_SET_BOUNDARIES = 106;
    private static final int OP_MEDIA_ITEM_SET_VOLUME = 107;
    private static final int OP_MEDIA_ITEM_SET_MUTE = 108;
    private static final int OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM = 109;
    private static final int OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM_STATUS = 110;
    private static final int OP_MEDIA_ITEM_GET_THUMBNAILS = 112;
    private static final int OP_MEDIA_ITEM_LOAD = 113;
    private static final int OP_MEDIA_ITEM_LOAD_STATUS = 114;

    private static final int OP_EFFECT_ADD_COLOR = 200;
    private static final int OP_EFFECT_ADD_IMAGE_KEN_BURNS = 201;
    private static final int OP_EFFECT_REMOVE = 202;

    private static final int OP_TRANSITION_INSERT_ALPHA = 300;
    private static final int OP_TRANSITION_INSERT_CROSSFADE = 301;
    private static final int OP_TRANSITION_INSERT_FADE_BLACK = 302;
    private static final int OP_TRANSITION_INSERT_SLIDING = 303;
    private static final int OP_TRANSITION_REMOVE = 304;
    private static final int OP_TRANSITION_SET_DURATION = 305;
    private static final int OP_TRANSITION_GET_THUMBNAIL = 306;

    private static final int OP_OVERLAY_ADD = 400;
    private static final int OP_OVERLAY_REMOVE = 401;
    private static final int OP_OVERLAY_SET_START_TIME = 402;
    private static final int OP_OVERLAY_SET_DURATION = 403;
    private static final int OP_OVERLAY_SET_ATTRIBUTES = 404;

    private static final int OP_AUDIO_TRACK_ADD = 500;
    private static final int OP_AUDIO_TRACK_REMOVE = 501;
    private static final int OP_AUDIO_TRACK_SET_VOLUME = 502;
    private static final int OP_AUDIO_TRACK_SET_MUTE = 503;
    private static final int OP_AUDIO_TRACK_SET_BOUNDARIES = 505;
    private static final int OP_AUDIO_TRACK_SET_LOOP = 506;
    private static final int OP_AUDIO_TRACK_SET_DUCK = 507;
    private static final int OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM = 508;
    private static final int OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM_STATUS = 509;

    private static final int DUCK_THRESHOLD = 20;
    private static final int DUCK_TRACK_VOLUME = 65;
    // The default audio track volume
    private static final int DEFAULT_AUDIO_TRACK_VOLUME = 50;

    // Static member variables
    private static final Map<String, Intent> mPendingIntents = new HashMap<String, Intent>();
    private static final List<ApiServiceListener> mListeners = new ArrayList<ApiServiceListener>();
    private static final IntentPool mIntentPool = new IntentPool(8);
    private static VideoEditorProject mVideoProject;
    private static VideoEditor mVideoEditor;
    private static ServiceMediaProcessingProgressListener mGeneratePreviewListener;
    private static volatile boolean mExportCancelled;

    private IntentProcessor mVideoThread;
    private IntentProcessor mAudioThread;
    private IntentProcessor mThumbnailThread;
    private Handler mHandler;

    private final Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPendingIntents.size() == 0) {
                logd("Stop runnable: Stopping service");
                stopSelf();
            }
        }
    };

    /**
     * Generate preview listener
     */
    private final class ServiceMediaProcessingProgressListener
            implements VideoEditor.MediaProcessingProgressListener {
        // Instance variables
        private final String mProjectPath;

        /**
         * Constructor
         *
         * @param projectPath The project path
         */
        public ServiceMediaProcessingProgressListener(String projectPath) {
            mProjectPath = projectPath;
        }

        @Override
        public void onProgress(Object item, int action, int progress) {
            final Intent intent = mIntentPool.get();
            intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_GENERATE_PREVIEW_PROGRESS);
            intent.putExtra(PARAM_PROJECT_PATH, mProjectPath);
            intent.putExtra(PARAM_ACTION, action);
            intent.putExtra(PARAM_PROGRESS_VALUE, progress);

            if (item == null) { // Last callback uses null
            } else if (item instanceof MediaItem) {
                intent.putExtra(PARAM_STORYBOARD_ITEM_ID, ((MediaItem)item).getId());
                intent.putExtra(PARAM_ATTRIBUTES, MediaItem.class.getCanonicalName());
            } else if (item instanceof Transition) {
                intent.putExtra(PARAM_STORYBOARD_ITEM_ID, ((Transition)item).getId());
                intent.putExtra(PARAM_ATTRIBUTES, Transition.class.getCanonicalName());
            } else if (item instanceof AudioTrack) {
                intent.putExtra(PARAM_STORYBOARD_ITEM_ID, ((AudioTrack)item).getId());
                intent.putExtra(PARAM_ATTRIBUTES, AudioTrack.class.getCanonicalName());
            } else {
                Log.w(TAG, "Unsupported storyboard item type: " + item.getClass());
                return;
            }

            completeRequest(intent, null, null, null, null, true);
        }
    }

    /**
     * @return A unique id
     */
    public static String generateId() {
        return StringUtils.randomString(6);
    }

    /**
     * Register a listener
     *
     * @param listener The listener
     */
    public static void registerListener(ApiServiceListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregister a listener
     *
     * @param listener The listener
     */
    public static void unregisterListener(ApiServiceListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Load the projects
     *
     * @param context The context
     */
    public static void loadProjects(Context context) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_LOAD_PROJECTS);

        startCommand(context, intent);
    }

    /**
     * Create a new VideoEditor project
     *
     * @param context The context
     * @param projectPath The project path
     * @param projectName The project name
     * @param movies The array of movie file names to add to the newly
     *      created project
     * @param photos The array of photo file names to add to the newly
     *      created project
     * @param themeType The theme type
     */
    public static void createVideoEditor(Context context, String projectPath, String projectName,
                String[] movies, String[] photos, String themeType) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_CREATE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_PROJECT_NAME, projectName);
        intent.putExtra(PARAM_MOVIES_FILENAMES, movies);
        intent.putExtra(PARAM_PHOTOS_FILENAMES, photos);
        intent.putExtra(PARAM_THEME, themeType);

        startCommand(context, intent);
    }

    /**
     * Create a new VideoEditor project
     *
     * @param context The context
     * @param projectPath The project path
     */
    public static void loadVideoEditor(Context context, String projectPath) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_LOAD);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);

        startCommand(context, intent);
    }

    /**
     * Export the VideoEditor movie
     *
     * @param context The context
     * @param projectPath The project path
     * @param filename The export filename
     * @param height The output movie height
     * @param bitrate The output movie bitrate
     */
    public static void exportVideoEditor(Context context, String projectPath, String filename,
            int height, int bitrate) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_EXPORT);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_FILENAME, filename);
        intent.putExtra(PARAM_HEIGHT, height);
        intent.putExtra(PARAM_BITRATE, bitrate);

        startCommand(context, intent);
    }

    /**
     * Check if export is pending
     *
     * @param projectPath The project path
     * @param filename The export filename
     *
     * @return true if the export is pending
     */
    public static boolean isVideoEditorExportPending(String projectPath, String filename) {
        for (Intent intent : mPendingIntents.values()) {
            final int op = intent.getIntExtra(PARAM_OP, -1);
            if (op == OP_VIDEO_EDITOR_EXPORT) {
                String pp = intent.getStringExtra(PARAM_PROJECT_PATH);
                if (pp.equals(projectPath)) {
                    String fn = intent.getStringExtra(PARAM_FILENAME);
                    if (fn.equals(filename)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Cancel the export of the specified VideoEditor movie
     *
     * @param context The context
     * @param projectPath The project path
     * @param filename The export filename
     */
    public static void cancelExportVideoEditor(Context context, String projectPath,
            String filename) {
        mExportCancelled = true;
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_CANCEL_EXPORT);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_FILENAME, filename);

        startCommand(context, intent);
    }

    /**
     * Change the aspect ratio
     *
     * @param context The context
     * @param projectPath The project path
     * @param aspectRatio The aspect ratio
     */
    public static void setAspectRatio(Context context, String projectPath, int aspectRatio) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_SET_ASPECT_RATIO);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_ASPECT_RATIO, aspectRatio);

        startCommand(context, intent);
    }

    /**
     * Apply a theme
     *
     * @param context The context
     * @param projectPath The project path
     * @param theme The theme
     */
     public static void applyTheme(Context context, String projectPath, String theme) {
         final Intent intent = mIntentPool.get(context, ApiService.class);
         intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_APPLY_THEME);
         intent.putExtra(PARAM_PROJECT_PATH, projectPath);
         intent.putExtra(PARAM_THEME, theme);

         startCommand(context, intent);
     }

    /**
     * Checks if the service is busy modifying the timeline. While
     * the video editor is busy the application should not attempt
     * to preview the movie.
     *
     * @param projectPath The project path
     *
     * @return {@code true} if the video editor is modifying the timeline
     */
    public static boolean isProjectBeingEdited(String projectPath) {
        for (Intent intent : mPendingIntents.values()) {
            final int op = intent.getIntExtra(PARAM_OP, -1);
            switch (op) {
                // When these operations are pending the video editor is not busy.
                case OP_VIDEO_EDITOR_LOAD_PROJECTS:
                case OP_VIDEO_EDITOR_SAVE:
                case OP_MEDIA_ITEM_SET_VOLUME:
                case OP_MEDIA_ITEM_SET_MUTE:
                case OP_MEDIA_ITEM_GET_THUMBNAILS:
                case OP_MEDIA_ITEM_LOAD:
                case OP_TRANSITION_GET_THUMBNAIL:
                case OP_AUDIO_TRACK_SET_VOLUME:
                case OP_AUDIO_TRACK_SET_MUTE: {
                    break;
                }

                default: {
                    final String pp = intent.getStringExtra(PARAM_PROJECT_PATH);
                    if (pp != null && pp.equals(projectPath)) {
                        return true;
                    }
                    break;
                }
            }
        }

        return false;
    }

    /**
     * Save the VideoEditor project
     *
     * @param context The context
     * @param projectPath The project path
     */
    public static void saveVideoEditor(Context context, String projectPath) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_SAVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);

        startCommand(context, intent);
    }

    /**
     * Release the VideoEditor project
     *
     * @param context The context
     * @param projectPath The project path
     */
    public static void releaseVideoEditor(Context context, String projectPath) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_RELEASE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);

        startCommand(context, intent);
    }

    /**
     * Delete the project specified by the project path
     *
     * @param context The context
     * @param projectPath The project path
     */
    public static void deleteProject(Context context, String projectPath) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_DELETE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);

        startCommand(context, intent);
    }

    /**
     * Add a new video media item after the specified media item id
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The mediaItem id
     * @param afterMediaItemId The id of the media item preceding the media item
     * @param uri The media item URI
     * @param renderingMode The rendering mode
     * @param themeId The theme id
     */
    public static void addMediaItemVideoUri(Context context, String projectPath,
            String mediaItemId, String afterMediaItemId, Uri uri, int renderingMode,
            String themeId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_ADD_VIDEO_URI);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_FILENAME, uri);
        intent.putExtra(PARAM_MEDIA_ITEM_RENDERING_MODE, renderingMode);
        intent.putExtra(PARAM_THEME, themeId);

        startCommand(context, intent);
    }

    /**
     * Add a new image media item after the specified media item id
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The mediaItem id
     * @param afterMediaItemId The id of the media item preceding the media item
     * @param uri The media item URI
     * @param renderingMode The rendering mode
     * @param durationMs The duration of the item (for images only, ignored for videos)
     * @param themeId The theme id
     */
    public static void addMediaItemImageUri(Context context, String projectPath,
            String mediaItemId, String afterMediaItemId, Uri uri, int renderingMode,
            long durationMs, String themeId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_ADD_IMAGE_URI);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_FILENAME, uri);
        intent.putExtra(PARAM_MEDIA_ITEM_RENDERING_MODE, renderingMode);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_THEME, themeId);

        startCommand(context, intent);
    }

    /**
     * Download or make a copy of an image from the specified URI
     *
     * @param context The context
     * @param projectPath The project path
     * @param uri The media item URI
     * @param mimeType The MIME type
     */
    public static void loadMediaItem(Context context, String projectPath, Uri uri,
            String mimeType) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_LOAD);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_FILENAME, uri);
        intent.putExtra(PARAM_ATTRIBUTES, mimeType);

        startCommand(context, intent);
    }

    /**
     * Move a media item after the specified media id
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item to move
     * @param afterMediaItemId The id of the relative media item
     * @param themeId The theme id
     */
    public static void moveMediaItem(Context context, String projectPath,
            String mediaItemId, String afterMediaItemId, String themeId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_MOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_THEME, themeId);

        startCommand(context, intent);
    }

    /**
     * Remove a media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item to remove
     * @param themeId The theme id
     */
    public static void removeMediaItem(Context context, String projectPath, String mediaItemId,
            String themeId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_REMOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_THEME, themeId);

        startCommand(context, intent);
    }

    /**
     * Set the rendering mode for a media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     */
    public static void setMediaItemRenderingMode(Context context, String projectPath,
            String mediaItemId, int renderingMode) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_SET_RENDERING_MODE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_MEDIA_ITEM_RENDERING_MODE, renderingMode);

        startCommand(context, intent);
    }

    /**
     * Get the thumbnails of the specified size
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param width The width
     * @param height The height
     * @param startMs The start time in milliseconds
     * @param endMs The end time in milliseconds
     * @param count The number of thumbnails
     */
    public static void getMediaItemThumbnails(Context context,
            String projectPath, String mediaItemId, int width, int height,
            long startMs, long endMs, int count, int token, int[] indices) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_GET_THUMBNAILS);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_WIDTH, width);
        intent.putExtra(PARAM_HEIGHT, height);
        intent.putExtra(PARAM_START_TIME, startMs);
        intent.putExtra(PARAM_END_TIME, endMs);
        intent.putExtra(PARAM_COUNT, count);
        intent.putExtra(PARAM_TOKEN, token);
        intent.putExtra(PARAM_INDICES, indices);

        startCommand(context, intent);
    }

    /**
     * Set the media item duration
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param durationMs The media item duration
     */
    public static void setMediaItemDuration(Context context, String projectPath,
            String mediaItemId, long durationMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_SET_DURATION);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_DURATION, durationMs);

        startCommand(context, intent);
    }

    /**
     * Set the media item boundaries
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param beginBoundaryMs The media item begin boundary
     * @param endBoundaryMs The media item end boundary
     */
    public static void setMediaItemBoundaries(Context context, String projectPath,
            String mediaItemId, long beginBoundaryMs, long endBoundaryMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_SET_BOUNDARIES);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_BEGIN_BOUNDARY, beginBoundaryMs);
        intent.putExtra(PARAM_END_BOUNDARY, endBoundaryMs);

        startCommand(context, intent);
    }

    /**
     * Set the media item volume (MediaVideoItem only)
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param volumePercentage The volume
     */
    public static void setMediaItemVolume(Context context, String projectPath,
            String mediaItemId, int volumePercentage) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_SET_VOLUME);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_VOLUME, volumePercentage);

        startCommand(context, intent);
    }

    /**
     * Mute/unmute the media item (MediaVideoItem only)
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param muted true to mute the media item
     */
    public static void setMediaItemMute(Context context, String projectPath, String mediaItemId,
            boolean muted) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_SET_MUTE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_MUTE, muted);

        startCommand(context, intent);
    }

    /**
     * Extract the media item audio waveform
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     */
    public static void extractMediaItemAudioWaveform(Context context, String projectPath,
            String mediaItemId) {
        if (isMediaItemAudioWaveformPending(projectPath, mediaItemId)) {
            return;
        }

        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItemId);

        startCommand(context, intent);
    }

    /**
     * Check if extract audio waveform is pending for the specified MediaItem
     *
     * @param projectPath The project path
     * @param mediaItemId The MediaItem id
     *
     * @return true if the extract audio waveform is pending
     */
    public static boolean isMediaItemAudioWaveformPending(String projectPath, String mediaItemId) {
        for (Intent intent : mPendingIntents.values()) {
            int op = intent.getIntExtra(PARAM_OP, -1);
            if (op == OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM) {
                String pp = intent.getStringExtra(PARAM_PROJECT_PATH);
                if (pp.equals(projectPath)) {
                    String mid = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    if (mid.equals(mediaItemId)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Insert an alpha transition after the specified media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param afterMediaItemId Insert the transition after the media item with this id
     * @param transitionId The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The transition behavior
     * @param maskRawResourceId The mask raw resource id
     * @param blending The transition blending
     * @param invert The transition invert
     */
    public static void insertAlphaTransition(Context context, String projectPath,
            String afterMediaItemId, String transitionId, long durationMs, int behavior,
            int maskRawResourceId, int blending, boolean invert) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_INSERT_ALPHA);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_TRANSITION_BEHAVIOR, behavior);
        intent.putExtra(PARAM_TRANSITION_MASK, maskRawResourceId);
        intent.putExtra(PARAM_TRANSITION_BLENDING, blending);
        intent.putExtra(PARAM_TRANSITION_INVERT, invert);

        startCommand(context, intent);
    }

    /**
     * Insert an crossfade transition after the specified media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param afterMediaItemId Insert the transition after the media item with this id
     * @param transitionId The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The transition behavior
     */
    public static void insertCrossfadeTransition(Context context, String projectPath,
            String afterMediaItemId, String transitionId, long durationMs, int behavior) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_INSERT_CROSSFADE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_TRANSITION_BEHAVIOR, behavior);

        startCommand(context, intent);
    }

    /**
     * Insert a fade-to-black transition after the specified media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param afterMediaItemId Insert the transition after the media item with this id
     * @param transitionId The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The transition behavior
     */
    public static void insertFadeBlackTransition(Context context, String projectPath,
            String afterMediaItemId, String transitionId, long durationMs, int behavior) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_INSERT_FADE_BLACK);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_TRANSITION_BEHAVIOR, behavior);

        startCommand(context, intent);
    }

    /**
     * Insert a sliding transition after the specified media item
     *
     * @param context The context
     * @param projectPath The project path
     * @param afterMediaItemId Insert the transition after the media item with this id
     * @param transitionId The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The transition behavior
     * @param direction The slide direction
     */
    public static void insertSlidingTransition(Context context, String projectPath,
            String afterMediaItemId, String transitionId, long durationMs, int behavior,
            int direction) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_INSERT_SLIDING);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, afterMediaItemId);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_TRANSITION_BEHAVIOR, behavior);
        intent.putExtra(PARAM_TRANSITION_DIRECTION, direction);

        startCommand(context, intent);
    }

    /**
     * Remove a transition
     *
     * @param context The context
     * @param projectPath The project path
     * @param transitionId The id of the transition to remove
     */
    public static void removeTransition(Context context, String projectPath, String transitionId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_REMOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);

        startCommand(context, intent);
    }

    /**
     * Set a transition duration
     *
     * @param context The context
     * @param projectPath The project path
     * @param transitionId The id of the transition
     * @param durationMs The transition duration
     */
    public static void setTransitionDuration(Context context, String projectPath,
            String transitionId, long durationMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_SET_DURATION);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_DURATION, durationMs);

        startCommand(context, intent);
    }

    /**
     * Get the thumbnail of the specified height
     *
     * @param context The context
     * @param projectPath The project path
     * @param transitionId The id of the transition
     * @param height The height
     */
    public static void getTransitionThumbnails(Context context, String projectPath,
            String transitionId, int height) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_TRANSITION_GET_THUMBNAIL);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, transitionId);
        intent.putExtra(PARAM_HEIGHT, height);

        startCommand(context, intent);
    }

    /**
     * Check if the transition thumbnailing is in progress
     *
     * @param projectPath The project path
     * @param transitionId The id of the transition
     *
     * @return true if the transition thumbnailing is in progress
     */
    public static boolean isTransitionThumbnailsPending(String projectPath, String transitionId) {
        for (Intent intent : mPendingIntents.values()) {
            int op = intent.getIntExtra(PARAM_OP, -1);
            if (op == OP_TRANSITION_GET_THUMBNAIL) {
                String pp = intent.getStringExtra(PARAM_PROJECT_PATH);
                if (pp.equals(projectPath)) {
                    String mid = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    if (mid.equals(transitionId)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Add a color effect
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param effectId The effect id
     * @param startTimeMs The start time in milliseconds
     * @param durationMs The duration in milliseconds
     * @param type The effect type
     * @param param The effect param (if any)
     */
    public static void addEffectColor(Context context, String projectPath, String mediaItemId,
            String effectId, long startTimeMs, long durationMs, int type, int param) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_EFFECT_ADD_COLOR);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, effectId);
        intent.putExtra(PARAM_START_TIME, startTimeMs);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_EFFECT_TYPE, type);
        intent.putExtra(PARAM_EFFECT_PARAM, param);

        startCommand(context, intent);
    }

    /**
     * Add a Ken Burns effect
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param effectId The effect id
     * @param startTimeMs The start time
     * @param durationMs The duration of the item
     * @param startRect The start rectangle for the Ken Burns effect
     * @param endRect The end rectangle for the Ken Burns effect
     */
    public static void addEffectKenBurns(Context context, String projectPath,
            String mediaItemId, String effectId, long startTimeMs, long durationMs,
            Rect startRect, Rect endRect) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_EFFECT_ADD_IMAGE_KEN_BURNS);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, effectId);
        intent.putExtra(PARAM_START_TIME, startTimeMs);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_MEDIA_ITEM_START_RECT, startRect);
        intent.putExtra(PARAM_MEDIA_ITEM_END_RECT, endRect);

        startCommand(context, intent);
    }

    /**
     * Remove an effect
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param effectId The id of the effect to remove
     */
    public static void removeEffect(Context context, String projectPath, String mediaItemId,
            String effectId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_EFFECT_REMOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, effectId);

        startCommand(context, intent);
    }

    /**
     * Add an overlay
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param userAttributes The overlay user attributes
     * @param startTimeMs The start time in milliseconds
     * @param durationMs The duration in milliseconds
     */
    public static void addOverlay(Context context, String projectPath, String mediaItemId,
            String overlayId, Bundle userAttributes, long startTimeMs, long durationMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_OVERLAY_ADD);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, overlayId);
        intent.putExtra(PARAM_START_TIME, startTimeMs);
        intent.putExtra(PARAM_DURATION, durationMs);
        intent.putExtra(PARAM_ATTRIBUTES, userAttributes);

        startCommand(context, intent);
    }

    /**
     * Remove an overlay
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param overlayId The id of the overlay to remove
     */
    public static void removeOverlay(Context context, String projectPath, String mediaItemId,
            String overlayId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_OVERLAY_REMOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, overlayId);

        startCommand(context, intent);
    }

    /**
     * Set the start time of an overlay
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param overlayId The id of the overlay
     * @param startTimeMs The start time in milliseconds
     */
    public static void setOverlayStartTime(Context context, String projectPath, String mediaItemId,
            String overlayId, long startTimeMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_OVERLAY_SET_START_TIME);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, overlayId);
        intent.putExtra(PARAM_START_TIME, startTimeMs);

        startCommand(context, intent);
    }

    /**
     * Set the duration of an overlay
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param overlayId The id of the overlay
     * @param durationMs The duration in milliseconds
     */
    public static void setOverlayDuration(Context context, String projectPath, String mediaItemId,
            String overlayId, long durationMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_OVERLAY_SET_DURATION);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, overlayId);
        intent.putExtra(PARAM_DURATION, durationMs);

        startCommand(context, intent);
    }

    /**
     * Set the user attributes of an overlay
     *
     * @param context The context
     * @param projectPath The project path
     * @param mediaItemId The media item id
     * @param overlayId The id of the overlay
     * @param userAttributes The user attributes
     */
    public static void setOverlayUserAttributes(Context context, String projectPath,
            String mediaItemId, String overlayId, Bundle userAttributes) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_OVERLAY_SET_ATTRIBUTES);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID, mediaItemId);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, overlayId);
        intent.putExtra(PARAM_ATTRIBUTES, userAttributes);

        startCommand(context, intent);
    }

    /**
     * Add an audio track
     *
     * @param context The context
     * @param projectPath The project path
     * @param id The audio track id
     * @param uri The audio track URI
     * @param loop true to loop the audio track
     */
    public static void addAudioTrack(Context context, String projectPath, String id, Uri uri,
            boolean loop) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_ADD);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, id);
        intent.putExtra(PARAM_FILENAME, uri);
        intent.putExtra(PARAM_LOOP, loop);

        startCommand(context, intent);
    }

    /**
     * Remove an audio track from the storyboard timeline
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track to remove
     */
    public static void removeAudioTrack(Context context, String projectPath, String audioTrackId) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_REMOVE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);

        startCommand(context, intent);
    }

    /**
     * Set the audio track boundaries
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param beginBoundaryMs The audio track begin boundary
     * @param endBoundaryMs The audio track end boundary
     */
    public static void setAudioTrackBoundaries(Context context, String projectPath,
            String audioTrackId, long beginBoundaryMs, long endBoundaryMs) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_SET_BOUNDARIES);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);
        intent.putExtra(PARAM_BEGIN_BOUNDARY, beginBoundaryMs);
        intent.putExtra(PARAM_END_BOUNDARY, endBoundaryMs);

        startCommand(context, intent);
    }

    /**
     * Set the loop flag for an audio track
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param loop true to loop audio
     */
    public static void setAudioTrackLoop(Context context, String projectPath, String audioTrackId,
            boolean loop) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_SET_LOOP);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);
        intent.putExtra(PARAM_LOOP, loop);

        startCommand(context, intent);
    }

    /**
     * Set the duck flag for an audio track
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param duck true to enable ducking
     */
    public static void setAudioTrackDuck(Context context, String projectPath, String audioTrackId,
            boolean duck) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_SET_DUCK);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);
        intent.putExtra(PARAM_DUCK, duck);

        startCommand(context, intent);
    }

    /**
     * Set the audio track volume
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param volumePercentage The audio track volume (in percentage)
     */
    public static void setAudioTrackVolume(Context context, String projectPath,
            String audioTrackId, int volumePercentage) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_SET_VOLUME);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);
        intent.putExtra(PARAM_VOLUME, volumePercentage);

        startCommand(context, intent);
    }

    /**
     * Mute/unmute the audio track
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param muted true to mute the audio track
     */
    public static void setAudioTrackMute(Context context, String projectPath, String audioTrackId,
            boolean muted) {
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_SET_MUTE);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);
        intent.putExtra(PARAM_MUTE, muted);

        startCommand(context, intent);
    }

    /**
     * Extract the audio track audio waveform
     *
     * @param context The context
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     */
    public static void extractAudioTrackAudioWaveform(Context context, String projectPath,
            String audioTrackId) {
        if (isAudioTrackAudioWaveformPending(projectPath, audioTrackId)) {
            return;
        }
        final Intent intent = mIntentPool.get(context, ApiService.class);
        intent.putExtra(PARAM_OP, OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM);
        intent.putExtra(PARAM_PROJECT_PATH, projectPath);
        intent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrackId);

        startCommand(context, intent);
    }

    /**
     * Check if extract audio waveform is pending for the specified audio track
     *
     * @param projectPath The project path
     * @param audioTrackId The audio track id
     *
     * @return true if the extract audio waveform is pending
     */
    public static boolean isAudioTrackAudioWaveformPending(String projectPath,
            String audioTrackId) {
        for (Intent intent : mPendingIntents.values()) {
            int op = intent.getIntExtra(PARAM_OP, -1);
            if (op == OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM) {
                String pp = intent.getStringExtra(PARAM_PROJECT_PATH);
                if (pp.equals(projectPath)) {
                    String mid = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    if (mid.equals(audioTrackId)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Start the service (if it is not running) with the specified Intent
     *
     * @param context The context
     * @param intent The intent
     *
     * @return The request id of the pending request
     */
    private static String startCommand(Context context, Intent intent) {
        final String requestId = StringUtils.randomString(8);
        intent.putExtra(PARAM_REQUEST_ID, requestId);
        mPendingIntents.put(requestId, intent);

        context.startService(intent);

        final String projectPath = intent.getStringExtra(PARAM_PROJECT_PATH);
        if (projectPath != null) {
            final boolean projectEdited = isProjectBeingEdited(projectPath);
            if (projectEdited) {
                for (ApiServiceListener listener : mListeners) {
                    listener.onProjectEditState(projectPath, projectEdited);
                }
            }
        }

        return requestId;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());

        mVideoThread = new IntentProcessor("VideoServiceThread");
        mVideoThread.start();

        mAudioThread = new IntentProcessor("AudioServiceThread");
        mAudioThread.start();

        mThumbnailThread = new IntentProcessor("ThumbnailServiceThread");
        mThumbnailThread.start();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        final int op = intent.getIntExtra(PARAM_OP, -1);
        switch(op) {
            case OP_VIDEO_EDITOR_LOAD_PROJECTS:
            case OP_VIDEO_EDITOR_CREATE:
            case OP_VIDEO_EDITOR_LOAD:
            case OP_VIDEO_EDITOR_SAVE:
            case OP_VIDEO_EDITOR_RELEASE:
            case OP_VIDEO_EDITOR_DELETE:
            case OP_VIDEO_EDITOR_SET_ASPECT_RATIO:
            case OP_VIDEO_EDITOR_APPLY_THEME:
            case OP_VIDEO_EDITOR_EXPORT:
            case OP_VIDEO_EDITOR_CANCEL_EXPORT:
            case OP_VIDEO_EDITOR_EXPORT_STATUS:

            case OP_MEDIA_ITEM_ADD_VIDEO_URI:
            case OP_MEDIA_ITEM_ADD_IMAGE_URI:
            case OP_MEDIA_ITEM_MOVE:
            case OP_MEDIA_ITEM_REMOVE:
            case OP_MEDIA_ITEM_SET_RENDERING_MODE:
            case OP_MEDIA_ITEM_SET_DURATION:
            case OP_MEDIA_ITEM_SET_BOUNDARIES:
            case OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM:
            case OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM_STATUS:
            case OP_MEDIA_ITEM_LOAD:
            case OP_MEDIA_ITEM_LOAD_STATUS:

            case OP_EFFECT_ADD_COLOR:
            case OP_EFFECT_ADD_IMAGE_KEN_BURNS:
            case OP_EFFECT_REMOVE:

            case OP_TRANSITION_INSERT_ALPHA:
            case OP_TRANSITION_INSERT_CROSSFADE:
            case OP_TRANSITION_INSERT_FADE_BLACK:
            case OP_TRANSITION_INSERT_SLIDING:
            case OP_TRANSITION_REMOVE:
            case OP_TRANSITION_SET_DURATION:

            case OP_OVERLAY_ADD:
            case OP_OVERLAY_REMOVE:
            case OP_OVERLAY_SET_START_TIME:
            case OP_OVERLAY_SET_DURATION:
            case OP_OVERLAY_SET_ATTRIBUTES:

            case OP_AUDIO_TRACK_ADD:
            case OP_AUDIO_TRACK_REMOVE:
            case OP_AUDIO_TRACK_SET_BOUNDARIES:
            case OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM:
            case OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM_STATUS: {
                mVideoThread.submit(intent);
                break;
            }

            case OP_TRANSITION_GET_THUMBNAIL: {
                mThumbnailThread.submit(intent);
                break;
            }

            case OP_MEDIA_ITEM_GET_THUMBNAILS: {
                final String projectPath = intent.getStringExtra(PARAM_PROJECT_PATH);
                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final int token = intent.getIntExtra(PARAM_TOKEN, 0);
                // Cancel any pending thumbnail request for the same media item
                // but with a different token
                Iterator<Intent> intentQueueIterator = mThumbnailThread.getIntentQueueIterator();
                while (intentQueueIterator.hasNext()) {
                    Intent qIntent = intentQueueIterator.next();
                    int opi = qIntent.getIntExtra(PARAM_OP, -1);
                    String pp = qIntent.getStringExtra(PARAM_PROJECT_PATH);
                    String mid = qIntent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    int tk = qIntent.getIntExtra(PARAM_TOKEN, 0);
                    if (opi == op && pp.equals(projectPath) && mid.equals(mediaItemId)
                            && tk != token) {
                        boolean canceled = mThumbnailThread.cancel(qIntent);
                        if (canceled) {
                            logd("Canceled operation: " + op + " for media item" + mediaItemId);
                            mPendingIntents.remove(qIntent.getStringExtra(PARAM_REQUEST_ID));
                            mIntentPool.put(qIntent);
                        }
                        break;
                    }
                }
                mThumbnailThread.submit(intent);
                break;
            }

            case OP_MEDIA_ITEM_SET_VOLUME:
            case OP_MEDIA_ITEM_SET_MUTE:

            case OP_AUDIO_TRACK_SET_VOLUME:
            case OP_AUDIO_TRACK_SET_MUTE:
            case OP_AUDIO_TRACK_SET_LOOP:
            case OP_AUDIO_TRACK_SET_DUCK: {
                mAudioThread.submit(intent);
                break;
            }

            default: {
                Log.e(TAG, "No thread assigned: " + op);
                break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mThumbnailThread != null) {
            mThumbnailThread.quit();
            mThumbnailThread = null;
        }

        if (mAudioThread != null) {
            mAudioThread.quit();
            mAudioThread = null;
        }

        if (mVideoThread != null) {
            mVideoThread.quit();
            mVideoThread = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Process the intent
     *
     * @param intent The intent
     */
    public void processIntent(final Intent intent) {
        final int op = intent.getIntExtra(PARAM_OP, -1);
        VideoEditor videoEditor = null;
        try {
            final String projectPath = intent.getStringExtra(PARAM_PROJECT_PATH);
            // Check if the project path matches the current VideoEditor project
            switch (op) {
                case OP_VIDEO_EDITOR_LOAD_PROJECTS:
                case OP_VIDEO_EDITOR_CREATE:
                case OP_VIDEO_EDITOR_LOAD:
                case OP_VIDEO_EDITOR_DELETE: {
                    break;
                }

                default: {
                    videoEditor = getVideoEditor(projectPath);
                    if (videoEditor == null) {
                        throw new IllegalArgumentException("Invalid project path: "
                                + projectPath + " for operation: " + op);
                    }
                    break;
                }
            }

            switch (op) {
                case OP_VIDEO_EDITOR_LOAD_PROJECTS: {
                    logd("OP_LOAD_PROJECTS");
                    final List<VideoEditorProject> projects = new ArrayList<VideoEditorProject>();
                    final File dir = FileUtils.getProjectsRootDir(getApplicationContext());
                    if (dir != null) {
                        // Collect valid projects (project with valid metadata).
                        final File[] files = dir.listFiles();
                        if (files != null) {
                            for (int i = 0; i < files.length; i++) {
                                if (files[i].isDirectory()) {
                                    final String pp = files[i].getAbsolutePath();
                                    try {
                                        projects.add(VideoEditorProject.fromXml(null, pp));
                                    } catch (FileNotFoundException ex) {
                                        Log.w(TAG, "processIntent: Project file not found: " + pp);
                                        FileUtils.deleteDir(new File(pp));
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                            }

                            if (projects.size() > 0) {
                                // Sort the projects in order of "last saved"
                                Collections.sort(projects, new Comparator<VideoEditorProject>() {
                                    @Override
                                    public int compare(VideoEditorProject object1,
                                            VideoEditorProject object2) {
                                        if (object1.getLastSaved() > object2.getLastSaved()) {
                                            return -1;
                                        } else if (object1.getLastSaved() == object2.getLastSaved()) {
                                            return 0;
                                        } else {
                                            return 1;
                                        }
                                    }
                                });
                            }
                        }
                    }

                    completeRequest(intent, videoEditor, null, projects, null, true);
                    break;
                }

                case OP_VIDEO_EDITOR_CREATE: {
                    logd("OP_VIDEO_EDITOR_CREATE: " + projectPath);

                    try {
                        // Release the current video editor if any
                        releaseEditor();

                        videoEditor = VideoEditorFactory.create(projectPath);

                        // Add the movies to the timeline
                        final String[] movies = intent.getStringArrayExtra(PARAM_MOVIES_FILENAMES);
                        for (int i = 0; i < movies.length; i++) {
                            final MediaItem mediaItem = new MediaVideoItem(videoEditor,
                                    generateId(), movies[i],
                                    MediaItem.RENDERING_MODE_BLACK_BORDER);
                            videoEditor.addMediaItem(mediaItem);
                        }

                        // Add the photos to the timeline
                        final String[] photos = intent.getStringArrayExtra(PARAM_PHOTOS_FILENAMES);
                        for (int i = 0; i < photos.length; i++) {
                            final MediaItem mediaItem = new MediaImageItem(videoEditor,
                                    generateId(), photos[i],
                                    MediaItemUtils.getDefaultImageDuration(),
                                    MediaItem.RENDERING_MODE_BLACK_BORDER);
                            videoEditor.addMediaItem(mediaItem);
                        }

                        // Create the project
                        final String projectName = intent.getStringExtra(PARAM_PROJECT_NAME);
                        final String themeId = intent.getStringExtra(PARAM_THEME);
                        if (themeId != null) {
                            applyThemeToMovie(videoEditor, themeId);
                        }

                        // Set the aspect ratio to the aspect ratio of the first item
                        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
                        if (mediaItems.size() > 0) {
                            videoEditor.setAspectRatio(mediaItems.get(0).getAspectRatio());
                        }

                        // Create the video editor project
                        final VideoEditorProject videoProject = new VideoEditorProject(
                                videoEditor, projectPath, projectName, System.currentTimeMillis(),
                                0, 0, VideoEditorProject.DEFAULT_ZOOM_LEVEL, null, themeId, null);
                        videoProject.setMediaItems(copyMediaItems(
                                videoEditor.getAllMediaItems()));
                        videoProject.setAudioTracks(copyAudioTracks(
                                videoEditor.getAllAudioTracks()));

                        // Make this project the current project
                        mVideoEditor = videoEditor;
                        mGeneratePreviewListener = new ServiceMediaProcessingProgressListener(
                                projectPath);

                        completeRequest(intent, videoEditor, null, videoProject, null, false);
                        generatePreview(videoEditor, true);
                        completeRequest(intent);
                    } catch (Exception ex) {
                        if (videoEditor != null) {
                            videoEditor.release();
                            videoEditor = null;
                        }
                        throw ex;
                    }

                    break;
                }

                case OP_VIDEO_EDITOR_LOAD: {
                    videoEditor = releaseEditorNot(projectPath);

                    if (videoEditor == null) {  // The old project was released.
                        logd("OP_VIDEO_EDITOR_LOAD: Loading: " + projectPath);
                        try {
                            // Load the project
                            videoEditor = VideoEditorFactory.load(projectPath, false);

                            // Load the video editor project
                            final VideoEditorProject videoProject = VideoEditorProject.fromXml(
                                    videoEditor, projectPath);
                            videoProject.setMediaItems(copyMediaItems(
                                    videoEditor.getAllMediaItems()));
                            videoProject.setAudioTracks(copyAudioTracks(
                                    videoEditor.getAllAudioTracks()));
                            // Make this the current project
                            mVideoEditor = videoEditor;
                            mGeneratePreviewListener = new ServiceMediaProcessingProgressListener(
                                    projectPath);

                            completeRequest(intent, videoEditor, null, videoProject, null, false);
                            generatePreview(videoEditor, true);
                            completeRequest(intent);
                        } catch (Exception ex) {
                            if (videoEditor != null) {
                                videoEditor.release();
                                videoEditor = null;
                            }
                            throw ex;
                        }
                    } else {  // The project is already loaded.
                        logd("OP_VIDEO_EDITOR_LOAD: Was already loaded: " + projectPath);
                        completeRequest(intent, videoEditor, null, null, null, true);
                    }

                    break;
                }

                case OP_VIDEO_EDITOR_SET_ASPECT_RATIO: {
                    logd("OP_VIDEO_EDITOR_SET_ASPECT_RATIO");

                    videoEditor.setAspectRatio(intent.getIntExtra(PARAM_ASPECT_RATIO,
                            MediaProperties.ASPECT_RATIO_UNDEFINED));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_VIDEO_EDITOR_APPLY_THEME: {
                    logd("OP_VIDEO_EDITOR_APPLY_THEME");

                    // Apply the theme
                    applyThemeToMovie(videoEditor, intent.getStringExtra(PARAM_THEME));

                    final List<MovieMediaItem> mediaItems =
                            copyMediaItems(videoEditor.getAllMediaItems());
                    final List<MovieAudioTrack> audioTracks =
                            copyAudioTracks(videoEditor.getAllAudioTracks());

                    completeRequest(intent, videoEditor, null, mediaItems, audioTracks, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_VIDEO_EDITOR_EXPORT: {
                    logd("OP_VIDEO_EDITOR_EXPORT");
                    exportMovie(videoEditor, intent);
                    break;
                }

                case OP_VIDEO_EDITOR_CANCEL_EXPORT: {
                    logd("OP_VIDEO_EDITOR_CANCEL_EXPORT");
                    videoEditor.cancelExport(intent.getStringExtra(PARAM_FILENAME));
                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_VIDEO_EDITOR_EXPORT_STATUS: {
                    logd("OP_VIDEO_EDITOR_EXPORT_STATUS");
                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_VIDEO_EDITOR_SAVE: {
                    logd("OP_VIDEO_EDITOR_SAVE: " + projectPath);
                    videoEditor.save();

                    final VideoEditorProject videoProject = getProject(projectPath);
                    if (videoProject != null) {
                        videoProject.saveToXml();
                    }

                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_VIDEO_EDITOR_RELEASE: {
                    logd("OP_VIDEO_EDITOR_RELEASE: " + projectPath);
                    releaseEditor(projectPath);
                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_VIDEO_EDITOR_DELETE: {
                    logd("OP_VIDEO_EDITOR_DELETE: " + projectPath);
                    releaseEditor(projectPath);
                    // Delete all the files and the project folder.
                    FileUtils.deleteDir(new File(projectPath));
                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_MEDIA_ITEM_ADD_VIDEO_URI: {
                    logd("OP_MEDIA_ITEM_ADD_VIDEO_URI: " +
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    final Uri data = intent.getParcelableExtra(PARAM_FILENAME);
                    String filename = null;
                    // Get the filename
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(data,
                                new String[] {Video.Media.DATA}, null, null, null);
                        if (cursor.moveToFirst()) {
                            filename = cursor.getString(0);
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }

                    if (filename == null) {
                        throw new IllegalArgumentException("Media file not found: " + data);
                    }

                    final MediaItem mediaItem = new MediaVideoItem(videoEditor,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            filename,
                            intent.getIntExtra(PARAM_MEDIA_ITEM_RENDERING_MODE, 0));

                    videoEditor.insertMediaItem(mediaItem,
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));

                    // If this is the first media item, change the aspect ratio
                    final Integer aspectRatio;
                    if (videoEditor.getAllMediaItems().size() == 1) {
                        videoEditor.setAspectRatio(mediaItem.getAspectRatio());
                        aspectRatio = videoEditor.getAspectRatio();
                    } else {
                        aspectRatio = null;
                    }

                    // Apply the theme if any
                    final String themeId = intent.getStringExtra(PARAM_THEME);
                    if (themeId != null) {
                        applyThemeToMediaItem(videoEditor, themeId, mediaItem);
                    }

                    completeRequest(intent, videoEditor, null, new MovieMediaItem(mediaItem),
                            aspectRatio, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_ADD_IMAGE_URI: {
                    logd("OP_MEDIA_ITEM_ADD_IMAGE_URI: "
                        + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final Uri data = intent.getParcelableExtra(PARAM_FILENAME);
                    String filename = null;
                    // Get the filename
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(data,
                                new String[] {Images.Media.DATA, Images.Media.MIME_TYPE},
                                null, null, null);
                        if (cursor.moveToFirst()) {
                            filename = cursor.getString(0);
                            final String mimeType = cursor.getString(1);
                            if ("image/jpeg".equals(mimeType)) {
                                try {
                                    final File outputFile = new File(projectPath,
                                            "gallery_image_" + generateId() + ".jpg");
                                    if (ImageUtils.transformJpeg(filename, outputFile)) {
                                        filename = outputFile.getAbsolutePath();
                                    }
                                } catch (Exception ex) {
                                    // Ignore the exception and continue
                                    Log.w(TAG, "Could not transform JPEG: " + filename, ex);
                                }
                            }
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }

                    if (filename == null) {
                        throw new IllegalArgumentException("Media file not found: " + data);
                    }

                    final MediaItem mediaItem = new MediaImageItem(videoEditor,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            filename,
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_MEDIA_ITEM_RENDERING_MODE, 0));

                    videoEditor.insertMediaItem(mediaItem,
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));

                    // If this is the first media item, change the aspect ratio
                    final Integer aspectRatio;
                    if (videoEditor.getAllMediaItems().size() == 1) {
                        videoEditor.setAspectRatio(mediaItem.getAspectRatio());
                        aspectRatio = videoEditor.getAspectRatio();
                    } else {
                        aspectRatio = null;
                    }

                    // Apply the theme if any
                    final String themeId = intent.getStringExtra(PARAM_THEME);
                    if (themeId != null) {
                        applyThemeToMediaItem(videoEditor, themeId, mediaItem);
                    }

                    completeRequest(intent, videoEditor, null, new MovieMediaItem(mediaItem),
                            aspectRatio, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_LOAD: {
                    final Uri data = intent.getParcelableExtra(PARAM_FILENAME);
                    logd("OP_MEDIA_ITEM_LOAD: " + data);
                    final Intent requestIntent = intent;
                    new Thread() {
                        @Override
                        public void run() {
                            InputStream is = null;
                            FileOutputStream fos = null;
                            final File file = new File(projectPath, "download_" + generateId());

                            final Intent statusIntent = mIntentPool.get();
                            statusIntent.putExtra(PARAM_OP, OP_MEDIA_ITEM_LOAD_STATUS);
                            statusIntent.putExtra(PARAM_PROJECT_PATH,
                                    requestIntent.getStringExtra(PARAM_PROJECT_PATH));
                            statusIntent.putExtra(PARAM_INTENT, requestIntent);
                            try {
                                is = getContentResolver().openInputStream(data);
                                // Save the input stream to a file
                                fos = new FileOutputStream(file);
                                final byte[] readBuffer = new byte[2048];
                                int readBytes;
                                while ((readBytes = is.read(readBuffer)) >= 0) {
                                    fos.write(readBuffer, 0, readBytes);
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Cannot open input stream for: " + data);
                                statusIntent.putExtra(PARAM_EXCEPTION, ex);
                                file.delete();
                            } finally {
                                if (is != null) {
                                    try {
                                        is.close();
                                    } catch (IOException ex) {
                                        Log.e(TAG, "Cannot close input stream for: " + data);
                                    }
                                }

                                if (fos != null) {
                                    try {
                                        fos.flush();
                                        fos.close();
                                    } catch (IOException ex) {
                                        Log.e(TAG, "Cannot close output stream for: " + data);
                                    }
                                }
                            }

                            if (!statusIntent.hasExtra(PARAM_EXCEPTION)) {
                                final String filename = file.getAbsolutePath();
                                try {
                                    final String mimeType = getContentResolver().getType(data);
                                    if ("image/jpeg".equals(mimeType)) {
                                        final File outputFile = new File(projectPath,
                                                "download_" + generateId() + ".jpg");
                                        if (ImageUtils.transformJpeg(filename, outputFile)) {
                                            // Delete the downloaded file
                                            file.delete();
                                            statusIntent.putExtra(PARAM_FILENAME,
                                                    outputFile.getAbsolutePath());
                                        } else {
                                            statusIntent.putExtra(PARAM_FILENAME, filename);
                                        }
                                    } else {
                                        statusIntent.putExtra(PARAM_FILENAME, filename);
                                    }
                                } catch (Exception ex) {
                                    // Ignore the exception and continue
                                    Log.w(TAG, "Could not transform JPEG: " + filename, ex);
                                    statusIntent.putExtra(PARAM_FILENAME, filename);
                                }
                            }

                            mVideoThread.submit(statusIntent);
                        }
                    }.start();

                    break;
                }

                case OP_MEDIA_ITEM_LOAD_STATUS: {
                    final Intent originalIntent = (Intent)intent.getParcelableExtra(PARAM_INTENT);
                    if (intent.hasExtra(PARAM_EXCEPTION)) { //
                        final Exception exception =
                            (Exception)intent.getSerializableExtra(PARAM_EXCEPTION);
                        completeRequest(intent, videoEditor, exception, null, originalIntent,
                                true);
                    } else {
                        completeRequest(intent, videoEditor, null,
                                intent.getStringExtra(PARAM_FILENAME), originalIntent, true);
                    }
                    break;
                }

                case OP_MEDIA_ITEM_MOVE: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_MOVE: " + mediaItemId);

                    // Determine the position of the media item we are moving
                    final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
                    final int mediaItemsCount = mediaItems.size();
                    int movedItemPosition = -1;
                    MediaItem movedMediaItem = null;
                    for (int i = 0; i < mediaItemsCount; i++) {
                        final MediaItem mi = mediaItems.get(i);
                        if (mi.getId().equals(mediaItemId)) {
                            movedMediaItem = mi;
                            movedItemPosition = i;
                            break;
                        }
                    }

                    if (movedItemPosition == -1) {
                        throw new IllegalArgumentException("Moved MediaItem not found: " +
                                mediaItemId);
                    }

                    final Transition beginTransition = movedMediaItem.getBeginTransition();
                    final Transition endTransition = movedMediaItem.getEndTransition();

                    final String afterMediaItemId = intent.getStringExtra(
                            PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                    videoEditor.moveMediaItem(mediaItemId, afterMediaItemId);

                    // Apply the theme if any
                    final String themeId = intent.getStringExtra(PARAM_THEME);
                    if (themeId != null) {
                        // Apply the theme at the removed position
                        applyThemeAfterMove(videoEditor, themeId, movedMediaItem,
                                movedItemPosition, beginTransition, endTransition);
                    }

                    final List<MovieMediaItem> mediaItemsCopy = copyMediaItems(mediaItems);
                    completeRequest(intent, videoEditor, null, mediaItemsCopy, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_REMOVE: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_REMOVE: " + mediaItemId);

                    // Determine the position of the media item we are removing
                    final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
                    final int mediaItemsCount = mediaItems.size();
                    int removedItemPosition = -1;
                    MediaItem removedMediaItem = null;
                    for (int i = 0; i < mediaItemsCount; i++) {
                        if (mediaItems.get(i).getId().equals(mediaItemId)) {
                            removedMediaItem = mediaItems.get(i);
                            removedItemPosition = i;
                            break;
                        }
                    }

                    if (removedMediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }

                    final Transition beginTransition = removedMediaItem.getBeginTransition();
                    final Transition endTransition = removedMediaItem.getEndTransition();

                    videoEditor.removeMediaItem(mediaItemId);

                    // Apply the theme if any
                    MovieTransition movieTransition = null;
                    final String themeId = intent.getStringExtra(PARAM_THEME);
                    if (themeId != null && mediaItems.size() > 0) {
                        final Transition transition = applyThemeAfterRemove(videoEditor, themeId,
                                removedItemPosition, beginTransition, endTransition);
                        if (transition != null) {
                            movieTransition = new MovieTransition(transition);
                        }
                    }

                    completeRequest(intent, videoEditor, null, movieTransition, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_SET_RENDERING_MODE: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_SET_RENDERING_MODE: " + mediaItemId);

                    final MediaItem mediaItem = videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }
                    mediaItem.setRenderingMode(intent.getIntExtra(PARAM_MEDIA_ITEM_RENDERING_MODE,
                            MediaItem.RENDERING_MODE_BLACK_BORDER));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_SET_DURATION: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_SET_DURATION: " + mediaItemId);

                    final MediaImageItem mediaItem =
                        (MediaImageItem)videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }

                    final long durationMs = intent.getLongExtra(PARAM_DURATION, 0);
                    mediaItem.setDuration(durationMs);
                    // Adjust all effects to the new duration
                    final List<Effect> effects = mediaItem.getAllEffects();
                    for (Effect effect : effects) {
                        effect.setDuration(durationMs);
                    }

                    completeRequest(intent, videoEditor, null, new MovieMediaItem(mediaItem), null,
                            false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_SET_BOUNDARIES: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MediaVideoItem mediaItem =
                        (MediaVideoItem)videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }

                    mediaItem.setExtractBoundaries(intent.getLongExtra(PARAM_BEGIN_BOUNDARY, 0),
                            intent.getLongExtra(PARAM_END_BOUNDARY, 0));

                    final List<Overlay> overlays = mediaItem.getAllOverlays();
                    if (overlays.size() > 0) {
                        // Adjust the overlay
                        final Overlay overlay = overlays.get(0);
                        if (overlay.getStartTime() < mediaItem.getBoundaryBeginTime()) {
                            overlay.setStartTime(mediaItem.getBoundaryBeginTime());
                            overlay.setDuration(Math.min(overlay.getDuration(),
                                    mediaItem.getTimelineDuration()));
                        } else if (overlay.getStartTime() + overlay.getDuration() >
                                    mediaItem.getBoundaryEndTime()) {
                            overlay.setStartTime(Math.max(mediaItem.getBoundaryBeginTime(),
                                    mediaItem.getBoundaryEndTime() - overlay.getDuration()));
                            overlay.setDuration(mediaItem.getBoundaryEndTime() -
                                    overlay.getStartTime());
                        }
                    }

                    completeRequest(intent, videoEditor, null, new MovieMediaItem(mediaItem), null,
                            false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_MEDIA_ITEM_GET_THUMBNAILS: {
                    // Note that this command is executed in the thumbnail thread
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_GET_THUMBNAILS: " + mediaItemId);

                    final MediaItem mediaItem = videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }

                    final VideoEditor ve = videoEditor; // Just to make it "final"
                    mediaItem.getThumbnailList(
                            intent.getIntExtra(PARAM_WIDTH, 0),
                            intent.getIntExtra(PARAM_HEIGHT, 0),
                            intent.getLongExtra(PARAM_START_TIME, 0),
                            intent.getLongExtra(PARAM_END_TIME, 0),
                            intent.getIntExtra(PARAM_COUNT, 0),
                            intent.getIntArrayExtra(PARAM_INDICES),
                            new GetThumbnailListCallback() {
                                public void onThumbnail(Bitmap bitmap, int index) {
                                    completeRequest(
                                            intent, ve, null, bitmap,
                                            Integer.valueOf(index), false);
                                }
                            }
                            );

                    completeRequest(intent, videoEditor, null, null, null, true);
                    break;
                }

                case OP_MEDIA_ITEM_SET_VOLUME: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_SET_VOLUME: " + mediaItemId);

                    final MediaItem mediaItem = videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem != null && mediaItem instanceof MediaVideoItem) {
                        ((MediaVideoItem)mediaItem).setVolume(intent.getIntExtra(PARAM_VOLUME, 0));

                        completeRequest(intent, videoEditor, null, null, null, false);
                        generatePreview(videoEditor, false);
                        completeRequest(intent);
                    } else {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }
                    break;
                }

                case OP_MEDIA_ITEM_SET_MUTE: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_SET_MUTE: " + mediaItemId);

                    final MediaItem mediaItem = videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem != null && mediaItem instanceof MediaVideoItem) {
                        ((MediaVideoItem)mediaItem).setMute(intent.getBooleanExtra(PARAM_MUTE,
                                false));

                        completeRequest(intent, videoEditor, null, null, null, false);
                        generatePreview(videoEditor, false);
                        completeRequest(intent);
                    } else {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }
                    break;
                }

                case OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM: {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM: " + mediaItemId);

                    final MediaItem mediaItem = videoEditor.getMediaItem(mediaItemId);
                    if (mediaItem != null && mediaItem instanceof MediaVideoItem) {
                        final MediaVideoItem movieMediaItem = ((MediaVideoItem)mediaItem);
                        final WaveformData waveformData = movieMediaItem.getWaveformData();
                        if (waveformData == null) {
                            extractMediaItemAudioWaveform(intent, videoEditor, movieMediaItem);
                            completeRequest(intent, videoEditor, null,
                                    movieMediaItem.getWaveformData(), null, true);
                        } else {
                            completeRequest(intent, videoEditor, null, waveformData, null, true);
                        }
                    } else {
                        throw new IllegalArgumentException("MediaItem not found: " + mediaItemId);
                    }
                    break;
                }

                case OP_TRANSITION_INSERT_ALPHA: {
                    logd("OP_TRANSITION_INSERT_ALPHA: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final String afterMediaItemId =
                        intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                    final MediaItem afterMediaItem;
                    if (afterMediaItemId != null) {
                        afterMediaItem = videoEditor.getMediaItem(afterMediaItemId);
                    } else {
                        afterMediaItem = null;
                    }

                    final int maskRawResourceId = intent.getIntExtra(PARAM_TRANSITION_MASK,
                            R.raw.mask_contour);

                    final MediaItem beforeMediaItem = nextMediaItem(videoEditor, afterMediaItemId);
                    final Transition transition = new TransitionAlpha(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            afterMediaItem, beforeMediaItem,
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_TRANSITION_BEHAVIOR,
                                    Transition.BEHAVIOR_LINEAR),
                                    FileUtils.getMaskFilename(getApplicationContext(),
                                            maskRawResourceId),
                            intent.getIntExtra(PARAM_TRANSITION_BLENDING, 100),
                            intent.getBooleanExtra(PARAM_TRANSITION_INVERT, false));
                    videoEditor.addTransition(transition);

                    completeRequest(intent, videoEditor, null, transition, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_INSERT_CROSSFADE: {
                    logd("OP_TRANSITION_INSERT_CROSSFADE: "
                        + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final String afterMediaItemId =
                        intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                    final MediaItem afterMediaItem;
                    if (afterMediaItemId != null) {
                        afterMediaItem = videoEditor.getMediaItem(afterMediaItemId);
                    } else {
                        afterMediaItem = null;
                    }

                    final MediaItem beforeMediaItem = nextMediaItem(videoEditor, afterMediaItemId);
                    final Transition transition = new TransitionCrossfade(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            afterMediaItem, beforeMediaItem,
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_TRANSITION_BEHAVIOR,
                                    Transition.BEHAVIOR_LINEAR));
                    videoEditor.addTransition(transition);

                    completeRequest(intent, videoEditor, null, transition, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_INSERT_FADE_BLACK: {
                    logd("OP_TRANSITION_INSERT_FADE_TO_BLACK: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final String afterMediaItemId =
                        intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                    final MediaItem afterMediaItem;
                    if (afterMediaItemId != null) {
                        afterMediaItem = videoEditor.getMediaItem(afterMediaItemId);
                    } else {
                        afterMediaItem = null;
                    }

                    final MediaItem beforeMediaItem = nextMediaItem(videoEditor, afterMediaItemId);
                    final Transition transition = new TransitionFadeBlack(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            afterMediaItem, beforeMediaItem,
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_TRANSITION_BEHAVIOR,
                                    Transition.BEHAVIOR_LINEAR));
                    videoEditor.addTransition(transition);

                    completeRequest(intent, videoEditor, null, transition, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_INSERT_SLIDING: {
                    logd("OP_TRANSITION_INSERT_SLIDING: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final String afterMediaItemId =
                        intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                    final MediaItem afterMediaItem;
                    if (afterMediaItemId != null) {
                        afterMediaItem = videoEditor.getMediaItem(afterMediaItemId);
                    } else {
                        afterMediaItem = null;
                    }

                    final MediaItem beforeMediaItem = nextMediaItem(videoEditor, afterMediaItemId);
                    final Transition transition = new TransitionSliding(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            afterMediaItem, beforeMediaItem,
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_TRANSITION_BEHAVIOR,
                                    Transition.BEHAVIOR_LINEAR),
                                    intent.getIntExtra(PARAM_TRANSITION_DIRECTION,
                                            TransitionSliding.DIRECTION_RIGHT_OUT_LEFT_IN));
                    videoEditor.addTransition(transition);

                    completeRequest(intent, videoEditor, null, transition, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_REMOVE: {
                    logd("OP_TRANSITION_REMOVE: "
                        + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    videoEditor.removeTransition(intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_SET_DURATION: {
                    final String transitionId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_TRANSITION_SET_DURATION: " + transitionId);

                    final Transition transition = videoEditor.getTransition(transitionId);
                    if (transition == null) {
                        throw new IllegalArgumentException("Transition not found: " +
                                transitionId);
                    }
                    transition.setDuration(intent.getLongExtra(PARAM_DURATION, 0));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_TRANSITION_GET_THUMBNAIL: {
                    final String transitionId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_TRANSITION_GET_THUMBNAIL: " + transitionId);

                    final Transition transition = videoEditor.getTransition(transitionId);
                    if (transition == null) {
                        throw new IllegalArgumentException("Transition not found: " +
                                transitionId);
                    }

                    final int height = intent.getIntExtra(PARAM_HEIGHT, 0);
                    final MediaItem afterMediaItem = transition.getAfterMediaItem();
                    final Bitmap[] thumbnails = new Bitmap[2];
                    if (afterMediaItem != null) {
                        thumbnails[0] = afterMediaItem.getThumbnail(
                                (afterMediaItem.getWidth() * height) / afterMediaItem.getHeight(),
                                height, afterMediaItem.getTimelineDuration());
                    } else {
                        thumbnails[0] = null;
                    }

                    final MediaItem beforeMediaItem = transition.getBeforeMediaItem();
                    if (beforeMediaItem != null) {
                        thumbnails[1] = beforeMediaItem.getThumbnail(
                                (beforeMediaItem.getWidth() * height) / beforeMediaItem.getHeight(),
                                height, 0);
                    } else {
                        thumbnails[1] = null;
                    }

                    completeRequest(intent, videoEditor, null, thumbnails, null, true);
                    break;
                }

                case OP_EFFECT_ADD_COLOR: {
                    logd("OP_EFFECT_ADD_COLOR: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    // Remove any existing effect
                    final List<Effect> effects = mediaItem.getAllEffects();
                    for (Effect effect : effects) {
                        mediaItem.removeEffect(effect.getId());
                    }

                    final Effect effect = new EffectColor(mediaItem,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            intent.getLongExtra(PARAM_START_TIME, -1),
                            intent.getLongExtra(PARAM_DURATION, 0),
                            intent.getIntExtra(PARAM_EFFECT_TYPE, -1),
                            intent.getIntExtra(PARAM_EFFECT_PARAM, -1));
                    mediaItem.addEffect(effect);

                    completeRequest(intent, videoEditor, null, new MovieEffect(effect), null,
                            false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_EFFECT_ADD_IMAGE_KEN_BURNS: {
                    logd("OP_EFFECT_ADD_IMAGE_KEN_BURNS: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    // Remove any existing effect
                    final List<Effect> effects = mediaItem.getAllEffects();
                    for (Effect effect : effects) {
                        mediaItem.removeEffect(effect.getId());
                    }

                    final Effect effect = new EffectKenBurns(mediaItem,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            (Rect)intent.getParcelableExtra(PARAM_MEDIA_ITEM_START_RECT),
                            (Rect)intent.getParcelableExtra(PARAM_MEDIA_ITEM_END_RECT),
                            intent.getLongExtra(PARAM_START_TIME, 0),
                            intent.getLongExtra(PARAM_DURATION, 0));
                    mediaItem.addEffect(effect);

                    completeRequest(intent, videoEditor, null, new MovieEffect(effect), null,
                            false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_EFFECT_REMOVE: {
                    logd("OP_EFFECT_REMOVE: " + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    mediaItem.removeEffect(intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_OVERLAY_ADD: {
                    logd("OP_OVERLAY_ADD: " + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    // Remove any existing overlays
                    final List<Overlay> overlays = mediaItem.getAllOverlays();
                    for (Overlay overlay : overlays) {
                        mediaItem.removeOverlay(overlay.getId());
                    }

                    final int scaledWidth, scaledHeight;
                    if (mediaItem instanceof MediaVideoItem) {
                        scaledWidth = ((MediaVideoItem)mediaItem).getWidth();
                        scaledHeight = ((MediaVideoItem)mediaItem).getHeight();
                    } else {
                        scaledWidth = ((MediaImageItem)mediaItem).getScaledWidth();
                        scaledHeight = ((MediaImageItem)mediaItem).getScaledHeight();
                    }

                    final Bundle userAttributes = intent.getBundleExtra(PARAM_ATTRIBUTES);

                    final int overlayType = MovieOverlay.getType(userAttributes);
                    final String title = MovieOverlay.getTitle(userAttributes);
                    final String subTitle = MovieOverlay.getSubtitle(userAttributes);

                    final OverlayFrame overlay = new OverlayFrame(mediaItem,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            ImageUtils.buildOverlayBitmap(getApplicationContext(), null,
                                    overlayType, title, subTitle, scaledWidth, scaledHeight),
                            intent.getLongExtra(PARAM_START_TIME, -1),
                            intent.getLongExtra(PARAM_DURATION, 0));

                    // Set the user attributes
                    for (String name : userAttributes.keySet()) {
                        if (MovieOverlay.getAttributeType(name).equals(Integer.class)) {
                            overlay.setUserAttribute(name,
                                    Integer.toString(userAttributes.getInt(name)));
                        } else { // Strings
                            overlay.setUserAttribute(name, userAttributes.getString(name));
                        }
                    }
                    mediaItem.addOverlay(overlay);

                    completeRequest(intent, videoEditor, null, new MovieOverlay(overlay), null,
                            false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_OVERLAY_REMOVE: {
                    logd("OP_OVERLAY_REMOVE: " + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    mediaItem.removeOverlay(intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_OVERLAY_SET_START_TIME: {
                    logd("OP_OVERLAY_SET_START_TIME: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    final Overlay overlay = mediaItem.getOverlay(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    if (overlay == null) {
                        throw new IllegalArgumentException("Overlay not found: " +
                                intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    }

                    overlay.setStartTime(intent.getLongExtra(PARAM_START_TIME, 0));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_OVERLAY_SET_DURATION: {
                    logd("OP_OVERLAY_SET_DURATION: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    final Overlay overlay = mediaItem.getOverlay(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    if (overlay == null) {
                        throw new IllegalArgumentException("Overlay not found: " +
                                intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    }

                    overlay.setDuration(intent.getLongExtra(PARAM_DURATION, 0));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_OVERLAY_SET_ATTRIBUTES: {
                    logd("OP_OVERLAY_SET_ATTRIBUTES: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final MediaItem mediaItem = videoEditor.getMediaItem(
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    if (mediaItem == null) {
                        throw new IllegalArgumentException("MediaItem not found: " +
                                intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID));
                    }

                    final Overlay overlay = mediaItem.getOverlay(
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    if (overlay == null) {
                        throw new IllegalArgumentException("Overlay not found: " +
                                intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));
                    }

                    final int scaledWidth, scaledHeight;
                    if (mediaItem instanceof MediaVideoItem) {
                        scaledWidth = ((MediaVideoItem)mediaItem).getWidth();
                        scaledHeight = ((MediaVideoItem)mediaItem).getHeight();
                    } else {
                        scaledWidth = ((MediaImageItem)mediaItem).getScaledWidth();
                        scaledHeight = ((MediaImageItem)mediaItem).getScaledHeight();
                    }

                    final Bundle userAttributes = intent.getBundleExtra(PARAM_ATTRIBUTES);
                    final int overlayType = MovieOverlay.getType(userAttributes);
                    final String title = MovieOverlay.getTitle(userAttributes);
                    final String subTitle = MovieOverlay.getSubtitle(userAttributes);

                    ((OverlayFrame)overlay).setBitmap(
                            ImageUtils.buildOverlayBitmap(getApplicationContext(), null,
                                    overlayType, title, subTitle, scaledWidth, scaledHeight));

                    for (String name : userAttributes.keySet()) {
                        if (MovieOverlay.getAttributeType(name).equals(Integer.class)) {
                            overlay.setUserAttribute(name,
                                    Integer.toString(userAttributes.getInt(name)));
                        } else { // Strings
                            overlay.setUserAttribute(name, userAttributes.getString(name));
                        }
                    }

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, true);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_ADD: {
                    logd("OP_AUDIO_TRACK_ADD: " + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    final Uri data = intent.getParcelableExtra(PARAM_FILENAME);
                    String filename = null;
                    // Get the filename
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(data,
                                new String[] {Audio.Media.DATA}, null, null, null);
                        if (cursor.moveToFirst()) {
                            filename = cursor.getString(0);
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }

                    if (filename == null) {
                        throw new IllegalArgumentException("Media file not found: " + data);
                    }

                    final AudioTrack audioTrack = new AudioTrack(videoEditor,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID), filename);
                    audioTrack.enableDucking(DUCK_THRESHOLD, DUCK_TRACK_VOLUME);
                    audioTrack.setVolume(DEFAULT_AUDIO_TRACK_VOLUME);
                    if (intent.getBooleanExtra(PARAM_LOOP, false)) {
                        audioTrack.enableLoop();
                    } else {
                        audioTrack.disableLoop();
                    }

                    videoEditor.addAudioTrack(audioTrack);

                    completeRequest(intent, videoEditor, null, new MovieAudioTrack(audioTrack),
                            null, false);
                    // This is needed to decode the audio file into a PCM file
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_REMOVE: {
                    logd("OP_AUDIO_TRACK_REMOVE: "
                            + intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    videoEditor.removeAudioTrack(intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_SET_BOUNDARIES: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_SET_BOUNDARIES: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    audioTrack.setExtractBoundaries(intent.getLongExtra(PARAM_BEGIN_BOUNDARY, 0),
                            intent.getLongExtra(PARAM_END_BOUNDARY, 0));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_SET_LOOP: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_SET_LOOP: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    if (intent.getBooleanExtra(PARAM_LOOP, false)) {
                        audioTrack.enableLoop();
                    } else {
                        audioTrack.disableLoop();
                    }

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_SET_DUCK: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_SET_DUCK: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    if (intent.getBooleanExtra(PARAM_DUCK, false)) {
                        audioTrack.enableDucking(DUCK_THRESHOLD, DUCK_TRACK_VOLUME);
                    } else {
                        audioTrack.disableDucking();
                    }

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_SET_VOLUME: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_SET_VOLUME: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    audioTrack.setVolume(intent.getIntExtra(PARAM_VOLUME, 0));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_SET_MUTE: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_SET_MUTE: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    audioTrack.setMute(intent.getBooleanExtra(PARAM_MUTE, false));

                    completeRequest(intent, videoEditor, null, null, null, false);
                    generatePreview(videoEditor, false);
                    completeRequest(intent);
                    break;
                }

                case OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM: {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    logd("OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM: " + audioTrackId);

                    final AudioTrack audioTrack = videoEditor.getAudioTrack(audioTrackId);
                    if (audioTrack == null) {
                        throw new IllegalArgumentException("AudioTrack not found: " +
                                audioTrackId);
                    }

                    final WaveformData waveformData = audioTrack.getWaveformData();
                    if (waveformData == null) {
                        extractAudioTrackAudioWaveform(intent, videoEditor, audioTrack);
                        completeRequest(intent, videoEditor, null, audioTrack.getWaveformData(),
                                null, true);
                    } else {
                        completeRequest(intent, videoEditor, null, waveformData, null, true);
                    }
                    break;
                }

                default: {
                    throw new IllegalArgumentException("Unhandled operation: " + op);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            completeRequest(intent, videoEditor, ex, null, null, true);
        }
    }

    /**
     * Complete the request
     *
     * @param intent The intent
     * @param videoEditor The video editor
     * @param exception The exception
     * @param result The result object
     * @param extraResult The extra result object
     * @param finalize true if the request should be finalized
     */
    private void completeRequest(final Intent intent, final VideoEditor videoEditor,
            final Exception exception, final Object result, final Object extraResult,
            final boolean finalize) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                onIntentProcessed(intent, videoEditor, result, extraResult, exception, finalize);
            }
        });
    }

    /**
     * Complete the request
     *
     * @param intent The intent
     */
    private void completeRequest(final Intent intent) {
        mHandler.post (new Runnable() {
            @Override
            public void run() {
                finalizeRequest(intent);
                mIntentPool.put(intent);
            }
        });
    }

    /**
     * Callback called after the specified intent is processed.
     *
     * @param intent The intent
     * @param videoEditor The VideoEditor on which the operation was performed
     * @param result The result object
     * @param extraResult The extra result object
     * @param ex The exception
     * @param finalize true if the request should be finalized
     */
    @SuppressWarnings("unchecked")
    public void onIntentProcessed(final Intent intent, VideoEditor videoEditor,
            Object result, Object extraResult, Exception ex, boolean finalize) {

        final String projectPath = intent.getStringExtra(PARAM_PROJECT_PATH);
        final int op = intent.getIntExtra(PARAM_OP, -1);
        switch (op) {
            case OP_VIDEO_EDITOR_LOAD_PROJECTS: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final List<VideoEditorProject> projects = (List<VideoEditorProject>)result;
                for (ApiServiceListener listener : mListeners) {
                    listener.onProjectsLoaded(projects, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_CREATE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                // Release the old project
                if (mVideoProject != null) {
                    mVideoProject.release();
                    mVideoProject = null;
                }

                if (ex != null) {
                    FileUtils.deleteDir(new File(projectPath));
                } else {
                    mVideoProject = (VideoEditorProject)result;
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorCreated(projectPath, mVideoProject,
                            videoEditor != null ? videoEditor.getAllMediaItems() : null,
                            videoEditor != null ? videoEditor.getAllAudioTracks() : null, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_LOAD: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                if (result != null) { // A new project was created
                    if (mVideoProject != null) {
                        mVideoProject.release();
                        mVideoProject = null;
                    }
                    mVideoProject = (VideoEditorProject)result;
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorLoaded(projectPath, mVideoProject,
                            ex == null ? videoEditor.getAllMediaItems() : null,
                            ex == null ? videoEditor.getAllAudioTracks() : null, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_SET_ASPECT_RATIO: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final int aspectRatio = intent.getIntExtra(PARAM_ASPECT_RATIO,
                        MediaProperties.ASPECT_RATIO_UNDEFINED);
                if (ex == null) {
                    final VideoEditorProject videoProject = getProject(projectPath);
                    if (videoProject != null) {
                        videoProject.setAspectRatio(aspectRatio);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorAspectRatioSet(projectPath, aspectRatio, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_APPLY_THEME: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String theme = intent.getStringExtra(PARAM_THEME);
                if (ex == null) {
                    final VideoEditorProject videoProject = getProject(projectPath);
                    if (videoProject != null) {
                        videoProject.setTheme(theme);
                        videoProject.setMediaItems((List<MovieMediaItem>)result);
                        videoProject.setAudioTracks((List<MovieAudioTrack>)extraResult);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorThemeApplied(projectPath, theme, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_GENERATE_PREVIEW_PROGRESS: {
                final String className = intent.getStringExtra(PARAM_ATTRIBUTES);
                final String itemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final int action = intent.getIntExtra(PARAM_ACTION,
                        VideoEditor.MediaProcessingProgressListener.ACTION_DECODE);
                final int progress = intent.getIntExtra(PARAM_PROGRESS_VALUE, 0);

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorGeneratePreviewProgress(projectPath, className, itemId,
                            action, progress);
                }

                break;
            }

            case OP_VIDEO_EDITOR_EXPORT: {
                // The finalizeRequest() call and listener callbacks are done in
                // OP_VIDEO_EDITOR_EXPORT_STATUS intent handling (where we are
                // called originalIntent).
                break;
            }

            case OP_VIDEO_EDITOR_CANCEL_EXPORT: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorExportCanceled(projectPath,
                            intent.getStringExtra(PARAM_FILENAME));
                }
                break;
            }

            case OP_VIDEO_EDITOR_EXPORT_STATUS: {
                // This operation is for the service internal use only
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String filename = intent.getStringExtra(PARAM_FILENAME);
                if (intent.hasExtra(PARAM_EXCEPTION)) { // Complete
                    final Intent originalIntent = (Intent)intent.getParcelableExtra(PARAM_INTENT);
                    finalizeRequest(originalIntent);
                    mIntentPool.put(originalIntent);

                    final Exception exception =
                        (Exception)intent.getSerializableExtra(PARAM_EXCEPTION);
                    final VideoEditorProject videoProject = getProject(projectPath);
                    final boolean cancelled = intent.getBooleanExtra(PARAM_CANCELLED, false);
                    if (!cancelled && videoProject != null && exception == null) {
                        final Uri uri = (Uri)intent.getParcelableExtra(PARAM_MOVIE_URI);
                        videoProject.addExportedMovieUri(uri);
                    }

                    for (ApiServiceListener listener : mListeners) {
                        listener.onVideoEditorExportComplete(
                                projectPath, filename, exception, cancelled);
                    }
                } else { // Progress
                    for (ApiServiceListener listener : mListeners) {
                        listener.onVideoEditorExportProgress(projectPath, filename,
                                intent.getIntExtra(PARAM_PROGRESS_VALUE, -1));
                    }

                    // The original request is still pending
                }
                break;
            }

            case OP_VIDEO_EDITOR_SAVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorSaved(projectPath, ex);
                }
                break;
            }

            case OP_VIDEO_EDITOR_RELEASE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    videoProject.release();
                    if (mVideoProject == videoProject) {
                        mVideoProject = null;
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorReleased(projectPath, ex);
                }

                break;
            }

            case OP_VIDEO_EDITOR_DELETE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    videoProject.release();
                    if (mVideoProject == videoProject) {
                        mVideoProject = null;
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onVideoEditorDeleted(projectPath, ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_ADD_VIDEO_URI: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String afterMediaItemId =
                    intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);

                final MovieMediaItem movieMediaItem = (MovieMediaItem)result;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null && extraResult != null) {
                        // The aspect ratio has changed
                        videoProject.setAspectRatio((Integer)extraResult);
                    }

                    if (ex == null) {
                        videoProject.insertMediaItem(movieMediaItem, afterMediaItemId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemAdded(projectPath,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID), movieMediaItem,
                            afterMediaItemId, MediaVideoItem.class, (Integer)extraResult, ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_ADD_IMAGE_URI: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String afterMediaItemId =
                    intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID);

                final MovieMediaItem movieMediaItem = (MovieMediaItem)result;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null && extraResult != null) {
                        // The aspect ratio has changed
                        videoProject.setAspectRatio((Integer)extraResult);
                    }

                    if (ex == null) {
                        videoProject.insertMediaItem(movieMediaItem, afterMediaItemId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemAdded(projectPath,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID), movieMediaItem,
                            afterMediaItemId, MediaImageItem.class, (Integer)extraResult, ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_LOAD: {
                // Note that this message is handled only if the download
                // cannot start.
                final Uri data = (Uri)intent.getParcelableExtra(PARAM_FILENAME);
                final String mimeType = intent.getStringExtra(PARAM_ATTRIBUTES);
                if (finalize) {
                    finalizeRequest(intent);
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaLoaded(projectPath, data, mimeType, null, ex);
                }
                break;
            }

            case OP_MEDIA_ITEM_LOAD_STATUS: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final Intent originalIntent = (Intent)intent.getParcelableExtra(PARAM_INTENT);
                final Uri data = (Uri)originalIntent.getParcelableExtra(PARAM_FILENAME);
                final String mimeType = originalIntent.getStringExtra(PARAM_ATTRIBUTES);

                finalizeRequest(originalIntent);
                mIntentPool.put(originalIntent);

                final String filename = intent.getStringExtra(PARAM_FILENAME);

                if (ex == null && filename != null) {
                    final VideoEditorProject videoProject = getProject(projectPath);
                    videoProject.addDownload(data.toString(), mimeType, filename);
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaLoaded(projectPath, data, mimeType, filename, ex);
                }
                break;
            }

            case OP_MEDIA_ITEM_MOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                if (ex == null) {
                    final VideoEditorProject videoProject = getProject(projectPath);
                    if (videoProject != null) {
                        videoProject.setMediaItems((List<MovieMediaItem>)result);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemMoved(projectPath,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            intent.getStringExtra(PARAM_RELATIVE_STORYBOARD_ITEM_ID), ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_REMOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final MovieTransition transition = (MovieTransition)result;
                if (ex == null) {
                    final VideoEditorProject videoProject = getProject(projectPath);
                    if (videoProject != null) {
                        videoProject.removeMediaItem(mediaItemId, transition);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemRemoved(projectPath, mediaItemId, transition, ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_SET_RENDERING_MODE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final int renderingMode = intent.getIntExtra(PARAM_MEDIA_ITEM_RENDERING_MODE,
                        MediaItem.RENDERING_MODE_BLACK_BORDER);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final MovieMediaItem mediaItem = videoProject.getMediaItem(mediaItemId);
                    if (mediaItem != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            mediaItem.setRenderingMode(renderingMode);
                        } else {
                            mediaItem.setAppRenderingMode(mediaItem.getRenderingMode());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemRenderingModeSet(projectPath, mediaItemId, renderingMode,
                            ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_SET_DURATION: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.updateMediaItem((MovieMediaItem)result);
                    } else {
                        final MovieMediaItem oldMediaItem = videoProject.getMediaItem(mediaItemId);
                        if (oldMediaItem != null) {
                            videoProject.setClean(false);
                            oldMediaItem.setAppExtractBoundaries(0, oldMediaItem.getDuration());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemDurationSet(projectPath, mediaItemId,
                            intent.getLongExtra(PARAM_DURATION, 0), ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_SET_BOUNDARIES: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        final MovieMediaItem mediaItem = (MovieMediaItem)result;
                        videoProject.updateMediaItem(mediaItem);
                    } else {
                        final MovieMediaItem oldMediaItem = videoProject.getMediaItem(mediaItemId);
                        if (oldMediaItem != null) {
                            videoProject.setClean(false);
                            oldMediaItem.setAppExtractBoundaries(
                                    oldMediaItem.getBoundaryBeginTime(),
                                    oldMediaItem.getBoundaryEndTime());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemBoundariesSet(projectPath, mediaItemId,
                            intent.getLongExtra(PARAM_BEGIN_BOUNDARY, 0),
                            intent.getLongExtra(PARAM_END_BOUNDARY, 0), ex);
                }

                break;
            }

            case OP_MEDIA_ITEM_GET_THUMBNAILS: {
                if (finalize) {
                    finalizeRequest(intent);
                    break;
                }

                final Bitmap bitmap = (Bitmap)result;
                final int index = (Integer)extraResult;
                boolean used = false;
                for (ApiServiceListener listener : mListeners) {
                    used |= listener.onMediaItemThumbnail(projectPath,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            bitmap, index, intent.getIntExtra(PARAM_TOKEN, 0),
                            ex);
                }

                if (used == false) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }

                break;
            }

            case OP_MEDIA_ITEM_SET_VOLUME: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieMediaItem mediaItem = videoProject.getMediaItem(mediaItemId);
                    if (mediaItem != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            mediaItem.setVolume(intent.getIntExtra(PARAM_VOLUME, 0));
                        } else {
                            mediaItem.setAppVolume(mediaItem.getVolume());
                        }
                    }
                }

                break;
            }

            case OP_MEDIA_ITEM_SET_MUTE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieMediaItem mediaItem = videoProject.getMediaItem(mediaItemId);
                    if (mediaItem != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            mediaItem.setMute(intent.getBooleanExtra(PARAM_MUTE, false));
                        } else {
                            mediaItem.setAppMute(mediaItem.isMuted());
                        }
                    }
                }

                break;
            }

            case OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM_STATUS: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final int progress = intent.getIntExtra(PARAM_PROGRESS_VALUE, 0);

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemExtractAudioWaveformProgress(projectPath, mediaItemId,
                        progress);
                }

                break;
            }

            case OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (ex == null && videoProject != null) {
                    if (result != null) {
                        final MovieMediaItem mediaItem = videoProject.getMediaItem(mediaItemId);
                        if (mediaItem != null) {
                            videoProject.setClean(false);
                            mediaItem.setWaveformData((WaveformData)result);
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onMediaItemExtractAudioWaveformComplete(projectPath, mediaItemId, ex);
                }

                break;
            }

            case OP_TRANSITION_INSERT_ALPHA:
            case OP_TRANSITION_INSERT_CROSSFADE:
            case OP_TRANSITION_INSERT_FADE_BLACK:
            case OP_TRANSITION_INSERT_SLIDING: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String afterMediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);

                final MovieTransition movieTransition;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final Transition transition = (Transition)result;
                    // This is null for start transitions
                    if (ex == null) {
                        movieTransition = new MovieTransition(transition);
                        videoProject.addTransition(movieTransition, afterMediaItemId);
                    } else {
                        movieTransition = null;
                    }
                } else {
                    movieTransition = null;
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onTransitionInserted(projectPath, movieTransition,
                            afterMediaItemId, ex);
                }

                break;
            }

            case OP_TRANSITION_REMOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String transitionId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.removeTransition(transitionId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onTransitionRemoved(projectPath, transitionId, ex);
                }

                break;
            }

            case OP_TRANSITION_SET_DURATION: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String transitionId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final long durationMs = intent.getLongExtra(PARAM_DURATION, 0);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final MovieTransition transition = videoProject.getTransition(transitionId);
                    if (transition != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            transition.setDuration(durationMs);
                        } else {
                            transition.setAppDuration(transition.getDuration());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onTransitionDurationSet(projectPath, transitionId, durationMs, ex);
                }

                break;
            }

            case OP_TRANSITION_GET_THUMBNAIL: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final Bitmap[] bitmaps = (Bitmap[])result;
                boolean used = false;
                for (ApiServiceListener listener : mListeners) {
                    used |= listener.onTransitionThumbnails(projectPath,
                            intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID),
                            bitmaps, ex);
                }

                if (used == false) {
                    if (bitmaps != null) {
                        for (int i = 0; i < bitmaps.length; i++) {
                            if (bitmaps[i] != null) {
                                bitmaps[i].recycle();
                            }
                        }
                    }
                }

                break;
            }

            case OP_OVERLAY_ADD: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);

                final MovieOverlay movieOverlay = (MovieOverlay)result;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.addOverlay(mediaItemId, movieOverlay);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onOverlayAdded(projectPath, movieOverlay, mediaItemId, ex);
                }

                break;
            }

            case OP_OVERLAY_REMOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                final String overlayId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.removeOverlay(mediaItemId, overlayId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onOverlayRemoved(projectPath, overlayId, mediaItemId, ex);
                }

                break;
            }

            case OP_OVERLAY_SET_START_TIME: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                final String overlayId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final long startTimeMs = intent.getLongExtra(PARAM_START_TIME, 0);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final MovieOverlay overlay = videoProject.getOverlay(mediaItemId, overlayId);
                    if (overlay != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            overlay.setStartTime(startTimeMs);
                        } else {
                            overlay.setAppStartTime(overlay.getStartTime());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onOverlayStartTimeSet(projectPath, overlayId, mediaItemId,
                            startTimeMs, ex);
                }

                break;
            }

            case OP_OVERLAY_SET_DURATION: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                final String overlayId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final long durationMs = intent.getLongExtra(PARAM_DURATION, 0);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final MovieOverlay overlay = videoProject.getOverlay(mediaItemId, overlayId);
                    if (overlay != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            overlay.setDuration(durationMs);
                        } else {
                            overlay.setAppDuration(overlay.getDuration());
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onOverlayDurationSet(projectPath, overlayId, mediaItemId,
                            durationMs, ex);
                }

                break;
            }

            case OP_OVERLAY_SET_ATTRIBUTES: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                final String overlayId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final Bundle userAttributes = intent.getBundleExtra(PARAM_ATTRIBUTES);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        final MovieOverlay overlay = videoProject.getOverlay(mediaItemId,
                                overlayId);
                        if (overlay != null) {
                            videoProject.setClean(false);
                            overlay.updateUserAttributes(userAttributes);
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onOverlayUserAttributesSet(projectPath, overlayId, mediaItemId,
                            userAttributes, ex);
                }

                break;
            }

            case OP_EFFECT_ADD_COLOR:
            case OP_EFFECT_ADD_IMAGE_KEN_BURNS:{
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);

                final MovieEffect movieEffect = (MovieEffect)result;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.addEffect(mediaItemId, movieEffect);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onEffectAdded(projectPath, movieEffect, mediaItemId, ex);
                }

                break;
            }

            case OP_EFFECT_REMOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String mediaItemId = intent.getStringExtra(
                        PARAM_RELATIVE_STORYBOARD_ITEM_ID);
                final String effectId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.removeEffect(mediaItemId, effectId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onEffectRemoved(projectPath, effectId, mediaItemId, ex);
                }

                break;
            }

            case OP_AUDIO_TRACK_ADD: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final MovieAudioTrack movieAudioTrack = (MovieAudioTrack)result;
                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.addAudioTrack(movieAudioTrack);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onAudioTrackAdded(projectPath, movieAudioTrack, ex);
                }

                break;
            }

            case OP_AUDIO_TRACK_REMOVE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        videoProject.removeAudioTrack(audioTrackId);
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onAudioTrackRemoved(projectPath, audioTrackId, ex);
                }

                break;
            }

            case OP_AUDIO_TRACK_SET_BOUNDARIES: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final long beginBoundary = intent.getLongExtra(PARAM_BEGIN_BOUNDARY, 0);
                final long endBoundary = intent.getLongExtra(PARAM_END_BOUNDARY, 0);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    if (ex == null) {
                        final MovieAudioTrack audioTrack =
                            videoProject.getAudioTrack(audioTrackId);
                        if (audioTrack != null) {
                            videoProject.setClean(false);
                            audioTrack.setExtractBoundaries(beginBoundary, endBoundary);
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onAudioTrackBoundariesSet(projectPath, audioTrackId,
                            beginBoundary, endBoundary, ex);
                }

                break;
            }

            case OP_AUDIO_TRACK_SET_LOOP: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieAudioTrack audioTrack = videoProject.getAudioTrack(audioTrackId);
                    if (audioTrack != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            audioTrack.enableLoop(intent.getBooleanExtra(PARAM_LOOP, false));
                        } else {
                            audioTrack.enableAppLoop(audioTrack.isLooping());
                        }
                    }
                }

                break;
            }

            case OP_AUDIO_TRACK_SET_DUCK: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieAudioTrack audioTrack = videoProject.getAudioTrack(audioTrackId);
                    if (audioTrack != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            audioTrack.enableDucking(intent.getBooleanExtra(PARAM_DUCK, false));
                        } else {
                            audioTrack.enableAppDucking(audioTrack.isDuckingEnabled());
                        }
                    }
                }

                break;
            }

            case OP_AUDIO_TRACK_SET_VOLUME: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieAudioTrack audioTrack = videoProject.getAudioTrack(audioTrackId);
                    if (audioTrack != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            audioTrack.setVolume(intent.getIntExtra(PARAM_VOLUME, 0));
                        } else {
                            audioTrack.setAppVolume(audioTrack.getVolume());
                        }
                    }
                }

                break;
            }

            case OP_AUDIO_TRACK_SET_MUTE: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final VideoEditorProject videoProject = getProject(projectPath);
                if (videoProject != null) {
                    final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                    final MovieAudioTrack audioTrack = videoProject.getAudioTrack(audioTrackId);
                    if (audioTrack != null) {
                        videoProject.setClean(false);
                        if (ex == null) {
                            audioTrack.setMute(intent.getBooleanExtra(PARAM_MUTE, false));
                        } else {
                            audioTrack.setAppMute(audioTrack.isMuted());
                        }
                    }
                }

                break;
            }

            case OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM_STATUS: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);
                final int progress = intent.getIntExtra(PARAM_PROGRESS_VALUE, 0);

                for (ApiServiceListener listener : mListeners) {
                    listener.onAudioTrackExtractAudioWaveformProgress(projectPath, audioTrackId,
                            progress);
                }

                break;
            }

            case OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM: {
                if (finalize) {
                    finalizeRequest(intent);
                }

                final String audioTrackId = intent.getStringExtra(PARAM_STORYBOARD_ITEM_ID);

                final VideoEditorProject videoProject = getProject(projectPath);
                if (ex == null && videoProject != null) {
                    if (result != null) {
                        final MovieAudioTrack audioTrack =
                            videoProject.getAudioTrack(audioTrackId);
                        if (audioTrack != null) {
                            videoProject.setClean(false);
                            audioTrack.setWaveformData((WaveformData)result);
                        }
                    }
                }

                for (ApiServiceListener listener : mListeners) {
                    listener.onAudioTrackExtractAudioWaveformComplete(projectPath,
                            audioTrackId, ex);
                }

                break;
            }

            default: {
                if (finalize) {
                    finalizeRequest(intent);
                }
                break;
            }
        }

        if (finalize) {
            mIntentPool.put(intent);
        }
    }

    /**
     * Finalizes a request. Calls the listeners that are interested in project status
     * change and stops this service if there are no more pending intents.
     *
     * @param intent The intent that just completed
     */
    private void finalizeRequest(Intent intent) {
        mPendingIntents.remove(intent.getStringExtra(PARAM_REQUEST_ID));

        final String projectPath = intent.getStringExtra(PARAM_PROJECT_PATH);
        if (projectPath != null) {
            final boolean projectEdited = isProjectBeingEdited(projectPath);
            if (projectEdited == false) {
                for (ApiServiceListener listener : mListeners) {
                    listener.onProjectEditState(projectPath, projectEdited);
                }
            }
        }

        if (mPendingIntents.size() == 0) {
            // Cancel the current timer if any. Extend the timeout by 5000 ms.
            mHandler.removeCallbacks(mStopRunnable);

            // Start a timer which will stop the service if the queue of
            // pending intent will be empty at that time.
            // This prevents the service from starting & stopping too often.
            mHandler.postDelayed(mStopRunnable, 5000);
            logd("completeRequest: Stopping service in 5000 ms");
        }
    }

    /**
     * Checks if the current project is the project specified by the specified path.
     *
     * @param projectPath The project path
     *
     * @return The video editor project
     */
    private VideoEditorProject getProject(String projectPath) {
        if (mVideoProject != null) {
            if (mVideoProject.getPath().equals(projectPath)) {
                return mVideoProject;
            }
        }

        return null;
    }

    /**
     * Check if the current editor is the project specified by the specified path
     *
     * @param projectPath The project path
     *
     * @return The video editor
     */
    private synchronized VideoEditor getVideoEditor(String projectPath) {
        if (mVideoEditor != null) {
            if (mVideoEditor.getPath().equals(projectPath)) {
                return mVideoEditor;
            }
        }

        return null;
    }

    /**
     * Release the editor
     */
    private synchronized void releaseEditor() {
        if (mVideoEditor != null) {
            logd("releaseEditor (current): " + mVideoEditor.getPath());
            mVideoEditor.release();
            mVideoEditor = null;
            mGeneratePreviewListener = null;

            System.gc();
        }
    }

    /**
     * Release the specified editor
     *
     * @param projectPath The project path
     */
    private synchronized void releaseEditor(String projectPath) {
        if (mVideoEditor != null) {
            if (mVideoEditor.getPath().equals(projectPath)) {
                logd("releaseEditor: " + projectPath);
                mVideoEditor.release();
                mVideoEditor = null;
                mGeneratePreviewListener = null;

                System.gc();
            }
        }
    }

    /**
     * Release the current editor if it is *not* the current editor
     *
     * @param projectPath The project path
     *
     * @return The current video editor
     */
    private synchronized VideoEditor releaseEditorNot(String projectPath) {
        if (mVideoEditor != null) {
            if (!mVideoEditor.getPath().equals(projectPath)) {
                logd("releaseEditorNot: " + mVideoEditor.getPath());
                mVideoEditor.release();
                mVideoEditor = null;
                mGeneratePreviewListener = null;

                System.gc();
            }
        }

        return mVideoEditor;
    }

    /**
     * Generate the preview
     *
     * @param videoEditor The video editor
     * @param updatePreviewFrame true to show preview frame when done
     */
    private void generatePreview(VideoEditor videoEditor, boolean updatePreviewFrame) {
        try {
            videoEditor.generatePreview(mGeneratePreviewListener);
            if (mGeneratePreviewListener != null) {
                // This is the last callback which is always fired last to
                // let the UI know that generate preview completed
                mGeneratePreviewListener.onProgress(null,
                        updatePreviewFrame ? ACTION_UPDATE_FRAME : ACTION_NO_FRAME_UPDATE, 100);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Exports a movie in a distinct worker thread.
     *
     * @param videoEditor The video editor
     * @param intent The intent
     */
    private void exportMovie(final VideoEditor videoEditor, final Intent intent) {
        mExportCancelled = false;
        new Thread() {
            @Override
            public void run() {
                final String filename = intent.getStringExtra(PARAM_FILENAME);
                final int height = intent.getIntExtra(PARAM_HEIGHT, -1);
                final int bitrate = intent.getIntExtra(PARAM_BITRATE, -1);

                // Create the export status Intent
                final Intent statusIntent = mIntentPool.get();
                statusIntent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_EXPORT_STATUS);
                statusIntent.putExtra(PARAM_PROJECT_PATH, intent.getStringExtra(
                        PARAM_PROJECT_PATH));
                statusIntent.putExtra(PARAM_FILENAME, filename);
                statusIntent.putExtra(PARAM_INTENT, intent);
                Exception resultException = null;

                try {
                    videoEditor.export(filename, height, bitrate, new ExportProgressListener() {
                        @Override
                        public void onProgress(VideoEditor videoEditor, String filename,
                                int progress) {
                            final Intent progressIntent = mIntentPool.get();
                            progressIntent.putExtra(PARAM_OP, OP_VIDEO_EDITOR_EXPORT_STATUS);
                            progressIntent.putExtra(PARAM_PROJECT_PATH, intent.getStringExtra(
                                    PARAM_PROJECT_PATH));
                            progressIntent.putExtra(PARAM_FILENAME, filename);
                            progressIntent.putExtra(PARAM_INTENT, intent);
                            progressIntent.putExtra(PARAM_PROGRESS_VALUE, progress);
                            mVideoThread.submit(progressIntent);
                            logv("Export progress: " + progress + " for: " + filename);
                        }
                    });

                    statusIntent.putExtra(PARAM_CANCELLED, mExportCancelled);
                    if (!mExportCancelled) {
                        if (new File(filename).exists()) {
                            statusIntent.putExtra(PARAM_MOVIE_URI, exportToGallery(filename));
                        } else {
                            resultException = new IllegalStateException("Export file does not exist: " + filename);
                        }
                        logv("Export complete for: " + filename);
                    } else {
                        logv("Export cancelled by user, file name: " + filename);
                    }
                } catch (Exception ex) {
                    logv("Export error for: " + filename);
                    ex.printStackTrace();
                    resultException = ex;
                }

                // Complete the request
                statusIntent.putExtra(PARAM_EXCEPTION, resultException);
                mVideoThread.submit(statusIntent);
            }
        }.start();
    }

    /**
     * Extract the audio waveform of a media item
     *
     * @param intent The original Intent
     * @param videoEditor The video editor
     * @param mediaItem The media item
     */
    private void extractMediaItemAudioWaveform(final Intent intent, final VideoEditor videoEditor,
            final MediaVideoItem mediaItem) throws IOException {
        mediaItem.extractAudioWaveform(
            new ExtractAudioWaveformProgressListener() {
            @Override
            public void onProgress(int progress) {
                final Intent progressIntent = mIntentPool.get();
                progressIntent.putExtra(PARAM_OP, OP_MEDIA_ITEM_EXTRACT_AUDIO_WAVEFORM_STATUS);
                progressIntent.putExtra(PARAM_PROJECT_PATH,
                        intent.getStringExtra(PARAM_PROJECT_PATH));
                progressIntent.putExtra(PARAM_INTENT, intent);
                progressIntent.putExtra(PARAM_STORYBOARD_ITEM_ID, mediaItem.getId());
                progressIntent.putExtra(PARAM_PROGRESS_VALUE, progress);

                completeRequest(progressIntent, videoEditor, null, null, null, true);
            }
        });
    }

    /**
     * Extract the audio waveform of an AudioTrack
     *
     * @param intent The original Intent
     * @param videoEditor The video editor
     * @param audioTrack The audio track
     */
    private void extractAudioTrackAudioWaveform(final Intent intent, final VideoEditor videoEditor,
            final AudioTrack audioTrack) throws IOException {
        audioTrack.extractAudioWaveform(
            new ExtractAudioWaveformProgressListener() {
            @Override
            public void onProgress(int progress) {
                final Intent progressIntent = mIntentPool.get();
                progressIntent.putExtra(PARAM_OP,
                        OP_AUDIO_TRACK_EXTRACT_AUDIO_WAVEFORM_STATUS);
                progressIntent.putExtra(PARAM_PROJECT_PATH,
                        intent.getStringExtra(PARAM_PROJECT_PATH));
                progressIntent.putExtra(PARAM_INTENT, intent);
                progressIntent.putExtra(PARAM_STORYBOARD_ITEM_ID, audioTrack.getId());
                progressIntent.putExtra(PARAM_PROGRESS_VALUE, progress);

                completeRequest(progressIntent, videoEditor, null, null, null, true);
            }
        });
    }

    /**
     * Get the media item following the specified media item
     *
     * @param videoEditor The video editor
     * @param mediaItemId The media item id
     * @return The next media item
     */
    private static MediaItem nextMediaItem(VideoEditor videoEditor, String mediaItemId) {
        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
        if (mediaItemId == null) {
            if (mediaItems.size() > 0) {
                return mediaItems.get(0);
            }
        } else {
            final int mediaItemCount = mediaItems.size();
            for (int i = 0; i < mediaItemCount; i++) {
                MediaItem mi = mediaItems.get(i);
                if (mi.getId().equals(mediaItemId)) {
                    if (i < mediaItemCount - 1) {
                        return mediaItems.get(i + 1);
                    } else {
                        break;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Export the movie to the Gallery
     *
     * @param filename The filename
     * @return The video MediaStore URI
     */
    private Uri exportToGallery(String filename) {
        // Save the name and description of a video in a ContentValues map.
        final ContentValues values = new ContentValues(2);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, filename);
        // Add a new record (identified by uri)
        final Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values);
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://"+ filename)));
        return uri;
    }

    /**
     * Apply a theme to the entire movie. This method shall be used when the
     * theme is changing.
     *
     * @param videoEditor The video editor
     * @param themeId The theme id
     */
    private void applyThemeToMovie(VideoEditor videoEditor, String themeId) throws IOException {
        final Context context = getApplicationContext();
        final MovieTheme theme = MovieTheme.getTheme(context, themeId);
        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();

        // Add the transitions
        final int mediaItemsCount = mediaItems.size();
        if (mediaItemsCount > 0) {
            // Remove all the transitions
            for (int i = 0; i < mediaItemsCount; i++) {
                final MediaItem mi = mediaItems.get(i);
                if (i == 0) {
                    final Transition beginTransition = mi.getBeginTransition();
                    if (beginTransition != null) {
                        videoEditor.removeTransition(beginTransition.getId());
                    }
                }

                final Transition endTransition = mi.getEndTransition();
                if (endTransition != null) {
                    videoEditor.removeTransition(endTransition.getId());
                }
            }

            // Add the begin transition to the first media item
            final MovieTransition beginMovieTransition = theme.getBeginTransition();
            if (beginMovieTransition != null) {
                final MediaItem firstMediaItem = mediaItems.get(0);
                videoEditor.addTransition(
                        beginMovieTransition.buildTransition(context, null, firstMediaItem));
            }

            // Add the mid transitions
            final MovieTransition midMovieTransition = theme.getMidTransition();
            if (midMovieTransition != null) {
                for (int i = 0; i < mediaItemsCount - 1; i++) {
                    videoEditor.addTransition(
                            midMovieTransition.buildTransition(context,
                                    mediaItems.get(i), mediaItems.get(i + 1)));
                }
            }

            // Add the end transition to the last media item
            final MovieTransition endMovieTransition = theme.getEndTransition();
            if (endMovieTransition != null) {
                final MediaItem lastMediaItem = mediaItems.get(mediaItemsCount - 1);
                videoEditor.addTransition(
                        endMovieTransition.buildTransition(context, lastMediaItem, null));
            }
        }

        // Add the overlay
        final MovieOverlay movieOverlay = theme.getOverlay();
        if (movieOverlay != null && mediaItemsCount > 0) {
            // Remove all the overlay for the first media item
            final MediaItem mediaItem = mediaItems.get(0);
            final List<Overlay> overlays = mediaItem.getAllOverlays();
            if (overlays.size() > 0) {
                mediaItem.removeOverlay(overlays.get(0).getId());
            }

            // Add the new overlay
            final int scaledWidth, scaledHeight;
            if (mediaItem instanceof MediaVideoItem) {
                scaledWidth = ((MediaVideoItem)mediaItem).getWidth();
                scaledHeight = ((MediaVideoItem)mediaItem).getHeight();
            } else {
                scaledWidth = ((MediaImageItem)mediaItem).getScaledWidth();
                scaledHeight = ((MediaImageItem)mediaItem).getScaledHeight();
            }

            final Overlay overlay = new OverlayFrame(mediaItem, generateId(),
                    ImageUtils.buildOverlayBitmap(getApplicationContext(), null,
                            movieOverlay.getType(), movieOverlay.getTitle(),
                            movieOverlay.getSubtitle(), scaledWidth, scaledHeight),
                            movieOverlay.getStartTime(), movieOverlay.getDuration());

            // Set the user attributes
            final Bundle userAttributes = movieOverlay.buildUserAttributes();
            for (String name : userAttributes.keySet()) {
                if (MovieOverlay.getAttributeType(name).equals(Integer.class)) {
                    overlay.setUserAttribute(name,
                            Integer.toString(userAttributes.getInt(name)));
                } else { // Strings
                    overlay.setUserAttribute(name, userAttributes.getString(name));
                }
            }
            mediaItem.addOverlay(overlay);
        }

        final MovieAudioTrack at = theme.getAudioTrack();
        if (at != null) {
            // Remove all audio tracks
            final List<AudioTrack> audioTracks = videoEditor.getAllAudioTracks();
            while (audioTracks.size() > 0) {
                videoEditor.removeAudioTrack(audioTracks.get(0).getId());
            }

            // Add the new audio track
            final AudioTrack audioTrack = new AudioTrack(videoEditor, generateId(),
                    FileUtils.getAudioTrackFilename(context, at.getRawResourceId()));

            // Enable looping if necessary
            if (at.isLooping()) {
                audioTrack.enableLoop();
            }

            // Enable ducking
            audioTrack.enableDucking(DUCK_THRESHOLD, DUCK_TRACK_VOLUME);
            audioTrack.setVolume(DEFAULT_AUDIO_TRACK_VOLUME);
            videoEditor.addAudioTrack(audioTrack);
        }
    }

    /**
     * Apply a theme
     *
     * @param videoEditor The video editor
     * @param themeId The theme id
     * @param mediaItem The mediaItem
     */
    private void applyThemeToMediaItem(VideoEditor videoEditor, String themeId,
            MediaItem mediaItem) throws IOException {
        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
        final int mediaItemsCount = mediaItems.size();
        if (mediaItemsCount == 0) {
            return;
        }

        // We would only add transitions if the transitions don't exist
        final Transition beginTransition = mediaItem.getBeginTransition();
        final Transition endTransition = mediaItem.getEndTransition();

        final Context context = getApplicationContext();
        final MovieTheme theme = MovieTheme.getTheme(context, themeId);

        final MediaItem firstMediaItem = mediaItems.get(0);
        if (beginTransition == null) {
            // Add the begin transition
            final MovieTransition beginMovieTransition = theme.getBeginTransition();
            if (beginMovieTransition != null) {
                if (firstMediaItem == mediaItem) {
                    videoEditor.addTransition(
                            beginMovieTransition.buildTransition(context, null, mediaItem));
                }
            }
        }

        // Add the mid transitions
        final MovieTransition midMovieTransition = theme.getMidTransition();
        if (midMovieTransition != null) {
            for (int i = 0; i < mediaItemsCount; i++) {
                final MediaItem mi = mediaItems.get(i);
                if (mi == mediaItem) {
                    if (i > 0) { // Not the first one
                        if (beginTransition == null) {
                            // Add transition before this media item
                            videoEditor.addTransition(midMovieTransition.buildTransition(context,
                                    mediaItems.get(i - 1), mi));
                        }
                    }

                    if (i < mediaItemsCount - 1) { // Not the last one
                        if (endTransition == null) {
                            // Add the transition after this media item
                            videoEditor.addTransition(midMovieTransition.buildTransition(context,
                                        mi, mediaItems.get(i + 1)));
                        }
                    }
                    break;
                }
            }
        }

        if (endTransition == null) {
            // Add the end transition to the last media item
            final MovieTransition endMovieTransition = theme.getEndTransition();
            final MediaItem lastMediaItem = mediaItems.get(mediaItemsCount - 1);
            if (endMovieTransition != null && lastMediaItem == mediaItem) {
                videoEditor.addTransition(
                        endMovieTransition.buildTransition(context, lastMediaItem, null));
            }
        }

        // Add the overlay
        final MovieOverlay movieOverlay = theme.getOverlay();
        if (movieOverlay != null) {
            if (firstMediaItem == mediaItem) {
                // Remove the overlay
                final List<Overlay> overlays = mediaItem.getAllOverlays();
                if (overlays.size() > 0) {
                    mediaItem.removeOverlay(overlays.get(0).getId());
                }

                // Add the new overlay
                final int scaledWidth, scaledHeight;
                if (mediaItem instanceof MediaVideoItem) {
                    scaledWidth = ((MediaVideoItem)mediaItem).getWidth();
                    scaledHeight = ((MediaVideoItem)mediaItem).getHeight();
                } else {
                    scaledWidth = ((MediaImageItem)mediaItem).getScaledWidth();
                    scaledHeight = ((MediaImageItem)mediaItem).getScaledHeight();
                }

                final Overlay overlay = new OverlayFrame(mediaItem, generateId(),
                        ImageUtils.buildOverlayBitmap(getApplicationContext(), null,
                                movieOverlay.getType(), movieOverlay.getTitle(),
                                movieOverlay.getSubtitle(), scaledWidth, scaledHeight),
                        movieOverlay.getStartTime(),
                        Math.min(movieOverlay.getDuration(),
                                mediaItem.getDuration() - movieOverlay.getStartTime()));

                // Set the user attributes
                final Bundle userAttributes = movieOverlay.buildUserAttributes();
                for (String name : userAttributes.keySet()) {
                    if (MovieOverlay.getAttributeType(name).equals(Integer.class)) {
                        overlay.setUserAttribute(name,
                                Integer.toString(userAttributes.getInt(name)));
                    } else { // Strings
                        overlay.setUserAttribute(name, userAttributes.getString(name));
                    }
                }
                mediaItem.addOverlay(overlay);
            }
        }
    }

    /**
     * Apply theme transitions after an item was removed
     *
     * @param videoEditor The video editor
     * @param themeId The theme id
     * @param removedItemPosition The position of the removed item
     * @param beginTransition The removed item begin transition
     * @param endTransition The removed item end transition
     *
     * @return The transition that was added
     */
    private Transition applyThemeAfterRemove(VideoEditor videoEditor, String themeId,
            int removedItemPosition, Transition beginTransition,
            Transition endTransition) throws IOException {
        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
        final int mediaItemCount = mediaItems.size();
        if (mediaItemCount == 0) {
            return null;
        }

        final Context context = getApplicationContext();
        final MovieTheme theme = MovieTheme.getTheme(context, themeId);

        Transition transition = null;
        if (removedItemPosition == 0) { // First item removed
            if (theme.getBeginTransition() != null && beginTransition != null) {
                transition =
                    theme.getBeginTransition().buildTransition(context, null, mediaItems.get(0));
                videoEditor.addTransition(transition);
            }
        } else if (removedItemPosition == mediaItemCount) { // Last item removed
            if (theme.getEndTransition() != null && endTransition != null) {
                transition = theme.getEndTransition().buildTransition(context,
                            mediaItems.get(mediaItemCount - 1), null);
                videoEditor.addTransition(transition);
            }
        } else { // Mid item removed
            if (theme.getMidTransition() != null && beginTransition != null) {
                transition =
                    theme.getMidTransition().buildTransition(context,
                            mediaItems.get(removedItemPosition - 1),
                            mediaItems.get(removedItemPosition));
                videoEditor.addTransition(transition);
            }
        }

        return transition;
    }

    /**
     * Apply theme transitions after an item was moved
     *
     * @param videoEditor The video editor
     * @param themeId The theme id
     * @param movedMediaItem The moved media item
     * @param originalItemPosition The original media item position
     * @param beginTransition The moved item begin transition
     * @param endTransition The moved item end transition
     */
    private void applyThemeAfterMove(VideoEditor videoEditor, String themeId,
            MediaItem movedMediaItem, int originalItemPosition, Transition beginTransition,
            Transition endTransition) throws IOException {
        final List<MediaItem> mediaItems = videoEditor.getAllMediaItems();
        final int mediaItemCount = mediaItems.size();
        if (mediaItemCount == 0) {
            return;
        }

        final Context context = getApplicationContext();
        final MovieTheme theme = MovieTheme.getTheme(context, themeId);

        if (originalItemPosition == 0) { // First item moved
            if (theme.getBeginTransition() != null && beginTransition != null) {
                final Transition transition =
                    theme.getBeginTransition().buildTransition(context, null, mediaItems.get(0));
                videoEditor.addTransition(transition);
            }
        } else if (originalItemPosition == mediaItemCount - 1) { // Last item moved
            if (theme.getEndTransition() != null && endTransition != null) {
                final Transition transition = theme.getEndTransition().buildTransition(context,
                            mediaItems.get(mediaItemCount - 1), null);
                videoEditor.addTransition(transition);
            }
        } else { // Mid item moved
            final int newPosition = mediaItems.indexOf(movedMediaItem);
            if (newPosition > originalItemPosition) { // Moved forward
                if (endTransition != null && theme.getMidTransition() != null) {
                    final Transition transition = theme.getMidTransition().buildTransition(
                            context, mediaItems.get(originalItemPosition - 1),
                            mediaItems.get(originalItemPosition));
                    videoEditor.addTransition(transition);
                }
            } else { // Moved backward
                if (beginTransition != null && theme.getMidTransition() != null) {
                    final Transition transition = theme.getMidTransition().buildTransition(
                            context, mediaItems.get(originalItemPosition),
                                mediaItems.get(originalItemPosition + 1));
                    videoEditor.addTransition(transition);
                }
            }
        }

        // Apply the theme at the new position
        applyThemeToMediaItem(videoEditor, themeId, movedMediaItem);
    }

    /**
     * Copy the media items
     *
     * @param mediaItems The media items
     *
     * @return The list of media items
     */
    private List<MovieMediaItem> copyMediaItems(List<MediaItem> mediaItems) {
        final List<MovieMediaItem> movieMediaItems
            = new ArrayList<MovieMediaItem>(mediaItems.size());
        MovieMediaItem prevMediaItem = null;
        for (MediaItem mediaItem : mediaItems) {
            final MovieTransition prevTransition;
            if (prevMediaItem != null) {
                prevTransition = prevMediaItem.getEndTransition();
            } else if (mediaItem.getBeginTransition() != null) {
                prevTransition = new MovieTransition(mediaItem.getBeginTransition());
            } else {
                prevTransition = null;
            }

            final MovieMediaItem movieMediaItem = new MovieMediaItem(mediaItem, prevTransition);
            movieMediaItems.add(movieMediaItem);
            prevMediaItem = movieMediaItem;
        }

        return movieMediaItems;
    }

    /**
     * Copy the audio tracks
     *
     * @param audioTracks The audio tracks
     *
     * @return The list of audio tracks
     */
    private List<MovieAudioTrack> copyAudioTracks(List<AudioTrack> audioTracks) {
        final List<MovieAudioTrack> movieAudioTracks
            = new ArrayList<MovieAudioTrack>(audioTracks.size());
        for (AudioTrack audioTrack : audioTracks) {
            movieAudioTracks.add(new MovieAudioTrack(audioTrack));
        }
        return movieAudioTracks;
    }

    private static void logd(String message) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message);
        }
    }

    private static void logv(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, message);
        }
    }

    /**
     * Worker thread that processes intents and maintains its own intent queue.
     */
    private class IntentProcessor extends Thread {
        private final BlockingQueue<Intent> mIntentQueue;

        public IntentProcessor(String threadName) {
            super("IntentProcessor-" + threadName);
            mIntentQueue = new LinkedBlockingQueue<Intent>();
        }

        @Override
        public void run() {
            try {
                while(true) {
                    processIntent(mIntentQueue.take());
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Terminating " + getName());
            }
        }

        /**
         * Submits a new intent for processing.
         *
         * @param intent The intent to be processed
         */
        public void submit(Intent intent) {
            if (isAlive()) {
                mIntentQueue.add(intent);
            } else {
                Log.e(TAG, getName() + " should be started before submitting tasks.");
            }
        }

        /**
         * Removes an intent from the queue.
         *
         * @param intent The intent to be removed
         *
         * @return true if the intent is removed
         */
        public boolean cancel(Intent intent) {
            return mIntentQueue.remove(intent);
        }

        public Iterator<Intent> getIntentQueueIterator() {
            return mIntentQueue.iterator();
        }

        public void quit() {
            // Display an error if the queue is not empty and clear it.
            final int queueSize = mIntentQueue.size();
            if (queueSize > 0) {
                Log.e(TAG, "Thread queue is not empty. Size: " + queueSize);
                mIntentQueue.clear();
            }
            interrupt();
        }
    }
}
