package com.lgh.frame.frameanimlibrary;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.lgh.frame.frameanimlibrary.listener.OnAnimationListener;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameAniDrawable extends Drawable implements Runnable, Animatable {
    private static final String TAG = "FrameAniDrawable";
    private static final int INVALIDATE_SELF = 1;
    private static final int TRY_TO_START = 2;
    private Bitmap.Config mDecodeConfig = Bitmap.Config.RGB_565;
    private boolean mHasLoadImageInfo = false;
    private Handler mUiHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg){
            int what  = msg.what;
            switch (what){
                case INVALIDATE_SELF:
                    invalidateSelf();
                    break;
                case TRY_TO_START:
                    start();
                    break;
            }

        }
    };


    private DrawableCache mDrawableCache = new DrawableCache();

    /**
     * 线程池来加载复用的图片
     */
    private ExecutorService mExecutor;

    private OnAnimationListener mListener;

    private SoftReference<Context> mContext;

    private Drawable mCurrentDrawable;

    private ArrayList<AnimationFrame> mAnimationFrames = new ArrayList<AnimationFrame>();

    private static Map<Integer, SoftReference<FrameAniDrawable>> mOnDemandDrawableCache = new HashMap<Integer, SoftReference<FrameAniDrawable>>();

    private boolean mKeepLastFrame = false;


    /**
     * The current frame, may be -1 when not animating.
     */
    private int mCurFrame = -1;

    /**
     * Whether the drawable has an animation callback posted.
     */
    private boolean mRunning;

    /**
     * Whether the drawable should animate when visible.
     */
    private boolean mAnimating;

    private int mAlpha;
    private ColorFilter mColorFilter;

    private int mRepeatTimes;		//动画循环次数

    private int mCurRepeatTimes;   //当前循环次数


    private FrameAniDrawable(Context context, Bitmap.Config config) {
        mContext = new SoftReference<Context>(context);
        mRepeatTimes = 1;
        mCurRepeatTimes = 0;
        mDecodeConfig = config;
    }


    public void loadRaw(final int resourceId) {
        loadFromXml(resourceId);
    }

    public static FrameAniDrawable getDrawable(Context context, Bitmap.Config config){
        FrameAniDrawable drawable = new FrameAniDrawable(context,config);
        return drawable;
    }
    public static FrameAniDrawable getDrawable(Context context, int resourceId, Bitmap.Config config) {
//        if (mOnDemandDrawableCache.get(resourceId) == null || mOnDemandDrawableCache.get(resourceId).get() == null) {
        FrameAniDrawable drawable = new FrameAniDrawable(context,config);
        drawable.loadRaw(resourceId);
//            mOnDemandDrawableCache.put(resourceId, new SoftReference<OnDemandDrawable>(drawable));
        return drawable;
//        } else {
//            OnDemandDrawable drawable = mOnDemandDrawableCache.get(resourceId).get();
//            return drawable;
//        }
    }

    public static FrameAniDrawable getDrawable(Context context, int resourceId,Bitmap.Config config, OnAnimationListener listener) {
        FrameAniDrawable drawable = new FrameAniDrawable(context,config);
        drawable.setAnimationListener(listener);
        drawable.loadRaw(resourceId);
        return drawable;
    }




    public static FrameAniDrawable getDrawable(Context context, String folderPath,Bitmap.Config config, int setDuration) {
        FrameAniDrawable drawable = new FrameAniDrawable(context,config);
        drawable.loadFromFilePath(folderPath, setDuration);
        return drawable;
    }

    public static FrameAniDrawable getDrawable(Context context, String[] filePathList,Bitmap.Config config, int setDuration) {
        FrameAniDrawable drawable = new FrameAniDrawable(context,config);
        drawable.loadFromFilePath(filePathList, setDuration);
        return drawable;
    }

    private byte[] inputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }


    @Override
    public int getIntrinsicWidth() {
        return mCurrentDrawable != null ? mCurrentDrawable.getIntrinsicWidth() : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return mCurrentDrawable != null ? mCurrentDrawable.getIntrinsicHeight() : -1;
    }


    private void loadFromXml(final int resourceId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Context context = mContext.get();
                XmlResourceParser parser = context.getResources().getXml(resourceId);

                if (parser == null) {
                    Log.e(TAG, "loadFromXml parser null");
                    return;
                }

                try {
                    int eventType = parser.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_DOCUMENT) {

                        } else if (eventType == XmlPullParser.START_TAG) {

                            if (parser.getName().equals("item")) {
                                byte[] bytes = null;
                                int duration = 1000 / 60;

                                for (int i = 0; i < parser.getAttributeCount(); i++) {
                                    if (parser.getAttributeName(i).equals(
                                            "drawable")) {
                                        int resId = Integer.parseInt(parser
                                                .getAttributeValue(i)
                                                .substring(1));
                                        bytes = inputStreamToByteArray(context.getResources().openRawResource(resId));
                                    } else if (parser.getAttributeName(i)
                                            .equals("duration")) {
                                        duration = parser.getAttributeIntValue(
                                                i, 1000);
                                    }
                                }

                                AnimationFrame animationFrame = new AnimationFrame();
                                animationFrame.bytes = bytes;
                                animationFrame.duration = duration;
                                mAnimationFrames.add(animationFrame);
                            }

                        } else if (eventType == XmlPullParser.END_TAG) {

                        } else if (eventType == XmlPullParser.TEXT) {

                        }

                        eventType = parser.next();

                    }
                } catch (IOException e) {
                    Log.e(TAG, "loadFromXml catch IOException");
                } catch (XmlPullParserException e) {
                    Log.e(TAG, "loadFromXml catch XmlPullParserException");
                }

                loadFirstFrame();
                // Run on UI Thread
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) {
                            //Log.w("MyAnimationDrawable", "onDrawableLoadedListener");
                            mListener.onDrawableLoaded(mAnimationFrames);
                        }
                    }
                });
