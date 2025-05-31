package com.example.mynative;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.PlaybackParams;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class OpusMediaCodecPlayer {
    private static final String TAG = "OpusDecoder";

    // Opus格式参数
    //private static final String MIME_TYPE = "audio/opus";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_COUNT = 1; // 单声道
    private static final int FIXED_PACKET_SIZE = 1920; // 固定20ms数据量(48000*0.02*2)
    private static final int BYTES_PER_SAMPLE = 2;     // 16-bit PCM
    //private static final int FRAME_SIZE = 960; // 20ms帧大小 (48000 * 0.02 = 960)

    private MediaCodec decoder;
    private AudioTrack audioTrack;
    private boolean isRunning = false;
    private final LinkedBlockingQueue<byte[]> pcmQueue = new LinkedBlockingQueue<>();
    private Thread playbackThread;
    private int totalPcmSize = 0;

    public void initialize() throws Exception {
        // 创建MediaCodec解码器
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);

        // 配置MediaFormat
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_OPUS);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);

        // 对于Opus，需要设置MAX_INPUT_SIZE
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4000); // 足够大的缓冲区
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        byte[] csd0bytes = {
                // Opus
                0x4f, 0x70, 0x75, 0x73,
                // Head
                0x48, 0x65, 0x61, 0x64,
                // Version
                0x01,
                // Channel Count
                0x01,
                // Pre skip
                0x00, 0x00,
                // Input Sample Rate (Hz), eg: 48000
                (byte) 0x80, (byte) 0xbb, 0x00, 0x00,
                // Output Gain (Q7.8 in dB)
                0x00, 0x00,
                // Mapping Family
                0x00};
        byte[] csd1bytes = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] csd2bytes = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        ByteBuffer csd0 = ByteBuffer.wrap(csd0bytes);
        format.setByteBuffer("csd-0", csd0);
        ByteBuffer csd1 = ByteBuffer.wrap(csd1bytes);
        format.setByteBuffer("csd-1", csd1);
        ByteBuffer csd2 = ByteBuffer.wrap(csd2bytes);
        format.setByteBuffer("csd-2", csd2);

        // 配置解码器
        decoder.configure(format, null, null, 0);

        // 创建AudioTrack用于播放
        int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                bufferSize * 3,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);

        // 启动播放线程
        playbackThread = new Thread(this::fixedUniformCompressionPlayback);


        Log.d(TAG, "Opus decoder initialized");
    }

    public void start() {
        if (isRunning) {
            return;
        }

        decoder.start();
        audioTrack.play();
        playbackThread.start();
        isRunning = true;

        Log.d(TAG, "Opus decoder started");
    }

    public void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        try {
            if (playbackThread != null) {
                //playbackThread.interrupt();
                playbackThread.join();
            }
            decoder.stop();
            decoder.release();
            audioTrack.stop();
            audioTrack.release();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping decoder", e);
        } finally {

            decoder = null;
            audioTrack = null;
        }

        Log.d(TAG, "Opus decoder stopped");
    }

    public void feedInput(byte[] opusData) {
        if (!isRunning || opusData == null || opusData.length == 0) {
            return;
        }

        try {
            // 输入缓冲区
            int inputBufferId = decoder.dequeueInputBuffer(10000);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                assert inputBuffer != null;
                inputBuffer.clear();
                inputBuffer.put(opusData);
                decoder.queueInputBuffer(
                        inputBufferId,
                        0,
                        opusData.length,
                        0,
                        0);
            }

            // 输出缓冲区
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10000);

            while (outputBufferId >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);

                // 将解码后的PCM数据写入AudioTrack播放
                byte[] pcmData = new byte[bufferInfo.size];
                assert outputBuffer != null;
                outputBuffer.get(pcmData);

                pcmQueue.put(pcmData);

                //audioTrack.write(pcmData, 0, pcmData.length);

                decoder.releaseOutputBuffer(outputBufferId, false);
                outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Decoding error", e);
        }
    }

    //高级统一压缩播放
    private void fixedUniformCompressionPlayback() {
        Queue<byte[]> packetBuffer = new ArrayDeque<>();

        while (isRunning || !pcmQueue.isEmpty()) {
            try {
                // 1. 获取待处理包
                pcmQueue.drainTo(packetBuffer);
                if (packetBuffer.isEmpty()) {
                    Thread.yield();
                    continue;
                }

                // 2. 播放所有缓冲的包
                for (byte[] packet : packetBuffer) {
                    totalPcmSize += packet.length;
                    audioTrack.write(packet, 0, packet.length);
                }
                int playbackHeadPosition = audioTrack.getPlaybackHeadPosition();
                int diff = totalPcmSize - playbackHeadPosition*2;
                if(diff > 9000){
                    audioTrack.setPlaybackParams(new PlaybackParams().setSpeed(1.5f));
                    Log.d(TAG, "1.5f diff:" +diff );
                } else if (diff < 2500) {
                    audioTrack.setPlaybackParams(new PlaybackParams().setSpeed(1.0f));
                    Log.d(TAG, "1.0f diff:" +diff );
                }
                //Log.d(TAG, "totalPcmSize:"+totalPcmSize+" playbackHeadPosition:" + playbackHeadPosition + " diff:" + diff );

                packetBuffer.clear();
            } catch (Exception e) {
                Log.e(TAG, "Playback error", e);
            }
        }
    }
}

