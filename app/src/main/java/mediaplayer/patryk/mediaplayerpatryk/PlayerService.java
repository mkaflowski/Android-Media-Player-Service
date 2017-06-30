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
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Bundle;
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
    public final static String DELETE_ACTION = "PREVIOUS_ACTION";
    public final static String MINUS_TIME_ACTION = "PREVIOUS_ACTION";
    public final static String PLUS_TIME_ACTION = "PREVIOUS_ACTION";


    public final static String EXTRA_BUNDLE = "EXTRA_BUNDLE";
    public final static String TOTAL_TIME_VALUE_EXTRA = "TOTAL_TIME_VALUE_EXTRA";
    private final String ACTUAL_TIME_VALUE_EXTRA = "ACTUAL_TIME_VALUE_EXTRA";


    public static final int NOTIFICATION_ID = 117;


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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isNoisyReceiverRegistered)
            unregisterReceiver(noisyReceiver);

        if (player != null)
            player.release();

        stopForeground(true);
        abandonAudioFocus();
        cancelNotification();
        //// TODO: 30.06.2017 odkomentować
//        unregisterReceiver(screenReceiver);
//        unregisterReceiver(audioJackReceiver);
        mediaSessionCompat.setActive(false);
        mediaSessionCompat.release();
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
        KLog.d(mediaSessionCompat.getSessionToken());
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
                nextSong(player.isPlaying());
                KLog.d(ACTION_NEXT);
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

    private void handleActionSetPlaylist(String playlistName, int songPos) {
        KLog.d("handleActionSetPlaylist");
        playlist = getPlaylist(playlistName);
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

        sendBroadcastWithAction(NEXT_ACTION, null);
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

        sendBroadcastWithAction(PREVIOUS_ACTION, null);

        KLog.e("previous currentEpisode");
        loadCover();
    }

    private void loadCover() {
        final ImageView iv = new ImageView(this);

        KLog.d(currentSong.getImageUrl());

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
        sendBroadcastWithAction(PAUSE_ACTION, null);
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
        Bundle bundle = new Bundle();
        bundle.putInt(ACTUAL_TIME_VALUE_EXTRA, player.getCurrentPosition());
        sendBroadcastWithAction(GUI_UPDATE_ACTION, bundle);
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
        // TODO: 30.06.2017  
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

                if (playOnLoaded)
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
                    nextSong(true);
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
