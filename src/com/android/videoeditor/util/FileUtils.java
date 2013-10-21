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
import java.io.InputStream;

import com.android.videoeditor.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.media.videoeditor.MediaProperties;
import android.os.Environment;
import android.util.Log;

/**
 * File utilities
 */
public class FileUtils {
    // Logging
    private static final String TAG = "FileUtils";

    /**
     * It is not possible to instantiate this class
     */
    private FileUtils() {
    }

    /**
     * Gets the root path for all projects
     *
     * @param context The context
     *
     * @return The file representing the projects root directory, {@code null} if the external
     * storage is not currnetly mounted
     */
    public static File getProjectsRootDir(Context context)
            throws FileNotFoundException, IOException {
        final File dir = context.getExternalFilesDir(null);
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs()) {
                throw new FileNotFoundException("Cannot create folder: " + dir.getAbsolutePath());
            } else {
                // Create the file which hides the media files
                if (!new File(dir, ".nomedia").createNewFile()) {
                    throw new FileNotFoundException("Cannot create file .nomedia");
                }
            }
        }

        return dir;
    }

    /**
     * Get the filename for the specified raw resource id. Create the file if
     * the file does not exist.
     *
     * @param context The context
     * @param maskRawResourceId The mask raw resource id
     *
     * @return The mask filename
     */
    public static String getMaskFilename(Context context, int maskRawResourceId)
            throws FileNotFoundException, IOException {
        final String filename;
        switch (maskRawResourceId) {
            case R.raw.mask_contour: {
                filename = "mask_countour.jpg";
                break;
            }

            case R.raw.mask_diagonal: {
                filename = "mask_diagonal.jpg";
                break;
            }

            default: {
                throw new IllegalArgumentException("Invalid mask raw resource id");
            }
        }

        final File mf = new File(context.getFilesDir(), filename);
        if (!mf.exists()) {
            Bitmap bitmap = null;
            FileOutputStream fos = null;
            InputStream is = null;
            try {
                is = context.getResources().openRawResource(maskRawResourceId);
                bitmap = BitmapFactory.decodeStream(is);
                if (bitmap == null) {
                    throw new IllegalStateException("Cannot decode raw resource mask");
                }

                fos = context.openFileOutput(filename, Context.MODE_WORLD_READABLE);
                if (!bitmap.compress(CompressFormat.JPEG, 100, fos)) {
                    throw new IllegalStateException("Cannot compress bitmap");
                }
            } finally {
                if (is != null) {
                    is.close();
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            }
        }

        return mf.getAbsolutePath();
    }

    /**
     * Get the raw id for the mask file
     *
     * @param path The full file name
     *
     * @return The raw id
     */
    public static int getMaskRawId(String path) {
        final String filename = new File(path).getName();

        if (filename.equals("mask_countour.jpg")) {
            return R.raw.mask_contour;
        } else if (filename.equals("mask_diagonal.jpg")) {
            return R.raw.mask_diagonal;
        } else {
            throw new IllegalArgumentException("Unknown file: " + path);
        }
    }

    /**
     * Get the filename for the specified raw resource id. Create the file if
     * the file does not exist
     *
     * @param context The context
     * @param rawResourceId The raw resource id
     *
     * @return The audio track filename
     */
    public static String getAudioTrackFilename(Context context, int rawResourceId)
            throws FileNotFoundException, IOException {
        final String filename;
        switch (rawResourceId) {
            case R.raw.theme_travel_audio_track: {
                filename = "theme_travel.m4a";
                break;
            }

            case R.raw.theme_surfing_audio_track: {
                filename = "theme_surfing.m4a";
                break;
            }

            case R.raw.theme_film_audio_track: {
                filename = "theme_film.m4a";
                break;
            }

            case R.raw.theme_rockandroll_audio_track: {
                filename = "theme_rockandroll.m4a";
                break;
            }

            default: {
                throw new IllegalArgumentException("Invalid audio track raw resource id");
            }
        }

        final File mf = new File(context.getFilesDir(), filename);
        if (!mf.exists()) {
            FileOutputStream fos = null;
            InputStream is = null;
            try {
                is = context.getResources().openRawResource(rawResourceId);
                fos = context.openFileOutput(filename, Context.MODE_WORLD_READABLE);
                final byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, bytesRead);
                }
            } finally {
                if (is != null) {
                    is.close();
                }

                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            }
        }

        return mf.getAbsolutePath();
    }

    /**
     * Create a new project directory
     *
     * @return The absolute path to the project
     */
    public static String createNewProjectPath(Context context)
            throws FileNotFoundException, IOException {
        final File file = new File(getProjectsRootDir(context), StringUtils.randomString(10));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "New project: " + file.getAbsolutePath());
        }

        return file.getAbsolutePath();
    }

    /**
     * Get a unique video filename.
     *
     * @param fileType The file type
     *
     * @return The filename
     */
    public static String createMovieName(int fileType) {
        final String filename;
        switch (fileType) {
            case MediaProperties.FILE_MP4: {
                filename = "movie_" + StringUtils.randomStringOfNumbers(6) + ".mp4";
                break;
            }

            case MediaProperties.FILE_3GP: {
                filename = "movie_" + StringUtils.randomStringOfNumbers(6) + ".3gp";
                break;
            }

            default: {
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
            }
        }

        final File moviesDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES);
        // Make this directory if it does not exist
        if (!moviesDirectory.exists()) {
            moviesDirectory.mkdirs();
        }

        final File f = new File(moviesDirectory, filename);
        return f.getAbsolutePath();
    }

    /**
     * Delete all the files in the specified folder and the folder itself.
     *
     * @param dir The project path
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            final String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                final File f = new File(dir, children[i]);
                if (!deleteDir(f)) {
                    Log.e(TAG, "File cannot be deleted: " + f.getAbsolutePath());
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    /**
     * Get the name of the file
     *
     * @param filename The full path filename
     * @return The name of the file
     */
    public static String getSimpleName(String filename) {
        final int index = filename.lastIndexOf('/');
        if (index == -1) {
            return filename;
        } else {
            return filename.substring(index + 1);
        }
    }
}
