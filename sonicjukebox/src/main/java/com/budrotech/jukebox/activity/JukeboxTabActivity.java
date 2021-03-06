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
package com.budrotech.jukebox.activity;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.budrotech.jukebox.R;
import com.budrotech.jukebox.domain.MusicDirectory;
import com.budrotech.jukebox.domain.MusicDirectory.Entry;
import com.budrotech.jukebox.domain.PlayerState;
import com.budrotech.jukebox.domain.Share;
import com.budrotech.jukebox.service.DownloadFile;
import com.budrotech.jukebox.service.DownloadService;
import com.budrotech.jukebox.service.DownloadServiceImpl;
import com.budrotech.jukebox.service.MusicService;
import com.budrotech.jukebox.service.MusicServiceFactory;
import com.budrotech.jukebox.util.BackgroundTask;
import com.budrotech.jukebox.util.Constants;
import com.budrotech.jukebox.util.EntryByDiscAndTrackComparator;
import com.budrotech.jukebox.util.ImageLoader;
import com.budrotech.jukebox.util.ModalBackgroundTask;
import com.budrotech.jukebox.util.ShareDetails;
import com.budrotech.jukebox.util.SilentBackgroundTask;
import com.budrotech.jukebox.util.TabActivityBackgroundTask;
import com.budrotech.jukebox.util.TimeSpan;
import com.budrotech.jukebox.util.TimeSpanPicker;
import com.budrotech.jukebox.util.Util;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Sindre Mehus
 */
public class JukeboxTabActivity extends ResultActivity implements OnClickListener
{
	private static final String TAG = JukeboxTabActivity.class.getSimpleName();
	private static final Pattern COMPILE = Pattern.compile(":");
	protected static ImageLoader IMAGE_LOADER;
	protected static String theme;
	private static JukeboxTabActivity instance;

	private boolean destroyed;

	private static final String STATE_MENUDRAWER = "com.budrotech.jukebox.menuDrawer";
	private static final String STATE_ACTIVE_VIEW_ID = "com.budrotech.jukebox.activeViewId";
	private static final String STATE_ACTIVE_POSITION = "com.budrotech.jukebox.activePosition";
	private static final int DIALOG_ASK_FOR_SHARE_DETAILS = 102;

	public MenuDrawer menuDrawer;
	private int activePosition = 1;
	private int menuActiveViewId;
	private View nowPlayingView;
	View chatMenuItem;
	View bookmarksMenuItem;
	View sharesMenuItem;
	public static boolean nowPlayingHidden;
	public static Entry currentSong;
	public Bitmap nowPlayingImage;
	boolean licenseValid;
	NotificationManager notificationManager;
	private EditText shareDescription;
	TimeSpanPicker timeSpanPicker;
	CheckBox hideDialogCheckBox;
	CheckBox noExpirationCheckBox;
	CheckBox saveAsDefaultsCheckBox;
	ShareDetails shareDetails;

