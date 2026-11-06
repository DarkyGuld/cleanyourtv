package com.gathin.tvhome;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }

    /** A reusable, interruptible spring stored on the view under a small tag. */
    static void spring(View view, DynamicAnimation.ViewProperty property, int tag,
                       float target, float stiffness, float damping) {
        Object saved = view.getTag(tag);
        SpringAnimation animation;
        if (saved instanceof SpringAnimation) {
            animation = (SpringAnimation) saved;
        } else {
            animation = new SpringAnimation(view, property);
            animation.setSpring(new SpringForce());
            view.setTag(tag, animation);
        }
        if (animation.getSpring() == null) animation.setSpring(new SpringForce());
        animation.getSpring().setStiffness(stiffness).setDampingRatio(damping).setFinalPosition(target);
        animation.start();
    }

    /** Cancels the focus springs a recycled tile may still be running. */
    static void cancelFocus(View view) {
        for (int tag : new int[]{R.id.spring_scale_x, R.id.spring_scale_y,
                R.id.spring_translation_y, R.id.spring_translation_z}) {
            Object saved = view.getTag(tag);
            if (saved instanceof SpringAnimation) ((SpringAnimation) saved).cancel();
        }
    }

    /**
     * Lumen-style focus: interruptible springs on scale, lift and shadow. Fast key presses redirect
     * the existing spring instead of restarting a fresh animation.
     */
    static void focus(View view, boolean focused, float scale, float risePx, float zPx) {
        spring(view, DynamicAnimation.SCALE_X, R.id.spring_scale_x, focused ? scale : 1f, 400f, 0.86f);
        spring(view, DynamicAnimation.SCALE_Y, R.id.spring_scale_y, focused ? scale : 1f, 400f, 0.86f);
        spring(view, DynamicAnimation.TRANSLATION_Y, R.id.spring_translation_y, focused ? -risePx : 0f, 420f, 0.85f);
        spring(view, DynamicAnimation.TRANSLATION_Z, R.id.spring_translation_z, focused ? zPx : 0f, 300f, 1f);
    }
}
