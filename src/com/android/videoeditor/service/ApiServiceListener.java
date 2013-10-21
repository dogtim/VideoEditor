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

package com.android.videoeditor.service;

import android.graphics.Bitmap;
import android.media.videoeditor.AudioTrack;
import android.media.videoeditor.MediaItem;
import android.net.Uri;
import android.os.Bundle;

import java.util.List;

/**
 * Interface for API service listener. This interface declares various callbacks that
 * clients might be interested in to handle responses or state change from various API
 * service actions. Clients should extend this interface and override interested callbacks.
 * See {@link ProjectsCarouselView} for example usage.
 */
public class ApiServiceListener {
    /**
     * The list of projects was loaded
     *
     * @param projects The array of projects
     * @param exception The exception
     */
    public void onProjectsLoaded(List<VideoEditorProject> projects, Exception exception) {}

    /**
     * The project edit state
     *
     * @param projectPath The project path
     * @param projectEdited true if the project is edited
     */
    public void onProjectEditState(String projectPath, boolean projectEdited) {}

    /**
     * A new project was created
     *
     * @param projectPath The project path
     * @param project The VideoEditor project
     * @param mediaItems The list of media items
     * @param audioTracks The list of audio tracks
     * @param exception The exception that occurred
     */
    public void onVideoEditorCreated(String projectPath, VideoEditorProject project,
            List<MediaItem> mediaItems, List<AudioTrack> audioTracks, Exception exception) {}

    /**
     * The project was loaded
     *
     * @param projectPath The project path
     * @param project The VideoEditor project
     * @param mediaItems The list of media items
     * @param audioTracks The list of audio tracks
     * @param exception The exception that occurred
     */
    public void onVideoEditorLoaded(String projectPath, VideoEditorProject project,
            List<MediaItem> mediaItems, List<AudioTrack> audioTracks, Exception exception) {}

    /**
     * The aspect ratio was set
     *
     * @param projectPath The project path
     * @param aspectRatio The aspect ratio
     * @param exception The exception that occurred
     */
    public void onVideoEditorAspectRatioSet(String projectPath, int aspectRatio, Exception exception) {}

    /**
     * The specified theme was applied
     *
     * @param projectPath The project path
     * @param theme The theme
     * @param exception The exception that occurred
     */
    public void onVideoEditorThemeApplied(String projectPath, String theme, Exception exception) {}

    /**
     * Generate preview progress status
     *
     * @param projectPath The project path
     * @param className The class name
     * @param itemId The storyboard item id
     * @param action The action taken on the item
     * @param progress The export progress (0, 100)
     */
    public void onVideoEditorGeneratePreviewProgress(String projectPath, String className,
            String itemId, int action, int progress) {}

    /**
     * Export progress status
     *
     * @param projectPath The project path
     * @param filename The name of the file to export
     * @param progress The export progress (0, 100)
     */
    public void onVideoEditorExportProgress(String projectPath, String filename, int progress) {}

    /**
     * Export completed callback
     *
     * @param projectPath The project path
     * @param filename The name of the file to export
     * @param exception null if no exception has occurred (export succeeded)
     * @param cancelled if the export is cancelled by the user
     */
    public void onVideoEditorExportComplete(String projectPath, String filename,
            Exception exception, boolean cancelled) {}

    /**
     * Export canceled callback
     *
     * @param projectPath The project path
     * @param filename The name of the file to export
     */
    public void onVideoEditorExportCanceled(String projectPath, String filename) {}

    /**
     * The VideoEditor state was saved
     *
     * @param projectPath The project path
     * @param exception The exception which occurred (if any)
     */
    public void onVideoEditorSaved(String projectPath, Exception exception) {}

    /**
     * The VideoEditor stated was released
     *
     * @param projectPath The project path
     * @param exception The exception which occurred (if any)
     */
    public void onVideoEditorReleased(String projectPath, Exception exception) {}

    /**
     * The VideoEditor stated was deleted.
     *
     * @param projectPath The project path
     * @param exception The exception which occurred (if any)
     */
    public void onVideoEditorDeleted(String projectPath, Exception exception) {}

