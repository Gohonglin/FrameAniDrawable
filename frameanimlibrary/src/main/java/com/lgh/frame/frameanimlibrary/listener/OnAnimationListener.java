package com.lgh.frame.frameanimlibrary.listener;

import com.lgh.frame.frameanimlibrary.AnimationFrame;
import com.lgh.frame.frameanimlibrary.FrameAniDrawable;

import java.util.List;

public interface OnAnimationListener {
    void onDrawableLoaded(List<AnimationFrame> animationFrames);

    void onAnimationFrameLoaded(int animationFrame);

    void onAnimationFinished();
}
