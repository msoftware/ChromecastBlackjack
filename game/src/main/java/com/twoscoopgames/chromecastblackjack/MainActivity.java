package com.twoscoopgames.chromecastblackjack;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends ActionBarActivity {

    private static String TAG="MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        ChromeCastAdapter.getInstance(this).setMediaRouteButtonSelector(mediaRouteMenuItem);

        return true;
    }

    //private int i = 0;
    public void startChromeCast(View view) {
        Log.d(TAG, "startChromeCast");
        Intent intent = new Intent(this, BettingActivity.class);
        startActivity(intent);
        //ChromeCastAdapter.getInstance(this).sendMessage("derp " + i);
        //i++;
    }
}