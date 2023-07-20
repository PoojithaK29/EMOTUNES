package com.example.musicrecomendation;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.example.musicrecomendation.EmotionRecognition.AUTH_CODE_REQUEST_CODE;
import static com.example.musicrecomendation.EmotionRecognition.AUTH_TOKEN_REQUEST_CODE;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.otaliastudios.cameraview.BitmapCallback;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.controls.Facing;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Capabilities;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class RemotePlayerActivity extends AppCompatActivity implements CheckInternetConnection.ReceiverListener{

    private static final String TAG = RemotePlayerActivity.class.getSimpleName();

    private static final String CLIENT_ID = "b26c50c6eb134d24a28ab442988ab0bc";
    private static final String REDIRECT_URI = "com.example.musicrecomendation://callback";

    private final String PLAYLIST_URI = "spotify:playlist:33SSfTs3vNZOpRdm5NdixJ";
    private String HAPPY_SONGS_URI = "spotify:playlist:2BiQVRb8LV6lbDLy9XIhMk";
    private String SAD_SONGS_URI = "spotify:playlist:3CfQlMNi5zQ6mrDqx56GXT";
    private String ANGRY_URI = "spotify:playlist:2SAlj6IpdtsyI7qqU0ZKb2";
    private String DISGUST_URI = "spotify:playlist:3qgzMg4m5tvf16PzlPgGa9";
    private String FEAR_URI = "spotify:playlist:5AM4lgcUAw5sokybXj3ny7";
    private String NEUTRAL_URI = "spotify:playlist:4PFwZ4h1LMAOwdwXqvSYHd";
    private String SURPRISE_URI = "spotify:playlist:4o5SxWNsTNLOi9ahHeJF8A";

    private boolean spotify_connected = false;

    private static SpotifyAppRemote spotifyAppRemote;


    Button subscribeToPlayerContextButton;
    Button playerContextButton;
    TextView playerStateButton;
    ImageView coverArtImageView;
    AppCompatImageButton playPauseButton;
    AppCompatSeekBar seekBar;
    TextView startTime;
    TextView endTime;

    List<View> views;
    TrackProgressBar trackProgressBar;

    Subscription<PlayerState> playerStateSubscription;
    Subscription<PlayerContext> playerContextSubscription;
    Subscription<Capabilities> capabilitiesSubscription;
    String emotion;

    private final ErrorCallback errorCallback = this::logError;

    private final Subscription.EventCallback<PlayerContext> playerContextEventCallback =
            new Subscription.EventCallback<PlayerContext>() {
                @Override
                public void onEvent(PlayerContext playerContext) {
                    playerContextButton.setText("playing " + emotion + " song");
                    playerContextButton.setTag(playerContext);
                }
            };

    private final Subscription.EventCallback<PlayerState> playerStateEventCallback =
            new Subscription.EventCallback<PlayerState>() {
                @Override
                public void onEvent(PlayerState playerState) {
                    Drawable drawable =
                            ResourcesCompat.getDrawable(
                                    getResources(), R.drawable.mediaservice_shuffle, getTheme());

                    if (playerState.track != null) {
                        playerStateButton.setText(
                                String.format(
                                        Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name));
                        playerStateButton.setTag(playerState);
                    }
                    // Update progressbar
                    if (playerState.playbackSpeed > 0) {
                        trackProgressBar.unpause();
                    } else {
                        trackProgressBar.pause();
                    }

                    // Invalidate play / pause
                    if (playerState.isPaused) {
                        playPauseButton.setImageResource(R.drawable.btn_play);
                    } else {
                        playPauseButton.setImageResource(R.drawable.btn_pause);
                    }

                    if (playerState.track != null) {
                        // Get image from track
                        spotifyAppRemote
                                .getImagesApi()
                                .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
                                .setResultCallback(
                                        bitmap -> coverArtImageView.setImageBitmap(bitmap));
                        // Invalidate seekbar length and position
                        seekBar.setMax((int) playerState.track.duration);
                        trackProgressBar.setDuration(playerState.track.duration);
                        trackProgressBar.update(playerState.playbackPosition);
                    }

                    seekBar.setEnabled(true);
                }
            };

    private EmotionRecognition emotionRecognition;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_player);
        //get permission
        getPermission(Manifest.permission.CAMERA);
        getPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);


        //set playlist uri from preference
        HAPPY_SONGS_URI = "spotify:playlist:" + getDefaultSharedPreferences(this).getString("happy", "2BiQVRb8LV6lbDLy9XIhMk");
        SAD_SONGS_URI = "spotify:playlist:" +getDefaultSharedPreferences(this).getString("sad", "3CfQlMNi5zQ6mrDqx56GXT");
        DISGUST_URI = "spotify:playlist:" +getDefaultSharedPreferences(this).getString("disgust", "3qgzMg4m5tvf16PzlPgGa9");
        FEAR_URI = "spotify:playlist:" +getDefaultSharedPreferences(this).getString("fear", "5AM4lgcUAw5sokybXj3ny7");
        NEUTRAL_URI =  "spotify:playlist:" +getDefaultSharedPreferences(this).getString("neutral", "4PFwZ4h1LMAOwdwXqvSYHd");
        ANGRY_URI = "spotify:playlist:" + getDefaultSharedPreferences(this).getString("angry", "2SAlj6IpdtsyI7qqU0ZKb2");
        SURPRISE_URI = "spotify:playlist:" +getDefaultSharedPreferences(this).getString("surprise", "4o5SxWNsTNLOi9ahHeJF8A");

        playerContextButton = findViewById(R.id.current_context_label);
        subscribeToPlayerContextButton = findViewById(R.id.subscribe_to_player_context_button);
        coverArtImageView = findViewById(R.id.image);
        playerStateButton = findViewById(R.id.current_track_label);
        playPauseButton = findViewById(R.id.play_pause_button);

        seekBar = findViewById(R.id.seek_to);
        startTime = findViewById(R.id.start_time);
        endTime = findViewById(R.id.end_time);
        seekBar.setEnabled(false);
        seekBar.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        seekBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        setButtonListeners();

        trackProgressBar = new TrackProgressBar(seekBar, startTime, endTime);

        views =
                Arrays.asList(
                        subscribeToPlayerContextButton,
                        playPauseButton,
                        findViewById(R.id.skip_prev_button),
                        findViewById(R.id.skip_next_button),
                        seekBar);

        SpotifyAppRemote.setDebugMode(true);
        emotion = "neutral";
        currentMood = PLAYLIST_URI;
        if(isInternetAvailable()){
            onDisconnected();
            connect(true,false);
            initializeFaceCapture();
            emotionRecognition = new EmotionRecognition(this);
        }
    }

    private boolean isInternetAvailable() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.new.conn.CONNECTIVITY_CHANGE");
        registerReceiver(new CheckInternetConnection(),intentFilter);
        CheckInternetConnection.Listener = this;
        ConnectivityManager manager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isConnected = networkInfo != null && networkInfo.isConnected();
        showToast("network " + isConnected);

        return true;
    }


    @Override
    public void onNetworkChange(boolean isConnected) {
        showToast("network " + isConnected);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        emotionRecognition.cancelCall();
        emotionRecognition.mClassifier.close();
        spotify_connected = false;
//        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        for (File tempFile : picturesDir.listFiles()) {
//            tempFile.delete();
//        }
    }

    void showToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
    void showToast1(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
    private String currentMood = PLAYLIST_URI;
    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(spotifyAppRemote);
        onDisconnected();
    }

    private void onConnected() {
        for (View input : views) {
            input.setEnabled(true);
        }

        subscribedToPlayerState();
        subscribedToPlayerContext();
    }


    private void onDisconnected() {
        spotify_connected = false;
        ((TextView)findViewById(R.id.connect_authorize_button)).setText(R.string.connect);
        for (View view : views) {
            view.setEnabled(false);
        }
        coverArtImageView.setImageResource(R.drawable.widget_placeholder);
        playerContextButton.setText(R.string.title_player_context);
        playerStateButton.setText(R.string.title_current_track);
        playerContextButton.setVisibility(View.INVISIBLE);
        subscribeToPlayerContextButton.setVisibility(View.VISIBLE);
        playerStateButton.setVisibility(View.INVISIBLE);
    }

    private void connect(boolean showAuthView,boolean toPlay) {
        if(isInternetAvailable()){
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            SpotifyAppRemote.connect(
                    getApplicationContext(),
                    new ConnectionParams.Builder(CLIENT_ID)
                            .setRedirectUri(REDIRECT_URI)
                            .showAuthView(showAuthView)
                            .build(),
                    new Connector.ConnectionListener() {
                        @Override
                        public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                            RemotePlayerActivity.spotifyAppRemote = spotifyAppRemote;
                            RemotePlayerActivity.this.onConnected();
                            ((TextView)findViewById(R.id.connect_authorize_button)).setText(R.string.connected);
                            spotify_connected = true;
                            if(showAuthView){
                                faceCapture();
                            }
                        }
                        @Override
                        public void onFailure(Throwable error) {
                            spotify_connected = true;
                            showToast(getString(R.string.notConnected));
                            ((TextView)findViewById(R.id.connect_authorize_button)).setText(R.string.connect);
                            logError(error);
                            PreferenceManager.getDefaultSharedPreferences(RemotePlayerActivity.this).edit().putBoolean("token", false).apply();
                            PreferenceManager.getDefaultSharedPreferences(RemotePlayerActivity.this).edit().putBoolean("code", false).apply();
                            RemotePlayerActivity.this.onDisconnected();
                        }
                    });
        }else{
            showToast1("no internet connection");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        connect(false,false);
    }

    public void appendLog(String text)
    {
        File logFile = new File("sdcard/MusicRecommendationLog.txt");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            DateTimeFormatter dtf = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            }
            LocalDateTime now = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                now = LocalDateTime.now();
            }
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                buf.append(dtf.format(now).toString() + " : "+text);
            }
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private final BitmapCallback bitmapCallback = new BitmapCallback() {
        @Override
        public void onBitmapReady(@Nullable Bitmap bitmap) {
            emotionRecognition.processImageRequestResult(bitmap);
        }
    };
    CameraView camera;
    private void initializeFaceCapture(){
        camera = findViewById(R.id.camera);
//        camera.setLifecycleOwner(this);
        camera.setPlaySounds(false);
        camera.setFacing(Facing.FRONT);
        camera.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(@NonNull PictureResult result) {
                result.toBitmap(bitmapCallback);
            }
        });

    }
    void  faceCapture()
    {
        playerContextButton.setText(R.string.scanningForFace);
        camera.setLifecycleOwner(this);
        camera.takePicture();
    }

    void playSong(String emotion){
        this.emotion = emotion;
//        showToast("playing " + emotion + " song");
        if(emotion.equals("angry"))
        {
            currentMood = ANGRY_URI;
        }
        else if(emotion.equals("disgust")){
            currentMood = DISGUST_URI;
        }
        else if(emotion.equals("fear")){
            currentMood = FEAR_URI;
        }
        else if(emotion.equals("happy")){
            currentMood = HAPPY_SONGS_URI;
        }
        else if(emotion.equals("neutral")){
            currentMood = NEUTRAL_URI;
        }
        else if(emotion.equals("sad")){
            currentMood = SAD_SONGS_URI;
        }
        else if(emotion.equals("surprise")){
            currentMood = SURPRISE_URI;
        }
        else{
            currentMood = PLAYLIST_URI;
        }
        playUri(currentMood);
    }


    void playUri(String uri) {
        if(spotify_connected){
            spotifyAppRemote
                    .getPlayerApi()
                    .setShuffle(true)
                    .setErrorCallback(errorCallback);
            spotifyAppRemote
                    .getPlayerApi()
                    .play(uri)
//                .setResultCallback(empty -> logMessage(getString(R.string.command_feedback, "play")))
                    .setErrorCallback(errorCallback);
        }else{
            showToast(getString(R.string.notConnected));
        }

    }


    public void onSkipPreviousButtonClicked(View view) {
        spotifyAppRemote
                .getPlayerApi()
                .skipPrevious()
//                .setResultCallback(
//                        empty -> logMessage(getString(R.string.command_feedback, "skip previous")))
                .setErrorCallback(errorCallback);
    }

    public void onPlayPauseButtonClicked(View view) {
        spotifyAppRemote
                .getPlayerApi()
                .getPlayerState()
                .setResultCallback(
                        playerState -> {
                            if (playerState.isPaused) {
                                spotifyAppRemote
                                        .getPlayerApi()
                                        .resume()
//                                        .setResultCallback(
//                                                empty -> logMessage(getString(R.string.command_feedback, "play")))
                                        .setErrorCallback(errorCallback);
                            } else {
                                spotifyAppRemote
                                        .getPlayerApi()
                                        .pause()
//                                        .setResultCallback(
//                                                empty -> logMessage(getString(R.string.command_feedback, "pause")))
                                        .setErrorCallback(errorCallback);
                            }
                        });
    }

    public void onSkipNextButtonClicked(View view) {
        spotifyAppRemote
                .getPlayerApi()
                .skipNext()
//                .setResultCallback(data -> logMessage(getString(R.string.command_feedback, "skip next")))
                .setErrorCallback(errorCallback);
    }

