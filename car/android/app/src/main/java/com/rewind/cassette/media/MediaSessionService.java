package com.rewind.cassette.media;

import com.rewind.cassette.R;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.os.IBinder;
import android.os.Binder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vendored from @jofr/capacitor-media-session 4.0.0, with one change that the
 * published plugin cannot make: this extends MediaBrowserServiceCompat instead
 * of Service.
 *
 * Android Auto does not look at media sessions. It enumerates apps that publish
 * a MediaBrowserService and declares itself a media app via the
 * com.google.android.gms.car.application meta-data. Without both, the head unit
 * never learns the app exists — which is why Cassette's controls worked on the
 * phone's notification and lock screen while the car showed an empty media list
 * and the steering wheel had nothing to route to.
 *
 * The browse tree is deliberately empty: Cassette's queue is built by Gemini in
 * the WebView and is not addressable as static media ids. onGetRoot() still has
 * to return a non-null root, or Android Auto treats the connection as refused
 * and hides the app again.
 */
public class MediaSessionService extends MediaBrowserServiceCompat {
    private static final String TAG = "MediaSessionService";

    private MediaSessionCompat mediaSession;
    private boolean sessionTokenSet = false;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private MediaMetadataCompat.Builder mediaMetadataBuilder;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private MediaStyle notificationStyle;
    private final Map<String, NotificationCompat.Action> notificationActions = new HashMap<>();
    private final Map<String, Long> playbackStateActions = new HashMap<>();
    private final String[] possibleActions = {"previoustrack", "seekbackward", "play", "pause", "seekforward", "nexttrack", "seekto", "stop"};
    final Set<String> possibleCompactViewActions = new HashSet<>(Arrays.asList("previoustrack", "play", "pause", "nexttrack", "stop"));
    private static final int NOTIFICATION_ID = 1;

    private int playbackState = PlaybackStateCompat.STATE_NONE;
    private String title = "";
    private String artist = "";
    private String album = "";
    private Bitmap artwork = null;
    private long duration = 0;
    private long position = 0;
    private float playbackSpeed = 1.0F;

    private boolean possibleActionsUpdate = true;
    private boolean playbackStateUpdate = false;
    private boolean mediaMetadataUpdate = false;
    private boolean notificationUpdate = false;

    private MediaSessionPlugin plugin;
    private MediaSessionCallback callback;

    private final IBinder binder = new LocalBinder();

    public final class LocalBinder extends Binder {
        MediaSessionService getService() {
            return MediaSessionService.this;
        }
    }

    /**
     * Two kinds of client bind here now. Android Auto binds with the
     * MediaBrowserService action and must be handed the framework's own binder
     * via super; the Capacitor plugin binds with no action and wants the
     * LocalBinder. Returning the LocalBinder to Android Auto silently fails the
     * connection and the app disappears from the car again.
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return binder;
    }

    /**
     * Only the plugin disconnecting means playback is over. Android Auto binds
     * and unbinds as the car is started and stopped, and tearing the session
     * down on that would kill playback every time the head unit reconnected.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onUnbind(intent);
        }
        this.destroy();

        return super.onUnbind(intent);
    }

    /**
     * Android Auto refuses to show an app whose root is null, so return a real
     * (if childless) root for any caller. Cassette's queue lives in the WebView
     * and has no stable media ids to browse, so the tree stays empty and the
     * head unit falls back to the transport controls — which is all the wheel
     * needs.
     */
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("cassette_root", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(new ArrayList<>());
    }

