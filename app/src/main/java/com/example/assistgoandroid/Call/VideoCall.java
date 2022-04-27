package com.example.assistgoandroid.Call;

import static android.widget.Toast.LENGTH_SHORT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.example.assistgoandroid.Helpers.api.TwilioSDKStarterAPI;
import com.example.assistgoandroid.Helpers.api.model.Invite;
import com.example.assistgoandroid.Helpers.api.model.Notification;
import com.example.assistgoandroid.Helpers.service.RegistrationIntentService;
import com.example.assistgoandroid.R;
import com.example.assistgoandroid.models.Contact;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import retrofit2.Callback;
import tvi.webrtc.Camera1Enumerator;
import tvi.webrtc.VideoSink;
import retrofit2.Response;

/*
    Video invitation
    Resources
    https://www.twilio.com/blog/add-muting-unmuting-video-chat-app-30-seconds
    https://www.twilio.com/docs/video/android-getting-started#connect-to-a-room
    https://github.com/twilio/video-quickstart-android/blob/e83e170abc7184e27fa0c2c15818320e2d9c8e70/exampleVideoInvite/src/main/java/com/twilio/video/examples/videoinvite/VideoInviteActivity.java
 */
public class VideoCall extends AppCompatActivity {

    // misc
    private final String TAG = "VideoCall";
    private Contact contact;

    // Video views
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    // Local tracks
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;

    // API and data
    private String accessToken;
    public static final String TWILIO_SDK_STARTER_SERVER_URL = "https://rackley-iguana-5070.twil.io/video-token";
    private Room room;
    private String frontCameraId = null;
    private String backCameraId = null;
    private boolean disconnectedFromOnDestroy;
    private int previousAudioMode;
    private String identity;
    private String remoteParticipantIdentity;

    /*
     * A LocalParticipant represents the identity and tracks provided by this instance
     */
    private LocalParticipant localParticipant;

    /*
     * Intent keys used to provide information about a video notification
     */
    public static final String ACTION_VIDEO_NOTIFICATION = "VIDEO_NOTIFICATION";
    public static final String VIDEO_NOTIFICATION_ROOM_NAME = "VIDEO_NOTIFICATION_ROOM_NAME";
    public static final String VIDEO_NOTIFICATION_TITLE = "VIDEO_NOTIFICATION_TITLE";

    /*
     * Intent keys used to obtain a token and register with Twilio Notify
     */
    public static final String ACTION_REGISTRATION = "ACTION_REGISTRATION";
    public static final String REGISTRATION_ERROR = "REGISTRATION_ERROR";
    public static final String REGISTRATION_IDENTITY = "REGISTRATION_IDENTITY";
    public static final String REGISTRATION_TOKEN = "REGISTRATION_TOKEN";

    // Helper Objects
    private final Camera1Enumerator camera1Enumerator = new Camera1Enumerator();
    private CameraCapturer cameraCapturer;
    private VideoSink localVideoView;
    private AudioManager audioManager;
    private boolean isReceiverRegistered;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private NotificationManager notificationManager;
    private Intent cachedVideoNotificationIntent;
    private AlertDialog alertDialog;

