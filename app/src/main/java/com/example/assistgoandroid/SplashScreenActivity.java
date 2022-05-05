//package com.example.assistgoandroid;
//
//import android.annotation.SuppressLint;
//import android.content.Intent;
//import android.os.Bundle;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.twilio.video.app.auth.Authenticator;
//import com.twilio.video.app.ui.ScreenSelector;
//import com.twilio.video.app.ui.room.RoomActivity;
//
//import javax.inject.Inject;
//
//import dagger.hilt.android.AndroidEntryPoint;
//
//@SuppressLint("CustomSplashScreen")
//@AndroidEntryPoint
//public class SplashScreenActivity extends AppCompatActivity {
//
//    @Inject
//    Authenticator authenticator;
//
//    @Inject
//    ScreenSelector screenSelector;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        Intent newIntent;
//        if (authenticator.loggedIn())
//            newIntent = new Intent(this, RoomActivity.class);
//        else
//            newIntent = new Intent(this, screenSelector.getLoginScreen());
//
//        startActivity(newIntent.setData(getIntent().getData()));
//        finish();
//    }
//}