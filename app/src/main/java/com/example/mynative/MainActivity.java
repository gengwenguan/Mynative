package com.example.mynative;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Button;
import android.view.View;
import android.widget.Toast;

import com.example.mynative.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'mynative' library on application startup.
    static {
        System.loadLibrary("mynative");
    }

    private ActivityMainBinding binding;
    private SurfaceHolder holderPreview;
    private Terminal terminal;
    private TerminalPlayback terminalPlayback;
    private SharedPreferences sharedPreferences;
    private int m_progress;
    private boolean m_progressBarHold = false; //进度条是否按下
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_EDIT_TEXT = "last_input";
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(binding.getRoot());
        holderPreview = binding.surfacePreview.getHolder();
        holderPreview.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {}
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {}
            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {}
        });

        // 加载保存的内容
        String lastInput = sharedPreferences.getString(KEY_EDIT_TEXT, "");
        binding.editTextText.setText(lastInput);

        //创建网络预览终端
        terminal = new Terminal();

        Button btPreview = binding.buttonPreview;
        btPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 保存最后一次EditText 的内容，下次进入程序中可以直接加载
                String inputText = binding.editTextText.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_EDIT_TEXT, inputText);
                editor.apply(); // 提交变更

                if(btPreview.getText().toString().equals("停止")) {
                    Log.e("MAIN", "onClick stop");
                    terminal.stop();        //停止取流
                }else{
                    binding.buttonPlayback.setVisibility(View.INVISIBLE);
                    binding.buttonPlayback.setEnabled(false);
                    Log.e("MAIN", "onClick Preview");
                    btPreview.setText("停止");
                    String ipAddr = binding.editTextText.getText().toString();
                    Thread thread = new Thread(() -> {

                        terminal.run(ipAddr, holderPreview.getSurface());  //在新的线程中获取网络h264数据并解码显示

                        v.post(new Runnable() {
                            @Override
                            public void run() {
                                btPreview.setText("预览");
                                Toast.makeText(MainActivity.this,"预览结束",Toast.LENGTH_SHORT).show();
                                binding.buttonPlayback.setVisibility(View.VISIBLE);
                                binding.buttonPlayback.setEnabled(true);
                                //v.setVisibility(View.VISIBLE);
                            }
                        });
                    });
                    thread.start();
                }
            }
        });

        //创建回放查看终端
        terminalPlayback = new TerminalPlayback(data -> {
            //进度条没有被按下且进度发生变化时才操作ui线程更新进度条进度
            if(!m_progressBarHold && m_progress != data){
                //在UI线程对控件进行操作
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 在这里更新UI控件
                        m_progress = data;
                        binding.progressBar.setProgress(data);
                    }
                });
            }
        });

        Button btPlayback= binding.buttonPlayback;
        btPlayback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 保存最后一次EditText 的内容，下次进入程序中可以直接加载
                String inputText = binding.editTextText.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_EDIT_TEXT, inputText);
                editor.apply(); // 提交变更

                if(btPlayback.getText().toString().equals("停止")) {
                    Log.e("MAIN", "onClick stop");
                    terminalPlayback.stop();        //停止取流
                    setControlsVisibility(false);
                }else{
                    binding.buttonPreview.setVisibility(View.INVISIBLE);
                    binding.buttonPreview.setEnabled(false);
                    setControlsVisibility(true);
                    Log.e("MAIN", "onClick Preview");
                    btPlayback.setText("停止");
                    String ipAddr = binding.editTextText.getText().toString();
                    Thread thread = new Thread(() -> {

                        terminalPlayback.run(ipAddr, holderPreview.getSurface());  //在新的线程中获取网络h264数据并解码显示

                        v.post(new Runnable() {
                            @Override
                            public void run() {
                                btPlayback.setText("回放");
                                setControlsVisibility(false);
                                Toast.makeText(MainActivity.this,"回放结束",Toast.LENGTH_SHORT).show();
                                binding.buttonPreview.setVisibility(View.VISIBLE);
                                binding.buttonPreview.setEnabled(true);
                                //v.setVisibility(View.VISIBLE);
                            }
                        });
                    });
                    thread.start();
                }
            }
        });

        binding.progressBar.setProgress(0);
        SeekBar seekBar = binding.progressBar;
        // 设置进度变化监听器
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 当进度变化时调用
                //Toast.makeText(MainActivity.this, "Progress: " + progress, Toast.LENGTH_SHORT).show();
                m_progress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 当用户开始拖动进度条时调用
                m_progressBarHold = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SendCtrlMessage(m_progress);
                m_progressBarHold = false;
            }
        });

        binding.button2.setOnClickListener(v -> SendCtrlMessage(101));
        binding.button3.setOnClickListener(v -> SendCtrlMessage(102));
        binding.button23.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                if(binding.button23.getText().equals("x3")){
                    terminalPlayback.SetSpeed(3);
                    SendCtrlMessage(107);
                    binding.button23.setText("x1");
                }else{
                    terminalPlayback.SetSpeed(1);
                    SendCtrlMessage(108);
                    binding.button23.setText("x3");
                }

            }
        });
        binding.button1.setOnClickListener(v -> SendCtrlMessage(104));
        binding.button4.setOnClickListener(v -> SendCtrlMessage(105));

        setControlsVisibility(false);

// Example of a call to a native method
//        TextView tv = binding.sampleText;
//        tv.setText(stringFromJNI());
//
//        Button bt = binding.button;
//        bt.setOnClickListener(new View.OnClickListener() {
//                  @Override
//                  public void onClick(View v) {
//                      Log.e("MAIN", "binding.button");
//                      Toast.makeText(MainActivity.this,"方法2：btn4",Toast.LENGTH_SHORT).show();
//                      Intent intent = new Intent(MainActivity.this, test.class);
//                      startActivity(intent);
//                  }
//              });

    }
    //向服务器发送的控制消息：0~100进度条拖动、101快退、102快进、104上一个文件、105下一个文件、107加速、108停止加速
    private void SendCtrlMessage(int message){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] dataToSend = {0};
                dataToSend[0] = (byte)message;
                terminalPlayback.SendData(dataToSend);
            }
        });
        thread.start();
    }

    /**
     * 控制播放控制栏（进度条 + 按钮组）的可见性和可点击状态
     * @param isVisible true=显示并启用, false=隐藏并禁用
     */
    public void setControlsVisibility(boolean isVisible) {
        // 进度条控制
        binding.progressBar.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        binding.progressBar.setEnabled(isVisible);

        // 按钮容器控制（可选：如果需要对容器本身操作）
        binding.controlButtonsContainer.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        binding.controlButtonsContainer.setEnabled(isVisible);

        // 批量控制所有按钮（button1, button2, button23, button3, button4）
        List<Button> buttons = Arrays.asList(
                binding.button1,
                binding.button2,
                binding.button23,
                binding.button3,
                binding.button4
        );
        for (Button button : buttons) {
            button.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
            button.setEnabled(isVisible);
        }
    }
    /**
     * A native method that is implemented by the 'mynative' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}