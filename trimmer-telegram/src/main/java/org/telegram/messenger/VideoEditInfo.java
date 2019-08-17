package org.telegram.messenger;

import java.io.File;

public class VideoEditInfo {
    public static final int COMPRESS_240 = 0;
    public static final int COMPRESS_360 = 1;
    public static final int COMPRESS_480 = 2;
    public static final int COMPRESS_720 = 3;
    public static final int COMPRESS_1080 = 4;
    private File src;
    private File dst;
    private int trimStartUs, trimEndUs; // micro second
    private int resultWidth,resultHeight;
    private int frameRate;
    private int bitrate;

    public VideoEditInfo(File src, File dst, int trimStartUs, int trimEndUs) {
        this.src = src;
        this.dst = dst;
        this.trimStartUs = trimStartUs;
        this.trimEndUs = trimEndUs;
    }

    public VideoEditInfo(File src, File dst, int resultWidth, int resultHeight, int frameRate, int bitrate) {
        this.src = src;
        this.dst = dst;
        this.resultWidth = resultWidth;
        this.resultHeight = resultHeight;
        this.frameRate = frameRate;
        this.bitrate = bitrate;
    }

    public File getSrc() {
        return src;
    }

    public void setSrc(File src) {
        this.src = src;
    }

    public File getDst() {
        return dst;
    }

    public void setDst(File dst) {
        this.dst = dst;
    }

    public int getTrimStartUs() {
        return trimStartUs;
    }

    public void setTrimStartUs(int trimStartUs) {
        this.trimStartUs = trimStartUs;
    }

    public int getTrimEndUs() {
        return trimEndUs;
    }

    public void setTrimEndUs(int trimEndUs) {
        this.trimEndUs = trimEndUs;
    }

    public int getResultWidth() {
        return resultWidth;
    }

    public void setResultWidth(int resultWidth) {
        this.resultWidth = resultWidth;
    }

    public int getResultHeight() {
        return resultHeight;
    }

    public void setResultHeight(int resultHeight) {
        this.resultHeight = resultHeight;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
}
