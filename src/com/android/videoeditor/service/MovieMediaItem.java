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

import java.util.List;

import android.media.videoeditor.Effect;
import android.media.videoeditor.MediaImageItem;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.MediaVideoItem;
import android.media.videoeditor.Overlay;
import android.media.videoeditor.WaveformData;


/**
 * This class represents a media item
 */
public class MovieMediaItem {
    // The unique id of the media item
    private final String mUniqueId;

    // The transition type
    private final Class<?> mType;

    // The name of the file associated with the media item
    private final String mFilename;

    // The width and height
    private final int mWidth;
    private final int mHeight;

    // The aspect ratio
    private final int mAspectRatio;

    // The duration of the entire media item (ignore trim boundaries)
    private final long mDurationMs;

    // Trimming boundaries
    private final long mBeginBoundaryTimeMs;
    private final long mEndBoundaryTimeMs;

    // The rendering mode
    private int mRenderingMode;

    // The overlay applied to the media item
    private MovieOverlay mOverlay;

    // The effect applied to the media item
    private MovieEffect mEffect;

    // Begin and end transitions
    private MovieTransition mBeginTransition;
    private MovieTransition mEndTransition;

    // The audio waveform data
    private WaveformData mWaveformData;

    // Sound control
    private int mVolumePercent;
    private boolean mMuted;

    // Application values
    private long mAppBeginBoundaryTimeMs, mAppEndBoundaryTimeMs;
    private int mAppRenderingMode;
    private int mAppVolumePercent;
    private boolean mAppMuted;

    /**
     * Constructor
     *
     * @param mediaItem The media item
     */
    MovieMediaItem(MediaItem mediaItem) {
        this(mediaItem, mediaItem.getBeginTransition() != null ? new MovieTransition(
                mediaItem.getBeginTransition()) : null);
    }

    /**
     * Constructor
     *
     * @param mediaItem The media item
     * @param beginTransition The transition of the previous media item
     */
    MovieMediaItem(MediaItem mediaItem, MovieTransition beginTransition) {
        mType = mediaItem.getClass();
        mUniqueId = mediaItem.getId();
        mFilename = mediaItem.getFilename();
        mAppRenderingMode = mRenderingMode = mediaItem.getRenderingMode();
        mAspectRatio = mediaItem.getAspectRatio();
        mWidth = mediaItem.getWidth();
        mHeight = mediaItem.getHeight();

        mDurationMs = mediaItem.getDuration();
        if (mediaItem instanceof MediaVideoItem) {
            final MediaVideoItem videoMediaItem = ((MediaVideoItem)mediaItem);
            mAppBeginBoundaryTimeMs = mBeginBoundaryTimeMs = videoMediaItem.getBoundaryBeginTime();
            mAppEndBoundaryTimeMs = mEndBoundaryTimeMs = videoMediaItem.getBoundaryEndTime();
            try {
                mWaveformData = videoMediaItem.getWaveformData();
            } catch (Exception ex) {
                mWaveformData = null;
            }
            mAppVolumePercent = mVolumePercent = videoMediaItem.getVolume();
            mAppMuted = mMuted = videoMediaItem.isMuted();
        } else {
            mAppBeginBoundaryTimeMs = mBeginBoundaryTimeMs = 0;
            mAppEndBoundaryTimeMs = mEndBoundaryTimeMs = mediaItem.getTimelineDuration();
            mWaveformData = null;
            mAppVolumePercent = mVolumePercent = 0;
            mAppMuted = mMuted = false;
        }

        final List<Overlay> overlays = mediaItem.getAllOverlays();
        for (Overlay overlay : overlays) {
            addOverlay(new MovieOverlay(overlay));
        }

        final List<Effect> effects = mediaItem.getAllEffects();
        for (Effect effect : effects) {
            addEffect(new MovieEffect(effect));
        }

        setBeginTransition(beginTransition);

        if (mediaItem.getEndTransition() != null) {
            setEndTransition(new MovieTransition(mediaItem.getEndTransition()));
        }
    }

    /**
     * @return true if this is an image media item
     */
    public boolean isImage() {
        return MediaImageItem.class.equals(mType);
    }

    /**
     * @return true if this is an image video clip item
     */
    public boolean isVideoClip() {
        return MediaVideoItem.class.equals(mType);
    }

    /**
     * @return The id of the media item
     */
    public String getId() {
        return mUniqueId;
    }

    /**
     * @return The media source file name
     */
    public String getFilename() {
        return mFilename;
    }

    /**
     * If aspect ratio of the MediaItem is different from the aspect ratio of
     * the editor then this API controls the rendering mode.
     *
     * @param renderingMode rendering mode. It is one of:
     *            {@link #RENDERING_MODE_BLACK_BORDER},
     *            {@link #RENDERING_MODE_STRETCH}
     */
    void setRenderingMode(int renderingMode) {
        mRenderingMode = renderingMode;
    }

    /**
     * @return The rendering mode
     */
    int getRenderingMode() {
        return mRenderingMode;
    }

    /**
     * If aspect ratio of the MediaItem is different from the aspect ratio of
     * the editor then this API controls the rendering mode.
     *
     * @param renderingMode rendering mode.
     */
    public void setAppRenderingMode(int renderingMode) {
        mAppRenderingMode = renderingMode;
    }

    /**
     * @return The rendering mode
     */
    public int getAppRenderingMode() {
        return mAppRenderingMode;
    }

    /**
     * Set the volume of this audio track as percentage of the volume in the
     * original audio source file.
     *
     * @param volumePercent Percentage of the volume to apply. If it is set to
     *            0, then volume becomes mute. It it is set to 100, then volume
     *            is same as original volume. It it is set to 200, then volume
     *            is doubled (provided that volume amplification is supported)
     *
     * @throws UnsupportedOperationException if volume amplification is
     *             requested and is not supported.
     */
    void setVolume(int volumePercent) {
        mVolumePercent = volumePercent;
    }