	@Override
	protected void onCreate(Bundle bundle)
	{
		setUncaughtExceptionHandler();
		applyTheme();
		super.onCreate(bundle);

		startService(new Intent(this, DownloadServiceImpl.class));
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		if (bundle != null)
		{
			activePosition = bundle.getInt(STATE_ACTIVE_POSITION);
			menuActiveViewId = bundle.getInt(STATE_ACTIVE_VIEW_ID);
		}

		menuDrawer = MenuDrawer.attach(this, MenuDrawer.Type.BEHIND, Position.LEFT, MenuDrawer.MENU_DRAG_WINDOW);
		menuDrawer.setMenuView(R.layout.menu_main);

		chatMenuItem = findViewById(R.id.menu_chat);
		bookmarksMenuItem = findViewById(R.id.menu_bookmarks);
		sharesMenuItem = findViewById(R.id.menu_shares);

		findViewById(R.id.menu_home).setOnClickListener(this);
		findViewById(R.id.menu_browse).setOnClickListener(this);
		findViewById(R.id.menu_search).setOnClickListener(this);
		findViewById(R.id.menu_playlists).setOnClickListener(this);
		sharesMenuItem.setOnClickListener(this);
		chatMenuItem.setOnClickListener(this);
		bookmarksMenuItem.setOnClickListener(this);
		findViewById(R.id.menu_now_playing).setOnClickListener(this);
		findViewById(R.id.menu_settings).setOnClickListener(this);
		findViewById(R.id.menu_about).setOnClickListener(this);
		setActionBarDisplayHomeAsUp(true);

		TextView activeView = (TextView) findViewById(menuActiveViewId);

		if (activeView != null)
		{
			menuDrawer.setActiveView(activeView);
		}

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	@Override
	protected void onPostCreate(Bundle bundle)
	{
		super.onPostCreate(bundle);
		instance = this;

		int visibility = Util.isOffline(this) ? View.GONE : View.VISIBLE;
		chatMenuItem.setVisibility(visibility);
		bookmarksMenuItem.setVisibility(visibility);
		sharesMenuItem.setVisibility(visibility);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		applyTheme();
		instance = this;

		Util.registerMediaButtonEventReceiver(this);

		// Make sure to update theme
		if (theme != null && !theme.equals(Util.getTheme(this)))
		{
			theme = Util.getTheme(this);
			restart();
		}

		if (!nowPlayingHidden)
		{
			showNowPlaying();
		}
		else
		{
			hideNowPlaying();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				menuDrawer.toggleMenu();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onDestroy()
	{
		Util.unregisterMediaButtonEventReceiver(this);
		super.onDestroy();
		destroyed = true;
		nowPlayingView = null;
		clearImageLoader();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		boolean isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
		boolean isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
		boolean isVolumeAdjust = isVolumeDown || isVolumeUp;
		boolean isJukebox = getDownloadService() != null && getDownloadService().isJukeboxEnabled();

		if (isVolumeAdjust && isJukebox)
		{
			getDownloadService().adjustJukeboxVolume(isVolumeUp);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	protected void restart()
	{
		Intent intent = new Intent(this, this.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtras(getIntent());
		Util.startActivityForResultWithoutTransition(this, intent);
	}

	@Override
	public void finish()
	{
		super.finish();
		Util.disablePendingTransition(this);
	}

	@Override
	public boolean isDestroyed()
	{
		return destroyed;
	}

	public void showNowPlaying()
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				new SilentBackgroundTask<Void>(JukeboxTabActivity.this)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						if (!Util.getShowNowPlayingPreference(JukeboxTabActivity.this))
						{
							hideNowPlaying();
							return null;
						}

						nowPlayingView = findViewById(R.id.now_playing);

						if (nowPlayingView != null)
						{
							final DownloadService downloadService = DownloadServiceImpl.getInstance();

							if (downloadService != null)
							{
								PlayerState playerState = downloadService.getPlayerState();

								if (playerState.equals(PlayerState.PAUSED) || playerState.equals(PlayerState.STARTED))
								{
									DownloadFile file = downloadService.getCurrentPlaying();

									if (file != null)
									{
										final Entry song = file.getSong();
										showNowPlaying(JukeboxTabActivity.this, downloadService, song, playerState);
									}
								}
								else
								{
									hideNowPlaying();
								}
							}
						}

						return null;
					}

					@Override
					protected void done(Void result)
					{
					}
				}.execute();
			}
		});
	}

	private void applyTheme()
	{
		String theme = Util.getTheme(this);

		if ("dark".equalsIgnoreCase(theme) || "fullscreen".equalsIgnoreCase(theme))
		{
			setTheme(R.style.SonicJukeboxTheme);
		}
		else if ("light".equalsIgnoreCase(theme) || "fullscreenlight".equalsIgnoreCase(theme))
		{
			setTheme(R.style.SonicJukeboxTheme_Light);
		}
	}

