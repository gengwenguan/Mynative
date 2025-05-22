package com.example.mynative;


import java.io.*;
import java.net.Socket;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description TCP客户端连接要服务器接收服务器下发的h264数据
 */
public class TcpClient {
    //向上回调接收到的h264数据
    public interface Listener{
        void OnRecvMediaDataFromServer(byte[] data);
    }

    public  Listener m_Listener;
    private String m_serverAddress;
    private int m_port;
    private boolean m_brun = true;
    private Socket socket = null;
    TcpClient(Listener listener,String serverAddress, int port) {
        m_serverAddress = serverAddress;
        m_port = port;
        m_Listener = listener;
    }

    //
    public void SendData(byte[] dataToSend){
        try {
            if(socket != null && !socket.isClosed()){
                socket.getOutputStream().write(dataToSend); // 发送 byte[] 数据
                socket.getOutputStream().flush(); // 确保数据被发送
            }else{
                System.err.println("socket.isClosed()");
            }

        } catch (IOException e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //停止tcp客户端数据接收
    public void stop(){
        m_brun = false;
    }

    //循环运行直到上层主动调用stop()接口，该接口返回
    public void run()
    {

        try{
            System.out.println("m_serverAddress: " + m_serverAddress);
            socket = new Socket(m_serverAddress, m_port);
            InputStream inputStream = socket.getInputStream();
            DataInputStream dataInputStream = new DataInputStream(inputStream);

            while (m_brun) {
                // 读取前四个字节，获取数据长度
                int dataLength = dataInputStream.readInt();
                //System.out.println("Expected data length: " + dataLength);

                // 读取实际数据
                byte[] data = new byte[dataLength];
                int totalRead = 0;
                while (totalRead < dataLength) {
                    int bytesRead = dataInputStream.read(data, totalRead, dataLength - totalRead);
                    if (bytesRead == -1) {
                        throw new IOException("Unexpected end of stream");
                    }
                    totalRead += bytesRead;
                }

                m_Listener.OnRecvMediaDataFromServer(data); //回调接收的H264数据

            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }finally {
            // 确保最后关闭Socket
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                    System.out.println("Socket closed.");
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }


}