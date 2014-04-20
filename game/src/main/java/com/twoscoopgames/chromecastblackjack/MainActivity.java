package com.twoscoopgames.chromecastblackjack;

import android.content.Intent;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class MainActivity extends ActionBarActivity {

    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private CastDevice selectedDevice;
    private MyMediaRouterCallback mediaRouterCallback;
    private GoogleApiClient apiClient;

    private static String TAG="MainActivity";
    private boolean applicationStarted;
    private String sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(getResources().getString(R.string.app_id)))
                .build();
        mediaRouterCallback = new MyMediaRouterCallback();
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        if (isFinishing()) {
            mediaRouter.removeCallback(mediaRouterCallback);
        }
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.e(TAG, "onCreateOptionsMenu");
        super.onCreateOptionsMenu(menu);

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);

        return true;
    }

    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.e(TAG, "onRouteSelected");
            selectedDevice = CastDevice.getFromBundle(info.getExtras());
            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.e(TAG, "onRouteUnselected");
            teardown();
            selectedDevice = null;
        }
    }

    private void launchReceiver() {
        Log.e(TAG, "launchReceiver");

        castListener = new Cast.Listener() {
            @Override
            public void onApplicationDisconnected(int errorCode) {
                teardown();
            }
        };

        connectionCallbacks = new ConnectionCallbacks();
        connectionFailedListener = new ConnectionFailedListener();
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(selectedDevice, castListener)
                .setDebuggingEnabled();
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(connectionCallbacks)
                .addOnConnectionFailedListener(connectionFailedListener)
                .build();
        apiClient.connect();
    }

    private Cast.Listener castListener;
    private boolean waitingForReconnect;
    private ConnectionCallbacks connectionCallbacks;
    private ConnectionFailedListener connectionFailedListener;

    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            Log.e(TAG, "onConnected");

            if (waitingForReconnect) {
                waitingForReconnect = false;
                reconnectChannels();
            } else {
                try {
                    Cast.CastApi.launchApplication(apiClient, getResources().getString(R.string.app_id), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
                                                sessionId = result.getSessionId();
                                                String applicationStatus = result.getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();
                                                Log.d(TAG,
                                                        "application name: "
                                                                + applicationMetadata
                                                                .getName()
                                                                + ", status: "
                                                                + applicationStatus
                                                                + ", sessionId: "
                                                                + sessionId
                                                                + ", wasLaunched: "
                                                                + wasLaunched);

                                                applicationStarted = true;
                                                createCustomMessageChannel();
                                            } else {
                                                Log.e(TAG, "Application could not launch");
                                                teardown();
                                            }
                                        }
                                    });

                } catch (Exception e) {
                    Log.e("CHROMECAST", "Failed to launch application", e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            waitingForReconnect = true;
        }
    }

    private void reconnectChannels() {
        Log.e(TAG, "reconnectChannels");
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            teardown();
        }
    }

    private BlackjackChannel blackjackChannel;

    private void createCustomMessageChannel() {
        blackjackChannel = new BlackjackChannel();
        try {
            Cast.CastApi.setMessageReceivedCallbacks(apiClient, blackjackChannel.getNamespace(), blackjackChannel);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating channel", e);
        }
    }

    private void sendMessage(String message) {
        if (apiClient == null || blackjackChannel == null) {
            return;
        }
        try {
            Cast.CastApi.sendMessage(apiClient, blackjackChannel.getNamespace(), message)
                    .setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    if (!result.isSuccess()) {
                                        Log.e(TAG, "Sending message failed");
                                    }
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending message", e);
        }
    }

    private void teardown() {
        Log.e(TAG, "teardown");
        if (apiClient != null) {
            if (applicationStarted) {
                if (apiClient.isConnected()) {
                    try {
                        Cast.CastApi.stopApplication(apiClient, sessionId);
                        if (blackjackChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(
                                    apiClient,
                                    blackjackChannel.getNamespace());
                            blackjackChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    apiClient.disconnect();
                }
                applicationStarted = false;
            }
            apiClient = null;
        }
        selectedDevice = null;
        waitingForReconnect = false;
        sessionId = null;
    }

    private int i = 0;
    public void startChromeCast(View view) {
        Log.d(TAG, "startChromeCast");
        //Intent intent = new Intent(this, BettingActivity.class);
        //startActivity(intent);
        sendMessage("derp " + i);
        i++;
    }

    class BlackjackChannel implements Cast.MessageReceivedCallback {
        public String getNamespace() {
            return getResources().getString(R.string.namespace);
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }
    }
}
