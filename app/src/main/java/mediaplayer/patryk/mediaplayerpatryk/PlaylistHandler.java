package mediaplayer.patryk.mediaplayerpatryk;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;


public class PlaylistHandler {

    public static Playlist getPlaylist(Context context, String playlistName) {
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
}
