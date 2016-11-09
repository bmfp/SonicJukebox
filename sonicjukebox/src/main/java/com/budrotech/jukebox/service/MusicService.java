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
package com.budrotech.jukebox.service;

import android.content.Context;
import android.graphics.Bitmap;

import com.budrotech.jukebox.domain.Bookmark;
import com.budrotech.jukebox.domain.ChatMessage;
import com.budrotech.jukebox.domain.Genre;
import com.budrotech.jukebox.domain.Indexes;
import com.budrotech.jukebox.domain.JukeboxStatus;
import com.budrotech.jukebox.domain.Lyrics;
import com.budrotech.jukebox.domain.MusicDirectory;
import com.budrotech.jukebox.domain.MusicFolder;
import com.budrotech.jukebox.domain.Playlist;
import com.budrotech.jukebox.domain.SearchCriteria;
import com.budrotech.jukebox.domain.SearchResult;
import com.budrotech.jukebox.domain.Share;
import com.budrotech.jukebox.domain.UserInfo;
import com.budrotech.jukebox.domain.Version;
import com.budrotech.jukebox.util.CancellableTask;
import com.budrotech.jukebox.util.ProgressListener;

import java.util.List;

import okhttp3.Response;

/**
 * @author Sindre Mehus
 */
public interface MusicService
{

	void ping(Context context, ProgressListener progressListener) throws Exception;

	boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception;

	List<Genre> getGenres(Context context, ProgressListener progressListener) throws Exception;

	void star(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception;

	void unstar(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception;

	List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	Indexes getArtists(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	SearchResult search(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getPlaylist(String id, String name, Context context, ProgressListener progressListener) throws Exception;

	List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception;

	void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception;

	void updatePlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception;

	void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception;

	void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception;

	Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception;

	void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbumList2(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception;

	SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception;

	SearchResult getStarred2(Context context, ProgressListener progressListener) throws Exception;

	Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception;

	Response getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception;

	Version getLocalVersion(Context context) throws Exception;

	JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception;

	JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception;

	JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception;

	JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception;

	JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception;

	JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception;

	List<Share> getShares(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception;

	void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception;

	List<Bookmark> getBookmarks(Context context, ProgressListener progressListener) throws Exception;

	void deleteBookmark(String id, Context context, ProgressListener progressListener) throws Exception;

	void createBookmark(String id, int position, Context context, ProgressListener progressListener) throws Exception;

	UserInfo getUser(String username, Context context, ProgressListener progressListener) throws Exception;

	List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception;

	void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception;

	void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception;

	Bitmap getAvatar(Context context, String username, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception;
}