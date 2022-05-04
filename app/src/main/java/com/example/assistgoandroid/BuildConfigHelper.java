package com.example.assistgoandroid;

import com.twilio.video.app.BuildConfig;

public final class BuildConfigHelper {

    //https://stackoverflow.com/questions/21365928/gradle-how-to-use-buildconfig-in-an-android-library-with-a-flag-that-gets-set
    public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final String APPLICATION_ID = "com.twilio.video.app.community.debug";
    public static final String BUILD_TYPE = BuildConfig.BUILD_TYPE;
    public static final String FLAVOR = BuildConfig.FLAVOR;
    public static final int VERSION_CODE = 121;
    public static final String VERSION_NAME = "0.1.0-debug";

}