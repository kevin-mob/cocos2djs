/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2014-2016 Chukong Technologies Inc.
 * Copyright (c) 2017-2018 Xiamen Yaji Software Co., Ltd.
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

package org.cocos2dx.lib;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController.MediaPlayerControl;

import com.zhy.base.fileprovider.FileProvider7;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static android.content.Context.AUDIO_SERVICE;
import static android.media.MediaPlayer.SEEK_CLOSEST;

public class Cocos2dxVideoView extends SurfaceView implements MediaPlayerControl {
    private String TAG = "Cocos2dxVideoView";

    private Uri mVideoUri;
    private int mDuration;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    /**
     * mCurrentState is a VideoView object's current state.
     * mTargetState is the state that a method caller intends to reach.
     * For instance, regardless the VideoView object's current state,
     * calling pause() intends to bring the object to a target state
     * of STATE_PAUSED.
     */
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private OnVideoEventListener mOnVideoEventListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;

    // recording the seek position while preparing
    private int mSeekWhenPrepared;

    protected Cocos2dxActivity mCocos2dxActivity = null;

    protected int mViewLeft = 0;
    protected int mViewTop = 0;
    protected int mViewWidth = 0;
    protected int mViewHeight = 0;

    protected int mVisibleLeft = 0;
    protected int mVisibleTop = 0;
    protected int mVisibleWidth = 0;
    protected int mVisibleHeight = 0;

    protected boolean mFullScreenEnabled = false;
    protected int mFullScreenWidth = 0;
    protected int mFullScreenHeight = 0;

    private int mViewTag = 0;

    private boolean isMp3;

