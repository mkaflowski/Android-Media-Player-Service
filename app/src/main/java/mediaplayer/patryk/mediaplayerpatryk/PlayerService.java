package mediaplayer.patryk.mediaplayerpatryk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.widget.ImageView;

import com.socks.library.KLog;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import hybridmediaplayer.HybridMediaPlayer;

import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;

public class PlayerService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener {

    private static final String ACTION_SET_PLAYLIST = "mediaplayer.patryk.mediaplayerpatryk.action.SET_PLAYLIST";
    private static final String ACTION_PLAY = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PLAY";
    private static final String ACTION_PAUSE = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PAUSE";
    private static final String ACTION_NEXT = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_NEXT";
    private static final String ACTION_PREVIOUS = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_PREVIOUS";
    private static final String ACTION_SEND_INFO = "mediaplayer.patryk.mediaplayerpatryk.action.ACTION_SEND_INFO";

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
    public final static String DELETE_ACTION = "DELETE_ACTION";
    public final static String MINUS_TIME_ACTION = "MINUS_TIME_ACTION";
    public final static String PLUS_TIME_ACTION = "PLUS_TIME_ACTION";


    public final static String EXTRA_BUNDLE = "EXTRA_BUNDLE";
    public final static String TOTAL_TIME_VALUE_EXTRA = "TOTAL_TIME_VALUE_EXTRA";
    public final static String ACTUAL_TIME_VALUE_EXTRA = "ACTUAL_TIME_VALUE_EXTRA";
    public final static String COVER_URL_EXTRA = "COVER_URL_EXTRA";
    public final static String TITLE_EXTRA = "TITLE_EXTRA";
    public final static String ARTIST_EXTRA = "ARTIST_EXTRA";
    public final static String URL_EXTRA = "URL_EXTRA";


    private PlayerServiceBinder binder = new PlayerServiceBinder();

    private HybridMediaPlayer player;
    private Playlist playlist;
    private Song currentSong;
    int songPosition;
    private final int SKIP_TIME = 10000;

    private boolean isUpdatingThread;
    private boolean isPrepared;
    private boolean shouldPlay;
    private boolean isNoisyReceiverRegistered;
    private Thread updateThread;
    private Handler mainThreadHandler;
    private ScreenReceiver screenReceiver;

    private Bitmap cover;
    // TODO: 30.06.2017 ustawic coverPlaceholderId i notificationId
    private int coverPlaceholderId = R.mipmap.ic_launcher;
    private int notificationIconId = R.mipmap.ic_launcher;
    private int smallNotificationIconId = R.mipmap.ic_launcher;


    private MediaSessionCompat mediaSessionCompat;

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onFastForward() {
            super.onFastForward();
            seekTo(player.getCurrentPosition() - SKIP_TIME);
        }

        @Override
        public void onRewind() {
            super.onRewind();
            seekTo(player.getCurrentPosition() + SKIP_TIME);
        }

        @Override
        public void onPlay() {
            super.onPlay();
            KLog.e("onPlay");
            if (playlist != null && currentSong != null) {
                play();
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            KLog.e("onPause");
            if (playlist != null && currentSong != null) {
                pause();
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            if (playlist != null && currentSong != null) {
                pause();
            }
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            seekTo((int) pos);
        }

        @Override
        public void onSkipToNext() {
            KLog.d("onSkipToNext");
            super.onSkipToNext();
            nextSong(player.isPlaying());
        }

        @Override
        public void onSkipToPrevious() {
            KLog.d("onSkipToPrevious");
            super.onSkipToPrevious();
            previousSong(player.isPlaying());
        }
    };

    private BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pause();
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        initMediaSession();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mainThreadHandler = new Handler();

        if (intent != null) {
            if (intent.getAction().equals(ACTION_SET_PLAYLIST)) {
                String playlistName = intent.getStringExtra(EXTRA_PARAM1);
                int songPosition = intent.getIntExtra(EXTRA_PARAM2, 0);
                handleActionSetPlaylist(playlistName, songPosition);
                KLog.d(ACTION_SET_PLAYLIST);
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
                nextSong(player.isPlaying());
                KLog.d(ACTION_NEXT);
            }

            if (intent.getAction().equals(ACTION_SEND_INFO)) {
                sendInfoBroadcast();
                KLog.d(ACTION_SEND_INFO);
            }

            if (intent.getAction().equals(ACTION_PREVIOUS)) {
                previousSong(player.isPlaying());
                KLog.d(PREVIOUS_ACTION);
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isNoisyReceiverRegistered)
            unregisterReceiver(noisyReceiver);

        if (player != null)
            player.release();

        isUpdatingThread = false;
        stopForeground(true);
        abandonAudioFocus();
        cancelNotification();
        //// TODO: 30.06.2017 odkomentować
        unregisterReceiver(screenReceiver);
        mediaSessionCompat.setActive(false);
        mediaSessionCompat.release();
    }

    private void sendInfoBroadcast() {
        if(player == null || currentSong == null)
            return;

        Intent updateIntent = new Intent();
        updateIntent.setAction(GUI_UPDATE_ACTION);
        updateIntent.putExtra(URL_EXTRA, currentSong.getUrl());
        updateIntent.putExtra(ARTIST_EXTRA, currentSong.getArtist());
        updateIntent.putExtra(TITLE_EXTRA, currentSong.getTitle());
        updateIntent.putExtra(COVER_URL_EXTRA, currentSong.getImageUrl());
        updateIntent.putExtra(ACTUAL_TIME_VALUE_EXTRA, player.getCurrentPosition());
        updateIntent.putExtra(TOTAL_TIME_VALUE_EXTRA, player.getDuration());
        sendBroadcast(updateIntent);
    }

    private void cancelNotification() {
        // TODO: 30.06.2017
    }

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(this, MediaButtonReceiver.class);
        mediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "MediaTAG", mediaButtonReceiver, null);