    /*
     * The tag used to notify others when this identity is connecting to a Video room.
     */
    public static final List<String> NOTIFY_TAGS =
            new ArrayList<String>() {
                {
                    add("video");
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_chat_page);

        //Control buttons
        ImageView switchCameraBtn = findViewById(R.id.switchCamBtn);
        ImageView videochatBtn = findViewById(R.id.videochatBtn);
        ImageView muteBtn = findViewById(R.id.muteBtn);
        ImageView hangupBtn = findViewById(R.id.hangupBtn);

        //Video views
        primaryVideoView = findViewById(R.id.primary_video_view);
        thumbnailVideoView = findViewById(R.id.thumbnail_video_view);

        // Contact information
        contact = getIntent().getParcelableExtra("CONTACT_CARD");
        Log.i(TAG, "Contact is " + contact);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        /*
         * Setup the broadcast receiver to be notified of video notification messages
         */
        localBroadcastReceiver = new LocalBroadcastReceiver();
        registerReceiver();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = getIntent();

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else if (intent != null && intent.getAction().equals(ACTION_REGISTRATION)) {
            handleRegistration(intent);
        } else if (intent != null && intent.getAction().equals(ACTION_VIDEO_NOTIFICATION)) {
            /*
             * Cache the video invite notification intent until an access token is obtained through
             * registration
             */
            cachedVideoNotificationIntent = intent;
            register();
        } else {
            register();
        }

        View.OnClickListener switchCameraClick = v -> switchCamera();


        View.OnClickListener videoChatClick = v -> {
            //todo: make sure works
            if(localVideoTrack != null){
                boolean enable = !localVideoTrack.isEnabled();
                localVideoTrack.enable(enable);
            }

        };

        //todo icon for unmute/mute
        View.OnClickListener muteClick = v -> {
            if (localAudioTrack != null) {
                boolean enable = !localAudioTrack.isEnabled();
                localAudioTrack.enable(enable);
                //changing icon
//                int icon = enable ? R.drawable.ic_mic_white_24dp : R.drawable.ic_mic_off_black_24dp;
//                muteActionFab.setImageDrawable(
//                        ContextCompat.getDrawable(VideoInviteActivity.this, icon));
            }
        };

        View.OnClickListener hangupClick = v -> {
            /*
             * Disconnect from room
             */
            if (room != null) {
                room.disconnect();
            }
            //intializeUI();
            finish();
        };

        switchCameraBtn.setOnClickListener(switchCameraClick);
        videochatBtn.setOnClickListener(videoChatClick);
        muteBtn.setOnClickListener(muteClick);
        hangupBtn.setOnClickListener(hangupClick);

        /*
         * Connect to room
         */
        connectToRoom("room");
        /*
         * Notify other participants to join the room
         */
        this.notify("room");
    }

