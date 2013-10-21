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

package com.android.videoeditor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.videoeditor.MediaProperties;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * The export options dialog
 */
public class ExportOptionsDialog {
    // Listener
    public interface ExportOptionsListener {
        /**
         * User initiated the export operation
         *
         * @param movieHeight The movie height (from MediaProperties)
         * @param movieBitrate The movie bitrate (from MediaProperties)
         */
        public void onExportOptions(int movieHeight, int movieBitrate);
    }

    /**
     * Create the export options dialog
     *
     * @param context The context
     * @param positiveListener The positive listener
     * @param negativeListener The negative listener
     * @param cancelListener The cancel listener
     * @param aspectRatio The aspect ratio
     *
     * @return The dialog
     */
    public static Dialog create(Context context, final ExportOptionsListener positiveListener,
            DialogInterface.OnClickListener negativeListener,
            DialogInterface.OnCancelListener cancelListener, final int aspectRatio) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Set the title
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(context.getString(R.string.editor_export_movie));

        // Set the layout
        final LayoutInflater vi = (LayoutInflater)context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View myView = vi.inflate(R.layout.export_options_dialog_view, null);
        builder.setView(myView);

        // Prepare the dialog content
        prepareContent(myView, aspectRatio);

        // Setup the positive listener
        builder.setPositiveButton(context.getString(R.string.export_dialog_export),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Spinner sizeSpinner = (Spinner)myView.findViewById(
                                R.id.export_option_size);
                        final int movieHeight = indexToMovieHeight(
                                sizeSpinner.getSelectedItemPosition(), aspectRatio);
                        final Spinner qualitySpinner = (Spinner)myView.findViewById(
                                R.id.export_option_quality);
                        final int movieBitrate = indexToMovieBitrate(
                                qualitySpinner.getSelectedItemPosition());
                        positiveListener.onExportOptions(movieHeight, movieBitrate);
                    }
                });

        // Setup the negative listener
        builder.setNegativeButton(context.getString(android.R.string.cancel), negativeListener);

        builder.setCancelable(true);
        builder.setOnCancelListener(cancelListener);

        final AlertDialog dialog = builder.create();

        return dialog;
    }

    /**
     * Prepare the dialog content
     *
     * @param view The dialog content view
     * @param aspectRatio The project aspect ratio
     */
    private static void prepareContent(View view, int aspectRatio) {
        final Context context = view.getContext();
        // Setup the movie size spinner
        final ArrayAdapter<CharSequence> sizeAdapter = new ArrayAdapter<CharSequence>(
                context, android.R.layout.simple_spinner_item);
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Pair<Integer, Integer>[] supportedSizes =
            MediaProperties.getSupportedResolutions(aspectRatio);
        for (int i = 0; i < supportedSizes.length; i++) {
            sizeAdapter.add(supportedSizes[i].first + "x" + supportedSizes[i].second);
        }
        final Spinner sizeSpinner = (Spinner)view.findViewById(R.id.export_option_size);
        sizeSpinner.setAdapter(sizeAdapter);
        sizeSpinner.setPromptId(R.string.export_dialog_movie_size);

        // Setup the movie quality spinner
        final ArrayAdapter<CharSequence> qualityAdapter = new ArrayAdapter<CharSequence>(context,
                android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualityAdapter.add(context.getString(R.string.export_dialog_movie_quality_low));
        qualityAdapter.add(context.getString(R.string.export_dialog_movie_quality_medium));
        qualityAdapter.add(context.getString(R.string.export_dialog_movie_quality_high));
        final Spinner qualitySpinner = (Spinner)view.findViewById(R.id.export_option_quality);
        qualitySpinner.setAdapter(qualityAdapter);
        // Set the default quality to "Medium"
        qualitySpinner.setSelection(1);
        qualitySpinner.setPromptId(R.string.export_dialog_movie_quality);
    }

    /**
     * Convert the spinner selection to a movie height
     *
     * @param sizeIndex The index of the selected spinner item
     * @param aspectRatio The aspect ratio
     *
     * @return The movie height
     */
    private static int indexToMovieHeight(int sizeIndex, int aspectRatio) {
        final Pair<Integer, Integer>[] supportedSizes =
            MediaProperties.getSupportedResolutions(aspectRatio);
        return supportedSizes[sizeIndex].second;
    }

    /**
     * Convert the spinner selection to a movie quality
     *
     * @param qualityIndex The index of the selected spinner item
     *
     * @return The movie bitrate
     */
    private static int indexToMovieBitrate(int qualityIndex) {
        switch (qualityIndex) {
            case 0: { // Low
                return MediaProperties.BITRATE_512K;
            }

            case 1: { // Medium
                return MediaProperties.BITRATE_2M;
            }

            case 2: { // High
                return MediaProperties.BITRATE_8M;
            }

            default: {
                return MediaProperties.BITRATE_2M;
            }
        }
    }
}
