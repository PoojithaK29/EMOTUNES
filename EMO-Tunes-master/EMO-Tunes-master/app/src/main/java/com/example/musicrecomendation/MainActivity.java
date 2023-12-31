package com.example.musicrecomendation;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.musicrecomendation.classifiers.TFLiteImageClassifier;
import com.example.musicrecomendation.utils.ImageUtils;
import com.example.musicrecomendation.utils.SortingHelper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity {

    private static final int GALLERY_REQUEST_CODE = 0;
    private static final int TAKE_PHOTO_REQUEST_CODE = 1;

    private final String MODEL_FILE_NAME = "simple_classifier.tflite";

    private final int SCALED_IMAGE_BIGGEST_SIZE = 480;

    private TFLiteImageClassifier mClassifier;

    private ProgressBar mClassificationProgressBar;

    private ImageView mImageView;
    private Button mTakePhotoButton;


    private Uri mCurrentPhotoUri;

    private Map<String, List<Pair<String, String>>> mClassificationResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            this.getSupportActionBar().hide();
        }
        // catch block to handle NullPointerException
        catch (NullPointerException e) {
        }

        Boolean gotToken =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean("token", false);
        Boolean gotCode = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("token", false);
        Log.d("check_app", "onCreate: token " + gotToken);
        Log.d("check_app", "onCreate: code " + gotCode);
        if(!gotToken) {
            RequestToken();
        }
        if(!gotCode){
            RequestCode();
        }
        mClassificationProgressBar = findViewById(R.id.classification_progress_bar);

        mClassifier = new TFLiteImageClassifier(
                this.getAssets(),
                MODEL_FILE_NAME,
                getResources().getStringArray(R.array.emotions));

        mClassificationResult = new LinkedHashMap<>();

        mImageView = findViewById(R.id.image_view);


        mTakePhotoButton = findViewById(R.id.take_photo_button);
        mTakePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    getPermission(Manifest.permission.CAMERA);
    getPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    }
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCall();
        mClassifier.close();

        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (File tempFile : picturesDir.listFiles()) {
            tempFile.delete();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                // When an image from the gallery was successfully selected
                case GALLERY_REQUEST_CODE:
                    // We can immediately get an image URI from an intent data
                    Uri pickedImageUri = data.getData();
                    processImageRequestResult(pickedImageUri);
                    break;
                // When a photo was taken successfully
                case TAKE_PHOTO_REQUEST_CODE:
                    processImageRequestResult(mCurrentPhotoUri);
                    break;

                default:
                    break;
            }
        }
        if(requestCode == AUTH_TOKEN_REQUEST_CODE || requestCode == AUTH_CODE_REQUEST_CODE){
            final AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, data);

            if (requestCode == AUTH_TOKEN_REQUEST_CODE) {
                mAccessToken = response.getAccessToken();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("token", true).apply();
            } else if (requestCode == AUTH_CODE_REQUEST_CODE) {
                mAccessCode = response.getCode();
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("code", true).apply();
            }
        }
    }



    // Function to handle successful new image acquisition
    void processImageRequestResult(Uri resultImageUri) {
        Bitmap scaledResultImageBitmap = getScaledImageBitmap(resultImageUri);

        mImageView.setImageBitmap(scaledResultImageBitmap);

        // Clear the result of a previous classification
        mClassificationResult.clear();

        setCalculationStatusUI(true);

        detectFaces(scaledResultImageBitmap);
    }


    // Function to create an intent to take a photo
    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Make sure that there is activity of the camera that processes the intent
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (photoFile != null) {
                mCurrentPhotoUri = FileProvider.getUriForFile(
                        this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        photoFile);

                intent.putExtra(MediaStore.EXTRA_OUTPUT, mCurrentPhotoUri);
                startActivityForResult(intent, TAKE_PHOTO_REQUEST_CODE);
            }
        }
    }

    private Bitmap getScaledImageBitmap(Uri imageUri) {
        Bitmap scaledImageBitmap = null;

        try {
            Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(
                    this.getContentResolver(),
                    imageUri);

            int scaledHeight;
            int scaledWidth;

            // How many times you need to change the sides of an image
            float scaleFactor;

            // Get larger side and start from exactly the larger side in scaling
            if (imageBitmap.getHeight() > imageBitmap.getWidth()) {
                scaledHeight = SCALED_IMAGE_BIGGEST_SIZE;
                scaleFactor = scaledHeight / (float) imageBitmap.getHeight();
                scaledWidth = (int) (imageBitmap.getWidth() * scaleFactor);

            } else {
                scaledWidth = SCALED_IMAGE_BIGGEST_SIZE;
                scaleFactor = scaledWidth / (float) imageBitmap.getWidth();
                scaledHeight = (int) (imageBitmap.getHeight() * scaleFactor);
            }

            scaledImageBitmap = Bitmap.createScaledBitmap(
                    imageBitmap,
                    scaledWidth,
                    scaledHeight,
                    true);

            // An image in memory can be rotated
            scaledImageBitmap = ImageUtils.rotateToNormalOrientation(
                    getContentResolver(),
                    scaledImageBitmap,
                    imageUri);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return scaledImageBitmap;
    }

    private void detectFaces(Bitmap imageBitmap){
        // High-accuracy landmark detection and face classification
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

// Real-time contour detection
        FaceDetectorOptions realTimeOpts =
                new FaceDetectorOptions.Builder()
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();
        InputImage image = InputImage.fromBitmap(imageBitmap, 0);
        FaceDetector detector = FaceDetection.getClient(highAccuracyOpts);

        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                faces -> {
                                    Bitmap imageBitmap1 = image.getBitmapInternal();
                                    // Temporary Bitmap for drawing
                                    Bitmap tmpBitmap = Bitmap.createBitmap(
                                            imageBitmap1.getWidth(),
                                            imageBitmap1.getHeight(),
                                            imageBitmap1.getConfig());

                                    // Create an image-based canvas
                                    Canvas tmpCanvas = new Canvas(tmpBitmap);
                                    tmpCanvas.drawBitmap(
                                            imageBitmap1,
                                            0,
                                            0,
                                            null);

                                    Paint paint = new Paint();
                                    paint.setColor(Color.GREEN);
                                    paint.setStrokeWidth(2);
                                    paint.setTextSize(48);

                                    // Coefficient for indentation of face number
                                    final float textIndentFactor = 0.1f;

                                    // If at least one face was found
                                    if (!faces.isEmpty()) {
                                        // faceId ~ face text number
                                        int faceId = 1;
                                        Face face = faces.get(0);
                                        Rect faceRect = getInnerRect(
                                                face.getBoundingBox(),
                                                imageBitmap1.getWidth(),
                                                imageBitmap1.getHeight());

                                        // Draw a rectangle around a face
                                        paint.setStyle(Paint.Style.STROKE);
                                        tmpCanvas.drawRect(faceRect, paint);

                                        // Draw a face number in a rectangle
                                        paint.setStyle(Paint.Style.FILL);
                                        tmpCanvas.drawText(
                                                Integer.toString(faceId),
                                                faceRect.left +
                                                        faceRect.width() * textIndentFactor,
                                                faceRect.bottom -
                                                        faceRect.height() * textIndentFactor,
                                                paint);

                                        // Get subarea with a face
                                        Bitmap faceBitmap = Bitmap.createBitmap(
                                                imageBitmap1,
                                                faceRect.left,
                                                faceRect.top,
                                                faceRect.width(),
                                                faceRect.height());
                                        Log.d("testing_faces", "detectFaces: 1");
                                        classifyEmotions(faceBitmap, faceId);
                                        Log.d("testing_faces", "detectFaces: 5");

                                        // Set the image with the face designations
                                        mImageView.setImageBitmap(tmpBitmap);


                                        // If no faces are found
                                    } else {
                                        Toast.makeText(
                                                MainActivity.this,
                                                getString(R.string.faceless),
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }

                                    setCalculationStatusUI(false);
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        e.printStackTrace();

                                        setCalculationStatusUI(false);
                                    }
                                });
    }

    private void classifyEmotions(Bitmap imageBitmap, int faceId) {
        Map<String, Float> result = mClassifier.classify(imageBitmap, true);

        // Sort by increasing probability
        LinkedHashMap<String, Float> sortedResult =
                (LinkedHashMap<String, Float>) SortingHelper.sortByValues(result);

        ArrayList<String> reversedKeys = new ArrayList<>(sortedResult.keySet());
        // Change the order to get a decrease in probabilities
        Collections.reverse(reversedKeys);

        ArrayList<Pair<String, String>> faceGroup = new ArrayList<>();
        for (String key : reversedKeys) {
            String percentage = String.format("%.1f%%", sortedResult.get(key) * 100);
            faceGroup.add(new Pair<>(key, percentage));
        }

        String groupName = getString(R.string.face) + " " + faceId;
        mClassificationResult.put(groupName, faceGroup);
        Log.d("testing_app", "classifyEmotions: " + faceGroup.get(0).first);
        Intent intent = new Intent(this,RemotePlayerActivity.class);
        intent.putExtra("emotion",faceGroup.get(0).first);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    // Get a rectangle that lies inside the image area
    private Rect getInnerRect(Rect rect, int areaWidth, int areaHeight) {
        Rect innerRect = new Rect(rect);

        if (innerRect.top < 0) {
            innerRect.top = 0;
        }
        if (innerRect.left < 0) {
            innerRect.left = 0;
        }
        if (rect.bottom > areaHeight) {
            innerRect.bottom = areaHeight;
        }
        if (rect.right > areaWidth) {
            innerRect.right = areaWidth;
        }

        return innerRect;
    }

    // Create a temporary file for the image
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "ER_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        return image;
    }

    //Change the interface depending on the status of calculations
    private void setCalculationStatusUI(boolean isCalculationRunning) {
        if (isCalculationRunning) {
            mClassificationProgressBar.setVisibility(ProgressBar.VISIBLE);
            mTakePhotoButton.setEnabled(false);
        } else {
            mClassificationProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mTakePhotoButton.setEnabled(true);
        }
    }

    //authentication

    public static final String CLIENT_ID = "b26c50c6eb134d24a28ab442988ab0bc";
    private static final String REDIRECT_URI = "com.example.musicrecomendation://callback";
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    private String mAccessToken;
    private String mAccessCode;
    private Call mCall;


    public void RequestCode() {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.CODE);
        AuthorizationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request);
    }

    public void RequestToken() {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.TOKEN);
        AuthorizationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request);
    }

    private AuthorizationRequest getAuthenticationRequest(AuthorizationResponse.Type type) {
        return new AuthorizationRequest.Builder(CLIENT_ID, type, REDIRECT_URI)
                .setShowDialog(false)
                .setScopes(new String[]{"user-read-email"})
                .setCampaign("your-campaign-token")
                .build();
    }


    private void cancelCall() {
        if (mCall != null) {
            mCall.cancel();
        }
    }

    public void onSetPlaylistClicked(View view) {
        Intent intent = new Intent(this,SetPlaylistActivity.class);
        startActivity(intent);
    }
}