    /*
     * Called when a notification is clicked and this activity is in the background or closed
     * todo
     */
    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction().equals(ACTION_VIDEO_NOTIFICATION)) {
            handleVideoNotificationIntent(intent);
        }
    }

    //todo
    private void handleRegistration(Intent intent) {
        String registrationError = intent.getStringExtra(REGISTRATION_ERROR);
        if (registrationError != null) {
            Log.e(TAG, "REGISTRATION ERROR");
        } else {
            createLocalTracks();
            identity = intent.getStringExtra(REGISTRATION_IDENTITY);
            accessToken = intent.getStringExtra(REGISTRATION_TOKEN);
//            identityTextView.setText(identity);
//            statusTextView.setText(R.string.registered);
            //intializeUI();
            finish();
            if (cachedVideoNotificationIntent != null) {
                handleVideoNotificationIntent(cachedVideoNotificationIntent);
                cachedVideoNotificationIntent = null;
            }
        }
    }

    //todo
    private void handleVideoNotificationIntent(Intent intent) {
        notificationManager.cancelAll();
        /*
         * Only handle the notification if not already connected to a Video Room
         */
        if (room == null) {
            String title = intent.getStringExtra(VIDEO_NOTIFICATION_TITLE);
            String dialogRoomName = intent.getStringExtra(VIDEO_NOTIFICATION_ROOM_NAME);
            showVideoNotificationConnectDialog(title, dialogRoomName);
        }
    }

    /*
     * Register to obtain a token and register a binding with Twilio Notify
     */
    private void register() {
        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);
    }

    //todo
    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_VIDEO_NOTIFICATION);
            intentFilter.addAction(ACTION_REGISTRATION);
            LocalBroadcastManager.getInstance(this)
                    .registerReceiver(localBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    //todo
    private void unregisterReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver);
        isReceiverRegistered = false;
    }

    //todo
    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_REGISTRATION)) {
                handleRegistration(intent);
            } else if (action.equals(ACTION_VIDEO_NOTIFICATION)) {
                handleVideoNotificationIntent(intent);
            }
        }
    }

    /*
     * Creates a connect UI dialog to handle notifications
     * todo
     */
    private void showVideoNotificationConnectDialog(String title, String roomName) {
        EditText roomEditText = new EditText(this);
        roomEditText.setText(roomName);
        // Use the default color instead of the disabled color
        int currentColor = roomEditText.getCurrentTextColor();
        roomEditText.setEnabled(false);
        roomEditText.setTextColor(currentColor);
        alertDialog =
                createConnectDialog(
                        title,
                        roomEditText,
                        videoNotificationConnectClickListener(roomEditText),
                        cancelConnectDialogClickListener(),
                        this);
        alertDialog.show();
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return (dialog, which) -> {
            //intializeUI();
            finish();
            alertDialog.dismiss();
        };
    }

    public static AlertDialog createConnectDialog(
            String title,
            EditText roomEditText,
            DialogInterface.OnClickListener callParticipantsClickListener,
            DialogInterface.OnClickListener cancelClickListener,
            Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setIcon(R.drawable.call_icon);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setPositiveButton("Connect", callParticipantsClickListener);
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setView(roomEditText);
        return alertDialogBuilder.create();
    }

    private void switchCamera() {
        if (cameraCapturer != null) {
            String cameraId =
                    cameraCapturer.getCameraId().equals(getFrontCameraId())
                            ? getBackCameraId()
                            : getFrontCameraId();
            cameraCapturer.switchCamera(cameraId);
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                thumbnailVideoView.setMirror(cameraId.equals(getBackCameraId()));
            } else {
                primaryVideoView.setMirror(cameraId.equals(getBackCameraId()));
            }
        }
    }

    //https://www.twilio.com/docs/video/android-getting-started#connect-to-a-room
    //The name of the Room specifies which Room you wish to join.
    // If a Room by that name does not already exist, it will be created upon connection.
    // If a Room by that name is already active, you'll be connected to the Room and receive notifications from any other Participants
    // also connected to the same Room. Room names must be unique within an account.
    public void connectToRoom(String roomName) {
        enableAudioFocus(true);
        enableVolumeControl(true);

        ConnectOptions.Builder connectOptionsBuilder =
                new ConnectOptions.Builder(accessToken).roomName(roomName);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
        // setDisconnectBehavior();

        // ... Assume we have received the connected callback
        // After receiving the connected callback the LocalParticipant becomes available
        LocalParticipant localParticipant = room.getLocalParticipant();
        assert localParticipant != null;
        Log.i("LocalParticipant ", localParticipant.getIdentity());

        // Get the first participant from the room
        RemoteParticipant participant = room.getRemoteParticipants().get(0);
        Log.i("HandleParticipants", participant.getIdentity() + " is in the room.");
    }

    private void enableAudioFocus(boolean focus) {
        if (focus) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(i -> {})
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(
                    null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }


    void notify(final String roomName) {
        //String inviteJsonString;
        Invite invite = new Invite(identity, roomName);

        /*
         * Use Twilio Notify to let others know you are connecting to a Room
         */
        Notification notification =
                new Notification(
                        "Join " + identity + " in room " + roomName,
                        identity + " has invited you to join video room " + roomName,
                        invite.getMap(),
                        NOTIFY_TAGS);
        TwilioSDKStarterAPI.notify(notification)
                .enqueue(
                        new Callback<Void>() {

                            @Override
                            public void onResponse(retrofit2.Call<Void> call, Response<Void> response) {
                                if (!response.isSuccessful()) {
                                    String message =
                                            "Sending notification failed: "
                                                    + response.code()
                                                    + " "
                                                    + response.message();
                                    Log.e(TAG, message);
                                }
                            }

                            @Override
                            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                                String message = "Sending notification failed: " + t.getMessage();
                                Log.e(TAG, message);
                            }
                        });
    }

    /*
     * The behavior applied to disconnect
     */
