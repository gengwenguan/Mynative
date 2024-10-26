package com.example.mynative;

import android.view.Surface;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description 网络接收数据解码渲染客户端，从网络接收数据，进行解码渲染显示
 */
public class Terminal implements TcpClient.Listener {
    private TcpClient m_TcpClient;
    private H264DeCodePlay m_DecodePlay;

    //运行终端
    public void run(String serverAddress, Surface surface){
        m_DecodePlay = new H264DeCodePlay(surface);  //运行时创建解码器
        m_TcpClient = new TcpClient(this, serverAddress);

        //调用客户端阻塞运行从网络接收数据直到连接断开
        m_TcpClient.run();

        m_DecodePlay.deInit();  //运行结束销毁编码器
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
