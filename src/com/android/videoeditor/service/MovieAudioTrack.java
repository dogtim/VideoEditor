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

import java.io.IOException;

import android.media.videoeditor.AudioTrack;
import android.media.videoeditor.MediaProperties;
import android.media.videoeditor.VideoEditor;
import android.media.videoeditor.WaveformData;

/**
 * This class represents an audio track in the user interface
 */
public class MovieAudioTrack {
    // Instance variables
    private final String mUniqueId;
    private final String mFilename;
    private final int mRawResourceId;
    private final long mDurationMs;
    private long mStartTimeMs;
    private long mTimelineDurationMs;
    private int mVolumePercent;
    private boolean mMuted;
    private long mBeginBoundaryTimeMs;
    private long mEndBoundaryTimeMs;
    private boolean mLoop;

    private final int mAudioChannels;
    private final int mAudioType;
    private final int mAudioBitrate;
    private final int mAudioSamplingFrequency;

    // Ducking variables
    private boolean mIsDuckingEnabled;

    // The audio waveform data
    private WaveformData mWaveformData;

    private long mAppStartTimeMs;
    private int mAppVolumePercent;
    private boolean mAppMuted;
    private boolean mAppIsDuckingEnabled;
    private boolean mAppLoop;

    /**
     * An object of this type cannot be instantiated by using the default
     * constructor
     */
    @SuppressWarnings("unused")
    private MovieAudioTrack() throws IOException {
        this((AudioTrack)null);
    }

    /**
     * Constructor
     *
     * @param audioTrack The audio track
     */
    MovieAudioTrack(AudioTrack audioTrack) {
        mUniqueId = audioTrack.getId();
        mFilename = audioTrack.getFilename();
        mRawResourceId = 0;
        mAppStartTimeMs = mStartTimeMs = audioTrack.getStartTime();
        mDurationMs = audioTrack.getDuration();
        mBeginBoundaryTimeMs = audioTrack.getBoundaryBeginTime();
        mEndBoundaryTimeMs = audioTrack.getBoundaryEndTime();

        mAudioChannels = audioTrack.getAudioChannels();
        mAudioType = audioTrack.getAudioType();
        mAudioBitrate = audioTrack.getAudioBitrate();
        mAudioSamplingFrequency = audioTrack.getAudioSamplingFrequency();

        mAppVolumePercent = mVolumePercent = audioTrack.getVolume();
        mAppMuted = mMuted = audioTrack.isMuted();
        mAppLoop = mLoop = audioTrack.isLooping();

        mAppIsDuckingEnabled = mIsDuckingEnabled = audioTrack.isDuckingEnabled();

        try {
            mWaveformData = audioTrack.getWaveformData();
        } catch (Exception ex) {
            mWaveformData = null;
        }

        mTimelineDurationMs = mEndBoundaryTimeMs - mBeginBoundaryTimeMs;
    }

    /**
     * Constructor
     *
     * @param resId The audio track raw resource id
     */
    MovieAudioTrack(int resId) {
        mUniqueId = null;
        mFilename = null;
        mRawResourceId = resId;
        mAppStartTimeMs = mStartTimeMs = 0;
        mDurationMs = VideoEditor.DURATION_OF_STORYBOARD;
        mBeginBoundaryTimeMs = mStartTimeMs;
        mEndBoundaryTimeMs = mDurationMs;

        mAudioChannels = 0;
        mAudioType = MediaProperties.ACODEC_AAC_LC;
        mAudioBitrate = 0;
        mAudioSamplingFrequency = 0;

        mAppVolumePercent = mVolumePercent = 100;
        mAppMuted = mMuted = false;
        mAppLoop = mLoop = true;

        mAppIsDuckingEnabled = mIsDuckingEnabled = true;

        mWaveformData = null;

        mTimelineDurationMs = mEndBoundaryTimeMs - mBeginBoundaryTimeMs;
    }

    /**
     * @return The id of the media item
     */
    public String getId() {
        return mUniqueId;
    }

    /**
     * @return The raw resource id
     */
    public int getRawResourceId() {
        return mRawResourceId;
    }

    /**
     * Get the filename source for this audio track.
     *
     * @return The filename as an absolute file name
     */
    public String getFilename() {
        return mFilename;
    }

    /**
     * @return The number of audio channels in the source of this audio track
     */
    public int getAudioChannels() {
        return mAudioChannels;
    }

    /**
     * @return The audio codec of the source of this audio track
     */
    public int getAudioType() {
        return mAudioType;
    }

    /**
     * @return The audio sample frequency of the audio track
     */
    public int getAudioSamplingFrequency() {
        return mAudioSamplingFrequency;
    }

    /**
     * @return The audio bitrate of the audio track
     */
    public int getAudioBitrate() {
        return mAudioBitrate;
    }

