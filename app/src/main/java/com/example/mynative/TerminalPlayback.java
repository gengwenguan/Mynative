package com.example.mynative;

import android.view.Surface;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description 网络接收数据解码渲染回放客户端，接收服务器上录制的视频数据，进行解码渲染显示
 */
public class TerminalPlayback implements TcpClient.Listener {
    //向上回调接视频文件播放进度
    public interface Listener{
        void OnProgress(int data);
    }

    private TcpClient m_TcpClient = null;
    private H264DeCodePlay m_DecodePlay = null;
    private Listener m_Listener;

    TerminalPlayback(Listener listener) {
        m_Listener = listener;
    }

    //运行终端
    public void run(String serverAddress, Surface surface){
        m_DecodePlay = new H264DeCodePlay(surface);  //运行时创建解码器
        m_TcpClient = new TcpClient(this, serverAddress, 56060);

        //调用客户端阻塞运行从网络接收数据直到连接断开
        m_TcpClient.run();

        m_DecodePlay.deInit();  //运行结束销毁编码器
    }

    //向连接的服务器发送控制信令数据
    public void SendData(byte[] dataToSend) {
        if(m_TcpClient == null){
            return;
        }
        m_TcpClient.SendData(dataToSend);
    }
    //停止运行run接口返回
    public void stop(){
        if(m_TcpClient == null){
            return;
        }
        m_TcpClient.stop();
    }

    //网络收到一帧数据进行解码渲染
    @Override
    public void OnRecvH264FromServer(byte[] data){
        if(m_DecodePlay == null){
            return;
        }
        // 提取进度数据
        int progress = data[0];
        // 回调进度数据
        if (m_Listener != null) {
            m_Listener.OnProgress(progress);
        }
        // 提取剩余数据（从 data[1] 开始）
        byte[] remainingData = new byte[data.length - 1];
        System.arraycopy(data, 1, remainingData, 0, remainingData.length);

        // 解码和播放一帧
        m_DecodePlay.decodePlayOneFrame(remainingData);
    }


}
