package mediaplayer.patryk.mediaplayerpatryk;

import android.os.Bundle;
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
}
