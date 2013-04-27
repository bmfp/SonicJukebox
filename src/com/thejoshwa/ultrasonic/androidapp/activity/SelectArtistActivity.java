/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */

package com.thejoshwa.ultrasonic.androidapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.domain.Indexes;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicFolder;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.ArtistAdapter;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.util.ArrayList;
import java.util.List;

public class SelectArtistActivity extends SubsonicTabActivity implements AdapterView.OnItemClickListener {

    private static final int MENU_GROUP_MUSIC_FOLDER = 10;

    private ListView artistList;
    private View folderButton;
    private TextView folderName;
    private List<MusicFolder> musicFolders;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_artist);

        artistList = (ListView) findViewById(R.id.select_artist_list);
        artistList.setOnItemClickListener(this);
        //artistList.setOnTouchListener(gestureListener);

        folderButton = LayoutInflater.from(this).inflate(R.layout.select_artist_header, artistList, false);
        folderName = (TextView) folderButton.findViewById(R.id.select_artist_folder_2);

        if (!Util.isOffline(this)) {
            artistList.addHeaderView(folderButton);
        }

        registerForContextMenu(artistList);

        String title = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE);
        if (title == null) {
        	setTitle(Util.isOffline(this) ? R.string.music_library_label_offline : R.string.music_library_label);
        } else {
        	setTitle(title);
        }

        musicFolders = null;
        load();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main, menu);
    	inflater.inflate(R.menu.select_common, menu);
    	super.onCreateOptionsMenu(menu);
    	
    	return true;
    }

    private void refresh() {
        finish();
        Intent intent = getIntent();
        String title = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, title);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
        Util.startActivityWithoutTransition(this, intent);
    }

    private void selectFolder() {
        folderButton.showContextMenu();
    }

    private void load() {
        BackgroundTask<Indexes> task = new TabActivityBackgroundTask<Indexes>(this) {
            @Override
            protected Indexes doInBackground() throws Throwable {
                boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                MusicService musicService = MusicServiceFactory.getMusicService(SelectArtistActivity.this);
                if (!Util.isOffline(SelectArtistActivity.this)) {
                    musicFolders = musicService.getMusicFolders(SelectArtistActivity.this, this);
                }
                String musicFolderId = Util.getSelectedMusicFolderId(SelectArtistActivity.this);
                return musicService.getIndexes(musicFolderId, refresh, SelectArtistActivity.this, this);
            }

            @Override
            protected void done(Indexes result) {
            	if (result != null) {
            		List<Artist> artists = new ArrayList<Artist>(result.getShortcuts().size() + result.getArtists().size());
            		artists.addAll(result.getShortcuts());
            		artists.addAll(result.getArtists());
            		artistList.setAdapter(new ArtistAdapter(SelectArtistActivity.this, artists));
            	}

                // Display selected music folder
                if (musicFolders != null) {
                    String musicFolderId = Util.getSelectedMusicFolderId(SelectArtistActivity.this);
                    if (musicFolderId == null) {
                        folderName.setText(R.string.select_artist_all_folders);
                    } else {
                        for (MusicFolder musicFolder : musicFolders) {
                            if (musicFolder.getId().equals(musicFolderId)) {
                                folderName.setText(musicFolder.getName());
                                break;
                            }
                        }
                    }
                }
            }
        };
        task.execute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (view == folderButton) {
            selectFolder();
        } else {
            Artist artist = (Artist) parent.getItemAtPosition(position);
            Intent intent = new Intent(this, SelectAlbumActivity.class);
            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, artist.getId());
            intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, artist.getName());
            Util.startActivityWithoutTransition(this, intent);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        if (artistList.getItemAtPosition(info.position) instanceof Artist) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.select_artist_context, menu);
        } else if (info.position == 0) {
            String musicFolderId = Util.getSelectedMusicFolderId(this);
            MenuItem menuItem = menu.add(MENU_GROUP_MUSIC_FOLDER, -1, 0, R.string.select_artist_all_folders);
            if (musicFolderId == null) {
                menuItem.setChecked(true);
            }
            if (musicFolders != null) {
                for (int i = 0; i < musicFolders.size(); i++) {
                    MusicFolder musicFolder = musicFolders.get(i);
                    menuItem = menu.add(MENU_GROUP_MUSIC_FOLDER, i, i + 1, musicFolder.getName());
                    if (musicFolder.getId().equals(musicFolderId)) {
                        menuItem.setChecked(true);
                    }
                }
            }
            
            menu.setGroupCheckable(MENU_GROUP_MUSIC_FOLDER, true, true);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        Artist artist = (Artist) artistList.getItemAtPosition(info.position);

        if (artist != null) {
            switch (menuItem.getItemId()) {
                case R.id.artist_menu_play_now:
                    downloadRecursively(artist.getId(), false, false, true);
                    break;
                case R.id.artist_menu_play_last:
                    downloadRecursively(artist.getId(), false, true, false);
                    break;
                case R.id.artist_menu_pin:
                    downloadRecursively(artist.getId(), true, true, false);
                    break;
                default:
                    return super.onContextItemSelected(menuItem);
            }
        } else if (info.position == 0) {
            MusicFolder selectedFolder = menuItem.getItemId() == -1 ? null : musicFolders.get(menuItem.getItemId());
            String musicFolderId = selectedFolder == null ? null : selectedFolder.getId();
            String musicFolderName = selectedFolder == null ? getString(R.string.select_artist_all_folders)
                                                            : selectedFolder.getName();
            Util.setSelectedMusicFolderId(this, musicFolderId);
            folderName.setText(musicFolderName);
            refresh();
        }

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
			case android.R.id.home:
				menuDrawer.toggleMenu();
				return true; 
            case R.id.menu_refresh:
            	refresh();
                return true;
            case R.id.main_shuffle:
                Intent intent = new Intent(this, DownloadActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
                Util.startActivityWithoutTransition(this, intent);
                return true;              
        }

        return false;
    }
}