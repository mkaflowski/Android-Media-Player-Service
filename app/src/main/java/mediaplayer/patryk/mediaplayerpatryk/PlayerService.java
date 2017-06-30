package mediaplayer.patryk.mediaplayerpatryk;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.socks.library.KLog;

import java.util.ArrayList;
import java.util.List;

import hybridmediaplayer.HybridMediaPlayer;

public class PlayerService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener {

    private static final String ACTION_SET_PLAYLIST = "mediaplayer.patryk.mediaplayerpatryk.action.SET_PLAYLIST";
    private static final String ACTION_PLAY = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PLAY";
    private static final String ACTION_PAUSE = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PAUSE";
    private static final String ACTION_NEXT = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_NEXT";
    private static final String ACTION_PREVIOUS = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PREVIOUS";

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
    private boolean shouldPlay;

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
        // TODO: 30.06.2017 usunąć logi
        if (intent != null) {
            if (intent.getAction().equals(ACTION_SET_PLAYLIST)) {
                String playlistName = intent.getStringExtra(EXTRA_PARAM1);
                int songPosition = intent.getIntExtra(EXTRA_PARAM2, 0);
                handleActionSetPlaylist(playlistName, songPosition);
                KLog.w(1);
            }

            if (intent.getAction().equals(ACTION_PLAY)) {
                play();
                KLog.d(ACTION_PLAY);
            }

            if (intent.getAction().equals(ACTION_PAUSE)) {
                pause();
                KLog.d(ACTION_PAUSE);
            }

            if (intent.getAction().equals(ACTION_NEXT)) {
                nextSong();
                KLog.d(ACTION_NEXT);
            }

            if (intent.getAction().equals(ACTION_PREVIOUS)) {
                previousSong();
                KLog.d(PREVIOUS_ACTION);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleActionSetPlaylist(String playlistName, int songPos) {
        KLog.d("handleActionSetPlaylist");
        playlist = getPlaylist(playlistName);
        songPosition = songPos;
        currentSong = playlist.getSongs().get(songPosition);
        //createPlayer();
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

    private void previousSong() {
        KLog.i("previous song");

        songPosition--;

        if (songPosition < 0) {
            songPosition = 0;
            return;
        }

        currentSong = playlist.getSongs().get(songPosition);
        createPlayer();

        sendBroadcastWithAction(PREVIOUS_ACTION,null);

        KLog.e("previous currentEpisode");
        loadCover();
    }

    private void loadCover() {
        // TODO: 30.06.2017
    }

    private void play() {
        KLog.d("play");
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
        KLog.w("createPlayer");
        if (currentSong == null || currentSong.getUrl() == null) {
            stopSelf();
            return;
        }

        sendBroadcastWithAction(LOADING_ACTION, null);

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
                KLog.d("song prepared");
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

        player.prepare();
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
        Song song1 = new Song();
        song1.setArtist("Art1");
        song1.setTitle("Title1");
        song1.setUrl("https://api.soundcloud.com/tracks/98706380/stream?consumer_key=cd9d2e5604410d714e32642a4ec0eed4");
        song1.setImageUrl("https://upload.wikimedia.org/wikipedia/en/e/e9/In_Spades_%28Afghan_Whigs_cover%29.jpg");

        Song song2 = new Song();
        song2.setArtist("Art2");
        song2.setTitle("Title2");
        song2.setUrl("https://api.soundcloud.com/tracks/289659168/stream?consumer_key=cd9d2e5604410d714e32642a4ec0eed4");
        song2.setImageUrl("http://cdn.zumic.com/wp-content/uploads/2016/12/run-the-jewels-legend-has-it-soundcloud-cover-art-2016.jpg");

        List<Song> songList = new ArrayList<>();
        songList.add(song1);
        songList.add(song2);

        Playlist playlist = new Playlist();
        playlist.setName("My Playlist");
        playlist.setSongs(songList);

        return playlist;
    }


    @Override
    public void onAudioFocusChange(int focusChange) {
        // TODO: 30.06.2017
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

    @Override
    public IBinder onBind(Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return binder;
    }

    public static void startActionSetPlaylist(Context context, String playlistName, int songPosition) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_SET_PLAYLIST);
        intent.putExtra(EXTRA_PARAM1, playlistName);
        intent.putExtra(EXTRA_PARAM2, songPosition);
        context.startService(intent);
    }

    public static void startActionPlay(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PLAY);
        context.startService(intent);
    }

    public static void startActionPause(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PAUSE);
        context.startService(intent);
    }

    public static void startActionNextSong(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_NEXT);
        context.startService(intent);
    }

    public static void startActionPreviousSong(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PREVIOUS);
        context.startService(intent);
    }


    public class PlayerServiceBinder extends Binder {
        public PlayerServiceBinder getService() {
            return PlayerServiceBinder.this;
        }
    }
}