	public void showNotification(final Handler handler, final Entry song, final DownloadServiceImpl downloadService, final Notification notification, final PlayerState playerState)
	{
		if (!Util.isNotificationEnabled(this))
		{
			return;
		}

		new AsyncTask<Void, Void, String[]>()
		{
			@SuppressLint("NewApi")
			@Override
			protected String[] doInBackground(Void... params)
			{
				Thread.currentThread().setName("showNotification");
				RemoteViews notificationView = notification.contentView;
				RemoteViews bigNotificationView = null;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				{
					bigNotificationView = notification.bigContentView;
				}

				if (playerState == PlayerState.PAUSED)
				{
					setImageViewResourceOnUiThread(notificationView, R.id.control_play, R.drawable.media_start_normal_dark);

					if (bigNotificationView != null)
					{
						setImageViewResourceOnUiThread(bigNotificationView, R.id.control_play, R.drawable.media_start_normal_dark);
					}
				}
				else if (playerState == PlayerState.STARTED)
				{
					setImageViewResourceOnUiThread(notificationView, R.id.control_play, R.drawable.media_pause_normal_dark);

					if (bigNotificationView != null)
					{
						setImageViewResourceOnUiThread(bigNotificationView, R.id.control_play, R.drawable.media_pause_normal_dark);
					}
				}

				if (currentSong != song)
				{
					currentSong = song;

					String title = song.getTitle();
					String text = song.getArtist();
					String album = song.getAlbum();

					try
					{
						if (nowPlayingImage == null)
						{
							setImageViewResourceOnUiThread(notificationView, R.id.notification_image, R.drawable.unknown_album);

							if (bigNotificationView != null)
							{
								setImageViewResourceOnUiThread(bigNotificationView, R.id.notification_image, R.drawable.unknown_album);
							}
						}
						else
						{
							setImageViewBitmapOnUiThread(notificationView, R.id.notification_image, nowPlayingImage);

							if (bigNotificationView != null)
							{
								setImageViewBitmapOnUiThread(bigNotificationView, R.id.notification_image, nowPlayingImage);
							}
						}
					}
					catch (Exception x)
					{
						Log.w(TAG, "Failed to get notification cover art", x);
						setImageViewResourceOnUiThread(notificationView, R.id.notification_image, R.drawable.unknown_album);

						if (bigNotificationView != null)
						{
							setImageViewResourceOnUiThread(bigNotificationView, R.id.notification_image, R.drawable.unknown_album);
						}
					}

					setTextViewTextOnUiThread(notificationView, R.id.trackname, title);
					setTextViewTextOnUiThread(notificationView, R.id.artist, text);
					setTextViewTextOnUiThread(notificationView, R.id.album, album);

					if (bigNotificationView != null)
					{
						setTextViewTextOnUiThread(bigNotificationView, R.id.trackname, title);
						setTextViewTextOnUiThread(bigNotificationView, R.id.artist, text);
						setTextViewTextOnUiThread(bigNotificationView, R.id.album, album);
					}
				}

				// Send the notification and put the service in the foreground.
				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						downloadService.startForeground(Constants.NOTIFICATION_ID_PLAYING, notification);
					}
				});

