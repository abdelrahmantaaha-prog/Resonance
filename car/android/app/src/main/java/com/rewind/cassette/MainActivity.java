package com.rewind.cassette;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

import com.rewind.cassette.media.MediaSessionPlugin;

public class MainActivity extends BridgeActivity {

    private static final int REQ_NOTIFICATIONS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The media session plugin is vendored into this app rather than pulled
        // from npm, so Capacitor cannot discover it from capacitor.plugins.json —
        // it has to be registered by hand, and before super.onCreate() so the
        // bridge sees it while it is wiring up. The JS side still reaches it as
        // registerPlugin('MediaSession'); the @CapacitorPlugin name is unchanged.
        registerPlugin(MediaSessionPlugin.class);

        super.onCreate(savedInstanceState);

        // Cassette drives the YouTube IFrame player from JavaScript. Android's
        // WebView refuses programmatic playback until the user touches the page,
        // which would leave a phone docked in the car silent on track one.
        // Opting out is what lets the queue roll on by itself.
        //
        // BridgeActivity.onCreate() bails out early (leaving bridge == null) on
        // devices with no usable WebView, so both hops are guarded.
        Bridge bridge = getBridge();
        if (bridge != null) {
            WebView webView = bridge.getWebView();
            if (webView != null) {
                WebSettings settings = webView.getSettings();
                settings.setMediaPlaybackRequiresUserGesture(false);
            }
        }

        // Android 13+ suppresses the media notification without this grant, and
        // that notification IS the media session's surface — no notification
        // means no title/artist/artwork on the head unit. An unknown request code
        // falls through BridgeActivity's handler harmlessly, so there is nothing
        // to override on the way back.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[] { Manifest.permission.POST_NOTIFICATIONS }, REQ_NOTIFICATIONS);
        }
    }
}
