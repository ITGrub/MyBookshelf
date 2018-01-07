package com.monke.monkeybook.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.monke.monkeybook.MApplication;
import com.monke.monkeybook.R;
import com.monke.monkeybook.view.activity.ReadBookActivity;
import com.monke.mprogressbar.OnProgressListener;

/**
 * Created by GKF on 2018/1/2.
 * 朗读服务
 */

public class ReadAloudService extends Service {
    public static final String mediaButtonAction = "mediaButton";
    public static final String newReadAloudAction = "newReadAloud";
    private static final String doneServiceAction = "doneService";
    private static final String pauseServiceAction = "pauseService";
    private static final String resumeServiceAction = "resumeService";
    private static final String readActivityAction = "readActivity";
    private static final int notificationId = 3222;
    private TextToSpeech textToSpeech;
    private Boolean ttsInitSuccess = false;
    private Boolean speak = false;
    private String content;
    private OnProgressListener progressListener;
    private int nowSpeak;
    private int allSpeak;

    private PendingIntent readPendingIntent;
    private PendingIntent donePendingIntent;
    private PendingIntent pausePendingIntent;
    private PendingIntent resumePendingIntent;

    private AudioManager mAudioManager;
    private ComponentName  mComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        initIntent();
        pauseNotification();

        textToSpeech = new TextToSpeech(this, new TTSListener());

        mAudioManager =(AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mComponent = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        assert action != null;
        switch (action) {
            case doneServiceAction:
                doneService();
                break;
            case pauseServiceAction:
                pauseReadAloud();
                break;
            case resumeServiceAction:
                resumeReadAloud();
                break;
            case mediaButtonAction:
                aloudControl();
                break;
            case newReadAloudAction:
                newReadAloud(intent.getStringExtra("content"));
                break;
            default:
                break;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void newReadAloud(String content) {
        this.content = content;
        speak = false;
        nowSpeak = 0;
        playTTS();
    }

    public void playTTS() {
        if (ttsInitSuccess && !speak) {
            speak = !speak;
            String[] splitSpeech = content.split("\r\n");
            allSpeak = splitSpeech.length;
            for (int i = nowSpeak; i < allSpeak; i++) {
                if (i == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        textToSpeech.speak(splitSpeech[i], TextToSpeech.QUEUE_FLUSH, null, "content");
                    } else {
                        textToSpeech.speak(splitSpeech[i], TextToSpeech.QUEUE_FLUSH, null);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        textToSpeech.speak(splitSpeech[i], TextToSpeech.QUEUE_ADD, null, "content");
                    } else {
                        textToSpeech.speak(splitSpeech[i], TextToSpeech.QUEUE_ADD, null);
                    }
                }
            }
        }
    }

    private void doneService() {
        stopSelf();
        progressListener.moveStopProgress(1);
    }

    private void pauseReadAloud() {
        resumeNotification();
        speak = false;
        textToSpeech.stop();
    }

    private void resumeReadAloud() {
        pauseNotification();
        playTTS();
    }

    private void aloudControl() {
        if (speak) {
            pauseReadAloud();
        } else {
            resumeReadAloud();
        }
    }

    private void initIntent() {
        Intent readIntent = new Intent(this, ReadBookActivity.class);
        readIntent.setAction(readActivityAction);
        readPendingIntent = PendingIntent.getActivity(this, 0, readIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent doneIntent = new Intent(this, this.getClass());
        doneIntent.setAction(doneServiceAction);
        donePendingIntent = PendingIntent.getService(this, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent pauseIntent = new Intent(this, this.getClass());
        pauseIntent.setAction(pauseServiceAction);
        pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent resumeIntent = new Intent(this, this.getClass());
        resumeIntent.setAction(resumeServiceAction);
        resumePendingIntent = PendingIntent.getService(this, 0, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void pauseNotification() {
        //创建 Notification.Builder 对象
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MApplication.channelIReadAloud)
                .setSmallIcon(R.drawable.ic_volume_up_black_24dp)
                .setOngoing(true)
                .setContentTitle(getString(R.string.read_aloud_t))
                .setContentText(getString(R.string.read_aloud_s))
                .setContentIntent(readPendingIntent)
                .addAction(R.drawable.ic_stop_black_24dp, getString(R.string.stop), donePendingIntent)
                .addAction(R.drawable.ic_pause_black_24dp, getString(R.string.pause), pausePendingIntent);
        //发送通知
        Notification notification = builder.build();
        startForeground(notificationId, notification);
    }

    private void resumeNotification() {
        //创建 Notification.Builder 对象
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MApplication.channelIReadAloud)
                .setSmallIcon(R.drawable.ic_volume_up_black_24dp)
                .setOngoing(false)
                .setContentTitle(getString(R.string.read_aloud_t))
                .setContentText(getString(R.string.read_aloud_s))
                .setContentIntent(readPendingIntent)
                .addAction(R.drawable.ic_stop_black_24dp, getString(R.string.stop), donePendingIntent)
                .addAction(R.drawable.ic_play_arrow_black_24dp, getString(R.string.resume), resumePendingIntent);
        //发送通知
        Notification notification = builder.build();
        startForeground(notificationId, notification);
    }

    public void setOnProgressListener(OnProgressListener onProgressListener){
        progressListener = onProgressListener;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    public class MyBinder extends Binder {
        public ReadAloudService getService() {
            return ReadAloudService.this;
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
        textToSpeech.stop();
        textToSpeech.shutdown();
        textToSpeech = null;
    }

    private final class TTSListener implements TextToSpeech.OnInitListener {
        @Override
        public void onInit(int i) {
            if (i == TextToSpeech.SUCCESS) {
                textToSpeech.setOnUtteranceProgressListener(new ttsUtteranceListener());
                ttsInitSuccess = true;
                playTTS();
            }
        }
    }

    private class ttsUtteranceListener extends UtteranceProgressListener {

        @Override
        public void onStart(String s) {
        }

        @Override
        public void onDone(String s) {
            nowSpeak = nowSpeak + 1;
            if (nowSpeak == allSpeak) {
                progressListener.setDurProgress(1);
            }
        }

        @Override
        public void onError(String s) {

        }
    }

}