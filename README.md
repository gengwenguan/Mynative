# H264视频码流取流解码显示程序
该安卓工程为[https://github.com/gengwenguan/libmaix/tree/release](https://github.com/gengwenguan/libmaix/tree/release/examples/camera)
采集编码例程提供安卓端视频取流解码渲染例程，视频通过H264编码传输大大节省带宽占用，
该工程接收网络数据的格式为4字节h264数据长度后跟实际h264负载，理论上该程序可以取任意端的流
只要保证
        //先将一帧H264数据的长度发送给客户端，长度为4个字节
        ret = send(*it, &networkNumber, sizeof(networkNumber), 0);  
        if(ret>0){
            //再将实际的H264数据发送给客户端
            ret = send(*it, pData, nLen, 0);                         
        }
这样的方式将h264发送过来，该程序都能够正常解码播放，安卓工程中使用MediaCodec进行解码

