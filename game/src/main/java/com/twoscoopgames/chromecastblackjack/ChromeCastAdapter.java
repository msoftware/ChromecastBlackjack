package com.twoscoopgames.chromecastblackjack;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class ChromeCastAdapter {
    private static ChromeCastAdapter instance = null;

    public static ChromeCastAdapter getInstance(Context activity) {
        if (instance == null) {
            instance = new ChromeCastAdapter(activity);
        }
        return instance;
    }

    private static final String TAG = "ChromeCastAdapter";
    private Context activity;
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private MyMediaRouterCallback mediaRouterCallback;
    private CastDevice selectedDevice;
    private GoogleApiClient apiClient;

    private ChromeCastAdapter(Context activity) {
        this.activity = activity;

        mediaRouter = MediaRouter.getInstance(activity);
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(activity.getResources().getString(R.string.app_id)))
                .build();
        mediaRouterCallback = new MyMediaRouterCallback();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public void setMediaRouteButtonSelector(MenuItem mediaRouteMenuItem) {
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
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
        apiClient = new GoogleApiClient.Builder(activity)
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
    private boolean applicationStarted;
    private String sessionId;

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
                    Cast.CastApi.launchApplication(apiClient, activity.getResources().getString(R.string.app_id), false)
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
                    Log.e(TAG, "Failed to launch application", e);
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

    public void sendMessage(String message) {
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

    private class BlackjackChannel implements Cast.MessageReceivedCallback {
        public String getNamespace() {
            return activity.getResources().getString(R.string.namespace);
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }
    }
}
