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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.videoeditor.EffectColor;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.Transition;
import android.media.videoeditor.TransitionSliding;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.videoeditor.AlertDialogs;
import com.android.videoeditor.EffectType;
import com.android.videoeditor.KenBurnsActivity;
import com.android.videoeditor.OverlayTitleEditor;
import com.android.videoeditor.TransitionType;
import com.android.videoeditor.TransitionsActivity;
import com.android.videoeditor.VideoEditorActivity;
import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieEffect;
import com.android.videoeditor.service.MovieMediaItem;
import com.android.videoeditor.service.MovieOverlay;
import com.android.videoeditor.service.MovieTransition;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.util.MediaItemUtils;
import com.android.videoeditor.R;

/**
 * LinearLayout which holds media items and transitions.
 */
public class MediaLinearLayout extends LinearLayout {
    // Logging
    private static final String TAG = "MediaLinearLayout";

    // Dialog parameter ids
    private static final String PARAM_DIALOG_MEDIA_ITEM_ID = "media_item_id";
    private static final String PARAM_DIALOG_CURRENT_RENDERING_MODE = "rendering_mode";
    private static final String PARAM_DIALOG_TRANSITION_ID = "transition_id";

    // Transition duration limits
    private static final long MAXIMUM_IMAGE_DURATION = 6000;
    private static final long MAXIMUM_TRANSITION_DURATION = 3000;
    private static final long MINIMUM_TRANSITION_DURATION = 250;

    private static final long TIME_TOLERANCE = 30;

    // Instance variables
    private final ItemSimpleGestureListener mMediaItemGestureListener;
    private final ItemSimpleGestureListener mTransitionGestureListener;
    private final Handler mHandler;
    private final int mHalfParentWidth;
    private final int mHandleWidth;
    private final int mTransitionVerticalInset;
    private final ImageButton mLeftAddClipButton, mRightAddClipButton;
    private MediaLinearLayoutListener mListener;
    private ActionMode mMediaItemActionMode;
    private ActionMode mTransitionActionMode;
    private VideoEditorProject mProject;
    private boolean mPlaybackInProgress;
    private HandleView mLeftHandle, mRightHandle;
    private boolean mIsTrimming;  // Indicates if some media item is being trimmed.
    private boolean mMoveLayoutPending;
    private View mScrollView;  // Convenient handle to the parent scroll view.
    private View mSelectedView;
    private String mDragMediaItemId;
    private float mPrevDragPosition;
    private long mPrevDragScrollTime;
    private MovieMediaItem mDropAfterMediaItem;
    private int mDropIndex;
    private boolean mFirstEntered;

    /**
     * The media item action mode handler.
     */
    private class MediaItemActionModeCallback implements ActionMode.Callback {
        // Media item associated with this callback.
        private final MovieMediaItem mMediaItem;

        public MediaItemActionModeCallback(MovieMediaItem mediaItem) {
            mMediaItem = mediaItem;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mMediaItemActionMode = mode;

            final Activity activity = (Activity) getContext();
            activity.getMenuInflater().inflate(R.menu.media_item_mode_menu, menu);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final boolean enable = !ApiService.isProjectBeingEdited(mProject.getPath()) &&
                !mPlaybackInProgress;

            // Pan zoom effect is only for images. Hide it from video clips.
            MenuItem item;
            if (!mMediaItem.isImage()) {
                item = menu.findItem(R.id.action_pan_zoom_effect);
                item.setVisible(false);
                item.setEnabled(false);
            }

            // If the selected media item already has an effect applied on it, check the
            // corresponding effect menu item.
            MovieEffect effect = mMediaItem.getEffect();
            if (effect != null) {
                switch (mMediaItem.getEffect().getType()) {
                    case EffectType.EFFECT_KEN_BURNS:
                        item = menu.findItem(R.id.action_pan_zoom_effect);
                        break;
                    case EffectType.EFFECT_COLOR_GRADIENT:
                        item = menu.findItem(R.id.action_gradient_effect);
                        break;
                    case EffectType.EFFECT_COLOR_SEPIA:
                        item = menu.findItem(R.id.action_sepia_effect);
                        break;
                    case EffectType.EFFECT_COLOR_NEGATIVE:
                        item = menu.findItem(R.id.action_negative_effect);
                        break;
                    default:
                        item = menu.findItem(R.id.action_no_effect);
                        break;
                }
            } else {
                item = menu.findItem(R.id.action_no_effect);
            }
            item.setChecked(true);
            menu.findItem(R.id.media_item_effect_menu).setEnabled(enable);

            // Menu item for adding a new overlay. It is also used to edit
            // existing overlay. We change the displayed text accordingly.
            final MenuItem aomi = menu.findItem(R.id.action_add_overlay);
            aomi.setTitle((mMediaItem.getOverlay() == null) ?
                    R.string.editor_add_overlay : R.string.editor_edit_overlay);
            aomi.setEnabled(enable);

            final MenuItem romi = menu.findItem(R.id.action_remove_overlay);
            romi.setVisible(mMediaItem.getOverlay() != null);
            romi.setEnabled(enable && mMediaItem.getOverlay() != null);

            final MenuItem btmi = menu.findItem(R.id.action_add_begin_transition);
            btmi.setVisible(mMediaItem.getBeginTransition() == null);
            btmi.setEnabled(enable && mMediaItem.getBeginTransition() == null);

            final MenuItem etmi = menu.findItem(R.id.action_add_end_transition);
            etmi.setVisible(mMediaItem.getEndTransition() == null);
            etmi.setEnabled(enable && mMediaItem.getEndTransition() == null);

            final MenuItem rmmi = menu.findItem(R.id.action_rendering_mode);
            rmmi.setVisible(mProject.hasMultipleAspectRatios());
            rmmi.setEnabled(enable && mProject.hasMultipleAspectRatios());

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add_overlay: {
                    editOverlay(mMediaItem);
                    break;
                }

                case R.id.action_remove_overlay: {
                    removeOverlay(mMediaItem);
                    break;
                }

                case R.id.action_add_begin_transition: {
                    final MovieMediaItem prevMediaItem = mProject.getPreviousMediaItem(
                            mMediaItem.getId());
                    pickTransition(prevMediaItem);
                    break;
                }

                case R.id.action_add_end_transition: {
                    pickTransition(mMediaItem);
                    break;
                }

                case R.id.action_gradient_effect:
                case R.id.action_sepia_effect:
                case R.id.action_negative_effect:
                case R.id.action_pan_zoom_effect: {
                    applyEffect(item);
                    break;
                }

                case R.id.action_no_effect: {
                    if (!item.isChecked()) {
                        final Bundle bundle = new Bundle();
                        bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                        ((Activity) getContext()).showDialog(
                                VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID, bundle);
                    }
                    break;
                }

                case R.id.action_rendering_mode: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    bundle.putInt(PARAM_DIALOG_CURRENT_RENDERING_MODE,
                            mMediaItem.getAppRenderingMode());
                    ((Activity) getContext()).showDialog(
                            VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID, bundle);
                    break;
                }

                case R.id.action_delete_media_item: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    ((Activity) getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID, bundle);
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
            final View mediaItemView = getMediaItemView(mMediaItem.getId());
            if (mSelectedView != null) {
                mLeftHandle.endMove();
                mRightHandle.endMove();
            }
            unSelect(mediaItemView);
            showAddMediaItemButtons(true);
            mMediaItemActionMode = null;
        }

