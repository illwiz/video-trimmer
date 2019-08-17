package dev.illwiz.videotrimmer.trimmer;

import android.app.ProgressDialog;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.telegram.messenger.CustomVideoTimelinePlayView;
import org.telegram.messenger.VideoEditInfo;
import org.telegram.messenger.VideoTrimUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dev.illwiz.videotrimmer.R;
import dev.illwiz.videotrimmer.utils.Prop;
import dev.illwiz.videotrimmer.utils.TrimUtils;
import dev.illwiz.videotrimmer.utils.Utils;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class TelegramActivity extends AppCompatActivity {
    private static final int VIDEO_MIN_DURATION_MS = 3000;
    private static final int VIDEO_MAX_DURATION_MS = 60000;
    private static final long VIDEO_MAX_SIZE = 10 * 1024 * 1024;
    @BindView(R.id.videoViewWrapper)
    FrameLayout videoViewWrapper;
    @BindView(R.id.videoView)
    VideoView videoView;
    @BindView(R.id.playBtn)
    ImageView playBtn;
    @BindView(R.id.trimDurAndSizeTxt)
    TextView trimDurAndSizeTxt;
    @BindView(R.id.trimDurRangeTxt)
    TextView trimDurRangeTxt;
    @BindView(R.id.timelineView)
    CustomVideoTimelinePlayView timelineView;
    @BindView(R.id.trimBtn)
    TextView trimBtn;

    private Uri videoUri;
    private File videoFile;
    private float videoDuration;
    private long estimatedDuration,trimStartTime,trimEndTime;
    private long originalSize,estimatedSize;
    private Disposable trimTask,convertTask;
    private ProgressDialog progressDialog;
    private Runnable updateProgressRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trimmer_telegram);
        ButterKnife.bind(this);
        initialize();
    }

    @Override
    protected void onDestroy() {
        if(trimTask!=null) {
            trimTask.dispose();
        }
        if(convertTask!=null) {
            convertTask.dispose();
        }
        if(updateProgressRunnable!=null) {

        }
        super.onDestroy();
    }

    @OnClick(R.id.trimBtn)
    public void trimBtn() {
        //long now = System.currentTimeMillis();
        File outDir = Environment.getExternalStorageDirectory();
        /*File outDir = new File(videoFile.getParent(),"trim-"+now+".mp4");
        try {
            boolean createFileSuccess = outDir.createNewFile();
            Timber.d("Create file success: "+createFileSuccess+" - "+outDir.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            showMessage(e.getMessage());
            return;
        }*/
        Completable trimCompletable = Completable.fromAction(()->{
            //TrimUtils.trim(videoFile,outDir,trimStartTime,trimEndTime);
            File result = TrimUtils.trimVideo(videoFile,outDir,(int)trimStartTime,(int)trimEndTime);
            Timber.d("Trim result "+result.getAbsolutePath());
        });
        trimTask = trimCompletable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> {
                    showLoading(true,"Trim in progress, please wait...");
                })
                .subscribe(()->{
                   showLoading(false,null);
                   showMessage("Trim success");
                },throwable -> {
                    throwable.printStackTrace();
                    showLoading(false,null);
                    showMessage(throwable.getMessage());
                });
    }

    private Timer trimDurCounterTimer;

    @OnClick(R.id.videoViewWrapper)
    public void videoViewWrapper() {
        if(videoView.isPlaying()) {
            trimDurCounterTimer.cancel();
            videoView.pause();
            playBtn.setVisibility(View.VISIBLE);
        } else {
            trimDurCounterTimer = new Timer();
            trimDurCounterTimer.scheduleAtFixedRate(new TimerTask() {
                long currentTime;
                @Override
                public void run() {
                    currentTime = videoView.getCurrentPosition();
                    String trimRangeDurStr = Utils.getMinuteSeconds(currentTime) + "-" + Utils.getMinuteSeconds(trimEndTime);
                    runOnUiThread(()->{
                        trimDurRangeTxt.setText(trimRangeDurStr);
                    });
                    if(currentTime>=trimEndTime) {
                        trimDurCounterTimer.cancel();
                        runOnUiThread(()->{
                            videoView.pause();
                            videoView.seekTo((int)trimStartTime);
                            String trimRangeDurStr2 = Utils.getMinuteSeconds(trimStartTime) + "-" + Utils.getMinuteSeconds(trimEndTime);
                            trimDurRangeTxt.setText(trimRangeDurStr2);
                        });
                    }
                }
            },0,100);
            videoView.start();
            timelineView.post(updateProgressRunnable);
            playBtn.setVisibility(View.GONE);
        }
    }

    private void initialize() {
        videoUri = getIntent().getParcelableExtra(Prop.MAIN_OBJ);
        String path = Utils.getPath(this,videoUri);
        videoFile = new File(path);

        updateProgressRunnable = ()->{
            if(videoView==null||!videoView.isPlaying()) {
                timelineView.removeCallbacks(updateProgressRunnable);
            }
            updatePlayProgress();
            timelineView.postDelayed(updateProgressRunnable,17);
        };
        convertInputVideo(videoFile,()->{
            originalSize = videoFile.length();
            videoView.setOnPreparedListener(mediaPlayer -> {
                videoDuration = mediaPlayer.getDuration();
                initVideoTimelineView();
                playBtn.setVisibility(View.VISIBLE);
                updateVideoInfo();
            });
            //videoView.setMediaController(new MediaController(this));
            videoView.setVideoURI(videoUri);
        });
    }

    private void updatePlayProgress() {
        float progress = 0;
        if (videoView != null) {
            progress = videoView.getCurrentPosition() / (float) videoView.getDuration();
            if (timelineView.getVisibility() == View.VISIBLE) {
                progress -= timelineView.getLeftProgress();
                if (progress < 0) {
                    progress = 0;
                }
                progress /= (timelineView.getRightProgress() - timelineView.getLeftProgress());
                if (progress > 1) {
                    progress = 1;
                }
            }
        }
        timelineView.setProgress(progress);
    }

    private void updateVideoInfo() {
        trimStartTime = (long) Math.ceil(timelineView.getLeftProgress()*videoDuration);
        trimEndTime = (long) Math.ceil(timelineView.getRightProgress()*videoDuration);
        estimatedDuration = trimEndTime - trimStartTime;
        estimatedSize = (int) (originalSize * ((float) estimatedDuration / videoDuration));
        String videoTimeSize = String.format(Locale.US,"%s, ~%s", Utils.getMinuteSeconds(estimatedDuration), Utils.formatFileSize(estimatedSize));
        trimDurAndSizeTxt.setText(videoTimeSize);
        String trimRangeDurStr = Utils.getMinuteSeconds(trimStartTime) + "-" + Utils.getMinuteSeconds(trimEndTime);
        trimDurRangeTxt.setText(trimRangeDurStr);
    }

    private void initVideoTimelineView() {
        if(videoDuration>=(VIDEO_MIN_DURATION_MS+1000)) {
            float minProgressDiff = VIDEO_MIN_DURATION_MS/videoDuration;
            timelineView.setMinProgressDiff(minProgressDiff);
        }
        if(videoDuration>=(VIDEO_MAX_DURATION_MS+1000)) {
            float maxProgressDiff = VIDEO_MAX_DURATION_MS/videoDuration;
            timelineView.setMaxProgressDiff(maxProgressDiff);
        }
        timelineView.setMaxVideoSize(VIDEO_MAX_SIZE,originalSize);
        timelineView.setDelegate(new CustomVideoTimelinePlayView.VideoTimelineViewDelegate() {

            @Override
            public void onLeftProgressChanged(float progress) {
                Timber.d("onLeftProgressChanged "+progress);
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
                //videoView.seekTo((int) (videoDuration * progress));
                timelineView.setProgress(0);
                updateVideoInfo();
            }

            @Override
            public void onRightProgressChanged(float progress) {
                Timber.d("onRightProgressChanged "+progress);
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
                //videoView.seekTo((int) (videoDuration * progress));
                timelineView.setProgress(0);
                updateVideoInfo();
            }

            @Override
            public void onPlayProgressChanged(float progress) {
                Timber.d("onPlayProgressChanged "+progress);
                videoView.seekTo((int) (videoDuration * progress));
            }

            @Override
            public void didStartDragging() {
                Timber.d("didStartDragging");
            }

            @Override
            public void didStopDragging() {
                Timber.d("didStopDragging");
                videoView.seekTo((int) (videoDuration * timelineView.getLeftProgress()));
            }
        });
        timelineView.setVideoPath(videoUri);
    }

    private void convertInputVideo(File videoSrc,Runnable onComplete) {
        Completable convertCompletable = Completable.fromAction(()->{
            File outDir = Environment.getExternalStorageDirectory();
            MediaExtractor mex = new MediaExtractor();
            try {
                mex.setDataSource(videoSrc.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            MediaFormat mf = mex.getTrackFormat(0);
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(videoSrc.getAbsolutePath());
            int originalWidth = mf.getInteger(MediaFormat.KEY_WIDTH);
            int originalHeight = mf.getInteger(MediaFormat.KEY_HEIGHT);
            int resultWidth = 0;
            int resultHeight = 0;
            String bitrateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            int originalBitrate = bitrateStr==null ? -1 : Integer.parseInt(bitrateStr);
            int bitrate = originalBitrate;
            int originalFrameRate = mf.getInteger(MediaFormat.KEY_FRAME_RATE);
            int frameRate = originalFrameRate > 15 ? 15 : originalFrameRate;
            mmr.release();
            mex.release();
            if (bitrate > 900000) {
                bitrate = 900000;
            }
            int selectedCompression = VideoEditInfo.COMPRESS_360;
            int compressionsCount;
            if (originalWidth > 1280 || originalHeight > 1280) {
                compressionsCount = 5;
            } else if (originalWidth > 848 || originalHeight > 848) {
                compressionsCount = 4;
            } else if (originalWidth > 640 || originalHeight > 640) {
                compressionsCount = 3;
            } else if (originalWidth > 480 || originalHeight > 480) {
                compressionsCount = 2;
            } else {
                compressionsCount = 1;
            }

            if (selectedCompression >= compressionsCount) {
                selectedCompression = compressionsCount - 1;
            }
            if (selectedCompression != compressionsCount - 1) {
                float maxSize;
                int targetBitrate;
                switch (selectedCompression) {
                    case 0:
                        maxSize = 432.0f;
                        targetBitrate = 400000;
                        break;
                    case 1:
                        maxSize = 640.0f;
                        targetBitrate = 900000;
                        break;
                    case 2:
                        maxSize = 848.0f;
                        targetBitrate = 1100000;
                        break;
                    case 3:
                    default:
                        targetBitrate = 2500000;
                        maxSize = 1280.0f;
                        break;
                }
                float scale = originalWidth > originalHeight ? maxSize / originalWidth : maxSize / originalHeight;
                resultWidth = Math.round(originalWidth * scale / 2) * 2;
                resultHeight = Math.round(originalHeight * scale / 2) * 2;
                if (bitrate != 0) {
                    bitrate = Math.min(targetBitrate, (int) (originalBitrate / scale));
                    long videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);
                    Timber.d("Frame siwze "+videoFramesSize);
                }
            }

            if (selectedCompression == compressionsCount - 1) {
                resultWidth = originalWidth;
                resultHeight = originalHeight;
                bitrate = originalBitrate;
            }
            File result = TrimUtils.convertVideo(videoSrc,outDir,resultWidth,resultHeight,frameRate,bitrate);
            videoFile = result;
            videoUri = Uri.fromFile(result);
            Timber.d("Convert result "+result.getAbsolutePath());
        });
        convertTask = convertCompletable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> {
                    showLoading(true,"Convert in progress, please wait...");
                })
                .subscribe(()->{
                    showLoading(false,null);
                    showMessage("Convert success");
                    onComplete.run();
                },throwable -> {
                    throwable.printStackTrace();
                    showLoading(false,null);
                    showMessage(throwable.getMessage());
                });
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(android.R.id.content),message,Snackbar.LENGTH_LONG).show();
    }

    private void showLoading(boolean show,String message) {
        if(show) {
            progressDialog = ProgressDialog.show(this,"Loading",message,true,false);
        } else if(progressDialog!=null&&progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
