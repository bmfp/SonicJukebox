package com.budrotech.jukebox.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.budrotech.jukebox.activity.JukeboxTabActivity;
import com.budrotech.jukebox.domain.Playlist;

import org.moire.jukebox.R;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class PlaylistAdapter extends ArrayAdapter<Playlist>
{

	private final JukeboxTabActivity activity;

	public PlaylistAdapter(JukeboxTabActivity activity, List<Playlist> Playlists)
	{
		super(activity, R.layout.playlist_list_item, Playlists);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Playlist entry = getItem(position);
		PlaylistView view;

		if (convertView != null && convertView instanceof PlaylistView)
		{
			PlaylistView currentView = (PlaylistView) convertView;

			ViewHolder viewHolder = (ViewHolder) convertView.getTag();
			view = currentView;
			view.setViewHolder(viewHolder);
		}
		else
		{
			view = new PlaylistView(activity);
			view.setLayout();
		}

		view.setPlaylist(entry);
		return view;
	}

	public static class PlaylistComparator implements Comparator<Playlist>, Serializable
	{
		private static final long serialVersionUID = -6201663557439120008L;

		@Override
		public int compare(Playlist playlist1, Playlist playlist2)
		{
			return playlist1.getName().compareToIgnoreCase(playlist2.getName());
		}

		public static List<Playlist> sort(List<Playlist> playlists)
		{
			Collections.sort(playlists, new PlaylistComparator());
			return playlists;
		}
	}

	static class ViewHolder
	{
		TextView name;
	}
}