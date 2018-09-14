package com.liumeng.audiodemolm;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class StreamActivity extends AppCompatActivity implements View.OnClickListener {
    private Button   btn_start;
    private TextView mTvLog;

    private volatile boolean         mIsRecording;//volatile保证多线程内存同步
    private          ExecutorService mExecutorService;
    private          Handler         mMainThreadHandler;

    private byte[] mBuffer;//不能太大
    private static final int BUFFER_SIZE = 2048;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;

    private FileOutputStream mFileOutputStream;
    private AudioRecord      mAudioRecord;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        initView();
        initEvent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，停止后台任务，避免内存泄露
        mExecutorService.shutdownNow();
    }

    private void initEvent() {
        btn_start.setOnClickListener(this);
    }

    private void initView() {
        btn_start = findViewById(R.id.mBtnStart);
        mTvLog = findViewById(R.id.mTvLogs);
        //录音的JNI函数不具备线程安全性，所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        //主线程的Handler
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void onClick(View v) {
        if (mIsRecording) {
            mIsRecording = false;
            btn_start.setText("开始");
        } else {
            mIsRecording = true;
            btn_start.setText("停止");

            //提交后台任务，执行录音逻辑
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!startRecord()) {
                        recordFail();
                    }
                }
            });
        }
    }

    private boolean startRecord() {
        try {
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/sound/" + System.currentTimeMillis() + ".pcm");//获取绝对路径
            mAudioFile.getParentFile().mkdirs();//保证路径是存在的
            mAudioFile.createNewFile();

            //创建文件输入流
            mFileOutputStream = new FileOutputStream(mAudioFile);

            //配置 AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;//从麦克风采集
            int sampleRate = 44100;//采样频率（越高效果越好，但是文件相应也越大，44100是所有安卓系统都支持的采样频率）
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;//单声道输入
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//PCM 16 是所有安卓系统都支持的量化精度，同样也是精度越高音质越好，文件越大
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);//计算 AudioRecord 内部 buffer 最小的大小

            mAudioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE));            //buffer 不能小于最低要求，也不能小于我们每次读取的大小

            //开始录音
            mAudioRecord.startRecording();

            //记录开始时间
            mStartRecordTime = System.currentTimeMillis();

            //循环读取数据，写入输出流中
            while (mIsRecording) {
                int read = mAudioRecord.read(mBuffer, 0, BUFFER_SIZE);//返回长度
                if (read > 0) {
                    //读取成功，写入文件
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {
                    //读取失败，提示用户
                    return false;
                }
            }

            //退出循环，停止录音，释放资源
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            //释放资源
            if (mAudioRecord != null) {
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    }

    /**
     * 结束录音
     **/
    private boolean stopRecord() {
        try {
            //停止录音，关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            //mAudioRecord = null;

            mFileOutputStream.close();

            //记录结束时间
            mStopRecordTime = System.currentTimeMillis();

            //大于3秒才成功，在主线程改变UI
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
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void recordFail() {
        //Toast必须要在主线程才会显示，所有不能直接在这里写
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "录音失败", Toast.LENGTH_SHORT).show();

                //重置录音状态，以及UI状态
                mIsRecording = false;
                btn_start.setText("开始");
            }
        });
    }
}