        private void applyEffect(MenuItem clickedItem) {
            if (!clickedItem.isChecked()) {
                switch(clickedItem.getItemId()) {
                    case R.id.action_gradient_effect:
                        addEffect(EffectType.EFFECT_COLOR_GRADIENT,
                                mMediaItem.getId(), null, null);
                        clickedItem.setChecked(true);
                        break;
                    case R.id.action_sepia_effect:
                        addEffect(EffectType.EFFECT_COLOR_SEPIA,
                                mMediaItem.getId(), null, null);
                        clickedItem.setChecked(true);
                        break;
                    case R.id.action_negative_effect:
                        addEffect(EffectType.EFFECT_COLOR_NEGATIVE,
                                mMediaItem.getId(), null, null);
                        clickedItem.setChecked(true);
                        break;
                    case R.id.action_pan_zoom_effect: {
                        // Note that we don't check the pan zoom checkbox here.
                        // Because pan zoom effect will start a new activity and users
                        // could cancel applying the effect. Once pan zoom effect has
                        // really been applied. The action mode will be invalidated in
                        // onActivityResult() method and the checkbox is then checked.
                        final Intent intent = new Intent(getContext(), KenBurnsActivity.class);
                        intent.putExtra(KenBurnsActivity.PARAM_MEDIA_ITEM_ID, mMediaItem.getId());
                        intent.putExtra(KenBurnsActivity.PARAM_FILENAME, mMediaItem.getFilename());
                        intent.putExtra(KenBurnsActivity.PARAM_WIDTH, mMediaItem.getWidth());
                        intent.putExtra(KenBurnsActivity.PARAM_HEIGHT, mMediaItem.getHeight());
                        ((Activity) getContext()).startActivityForResult(intent,
                                VideoEditorActivity.REQUEST_CODE_KEN_BURNS);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    /**
     * The transition action mode handler.
     */
    private class TransitionActionModeCallback implements ActionMode.Callback {
        private final MovieTransition mTransition;

        public TransitionActionModeCallback(MovieTransition transition) {
            mTransition = transition;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mTransitionActionMode = mode;

            final Activity activity = (Activity) getContext();
            activity.getMenuInflater().inflate(R.menu.transition_mode_menu, menu);
            mode.setTitle(activity.getString(R.string.editor_transition_title));

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final boolean enable = !ApiService.isProjectBeingEdited(mProject.getPath()) &&
                !mPlaybackInProgress;

            final MenuItem etmi = menu.findItem(R.id.action_change_transition);
            etmi.setEnabled(enable);

            final MenuItem rtmi = menu.findItem(R.id.action_remove_transition);
            rtmi.setEnabled(enable);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_remove_transition: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_TRANSITION_ID, mTransition.getId());
                    ((Activity) getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID, bundle);
                    break;
                }

                case R.id.action_change_transition: {
                    editTransition(mTransition);
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
            final View transitionView = getTransitionView(mTransition.getId());
            unSelect(transitionView);
            showAddMediaItemButtons(true);
            mTransitionActionMode = null;
        }
    }

    public MediaLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mMediaItemGestureListener = new ItemSimpleGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                switch (area) {
                    case ItemSimpleGestureListener.LEFT_AREA: {
                        if (view.isSelected()) {
                            final MovieMediaItem mediaItem = (MovieMediaItem) view.getTag();
                            final MovieMediaItem prevMediaItem = mProject.getPreviousMediaItem(
                                    mediaItem.getId());
                            pickTransition(prevMediaItem);
                        }
                        break;
                    }

                    case ItemSimpleGestureListener.CENTER_AREA: {
                        break;
                    }

                    case ItemSimpleGestureListener.RIGHT_AREA: {
                        if (view.isSelected()) {
                            pickTransition((MovieMediaItem) view.getTag());
                        }
                        break;
                    }
                }
                select(view);

                return true;
            }

            @Override
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
                if (mProject.getMediaItemCount() > 1) {
                    view.startDrag(ClipData.newPlainText("File", mediaItem.getFilename()),
                            ((MediaItemView)view).getShadowBuilder(), mediaItem.getId(), 0);
                }

                select(view);

                if (mMediaItemActionMode == null) {
                    startActionMode(new MediaItemActionModeCallback(mediaItem));
                }
            }
        };

