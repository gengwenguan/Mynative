package com.example.mynative;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.widget.Toast;

import com.example.mynative.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'mynative' library on application startup.
    static {
        System.loadLibrary("mynative");
    }

    private ActivityMainBinding binding;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private EditText edittext;
    private Terminal terminal;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_EDIT_TEXT = "last_input";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        surface = binding.surfaceView;
        holder = surface.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

            }
        });

        // 加载保存的内容
        String lastInput = sharedPreferences.getString(KEY_EDIT_TEXT, "");
        edittext = binding.editTextText;
        edittext.setText(lastInput);

        terminal = new Terminal();

        Button bt2 = binding.button2;
        bt2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 保存最后一次EditText 的内容，下次进入程序中可以直接加载
                String inputText = edittext.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_EDIT_TEXT, inputText);
                editor.apply(); // 提交变更

                if(bt2.getText().toString() == "stop") {
                    Log.e("MAIN", "onClick stop");
                    bt2.setText("connect"); //变更按钮内容
                    terminal.stop();        //停止取流
                }else{
                    Log.e("MAIN", "onClick connect");
                    bt2.setText("stop");
                    String ipAddr = edittext.getText().toString();
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {

                            terminal.run(ipAddr, holder.getSurface());  //在新的线程中获取网络h264数据并解码显示

                            v.post(new Runnable() {
                                @Override
                                public void run() {
                                    bt2.setText("connect");
                                    Toast.makeText(MainActivity.this,"播放结束",Toast.LENGTH_LONG).show();
                                    //v.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    });
                    thread.start();
                }

            }
        });


        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText(stringFromJNI());

        Button bt = binding.button;
        bt.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View v) {
                      Log.e("MAIN", "binding.button");
                      Toast.makeText(MainActivity.this,"方法2：btn4",Toast.LENGTH_SHORT).show();
                      Intent intent = new Intent(MainActivity.this, test.class);
                      startActivity(intent);
                  }
              });

    }

    /**
     * A native method that is implemented by the 'mynative' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}