//                start();
            }
        }).start();
    }

    private boolean loadFromFilePath(String folderPath, int duration) {
        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            Log.e(TAG, "loadFromFilePath file isn't directory");
            return false;
        }
        String[] fileList = folder.list();
        if (fileList == null || fileList.length == 0) {
            return false;
        }
        for (int i = 0; i < fileList.length; i++) {
            fileList[i] = folder.getPath() + File.separator + fileList[i];
        }
        return loadFromFilePath(fileList, duration);
    }

    private boolean loadFromFilePath(String[] filesList, int _duration) {
        Context context = mContext.get();
        if (filesList == null || filesList.length == 0) {
            Log.e(TAG, "loadFromFilePath filePath null");
            return false;
        }
        try {
            FileInputStream tmpFileInputStream;
            byte[] bytes = null;
            int duration = _duration >= 0 ? _duration : 1000 / 60;
            File file = null;
            for (String path : filesList) {
                file = new File(path);
                if (!file.exists() || file.isDirectory()) {
                    continue;
                }
                tmpFileInputStream = new FileInputStream(path);
                bytes = inputStreamToByteArray(tmpFileInputStream);
                AnimationFrame animationFrame = new AnimationFrame();
                animationFrame.bytes = bytes;
                animationFrame.duration = duration;
                mAnimationFrames.add(animationFrame);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "loadFromFilePath FileNotFoundException");
        } catch (IOException e1) {
            Log.e(TAG, "loadFromFilePath  IOException");
        }
        loadFirstFrame();
        // Run on UI Thread
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    //Log.w("MyAnimationDrawable", "onDrawableLoadedListener");
                    mListener.onDrawableLoaded(mAnimationFrames);
                }
            }
        });
        return true;
    }

    //clear redundant drawable hold by this
    public void clearDrawable(boolean keepLastFrame) {
        if (!keepLastFrame) {
            mCurrentDrawable = null;
        }
        mDrawableCache.drawable1 = null;
        mDrawableCache.drawable2 = null;
        for (AnimationFrame frame : mAnimationFrames) {
            frame.drawable = null;
        }
    }


    public int getFrameSize() {
        return mAnimationFrames.size();
    }

    /**
     * Sets whether this AnimationDrawable is visible.
     * <p/>
     * When the drawable becomes invisible, it will pause its animation. A
     * subsequent change to visible with <code>restart</code> set to true will
     * restart the animation from the first frame. If <code>restart</code> is
     * false, the animation will resume from the most recent frame.
     *
     * @param visible true if visible, false otherwise
     * @param restart when visible, true to force the animation to restart
     *                from the first frame
     * @return true if the new visibility is different than its previous state
     */
    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (restart || changed) {
                boolean startFromZero = restart || mCurFrame < 0 ||
                        mCurFrame >= mAnimationFrames.size();

                if(startFromZero){
                    resetCurRepeatTimes();
                }

                setFrame(startFromZero ? 0 : mCurFrame, true, mAnimating);
            }
        } else {
            unscheduleSelf(this);
        }
        return changed;
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    /**
     * <p>Starts the animation, looping if necessary. This method has no effect
     * if the animation is running. Do not call this in the {@link android.app.Activity#onCreate}
     * method of your activity, because the {@link android.graphics.drawable.AnimationDrawable} is
     * not yet fully attached to the window. If you want to play
     * the animation immediately, without requiring interaction, then you might want to call it
     * from the {@link android.app.Activity#onWindowFocusChanged} method in your activity,
     * which will get called when Android brings your window into focus.</p>
     *
     * @see #isRunning()
     * @see #stop()
     */
    @Override
    public void start() {
        if(mHasLoadImageInfo == false){
            Message msg = Message.obtain();
            msg.what = TRY_TO_START;
            mUiHandler.sendMessageDelayed(msg,100);
        }
        mAnimating = true;
        resetCurRepeatTimes();
        if (!isRunning()) {
            run();
        }
    }

    /**
     * <p>Stops the animation. This method has no effect if the animation is
     * not running.</p>
     *
     * @see #isRunning()
     * @see #start()
     */
    @Override
    public void stop() {
        mAnimating = false;

        if (isRunning()) {
            unscheduleSelf(this);
        }
        clearDrawable(false);
        mExecutor.shutdownNow();
    }




    /**
     * <p>Indicates whether the animation is currently running or not.</p>
     *
     * @return true if the animation is running, false otherwise
     */
    @Override
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * <p>This method exists for implementation purpose only and should not be
     * called directly. Invoke {@link #start()} instead.</p>
     *
     * @see #start()
     */
    @Override
    public void run() {
        nextFrame(false);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mCurrentDrawable != null) {
            mCurrentDrawable.setBounds(getBounds());
            mCurrentDrawable.draw(canvas);
        }
    }

    @Override
    public void unscheduleSelf(Runnable what) {
        mCurFrame = -1;
        mRunning = false;
        super.unscheduleSelf(what);
    }


    @Override
    public void setAlpha(int alpha) {
        if (mAlpha != alpha) {
            mAlpha = alpha;
            if (mCurrentDrawable != null) {
                mCurrentDrawable.mutate().setAlpha(alpha);
            }
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (mColorFilter != cf) {
            mColorFilter = cf;
            if (mCurrentDrawable != null) {
                mCurrentDrawable.mutate().setColorFilter(cf);
            }
        }
    }

    /**
     * @return The number of frames in the animation
     */
    public int getNumberOfFrames() {
        return mAnimationFrames.size();
    }


    /**
     * @return The duration in milliseconds of the frame at the
     * specified index
     */
    public int getDuration() {
        return mAnimationFrames.get(mCurFrame).duration;
    }

    public void setAnimationListener(OnAnimationListener listener) {
        mListener = listener;
    }

    private void nextFrame(boolean unschedule) {
        int next = mCurFrame + 1;
        final int N = mAnimationFrames.size();

        if(next == N && N > 0){
            mCurRepeatTimes++;
            if(mCurRepeatTimes < mRepeatTimes){
                next = 0;
            }
        } else if (next > N) {
            next = 0;
        }
        setFrame(next, unschedule, next < N);
    }

    public void setMaxRepeatTimes(int repeatTimes){
        mRepeatTimes = repeatTimes < 1 ? 1 : repeatTimes;
    }

    public void resetCurRepeatTimes(){
        mCurRepeatTimes = 0;
    }

    @Override
    public void scheduleSelf(Runnable what, long when) {
        super.scheduleSelf(what, when);
    }

    public void setKeepLastFrameWhenFinish(boolean keepLastFrame) {
        this.mKeepLastFrame = keepLastFrame;
    }

    private void setFrame(int frame, boolean unschedule, boolean animate) {
        if (frame >= mAnimationFrames.size()) {
            if (frame == mAnimationFrames.size()) {
                mRunning = false;
//                loadFirstFrame();
                if (mListener != null) {
                    mListener.onAnimationFinished();
                }
                clearDrawable(this.mKeepLastFrame);
            }

            return;
        }
        if (mListener != null) {
            mListener.onAnimationFrameLoaded(frame);
        }
        mAnimating = animate;
        mCurFrame = frame;
        selectDrawable(frame);
        if (unschedule || animate) {
            unscheduleSelf(this);
        }
        if (animate) {
            // Unscheduling may have clobbered these values; restore them
            mCurFrame = frame;
            mRunning = true;

            scheduleSelf(this, SystemClock.uptimeMillis() + mAnimationFrames.get(mCurFrame).duration);
        }

    }


    public boolean selectDrawable(int frameNumber) {
        final AnimationFrame thisFrame = mAnimationFrames.get(frameNumber);
        //use previous frame if current frame has not been loaded
        if (thisFrame.drawable != null) {
            mCurrentDrawable = thisFrame.drawable;
            mDrawableCache.swapDrawableCache(thisFrame.drawable);
            Message msg = Message.obtain();
            msg.what = INVALIDATE_SELF;
            mUiHandler.sendMessage(msg);

        }
        loadNextFrame(mAnimationFrames, mContext.get());
        return false;
    }

    private void loadFirstFrame() {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            options.inMutable = true;
        }
        options.inSampleSize = 1;
        options.inPreferredConfig =mDecodeConfig;
        AnimationFrame firstFrame = mAnimationFrames.get(0);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeByteArray(firstFrame.bytes, 0, firstFrame.bytes.length, options);

        } catch (OutOfMemoryError e) {
            Log.i("EnhancedAnimation", " loadFirstFrame OutOfMemoryError");
            return;
        }
        firstFrame.drawable = new BitmapDrawable(mContext.get().getResources(), bitmap);
        mCurrentDrawable = firstFrame.drawable;
        setFrame(0, false, false);
        loadNextFrame(mAnimationFrames, mContext.get());
        mHasLoadImageInfo = true;
    }

    public void setDeodeConfig(Bitmap.Config config){
        mDecodeConfig = config;
    }


    /**
     * reuse previous bitmap if possible
     */
    private void tryReuseBitmap(BitmapFactory.Options inOptions, Drawable drawable, byte[] bitmapBytes,int bitmaplength) {
        Bitmap oldBitmap = ((BitmapDrawable) drawable).getBitmap();
        if (!oldBitmap.isMutable()) {
            return;
        }
        int oldBitmapSize = oldBitmap.getByteCount();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (oldBitmap.getByteCount() >= bitmaplength) {
                inOptions.inBitmap = oldBitmap;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            Bitmap bitmapBounds = BitmapFactory.decodeByteArray(bitmapBytes, 0,bitmaplength, options);
            if (bitmapBounds == null)
                return;
            if (bitmapBounds.getWidth() == oldBitmap.getWidth() && bitmapBounds.getHeight() == oldBitmap.getHeight()) {
                inOptions.inBitmap = oldBitmap;
            }
        }
    }

    private void loadNextFrame(final List<AnimationFrame> animationFrames, final Context context) {
        final int nextFrameNum = mCurFrame + 1;
        if (nextFrameNum < animationFrames.size()) {
            //线程池运行加载下一帧的图片
            if (mExecutor == null || mExecutor.isShutdown()) {
                mExecutor = Executors.newSingleThreadExecutor();
            }
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig =mDecodeConfig;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        options.inMutable = true;
                    }
                    options.inSampleSize = 1;
                    AnimationFrame nextFrame = animationFrames.get(nextFrameNum);

                    BitmapFactory.Options optionsSize = new  BitmapFactory.Options();
                    optionsSize.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(nextFrame.bytes, 0, nextFrame.bytes.length, optionsSize);
                    int BitmapSize = CauclateBitmapMemorySize(optionsSize.outWidth,optionsSize.outHeight);
                    if (nextFrameNum - 2 >= 0) {
                        Drawable drawable = mDrawableCache.getDrawableFromCache();
                        if (drawable != null) {
                            tryReuseBitmap(options, drawable, nextFrame.bytes,BitmapSize);
                        }
                        //clear previous drawable
                        AnimationFrame previousFrame = animationFrames.get(nextFrameNum - 2);
                        previousFrame.drawable = null;
                    }

                    try {
                        nextFrame.drawable = new BitmapDrawable(context.getResources(),
                                BitmapFactory.decodeByteArray(nextFrame.bytes, 0, nextFrame.bytes.length, options));
                    } catch (OutOfMemoryError e) {
                        Log.i(TAG, " loadNextFrame OutOfMemoryError");
                    }
                }
            });
        }
    }

    private int CauclateBitmapMemorySize(int width,int height){
        int bitmapSize = 0;
        if(mDecodeConfig == Bitmap.Config.RGB_565){
            bitmapSize = width*height*2;
        }else if(mDecodeConfig == Bitmap.Config.ARGB_4444){
            bitmapSize = width*height*2;
        }else if(mDecodeConfig == Bitmap.Config.ARGB_8888){
            bitmapSize = width*height*4;
        }else{
            bitmapSize = width*height*4;
        }
        return bitmapSize;
    }


}
