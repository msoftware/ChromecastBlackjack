package com.twoscoopgames.chromecastblackjack;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class BettingActivity extends ActionBarActivity {

    private TextView walletAmount;
    private TextView currentBet;
    private Button bet1;
    private Button bet5;
    private Button bet25;
    private Button bet100;

    private int wallet = 1000;
    private int bet = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_betting);
        walletAmount = (TextView) findViewById(R.id.wallet_amount);
        currentBet = (TextView) findViewById(R.id.current_bet);
        bet1 = (Button)findViewById(R.id.bet1);
        bet5 = (Button)findViewById(R.id.bet5);
        bet25 = (Button)findViewById(R.id.bet25);
        bet100 = (Button)findViewById(R.id.bet100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.betting, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        ChromeCastAdapter.getInstance(this).setMediaRouteButtonSelector(mediaRouteMenuItem);

        return true;
    }

    public void bet1(View view) {
        bet(1);
    }

    private void bet(int amount) {
        wallet -= amount;
        bet += amount;
        bet1.setEnabled(wallet >= 1);
        bet5.setEnabled(wallet >= 5);
        bet25.setEnabled(wallet >= 25);
        bet100.setEnabled(wallet >= 100);
        walletAmount.setText("$" + wallet);
        currentBet.setText("$" + bet);
    }

    public void bet5(View view) {
        bet(5);
    }

    public void bet25(View view) {
        bet(25);
    }

    public void bet100(View view) {
        bet(100);
    }

    public void clearBet(View view) {
        bet(-bet);
    }

    public void submitBet(View view) {
        ChromeCastAdapter.getInstance(this).sendMessage("bet " + bet);
    }
}
