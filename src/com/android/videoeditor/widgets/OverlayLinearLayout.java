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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import com.android.videoeditor.AlertDialogs;
import com.android.videoeditor.OverlayTitleEditor;
import com.android.videoeditor.VideoEditorActivity;
import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieMediaItem;
import com.android.videoeditor.service.MovieOverlay;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.util.MediaItemUtils;
import com.android.videoeditor.R;

/**
 * LinearLayout which displays overlays.
 */
public class OverlayLinearLayout extends LinearLayout {
    // Logging
    private static final String TAG = "OverlayLinearLayout";

    // Dialog parameter ids
    private static final String PARAM_DIALOG_MEDIA_ITEM_ID = "media_item_id";

    // Default overlay duration
    public static final long DEFAULT_TITLE_DURATION = 3000;

    // Instance variables
    private final ItemMoveGestureListener mOverlayGestureListener;
    private final int mHalfParentWidth;
    private final Handler mHandler;
    private final int mHandleWidth;
    private ActionMode mOverlayActionMode;
    private boolean mPlaybackInProgress;
    private VideoEditorProject mProject;
    private HandleView mLeftHandle, mRightHandle;
    private boolean mMoveLayoutPending;
    private View mResizingView;

    /**
     * The overlay listener
     */
    public interface OverlayLayoutListener {
        /**
         * Add a new overlay
         */
        public void onAddOverlay();
    }

    /**
     * The overlay action mode handler.
     */
    private class OverlayActionModeCallback implements ActionMode.Callback {
        // Instance variables
        private final MovieMediaItem mMediaItem;

        public OverlayActionModeCallback(MovieMediaItem mediaItem) {
            mMediaItem = mediaItem;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mOverlayActionMode = mode;

            final Activity activity = (Activity) getContext();
            activity.getMenuInflater().inflate(R.menu.overlay_mode_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final boolean enable = !ApiService.isProjectBeingEdited(mProject.getPath()) &&
                !mPlaybackInProgress;

            final MenuItem eomi = menu.findItem(R.id.action_edit_overlay);
            eomi.setEnabled(enable);

            final MenuItem romi = menu.findItem(R.id.action_remove_overlay);
            romi.setEnabled(enable);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_edit_overlay: {
                    final Activity activity = (Activity) getContext();
                    final Intent intent = new Intent(activity, OverlayTitleEditor.class);
                    intent.putExtra(OverlayTitleEditor.PARAM_MEDIA_ITEM_ID, mMediaItem.getId());

                    final MovieOverlay overlay = mMediaItem.getOverlay();
                    intent.putExtra(OverlayTitleEditor.PARAM_OVERLAY_ID, overlay.getId());
                    intent.putExtra(OverlayTitleEditor.PARAM_OVERLAY_ATTRIBUTES,
                            overlay.buildUserAttributes());
                    activity.startActivityForResult(intent,
                            VideoEditorActivity.REQUEST_CODE_PICK_OVERLAY);
                    break;
                }

                case R.id.action_remove_overlay: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    ((Activity) getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID, bundle);
                    break;
                }

                default: {
                    break;
                }
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            final View overlayView = getOverlayView(mMediaItem.getId());
            if (overlayView != null) {
                selectView(overlayView, false);
            }

            mOverlayActionMode = null;
        }
    }

    public OverlayLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mOverlayGestureListener = new ItemMoveGestureListener() {
            private MovieMediaItem mScrollMediaItem;
            private MovieOverlay mScrollOverlay;
            private long mScrollTotalDurationMs, mScrollMediaItemStartTime;
            private boolean mScrolled;

            @Override
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                switch (((OverlayView)view).getState()) {
                    case OverlayView.STATE_STUB: {
                        unselectAllViews();
                        ((OverlayView)view).setState(OverlayView.STATE_ADD_BUTTON);
                        break;
                    }

                    case OverlayView.STATE_ADD_BUTTON: {
                        final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
                        final Intent intent = new Intent(getContext(), OverlayTitleEditor.class);
                        intent.putExtra(OverlayTitleEditor.PARAM_MEDIA_ITEM_ID, mediaItem.getId());
                        ((Activity) getContext()).startActivityForResult(intent,
                                VideoEditorActivity.REQUEST_CODE_PICK_OVERLAY);
                        break;
                    }

                    case OverlayView.STATE_OVERLAY: {
                        if (!view.isSelected()) {
                            selectView(view, true);
                        }
                        break;
                    }

                    default: {
                        break;
                    }
                }

                return true;
            }