//package com.example.mynative;
//import android.media.AudioFormat;
//import android.media.AudioTrack;
//import android.media.MediaCodec;
//import android.media.MediaFormat;
//import android.util.Log;
//import java.nio.ByteBuffer;
//
//public class OpusMediaCodecPlayer {
//    private static final String TAG = "OpusPlayer";
//    private static final String MIME_TYPE = "audio/opus";
//    private static final int SAMPLE_RATE = 48000;
//    private static final int CHANNEL_COUNT = 1; // 单声道
//    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
//            SAMPLE_RATE,
//            AudioFormat.CHANNEL_OUT_MONO,
//            AudioFormat.ENCODING_PCM_16BIT);
//
//    private MediaCodec mediaCodec;
//    private AudioTrack audioTrack;
//    private boolean isPlaying = false;
//    private Thread playbackThread;
//
//    public void initialize() throws Exception {
//        // 1. 创建并配置MediaCodec解码器
//        mediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);
//
//        MediaFormat format = new MediaFormat();
//        format.setString(MediaFormat.KEY_MIME, MIME_TYPE);
//        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
//        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
//        // 对于Opus，可能需要设置以下参数
//        format.setInteger(MediaFormat.KEY_IS_ADTS, 0);
//        format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
//
//        // 2. 配置解码器
//        mediaCodec.configure(format, null, null, 0);
//
//        // 3. 创建AudioTrack用于播放PCM数据
//        audioTrack = new AudioTrack.Builder()
//                .setAudioFormat(new AudioFormat.Builder()
//                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                        .setSampleRate(SAMPLE_RATE)
//                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
//                        .build())
//                .setBufferSizeInBytes(BUFFER_SIZE)
//                .setTransferMode(AudioTrack.MODE_STREAM)
//                .build();
//    }
//
//    public void start() {
//        if (isPlaying) return;
//
//        isPlaying = true;
//        mediaCodec.start();
//        audioTrack.play();
//
//        // 启动播放线程
//        playbackThread = new Thread(this::playbackLoop);
//        playbackThread.start();
//    }
//
//    public void stop() {
//        isPlaying = false;
//
//        try {
//            if (playbackThread != null) {
//                playbackThread.join(500);
//            }
//        } catch (InterruptedException e) {
//            Log.w(TAG, "Playback thread interrupted", e);
//        }
//
//        if (mediaCodec != null) {
//            mediaCodec.stop();
//        }
//
//        if (audioTrack != null) {
//            audioTrack.stop();
//        }
//    }
//
//    public void release() {
//        stop();
//
//        if (mediaCodec != null) {
//            mediaCodec.release();
//            mediaCodec = null;
//        }
//
//        if (audioTrack != null) {
//            audioTrack.release();
//            audioTrack = null;
//        }
//    }
//
//    public void feedInput(byte[] opusData) {
//        if (!isPlaying || mediaCodec == null) return;
//
//        try {
//            // 获取输入缓冲区
//            int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
//            if (inputBufferIndex >= 0) {
//                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
//                if (inputBuffer != null) {
//                    inputBuffer.clear();
//                    inputBuffer.put(opusData);
//                    // 提交到解码器
//                    mediaCodec.queueInputBuffer(
//                            inputBufferIndex,
//                            0,
//                            opusData.length,
//                            0, // presentationTimeUs
//                            0); // flags
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error feeding input data", e);
//        }
//    }
//
//    private void playbackLoop() {
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//        while (isPlaying) {
//            try {
//                // 获取解码后的输出
//                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
//
//                if (outputBufferIndex >= 0) {
//                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
//                    if (outputBuffer != null && bufferInfo.size > 0) {
//                        // 将PCM数据写入AudioTrack播放
//                        byte[] pcmData = new byte[bufferInfo.size];
//                        outputBuffer.get(pcmData);
//                        audioTrack.write(pcmData, 0, pcmData.length);
//                    }
//                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                    // 输出格式变化（通常第一次调用时发生）
//                    MediaFormat newFormat = mediaCodec.getOutputFormat();
//                    Log.d(TAG, "Output format changed: " + newFormat);
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error in playback loop", e);
//                //break;
//            }
//        }
//    }
//}