        mediaSessionCompat.setCallback(mediaSessionCallback);
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // TODO: 30.06.2017 zastąpić Activity
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSessionCompat.setSessionActivity(pi);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        mediaSessionCompat.setMediaButtonReceiver(pendingIntent);

        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        mediaSessionCompat.setActive(true);
        setSessionToken(mediaSessionCompat.getSessionToken());
    }

    private void handleActionSetPlaylist(String playlistName, int songPos) {
        KLog.d("handleActionSetPlaylist");
        playlist = PlaylistHandler.getPlaylist(this, playlistName);
        songPosition = songPos;
        currentSong = playlist.getSongs().get(songPosition);
        createPlayer(false);
        updateMediaSessionMetaData();
        loadCover();
    }

    private void nextSong(boolean playOnLoaded) {
        KLog.i("next song");
        songPosition++;

        if (songPosition >= playlist.getSongs().size()) {
            songPosition = playlist.getSongs().size() - 1;
            return;
        }

        currentSong = playlist.getSongs().get(songPosition);
        createPlayer(playOnLoaded);

        sendBroadcastWithAction(NEXT_ACTION);
        loadCover();
    }

    private void previousSong(boolean playOnLoaded) {
        KLog.i("previous song");

        songPosition--;

        if (songPosition < 0) {
            songPosition = 0;
            return;
        }

        currentSong = playlist.getSongs().get(songPosition);
        createPlayer(playOnLoaded);

        sendBroadcastWithAction(PREVIOUS_ACTION);

        KLog.e("previous currentEpisode");
        loadCover();
    }

    private void loadCover() {
        final ImageView iv = new ImageView(this);

        if (currentSong.getImageUrl() != null && !currentSong.getImageUrl().isEmpty())
            Picasso.with(this).load(currentSong.getImageUrl()).placeholder(coverPlaceholderId).into(iv, new Callback() {
                @Override
                public void onSuccess() {
                    cover = ((BitmapDrawable) iv.getDrawable()).getBitmap();
                    updateMediaSessionMetaData();
                    makeNotification();
                    KLog.d("loadCover onSuccess");
                }

                @Override
                public void onError() {
                    cover = BitmapFactory.decodeResource(getResources(),
                            coverPlaceholderId);
                    updateMediaSessionMetaData();
                    makeNotification();
                    KLog.d("loadCover onError");

                }
            });
    }

    private void play() {
        KLog.d("play");
        if (!isPrepared) {
            createPlayer(true);
            return;
        }

        if (!retrieveAudioFocus())
            return;

        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        mediaSessionCompat.setActive(true);

        player.play();

        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyReceiver, filter);
        isNoisyReceiverRegistered = true;
        KLog.d("registerReceiver noisyReceiver");

        startUiUpdateThread();

        Intent updateIntent = new Intent();
        updateIntent.setAction(PLAY_ACTION);
        sendBroadcast(updateIntent);

        makeNotification();
    }

    private void pause() {
        KLog.d("pause");

        Intent updateIntent = new Intent();
        sendBroadcastWithAction(PAUSE_ACTION);
        sendBroadcast(updateIntent);
        isUpdatingThread = false;

        if (isNoisyReceiverRegistered)
            unregisterReceiver(noisyReceiver);
        isNoisyReceiverRegistered = false;
        KLog.d("unregisterReceiver noisyReceiver");


        if (player != null) {
            player.pause();
        }

        makeNotification();
        setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    private void seekTo(int time) {
        if (!isPrepared)
            return;

        player.seekTo(time);

        Intent updateIntent = new Intent();
        updateIntent.setAction(GUI_UPDATE_ACTION);
        updateIntent.putExtra(ACTUAL_TIME_VALUE_EXTRA, player.getCurrentPosition());
        sendBroadcast(updateIntent);
    }

    private void makeNotification() {
        if (playlist == null || player == null)
            return;
        if (cover == null)
            cover = BitmapFactory.decodeResource(getResources(),
                    coverPlaceholderId);


        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSessionCompat);

        builder.setSmallIcon(smallNotificationIconId);

        PendingIntent pplayIntent;
        if (player.isPlaying()) {
            pplayIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE);
        } else {
            pplayIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY);
        }

        builder.addAction(R.drawable.previous_pressed, "Rewind", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        if (player.isPlaying())
            builder.addAction(R.drawable.pause_notification, "Pause", pplayIntent);
        else
            builder.addAction(R.drawable.play_notification, "Play", pplayIntent);
        builder.addAction(R.drawable.next_pressed, "Rewind", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));


        builder.setStyle(new NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSessionCompat.getSessionToken())
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
        );

        builder.setColor(ContextCompat.getColor(this,R.color.colorPrimary));

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(345, builder.build());
    }


    private void setMediaPlaybackState(int state) {
        long position = (player == null ? 0 : player.getCurrentPosition());

        PlaybackStateCompat playbackStateCompat = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND |
                                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, position, 1, SystemClock.elapsedRealtime())
                .build();
        mediaSessionCompat.setPlaybackState(playbackStateCompat);
    }

    private void startUiUpdateThread() {
        if (mainThreadHandler == null) {
            isUpdatingThread = false;
            return;
        }

        isUpdatingThread = true;
        if (updateThread == null) {
            updateThread = new Thread(new Runnable() {
                @Override
                public void run() {

                    Intent guiUpdateIntent = new Intent();
                    guiUpdateIntent.setAction(GUI_UPDATE_ACTION);

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    while (isUpdatingThread && isPrepared) {
                        guiUpdateIntent.putExtra(ACTUAL_TIME_VALUE_EXTRA, player.getCurrentPosition());
                        sendBroadcast(guiUpdateIntent);
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    updateThread = null;
                }
            });

            updateThread.start();
        }
    }

    private void updateMediaSessionMetaData() {

        long duration = (player != null ? player.getDuration() : 180);

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.getArtist());
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "SoundCloud");
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle());
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentSong.getUrl());
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        // TODO: 30.06.2017 zrobić placeholder
        try {
            Bitmap icon;
            if (cover != null)
                icon = cover;
            else
                icon = BitmapFactory.decodeResource(getResources(),
                        coverPlaceholderId);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon);

        } catch (OutOfMemoryError e) {
            KLog.e("oom");
        }

        mediaSessionCompat.setMetadata(builder.build());
    }

    private void handleError(Exception exception) {
        // TODO: 30.06.2017 obsługa błędów
    }


    private void createPlayer(final boolean playOnLoaded) {
        KLog.w("createPlayer");
        if (currentSong == null || currentSong.getUrl() == null) {
            stopSelf();
            return;
        }

        sendBroadcastWithAction(LOADING_ACTION);

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
                sendBroadcastWithAction(LOADED_ACTION);


                Intent updateIntent = new Intent();
                updateIntent.setAction(GUI_UPDATE_ACTION);
                updateIntent.putExtra(TOTAL_TIME_VALUE_EXTRA, player.getDuration());
                sendBroadcast(updateIntent);

                isPrepared = true;

                if (playOnLoaded)
                    play();

                updateMediaSessionMetaData();
            }
        });

        player.setOnCompletionListener(new HybridMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(HybridMediaPlayer player) {
                if (songPosition == playlist.getSongs().size() - 1) {
                    sendBroadcastWithAction(COMPLETE_ACTION);
                    pause();
                    isUpdatingThread = false;
                    KLog.i("KONIEC PLAYLISTY");

                } else {
                    KLog.i("KONIEC PIOSENKI");
                    sendBroadcastWithAction(COMPLETE_ACTION);
                    isUpdatingThread = false;
                    nextSong(true);
                }
            }
        });

        player.prepare();
    }

    private void sendBroadcastWithAction(String loadingAction) {
        Intent updateIntent = new Intent();
        updateIntent.setAction(loadingAction);
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

    private boolean retrieveAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        return result == AudioManager.AUDIOFOCUS_GAIN;
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        KLog.d("focusChange - " + focusChange);
        if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT) {
            boolean isPlaying = player.isPlaying();
            pause();
            shouldPlay = isPlaying;
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            player.setVolume(1f);
            if (shouldPlay) {
                play();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
            abandonAudioFocus();
            mediaSessionCompat.setActive(false);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            player.setVolume(0.1f);
        }
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

    public static void startActionSendInfoBroadcast(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_SEND_INFO);
        context.startService(intent);
    }

    private final class ScreenReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null)
                if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    if (player == null || !player.isPlaying())
                        return;

                    startUiUpdateThread();

                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    isUpdatingThread = false;
                }
        }
    }

    public class PlayerServiceBinder extends Binder {
        public PlayerServiceBinder getService() {
            return PlayerServiceBinder.this;
        }
    }
}
