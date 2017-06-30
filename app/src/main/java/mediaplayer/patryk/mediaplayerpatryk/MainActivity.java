package mediaplayer.patryk.mediaplayerpatryk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PlayerService.startActionSetPlaylist(this,"playlistName",0);
    }

    public void play(View view) {
        PlayerService.startActionPlay(this);
    }

    public void pause(View view) {
        PlayerService.startActionPause(this);
    }

    public void next(View view) {
        PlayerService.startActionNextSong(this);
    }

    public void previous(View view) {
        PlayerService.startActionPreviousSong(this);
    }

    public void testNotification(View view) {
        final Intent emptyIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 555, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.exo_controls_fastforward)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!")
                        .setContentIntent(pendingIntent);

        mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(127, mBuilder.build());
    }
}
