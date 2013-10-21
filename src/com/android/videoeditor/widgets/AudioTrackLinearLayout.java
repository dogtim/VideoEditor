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

import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.videoeditor.AlertDialogs;
import com.android.videoeditor.VideoEditorActivity;
import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieAudioTrack;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.R;

/**
 * The LinearLayout which displays audio tracks
 */
public class AudioTrackLinearLayout extends LinearLayout {
    // Logging
    private static final String TAG = "AudioTrackLinearLayout";

    // Dialog parameter ids
    private static final String PARAM_DIALOG_AUDIO_TRACK_ID = "audio_track_id";

    // Instance variables
    private final ItemSimpleGestureListener mAudioTrackGestureListener;
    private final int mAudioTrackHeight;
    private final int mHalfParentWidth;
    private final View mAddAudioTrackButtonView;
    private final int mAddAudioTrackButtonWidth;
    private AudioTracksLayoutListener mListener;
    private ActionMode mAudioTrackActionMode;
    private VideoEditorProject mProject;
    private boolean mPlaybackInProgress;
    private long mTimelineDurationMs;

    /**
     * Activity listener
     */
    public interface AudioTracksLayoutListener {
        /**
         * Add an audio track
         */
        public void onAddAudioTrack();
    }

    /**
     * The audio track action mode handler
     */
    private class AudioTrackActionModeCallback implements ActionMode.Callback,
            SeekBar.OnSeekBarChangeListener {
        // Instance variables
        private final MovieAudioTrack mAudioTrack;

        /**
         * Constructor
         *
         * @param audioTrack The audio track
         */
        public AudioTrackActionModeCallback(MovieAudioTrack audioTrack) {
            mAudioTrack = audioTrack;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mAudioTrackActionMode = mode;

            mode.getMenuInflater().inflate(R.menu.audio_mode_menu, menu);

            final View titleBarView = inflate(getContext(), R.layout.audio_track_action_bar, null);

            mode.setCustomView(titleBarView);

            final TextView titleView = (TextView)titleBarView.findViewById(R.id.action_bar_title);
            titleView.setText(FileUtils.getSimpleName(mAudioTrack.getFilename()));

            final SeekBar seekBar =
                ((SeekBar)titleBarView.findViewById(R.id.action_volume));
            seekBar.setOnSeekBarChangeListener(this);
            seekBar.setProgress(mAudioTrack.getAppVolume());

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem duckItem = menu.findItem(R.id.action_duck);
            duckItem.setChecked(mAudioTrack.isAppDuckingEnabled());
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_duck: {
                    final boolean duck = !mAudioTrack.isAppDuckingEnabled();
                    mAudioTrack.enableAppDucking(duck);
                    ApiService.setAudioTrackDuck(getContext(), mProject.getPath(),
                            mAudioTrack.getId(), duck);
                    item.setChecked(duck);
                    break;
                }

                case R.id.action_remove_audio_track: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_AUDIO_TRACK_ID, mAudioTrack.getId());
                    ((Activity)getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_AUDIO_TRACK_ID, bundle);
                    break;
                }
            }
            return true;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                mAudioTrack.setAppVolume(progress);
                ApiService.setAudioTrackVolume(getContext(), mProject.getPath(),
                        mAudioTrack.getId(), progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            final View audioTrackView = getAudioTrackView(mAudioTrack.getId());
            if (audioTrackView != null) {
                selectView(audioTrackView, false);
            }

            mAudioTrackActionMode = null;
        }
    }

    public AudioTrackLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mAudioTrackGestureListener = new ItemSimpleGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                return true;
            }

            @Override
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                if (mAudioTrackActionMode == null) {
                    startActionMode(new AudioTrackActionModeCallback(
                            (MovieAudioTrack)view.getTag()));
                }
            }
        };

        // Add the beginning timeline item
        final View beginView = inflate(getContext(), R.layout.empty_timeline_item, null);
        beginView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unselectAllViews();
            }
        });
        addView(beginView);

        // Add the end timeline item
        final View endView = inflate(context, R.layout.empty_timeline_item, null);
        endView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unselectAllViews();
            }
        });
        addView(endView);

        // Add the audio track button
        mAddAudioTrackButtonView = inflate(getContext(), R.layout.add_audio_track_button, null);
        addView(mAddAudioTrackButtonView, 1);
        mAddAudioTrackButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onAddAudioTrack();
                }
            }
        });
        mAddAudioTrackButtonWidth = (int)context.getResources().getDimension(
                R.dimen.add_audio_track_button_width);

        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        // Get the layout height
        mAudioTrackHeight = (int)context.getResources().getDimension(R.dimen.audio_layout_height);

        setMotionEventSplittingEnabled(false);
    }

    public AudioTrackLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioTrackLinearLayout(Context context) {
        this(context, null, 0);
    }

    /**
     * The activity was resumed
     */
    public void onResume() {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null) { // This view represents an audio track
                final AudioTrackView audioTrackView = (AudioTrackView)childView;
                if (audioTrackView.getWaveformData() == null) {
                    final MovieAudioTrack audioTrack = (MovieAudioTrack)tag;
                    if (audioTrack.getWaveformData() != null) {
                        audioTrackView.setWaveformData(audioTrack.getWaveformData());
                        audioTrackView.invalidate();
                    }
                }
            }
        }
    }

    /**
     * @param listener The listener
     */
    public void setListener(AudioTracksLayoutListener listener) {
        mListener = listener;
    }

    /**
     * @param project The project
     */
    public void setProject(VideoEditorProject project) {
        // Close the contextual action bar
        if (mAudioTrackActionMode != null) {
            mAudioTrackActionMode.finish();
            mAudioTrackActionMode = null;
        }

        mProject = project;

        updateAddAudioTrackButton();

        removeAudioTrackViews();
    }

    /**
     * @param inProgress true if playback is in progress
     */
    public void setPlaybackInProgress(boolean inProgress) {
        mPlaybackInProgress = inProgress;

        // Don't allow the user to interact with the audio tracks while playback
        // is in progress
        if (inProgress && mAudioTrackActionMode != null) {
            mAudioTrackActionMode.finish();
            mAudioTrackActionMode = null;
        }
    }

    /**
     * Add the audio tracks
     *
     * @param audioTracks The audio tracks
     */
    public void addAudioTracks(List<MovieAudioTrack> audioTracks) {
        if (mAudioTrackActionMode != null) {
            mAudioTrackActionMode.finish();
            mAudioTrackActionMode = null;
        }

        updateAddAudioTrackButton();

        removeAudioTrackViews();

        mTimelineDurationMs = mProject.computeDuration();

        for (MovieAudioTrack audioTrack : audioTracks) {
            addAudioTrack(audioTrack);
        }
    }

    /**
     * Add a new audio track
     *
     * @param audioTrack The audio track
     *
     * @return The view that was added
     */
    public View addAudioTrack(MovieAudioTrack audioTrack) {
        updateAddAudioTrackButton();

        final AudioTrackView audioTrackView = (AudioTrackView)inflate(getContext(),
                R.layout.audio_track_item, null);

        audioTrackView.setTag(audioTrack);

        audioTrackView.setGestureListener(mAudioTrackGestureListener);

        audioTrackView.updateTimelineDuration(mTimelineDurationMs);

        if (audioTrack.getWaveformData() != null) {
            audioTrackView.setWaveformData(audioTrack.getWaveformData());
        } else {
            ApiService.extractAudioTrackAudioWaveform(getContext(), mProject.getPath(),
                    audioTrack.getId());
        }

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(audioTrackView, getChildCount() - 1, lp);

        if (mAudioTrackActionMode != null) {
            mAudioTrackActionMode.invalidate();
        }

        requestLayout();
        return audioTrackView;
    }

    /**
     * Remove an audio track
     *
     * @param audioTrackId The audio track id
     * @return The view which was removed
     */
    public View removeAudioTrack(String audioTrackId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieAudioTrack audioTrack = (MovieAudioTrack)childView.getTag();
            if (audioTrack != null && audioTrack.getId().equals(audioTrackId)) {
                removeViewAt(i);

                updateAddAudioTrackButton();

                requestLayout();
                return childView;
            }
        }

        return null;
    }

    /**
     * Update the audio track item
     *
     * @param audioTrackId The audio track id
     */
    public void updateAudioTrack(String audioTrackId) {
        final AudioTrackView audioTrackView = (AudioTrackView)getAudioTrackView(audioTrackId);
        if (audioTrackView == null) {
            Log.e(TAG, "updateAudioTrack: audio track view not found: " + audioTrackId);
            return;
        }

        if (mAudioTrackActionMode != null) {
            mAudioTrackActionMode.invalidate();
        }

        requestLayout();
        invalidate();
    }

    /**
     * An audio track is being decoded
     *
     * @param audioTrackId The audio track id
     * @param action The action
     * @param progress The progress
     */
    public void onGeneratePreviewProgress(String audioTrackId, int action, int progress) {
        final AudioTrackView audioTrackView = (AudioTrackView)getAudioTrackView(audioTrackId);
        if (audioTrackView == null) {
            Log.e(TAG, "onGeneratePreviewProgress: audio track view not found: " + audioTrackId);
            return;
        }

        audioTrackView.setProgress(progress);
    }

    /**
     * Set the waveform progress
     *
     * @param audioTrackId The audio track id
     * @param progress The progress
     */
    public void setWaveformExtractionProgress(String audioTrackId, int progress) {
        final AudioTrackView audioTrackView = (AudioTrackView)getAudioTrackView(audioTrackId);
        if (audioTrackView == null) {
            Log.e(TAG, "setWaveformExtractionProgress: audio track view not found: "
                    + audioTrackId);
            return;
        }

        audioTrackView.setProgress(progress);
    }

    /**
     * The waveform extraction is complete
     *
     * @param audioTrackId The audio track id
     */
    public void setWaveformExtractionComplete(String audioTrackId) {
        final AudioTrackView audioTrackView = (AudioTrackView)getAudioTrackView(audioTrackId);
        if (audioTrackView == null) {
            Log.e(TAG, "setWaveformExtractionComplete: audio track view not found: "
                    + audioTrackId);
            return;
        }

        audioTrackView.setProgress(-1);

        final MovieAudioTrack audioTrack = (MovieAudioTrack)audioTrackView.getTag();
        if (audioTrack.getWaveformData() != null) {
            audioTrackView.setWaveformData(audioTrack.getWaveformData());
        }

        requestLayout();
        invalidate();
    }

    /**
     * The timeline duration has changed. Refresh the view.
     */
    public void updateTimelineDuration() {
        mTimelineDurationMs = mProject.computeDuration();

        // Media items may had been added or removed
        updateAddAudioTrackButton();

        // Update the project duration for all views
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieAudioTrack audioTrack = (MovieAudioTrack)childView.getTag();
            if (audioTrack != null) {
                ((AudioTrackView)childView).updateTimelineDuration(mTimelineDurationMs);
            }
        }

        requestLayout();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int childrenCount = getChildCount();
        if (mTimelineDurationMs == 0) {
            int left = 0;
            for (int i = 0; i < childrenCount; i++) {
                final View childView = getChildAt(i);
                final MovieAudioTrack audioTrack = (MovieAudioTrack)childView.getTag();
                if (audioTrack != null) {
                    // Audio tracks are not visible
                    childView.layout(left, 0, left, mAudioTrackHeight);
                } else { // Beginning and end views
                    childView.layout(left, 0, left + mHalfParentWidth, mAudioTrackHeight);
                    left += mHalfParentWidth;
                }
            }
        } else {
            final int viewWidth = getWidth() - (2 * mHalfParentWidth);
            int left = 0;

            final int leftViewWidth = (Integer)((View)getParent().getParent()).getTag(
                    R.id.left_view_width);

            for (int i = 0; i < childrenCount; i++) {
                final View childView = getChildAt(i);
                final int id = childView.getId();
                final MovieAudioTrack audioTrack = (MovieAudioTrack)childView.getTag();
                if (audioTrack != null) { // Audio track views
                    final int width;
                    if (audioTrack.isAppLooping()) {
                        width = (int)((mTimelineDurationMs -
                                audioTrack.getAppStartTime()) * viewWidth / mTimelineDurationMs);
                    } else {
                        if (audioTrack.getAppStartTime() + audioTrack.getTimelineDuration() >
                                mTimelineDurationMs) {
                            width = (int)((mTimelineDurationMs -
                                audioTrack.getAppStartTime()) * viewWidth / mTimelineDurationMs);
                        } else {
                            width = (int)(audioTrack.getTimelineDuration() * viewWidth /
                                    mTimelineDurationMs);
                        }
                    }

                    final int trackLeft =
                        (int)((audioTrack.getAppStartTime() * viewWidth) / mTimelineDurationMs) +
                            leftViewWidth;
                    childView.layout(trackLeft, 0, trackLeft + width, mAudioTrackHeight);
                    left = trackLeft + width;
                } else if (id == R.id.add_audio_track_button) {
                    if (childView.getVisibility() == View.VISIBLE) {
                        childView.layout(left, 0, left + mAddAudioTrackButtonWidth,
                                mAudioTrackHeight);
                        left += mAddAudioTrackButtonWidth;
                    }
                } else if (i == 0) { // Begin view
                    childView.layout(left, 0, left + leftViewWidth, mAudioTrackHeight);
                    left += leftViewWidth;
                } else { // End view
                    childView.layout(left, 0, getWidth(), mAudioTrackHeight);
                }
            }
        }
    }

    /**
     * Create a new dialog
     *
     * @param id The dialog id
     * @param bundle The dialog bundle
     *
     * @return The dialog
     */
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        // If the project is not yet loaded do nothing.
        if (mProject == null) {
            return null;
        }

        switch (id) {
            case VideoEditorActivity.DIALOG_REMOVE_AUDIO_TRACK_ID: {
                final MovieAudioTrack audioTrack = mProject.getAudioTrack(
                        bundle.getString(PARAM_DIALOG_AUDIO_TRACK_ID));
                if (audioTrack == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                return AlertDialogs.createAlert(activity,
                        FileUtils.getSimpleName(audioTrack.getFilename()), 0,
                        activity.getString(R.string.editor_remove_audio_track_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mAudioTrackActionMode != null) {
                            mAudioTrackActionMode.finish();
                            mAudioTrackActionMode = null;
                        }
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_AUDIO_TRACK_ID);

                        ApiService.removeAudioTrack(activity, mProject.getPath(),
                                audioTrack.getId());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_AUDIO_TRACK_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_AUDIO_TRACK_ID);
                    }
                }, true);
            }

            default: {
                return null;
            }
        }
    }

    /**
     * Find the audio track view with the specified id
     *
     * @param audioTrackId The audio track id
     * @return The audio track view
     */
    private View getAudioTrackView(String audioTrackId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieAudioTrack audioTrack = (MovieAudioTrack)childView.getTag();
            if (audioTrack != null && audioTrackId.equals(audioTrack.getId())) {
                return childView;
            }
        }

        return null;
    }

    /**
     * Remove all audio track views (leave the beginning and end views)
     */
    private void removeAudioTrackViews() {
        int index = 0;
        while (index < getChildCount()) {
            final Object tag = getChildAt(index).getTag();
            if (tag != null) {
                removeViewAt(index);
            } else {
                index++;
            }
        }

        requestLayout();
    }

    /**
     * Set the background of the begin view
     */
    private void updateAddAudioTrackButton() {
        if (mProject == null) { // No project
            mAddAudioTrackButtonView.setVisibility(View.GONE);
        } else if (mProject.getMediaItemCount() > 0) {
            if (mProject.getAudioTracks().size() > 0) {
                mAddAudioTrackButtonView.setVisibility(View.GONE);
            } else {
                mAddAudioTrackButtonView.setVisibility(View.VISIBLE);
            }
        } else { // No media items
            mAddAudioTrackButtonView.setVisibility(View.GONE);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected == false) {
            // Close the contextual action bar
            if (mAudioTrackActionMode != null) {
                mAudioTrackActionMode.finish();
                mAudioTrackActionMode = null;
            }
        }

        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            childView.setSelected(false);
        }
    }

    /**
     * Select a view and unselect any view that is selected.
     *
     * @param selectedView The view to select
     * @param selected true if selected
     */
    private void selectView(View selectedView, boolean selected) {
        // Check if the selection has changed
        if (selectedView.isSelected() == selected) {
            return;
        }

        if (selected) {
            unselectAllViews();
        }

        if (selected && mAudioTrackActionMode == null) {
            startActionMode(new AudioTrackActionModeCallback(
                    (MovieAudioTrack)selectedView.getTag()));
        }

        // Select the new view
        selectedView.setSelected(selected);
    }

    /**
     * Unselect all views
     */
    private void unselectAllViews() {
        ((RelativeLayout)getParent()).setSelected(false);
    }
}