        mTransitionGestureListener = new ItemSimpleGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }
                select(view);
                return true;
            }

            @Override
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                select(view);

                if (mTransitionActionMode == null) {
                    startActionMode(new TransitionActionModeCallback(
                            (MovieTransition) view.getTag()));
                }
            }
        };

        // Add the beginning timeline item
        final View beginView = inflate(getContext(), R.layout.empty_left_timeline_item, null);
        beginView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unselectAllTimelineViews();
            }
        });

        mLeftAddClipButton = (ImageButton) beginView.findViewById(
                R.id.add_left_media_item_button);
        mLeftAddClipButton.setVisibility(View.GONE);
        mLeftAddClipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mProject != null && mProject.getMediaItemCount() > 0) {
                    unselectAllTimelineViews();
                    // Add a clip at the beginning of the movie.
                    mListener.onAddMediaItem(null);
                }
            }
        });
        addView(beginView);

        // Add the end timeline item
        final View endView = inflate(getContext(), R.layout.empty_right_timeline_item, null);
        endView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unselectAllTimelineViews();
            }
        });

        mRightAddClipButton = (ImageButton) endView.findViewById(
                R.id.add_right_media_item_button);
        mRightAddClipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mProject != null) {
                    unselectAllTimelineViews();
                    // Add a clip at the end of the movie.
                    final MovieMediaItem lastMediaItem = mProject.getLastMediaItem();
                    if (lastMediaItem != null) {
                        mListener.onAddMediaItem(lastMediaItem.getId());
                    } else {
                        mListener.onAddMediaItem(null);
                    }
                }
            }
        });
        addView(endView);

        mLeftHandle = (HandleView)inflate(getContext(), R.layout.left_handle_view, null);
        addView(mLeftHandle);

        mRightHandle = (HandleView)inflate(getContext(), R.layout.right_handle_view, null);
        addView(mRightHandle);

        mHandleWidth = (int) context.getResources().getDimension(R.dimen.handle_width);

        mTransitionVerticalInset = (int) context.getResources().getDimension(
                R.dimen.timelime_transition_vertical_inset);

        // Compute half the width of the screen (and therefore the parent view).
        final Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        mHandler = new Handler();

        setMotionEventSplittingEnabled(false);
    }

    public MediaLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaLinearLayout(Context context) {
        this(context, null, 0);
    }

    public void setParentTimelineScrollView(View scrollView) {
        mScrollView = scrollView;
    }

    /**
     * Called when the containing activity is resumed.
     */
    public void onResume() {
        // Invalidate all progress in case the transition generation or
        // Ken Burns effect completed while the activity was being paused.
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object item = childView.getTag();
            if (item != null) {
                if (item instanceof MovieMediaItem) {
                    ((MediaItemView) childView).resetGeneratingEffectProgress();
                } else if (item instanceof MovieTransition) {
                    ((TransitionView) childView).resetGeneratingTransitionProgress();
                }
            }
        }
    }

    public void setListener(MediaLinearLayoutListener listener) {
        mListener = listener;
    }

    public void setProject(VideoEditorProject project) {
        closeActionBars();
        clearAndHideTrimHandles();
        removeAllMediaItemAndTransitionViews();

        mProject = project;
    }

    /**
     * @param inProgress {@code true} if playback is in progress, false otherwise
     */
    public void setPlaybackInProgress(boolean inProgress) {
        mPlaybackInProgress = inProgress;
        setPlaybackState(inProgress);
        // Don't allow the user to interact with media items or
        // transitions while the playback is in progress.
        closeActionBars();
    }

    /**
     * Returns selected view's position on the timeline; -1 if none.
     */
    public int getSelectedViewPos() {
        return indexOfChild(mSelectedView);
    }

    /**
     * Selects the view at the specified position; null if it does not exist.
     */
    public void setSelectedView(int pos) {
        if (pos < 0) {
            return;
        }
        mSelectedView = getChildAt(pos);
        if (mSelectedView != null) {
            select(mSelectedView);
        }
    }

    /**
     * Clears existing media or transition items and adds all given media items.
     *
     * @param mediaItems The list of media items
     */
    public void addMediaItems(List<MovieMediaItem> mediaItems) {
        closeActionBars();
        removeAllMediaItemAndTransitionViews();

        for (MovieMediaItem mediaItem : mediaItems) {
            addMediaItem(mediaItem);
        }
    }

    /**
     * Adds a new media item at the end of the timeline.
     *
     * @param mediaItem The media item
     */
    private void addMediaItem(MovieMediaItem mediaItem) {
        final View mediaItemView = inflate(getContext(), R.layout.media_item, null);
        ((MediaItemView) mediaItemView).setGestureListener(mMediaItemGestureListener);
        ((MediaItemView) mediaItemView).setProjectPath(mProject.getPath());
        mediaItemView.setTag(mediaItem);

        // Add the new view
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.FILL_PARENT);
        // Add the view before the end view, left handle and right handle views.
        addView(mediaItemView, getChildCount() - 3, lp);

        // If the new media item has beginning and end transitions, add them.
        final MovieTransition beginTransition = mediaItem.getBeginTransition();
        if (beginTransition != null) {
            final int cc = getChildCount();
            // Account for the beginning and end views and the trim handles
            if (cc > 5) { // There is a previous view (transition or media item)
                final View view = getChildAt(cc - 5);
                final Object tag = view.getTag();
                // Do not add transition if it already exists
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem prevMediaItem = (MovieMediaItem)tag;
                    addTransition(beginTransition, prevMediaItem.getId());
                }
            } else { // This is the first media item
                addTransition(beginTransition, null);
            }
        }

        final MovieTransition endTransition = mediaItem.getEndTransition();
        if (endTransition != null) {
            addTransition(endTransition, mediaItem.getId());
        }

        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        // Now we can add clips by tapping the beginning view
        mLeftAddClipButton.setVisibility(View.VISIBLE);
    }

    /**
     * Inserts a new media item after the specified media item id.
     *
     * @param mediaItem The media item
     * @param afterMediaItemId The id of the media item preceding the media item
     */
    public void insertMediaItem(MovieMediaItem mediaItem, String afterMediaItemId) {
        final View mediaItemView = inflate(getContext(), R.layout.media_item, null);
        ((MediaItemView)mediaItemView).setGestureListener(mMediaItemGestureListener);
        ((MediaItemView)mediaItemView).setProjectPath(mProject.getPath());

        mediaItemView.setTag(mediaItem);

        int insertViewIndex;
        if (afterMediaItemId != null) {
            if ((insertViewIndex = getMediaItemViewIndex(afterMediaItemId)) == -1) {
                Log.e(TAG, "Media item not found: " + afterMediaItemId);
                return;
            }

            insertViewIndex++;

            if (insertViewIndex < getChildCount()) {
                final Object tag = getChildAt(insertViewIndex).getTag();
                if (tag != null && tag instanceof MovieTransition) {
                    // Remove the transition following the media item
                    removeViewAt(insertViewIndex);
                }
            }
        } else { // Insert at the beginning
            // If we have a transition at the beginning remove it
            final Object tag = getChildAt(1).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                removeViewAt(1);
            }

            insertViewIndex = 1;
        }

        // Add the new view
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(mediaItemView, insertViewIndex, lp);

        // If the new media item has beginning and end transitions add them
        final MovieTransition beginTransition = mediaItem.getBeginTransition();
        if (beginTransition != null) {
            if (insertViewIndex > 1) { // There is a previous view (transition or media item)
                final View view = getChildAt(insertViewIndex - 1);
                final Object tag = view.getTag();
                // Do not add transition if it already exists
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem prevMediaItem = (MovieMediaItem)tag;
                    addTransition(beginTransition, prevMediaItem.getId());
                }
            } else { // This is the first media item
                addTransition(beginTransition, null);
            }
        }

        final MovieTransition endTransition = mediaItem.getEndTransition();
        if (endTransition != null) {
            addTransition(endTransition, mediaItem.getId());
        }

        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        // Now we can add clips by tapping the beginning view
        mLeftAddClipButton.setVisibility(View.VISIBLE);
    }

    /**
     * Updates the specified media item.
     *
     * @param mediaItem The media item to be updated
     */
    public void updateMediaItem(MovieMediaItem mediaItem) {
        final String mediaItemId = mediaItem.getId();
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem) tag;
                if (mediaItemId.equals(mi.getId())) {
                    if (mediaItem != mi) {
                        // The media item is a new instance of the media item
                        childView.setTag(mediaItem);
                        if (mediaItem.getBeginTransition() != null) {
                            if (i > 0) {
                                final View tView = getChildAt(i - 1);
                                final Object tagT = tView.getTag();
                                if (tagT != null && tagT instanceof MovieTransition) {
                                    tView.setTag(mediaItem.getBeginTransition());
                                }
                            }
                        }

                        if (mediaItem.getEndTransition() != null) {
                            if (i < childrenCount - 1) {
                                final View tView = getChildAt(i + 1);
                                final Object tagT = tView.getTag();
                                if (tagT != null && tagT instanceof MovieTransition) {
                                    tView.setTag(mediaItem.getEndTransition());
                                }
                            }
                        }
                    }

                    if (childView.isSelected()) {
                        mLeftHandle.setEnabled(true);
                        mRightHandle.setEnabled(true);
                    }

                    break;
                }
            }
        }

        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
    }

    /**
     * Removes a media item view.
     *
     * @param mediaItemId The media item id
     * @param transition The transition inserted at the removal position
     *          if a theme is in use.
     *
     * @return The view which was removed
     */
    public View removeMediaItem(String mediaItemId, MovieTransition transition) {
        final int childrenCount = getChildCount();
        MovieMediaItem prevMediaItem = null;
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem)tag;
                if (mediaItemId.equals(mi.getId())) {
                    int mediaItemViewIndex = i;

                    // Remove the before transition
                    if (mediaItemViewIndex > 0) {
                        final Object beforeTag = getChildAt(mediaItemViewIndex - 1).getTag();
                        if (beforeTag != null && beforeTag instanceof MovieTransition) {
                            // Remove the transition view
                            removeViewAt(mediaItemViewIndex - 1);
                            mediaItemViewIndex--;
                        }
                    }

                    // Remove the after transition view
                    if (mediaItemViewIndex < getChildCount() - 1) {
                        final Object afterTag = getChildAt(mediaItemViewIndex + 1).getTag();
                        if (afterTag != null && afterTag instanceof MovieTransition) {
                            // Remove the transition view
                            removeViewAt(mediaItemViewIndex + 1);
                        }
                    }

                    // Remove the media item view
                    removeViewAt(mediaItemViewIndex);

                    if (transition != null) {
                        addTransition(transition,
                                prevMediaItem != null ? prevMediaItem.getId() : null);
                    }

                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }

                    if (mProject.getMediaItemCount() == 0) {
                        // We cannot add clips by tapping the beginning view
                        mLeftAddClipButton.setVisibility(View.GONE);
                    }
                    return childView;
                }

                prevMediaItem = mi;
            }
        }

        return null;
    }

    /**
     * Creates a new transition.
     *
     * @param afterMediaItemId Insert the transition after this media item id
     * @param transitionType The transition type
     * @param transitionDurationMs The transition duration in ms
     */
    public void addTransition(String afterMediaItemId, int transitionType,
            long transitionDurationMs) {
        unselectAllTimelineViews();

        final MovieMediaItem afterMediaItem;
        if (afterMediaItemId != null) {
            afterMediaItem = mProject.getMediaItem(afterMediaItemId);
            if (afterMediaItem == null) {
                return;
            }
        } else {
            afterMediaItem = null;
        }

        final String id = ApiService.generateId();
        switch (transitionType) {
            case TransitionType.TRANSITION_TYPE_ALPHA_CONTOUR: {
                ApiService.insertAlphaTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        R.raw.mask_contour, 50, false);
                break;
            }

            case TransitionType.TRANSITION_TYPE_ALPHA_DIAGONAL: {
                ApiService.insertAlphaTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        R.raw.mask_diagonal, 50, false);
                break;
            }

            case TransitionType.TRANSITION_TYPE_CROSSFADE: {
                ApiService.insertCrossfadeTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR);
                break;
            }

            case TransitionType.TRANSITION_TYPE_FADE_BLACK: {
                ApiService.insertFadeBlackTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_RIGHT_OUT_LEFT_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_RIGHT_OUT_LEFT_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_LEFT_OUT_RIGHT_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_LEFT_OUT_RIGHT_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_TOP_OUT_BOTTOM_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_TOP_OUT_BOTTOM_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_BOTTOM_OUT_TOP_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_BOTTOM_OUT_TOP_IN);
                break;
            }

            default: {
                break;
            }
        }

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
    }

    /**
     * Edits a transition.
     *
     * @param afterMediaItemId Insert the transition after this media item id
     * @param transitionId The transition id
     * @param transitionType The transition type
     * @param transitionDurationMs The transition duration in ms
     */
    public void editTransition(String afterMediaItemId, String transitionId, int transitionType,
            long transitionDurationMs) {
        final MovieTransition transition = mProject.getTransition(transitionId);
        if (transition == null) {
            return;
        }

        // Check if the type or duration had changed
        if (transition.getType() != transitionType) {
            // Remove the transition and add it again
            ApiService.removeTransition(getContext(), mProject.getPath(), transitionId);
            addTransition(afterMediaItemId, transitionType, transitionDurationMs);
        } else if (transition.getAppDuration() != transitionDurationMs) {
            transition.setAppDuration(transitionDurationMs);
            ApiService.setTransitionDuration(getContext(), mProject.getPath(), transitionId,
                    transitionDurationMs);
        }

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
    }

    /**
     * Adds a new transition after the specified media id. This method assumes that a
     * transition does not exist at the insertion point.
     *
     * @param transition The transition to be added
     * @param afterMediaItemId After the specified media item id
     *
     * @return The transition view that was added, {@code null} upon errors.
     */
    public View addTransition(MovieTransition transition, String afterMediaItemId) {
        // Determine the insert position
        int index;
        if (afterMediaItemId != null) {
            index = -1;
            final int childrenCount = getChildCount();
            for (int i = 0; i < childrenCount; i++) {
                final Object tag = getChildAt(i).getTag();
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem mi = (MovieMediaItem) tag;
                    if (afterMediaItemId.equals(mi.getId())) {
                        index = i + 1;
                        break;
                    }
                }
            }

            if (index < 0) {
                Log.e(TAG, "addTransition media item not found: " + afterMediaItemId);
                return null;
            }
        } else {
            index = 1;
        }

        final View transitionView = inflate(getContext(), R.layout.transition_view, null);
        ((TransitionView) transitionView).setGestureListener(mTransitionGestureListener);
        ((TransitionView) transitionView).setProjectPath(mProject.getPath());
        transitionView.setTag(transition);

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(transitionView, index, lp);

        // Adjust the size of all the views
        requestLayout();

        // If this transition was added by the user invalidate the menu item
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        return transitionView;
    }

    /**
     * Updates a transition.
     *
     * @param transitionId The transition id
     */
    public void updateTransition(String transitionId) {
        requestLayout();
        invalidate();
    }

    /**
     * Removes a transition with the specified id.
     *
     * @param transitionId The transition id
     */
    public void removeTransition(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    // Remove the view
                    removeViewAt(i);

                    // Adjust the size of all the views
                    requestLayout();

                    // If this transition was removed by the user invalidate the menu item
                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }

                    return;
                }
            }
        }
    }

    /**
     * Invalidates the available action modes. Used to refresh menu contents.
     */
    public void invalidateActionBar() {
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
        if (mTransitionActionMode != null) {
            mTransitionActionMode.invalidate();
        }
    }

    /**
     * A Ken Burns movie is encoded for an MediaImageItem.
     *
     * @param mediaItemId The media item id
     * @param action The action
     * @param progress Progress value (between 0..100)
     */
    public void onGeneratePreviewMediaItemProgress(String mediaItemId, int action, int progress) {
        // Display the progress while generating the Ken Burns video clip
        final MediaItemView view = (MediaItemView) getMediaItemView(mediaItemId);
        if (view != null) {
            view.setGeneratingEffectProgress(progress);

            if (view.isSelected()) {
                if (progress == 0) {
                    mLeftHandle.setEnabled(false);
                    mRightHandle.setEnabled(false);
                } else if (progress == 100) {
                    mLeftHandle.setEnabled(true);
                    mRightHandle.setEnabled(true);
                }
            }
        }
    }

    /**
     * A transition is being encoded.
     *
     * @param transitionId The transition id
     * @param action The action
     * @param progress The progress
     */
    public void onGeneratePreviewTransitionProgress(String transitionId, int action,
            int progress) {
        // Display the progress while generating the transition
        final TransitionView view = (TransitionView) getTransitionView(transitionId);
        if (view != null) {
            view.setGeneratingTransitionProgress(progress);

            if (view.isSelected()) {
                if (progress == 0) {
                    mLeftHandle.setEnabled(false);
                    mRightHandle.setEnabled(false);
                } else if (progress == 100) {
                    mLeftHandle.setEnabled(true);
                    mRightHandle.setEnabled(true);
                }
            }
        }
    }

    /**
     * Creates a new effect on the specified media item.
     *
     * @param effectType The effect type
     * @param mediaItemId Add the effect for this media item id
     * @param startRect The start rectangle
     * @param endRect The end rectangle
     */
    public void addEffect(int effectType, String mediaItemId, Rect startRect, Rect endRect) {
        final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
        if (mediaItem == null) {
            Log.e(TAG, "addEffect media item not found: " + mediaItemId);
            return;
        }

        final String id = ApiService.generateId();
        switch (effectType) {
            case EffectType.EFFECT_KEN_BURNS: {
                ApiService.addEffectKenBurns(getContext(), mProject.getPath(), mediaItemId,
                        id, 0, mediaItem.getDuration(), startRect, endRect);
                break;
            }

            case EffectType.EFFECT_COLOR_GRADIENT: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_GRADIENT,
                        EffectColor.GRAY);
                break;
            }

            case EffectType.EFFECT_COLOR_SEPIA: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_SEPIA, 0);
                break;
            }

            case EffectType.EFFECT_COLOR_NEGATIVE: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_NEGATIVE, 0);
                break;
            }

            default: {
                break;
            }
        }

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
    }

    /**
     * Set the media item thumbnail.
     *
     * @param mediaItemId The media item id
     * @param bitmap The bitmap
     * @param index The index of the bitmap
     * @param token The token given in the original request
     *
     * @return true if the bitmap is used
     */
    public boolean setMediaItemThumbnail(
            String mediaItemId, Bitmap bitmap, int index, int token) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem)tag;
                if (mediaItemId.equals(mi.getId())) {
                    return ((MediaItemView)getChildAt(i)).setBitmap(
                            bitmap, index, token);
                }
            }
        }

        return false;
    }

    /**
     * Sets the transition thumbnails.
     *
     * @param transitionId The transition id
     * @param bitmaps The bitmaps array
     *
     * @return true if the bitmaps were used
     */
    public boolean setTransitionThumbnails(String transitionId, Bitmap[] bitmaps) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    return ((TransitionView)getChildAt(i)).setBitmaps(bitmaps);
                }
            }
        }

        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Compute the total duration of the project.
        final long totalDurationMs = mProject.computeDuration();

        // Total available width for putting media items and transitions.
        // We subtract 2 half screen widths from the width because we put
        // 2 empty view at the beginning and end of the timeline, each with
        // half screen width. We then layout each child view into the
        // available width.
        final int viewWidth = getWidth() - (2 * mHalfParentWidth);

        // If we are in trimming mode, the left view width might be different
        // due to trimming; otherwise it equals half of screen width.
        final int leftViewWidth = (mSelectedView != null) ?
                (Integer) mScrollView.getTag(R.id.left_view_width) : mHalfParentWidth;

        // Top and bottom position are fixed for media item views. For transition views,
        // there is additional inset which makes them smaller. See below.
        final int top = getPaddingTop();
        final int bottom = b - t;

        long startMs = 0;
        int left = 0;

        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View view = getChildAt(i);
            final Object tag = view.getTag();
            if (tag != null) {
                final long durationMs = computeViewDuration(view);

                final int right = (int)((float)((startMs + durationMs) * viewWidth) /
                        (float)totalDurationMs) + leftViewWidth;

                if (tag instanceof MovieMediaItem) {
                    if (left != view.getLeft() || right != view.getRight()) {
                        final int oldLeft = view.getLeft();
                        final int oldRight = view.getRight();
                        view.layout(left, top, right, bottom);
                        ((MediaItemView) view).onLayoutPerformed(oldLeft, oldRight);
                    } else {
                        view.layout(left, top, right, bottom);
                    }
                } else {  // Transition view.
                    // Note that we set additional inset so it looks smaller
                    // than media item views on the timeline.
                    view.layout(left,
                            top + mTransitionVerticalInset,
                            right,
                            bottom - mTransitionVerticalInset);
                }

                startMs += durationMs;
                left = right;
            } else if (view == mLeftHandle && mSelectedView != null) {
                // We are in trimming mode, the left handle must be shown.
                view.layout(mSelectedView.getLeft() - mHandleWidth,
                        top + mSelectedView.getPaddingTop(),
                        mSelectedView.getLeft(),
                        bottom - mSelectedView.getPaddingBottom());
            } else if (view == mRightHandle && mSelectedView != null) {
                // We are in trimming mode, the right handle must be shown.
                view.layout(mSelectedView.getRight(),
                        top + mSelectedView.getPaddingTop(),
                        mSelectedView.getRight() + mHandleWidth,
                        bottom - mSelectedView.getPaddingBottom());
            } else if (i == 0) {  // Begin view
                view.layout(0, top, leftViewWidth, bottom);
                left += leftViewWidth;
            } else {  // End view
                view.layout(left, top, getWidth(), bottom);
            }
        }
        mMoveLayoutPending = false;
    }

    /**
     * Computes the duration of the specified view.
     *
     * @param view The specified view
     *
     * @return The duration in milliseconds, 0 if the specified view is not a media item view
     *         or a transition view
     */
    private long computeViewDuration(View view) {
        long durationMs;
        final Object tag = view.getTag();
        if (tag != null) {
            if (tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem) view.getTag();
                durationMs = mediaItem.getAppTimelineDuration();
                if (mediaItem.getBeginTransition() != null) {
                    durationMs -= mediaItem.getBeginTransition().getAppDuration();
                }

                if (mediaItem.getEndTransition() != null) {
                    durationMs -= mediaItem.getEndTransition().getAppDuration();
                }
            } else {  // Transition
                final MovieTransition transition = (MovieTransition) tag;
                durationMs = transition.getAppDuration();
            }
        } else {
            durationMs = 0;
        }

        return durationMs;
    }

    /**
     * Creates a new dialog.
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
            case VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity) getContext();
                return AlertDialogs.createAlert(activity,
                        FileUtils.getSimpleName(mediaItem.getFilename()),
                        0, mediaItem.isVideoClip() ?
                                activity.getString(R.string.editor_remove_video_question) :
                                    activity.getString(R.string.editor_remove_image_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mMediaItemActionMode != null) {
                            mMediaItemActionMode.finish();
                            mMediaItemActionMode = null;
                        }
                        unselectAllTimelineViews();

                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);

                        ApiService.removeMediaItem(activity, mProject.getPath(), mediaItem.getId(),
                                mProject.getTheme());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);
                    }
                }, true);
            }

            case VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(activity.getString(R.string.editor_change_rendering_mode));
                final CharSequence[] renderingModeStrings = new CharSequence[3];
                renderingModeStrings[0] = getContext().getString(R.string.rendering_mode_black_borders);
                renderingModeStrings[1] = getContext().getString(R.string.rendering_mode_stretch);
                renderingModeStrings[2] = getContext().getString(R.string.rendering_mode_crop);

                final int currentRenderingMode = bundle.getInt(PARAM_DIALOG_CURRENT_RENDERING_MODE);
                final int currentRenderingModeIndex;
                switch (currentRenderingMode) {
                    case MediaItem.RENDERING_MODE_CROPPING: {
                        currentRenderingModeIndex = 2;
                        break;
                    }

                    case MediaItem.RENDERING_MODE_STRETCH: {
                        currentRenderingModeIndex = 1;
                        break;
                    }

                    case MediaItem.RENDERING_MODE_BLACK_BORDER:
                    default: {
                        currentRenderingModeIndex = 0;
                        break;
                    }
                }

                builder.setSingleChoiceItems(renderingModeStrings, currentRenderingModeIndex,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_BLACK_BORDER);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(), mediaItem.getId(),
                                        MediaItem.RENDERING_MODE_BLACK_BORDER);
                                break;
                            }

                            case 1: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_STRETCH);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(),
                                        mediaItem.getId(), MediaItem.RENDERING_MODE_STRETCH);
                                break;
                            }

                            case 2: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_CROPPING);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(),
                                        mediaItem.getId(), MediaItem.RENDERING_MODE_CROPPING);
                                break;
                            }

                            default: {
                                break;
                            }
                        }
                        activity.removeDialog(VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID);
                    }
                });
                builder.setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID);
                    }
                });
                return builder.create();
            }

            case VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID: {
                final MovieTransition transition = mProject.getTransition(
                        bundle.getString(PARAM_DIALOG_TRANSITION_ID));
                if (transition == null) {
                    return null;
                }

                final Activity activity = (Activity) getContext();
                return AlertDialogs.createAlert(activity,
                        activity.getString(R.string.remove),
                        0, activity.getString(R.string.editor_remove_transition_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mTransitionActionMode != null) {
                            mTransitionActionMode.finish();
                            mTransitionActionMode = null;
                        }
                        unselectAllTimelineViews();
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);

                        ApiService.removeTransition(activity, mProject.getPath(),
                                transition.getId());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);
                    }
                }, true);
            }

            case VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity) getContext();
                return AlertDialogs.createAlert(activity,
                        FileUtils.getSimpleName(mediaItem.getFilename()),
                        0, activity.getString(R.string.editor_remove_effect_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);

                        ApiService.removeEffect(activity, mProject.getPath(),
                                mediaItem.getId(), mediaItem.getEffect().getId());

                        if (mMediaItemActionMode != null) {
                            mMediaItemActionMode.invalidate();
                        }
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);
                    }
                }, true);
            }

            default: {
                return null;
            }
        }
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        boolean result = false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                // Claim to accept any dragged content
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_STARTED: " + event);
                }

                mDragMediaItemId = (String)event.getLocalState();

                // Hide the handles while dragging
                mLeftHandle.setVisibility(View.GONE);
                mRightHandle.setVisibility(View.GONE);

                mDropAfterMediaItem = null;
                mDropIndex = -1;

                mFirstEntered = true;
                // This view accepts drag
                result = true;
                break;
            }

            case DragEvent.ACTION_DRAG_ENTERED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_ENTERED: " + event);
                }

                if (!mFirstEntered && mDropIndex >= 0) {
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_OK);
                } else {
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_NOT_OK);
                }
                mScrollView.invalidate();

                mFirstEntered = false;
                break;
            }

            case DragEvent.ACTION_DRAG_EXITED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_EXITED: " + event);
                }

                // Redraw the "normal playhead"
                mScrollView.setTag(R.id.playhead_type, TimelineHorizontalScrollView.PLAYHEAD_NORMAL);
                mScrollView.invalidate();
                break;
            }

            case DragEvent.ACTION_DRAG_ENDED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_ENDED: " + event);
                }

                mDragMediaItemId = null;
                mDropIndex = -1;

                // Hide the handles while dragging
                mLeftHandle.setVisibility(View.VISIBLE);
                mRightHandle.setVisibility(View.VISIBLE);

                // Redraw the "normal playhead"
                mScrollView.setTag(R.id.playhead_type, TimelineHorizontalScrollView.PLAYHEAD_NORMAL);
                mScrollView.invalidate();

                requestLayout();
                break;
            }

            case DragEvent.ACTION_DRAG_LOCATION: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_LOCATION: " + event);
                }

                moveToPosition(event.getX());
                // We returned true to DRAG_STARTED, so return true here
                result = true;
                break;
            }

            case DragEvent.ACTION_DROP: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DROP: " + event);
                }

                if (mDropIndex >= 0) {
                    final String afterMediaItemId =
                        mDropAfterMediaItem != null ? mDropAfterMediaItem.getId() : null;
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "ACTION_DROP: Index: " + mDropIndex + " | " + afterMediaItemId);
                    }
                    ApiService.moveMediaItem(getContext(), mProject.getPath(), mDragMediaItemId,
                            afterMediaItemId, null);
                }
                result = true;
                break;
            }


            default: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Other drag event: " + event);
                }
                result = true;
                break;
            }
        }

        return result;
    }

    /**
     * Move the playhead during a move operation
     *
     * @param eventX The event horizontal position
     */
    private void moveToPosition(float eventX) {
        final int x = (int)eventX - mScrollView.getScrollX();
        final long now = System.currentTimeMillis();
        if (now - mPrevDragScrollTime > 300) {
            if (x < mPrevDragPosition - 42) { // Backwards
                final long positionMs = getLeftDropPosition();
                if (mDropIndex >= 0) {
                    // Redraw the "move ok playhead"
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_OK);
                } else {
                    // Redraw the "move not ok playhead"
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_NOT_OK);
                }

                mListener.onRequestMovePlayhead(positionMs, true);
                mScrollView.invalidate();

                mPrevDragPosition = x;
                mPrevDragScrollTime = now;
            } else if (x > mPrevDragPosition + 42) { // Forward
                final long positionMs = getRightDropPosition();
                if (mDropIndex >= 0) {
                    // Redraw the "move ok playhead"
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_OK);
                } else {
                    // Redraw the "move not ok playhead"
                    mScrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_NOT_OK);
                }

                mListener.onRequestMovePlayhead(positionMs, true);
                mScrollView.invalidate();

                mPrevDragPosition = x;
                mPrevDragScrollTime = now;
            }
        } else {
            mPrevDragPosition = x;
        }
    }

    // Returns the begin time of a media item (exclude transition).
    private long getBeginTime(MovieMediaItem item) {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long beginMs = 0;
        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);
            final MovieTransition beginTransition = mediaItem.getBeginTransition();
            final MovieTransition endTransition = mediaItem.getEndTransition();

            if (item.getId().equals(mediaItem.getId())) {
                if (beginTransition != null) {
                    beginMs += beginTransition.getAppDuration();
                }
                return beginMs;
            }

            beginMs += mediaItem.getAppTimelineDuration();

            if (endTransition != null) {
                beginMs -= endTransition.getAppDuration();
            }
        }

        return 0;
    }

    // Returns the end time of a media item (exclude transition)
    private long getEndTime(MovieMediaItem item) {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long endMs = 0;
        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);
            final MovieTransition beginTransition = mediaItem.getBeginTransition();
            final MovieTransition endTransition = mediaItem.getEndTransition();

            endMs += mediaItem.getAppTimelineDuration();

            if (endTransition != null) {
                endMs -= endTransition.getAppDuration();
            }

            if (item.getId().equals(mediaItem.getId())) {
                return endMs;
            }

        }

        return 0;
    }

    /**
     * @return The valid time location of the drop (-1 if none)
     */
    private long getLeftDropPosition() {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long beginMs = 0;
        long endMs = 0;
        long timeMs = mProject.getPlayheadPos();

        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);

            endMs = beginMs + mediaItem.getAppTimelineDuration();

            if (mediaItem.getEndTransition() != null) {
                if (i < mediaItemsCount - 1) {
                    endMs -= mediaItem.getEndTransition().getAppDuration();
                }
            }

            if (timeMs > beginMs && timeMs <= endMs) {
                if (mediaItem.getBeginTransition() != null) {
                    beginMs += mediaItem.getBeginTransition().getAppDuration();
                }

                if (!mDragMediaItemId.equals(mediaItem.getId())) {
                    if (i > 0) {
                        // Check if the previous item is the drag item
                        final MovieMediaItem prevMediaItem = mediaItems.get(i - 1);
                        if (!mDragMediaItemId.equals(prevMediaItem.getId())) {
                            mDropAfterMediaItem = prevMediaItem;
                            mDropIndex = i;
                            return beginMs;
                        } else {
                            mDropAfterMediaItem = null;
                            mDropIndex = -1;
                            return beginMs;
                        }
                    } else {
                        mDropAfterMediaItem = null;
                        mDropIndex = 0;
                        return 0;
                    }
                } else {
                    mDropAfterMediaItem = null;
                    mDropIndex = -1;
                    return beginMs;
                }
            }

            beginMs = endMs;
        }

        return timeMs;
    }

    /**
     * @return The valid time location of the drop (-1 if none)
     */
    private long getRightDropPosition() {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long beginMs = 0;
        long endMs = 0;
        long timeMs = mProject.getPlayheadPos();

        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);

            endMs = beginMs + mediaItem.getAppTimelineDuration();

            if (mediaItem.getEndTransition() != null) {
                if (i < mediaItemsCount - 1) {
                    endMs -= mediaItem.getEndTransition().getAppDuration();
                }
            }

            if (timeMs >= beginMs && timeMs < endMs) {
                if (!mDragMediaItemId.equals(mediaItem.getId())) {
                    if (i < mediaItemsCount - 1) {
                        // Check if the next item is the drag item
                        final MovieMediaItem nextMediaItem = mediaItems.get(i + 1);
                        if (!mDragMediaItemId.equals(nextMediaItem.getId())) {
                            mDropAfterMediaItem = mediaItem;
                            mDropIndex = i;
                            return endMs;
                        } else {
                            mDropAfterMediaItem = null;
                            mDropIndex = -1;
                            return endMs;
                        }
                    } else {
                        mDropAfterMediaItem = mediaItem;
                        mDropIndex = i;
                        return endMs;
                    }
                } else {
                    mDropAfterMediaItem = null;
                    mDropIndex = -1;
                    return endMs;
                }
            }

            beginMs = endMs;
        }

        return timeMs;
    }


    /**
     * Adds/edits title overlay of the specified media item.
     */
    private void editOverlay(MovieMediaItem mediaItem) {
        final Intent intent = new Intent(getContext(), OverlayTitleEditor.class);
        intent.putExtra(OverlayTitleEditor.PARAM_MEDIA_ITEM_ID, mediaItem.getId());

        // Determine if user wants to edit an existing title overlay or add a new one.
        // Add overlay id and attributes bundle to the extra if the overlay already exists.
        final MovieOverlay overlay = mediaItem.getOverlay();
        if (overlay != null) {
            final String overlayId = mediaItem.getOverlay().getId();
            intent.putExtra(OverlayTitleEditor.PARAM_OVERLAY_ID, overlayId);
            final Bundle attributes = MovieOverlay.buildUserAttributes(
                    overlay.getType(), overlay.getTitle(), overlay.getSubtitle());
            intent.putExtra(OverlayTitleEditor.PARAM_OVERLAY_ATTRIBUTES,
                    attributes);
        }
        ((Activity) getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_PICK_OVERLAY);
    }

    /**
     * Removes the overlay of the specified media item.
     */
    private void removeOverlay(MovieMediaItem mediaItem) {
        final Bundle bundle = new Bundle();
        bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mediaItem.getId());
        ((Activity) getContext()).showDialog(
                VideoEditorActivity.DIALOG_REMOVE_OVERLAY_ID, bundle);
    }

    /**
     * Picks a transition.
     *
     * @param afterMediaItem After the media item
     *
     * @return true if the transition can be inserted
     */
    private boolean pickTransition(MovieMediaItem afterMediaItem) {
        // Check if the transition would be too short
        final long transitionDurationMs = getTransitionDuration(afterMediaItem);
        if (transitionDurationMs < MINIMUM_TRANSITION_DURATION) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.editor_transition_too_short),
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        final String afterMediaId = afterMediaItem != null ? afterMediaItem.getId() : null;
        final Intent intent = new Intent(getContext(), TransitionsActivity.class);
        intent.putExtra(TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID, afterMediaId);
        intent.putExtra(TransitionsActivity.PARAM_MINIMUM_DURATION, MINIMUM_TRANSITION_DURATION);
        intent.putExtra(TransitionsActivity.PARAM_DEFAULT_DURATION, transitionDurationMs);
        intent.putExtra(TransitionsActivity.PARAM_MAXIMUM_DURATION,
                getMaxTransitionDuration(afterMediaItem));
        ((Activity) getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_PICK_TRANSITION);
        return true;
    }

    /**
     * Edits a transition.
     *
     * @param transition The transition
     */
    private void editTransition(MovieTransition transition) {
        final MovieMediaItem afterMediaItem = mProject.getPreviousMediaItem(transition);
        final String afterMediaItemId = afterMediaItem != null ? afterMediaItem.getId() : null;

        final Intent intent = new Intent(getContext(), TransitionsActivity.class);
        intent.putExtra(TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID, afterMediaItemId);
        intent.putExtra(TransitionsActivity.PARAM_TRANSITION_ID, transition.getId());
        intent.putExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, transition.getType());
        intent.putExtra(TransitionsActivity.PARAM_MINIMUM_DURATION, MINIMUM_TRANSITION_DURATION);
        intent.putExtra(TransitionsActivity.PARAM_DEFAULT_DURATION, transition.getAppDuration());
        intent.putExtra(TransitionsActivity.PARAM_MAXIMUM_DURATION,
                getMaxTransitionDuration(afterMediaItem));
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_EDIT_TRANSITION);
    }

    /**
     * Finds the media item view with the specified id.
     *
     * @param mediaItemId The media item id
     * @return The found media item view; null if not found
     */
    private View getMediaItemView(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem)tag;
                if (mediaItemId.equals(mediaItem.getId())) {
                    return childView;
                }
            }
        }

        return null;
    }

    /**
     * Finds the media item view index with the specified id.
     *
     * @param mediaItemId The media item id
     * @return The media item view index; -1 if not found
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
     * Finds the transition view with the specified id.
     *
     * @param transitionId The transition id
     *
     * @return The found transition view; null if not found
     */
    private View getTransitionView(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    return childView;
                }
            }
        }

        return null;
    }

    /**
     * Removes a transition.
     *
     * @param transitionId The id of the transition to be removed
     */
    public void removeTransitionView(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    // Remove the view
                    removeViewAt(i);

                    // Adjust the size of all the views
                    requestLayout();

                    // If this transition was removed by the user invalidate the menu item
                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }
                    return;
                }
            }
        }
    }

    /**
     * Removes all media item and transition views but leave the beginning, end views, and handles.
     */
    private void removeAllMediaItemAndTransitionViews() {
        int index = 0;
        while (index < getChildCount()) {
            final Object tag = getChildAt(index).getTag();
            // Media item view or transition view is associated with a media item or transition
            // attached as a tag. We can thus check the nullity of the tag to determine if it is
            // media item view or transition view.
            if (tag != null) {
                removeViewAt(index);
            } else {
                index++;
            }
        }
        requestLayout();

        // We cannot add clips by tapping the beginning view.
        mLeftAddClipButton.setVisibility(View.GONE);
    }

    /**
     * Computes the transition duration.
     *
     * @param afterMediaItem The position of the transition
     *
     * @return The transition duration
     */
    private long getTransitionDuration(MovieMediaItem afterMediaItem) {
        if (afterMediaItem == null) {
            final MovieMediaItem firstMediaItem = mProject.getFirstMediaItem();
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2,
                    firstMediaItem.getAppTimelineDuration() / 4);
        } else if (mProject.isLastMediaItem(afterMediaItem.getId())) {
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2,
                    afterMediaItem.getAppTimelineDuration() / 4);
        } else {
            final MovieMediaItem beforeMediaItem =
                mProject.getNextMediaItem(afterMediaItem.getId());
            final long minDurationMs = Math.min(afterMediaItem.getAppTimelineDuration(),
                    beforeMediaItem.getAppTimelineDuration());
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2, minDurationMs / 4);
        }
    }

    /**
     * Computes the maximum transition duration.
     *
     * @param afterMediaItem The position of the transition
     *
     * @return The transition duration
     */
    private long getMaxTransitionDuration(MovieMediaItem afterMediaItem) {
        if (afterMediaItem == null) {
            final MovieMediaItem firstMediaItem = mProject.getFirstMediaItem();
            return Math.min(MAXIMUM_TRANSITION_DURATION,
                    firstMediaItem.getAppTimelineDuration() / 4);
        } else if (mProject.isLastMediaItem(afterMediaItem.getId())) {
            return Math.min(MAXIMUM_TRANSITION_DURATION,
                    afterMediaItem.getAppTimelineDuration() / 4);
        } else {
            final MovieMediaItem beforeMediaItem =
                mProject.getNextMediaItem(afterMediaItem.getId());
            final long minDurationMs = Math.min(afterMediaItem.getAppTimelineDuration(),
                    beforeMediaItem.getAppTimelineDuration());
            return Math.min(MAXIMUM_TRANSITION_DURATION, minDurationMs / 4);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        // We only care about when this layout is unselected, which means all children are
        // unselected. Clients should never call setSelected(true) since it is no-op here.
        if (selected == false) {
            closeActionBars();
            clearAndHideTrimHandles();
            mSelectedView = null;
            showAddMediaItemButtons(true);
        }
        dispatchSetSelected(false);
    }

    /**
     * Returns true if some view item on the timeline is selected.
     */
    public boolean hasItemSelected() {
        return (mSelectedView != null);
    }

    /**
     * Returns true if some media item is being trimmed by user.
     */
    public boolean isTrimming() {
        return mIsTrimming;
    }

    /**
     * Closes all contextual action bars.
     */
    private void closeActionBars() {
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.finish();
            mMediaItemActionMode = null;
        }

        if (mTransitionActionMode != null) {
            mTransitionActionMode.finish();
            mTransitionActionMode = null;
        }
    }

    /**
     * Hides left and right trim handles and unregisters their listeners.
     */
    private void clearAndHideTrimHandles() {
        mLeftHandle.setVisibility(View.GONE);
        mLeftHandle.setListener(null);
        mRightHandle.setVisibility(View.GONE);
        mRightHandle.setListener(null);
    }

    /**
     * Unselects the specified view. No-op if the specified view is already unselected.
     */
    private void unSelect(View view) {
        // Return early if the specified view is already unselected or null.
        if (view == null || !view.isSelected()) {
            return;
        }

        mSelectedView = null;
        view.setSelected(false);
        // Need to redraw other children as well because they had dimmed themselves.
        invalidateAllChildren();
        clearAndHideTrimHandles();
    }

    /**
     * Selects the specified view and un-selects all others.
     * No-op if the specified view is already selected.
     * The selected view will stand out and all other views on the
     * timeline are dimmed.
     */
    private void select(View selectedView) {
        // Return early if the view is already selected.
        if (selectedView.isSelected()) {
            return;
        }

        unselectAllTimelineViews();
        mSelectedView = selectedView;
        mSelectedView.setSelected(true);
        showAddMediaItemButtons(false);

        final Object tag = mSelectedView.getTag();
        if (tag instanceof MovieMediaItem) {
            final MediaItemView mediaItemView = (MediaItemView) mSelectedView;
            if (mediaItemView.isGeneratingEffect()) {
                mLeftHandle.setEnabled(false);
                mRightHandle.setEnabled(false);
            } else {
                mLeftHandle.setEnabled(true);
                mRightHandle.setEnabled(true);
            }

            final MovieMediaItem mi = (MovieMediaItem) tag;
            if (mMediaItemActionMode == null) {
                startActionMode(new MediaItemActionModeCallback(mi));
            }

            final boolean videoClip = mi.isVideoClip();
            if (videoClip) {
                mLeftHandle.setVisibility(View.VISIBLE);
                mLeftHandle.bringToFront();
                mLeftHandle.setLimitReached(mi.getAppBoundaryBeginTime() <= 0,
                        mi.getAppTimelineDuration() <=
                            MediaItemUtils.getMinimumVideoItemDuration());
                mLeftHandle.setListener(new HandleView.MoveListener() {
                    private View mTrimmedView;
                    private MovieMediaItem mMediaItem;
                    private long mTransitionsDurationMs;
                    private long mOriginalBeginMs, mOriginalEndMs;
                    private long mMinimumDurationMs;
                    private int mOriginalWidth;
                    private int mMovePosition;

                    @Override
                    public void onMoveBegin(HandleView view) {
                        mMediaItem = (MovieMediaItem)mediaItemView.getTag();
                        mTransitionsDurationMs = (mMediaItem.getBeginTransition() != null ?
                                mMediaItem.getBeginTransition().getAppDuration() : 0)
                                + (mMediaItem.getEndTransition() != null ?
                                        mMediaItem.getEndTransition().getAppDuration() : 0);
                        mOriginalBeginMs = mMediaItem.getAppBoundaryBeginTime();
                        mOriginalEndMs = mMediaItem.getAppBoundaryEndTime();
                        mOriginalWidth = mediaItemView.getWidth();
                        mMinimumDurationMs = MediaItemUtils.getMinimumVideoItemDuration();
                        setIsTrimming(true);
                        invalidateAllChildren();
                        mTrimmedView = mediaItemView;

                        mListener.onTrimMediaItemBegin(mMediaItem);
                        if (videoClip) { // Video clip
                            mListener.onTrimMediaItem(mMediaItem,
                                    mMediaItem.getAppBoundaryBeginTime());
                        } else {
                            mListener.onTrimMediaItem(mMediaItem, 0);
                        }
                        // Move the playhead
                        mScrollView.setTag(R.id.playhead_offset, view.getRight());
                        mScrollView.invalidate();
                    }

                    @Override
                    public boolean onMove(HandleView view, int left, int delta) {
                        if (mMoveLayoutPending) {
                            return false;
                        }

                        int position = left + delta;
                        mMovePosition = position;
                        // Compute what will become the width of the view
                        int newWidth = mTrimmedView.getRight() - position;
                        if (newWidth == mTrimmedView.getWidth()) {
                            return false;
                        }

                        // Compute the new duration
                        long newDurationMs = mTransitionsDurationMs +
                                (newWidth * mProject.computeDuration()) /
                                (getWidth() - (2 * mHalfParentWidth));
                        if (Math.abs(mMediaItem.getAppTimelineDuration() - newDurationMs) <
                                TIME_TOLERANCE) {
                            return false;
                        } else if (newDurationMs < Math.max(2 * mTransitionsDurationMs,
                                mMinimumDurationMs)) {
                            newDurationMs = Math.max(2 * mTransitionsDurationMs,
                                    mMinimumDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = mTrimmedView.getRight() - newWidth;
                        } else if (mMediaItem.getAppBoundaryEndTime() - newDurationMs < 0) {
                            newDurationMs = mMediaItem.getAppBoundaryEndTime();
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = mTrimmedView.getRight() - newWidth;
                        }

                        // Return early if the new duration has not changed. We don't have to
                        // adjust the layout.
                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(
                                mMediaItem.getAppBoundaryEndTime() - newDurationMs,
                                mMediaItem.getAppBoundaryEndTime());

                        mLeftHandle.setLimitReached(mMediaItem.getAppBoundaryBeginTime() <= 0,
                                mMediaItem.getAppTimelineDuration() <= mMinimumDurationMs);
                        mMoveLayoutPending = true;
                        mScrollView.setTag(R.id.left_view_width,
                                mHalfParentWidth - (newWidth - mOriginalWidth));
                        mScrollView.setTag(R.id.playhead_offset, position);
                        requestLayout();

                        mListener.onTrimMediaItem(mMediaItem,
                                mMediaItem.getAppBoundaryBeginTime());
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
                                            moveDone();
                                        }
                                    } else {
                                        moveDone();
                                    }
                                }
                            });
                        } else {
                            moveDone();
                        }
                    }

                    /**
                     * The move is complete
                     */
                    private void moveDone() {
                        mScrollView.setTag(R.id.left_view_width, mHalfParentWidth);
                        mScrollView.setTag(R.id.playhead_offset, -1);

                        mListener.onTrimMediaItemEnd(mMediaItem,
                                mMediaItem.getAppBoundaryBeginTime());
                        mListener.onRequestMovePlayhead(getBeginTime(mMediaItem), false);

                        if (Math.abs(mOriginalBeginMs - mMediaItem.getAppBoundaryBeginTime()) >
                                    TIME_TOLERANCE
                                || Math.abs(mOriginalEndMs - mMediaItem.getAppBoundaryEndTime()) >
                                    TIME_TOLERANCE) {

                            if (videoClip) { // Video clip
                                ApiService.setMediaItemBoundaries(getContext(), mProject.getPath(),
                                        mMediaItem.getId(), mMediaItem.getAppBoundaryBeginTime(),
                                        mMediaItem.getAppBoundaryEndTime());
                            } else { // Image
                                ApiService.setMediaItemDuration(getContext(), mProject.getPath(),
                                        mMediaItem.getId(), mMediaItem.getAppTimelineDuration());
                            }

                            final long durationMs = mMediaItem.getAppTimelineDuration();
                            mRightHandle.setLimitReached(durationMs <=
                                MediaItemUtils.getMinimumMediaItemDuration(mMediaItem),
                                    videoClip ? (mMediaItem.getAppBoundaryEndTime() >=
                                        mMediaItem.getDuration()) : durationMs >=
                                            MAXIMUM_IMAGE_DURATION);

                            mLeftHandle.setEnabled(false);
                            mRightHandle.setEnabled(false);
                        }
                        setIsTrimming(false);
                        mScrollView.invalidate();
                        invalidateAllChildren();
                    }
                });
            }

            mRightHandle.setVisibility(View.VISIBLE);
            mRightHandle.bringToFront();
            final long durationMs = mi.getAppTimelineDuration();
            mRightHandle.setLimitReached(
                    durationMs <= MediaItemUtils.getMinimumMediaItemDuration(mi),
                    videoClip ? (mi.getAppBoundaryEndTime() >= mi.getDuration()) :
                        durationMs >= MAXIMUM_IMAGE_DURATION);
            mRightHandle.setListener(new HandleView.MoveListener() {
                private View mTrimmedView;
                private MovieMediaItem mMediaItem;
                private long mTransitionsDurationMs;
                private long mOriginalBeginMs, mOriginalEndMs;
                private long mMinimumItemDurationMs;
                private int mMovePosition;

                @Override
                public void onMoveBegin(HandleView view) {
                    mMediaItem = (MovieMediaItem)mediaItemView.getTag();
                    mTransitionsDurationMs = (mMediaItem.getBeginTransition() != null ?
                            mMediaItem.getBeginTransition().getAppDuration() : 0)
                            + (mMediaItem.getEndTransition() != null ?
                                    mMediaItem.getEndTransition().getAppDuration() : 0);
                    mOriginalBeginMs = mMediaItem.getAppBoundaryBeginTime();
                    mOriginalEndMs = mMediaItem.getAppBoundaryEndTime();
                    mMinimumItemDurationMs = MediaItemUtils.getMinimumMediaItemDuration(mMediaItem);
                    setIsTrimming(true);
                    invalidateAllChildren();
                    mTrimmedView = mediaItemView;

                    mListener.onTrimMediaItemBegin(mMediaItem);
                    if (videoClip) {  // Video clip
                        mListener.onTrimMediaItem(mMediaItem, mMediaItem.getAppBoundaryEndTime());
                    } else {
                        mListener.onTrimMediaItem(mMediaItem, 0);
                    }

                    // Move the playhead
                    mScrollView.setTag(R.id.playhead_offset, view.getLeft());
                    mScrollView.invalidate();
                }

                @Override
                public boolean onMove(HandleView view, int left, int delta) {
                    if (mMoveLayoutPending) {
                        return false;
                    }

                    int position = left + delta;
                    mMovePosition = position;

                    long newDurationMs;
                    // Compute what will become the width of the view
                    int newWidth = position - mTrimmedView.getLeft();
                    if (newWidth == mTrimmedView.getWidth()) {
                        return false;
                    }

                    // Compute the new duration
                    newDurationMs = mTransitionsDurationMs +
                            (newWidth * mProject.computeDuration()) /
                            (getWidth() - (2 * mHalfParentWidth));
                    if (Math.abs(mMediaItem.getAppTimelineDuration() - newDurationMs) <
                            TIME_TOLERANCE) {
                        return false;
                    }

                    if (videoClip) { // Video clip
                        if (newDurationMs < Math.max(2 * mTransitionsDurationMs,
                                mMinimumItemDurationMs)) {
                            newDurationMs = Math.max(2 * mTransitionsDurationMs,
                                    mMinimumItemDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        } else if (mMediaItem.getAppBoundaryBeginTime() + newDurationMs >
                                mMediaItem.getDuration()) {
                            newDurationMs = mMediaItem.getDuration() -
                                mMediaItem.getAppBoundaryBeginTime();
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        }

                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(mMediaItem.getAppBoundaryBeginTime(),
                                mMediaItem.getAppBoundaryBeginTime() + newDurationMs);
                        mListener.onTrimMediaItem(mMediaItem, mMediaItem.getAppBoundaryEndTime());
                    } else { // Image
                        if (newDurationMs < Math.max(mMinimumItemDurationMs,
                                2 * mTransitionsDurationMs)) {
                            newDurationMs = Math.max(mMinimumItemDurationMs,
                                    2 * mTransitionsDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        } else if (newDurationMs > MAXIMUM_IMAGE_DURATION) {
                            newDurationMs = MAXIMUM_IMAGE_DURATION;
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        }

                        // Check if the duration would change
                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(0, newDurationMs);
                        mListener.onTrimMediaItem(mMediaItem, 0);
                    }

                    mScrollView.setTag(R.id.playhead_offset, position);
                    mRightHandle.setLimitReached(
                            newDurationMs <= mMinimumItemDurationMs,
                            videoClip ? (mMediaItem.getAppBoundaryEndTime() >=
                                mMediaItem.getDuration()) : newDurationMs >=
                                    MAXIMUM_IMAGE_DURATION);

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
                                        moveDone();
                                    }
                                } else {
                                    moveDone();
                                }
                            }
                        });
                    } else {
                        moveDone();
                    }
                }

                /**
                 * The move is complete
                 */
                private void moveDone() {
                    mScrollView.setTag(R.id.playhead_offset, -1);

                    mListener.onTrimMediaItemEnd(mMediaItem,
                            mMediaItem.getAppBoundaryEndTime());
                    mListener.onRequestMovePlayhead(getEndTime(mMediaItem), false);

                    if (Math.abs(mOriginalBeginMs - mMediaItem.getAppBoundaryBeginTime()) >
                            TIME_TOLERANCE ||
                            Math.abs(mOriginalEndMs - mMediaItem.getAppBoundaryEndTime()) >
                            TIME_TOLERANCE) {
                        if (videoClip) { // Video clip
                            ApiService.setMediaItemBoundaries(getContext(), mProject.getPath(),
                                    mMediaItem.getId(), mMediaItem.getAppBoundaryBeginTime(),
                                    mMediaItem.getAppBoundaryEndTime());
                        } else { // Image
                            ApiService.setMediaItemDuration(getContext(), mProject.getPath(),
                                    mMediaItem.getId(), mMediaItem.getAppTimelineDuration());
                        }

                        if (videoClip) {
                            mLeftHandle.setLimitReached(mMediaItem.getAppBoundaryBeginTime() <= 0,
                                    mMediaItem.getAppTimelineDuration() <= mMinimumItemDurationMs);
                        }

                        mLeftHandle.setEnabled(false);
                        mRightHandle.setEnabled(false);
                    }
                    setIsTrimming(false);
                    mScrollView.invalidate();
                    invalidateAllChildren();
                }
            });
        } else if (tag instanceof MovieTransition) {
            if (mTransitionActionMode == null) {
                startActionMode(new TransitionActionModeCallback((MovieTransition) tag));
            }
        }
    }

    /**
     * Indicates if any media item is being trimmed or no.
     */
    private void setIsTrimming(boolean isTrimming) {
        mIsTrimming = isTrimming;
    }

    /**
     * Sets the playback state for all media item views.
     *
     * @param playback indicates if the playback is ongoing
     */
    private void setPlaybackState(boolean playback) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null) {
                if (tag instanceof MovieMediaItem) {
                    ((MediaItemView) childView).setPlaybackMode(playback);
                } else if (tag instanceof MovieTransition) {
                    ((TransitionView) childView).setPlaybackMode(playback);
                }
            }
        }
    }

    /**
     * Un-selects all views in the timeline relative layout, including playhead view and
     * the ones in audio track layout and transition layout.
     */
    private void unselectAllTimelineViews() {
        ((RelativeLayout) getParent()).setSelected(false);
        invalidateAllChildren();
    }

    /**
     * Invalidates all children. Note that invalidating the parent does not invalidate its children.
     */
    private void invalidateAllChildren() {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            childView.invalidate();
        }
    }

    /**
     * Shows or hides "add media buttons" on both sides of the timeline.
     *
     * @param show {@code true} to show the "Add media item" buttons, {@code false} to hide them
     */
    private void showAddMediaItemButtons(boolean show) {
        if (show) {
            // Shows left add button iff there is at least one media item on the timeline.
            if (mProject.getMediaItemCount() > 0) {
                mLeftAddClipButton.setVisibility(View.VISIBLE);
            }
            mRightAddClipButton.setVisibility(View.VISIBLE);
        } else {
            mLeftAddClipButton.setVisibility(View.GONE);
            mRightAddClipButton.setVisibility(View.GONE);
        }
    }
}
