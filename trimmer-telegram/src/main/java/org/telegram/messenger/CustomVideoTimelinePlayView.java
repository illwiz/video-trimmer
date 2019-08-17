/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

@TargetApi(10)
public class CustomVideoTimelinePlayView extends View {

    private long videoLength;
    private float progressLeft;
    private float progressRight = 1;
    private Paint paint;
    private Paint paint2;
    private boolean pressedLeft;
    private boolean pressedRight;
    private boolean pressedPlay;
    private float playProgress = 0.5f;
    private float bufferedProgress = 0.5f;
    private float pressDx;
    private MediaMetadataRetriever mediaMetadataRetriever;
    private VideoTimelineViewDelegate delegate;
    private ArrayList<Bitmap> frames = new ArrayList<>();
    private AsyncTask<Integer, Integer, Bitmap> currentTask;
    private static final Object sync = new Object();
    private long frameTimeOffset;
    private int frameWidth;
    private int frameHeight;
    private int framesToLoad;
    private float maxProgressDiff = 1.0f;
    private float minProgressDiff = 0.0f;
    private boolean isRoundFrames;
    private Rect rect1;
    private Rect rect2;
    private RectF rect3 = new RectF();
    private Drawable drawableLeft;
    private Drawable drawableRight;
    private int lastWidth;

    private float maxVideoSizeProgressDiff = -1;

    public interface VideoTimelineViewDelegate {
        void onLeftProgressChanged(float progress);
        void onRightProgressChanged(float progress);
        void onPlayProgressChanged(float progress);
        void didStartDragging();
        void didStopDragging();
    }

    public CustomVideoTimelinePlayView(Context context) {
        super(context);
        initialize(null);
    }