    /**
     * Get the volume of the audio track as percentage of the volume in the
     * original audio source file.
     *
     * @return The volume in percentage
     */
    int getVolume() {
        return mVolumePercent;
    }

    /**
     * Set the volume of this audio track as percentage of the volume in the
     * original audio source file.
     *
     * @param volumePercent Percentage of the volume to apply. If it is set to
     *            0, then volume becomes mute. It it is set to 100, then volume
     *            is same as original volume. It it is set to 200, then volume
     *            is doubled (provided that volume amplification is supported)
     *
     * @throws UnsupportedOperationException if volume amplification is
     *             requested and is not supported.
     */
    public void setAppVolume(int volumePercent) {
        mAppVolumePercent = volumePercent;
    }

    /**
     * Get the volume of the audio track as percentage of the volume in the
     * original audio source file.
     *
     * @return The volume in percentage
     */
    public int getAppVolume() {
        return mAppVolumePercent;
    }

    /**
     * @param muted true to mute the media item
     */
    void setMute(boolean muted) {
        mMuted = muted;
    }

    /**
     * @return true if the media item is muted
     */
    boolean isMuted() {
        return mMuted;
    }

    /**
     * @param muted true to mute the media item
     */
    public void setAppMute(boolean muted) {
        mAppMuted = muted;
    }

    /**
     * @return true if the media item is muted
     */
    public boolean isAppMuted() {
        return mAppMuted;
    }

    /**
     * @return The boundary begin time
     */
    long getBoundaryBeginTime() {
        return mBeginBoundaryTimeMs;
    }

    /**
     * @return The boundary end time
     */
    long getBoundaryEndTime() {
        return mEndBoundaryTimeMs;
    }

    /**
     * Sets the start and end marks for trimming a video media item.
     *
     * @param beginMs Start time in milliseconds. Set to 0 to extract from the
     *            beginning
     * @param endMs End time in milliseconds.
     */
    public void setAppExtractBoundaries(long beginMs, long endMs) {
        mAppBeginBoundaryTimeMs = beginMs;
        mAppEndBoundaryTimeMs = endMs;
    }

    /**
     * @return The boundary begin time
     */
    public long getAppBoundaryBeginTime() {
        return mAppBeginBoundaryTimeMs;
    }

    /**
     * @return The boundary end time
     */
    public long getAppBoundaryEndTime() {
        return mAppEndBoundaryTimeMs;
    }

    /**
     * @return The timeline duration. This is the actual duration in the
     *         timeline (trimmed duration)
     */
    public long getAppTimelineDuration() {
        return mAppEndBoundaryTimeMs - mAppBeginBoundaryTimeMs;
    }

    /**
     * @return The duration of the entire media item (ignore trim)
     */
    public long getDuration() {
        return mDurationMs;
    }

    /**
     * @return Get the width of the media item
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * @return Get the height of the media item
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Get aspect ratio of the source media item.
     *
     * @return the aspect ratio as described in MediaProperties.
     *         MediaProperties.ASPECT_RATIO_UNDEFINED if aspect ratio is not
     *         supported as in MediaProperties
     */
    public int getAspectRatio() {
        return mAspectRatio;
    }

    /**
     * @param beginTransition Begin transition
     */
    void setBeginTransition(MovieTransition beginTransition) {
        mBeginTransition = beginTransition;
    }

    /**
     * @return The begin transition
     */
    public MovieTransition getBeginTransition() {
        return mBeginTransition;
    }

    /**
     * @param endTransition end transition
     */
    void setEndTransition(MovieTransition endTransition) {
        mEndTransition = endTransition;
    }

    /**
     * @return The end transition
     */
    public MovieTransition getEndTransition() {
        return mEndTransition;
    }

    /**
     * @return The overlay
     */
    public MovieOverlay getOverlay() {
        return mOverlay;
    }

    /**
     * Only one overlay is supported at this time
     *
     * @param overlay The overlay
     */
    void addOverlay(MovieOverlay overlay) {
        if (mOverlay != null) {
            throw new IllegalStateException("Overlay already set for media item: " + mUniqueId);
        }
        mOverlay = overlay;
    }

    /**
     * @param overlayId The overlay id
     */
    void removeOverlay(String overlayId) {
        if (mOverlay != null) {
            if (!mOverlay.getId().equals(overlayId)) {
                throw new IllegalStateException("Overlay does not match: " + mOverlay.getId() + " "
                        + overlayId);
            }
            mOverlay = null;
        }
    }

    /**
     * @return The effect
     */
    public MovieEffect getEffect() {
        return mEffect;
    }

    /**
     * Only one effect is supported at this time
     *
     * @param effect The effect
     */
    void addEffect(MovieEffect effect) {
        if (mEffect != null) {
            throw new IllegalStateException("Effect already set for media item: " + mUniqueId);
        }
        mEffect = effect;
    }

    /**
     * @param effectId The effect id
     */
    void removeEffect(String effectId) {
        if (mEffect != null) {
            if (!mEffect.getId().equals(effectId)) {
                throw new IllegalStateException("Effect does not match: " + mEffect.getId() + " "
                        + effectId);
            }

            mEffect = null;
        }
    }

    /**
     * @return waveform data
     */
    public WaveformData getWaveformData() {
        return mWaveformData;
    }

    /**
     * @param waveformData The waveform data
     */
    void setWaveformData(WaveformData waveformData) {
        mWaveformData = waveformData;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MovieMediaItem)) {
            return false;
        }
        return mUniqueId.equals(((MovieMediaItem)object).mUniqueId);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }
}