    public void connectAndInitialize(MediaSessionPlugin plugin, Intent intent) {
        this.plugin = plugin;

        // Reuse the session across reconnects. Building a second
        // MediaSessionCompat would orphan the one Android Auto is already bound
        // to, and its token could no longer be published (see below).
        if (mediaSession == null) {
            mediaSession = new MediaSessionCompat(this, "WebViewMediaSession");
        }
        mediaSession.setCallback(new MediaSessionCallback(plugin));
        mediaSession.setActive(true);
        // Publishes the session to every MediaBrowser client. Android Auto reads
        // the token from here to bind its transport controls (and therefore the
        // steering wheel) to this session.
        //
        // MediaBrowserServiceCompat.setSessionToken() throws IllegalStateException
        // if it is ever called twice, and connectAndInitialize() runs on every
        // onServiceConnected — which fires again whenever the plugin rebinds to a
        // service that is still alive. That is a hard crash, so it is guarded.
        if (!sessionTokenSet) {
            setSessionToken(mediaSession.getSessionToken());
            sessionTokenSet = true;
        }

        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setState(PlaybackStateCompat.STATE_PAUSED, position, playbackSpeed);
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        mediaMetadataBuilder = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        mediaSession.setMetadata(mediaMetadataBuilder.build());

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("playback", "Playback", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        notificationStyle = new MediaStyle().setMediaSession(mediaSession.getSessionToken());
        notificationBuilder = new NotificationCompat.Builder(this, "playback")
                .setStyle(notificationStyle)
                .setSmallIcon(R.drawable.ic_baseline_volume_up_24)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
          startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }

        notificationActions.put("play", new NotificationCompat.Action(
                R.drawable.ic_baseline_play_arrow_24, "Play", MediaButtonReceiver.buildMediaButtonPendingIntent(this, (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY))
        ));
        notificationActions.put("pause", new NotificationCompat.Action(
                R.drawable.ic_baseline_pause_24, "Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this, (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE))
        ));
        notificationActions.put("seekbackward", new NotificationCompat.Action(
                R.drawable.ic_baseline_replay_30_24, "Previous Track", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)
        ));
        notificationActions.put("seekforward", new NotificationCompat.Action(
                R.drawable.ic_baseline_forward_30_24, "Next Track", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)
        ));
        notificationActions.put("previoustrack", new NotificationCompat.Action(
                R.drawable.ic_baseline_skip_previous_24, "Previous Track", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        ));
        notificationActions.put("nexttrack", new NotificationCompat.Action(
                R.drawable.ic_baseline_skip_next_24, "Next Track", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        ));
        notificationActions.put("stop", new NotificationCompat.Action(
                R.drawable.ic_baseline_stop_24, "Stop", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)
        ));

        playbackStateActions.put("previoustrack", PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        playbackStateActions.put("seekbackward", PlaybackStateCompat.ACTION_REWIND);
        playbackStateActions.put("play", (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY));
        playbackStateActions.put("pause", (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE));
        playbackStateActions.put("seekforward", PlaybackStateCompat.ACTION_FAST_FORWARD);
        playbackStateActions.put("nexttrack", PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        playbackStateActions.put("seekto", PlaybackStateCompat.ACTION_SEEK_TO);
        playbackStateActions.put("stop", PlaybackStateCompat.ACTION_STOP);
    }

    public void destroy() {
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    public void setPlaybackState(int playbackState) {
        if (this.playbackState != playbackState) {
            this.playbackState = playbackState;
            playbackStateUpdate = true;
            possibleActionsUpdate = true;
        }
    }

    public void setTitle(String title)  {
        if (!this.title.equals(title)) {
            this.title = title;
            mediaMetadataUpdate = true;
            notificationUpdate = true;
        }
    }

    public void setArtist(String artist) {
        if (!this.artist.equals(artist)) {
            this.artist = artist;
            mediaMetadataUpdate = true;
            notificationUpdate = true;
        }
    }

    public void setAlbum(String album) {
        if (!this.album.equals(album)) {
            this.album = album;
            mediaMetadataUpdate = true;
            notificationUpdate = true;
        }
    }

    public void setArtwork(Bitmap artwork) {
        this.artwork = artwork;
        mediaMetadataUpdate = true;
        notificationUpdate = true;
    }

    public void setDuration(long duration) {
        if (this.duration != duration) {
            this.duration = duration;
            mediaMetadataUpdate = true;
            notificationUpdate = true;
        }
    }

    public void setPosition(long position) {
        if (this.position != position) {
            this.position = position;
            playbackStateUpdate = true;
        }
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        if (this.playbackSpeed != playbackSpeed) {
            this.playbackSpeed = playbackSpeed;
            playbackStateUpdate = true;
        }
    }

    @SuppressLint("RestrictedApi")
    public void update() {
        if (possibleActionsUpdate) {
          if (notificationBuilder != null) {
            notificationBuilder.mActions.clear();
          }

            long activePlaybackStateActions = 0;
            int[] activeCompactViewActionIndices = new int[3];

            int notificationActionIndex = 0;
            int compactNotificationActionIndicesIndex = 0;
            for (String actionName : possibleActions) {
                if (plugin.hasActionHandler(actionName)) {
                    if (actionName.equals("play") && playbackState != PlaybackStateCompat.STATE_PAUSED) {
                        continue;
                    }
                    if (actionName.equals("pause") && playbackState != PlaybackStateCompat.STATE_PLAYING) {
                        continue;
                    }

                    if (playbackStateActions.containsKey(actionName)) {
                        activePlaybackStateActions = activePlaybackStateActions | playbackStateActions.get(actionName);
                    }

                    if (notificationActions.containsKey(actionName)) {
                        notificationBuilder.addAction(notificationActions.get(actionName));
                        if (possibleCompactViewActions.contains(actionName) && compactNotificationActionIndicesIndex < 3) {
                            activeCompactViewActionIndices[compactNotificationActionIndicesIndex] = notificationActionIndex;
                            compactNotificationActionIndicesIndex++;
                        }
                        notificationActionIndex++;
                    }
                }
            }

            if (playbackStateBuilder != null) {
              playbackStateBuilder.setActions(activePlaybackStateActions);
            }
            if (notificationStyle != null) {
              if (compactNotificationActionIndicesIndex > 0) {
                notificationStyle.setShowActionsInCompactView(Arrays.copyOfRange(activeCompactViewActionIndices, 0, compactNotificationActionIndicesIndex));
            } else {
                notificationStyle.setShowActionsInCompactView();
              }
            }

            possibleActionsUpdate = false;
            playbackStateUpdate = true;
            notificationUpdate = true;
        }

        if (playbackStateUpdate && playbackStateBuilder != null) {
            playbackStateBuilder.setState(this.playbackState, this.position, this.playbackSpeed);
            mediaSession.setPlaybackState(playbackStateBuilder.build());
            playbackStateUpdate = false;
        }

        if (mediaMetadataUpdate && mediaMetadataBuilder != null) {
            mediaMetadataBuilder
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
            mediaSession.setMetadata(mediaMetadataBuilder.build());
            mediaMetadataUpdate = false;
        }

        if (notificationUpdate && notificationBuilder != null) {
            notificationBuilder
                    .setContentTitle(title)
                    .setContentText(artist + " - " + album)
                    .setLargeIcon(artwork);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            notificationUpdate = false;
        }
    }

    public void updatePossibleActions() {
        this.possibleActionsUpdate = true;
        this.update();
    }
}
