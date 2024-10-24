package com.example.mynative;


import java.io.*;
import java.net.Socket;

/**
 * @author gengwenguan
 * @createDate 2024/10/25
 * @description TCP客户端连接要服务器接收服务器下发的h264数据
 */
public class TcpClient {
    public interface Listener{
        void OnRecvH264FromServer(byte[] data);
    }

    public  Listener m_Listener;
    private String m_serverAddress;
    private int m_port = 56050;
    private boolean m_brun = true;
    TcpClient(Listener listener,String serverAddress) {
        m_serverAddress = serverAddress;
        m_Listener = listener;
    }

    public void stop(){
        m_brun = false;
    }
    public void run()
    {
        Socket socket = null;
        try{
            System.out.println("m_serverAddress: " + m_serverAddress);
            socket = new Socket(m_serverAddress, m_port);
            InputStream inputStream = socket.getInputStream();
            DataInputStream dataInputStream = new DataInputStream(inputStream);

            while (m_brun) {
                // 读取前四个字节，获取数据长度
                int dataLength = dataInputStream.readInt();
                System.out.println("Expected data length: " + dataLength);

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

                m_Listener.OnRecvH264FromServer(data); //回调接收的H264数据
                // 将字节数组转换为字符串（假设数据是字符串）
                //String receivedData = new String(data, "UTF-8");
                //System.out.println("Received data: " + receivedData);
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