    public Cocos2dxVideoView(Cocos2dxActivity activity, int tag) {
        super(activity);

        mViewTag = tag;
        mCocos2dxActivity = activity;
        initVideoView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mVideoWidth == 0 || mVideoHeight == 0) {
            setMeasuredDimension(mViewWidth, mViewHeight);
            Log.i(TAG, "" + mViewWidth + ":" + mViewHeight);
        } else {
            setMeasuredDimension(mVisibleWidth, mVisibleHeight);
            Log.i(TAG, "" + mVisibleWidth + ":" + mVisibleHeight);
        }

    }

    public void setVideoRect(int left, int top, int maxWidth, int maxHeight) {
        mViewLeft = left;
        mViewTop = top;
        mViewWidth = maxWidth;
        mViewHeight = maxHeight;

        fixSize(mViewLeft, mViewTop, mViewWidth, mViewHeight);
    }

    public void setFullScreenEnabled(boolean enabled, int width, int height) {
        if (mFullScreenEnabled != enabled) {
            mFullScreenEnabled = enabled;
            if (width != 0 && height != 0) {
                mFullScreenWidth = width;
                mFullScreenHeight = height;
            }

            fixSize();
        }
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                /* Parent says we can be as big as we want. Just don't be larger
                 * than max size imposed on ourselves.
                 */
                result = desiredSize;
                break;

            case MeasureSpec.AT_MOST:
                /* Parent says we can be as big as we want, up to specSize.
                 * Don't be larger than specSize, and don't be larger than
                 * the max size imposed on ourselves.
                 */
                result = Math.min(desiredSize, specSize);
                break;

            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }

        return result;
    }

    private boolean mNeedResume = false;

    @Override
    public void setVisibility(int visibility) {
        if (visibility == INVISIBLE) {
            mNeedResume = isPlaying();
            if (mNeedResume) {
                mSeekWhenPrepared = getCurrentPosition();
            }
        } else if (mNeedResume) {
            start();
            mNeedResume = false;
        }
        super.setVisibility(visibility);
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        //Fix issue#11516:Can't play video on Android 2.3.x
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /*YJH if((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP)
        {
            if (isPlaying()) {
                pause();
            } else if(mCurrentState == STATE_PAUSED){
                resume();
            }
        }*/

        return false;
    }

    private boolean mIsAssetRouse = false;
    private String mVideoFilePath = null;
    private static final String AssetResourceRoot = "assets/";

    public void setVideoFileName(String path) {
        if (path.startsWith(AssetResourceRoot)) {
            path = path.substring(AssetResourceRoot.length());
        }
        if (path.startsWith("/")) {
            mIsAssetRouse = false;
            setVideoURI(FileProvider7.getUriForFile(getContext(), new File(path)), null);
        } else {
            mVideoFilePath = path;
            mIsAssetRouse = true;
            setVideoURI(Uri.parse(path), null);
        }
    }

    public void setVideoURL(String url) {
        mIsAssetRouse = false;
        setVideoURI(Uri.parse(url), null);
    }

    /**
     * @hide
     */
    private void setVideoURI(Uri uri, Map<String, String> headers) {
        mVideoUri = uri;
        String path = mVideoUri.getPath();
        if(!TextUtils.isEmpty(path))
            isMp3 = path.toLowerCase().endsWith(".mp3") || path.toLowerCase().endsWith(".wav");
        mSeekWhenPrepared = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        openVideo();

        if(isMp3)
            return;

        requestLayout();
        invalidate();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    private void openVideo() {

        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the framework.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mCocos2dxActivity.sendBroadcast(i);

        if (mSurfaceHolder == null && !isMp3) {
            // not ready for playback just yet, will try again later
            return;
        }

        if(isMp3){
            getHolder().removeCallback(mSHCallback);
        }

        if (mIsAssetRouse) {
            if (mVideoFilePath == null)
                return;
        } else if (mVideoUri == null) {
            return;
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);

        try {
            //if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    mOnVideoEventListener.onVideoEvent(mViewTag, mCurrentState == STATE_PAUSED
                            || (mNeedAutoPlay != null && !mNeedAutoPlay) ? EVENT_PAUSED : EVENT_PLAYING);
                }
            });
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (mOnVideoEventListener != null) {
                        switch (what) {
                            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                                mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_STALLED);
                                break;
                            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                                //视频缓冲完成后，回调播放状态。
                                mOnVideoEventListener.onVideoEvent(mViewTag, mCurrentState == STATE_PAUSED
                                        || (mNeedAutoPlay != null && !mNeedAutoPlay) ? EVENT_PAUSED : EVENT_PLAYING);
                                mNeedAutoPlay = null;
                                System.out.println("====== MEDIA_INFO_BUFFERING_END ");
                                break;
                        }
                    }

                    return false;
                }
            });

            if(!isMp3)
                mMediaPlayer.setDisplay(mSurfaceHolder);

            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            //}

            mDuration = -1;
            mCurrentBufferPercentage = 0;
            if (mIsAssetRouse) {
                AssetFileDescriptor afd = mCocos2dxActivity.getAssets().openFd(mVideoFilePath);
                mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } else {
                mMediaPlayer.setDataSource(mCocos2dxActivity, mVideoUri);
            }

            mMediaPlayer.prepareAsync();

            /**
             * Don't set the target state here either, but preserve the target state that was there before.
             */
            mCurrentState = STATE_PREPARING;
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mVideoUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mVideoUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
    }

    private boolean mKeepRatio = false;

    public void setKeepRatio(boolean enabled) {
        mKeepRatio = enabled;
        fixSize();
    }

    public void fixSize() {
        if (mFullScreenEnabled) {
            fixSize(0, 0, mFullScreenWidth, mFullScreenHeight);
        } else {
            fixSize(mViewLeft, mViewTop, mViewWidth, mViewHeight);
        }
    }

    public void fixSize(int left, int top, int width, int height) {
        if (mVideoWidth == 0 || mVideoHeight == 0) {
            mVisibleLeft = left;
            mVisibleTop = top;
            mVisibleWidth = width;
            mVisibleHeight = height;
        } else if (width != 0 && height != 0) {
            if (mKeepRatio) {
                if (mVideoWidth * height > width * mVideoHeight) {
                    mVisibleWidth = width;
                    mVisibleHeight = width * mVideoHeight / mVideoWidth;
                } else if (mVideoWidth * height < width * mVideoHeight) {
                    mVisibleWidth = height * mVideoWidth / mVideoHeight;
                    mVisibleHeight = height;
                }
                mVisibleLeft = left + (width - mVisibleWidth) / 2;
                mVisibleTop = top + (height - mVisibleHeight) / 2;
            } else {
                mVisibleLeft = left;
                mVisibleTop = top;
                mVisibleWidth = width;
                mVisibleHeight = height;
            }
        } else {
            mVisibleLeft = left;
            mVisibleTop = top;
            mVisibleWidth = mVideoWidth;
            mVisibleHeight = mVideoHeight;
        }

        getHolder().setFixedSize(mVisibleWidth, mVisibleHeight);

        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lParams.leftMargin = mVisibleLeft;
        lParams.topMargin = mVisibleTop;
        lParams.gravity = Gravity.TOP | Gravity.LEFT;
        setLayoutParams(lParams);
    }

    protected
    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                    }
                }
            };


    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }

            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            // mSeekWhenPrepared may be changed after seekTo() call
            int seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }

            if (mVideoWidth != 0 && mVideoHeight != 0) {
                fixSize();
            }

            if (mTargetState == STATE_PLAYING) {
                start();
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;

                    release(true);
                    if (mOnVideoEventListener != null) {
                        mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_COMPLETED);
                    }
                }
            };


    private static final int EVENT_PLAYING = 0;
    private static final int EVENT_PAUSED = 1;
    private static final int EVENT_STOPPED = 2;
    private static final int EVENT_COMPLETED = 3;
    private static final int EVENT_ERROR = 4;//error
    private static final int EVENT_STALLED = 5;//loading

    public interface OnVideoEventListener {
        void onVideoEvent(int tag, int event);
    }

    private MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;

                    if (mOnVideoEventListener != null) {
                        mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_ERROR);
                    }


                    /* If an error handler has been supplied, use it and finish. */
                    if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                            return true;
                        }
                    }

                    /* Otherwise, pop up an error dialog so the user knows that
                     * something bad has happened. Only try and pop up the dialog
                     * if we're attached to a window. When we're going away and no
                     * longer have a window, don't bother showing the user an error.
                     */