    /**
     * A new media item was added
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param mediaItem The newly added media item (null if an error occurred)
     * @param afterMediaId The media item id preceding the media item
     * @param mediaItemClass The media item class
     * @param aspectRatio The aspectRatio
     * @param exception The exception which occurred
     */
    public void onMediaItemAdded(String projectPath, String mediaItemId,
            MovieMediaItem mediaItem, String afterMediaId, Class<?> mediaItemClass,
            Integer aspectRatio, Exception exception) {}

    /**
     * Media load complete
     *
     * @param projectPath The project path
     * @param mediaUri The media URI
     * @param mimeType The mime type
     * @param filename The filename of the downloaded media item
     * @param exception The exception which occurred
     */
    public void onMediaLoaded(String projectPath, Uri mediaUri, String mimeType,
            String filename, Exception exception) {}

    /**
     * A media item was moved
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item which moved
     * @param afterMediaItemId The id of the relative media item id
     * @param exception The exception which occurred
     */
    public void onMediaItemMoved(String projectPath, String mediaItemId,
            String afterMediaItemId, Exception exception) {}

    /**
     * A media item was removed
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item which was removed
     * @param transition The transition inserted at the removal position
     *          if a theme is in use.
     * @param exception The exception which occurred
     */
    public void onMediaItemRemoved(String projectPath, String mediaItemId,
            MovieTransition transition, Exception exception) {}

    /**
     * A media item rendering mode was set
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param renderingMode The rendering mode
     * @param exception The exception which occurred
     */
    public void onMediaItemRenderingModeSet(String projectPath, String mediaItemId,
            int renderingMode, Exception exception) {}

    /**
     * A media item duration was set
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param durationMs The duration of the image media item
     * @param exception The exception which occurred
     */
    public void onMediaItemDurationSet(String projectPath, String mediaItemId,
            long durationMs, Exception exception) {}

    /**
     * A media item boundaries was set
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param beginBoundaryMs The begin boundary
     * @param endBoundaryMs The end boundary
     * @param exception The exception which occurred
     */
    public void onMediaItemBoundariesSet(String projectPath, String mediaItemId,
            long beginBoundaryMs, long endBoundaryMs, Exception exception) {}

    /**
     * A media item thumbnail was extracted
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param thumbnail The bitmap thumbnail
     * @param index The index of the thumbnail
     * @param token The token given in the original request
     * @param exception The exception which occurred
     *
     * @return true if the bitmap is used
     */
    public boolean onMediaItemThumbnail(String projectPath, String mediaItemId,
            Bitmap thumbnail, int index, int token, Exception exception) {
        return false;
    }

    /**
     * Extract media item audio waveform progress callback
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the media item
     * @param progress The progress (0, 100)
     */
    public void onMediaItemExtractAudioWaveformProgress(String projectPath,
            String mediaItemId, int progress) {}

    /**
     * The audio waveform of the specified media item completed
     *
     * @param projectPath The project path
     * @param mediaItemId The id of the MediaItem
     * @param exception The exception which occurred
     */
    public void onMediaItemExtractAudioWaveformComplete(String projectPath,
            String mediaItemId, Exception exception) {}

    /**
     * A new transition was inserted
     *
     * @param projectPath The project path
     * @param transition The newly added transition
     * @param afterMediaId After the media id
     * @param exception The exception which occurred
     */
    public void onTransitionInserted(String projectPath, MovieTransition transition,
            String afterMediaId, Exception exception) {}

    /**
     * A transition was removed
     *
     * @param projectPath The project path
     * @param transitionId The id of the transition which was removed
     * @param exception The exception which occurred
     */
    public void onTransitionRemoved(String projectPath, String transitionId,
            Exception exception) {}

    /**
     * A transition duration was changed
     *
     * @param projectPath The project path
     * @param transitionId The id of the transition which was modified
     * @param durationMs The duration in milliseconds
     * @param exception The exception which occurred
     */
    public void onTransitionDurationSet(String projectPath, String transitionId,
            long durationMs, Exception exception) {}

