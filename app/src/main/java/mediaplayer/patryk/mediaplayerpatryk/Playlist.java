package mediaplayer.patryk.mediaplayerpatryk;

import java.util.ArrayList;
import java.util.List;


public class Playlist {
    private List<Song> songs;
    private String name;

    public Playlist() {
        songs = new ArrayList<>();
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
