package com.lgh.frame.frameanimlibrary;

import android.graphics.drawable.Drawable;

public class DrawableCache {
    public Drawable drawable1;
    public Drawable drawable2;
    public int frontDrawable;

    //swap front and back drawable
    void swapDrawableCache(Drawable drawable) {
        if (frontDrawable == 1) {
            frontDrawable = 2;
            drawable2 = drawable;
        } else {
            frontDrawable = 1;
            drawable1 = drawable;
        }
    }

    //Reuse previous frame's bitmap,double buffer here
    Drawable getDrawableFromCache() {
        if (frontDrawable == 1) {
            return drawable2;
        } else {
            return drawable1;
        }
    }
}
