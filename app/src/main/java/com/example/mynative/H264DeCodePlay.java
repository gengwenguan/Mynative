package com.example.mynative;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description 解码H264播放
 */
public class H264DeCodePlay {

    private static final String TAG = "zqf-dev";

    //使用android MediaCodec解码
    private MediaCodec mediaCodec;
    private Surface surface;

    ByteBuffer[] m_inputBuffers;


    H264DeCodePlay(Surface surface) {
        //this.is = is;
        this.surface = surface;
        initMediaCodec();
    }

    public void deInit(){
        mediaCodec.stop();
        // 清理资源的逻辑，如关闭文件、断开网络连接等
        mediaCodec.release();
        Log.e(TAG, "H264DeCodePlay deInit");
    }


    private void initMediaCodec() {
        try {
            //Log.e(TAG, "videoPath " + videoPath);
            //创建解码器 H264的Type为  AAC
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            //创建配置这样配置可以解码540P及以下的分辨率，调整该参数可以解码更高分辨率的视频
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 540, 960);
            //设置解码预期的帧速率【以帧/秒为单位的视频格式的帧速率的键】
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            //配置绑定mediaFormat和surface
            mediaCodec.configure(mediaFormat, surface, null, 0);

            mediaCodec.start();
            m_inputBuffers = mediaCodec.getInputBuffers();
        } catch (IOException e) {
            e.printStackTrace();
            //创建解码失败
            Log.e(TAG, "创建解码失败");
        }
    }

    //解码一帧H264数据
    void decodePlayOneFrame(byte[] data){
        // 查询10000毫秒后，如果dSP芯片的buffer全部被占用，返回-1；存在则大于0
        int inIndex = mediaCodec.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            //根据返回的index拿到可以用的buffer
            ByteBuffer byteBuffer = m_inputBuffers[inIndex];
            //清空缓存
            byteBuffer.clear();
            //开始为buffer填充数据
            byteBuffer.put(data);
            //byteBuffer.put(bytes, startIndex, nextFrameStart - startIndex);
            //填充数据后通知mediacodec查询inIndex索引的这个buffer,
            mediaCodec.queueInputBuffer(inIndex, 0, data.length, 0, 0);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            //mediaCodec 查询 "mediaCodec的输出方队列"得到索引
            int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
            //Log.e(TAG, "outIndex " + outIndex);
            if (outIndex >= 0) {
                //如果surface绑定了，则直接输入到surface渲染并释放
                mediaCodec.releaseOutputBuffer(outIndex, true);
            } else {
                Log.e(TAG, "没有解码成功111");
            }
        }else{
            Log.e(TAG, "dequeueInputBuffer error!!");
        }
    }
}