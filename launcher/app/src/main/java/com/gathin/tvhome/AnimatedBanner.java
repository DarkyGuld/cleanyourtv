package com.gathin.tvhome;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;

/**
 * API 28 animated banners. Keeping ImageDecoder and AnimatedImageDrawable in this class means the
 * verifier never resolves them on Android 8.0/8.1, where minSdk 26 still has to start Home.
 * Every caller checks Build.VERSION.SDK_INT >= 28 before touching this class.
 */
@RequiresApi(28)
final class AnimatedBanner {
    private AnimatedBanner() { }

    static Drawable decode(File file) throws IOException {
        return repeat(ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)));
    }

    static Drawable decode(Context context, int id) throws IOException {
        return repeat(ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.getResources(), id),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)));
    }

    static boolean isAnimated(Drawable drawable) {
        return drawable instanceof AnimatedImageDrawable;
    }

    static void start(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).start();
    }

    static void stop(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).stop();
    }

    private static Drawable repeat(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        }
        return drawable;
    }
}