//    private void setDisconnectBehavior() {
//        connectActionFab.setImageDrawable(
//                ContextCompat.getDrawable(this, R.drawable.ic_call_end_white_24dp));
//        connectActionFab.show();
//        connectActionFab.setOnClickListener(hangup());
//    }

    /*
     * Called when remote participant joins the room
     */
    @SuppressLint("SetTextI18n")
    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Toast.makeText(this, "Rendering multiple participants not supported in this app", LENGTH_SHORT).show();
        }
        remoteParticipantIdentity = remoteParticipant.getIdentity();
        Toast.makeText(this, "RemoteParticipant " + remoteParticipantIdentity + " joined", LENGTH_SHORT).show();

        /*
         * Add remote participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant media events
         */
        remoteParticipant.setListener(mediaListener());
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        moveLocalVideoToThumbnailView();
        primaryVideoView.setMirror(false);
        videoTrack.addSink(primaryVideoView);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            if (localVideoTrack != null) {
                localVideoTrack.removeSink(primaryVideoView);
                localVideoTrack.addSink(thumbnailVideoView);
            }
            localVideoView = thumbnailVideoView;
            thumbnailVideoView.setMirror(cameraCapturer.getCameraId().equals(getFrontCameraId()));
        }
    }

    /*
     * Called when participant leaves the room
     */
    @SuppressLint("SetTextI18n")
    private void removeParticipant(RemoteParticipant remoteParticipant) {
        Toast.makeText(this, "Participant " + remoteParticipant.getIdentity() + " left.", Toast.LENGTH_LONG).show();
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            return;
        }

        /*
         * Remove participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track.
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(Objects.requireNonNull(remoteVideoTrackPublication.getRemoteVideoTrack()));
            }
        }
        moveLocalVideoToPrimaryView();
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeSink(primaryVideoView);
    }

    private void moveLocalVideoToPrimaryView() {
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            localVideoTrack.removeSink(thumbnailVideoView);
            thumbnailVideoView.setVisibility(View.GONE);
            localVideoTrack.removeSink(primaryVideoView);
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(cameraCapturer.getCameraId().equals(getFrontCameraId()));
        }
    }

    /*
     * Room events listener
     */
    @SuppressLint("SetTextI18n")
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                localParticipant = room.getLocalParticipant();
                Log.i(TAG, "Connected to + room.getName()");
                setTitle(room.getName());

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                Log.i(TAG, "Failed to connect");
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                localParticipant = null;
                Log.i(TAG, "Disconnected from " + room.getName());
                room = null;
                enableAudioFocus(false);
                enableVolumeControl(false);
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    moveLocalVideoToPrimaryView();
                    finish(); //return to prev screen
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                addRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                removeParticipant(remoteParticipant);
            }

            @Override
            public void onRecordingStarted(Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
            }

            @Override
            public void onReconnecting(Room room, TwilioException exception) {
                Log.i(TAG, "Reconnecting to " + room.getName());
            }

            @Override
            public void onReconnected(Room room) {
                Log.i(TAG, "Connected to " + room.getName());
            }
        };
    }

    private void enableVolumeControl(boolean volumeControl) {
        if (volumeControl) {
            /*
             * Enable changing the volume using the up/down keys during a conversation
             */
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        } else {
            setVolumeControlStream(getVolumeControlStream());
        }
    }

    @SuppressLint("SetTextI18n")
    private RemoteParticipant.Listener mediaListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(TAG, "onAudioTrackPublished");
            }

            @Override
            public void onAudioTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(TAG, "onAudioTrackPublished");
            }

            @Override
            public void onVideoTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(TAG, "onAudioTrackPublished");
            }

            @Override
            public void onVideoTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(TAG, "onAudioTrackUnpublished");
            }

            @Override
            public void onDataTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(TAG, "onDataTrackPublished");
            }

            @Override
            public void onDataTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(TAG, "onDataTrackUnpublished");
            }

            @Override
            public void onAudioTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    RemoteAudioTrack remoteAudioTrack) {
                Log.i(TAG, "onAudioTrackSubscribed");
            }

            @Override
            public void onAudioTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    RemoteAudioTrack remoteAudioTrack) {
                Log.i(TAG, "onAudioTrackUnsubscribed");
            }

            @Override
            public void onAudioTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    TwilioException twilioException) {
                Log.i(TAG, "onAudioTrackSubscriptionFailed");
            }

            @Override
            public void onVideoTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    RemoteVideoTrack remoteVideoTrack) {
                Log.i(TAG, "onVideoTrackSubscribed");
                addRemoteParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    RemoteVideoTrack remoteVideoTrack) {
                Log.i(TAG, "onVideoTrackUnsubscribed");
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    TwilioException twilioException) {
                Log.i(TAG, "onVideoTrackSubscriptionFailed");
            }

            @Override
            public void onDataTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    RemoteDataTrack remoteDataTrack) {
                Log.i(TAG, "onDataTrackSubscribed");
            }

            @Override
            public void onDataTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    RemoteDataTrack remoteDataTrack) {
                Log.i(TAG, "onDataTrackUnsubscribed");
            }

            @Override
            public void onDataTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    TwilioException twilioException) {
                Log.i(TAG, "onDataTrackSubscriptionFailed");
            }

            @Override
            public void onAudioTrackEnabled(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {}

            @Override
            public void onAudioTrackDisabled(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {}

            @Override
            public void onVideoTrackEnabled(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {}

            @Override
            public void onVideoTrackDisabled(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {}
        };
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        if (localVideoTrack == null
                && checkPermissionForCameraAndMicrophone()
                && cameraCapturer != null) {
            localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturer);
            assert localVideoTrack != null;
            localVideoTrack.addSink(localVideoView);

            /*
             * If connected to a Room then share the local video track.
             */
            if (localParticipant != null) {
                localParticipant.publishTrack(localVideoTrack);
            }
        }

        /*
         * Update reconnecting UI
         */
        if (room != null) {
            Log.i(TAG, "Connected to " + room.getName());
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver();
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         *
         * If this local video track is being shared in a Room, participants will be notified
         * that the track has been unpublished.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, unpublish from room before
             * releasing the video track. Participants will be notified that the track has been
             * removed.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }
            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onPause();
    }

    private DialogInterface.OnClickListener videoNotificationConnectClickListener(
            final EditText roomEditText) {
        return (dialog, which) -> {
            /*
             * Connect to room
             */
            connectToRoom(roomEditText.getText().toString());
        };
    }



    @Override
    protected void onDestroy() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }

        super.onDestroy();
    }

    private boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone() {

        // request permission in fragment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    100);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_LONG).show();
        }
    }

    private String getFrontCameraId() {
        if (frontCameraId == null) {
            for (String deviceName : camera1Enumerator.getDeviceNames()) {
                if (camera1Enumerator.isFrontFacing(deviceName)) {
                    frontCameraId = deviceName;
                }
            }
        }

        return frontCameraId;
    }

    private String getBackCameraId() {
        if (backCameraId == null) {
            for (String deviceName : camera1Enumerator.getDeviceNames()) {
                if (camera1Enumerator.isBackFacing(deviceName)) {
                    backCameraId = deviceName;
                }
            }
        }

        return backCameraId;
    }

    private void createLocalTracks() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true);

        // Share your camera
        cameraCapturer = new CameraCapturer(this, getFrontCameraId());
        localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturer);
        primaryVideoView.setMirror(true);
        localVideoTrack.addSink(primaryVideoView);
        localVideoView = primaryVideoView;
    }
}