    /**
     * Two transition thumbnails were extracted
     *
     * @param projectPath The project path
     * @param transitionId The id of the transition
     * @param thumbnails The thumbnails array
     * @param exception The exception which occurred
     *
     * @return true if the bitmap is used
     */
    public boolean onTransitionThumbnails(String projectPath, String transitionId,
            Bitmap[] thumbnails, Exception exception) {
        return false;
    }

    /**
     * A new overlay was added
     *
     * @param projectPath The project path
     * @param overlay The newly added overlay
     * @param mediaItemId The media item id
     * @param exception The exception which occurred
     */
    public void onOverlayAdded(String projectPath, MovieOverlay overlay,
            String mediaItemId, Exception exception) {}

    /**
     * A overlay was removed
     *
     * @param projectPath The project path
     * @param overlayId The id of the overlay
     * @param mediaItemId The media item id
     * @param exception The exception which occurred
     */
    public void onOverlayRemoved(String projectPath, String overlayId,
            String mediaItemId, Exception exception) {}

    /**
     * The overlay start time was set
     *
     * @param projectPath The project path
     * @param overlayId The id of the overlay
     * @param mediaItemId The media item id
     * @param startTimeMs The start time in milliseconds
     * @param exception The exception which occurred
     */
    public void onOverlayStartTimeSet(String projectPath, String overlayId,
            String mediaItemId, long startTimeMs, Exception exception) {}

    /**
     * The overlay duration was set
     *
     * @param projectPath The project path
     * @param overlayId The id of the overlay
     * @param mediaItemId The media item id
     * @param durationMs The duration in milliseconds
     * @param exception The exception which occurred
     */
    public void onOverlayDurationSet(String projectPath, String overlayId,
            String mediaItemId, long durationMs, Exception exception) {}

    /**
     * The overlay user attributes were set
     *
     * @param projectPath The project path
     * @param overlayId The id of the overlay
     * @param mediaItemId The media item id
     * @param userAttributes The user attributes
     * @param exception The exception which occurred
     */
    public void onOverlayUserAttributesSet(String projectPath, String overlayId,
            String mediaItemId, Bundle userAttributes, Exception exception) {}

    /**
     * A new effect was added
     *
     * @param projectPath The project path
     * @param effect The newly added effect
     * @param mediaItemId The media item id
     * @param exception The exception which occurred
     */
    public void onEffectAdded(String projectPath, MovieEffect effect,
            String mediaItemId, Exception exception) {}

    /**
     * An effect was removed
     *
     * @param projectPath The project path
     * @param effectId The id of the effect which was removed
     * @param mediaItemId The media item id
     * @param exception The exception which occurred
     */
    public void onEffectRemoved(String projectPath, String effectId,
            String mediaItemId, Exception exception) {}

    /**
     * A new audio track was added
     *
     * @param projectPath The project path
     * @param audioTrack The newly added audioTrack
     * @param exception The exception which occurred
     */
    public void onAudioTrackAdded(String projectPath, MovieAudioTrack audioTrack,
            Exception exception) {}

    /**
     * An audio track was removed
     *
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param exception The exception which occurred
     */
    public void onAudioTrackRemoved(String projectPath, String audioTrackId,
            Exception exception) {}

    /**
     * An audio track boundaries was set
     *
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param beginBoundaryMs The begin boundary
     * @param endBoundaryMs The end boundary
     * @param exception The exception which occurred
     */
    public void onAudioTrackBoundariesSet(String projectPath, String audioTrackId,
            long beginBoundaryMs, long endBoundaryMs, Exception exception) {}

    /**
     * Extract audio waveform progress callback
     *
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param progress The progress (0, 100)
     */
    public void onAudioTrackExtractAudioWaveformProgress(String projectPath,
            String audioTrackId, int progress) {}

    /**
     * The audio track audio waveform of the specified audio track completed
     *
     * @param projectPath The project path
     * @param audioTrackId The id of the audio track
     * @param exception The exception which occurred
     */
    public void onAudioTrackExtractAudioWaveformComplete(String projectPath,
            String audioTrackId, Exception exception) {}
}