//    public void onGetFitnessRecommendedContentItemsClicked(View view) {
//        spotifyAppRemote
//                .getContentApi()
//                .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
//                .setResultCallback(
//                        listItems -> {
//                            final CountDownLatch latch = new CountDownLatch(listItems.items.length);
//                            final List<ListItem> combined = new ArrayList<>(50);
//                            for (int j = 0; j < listItems.items.length; j++) {
//                                if (listItems.items[j].playable) {
//                                    combined.add(listItems.items[j]);
//                                    handleLatch(latch, combined);
//                                } else {
//                                    spotifyAppRemote
//                                            .getContentApi()
//                                            .getChildrenOfItem(listItems.items[j], 3, 0)
//                                            .setResultCallback(
//                                                    childListItems -> {
//                                                        combined.addAll(Arrays.asList(childListItems.items));
//                                                        handleLatch(latch, combined);
//                                                    })
//                                            .setErrorCallback(errorCallback);
//                                }
//                            }
//                        })
//                .setErrorCallback(errorCallback);
//    }
//
//    private void handleLatch(CountDownLatch latch, List<ListItem> combined) {
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        latch.countDown();
//        if (latch.getCount() == 0) {
////            showDialog(
////                    getString(R.string.command_response, getString(R.string.browse_content)),
////                    gson.toJson(combined));
//        }
////        appendLog(gson.toJson(combined));
//    }
    public void subscribedToPlayerContext() {
        if (playerContextSubscription != null && !playerContextSubscription.isCanceled()) {
            playerContextSubscription.cancel();
            playerContextSubscription = null;
        }

        playerContextButton.setVisibility(View.VISIBLE);
        subscribeToPlayerContextButton.setVisibility(View.INVISIBLE);

        playerContextSubscription =
                (Subscription<PlayerContext>)
                        spotifyAppRemote
                                .getPlayerApi()
                                .subscribeToPlayerContext()
                                .setEventCallback(playerContextEventCallback)
                                .setErrorCallback(
                                        throwable -> {
                                            playerContextButton.setVisibility(View.INVISIBLE);
                                            subscribeToPlayerContextButton.setVisibility(View.VISIBLE);
                                            logError(throwable);
                                        });
    }

    public void subscribedToPlayerState() {

        if (playerStateSubscription != null && !playerStateSubscription.isCanceled()) {
            playerStateSubscription.cancel();
            playerStateSubscription = null;
        }
        playerStateButton.setVisibility(View.VISIBLE);
        playerStateSubscription =
                (Subscription<PlayerState>)
                        spotifyAppRemote
                                .getPlayerApi()
                                .subscribeToPlayerState()
                                .setEventCallback(playerStateEventCallback)
                                .setLifecycleCallback(
                                        new Subscription.LifecycleCallback() {
                                            @Override
                                            public void onStart() {
                                            }

                                            @Override
                                            public void onStop() {
                                            }
                                        })
                                .setErrorCallback(
                                        throwable -> {
                                            playerStateButton.setVisibility(View.INVISIBLE);
                                            logError(throwable);
                                        });
    }

    private void logError(Throwable throwable) {
//        Toast.makeText(this, R.string.err_generic_toast, Toast.LENGTH_SHORT).show();
        Log.e("spotifyerror", "", throwable.getCause());
//        appendLog(throwable.getMessage());
//        if(isInternetAvailable()){
//            switch (throwable.getMessage()){
//                case "CouldNotFindSpotifyApp":
//                    showToast("Spotify app is not installed");
//                    break;
//                case "NotLoggedInException":
//                    showToast("user not logged in to the spotify app");
//                    break;
//            }
//        }else{
//            showToast("no internet connection");
//        }

    }

    private void logMessage(String msg) {
        logMessage(msg, Toast.LENGTH_SHORT);
    }

    private void logMessage(String msg, int duration) {
        Toast.makeText(this, msg, duration).show();
        Log.d(TAG, msg);
    }
    public void play(View view) {
        playUri(currentMood);
    }


    private class TrackProgressBar {

        private static final int LOOP_DURATION = 500;
        private final SeekBar seekBar;
        private final Handler handler;
        private final TextView startTime;
        private final TextView endTime;

        private final SeekBar.OnSeekBarChangeListener seekBarChangeListener =
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        spotifyAppRemote
                                .getPlayerApi()
                                .seekTo(seekBar.getProgress())
                                .setErrorCallback(errorCallback);
                    }
                };

        private final Runnable seekRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        int progress = seekBar.getProgress();
                        seekBar.setProgress(progress + LOOP_DURATION);
                        handler.postDelayed(seekRunnable, LOOP_DURATION);
                        startTime.setText(formatTime(progress));

                    }
                };

        private TrackProgressBar(SeekBar seekBar,TextView startTime, TextView endTime) {
            this.seekBar = seekBar;
            this.startTime = startTime;
            this.endTime = endTime;
            this.seekBar.setOnSeekBarChangeListener(seekBarChangeListener);
            handler = new Handler();
        }

        private void setDuration(long duration) {
            seekBar.setMax((int) duration);
            endTime.setText(formatTime(duration));

        }

        private void update(long progress) {
            seekBar.setProgress((int) progress);
            startTime.setText(formatTime(progress));
        }

        private void pause() {
            handler.removeCallbacks(seekRunnable);
        }

        private void unpause() {
            handler.removeCallbacks(seekRunnable);
            handler.postDelayed(seekRunnable, LOOP_DURATION);
        }
    }

    String formatTime(long time){
        Date date = new Date(time);
        DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateFormatted = formatter.format(date);
        int index = dateFormatted.lastIndexOf("0");
        if(dateFormatted.length() - index - 1 > 4){
            dateFormatted =  dateFormatted.substring(index);
        }else{
            dateFormatted = dateFormatted.substring(dateFormatted.length() - 4);
        }
        return dateFormatted;
    }


    //request permissions
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            this.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });
    private void getPermission(String requestedPermission){
        if (ContextCompat.checkSelfPermission(
                this, requestedPermission) ==
                PackageManager.PERMISSION_GRANTED) {
        }  else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    requestedPermission);
        }

    }

    private static final int TAKE_PHOTO_REQUEST_CODE = 1;

    private Uri currentPhotoUri;


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case TAKE_PHOTO_REQUEST_CODE:
                    emotionRecognition.processImageRequestResult(currentPhotoUri);
                    break;

                default:
                    break;
            }
        }
        if(requestCode == AUTH_TOKEN_REQUEST_CODE || requestCode == AUTH_CODE_REQUEST_CODE){
            final AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, data);

            if (requestCode == AUTH_TOKEN_REQUEST_CODE) {
                emotionRecognition.mAccessToken = response.getAccessToken();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("token", true).apply();
            } else {
                emotionRecognition.mAccessCode = response.getCode();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("code", true).apply();
            }
        }
    }

    private void setButtonListeners(){
        AppCompatImageButton takePhotoButton = findViewById(R.id.take_photo);
        CardView cameraCard = findViewById(R.id.cameraCard);
        takePhotoButton.setOnLongClickListener(view ->
        {
            if(cameraCard.getVisibility() == View.VISIBLE){
                cameraCard.setVisibility(View.INVISIBLE);
            }else{
                cameraCard.setVisibility(View.VISIBLE);
            }
            return true;
        });
        takePhotoButton.setOnClickListener(v -> {
            faceCapture();
        });

        AppCompatImageButton setPlaylistButton = findViewById(R.id.set_playlist);
        setPlaylistButton.setOnClickListener(v -> {
            Intent intent = new Intent(RemotePlayerActivity.this,SetPlaylistActivity.class);
            startActivity(intent);
        });
    }
}

