package dev.illwiz.videotrimmer.trimmer;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.telegram.messenger.CustomVideoTimelinePlayView;
import org.telegram.messenger.VideoTimelinePlayView;

import java.io.File;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dev.illwiz.videotrimmer.R;
import dev.illwiz.videotrimmer.utils.Prop;
import dev.illwiz.videotrimmer.utils.Utils;
import timber.log.Timber;

public class TelegramActivity extends AppCompatActivity {
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

    private Uri videoUri;
    private File videoFile;
    private float videoDuration;
    private long estimatedDuration,trimStartTime,trimEndTime;
    private long originalSize,estimatedSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trimmer_telegram);
        ButterKnife.bind(this);
        initialize();
    }

    @OnClick(R.id.videoViewWrapper)
    public void videoViewWrapper() {
        if(videoView.isPlaying()) {
            videoView.pause();
            playBtn.setVisibility(View.VISIBLE);
        } else {
            videoView.start();
            playBtn.setVisibility(View.GONE);
        }
    }

    private void initialize() {
        videoUri = getIntent().getParcelableExtra(Prop.MAIN_OBJ);
        String path = Utils.getPath(this,videoUri);
        if(path==null) {
            return;
        }
        videoFile = new File(path);
        if(videoFile.exists()) {
            originalSize = videoFile.length();
        }

        timelineView.setMinProgressDiff(0.3f);
        timelineView.setMaxProgressDiff(0.8f);
        timelineView.setDelegate(new CustomVideoTimelinePlayView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                Timber.d("onLeftProgressChanged "+progress);
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
                videoView.seekTo((int) (videoDuration * progress));
                timelineView.setProgress(0);
                updateVideoInfo();
            }

            @Override
            public void onRightProgressChanged(float progress) {
                Timber.d("onRightProgressChanged "+progress);
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
                videoView.seekTo((int) (videoDuration * progress));
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
            }
        });
        timelineView.setVideoPath(videoUri);

        videoView.setOnPreparedListener(mediaPlayer -> {
            videoDuration = mediaPlayer.getDuration();
            playBtn.setVisibility(View.VISIBLE);
            updateVideoInfo();
        });
        //videoView.setMediaController(new MediaController(this));
        videoView.setVideoURI(videoUri);
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
}