    public CustomVideoTimelinePlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(attrs);
    }

    public CustomVideoTimelinePlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(attrs);
    }

    private void initialize(AttributeSet attrs) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);
        paint2 = new Paint();
        paint2.setColor(0x7f000000);
        drawableLeft = getContext().getResources().getDrawable(R.drawable.video_cropleft);
        drawableLeft.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        drawableRight = getContext().getResources().getDrawable(R.drawable.video_cropright);
        drawableRight.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
    }

    public float getProgress() {
        return playProgress;
    }

    public float getLeftProgress() {
        return progressLeft;
    }

    public float getRightProgress() {
        return progressRight;
    }

    public void setMinProgressDiff(float value) {
        minProgressDiff = value;
    }

    public void setMaxProgressDiff(float value) {
        maxProgressDiff = value;
        if (progressRight - progressLeft > maxProgressDiff) {
            progressRight = progressLeft + maxProgressDiff;
            invalidate();
        }
    }

    /**
     * Pass 0 or below to disable max video size
     */
    public void setMaxVideoSize(long maxVideoSize, long videoOriginalSize) {
        if(maxVideoSize>0) {
            maxVideoSizeProgressDiff = ((float)maxVideoSize / videoOriginalSize);
        }
    }

    public void setRoundFrames(boolean value) {
        isRoundFrames = value;
        if (isRoundFrames) {
            rect1 = new Rect(Utils.dp(getContext(),14), Utils.dp(getContext(),14), Utils.dp(getContext(),14 + 28), Utils.dp(getContext(),14 + 28));
            rect2 = new Rect();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();

        int width = getMeasuredWidth() - Utils.dp(getContext(),32);
        int startX = (int) (width * progressLeft) + Utils.dp(getContext(),16);
        //int playX = (int) (width * (progressLeft + (progressRight - progressLeft) * playProgress)) + Utils.dp(getContext(),16);
        int endX = (int) (width * progressRight) + Utils.dp(getContext(),16);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (mediaMetadataRetriever == null) {
                return false;
            }
            int additionWidth = Utils.dp(getContext(),12);
            int additionWidthPlay = Utils.dp(getContext(),8);
            /*if (playX - additionWidthPlay <= x && x <= playX + additionWidthPlay && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging();
                }
                pressedPlay = true;
                pressDx = (int) (x - playX);
                invalidate();
                return true;
            } else*/ if (startX - additionWidth <= x && x <= startX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging();
                }
                pressedLeft = true;
                pressDx = (int) (x - startX);
                invalidate();
                return true;
            } else if (endX - additionWidth <= x && x <= endX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging();
                }
                pressedRight = true;
                pressDx = (int) (x - endX);
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedLeft) {
                if (delegate != null) {
                    delegate.didStopDragging();
                }
                pressedLeft = false;
                return true;
            } else if (pressedRight) {
                if (delegate != null) {
                    delegate.didStopDragging();
                }
                pressedRight = false;
                return true;
            } /*else if (pressedPlay) {
                if (delegate != null) {
                    delegate.didStopDragging();
                }
                pressedPlay = false;
                return true;
            }*/
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            /*if (pressedPlay) {
                playX = (int) (x - pressDx);
                playProgress = (float) (playX - Utils.dp(getContext(),16)) / (float) width;
                if (playProgress < progressLeft) {
                    playProgress = progressLeft;
                } else if (playProgress > progressRight) {
                    playProgress = progressRight;
                }
                playProgress = (playProgress - progressLeft) / (progressRight - progressLeft);
                if (delegate != null) {
                    delegate.onPlayProgressChanged(progressLeft + (progressRight - progressLeft) * playProgress);
                }
                invalidate();
                return true;
            } else*/ if (pressedLeft) {
                startX = (int) (x - pressDx);
                if (startX < Utils.dp(getContext(),16)) {
                    startX = Utils.dp(getContext(),16);
                } else if (startX > endX) {
                    startX = endX;
                }
                progressLeft = (float) (startX - Utils.dp(getContext(),16)) / (float) width;
                if(maxVideoSizeProgressDiff!=-1 && progressRight - progressLeft > maxVideoSizeProgressDiff) { // Higher than max video size limit
                    progressRight = progressLeft + maxVideoSizeProgressDiff;
                } else if (progressRight - progressLeft > maxProgressDiff) {
                    progressRight = progressLeft + maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressLeft = progressRight - minProgressDiff;
                    if (progressLeft < 0) {
                        progressLeft = 0;
                    }
                }
                if (delegate != null) {
                    delegate.onLeftProgressChanged(progressLeft);
                }
                invalidate();
                return true;
            } else if (pressedRight) {
                endX = (int) (x - pressDx);
                if (endX < startX) {
                    endX = startX;
                } else if (endX > width + Utils.dp(getContext(),16)) {
                    endX = width + Utils.dp(getContext(),16);
                }
                progressRight = (float) (endX - Utils.dp(getContext(),16)) / (float) width;
                if(maxVideoSizeProgressDiff!=-1 && progressRight - progressLeft > maxVideoSizeProgressDiff) { // Higher than max video size limit
                    progressLeft = progressRight - maxVideoSizeProgressDiff;
                } else if (progressRight - progressLeft > maxProgressDiff) {
                    progressLeft = progressRight - maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressRight = progressLeft + minProgressDiff;
                    if (progressRight > 1.0f) {
                        progressRight = 1.0f;
                    }
                }
                if (delegate != null) {
                    delegate.onRightProgressChanged(progressRight);
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public void setVideoPath(Uri path) {
        destroy();
        mediaMetadataRetriever = new MediaMetadataRetriever();
        progressLeft = 0.0f;
        progressRight = minProgressDiff;
        try {
            mediaMetadataRetriever.setDataSource(getContext(),path);
            String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            videoLength = Long.parseLong(duration);
        } catch (Exception e) {
            //FileLog.e(e);
            e.printStackTrace();
        }
        invalidate();
    }

    public void setDelegate(VideoTimelineViewDelegate delegate) {
        this.delegate = delegate;
    }

    private void reloadFrames(int frameNum) {
        if (mediaMetadataRetriever == null) {
            return;
        }
        if (frameNum == 0) {
            if (isRoundFrames) {
                frameHeight = frameWidth = Utils.dp(getContext(),56);
                framesToLoad = (int) Math.ceil((getMeasuredWidth() - Utils.dp(getContext(),16)) / (frameHeight / 2.0f));
            } else {
                frameHeight = Utils.dp(getContext(),40);
                framesToLoad = (getMeasuredWidth() - Utils.dp(getContext(),16)) / frameHeight;
                frameWidth = (int) Math.ceil((float) (getMeasuredWidth() - Utils.dp(getContext(),16)) / (float) framesToLoad);
            }
            frameTimeOffset = videoLength / framesToLoad;
        }
        currentTask = new AsyncTask<Integer, Integer, Bitmap>() {
            private int frameNum = 0;

            @Override
            protected Bitmap doInBackground(Integer... objects) {
                frameNum = objects[0];
                Bitmap bitmap = null;
                if (isCancelled()) {
                    return null;
                }
                try {
                    bitmap = mediaMetadataRetriever.getFrameAtTime(frameTimeOffset * frameNum * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (isCancelled()) {
                        return null;
                    }
                    if (bitmap != null) {
                        Bitmap result = Bitmap.createBitmap(frameWidth, frameHeight, bitmap.getConfig());
                        Canvas canvas = new Canvas(result);
                        float scaleX = (float) frameWidth / (float) bitmap.getWidth();
                        float scaleY = (float) frameHeight / (float) bitmap.getHeight();
                        float scale = scaleX > scaleY ? scaleX : scaleY;
                        int w = (int) (bitmap.getWidth() * scale);
                        int h = (int) (bitmap.getHeight() * scale);
                        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        Rect destRect = new Rect((frameWidth - w) / 2, (frameHeight - h) / 2, w, h);
                        canvas.drawBitmap(bitmap, srcRect, destRect, null);
                        bitmap.recycle();
                        bitmap = result;
                    }
                } catch (Exception e) {
                    //FileLog.e(e);
                    e.printStackTrace();
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (!isCancelled()) {
                    frames.add(bitmap);
                    invalidate();
                    if (frameNum < framesToLoad) {
                        reloadFrames(frameNum + 1);
                    }
                }
            }
        };
        currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, frameNum, null, null);
    }

    public void destroy() {
        synchronized (sync) {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                    mediaMetadataRetriever = null;
                }
            } catch (Exception e) {
                //FileLog.e(e);
                e.printStackTrace();
            }
        }
        for (int a = 0; a < frames.size(); a++) {
            Bitmap bitmap = frames.get(a);
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    public boolean isDragging() {
        return pressedPlay;
    }

    public void setProgress(float value) {
        playProgress = value;
        invalidate();
    }

    public void clearFrames() {
        for (int a = 0; a < frames.size(); a++) {
            Bitmap bitmap = frames.get(a);
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != widthSize) {
            clearFrames();
            lastWidth = widthSize;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getMeasuredWidth() - Utils.dp(getContext(),36);
        int startX = (int) (width * progressLeft) + Utils.dp(getContext(),16);
        int endX = (int) (width * progressRight) + Utils.dp(getContext(),16);

        canvas.save();
        canvas.clipRect(Utils.dp(getContext(),16), Utils.dp(getContext(),4), width + Utils.dp(getContext(),20), Utils.dp(getContext(),48));
        if (frames.isEmpty() && currentTask == null) {
            reloadFrames(0);
        } else {
            int offset = 0;
            for (int a = 0; a < frames.size(); a++) {
                Bitmap bitmap = frames.get(a);
                if (bitmap != null) {
                    int x = Utils.dp(getContext(),16) + offset * (isRoundFrames ? frameWidth / 2 : frameWidth);
                    if(a>0) { // Add frame space 2dp
                        x = x+(Utils.dp(getContext(),2)*offset);
                    }
                    int y = Utils.dp(getContext(),2 + 4);
                    if (isRoundFrames) {
                        rect2.set(x, y, x + Utils.dp(getContext(),28), y + Utils.dp(getContext(),28));
                        canvas.drawBitmap(bitmap, rect1, rect2, null);
                    } else {
                        canvas.drawBitmap(bitmap, x, y, null);
                    }
                }
                offset++;
            }
        }

        int top = Utils.dp(getContext(),2 + 4);
        int end = Utils.dp(getContext(),48);

        canvas.drawRect(Utils.dp(getContext(),16), top, startX, Utils.dp(getContext(),46), paint2);
        canvas.drawRect(endX + Utils.dp(getContext(),4), top, Utils.dp(getContext(),16) + width + Utils.dp(getContext(),4), Utils.dp(getContext(),46), paint2);

        // Draw top and bottom line
        canvas.drawRect(startX, Utils.dp(getContext(),4), startX + Utils.dp(getContext(),2), end, paint);
        canvas.drawRect(endX + Utils.dp(getContext(),2), Utils.dp(getContext(),4), endX + Utils.dp(getContext(),4), end, paint);
        canvas.drawRect(startX + Utils.dp(getContext(),2), Utils.dp(getContext(),4), endX + Utils.dp(getContext(),4), top, paint);
        canvas.drawRect(startX + Utils.dp(getContext(),2), end - Utils.dp(getContext(),2), endX + Utils.dp(getContext(),4), end, paint);
        canvas.restore();

        // Draw left line and the drawable
        /*rect3.set(startX - Utils.dp(getContext(),8), Utils.dp(getContext(),4), startX + Utils.dp(getContext(),2), end);
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),2), Utils.dp(getContext(),2), paint);
        drawableLeft.setBounds(startX - Utils.dp(getContext(),8), Utils.dp(getContext(),4) + (Utils.dp(getContext(),44) - Utils.dp(getContext(),18)) / 2, startX + Utils.dp(getContext(),2), (Utils.dp(getContext(),44) - Utils.dp(getContext(),18)) / 2 + Utils.dp(getContext(),18 + 4));
        drawableLeft.draw(canvas);*/
        rect3.set(startX, Utils.dp(getContext(),4), startX + Utils.dp(getContext(),1), end);
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),4), Utils.dp(getContext(),4), paint);
        rect3.set(startX - Utils.dp(getContext(),2f), Utils.dp(getContext(),16), startX + Utils.dp(getContext(),4), Utils.dp(getContext(),36.2f));
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),4), Utils.dp(getContext(),4), paint);

        // Draw right line and the drawable
        /*rect3.set(endX + Utils.dp(getContext(),2), Utils.dp(getContext(),4), endX + Utils.dp(getContext(),12), end);
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),2), Utils.dp(getContext(),2), paint);
        drawableRight.setBounds(endX + Utils.dp(getContext(),2), Utils.dp(getContext(),4) + (Utils.dp(getContext(),44) - Utils.dp(getContext(),18)) / 2, endX + Utils.dp(getContext(),12), (Utils.dp(getContext(),44) - Utils.dp(getContext(),18)) / 2 + Utils.dp(getContext(),18 + 4));
        drawableRight.draw(canvas);*/
        rect3.set(endX + Utils.dp(getContext(),2), Utils.dp(getContext(),4), endX + Utils.dp(getContext(),3), end);
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),4), Utils.dp(getContext(),4), paint);
        rect3.set(endX - Utils.dp(getContext(),0), Utils.dp(getContext(),16), endX + Utils.dp(getContext(),6), Utils.dp(getContext(),36.2f));
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),4), Utils.dp(getContext(),4), paint);

        // Draw seek bar to seek frames
        float cx = Utils.dp(getContext(),18) + width * (progressLeft + (progressRight - progressLeft) * playProgress);
        rect3.set(cx - Utils.dp(getContext(),1.5f), Utils.dp(getContext(),2), cx + Utils.dp(getContext(),1.5f), Utils.dp(getContext(),50));
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),1), Utils.dp(getContext(),1), paint2);
        canvas.drawCircle(cx, Utils.dp(getContext(),52), Utils.dp(getContext(),3.5f), paint2);

        rect3.set(cx - Utils.dp(getContext(),1), Utils.dp(getContext(),2), cx + Utils.dp(getContext(),1), Utils.dp(getContext(),50));
        canvas.drawRoundRect(rect3, Utils.dp(getContext(),1), Utils.dp(getContext(),1), paint);
        canvas.drawCircle(cx, Utils.dp(getContext(),52), Utils.dp(getContext(),3), paint);
    }
}