				return null;
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void hidePlayingNotification(final Handler handler, final DownloadServiceImpl downloadService)
	{
		currentSong = null;

		// Remove notification and remove the service from the foreground
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				downloadService.stopForeground(true);
			}
		});
	}

	private void showNowPlaying(final Context context, final DownloadService downloadService, final Entry song, final PlayerState playerState)
	{
		if (context == null || downloadService == null || song == null || playerState == null)
		{
			return;
		}

		if (!Util.getShowNowPlayingPreference(context))
		{
			hideNowPlaying();
			return;
		}

		if (nowPlayingView == null)
		{
			nowPlayingView = findViewById(R.id.now_playing);
		}

		if (nowPlayingView != null)
		{
			try
			{
				setVisibilityOnUiThread(nowPlayingView, View.VISIBLE);
				nowPlayingHidden = false;

				ImageView playButton = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);

				if (playerState == PlayerState.PAUSED)
				{
					setImageDrawableOnUiThread(playButton, Util.getDrawableFromAttribute(context, R.attr.media_play));
				}
				else if (playerState == PlayerState.STARTED)
				{
					setImageDrawableOnUiThread(playButton, Util.getDrawableFromAttribute(context, R.attr.media_pause));
				}

				String title = song.getTitle();
				String artist = song.getArtist();

				final ImageView nowPlayingAlbumArtImage = (ImageView) nowPlayingView.findViewById(R.id.now_playing_image);
				TextView nowPlayingTrack = (TextView) nowPlayingView.findViewById(R.id.now_playing_trackname);
				TextView nowPlayingArtist = (TextView) nowPlayingView.findViewById(R.id.now_playing_artist);

				this.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						getImageLoader().loadImage(nowPlayingAlbumArtImage, song, false, Util.getNotificationImageSize(context), false, true);
					}
				});

				final Intent intent = new Intent(context, SelectAlbumActivity.class);

				if (Util.getShouldUseId3Tags(context))
				{
					intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, true);
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getAlbumId());
				}
				else
				{
					intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false);
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getParent());
				}

				intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, song.getAlbum());

				setOnClickListenerOnUiThread(nowPlayingAlbumArtImage, new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
					}
				});

				setTextOnUiThread(nowPlayingTrack, title);
				setTextOnUiThread(nowPlayingArtist, artist);

				ImageView nowPlayingControlPlay = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);

				SwipeDetector swipeDetector = new SwipeDetector(JukeboxTabActivity.this, downloadService);
				setOnTouchListenerOnUiThread(nowPlayingView, swipeDetector);

				setOnClickListenerOnUiThread(nowPlayingView, new OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
					}
				});

				setOnClickListenerOnUiThread(nowPlayingControlPlay, new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						downloadService.togglePlayPause();
					}
				});

			}
			catch (Exception x)
			{
				Log.w(TAG, "Failed to get notification cover art", x);
			}
		}
	}

	public void hideNowPlaying()
	{
		try
		{
			if (nowPlayingView == null)
			{
				nowPlayingView = findViewById(R.id.now_playing);
			}

			if (nowPlayingView != null)
			{
				setVisibilityOnUiThread(nowPlayingView, View.GONE);
			}
		}
		catch (Exception ex)
		{
			Log.w(String.format("Exception in hideNowPlaying: %s", ex), ex);
		}
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_ASK_FOR_SHARE_DETAILS)
		{
			final LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			final View layout = layoutInflater.inflate(R.layout.share_details, (ViewGroup) findViewById(R.id.share_details));

			if (layout != null)
			{
				shareDescription = (EditText) layout.findViewById(R.id.share_description);
				hideDialogCheckBox = (CheckBox) layout.findViewById(R.id.hide_dialog);
				noExpirationCheckBox = (CheckBox) layout.findViewById(R.id.timeSpanDisableCheckBox);
				saveAsDefaultsCheckBox = (CheckBox) layout.findViewById(R.id.save_as_defaults);
				timeSpanPicker = (TimeSpanPicker) layout.findViewById(R.id.date_picker);
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.share_set_share_options);

			builder.setPositiveButton(R.string.common_save, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int clickId)
				{
					if (!noExpirationCheckBox.isChecked())
					{
						TimeSpan timeSpan = timeSpanPicker.getTimeSpan();
						TimeSpan now = TimeSpan.getCurrentTime();
						shareDetails.Expiration = now.add(timeSpan).getTotalMilliseconds();
					}

					shareDetails.Description = String.valueOf(shareDescription.getText());

					if (hideDialogCheckBox.isChecked())
					{
						Util.setShouldAskForShareDetails(JukeboxTabActivity.this, false);
					}

					if (saveAsDefaultsCheckBox.isChecked())
					{
						String timeSpanType = timeSpanPicker.getTimeSpanType();
						int timeSpanAmount = timeSpanPicker.getTimeSpanAmount();
						Util.setDefaultShareExpiration(JukeboxTabActivity.this, !noExpirationCheckBox.isChecked() && timeSpanAmount > 0 ? String.format("%d:%s", timeSpanAmount, timeSpanType) : "");
						Util.setDefaultShareDescription(JukeboxTabActivity.this, shareDetails.Description);
					}

					share();
				}
			});

			builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int clickId)
				{
					shareDetails = null;
					dialog.cancel();
				}
			});
			builder.setView(layout);
			builder.setCancelable(true);

			timeSpanPicker.setTimeSpanDisableText(getResources().getString(R.string.no_expiration));

			noExpirationCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
			{
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b)
				{
					timeSpanPicker.setEnabled(!b);
				}
			});

			String defaultDescription = Util.getDefaultShareDescription(this);
			String timeSpan = Util.getDefaultShareExpiration(this);

			String[] split = COMPILE.split(timeSpan);

			if (split.length == 2)
			{
				int timeSpanAmount = Integer.parseInt(split[0]);
				String timeSpanType = split[1];

				if (timeSpanAmount > 0)
				{
					noExpirationCheckBox.setChecked(false);
					timeSpanPicker.setEnabled(true);
					timeSpanPicker.setTimeSpanAmount(String.valueOf(timeSpanAmount));
					timeSpanPicker.setTimeSpanType(timeSpanType);
				}
				else
				{
					noExpirationCheckBox.setChecked(true);
					timeSpanPicker.setEnabled(false);
				}
			}
			else
			{
				noExpirationCheckBox.setChecked(true);
				timeSpanPicker.setEnabled(false);
			}

			shareDescription.setText(defaultDescription);

			return builder.create();
		}
		else
		{
			return super.onCreateDialog(id);
		}
	}

	public void createShare(final List<Entry> entries)
	{
		boolean askForDetails = Util.getShouldAskForShareDetails(this);

		shareDetails = new ShareDetails();
		shareDetails.Entries = entries;

		if (askForDetails)
		{
			showDialog(DIALOG_ASK_FOR_SHARE_DETAILS);
		}
		else
		{
			shareDetails.Description = Util.getDefaultShareDescription(this);
			shareDetails.Expiration = TimeSpan.getCurrentTime().add(Util.getDefaultShareExpirationInMillis(this)).getTotalMilliseconds();
			share();
		}
	}

	public void share()
	{
		BackgroundTask<Share> task = new TabActivityBackgroundTask<Share>(this, true)
		{
			@Override
			protected Share doInBackground() throws Throwable
			{
				List<String> ids = new ArrayList<String>();

				if (shareDetails.Entries.isEmpty())
				{
					ids.add(getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID));
				}
				else
				{
					for (Entry entry : shareDetails.Entries)
					{
						ids.add(entry.getId());
					}
				}

				MusicService musicService = MusicServiceFactory.getMusicService(JukeboxTabActivity.this);

				long timeInMillis = 0;

				if (shareDetails.Expiration != 0)
				{
					timeInMillis = shareDetails.Expiration;
				}

				List<Share> shares = musicService.createShare(ids, shareDetails.Description, timeInMillis, JukeboxTabActivity.this, this);
				return shares.get(0);
			}

			@Override
			protected void done(Share result)
			{
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, String.format("%s\n\n%s", Util.getShareGreeting(JukeboxTabActivity.this), result.getUrl()));
				startActivity(Intent.createChooser(intent, getResources().getString(R.string.share_via)));
			}
		};

		task.execute();
	}

	public void setTextViewTextOnUiThread(final RemoteViews view, final int id, final CharSequence text)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setTextViewText(id, text);
				}
			}
		});
	}

	public void setImageViewBitmapOnUiThread(final RemoteViews view, final int id, final Bitmap bitmap)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setImageViewBitmap(id, bitmap);
				}
			}
		});
	}

	public void setImageViewResourceOnUiThread(final RemoteViews view, final int id, final int resource)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setImageViewResource(id, resource);
				}
			}
		});
	}

	public void setOnTouchListenerOnUiThread(final View view, final OnTouchListener listener)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setOnTouchListener(listener);
				}
			}
		});
	}

	public void setOnClickListenerOnUiThread(final View view, final OnClickListener listener)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setOnClickListener(listener);
				}
			}
		});
	}

	public void setTextOnUiThread(final TextView view, final CharSequence text)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setText(text);
				}
			}
		});
	}

	public void setImageDrawableOnUiThread(final ImageView view, final Drawable drawable)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setImageDrawable(drawable);
				}
			}
		});
	}

	public void setVisibilityOnUiThread(final View view, final int visibility)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != visibility)
				{
					view.setVisibility(visibility);
				}
			}
		});
	}

	public static JukeboxTabActivity getInstance()
	{
		return instance;
	}

	public boolean getIsDestroyed()
	{
		return destroyed;
	}

	public void setProgressVisible(boolean visible)
	{
		View view = findViewById(R.id.tab_progress);
		if (view != null)
		{
			view.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	public void updateProgress(CharSequence message)
	{
		TextView view = (TextView) findViewById(R.id.tab_progress_message);
		if (view != null)
		{
			view.setText(message);
		}
	}

	public DownloadService getDownloadService()
	{
		return DownloadServiceImpl.getDownloadService(this);
	}

	protected void warnIfNetworkOrStorageUnavailable()
	{
		Util.warnIfNetworkOrStorageUnavailable(this);
	}

	public synchronized void clearImageLoader()
	{
		if (IMAGE_LOADER != null && IMAGE_LOADER.isRunning()) IMAGE_LOADER.clear();
	}

	public synchronized ImageLoader getImageLoader()
	{
		if (IMAGE_LOADER == null || !IMAGE_LOADER.isRunning())
		{
			IMAGE_LOADER = new ImageLoader(this, Util.getImageLoaderConcurrency(this));
			IMAGE_LOADER.startImageLoader();
		}

		return IMAGE_LOADER;
	}

	void download(final boolean append, final boolean autoPlay, final boolean playNext, final boolean shuffle, final List<Entry> songs)
	{
		String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
		DownloadServiceImpl.getDownloadService(JukeboxTabActivity.this).download(JukeboxTabActivity.this, songs, append, false, autoPlay, playNext, shuffle);
		if (playlistName != null)
		{
			DownloadServiceImpl.getDownloadService(JukeboxTabActivity.this).setSuggestedPlaylistName(playlistName);
		}
	}

	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext, final boolean unpin, final boolean isArtist)
	{
		downloadRecursively(id, "", false, true, save, append, autoplay, shuffle, background, playNext, unpin, isArtist);
	}

	protected void downloadShare(final String id, final String name, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean unpin)
	{
		downloadRecursively(id, name, true, false, save, append, autoplay, shuffle, background, false, unpin, false);
	}

	protected void downloadPlaylist(final String id, final String name, final boolean save, final boolean append, final boolean unpin)
	{
		downloadRecursively(id, name, false, false, save, append, false, false, true, false, unpin, false);
	}

	protected void downloadRecursively(final String id, final String name, final boolean isShare, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext, final boolean unpin, final boolean isArtist)
	{
		ModalBackgroundTask<List<Entry>> task = new ModalBackgroundTask<List<Entry>>(this, false)
		{
			private static final int MAX_SONGS = 500;

			@Override
			protected List<Entry> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(JukeboxTabActivity.this);
				List<Entry> songs = new LinkedList<Entry>();
				MusicDirectory root;

				if (!Util.isOffline(JukeboxTabActivity.this) && isArtist && Util.getShouldUseId3Tags(JukeboxTabActivity.this))
				{
					getSongsForArtist(id, songs);
				}
				else
				{
					if (isDirectory)
					{
						root = !Util.isOffline(JukeboxTabActivity.this) && Util.getShouldUseId3Tags(JukeboxTabActivity.this) ? musicService.getAlbum(id, name, false, JukeboxTabActivity.this, this) : musicService.getMusicDirectory(id, name, false, JukeboxTabActivity.this, this);
					}
					else if (isShare)
					{
						root = new MusicDirectory();

						List<Share> shares = musicService.getShares(true, JukeboxTabActivity.this, this);

						for (Share share : shares)
						{
							if (share.getId().equals(id))
							{
								for (Entry entry : share.getEntries())
								{
									root.addChild(entry);
								}

								break;
							}
						}
					}
					else
					{
						root = musicService.getPlaylist(id, name, JukeboxTabActivity.this, this);
					}

					getSongsRecursively(root, songs);
				}

				return songs;
			}

			private void getSongsRecursively(MusicDirectory parent, List<Entry> songs) throws Exception
			{
				if (songs.size() > MAX_SONGS)
				{
					return;
				}

				for (Entry song : parent.getChildren(false, true))
				{
					if (!song.isVideo())
					{
						songs.add(song);
					}
				}

				MusicService musicService = MusicServiceFactory.getMusicService(JukeboxTabActivity.this);

				for (Entry dir : parent.getChildren(true, false))
				{
					MusicDirectory root;

					root = !Util.isOffline(JukeboxTabActivity.this) && Util.getShouldUseId3Tags(JukeboxTabActivity.this) ? musicService.getAlbum(dir.getId(), dir.getTitle(), false, JukeboxTabActivity.this, this) : musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, JukeboxTabActivity.this, this);

					getSongsRecursively(root, songs);
				}
			}

			private void getSongsForArtist(String id, Collection<Entry> songs) throws Exception
			{
				if (songs.size() > MAX_SONGS)
				{
					return;
				}

				MusicService musicService = MusicServiceFactory.getMusicService(JukeboxTabActivity.this);
				MusicDirectory artist = musicService.getArtist(id, "", false, JukeboxTabActivity.this, this);

				for (Entry album : artist.getChildren())
				{
					MusicDirectory albumDirectory = musicService.getAlbum(album.getId(), "", false, JukeboxTabActivity.this, this);

					for (Entry song : albumDirectory.getChildren())
					{
						if (!song.isVideo())
						{
							songs.add(song);
						}
					}
				}
			}

			@Override
			protected void done(List<Entry> songs)
			{
				if (Util.getShouldSortByDisc(JukeboxTabActivity.this))
				{
					Collections.sort(songs, new EntryByDiscAndTrackComparator());
				}

				DownloadService downloadService = getDownloadService();
				if (!songs.isEmpty() && downloadService != null)
				{
					if (!append && !playNext && !unpin && !background)
					{
						downloadService.clear();
					}
					warnIfNetworkOrStorageUnavailable();
					if (!background)
					{
						if (unpin)
						{
							downloadService.unpin(songs);
						}
						else
						{
							downloadService.download(songs, save, autoplay, playNext, shuffle, false);
							if (!append && Util.getShouldTransitionOnPlaybackPreference(JukeboxTabActivity.this))
							{
								Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, DownloadActivity.class);
							}
						}
					}
					else
					{
						if (unpin)
						{
							downloadService.unpin(songs);
						}
						else
						{
							downloadService.downloadBackground(songs, save);
						}
					}
				}
			}
		};

		task.execute();
	}

	protected void setActionBarDisplayHomeAsUp(boolean enabled)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setDisplayHomeAsUpEnabled(enabled);
		}
	}

	protected void setActionBarTitle(int id)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setTitle(id);
		}
	}

	protected void setActionBarSubtitle(CharSequence title)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setSubtitle(title);
		}
	}

	protected void setActionBarSubtitle(int id)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setSubtitle(id);
		}
	}

	protected CharSequence getActionBarSubtitle()
	{
		ActionBar actionBar = getActionBar();
		CharSequence subtitle = null;

		if (actionBar != null)
		{
			subtitle = actionBar.getSubtitle();
		}

		return subtitle;
	}

	private void setUncaughtExceptionHandler()
	{
		Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
		if (!(handler instanceof SubsonicUncaughtExceptionHandler))
		{
			Thread.setDefaultUncaughtExceptionHandler(new SubsonicUncaughtExceptionHandler(this));
		}
	}

	/**
	 * Logs the stack trace of uncaught exceptions to a file on the SD card.
	 */
	private static class SubsonicUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
	{

		private final Thread.UncaughtExceptionHandler defaultHandler;
		private final Context context;
		private static final String filename = "jukebox-stacktrace.txt";

		private SubsonicUncaughtExceptionHandler(Context context)
		{
			this.context = context;
			defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		}

		@Override
		public void uncaughtException(Thread thread, Throwable throwable)
		{
			File file = null;
			PrintWriter printWriter = null;

			try
			{
				file = new File(Environment.getExternalStorageDirectory(), filename);
				printWriter = new PrintWriter(file);
				printWriter.println("Android API level: " + Build.VERSION.SDK_INT);
				printWriter.println("Sonic Jukebox version name: " + Util.getVersionName(context));
				printWriter.println("Sonic Jukebox version code: " + Util.getVersionCode(context));
				printWriter.println();
				throwable.printStackTrace(printWriter);
				Log.i(TAG, "Stack trace written to " + file);
			}
			catch (Throwable x)
			{
				Log.e(TAG, "Failed to write stack trace to " + file, x);
			}
			finally
			{
				Util.close(printWriter);
				if (defaultHandler != null)
				{
					defaultHandler.uncaughtException(thread, throwable);
				}
			}
		}
	}

	@Override
	public void onClick(View v)
	{
		menuActiveViewId = v.getId();
		menuDrawer.setActiveView(v);

		Intent intent;

		switch (menuActiveViewId)
		{
			case R.id.menu_home:
				intent = new Intent(JukeboxTabActivity.this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
				break;
			case R.id.menu_browse:
				intent = new Intent(JukeboxTabActivity.this, SelectArtistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
				break;
			case R.id.menu_search:
				intent = new Intent(JukeboxTabActivity.this, SearchActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
				break;
			case R.id.menu_playlists:
				intent = new Intent(JukeboxTabActivity.this, SelectPlaylistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
				break;
			case R.id.menu_shares:
				intent = new Intent(JukeboxTabActivity.this, ShareActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, intent);
				break;
			case R.id.menu_chat:
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, ChatActivity.class);
				break;
			case R.id.menu_bookmarks:
				Util.startActivityForResultWithoutTransition(this, BookmarkActivity.class);
				break;
			case R.id.menu_now_playing:
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, DownloadActivity.class);
				break;
			case R.id.menu_settings:
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, SettingsActivity.class);
				break;
			case R.id.menu_about:
				Util.startActivityForResultWithoutTransition(JukeboxTabActivity.this, HelpActivity.class);
				break;
		}

		menuDrawer.closeMenu(true);
	}

	@Override
	protected void onRestoreInstanceState(Bundle inState)
	{
		super.onRestoreInstanceState(inState);
		menuDrawer.restoreState(inState.getParcelable(STATE_MENUDRAWER));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putParcelable(STATE_MENUDRAWER, menuDrawer.saveState());
		outState.putInt(STATE_ACTIVE_VIEW_ID, menuActiveViewId);
		outState.putInt(STATE_ACTIVE_POSITION, activePosition);
	}

	@Override
	public void onBackPressed()
	{
		final int drawerState = menuDrawer.getDrawerState();

		if (drawerState == MenuDrawer.STATE_OPEN || drawerState == MenuDrawer.STATE_OPENING)
		{
			menuDrawer.closeMenu(true);
			return;
		}

		super.onBackPressed();
	}

	protected class SwipeDetector implements OnTouchListener
	{
		public SwipeDetector(JukeboxTabActivity activity, final DownloadService downloadService)
		{
			this.downloadService = downloadService;
			this.activity = activity;
		}

		private static final int MIN_DISTANCE = 30;
		private float downX, downY, upX, upY;
		private DownloadService downloadService;
		private JukeboxTabActivity activity;

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			switch (event.getAction())
			{
				case MotionEvent.ACTION_DOWN:
				{
					downX = event.getX();
					downY = event.getY();
					return false;
				}
				case MotionEvent.ACTION_UP:
				{
					upX = event.getX();
					upY = event.getY();

					float deltaX = downX - upX;
					float deltaY = downY - upY;

					if (Math.abs(deltaX) > MIN_DISTANCE)
					{
						// left or right
						if (deltaX < 0)
						{
							downloadService.previous();
							return false;
						}
						if (deltaX > 0)
						{
							downloadService.next();
							return false;
						}
					}
					else if (Math.abs(deltaY) > MIN_DISTANCE)
					{
						if (deltaY < 0)
						{
							JukeboxTabActivity.nowPlayingHidden = true;
							activity.hideNowPlaying();
							return false;
						}
						if (deltaY > 0)
						{
							return false;
						}
					}

					Util.startActivityForResultWithoutTransition(activity, DownloadActivity.class);
					return false;
				}
			}

			return false;
		}
	}
}