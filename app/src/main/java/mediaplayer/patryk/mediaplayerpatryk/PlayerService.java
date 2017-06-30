package mediaplayer.patryk.mediaplayerpatryk;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.socks.library.KLog;

import java.util.List;

import hybridmediaplayer.HybridMediaPlayer;

public class PlayerService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener {

    private static final String ACTION_START_PLAYLIST = "mediaplayer.patryk.mediaplayerpatryk.action.DOWNLOAD";

    private static final String EXTRA_PARAM1 = "mediaplayer.patryk.mediaplayerpatryk.PARAM1";
    private static final String EXTRA_PARAM2 = "mediaplayer.patryk.mediaplayerpatryk.PARAM2";


    public final static String LOADING_ACTION = "LOADING_ACTION";
    public final static String LOADED_ACTION = "LOADED_ACTION";
    public final static String GUI_UPDATE_ACTION = "GUI_UPDATE_ACTION";
    public final static String COMPLETE_ACTION = "COMPLETE_ACTION";
    public final static String PLAY_ACTION = "PLAY_ACTION";
    public final static String PAUSE_ACTION = "PAUSE_ACTION";
    public final static String NEXT_ACTION = "NEXT_ACTION";
    public final static String PREVIOUS_ACTION = "PREVIOUS_ACTION";

    public final static String EXTRA_BUNDLE = "EXTRA_BUNDLE";
    public final static String TOTAL_TIME_VALUE_EXTRA = "TOTAL_TIME_VALUE_EXTRA";


    private PlayerServiceBinder binder = new PlayerServiceBinder();

    private HybridMediaPlayer player;
    private Playlist playlist;
    private Song currentSong;
    int songPosition;

    private boolean isUpdatingThread;
    private boolean isPrepared;

    private MediaSessionCompat mediaSessionCompat;
    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        // TODO: 30.06.2017
        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            super.onCommand(command, extras, cb);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaSession();
    }

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(this, MediaButtonReceiver.class);
        mediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "MediaTAG", mediaButtonReceiver, null);

        mediaSessionCompat.setCallback(mediaSessionCallback);
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        mediaSessionCompat.setMediaButtonReceiver(pendingIntent);

        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        mediaSessionCompat.setActive(true);
        setSessionToken(mediaSessionCompat.getSessionToken());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String playlistName = intent.getStringExtra(EXTRA_PARAM1);
            int songPosition = intent.getIntExtra(EXTRA_PARAM1, 0);
            handleActionStartPlaylist(playlistName, songPosition);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleActionStartPlaylist(String playlistName, int songPos) {
        playlist = getPlaylist(playlistName);
        songPosition = songPos;
        currentSong = playlist.getSongs().get(songPosition);
        createPlayer();

        isPrepared = false;
        killPlayer();
        player = HybridMediaPlayer.getInstance(this);

        player.setDataSource(currentSong.getUrl());

        player.setOnErrorListener(new HybridMediaPlayer.OnErrorListener() {
            @Override
            public void onError(Exception e, HybridMediaPlayer hybridMediaPlayer) {
                handleError(e);
            }

        });

        player.setOnPreparedListener(new HybridMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(HybridMediaPlayer player) {
                sendBroadcastWithAction(LOADED_ACTION, null);

                Bundle extraBundle = new Bundle();
                extraBundle.putInt(TOTAL_TIME_VALUE_EXTRA, player.getDuration());
                sendBroadcastWithAction(GUI_UPDATE_ACTION, extraBundle);

                isPrepared = true;

                play();

                updateMediaSessionMetaData();
            }
        });

        player.setOnCompletionListener(new HybridMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(HybridMediaPlayer player) {
                if (songPosition == playlist.getSongs().size() - 1) {
                    sendBroadcastWithAction(COMPLETE_ACTION, null);
                    pause();
                    isUpdatingThread = false;
                    KLog.i("KONIEC PLAYLISTY");

                } else {
                    KLog.i("KONIEC PIOSENKI");
                    sendBroadcastWithAction(COMPLETE_ACTION, null);
                    isUpdatingThread = false;
                    nextSong();
                }
            }
        });
    }

    private void nextSong() {
        KLog.i("next song");
        songPosition++;

        if (songPosition >= playlist.getSongs().size()) {
            songPosition = playlist.getSongs().size() - 1;
            return;
        }

        currentSong = playlist.getSongs().get(songPosition);
        createPlayer();

        sendBroadcastWithAction(NEXT_ACTION, null);

        loadCover();
    }

    private void loadCover() {

    }

    private void pause() {
        KLog.d("pause");
        Intent updateIntent = new Intent();
        sendBroadcastWithAction(PAUSE_ACTION, null);
        sendBroadcast(updateIntent);
        isUpdatingThread = false;
        if (player != null) {
            player.pause();
        }

        makeNotification();
        setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    private void makeNotification() {
        // TODO: 30.06.2017  
    }


    private void play() {
        if (!isPrepared) {
            createPlayer();
            return;
        }

        if (!retrieveAudioFocus())
            return;

        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        mediaSessionCompat.setActive(true);


        player.play();

        startUiUpdateThread();

        Intent updateIntent = new Intent();
        updateIntent.setAction(PLAY_ACTION);
        sendBroadcast(updateIntent);

        makeNotification();
    }

    private void setMediaPlaybackState(int statePlaying) {
        // TODO: 30.06.2017  
    }

    private void startUiUpdateThread() {
        // TODO: 30.06.2017  
    }

    private void updateMediaSessionMetaData() {
        // TODO: 30.06.2017  
    }

    private void handleError(Exception exception) {
        // TODO: 30.06.2017 obsługa błędów
    }
    


    private void createPlayer() {
        if (currentSong == null || currentSong.getUrl() == null) {
            stopSelf();
            return;
        }

        sendBroadcastWithAction(LOADING_ACTION, null);
    }

    private void sendBroadcastWithAction(String loadingAction, Bundle bundle) {
        Intent updateIntent = new Intent();
        updateIntent.setAction(loadingAction);
        updateIntent.putExtra(EXTRA_BUNDLE, bundle);
        sendBroadcast(updateIntent);
    }

    private void killPlayer() {
        abandonAudioFocus();
        isUpdatingThread = false;
        if (player != null) {
            try {
                player.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
    }

    private boolean retrieveAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        return result == AudioManager.AUDIOFOCUS_GAIN;
    }

    private Playlist getPlaylist(String playlistName) {
        // TODO: 30.06.2017 wczytywanie playlisty z realma
        return null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: 30.06.2017 odkomentować 
//        if (SERVICE_INTERFACE.equals(intent.getAction())) {
//            return super.onBind(intent);
//        }
        return binder;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        // TODO: 30.06.2017 android auto
        return null;
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        // TODO: 30.06.2017 android auto

    }

    public static void startPlaylist(Context context, String playlistName, int songPosition) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_START_PLAYLIST);
        intent.putExtra(EXTRA_PARAM1, playlistName);
        intent.putExtra(EXTRA_PARAM2, songPosition);
        context.startService(intent);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        // TODO: 30.06.2017
    }

    public class PlayerServiceBinder extends Binder {
        public PlayerServiceBinder getService() {
            return PlayerServiceBinder.this;
        }
    }
}
