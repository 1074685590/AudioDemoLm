package com.liumeng.audiodemolm;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Gin on 2017/11/28.
 */

public class FileActivity extends AppCompatActivity {
    private TextView mTvLog, mTvPressToSay;

    private ExecutorService mExecutorService;
    private MediaRecorder   mMediaRecorder;
    private File            mAudioFile;
    private long            mStartRecordTime, mStopRecordTime;

    private Handler mMainThreadHandler;
    private MediaPlayer mMediaPlayer;
    private boolean mIsPlaying;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        initView();
        initEvent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，停止后台任务，避免内存泄露
        mExecutorService.shutdownNow();
        releaseRecorder();
    }

    private void initView() {
        mTvLog = findViewById(R.id.mTvLog);
        mTvPressToSay = findViewById(R.id.mTvPressToSay);
        //录音的JNI函数不具备线程安全性，所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        //主线程的Handler
        mMainThreadHandler = new Handler(Looper.getMainLooper());

    }

    public void onOroff(View view) {
        //检查当前状态
        if(mAudioFile != null&&!mIsPlaying){
            //设置当前的播放状态
            mIsPlaying=true;

            //提交后台任务，播放
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    private void initEvent() {
        //按下说话，释放发送，所以我们不能使用OnClickListener
        //用OnTouchListener
        mTvPressToSay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //根据不同的touch action做出相应的处理
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopRecord();
                        break;
                }
                return true;
            }
        });
    }

    //开始录音
    private void startRecord() {
        mTvPressToSay.setText("正在说话...");
        mTvPressToSay.setBackgroundColor(Color.GRAY);
        //提交后台任务，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前录音的 MediaRecorder
                releaseRecorder();
                //执行录音逻辑，如果失败提示用户
                if (!doStart()) {
                    recordFail();
                }
            }
        });
    }

    //结束录音
    private void stopRecord() {
        mTvPressToSay.setText("按住说话");
        mTvPressToSay.setBackgroundColor(Color.WHITE);
        //提交后台任务，执行停止逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //执行停止录音逻辑，失败就提醒用户
                if (!doStop()) {
                    recordFail();
                }
                //释放 MediaRecorder
                releaseRecorder();
            }
        });
    }

    /**
     * 启动录音
     **/
    private boolean doStart() {
        try {
            //创建 MediaRecorder
            mMediaRecorder = new MediaRecorder();
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/sound/" + System.currentTimeMillis() + ".m4a");//获取绝对路径
            mAudioFile.getParentFile().mkdirs();//保证路径是存在的
            mAudioFile.createNewFile();

            //配置 MediaRecorder
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//从麦克风采集
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//保存为MP4格式
            mMediaRecorder.setAudioSamplingRate(44100);//采样频率（越高效果越好，但是文件相应也越大，44100是所有安卓系统都支持的采样频率）
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//编码格式，AAC是通用的格式
            mMediaRecorder.setAudioEncodingBitRate(96000);//编码频率，96000是音质较好的频率
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            //开始录音
            mMediaRecorder.prepare();//准备开始录音
            mMediaRecorder.start();//开始录音

            //记录开始录音时间，统计时长
            mStartRecordTime = System.currentTimeMillis();

            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();//捕获异常，避免闪退 返回false 提醒用户失败
            return false;
        }
    }

    /**
     * 停止录音
     **/
    private boolean doStop() {
        //停止录音
        try {
            mMediaRecorder.stop();

            //记录停止时间
            mStopRecordTime = System.currentTimeMillis();

            //只接受超过3秒的录音，在UI上显示出来
            final int times = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if (times > 3) {
                //在主线程改变UI，显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvLog.setText(mTvLog.getText() + "\n录音成功 " + times + "秒");
                    }
                });
                //停止成功
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 释放 MediaRecorder
     **/
    private void releaseRecorder() {
        //检查 MediaRecorder 不为空
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 录音失败
     **/
    private void recordFail() {
        mAudioFile = null;
        //Toast必须要在主线程才会显示，所有不能直接在这里写
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
            }
        });
    }


    //播放逻辑
    private void doPlay(File mAudioFile) {
        //配置播放器 MediaPlayer
        mMediaPlayer = new MediaPlayer();
        try{
            //设置声音文件
            mMediaPlayer.setDataSource(mAudioFile.getAbsolutePath());

            //监听回调
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //播放结束，释放播放器
                    stopPlay();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //提示
                    playFail();

                    //释放播放器
                    stopPlay();

                    //错误已经处理
                    return true;
                }
            });

            mMediaPlayer.setVolume(1,1);//配置音量（范围0~1,0为静音，1为原音量）
            mMediaPlayer.setLooping(false);//是否循环
            mMediaPlayer.prepare();//准备
            mMediaPlayer.start();//开始
        }catch (RuntimeException | IOException e){
            //异常处理，防止闪退
            e.printStackTrace();
            playFail();
            //释放播放器
            stopPlay();
        }
    }

    //停止逻辑
    private void stopPlay() {
        //重置状态
        mIsPlaying=false;

        //释放播放器
        if(mMediaPlayer != null){
            //重置监听器，防止内存泄漏
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);

            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer=null;
        }
    }

    //提醒用户播放失败
    private void playFail() {
        //在主线程Toast提示
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"播放失败",Toast.LENGTH_SHORT).show();
            }
        });
    }

}