    /**
     * Set the volume of this audio track as percentage of the volume in the
     * original audio source file.
     *
     * @param volumePercent Percentage of the volume to apply. If it is set to
     *            0, then volume becomes mute. It it is set to 100, then volume
     *            is same as original volume. It it is set to 200, then volume
     *            is doubled (provided that volume amplification is supported)
     * @throws UnsupportedOperationException if volume amplification is requested
     *             and is not supported.
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
     * @throws UnsupportedOperationException if volume amplification is requested
     *             and is not supported.
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
     * @param muted true to mute the audio track
     */
    void setMute(boolean muted) {
        mMuted = muted;
    }

    /**
     * @return true if the audio track is muted
     */
    boolean isMuted() {
        return mMuted;
    }

    /**
     * @param muted true to mute the audio track
     */
    public void setAppMute(boolean muted) {
        mAppMuted = muted;
    }

    /**
     * @return true if the audio track is muted
     */
    public boolean isAppMuted() {
        return mAppMuted;
    }

    /**
     * Set the start time of this audio track relative to the storyboard
     * timeline. Default value is 0.
     *
     * @param startTimeMs the start time in milliseconds
     */
    void setStartTime(long startTimeMs) {
        mStartTimeMs = startTimeMs;
    }

    /**
     * Get the start time of this audio track relative to the storyboard
     * timeline.
     *
     * @return The start time in milliseconds
     */
    public long getStartTime() {
        return mStartTimeMs;
    }

    /**
     * Set the start time of this audio track relative to the storyboard
     * timeline. Default value is 0.
     *
     * @param startTimeMs the start time in milliseconds
     */
    public void setAppStartTime(long startTimeMs) {
        mAppStartTimeMs = startTimeMs;
    }

    /**
     * Get the start time of this audio track relative to the storyboard
     * timeline.
     *
     * @return The start time in milliseconds
     */
    public long getAppStartTime() {
        return mAppStartTimeMs;
    }

    /**
     * @return The duration in milliseconds. This value represents the audio
     *         track duration (not looped)
     */
    public long getDuration() {
        return mDurationMs;
    }

    /**
     * @return The timeline duration.
     */
    public long getTimelineDuration() {
        return mTimelineDurationMs;
    }

    /**
     * Sets the start and end marks for trimming an audio track
     *
     * @param beginMs start time in the audio track in milliseconds (relative to
     *            the beginning of the audio track)
     * @param endMs end time in the audio track in milliseconds (relative to the
     *            beginning of the audio track)
     */
    void setExtractBoundaries(long beginMs, long endMs) {
        mBeginBoundaryTimeMs = beginMs;
        mEndBoundaryTimeMs = endMs;
        mTimelineDurationMs = mEndBoundaryTimeMs - mBeginBoundaryTimeMs;
    }

    /**
     * @return The boundary begin time
     */
    public long getBoundaryBeginTime() {
        return mBeginBoundaryTimeMs;
    }

    /**
     * @return The boundary end time
     */
    public long getBoundaryEndTime() {
        return mEndBoundaryTimeMs;
    }

    /**
     * Enable the loop mode for this audio track. Note that only one of the
     * audio tracks in the timeline can have the loop mode enabled. When looping
     * is enabled the samples between mBeginBoundaryTimeMs and
     * mEndBoundaryTimeMs are looped.
     *
     * @param loop true to enable looping
     */
    void enableLoop(boolean loop) {
        mLoop = loop;
    }

    /**
     * @return true if looping is enabled
     */
    boolean isLooping() {
        return mLoop;
    }

    /**
     * Enable the loop mode for this audio track. Note that only one of the
     * audio tracks in the timeline can have the loop mode enabled. When looping
     * is enabled the samples between mBeginBoundaryTimeMs and
     * mEndBoundaryTimeMs are looped.
     *
     * @param loop true to enable looping
     */
    public void enableAppLoop(boolean loop) {
        mAppLoop = loop;
    }

    /**
     * @return true if looping is enabled
     */
    public boolean isAppLooping() {
        return mAppLoop;
    }

    /**
     * Enable/disable ducking
     *
     * @param enabled true to enable ducking
     */
    void enableDucking(boolean enabled) {
        mIsDuckingEnabled = enabled;
    }

    /**
     * @return true if ducking is enabled
     */
    boolean isDuckingEnabled() {
        return mIsDuckingEnabled;
    }

    /**
     * Enable/disable ducking
     *
     * @param enabled true to enable ducking
     */
    public void enableAppDucking(boolean enabled) {
        mAppIsDuckingEnabled = enabled;
    }

    /**
     * @return true if ducking is enabled
     */
    public boolean isAppDuckingEnabled() {
        return mAppIsDuckingEnabled;
    }

    /**
     * @return The waveform data
     */
    public WaveformData getWaveformData() {
        return mWaveformData;
    }

    /**
     * @param waveformData The audio waveform data
     */
    void setWaveformData(WaveformData waveformData) {
        mWaveformData = waveformData;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MovieAudioTrack)) {
            return false;
        }
        return mUniqueId.equals(((MovieAudioTrack)object).mUniqueId);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }
}
