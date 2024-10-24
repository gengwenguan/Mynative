package com.example.mynative;

import android.view.Surface;

public class Terminal implements TcpClient.Listener {
    private TcpClient m_TcpClient;
    private H264DeCodePlay m_DecodePlay;

    //运行终端
    public void run(String serverAddress, Surface surface){
        m_DecodePlay = new H264DeCodePlay(surface);
        m_TcpClient = new TcpClient(this, serverAddress);

        //调用客户端阻塞运行从网络接收数据直到连接断开
        m_TcpClient.run();
        m_DecodePlay.deInit();
    }

    //停止运行run接口返回
    public void stop(){
        m_TcpClient.stop();
    }

    //网络收到一帧数据进行解码渲染
    @Override
    public void OnRecvH264FromServer(byte[] data){
        m_DecodePlay.decodePlayOneFrame(data);
    }


}
