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

import java.util.Random;

import com.android.videoeditor.R;

import android.content.Context;
import android.graphics.Paint;

/**
 * String utilities
 */
public class StringUtils {
    /**
     * Pseudo-random number generator object for use with randomString(). The
     * Random class is not considered to be cryptographically secure, so only
     * use these random Strings for low to medium security applications.
     */
    private static Random sRandGen = new Random();

    /**
     * Array of numbers and letters. Numbers appear in the list
     * twice so that there is a more equal chance that a number will be picked.
     * We can use the array to get a random number or letter by picking a random
     * array index.
     */
    private static char[] sNumbersAndLetters =
        ("0123456789abcdefghijklmnopqrstuvwxyz0123456789").toCharArray();

    /**
     * Array of numbers.
     */
    private static char[] sNumbers = ("0123456789").toCharArray();

    /**
     * This class cannot be instantiated
     */
    private StringUtils() {
    }

    /**
     * Returns a random String of numbers and letters (lower and upper case) of
     * the specified length. The method uses the Random class that is built-in
     * to Java which is suitable for low to medium grade security uses. This
     * means that the output is only pseudo random, i.e., each number is
     * mathematically generated so is not truly random.
     * <p>
     * The specified length must be at least one. If not, the method will return null.
     *
     * @param length the desired length of the random String to return.
     * @return a random String of numbers and letters of the specified length.
     */
    public static String randomString(int length) {
        if (length < 1) {
            return null;
        }
        // Create a char buffer to put random letters and numbers in.
        final char[] randBuffer = new char[length];
        for (int i = 0; i < randBuffer.length; i++) {
            randBuffer[i] = sNumbersAndLetters[sRandGen.nextInt(sNumbersAndLetters.length - 1)];
        }

        return new String(randBuffer);
    }

    /**
     * Returns a random String of numbers of the specified length.
     * This means that the output is only pseudo random, i.e., each number is
     * mathematically generated so is not truly random.
     * <p>
     * The specified length must be at least one. If not, the method will return null.
     *
     * @param length the desired length of the random String to return.
     * @return a random String of numbers of the specified length.
     */
    public static String randomStringOfNumbers(int length) {
        if (length < 1) {
            return null;
        }
        // Create a char buffer to put random letters and numbers in.
        final char[] randBuffer = new char[length];
        for (int i = 0; i < randBuffer.length; i++) {
            randBuffer[i] = sNumbers[sRandGen.nextInt(sNumbers.length - 1)];
        }
        return new String(randBuffer);
    }

    /**
     * Get a readable string displaying the time
     *
     * @param context The context (needed only for relative time)
     * @param time The time
     *
     * @return The time string
     */
    public static String getTimestampAsString(Context context, long time) {
        final long hours = time / 3600000;
        time %= 3600000;
        final long mins = time / 60000;
        time %= 60000;
        final long sec = time / 1000;
        time %= 1000;
        time /= 100;
        return String.format("%02d:%02d:%02d.%01d", hours, mins, sec, time);
    }

    /**
     * Get a readable string displaying the time
     *
     * @param context The context (needed only for relative time)
     * @param time The time
     *
     * @return The time string
     */
    public static String getSimpleTimestampAsString(Context context, long time) {
        final long hours = time / 3600000;
        time %= 3600000;
        final long mins = time / 60000;
        time %= 60000;
        final long sec = time / 1000;
        return String.format("%02d:%02d:%02d", hours, mins, sec);
    }

    /**
     * Get a readable string displaying the time
     *
     * @param context The context (needed only for relative time)
     * @param time The time
     *
     * @return The time string
     */
    public static String getDurationAsString(Context context, long time) {
        final long hours = time / 3600000;
        time %= 3600000;
        final long mins = time / 60000;
        time %= 60000;
        final long sec = time / 1000;

        if (hours == 0) {
            if (mins == 0) {
                return String.format(context.getString(R.string.seconds), sec);
            } else if (mins == 1) {
                return String.format(context.getString(R.string.minute_and_seconds), sec);
            } else {
                return String.format(context.getString(R.string.minutes), mins);
            }
        } else if (hours == 1) {
            return String.format(context.getString(R.string.hour_and_minutes), mins);
        } else {
            return String.format(context.getString(R.string.hours_and_minutes), hours, mins);
        }
    }

    /**
     * Trim text to a maximum size
     *
     * @param text The text
     * @param p The paint
     * @param maxSize The maximum size
     *
     * @return The text
     */
    public static String trimText(String text, Paint p, int maxSize) {
        final int textSize = (int)p.measureText(text);
        if (textSize > maxSize) {
            final int chars = p.breakText(text, true, maxSize - 12, null);
            text = text.substring(0, chars);
            text += "...";
        }

        return text;
    }
}