//                    if (getWindowToken() != null) {
//                        Resources r = mCocos2dxActivity.getResources();
//                        int messageId;
//
//                        if (framework_err == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
//                            // messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
//                            messageId = r.getIdentifier("VideoView_error_text_invalid_progressive_playback", "string", "android");
//                        } else {
//                            // messageId = com.android.internal.R.string.VideoView_error_text_unknown;
//                            messageId = r.getIdentifier("VideoView_error_text_unknown", "string", "android");
//                        }
//
//                        int titleId = r.getIdentifier("VideoView_error_title", "string", "android");
//                        int buttonStringId = r.getIdentifier("VideoView_error_button", "string", "android");
//
//                        new AlertDialog.Builder(mCocos2dxActivity)
//                                .setTitle(r.getString(titleId))
//                                .setMessage(messageId)
//                                .setPositiveButton(r.getString(buttonStringId),
//                                        new DialogInterface.OnClickListener() {
//                                            public void onClick(DialogInterface dialog, int whichButton) {
//                                                /* If we get here, there is no onError listener, so
//                                                 * at least inform them that the video is over.
//                                                 */
//                                                if (mOnVideoEventListener != null) {
//                                                    mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_ERROR);
//                                                }
//                                            }
//                                        })
//                                .setCancelable(false)
//                                .show();
//                    }
                    return true;
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during play back.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnVideoEventListener l) {
        mOnVideoEventListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during play back or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * 记录非主动点击暂停导致的视频暂停，这种情况需要在resume时自动播放
     */
    private Boolean mNeedAutoPlay;
    public void pauseVideo(){
        if(isInPlaybackState()){
            mSeekWhenPrepared = getCurrentPosition();
        }
        mNeedAutoPlay = isPlaying();
        System.out.println("====== pauseVideo mNeedAutoPlay: " + mNeedAutoPlay);
        pause();
    }

    public void resumeVideo(){
        if((mSurfaceHolder != null || isMp3) && mNeedAutoPlay != null && mNeedAutoPlay){
            if(mSurfaceHolder != null){
                //有些手机锁屏时不会销毁mSurfaceHolder 这里直接resume
                mSeekWhenPrepared = 0;
                resume();
            }else{
                //处理华为手机播放mp3音乐时home再回来时不能自动播放
                mTargetState = STATE_PLAYING;
                openVideo();
            }
            mNeedAutoPlay = null;
        }
    }


    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int w, int h) {
            System.out.println("====== surfaceChanged ");
            boolean isValidState = mTargetState == STATE_PLAYING;
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if(mMediaPlayer != null){
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                if(hasValidSize){
                    if(mNeedAutoPlay != null && mNeedAutoPlay){
                        start();
                    }else if(isValidState){
                        start();
                    }
                }
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
            openVideo();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;

            release(true);
        }
    };

    /*
     * release the media player in any state
     */
    private void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
        }
    }

    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            if (mOnVideoEventListener != null) {
                mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_PLAYING);
            }
        }
        mTargetState = STATE_PLAYING;
    }

    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
                if (mOnVideoEventListener != null) {
                    mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_PAUSED);
                }
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void stop() {

        if(isMp3){
            mVideoFilePath = null;
            mVideoUri = null;
            stopPlayback();
        }else if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                stopPlayback();
                if (mOnVideoEventListener != null) {
                    mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_STOPPED);
                }
            }
        }
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        if (isInPlaybackState()) {
            if (mCurrentState == STATE_PAUSED) {
                mMediaPlayer.start();
                mCurrentState = STATE_PLAYING;
                if (mOnVideoEventListener != null) {
                    mOnVideoEventListener.onVideoEvent(mViewTag, EVENT_PLAYING);
                }
            }
        }
    }

    public void restart() {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(0);
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            mTargetState = STATE_PLAYING;
        }
    }

    // cache duration as mDuration for faster access
    public int getDuration() {
        if (isInPlaybackState()) {
            if (mDuration > 0) {
                return mDuration;
            }
            mDuration = mMediaPlayer.getDuration();
            return mDuration;
        }
        mDuration = -1;
        return mDuration;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mMediaPlayer.seekTo(msec, SEEK_CLOSEST);
            }else {
                mMediaPlayer.seekTo(msec);
            }
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public void setVolume(float volume) {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if(volume < 0){
                volume = (float) Math.min(Math.ceil(volume * max), -1);
            }else if(volume > 0) {
                volume = (float) Math.max(Math.ceil(volume * max), 1);
            }
            current = (int) (current + volume);
            current = current < 0 ? 0: current > max ? max : current;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current, AudioManager.FLAG_PLAY_SOUND);
        }
    }

    public void setBrightness(float brightness) {
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            if(lp.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE){
                lp.screenBrightness = getBrightness(activity);
            }

            lp.screenBrightness += brightness;
            if (lp.screenBrightness > 1) {
                lp.screenBrightness = 1;
            } else if (lp.screenBrightness < 0.01) {
                lp.screenBrightness = (float) 0.01;
            }
            activity.getWindow().setAttributes(lp);
        }
    }

    private int getBrightness(Activity activity){
        int value = 0;
        ContentResolver cr = activity.getContentResolver();
        try {
            value = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)/255;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    public int getAudioSessionId() {
        return mMediaPlayer.getAudioSessionId();
    }


    @Override
    protected void onDetachedFromWindow() {
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;//activity恢复之前亮度
            activity.getWindow().setAttributes(lp);
        }
        super.onDetachedFromWindow();
    }
}
