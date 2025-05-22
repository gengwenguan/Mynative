package com.example.mynative;

import android.util.Log;
import android.view.Surface;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description 网络接收数据解码渲染客户端，从网络接收数据，进行解码渲染显示
 */
public class Terminal implements TcpClient.Listener {
    private TcpClient m_TcpClient;
    private H264DeCodePlay m_DecodePlay;
    private OpusMediaCodecPlayer opusPlayer;

    //运行终端
    public void run(String serverAddress, Surface surface){
        m_DecodePlay = new H264DeCodePlay(surface);  //运行时创建解码器
        m_TcpClient = new TcpClient(this, serverAddress, 56050);
        try {
            opusPlayer = new OpusMediaCodecPlayer();
            opusPlayer.initialize();
            opusPlayer.start();
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to initialize player", e);
        }


        //调用客户端阻塞运行从网络接收数据直到连接断开
        m_TcpClient.run();

        m_DecodePlay.deInit();  //运行结束销毁编码器
    }

    //停止运行run接口返回
    public void stop(){
        opusPlayer.stop();
        //opusPlayer.release();
        m_TcpClient.stop();
    }

    //网络收到音频或者视频数据
    @Override
    public void OnRecvMediaDataFromServer(byte[] data){
        // 提取进度数据
        int flag = data[0];
        // 提取剩余数据（从 data[1] 开始）
        byte[] remainingData = new byte[data.length - 1];
        System.arraycopy(data, 1, remainingData, 0, remainingData.length);
        if(flag == 0){
            opusPlayer.feedInput(remainingData);
        } else if (flag == 1) {
            m_DecodePlay.decodePlayOneFrame(remainingData);
        }else{
            System.err.println("unknow flag=" + flag);
        }
    }
}