            @Override
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                switch (((OverlayView)view).getState()) {
                    case OverlayView.STATE_STUB: {
                        break;
                    }

                    case OverlayView.STATE_ADD_BUTTON: {
                        break;
                    }

                    case OverlayView.STATE_OVERLAY: {
                        if (!view.isSelected()) {
                            selectView(view, true);
                        }

                        if (mOverlayActionMode == null) {
                            startActionMode(new OverlayActionModeCallback(
                                    (MovieMediaItem)view.getTag()));
                        }
                        break;
                    }

                    default: {
                        break;
                    }
                }
            }

            @Override
            public boolean onMoveBegin(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                mScrollMediaItem = (MovieMediaItem)view.getTag();
                mScrollMediaItemStartTime = mProject.getMediaItemBeginTime(mScrollMediaItem.getId());
                mScrollOverlay = mScrollMediaItem.getOverlay();
                // The duration of the timeline does not change while moving the Overlay
                mScrollTotalDurationMs = mProject.computeDuration();
                mRightHandle.setVisibility(View.GONE);
                mScrolled = false;
                return true;
            }

            @Override
            public boolean onMove(View view, MotionEvent e1, MotionEvent e2) {
                final int beginPos = (int)(view.getLeft() - mHalfParentWidth - e1.getX() +
                        e2.getX());
                long startTimeMs = ((beginPos * mScrollTotalDurationMs) /
                        (getWidth() - (2 * mHalfParentWidth)));
                if (startTimeMs <= mScrollMediaItemStartTime) {
                    startTimeMs = mScrollMediaItemStartTime;
                } else if (startTimeMs + mScrollOverlay.getAppDuration() >
                    mScrollMediaItemStartTime + mScrollMediaItem.getAppTimelineDuration()) {
                    startTimeMs = mScrollMediaItemStartTime +
                        mScrollMediaItem.getAppTimelineDuration() - mScrollOverlay.getAppDuration();
                }

                mScrolled = true;
                mScrollOverlay.setAppStartTime(startTimeMs - mScrollMediaItemStartTime +
                        mScrollMediaItem.getAppBoundaryBeginTime());
                requestLayout();
                return true;
            }

            @Override
            public void onMoveEnd(View view) {
                mRightHandle.setVisibility(View.VISIBLE);
                if (mScrolled) {
                    mScrolled = false;
                    // Update the limits of the right handle
                    mRightHandle.setLimitReached(mScrollOverlay.getAppDuration() <=
                        MediaItemUtils.getMinimumMediaItemDuration(mScrollMediaItem),
                        mScrollOverlay.getAppStartTime() + mScrollOverlay.getAppDuration() >=
                            mScrollMediaItem.getAppBoundaryEndTime());

                    ApiService.setOverlayStartTime(getContext(), mProject.getPath(),
                            mScrollMediaItem.getId(), mScrollOverlay.getId(),
                            mScrollOverlay.getAppStartTime());
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
        final View endView = inflate(getContext(), R.layout.empty_timeline_item, null);
        endView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unselectAllViews();
            }
        });
        addView(endView);

        mLeftHandle = (HandleView)inflate(getContext(), R.layout.left_handle_view, null);
        addView(mLeftHandle);

        mRightHandle = (HandleView)inflate(getContext(), R.layout.right_handle_view, null);
        addView(mRightHandle);

        mHandleWidth = (int)context.getResources().getDimension(R.dimen.handle_width);

        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        mHandler = new Handler();

        setMotionEventSplittingEnabled(false);
   }

    public OverlayLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverlayLinearLayout(Context context) {
        this(context, null, 0);
    }

    /**
     * @param project The project
     */
    public void setProject(VideoEditorProject project) {
        // Close the contextual action bar
        if (mOverlayActionMode != null) {
            mOverlayActionMode.finish();
            mOverlayActionMode = null;
        }

        mLeftHandle.setVisibility(View.GONE);
        mLeftHandle.setListener(null);
        mRightHandle.setVisibility(View.GONE);
        mRightHandle.setListener(null);

        removeViews();

        mProject = project;
    }

    /**
     * @param inProgress true if playback is in progress
     */
    public void setPlaybackInProgress(boolean inProgress) {
        mPlaybackInProgress = inProgress;

        // Don't allow the user to interact with the overlays while playback
        // is in progress
        if (inProgress && mOverlayActionMode != null) {
            mOverlayActionMode.finish();
            mOverlayActionMode = null;
        }
    }

    /**
     * Add all the media items
     *
     * @param mediaItems The list of media items
     */
    public void addMediaItems(List<MovieMediaItem> mediaItems) {
        if (mOverlayActionMode != null) {
            mOverlayActionMode.finish();
            mOverlayActionMode = null;
        }

        removeViews();

        for (MovieMediaItem mediaItem : mediaItems) {
            addMediaItem(mediaItem);
        }
    }

    /**
     * Add a new media item at the end of the timeline
     *
     * @param mediaItem The media item
     * @return The view that was added
     */
    private View addMediaItem(MovieMediaItem mediaItem) {
        final OverlayView overlayView = (OverlayView)inflate(getContext(),
                R.layout.overlay_item, null);
        if (mediaItem.getOverlay() != null) {
            overlayView.setState(OverlayView.STATE_OVERLAY);
        } else {
            overlayView.setState(OverlayView.STATE_STUB);
        }

        overlayView.setTag(mediaItem);

        overlayView.setGestureListener(mOverlayGestureListener);

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(overlayView, getChildCount() - 1, lp);

        if (mOverlayActionMode != null) {
            mOverlayActionMode.invalidate();
        }

        requestLayout();
        return overlayView;
    }

    /**
     * Insert a new media item after the specified media item id
     *
     * @param mediaItem The media item
     * @param afterMediaItemId The id of the media item preceding the media item
     */
    public void insertMediaItem(MovieMediaItem mediaItem, String afterMediaItemId) {
        final OverlayView overlayView = (OverlayView)inflate(getContext(),
                R.layout.overlay_item, null);
        if (mediaItem.getOverlay() != null) {
            overlayView.setState(OverlayView.STATE_OVERLAY);
        } else {
            overlayView.setState(OverlayView.STATE_STUB);
        }

        overlayView.setTag(mediaItem);

        overlayView.setGestureListener(mOverlayGestureListener);

        int insertViewIndex;
        if (afterMediaItemId != null) {
            if ((insertViewIndex = getMediaItemViewIndex(afterMediaItemId)) == -1) {
                Log.e(TAG, "Media item not found: " + afterMediaItemId);
                return;
            }

            insertViewIndex++;
        } else { // Insert at the beginning
            insertViewIndex = 1;
        }

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(overlayView, insertViewIndex, lp);

        if (mOverlayActionMode != null) {
            mOverlayActionMode.invalidate();
        }

        requestLayout();
    }

    /**
     * Update media item
     *
     * @param mediaItem The media item
     */
    public void updateMediaItem(MovieMediaItem mediaItem) {
        final String mediaItemId = mediaItem.getId();
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieMediaItem mi = (MovieMediaItem)childView.getTag();
            if (mi != null && mediaItemId.equals(mi.getId())) {
                if (mediaItem != mi) {
                    // The media item is a new instance
                    childView.setTag(mediaItem);
                }
                break;
            }
        }

        requestLayout();
        invalidate();
    }

    /**
     * Remove a media item
     *
     * @param mediaItemId The media item id
     * @return The view which was removed
     */
    public View removeMediaItem(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieMediaItem mediaItem = (MovieMediaItem)childView.getTag();
            if (mediaItem != null && mediaItem.getId().equals(mediaItemId)) {
                removeViewAt(i);
                requestLayout();
                return childView;
            }
        }

        return null;
    }

    /**
     * A new overlay was added
     *
     * @param mediaItemId The media item which owns the overlay
     * @param overlay The overlay which was added
     */
    public void addOverlay(String mediaItemId, MovieOverlay overlay) {
        final OverlayView view = (OverlayView)getOverlayView(mediaItemId);
        if (view == null) {
            Log.e(TAG, "addOverlay: Media item not found: " + mediaItemId);
            return;
        }

        view.setState(OverlayView.STATE_OVERLAY);

        requestLayout();
        invalidate();
    }

    /**
     * An overlay was removed
     *
     * @param mediaItemId The media item which owns the overlay
     * @param overlayId The overlay id
     */
    public void removeOverlay(String mediaItemId, String overlayId) {
        final OverlayView view = (OverlayView)getOverlayView(mediaItemId);
        if (view == null) {
            Log.e(TAG, "removeOverlay: Media item not found: " + mediaItemId);
            return;
        }

        view.setState(OverlayView.STATE_STUB);

        requestLayout();
        invalidate();

        if (mOverlayActionMode != null) {
            mOverlayActionMode.finish();
            mOverlayActionMode = null;
        }
    }

    /**
     * Update the overlay attributes
     *
     * @param mediaItemId The media item id
     * @param overlayId The overlay id
     * @param userAttributes The overlay attributes
     */
    public void updateOverlayAttributes(String mediaItemId, String overlayId,
            Bundle userAttributes) {
        final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
        if (mediaItem == null) {
            Log.e(TAG, "updateOverlayAttributes: Media item not found: " + mediaItemId);
            return;
        }

        final View overlayView = getOverlayView(mediaItemId);
        if (overlayView == null) {
            Log.e(TAG, "updateOverlayAttributes: Overlay not found: " + overlayId);
            return;
        }

        overlayView.invalidate();
    }

    /**
     * Refresh the view
     */
    public void refresh() {
        requestLayout();
        invalidate();
    }

    /**
     * Invalidate the CAB
     */
    public void invalidateCAB() {
        if (mOverlayActionMode != null) {
            mOverlayActionMode.invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final long totalDurationMs = mProject.computeDuration();
        final int viewWidth = getWidth() - (2 * mHalfParentWidth);

        final int leftViewWidth = (Integer)((View)getParent().getParent()).getTag(
                R.id.left_view_width);
        long mediaItemStartTimeMs = 0;
        int left = 0;
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View view = getChildAt(i);
            final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
            if (mediaItem != null) { // Media item
                final MovieOverlay overlay = mediaItem.getOverlay();

                final int right;
                if (overlay != null) {
                    // Note that this logic matches the one used in ApiService
                    // when handling the OP_MEDIA_ITEM_SET_BOUNDARIES command
                    if (overlay.getAppStartTime() <= mediaItem.getAppBoundaryBeginTime()) {
                        left = leftViewWidth + (int)((mediaItemStartTimeMs * viewWidth) /
                                totalDurationMs);
                        final long durationMs = Math.min(overlay.getAppDuration(),
                                mediaItem.getAppTimelineDuration());
                        right = leftViewWidth + (int)(((mediaItemStartTimeMs + durationMs) *
                                viewWidth) / totalDurationMs);
                    } else if (overlay.getAppStartTime() + overlay.getAppDuration() >
                                mediaItem.getAppBoundaryEndTime()) {
                        final long startTimeMs = Math.max(mediaItem.getAppBoundaryBeginTime(),
                                mediaItem.getAppBoundaryEndTime() - overlay.getAppDuration());
                        left = leftViewWidth + (int)(((mediaItemStartTimeMs + startTimeMs -
                                mediaItem.getAppBoundaryBeginTime()) * viewWidth) /
                                totalDurationMs);
                        final long durationMs = mediaItem.getAppBoundaryEndTime() - startTimeMs;
                        right = leftViewWidth + (int)(((mediaItemStartTimeMs + startTimeMs -
                                mediaItem.getAppBoundaryBeginTime() +
                                durationMs) * viewWidth) / totalDurationMs);
                    } else {
                        left = leftViewWidth + (int)(((mediaItemStartTimeMs +
                                overlay.getAppStartTime() - mediaItem.getAppBoundaryBeginTime()) *
                                viewWidth) / totalDurationMs);
                        right = leftViewWidth + (int)(((mediaItemStartTimeMs
                                + overlay.getAppStartTime() - mediaItem.getAppBoundaryBeginTime() +
                                overlay.getAppDuration()) * viewWidth) / totalDurationMs);
                    }
                } else {
                    left = leftViewWidth + (int)((mediaItemStartTimeMs * viewWidth) /
                            totalDurationMs);
                    right = leftViewWidth + (int)(((mediaItemStartTimeMs
                            + mediaItem.getAppTimelineDuration()) * viewWidth) / totalDurationMs);
                }

                view.layout(left, 0, right, b - t);

                mediaItemStartTimeMs += mediaItem.getAppTimelineDuration();
                if (mediaItem.getEndTransition() != null) {
                    mediaItemStartTimeMs -= mediaItem.getEndTransition().getAppDuration();
                }

                left = right;
            } else if (view == mLeftHandle) {
                if (mResizingView != null) {
                    view.layout(mResizingView.getLeft() - mHandleWidth,
                            mResizingView.getPaddingTop(),
                            mResizingView.getLeft(), b - t - mResizingView.getPaddingBottom());
                }
            } else if (view == mRightHandle) {
                if (mResizingView != null) {
                    view.layout(mResizingView.getRight(), mResizingView.getPaddingTop(),
                            mResizingView.getRight() + mHandleWidth,
                            b - t - mResizingView.getPaddingBottom());
                }
            } else if (i == 0) { // Begin view
                view.layout(left, 0, left + leftViewWidth, b - t);
                left += leftViewWidth;
            } else { // End view
                view.layout(getWidth() - mHalfParentWidth - (mHalfParentWidth - leftViewWidth), 0,
                        getWidth(), b - t);
            }
        }

        mMoveLayoutPending = false;
    }

    /**
     * Create a new dialog
     *
     * @param id The dialog id
     * @param bundle The dialog bundle
     * @return The dialog
     */
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        // If the project is not yet loaded do nothing.
        if (mProject == null) {
            return null;
        }

        switch (id) {
            case VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity) getContext();
                return AlertDialogs.createAlert(activity, FileUtils.getSimpleName(
                        mediaItem.getFilename()), 0,
                        activity.getString(R.string.editor_remove_overlay_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mOverlayActionMode != null) {
                            mOverlayActionMode.finish();
                            mOverlayActionMode = null;
                        }
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID);

                        ApiService.removeOverlay(activity, mProject.getPath(), mediaItem.getId(),
                                mediaItem.getOverlay().getId());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID);
                    }
                }, true);
            }

            default: {
                return null;
            }
        }
    }

    /**
     * Find the overlay view with the specified id
     *
     * @param mediaItemId The media item id
     * @return The overlay view
     */
    private View getOverlayView(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final MovieMediaItem mediaItem = (MovieMediaItem)childView.getTag();
            if (mediaItem != null && mediaItemId.equals(mediaItem.getId())) {
                return childView;
            }
        }

        return null;
    }

    /**
     * Find the media item view index with the specified id
     *
     * @param mediaItemId The media item id
     * @return The media item view index
     */
    private int getMediaItemViewIndex(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem)tag;
                if (mediaItemId.equals(mediaItem.getId())) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Remove all overlay views (leave the beginning and end views)
     */
    private void removeViews() {
        int index = 0;
        while (index < getChildCount()) {
            final MovieMediaItem mediaItem = (MovieMediaItem)getChildAt(index).getTag();
            if (mediaItem != null) {
                removeViewAt(index);
            } else {
                index++;
            }
        }

        requestLayout();
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected == false) {
            // Close the contextual action bar
            if (mOverlayActionMode != null) {
                mOverlayActionMode.finish();
                mOverlayActionMode = null;
            }

            mLeftHandle.setVisibility(View.GONE);
            mLeftHandle.setListener(null);
            mRightHandle.setVisibility(View.GONE);
            mRightHandle.setListener(null);
            mResizingView = null;
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
     * @param view The view to select
     * @param selected true if selected
     */
    private void selectView(final View selectedView, boolean selected) {
        // Check if the selection has changed
        if (selectedView.isSelected() == selected) {
            return;
        }

        if (selected == false) {
            // Select the new view
            selectedView.setSelected(selected);
            mResizingView = null;
            mLeftHandle.setVisibility(View.GONE);
            mLeftHandle.setListener(null);
            mRightHandle.setVisibility(View.GONE);
            mRightHandle.setListener(null);
            return;
        }

        // Unselect all other views
        unselectAllViews();

        // Select the new view
        selectedView.setSelected(selected);

        final Object tag = selectedView.getTag();
        final MovieMediaItem mediaItem = (MovieMediaItem)tag;
        if (mOverlayActionMode == null) {
            startActionMode(new OverlayActionModeCallback(mediaItem));
        }

        final MovieOverlay overlay = mediaItem.getOverlay();
        final View overlayView = getOverlayView(mediaItem.getId());
        mResizingView = overlayView;

        mRightHandle.setVisibility(View.VISIBLE);
        mRightHandle.bringToFront();
        mRightHandle.setLimitReached(overlay.getAppDuration() <=
            MediaItemUtils.getMinimumMediaItemDuration(mediaItem),
                overlay.getAppStartTime() + overlay.getAppDuration() >=
                    mediaItem.getAppBoundaryEndTime());
        mRightHandle.setListener(new HandleView.MoveListener() {
            private MovieMediaItem mMediaItem;
            private int mMovePosition;
            private long mMinimumDurationMs;

            @Override
            public void onMoveBegin(HandleView view) {
                mMediaItem = mediaItem;
                mMinimumDurationMs = MediaItemUtils.getMinimumMediaItemDuration(mediaItem);
            }

            @Override
            public boolean onMove(HandleView view, int left, int delta) {
                if (mMoveLayoutPending) {
                    return false;
                }

                final int position = left + delta;
                // Compute what will become the width of the view
                final int newWidth = position - overlayView.getLeft();

                // Compute the new duration
                long newDurationMs = (newWidth * mProject.computeDuration()) /
                        (getWidth() - (2 * mHalfParentWidth));

                if (newDurationMs < mMinimumDurationMs) {
                    newDurationMs = mMinimumDurationMs;
                } else if (overlay.getAppStartTime() + newDurationMs >
                        mMediaItem.getAppBoundaryEndTime()) {
                    newDurationMs = mMediaItem.getAppBoundaryEndTime() - overlay.getAppStartTime();
                }

                mRightHandle.setLimitReached(overlay.getAppDuration() <= mMinimumDurationMs,
                        overlay.getAppStartTime() + overlay.getAppDuration() >=
                            mMediaItem.getAppBoundaryEndTime());
                overlay.setAppDuration(newDurationMs);

                mMovePosition = position;
                mMoveLayoutPending = true;

                requestLayout();

                return true;
            }

            @Override
            public void onMoveEnd(final HandleView view, final int left, final int delta) {
                final int position = left + delta;
                if (mMoveLayoutPending || (position != mMovePosition)) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mMoveLayoutPending) {
                                mHandler.post(this);
                            } else if (position != mMovePosition) {
                                if (onMove(view, left, delta)) {
                                    mHandler.post(this);
                                } else {
                                    scaleDone();
                                }
                            } else {
                                scaleDone();
                            }
                        }
                    });
                } else {
                    scaleDone();
                }
            }

            /**
             * Scale is done
             */
            public void scaleDone() {
                ApiService.setOverlayDuration(getContext(), mProject.getPath(),
                        mMediaItem.getId(), overlay.getId(), overlay.getAppDuration());
            }
        });
    }

    /**
     * Unselect all views
     */
    private void unselectAllViews() {
        ((RelativeLayout)getParent()).setSelected(false);
    }
}
