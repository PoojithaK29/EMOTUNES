package com.example.musicrecomendation;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.example.musicrecomendation.classifiers.TFLiteImageClassifier;
import com.example.musicrecomendation.utils.ImageUtils;
import com.example.musicrecomendation.utils.SortingHelper;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class EmotionRecognition {
    private static final int TAKE_PHOTO_REQUEST_CODE = 1;

    private final String MODEL_FILE_NAME = "simple_classifier.tflite";

    private final int SCALED_IMAGE_BIGGEST_SIZE = 480;

    TFLiteImageClassifier mClassifier;



    Uri mCurrentPhotoUri;

    private Map<String, List<Pair<String, String>>> mClassificationResult;
    
    private final RemotePlayerActivity context;
    
    EmotionRecognition(RemotePlayerActivity context){
        this.context = context;
        Boolean gotToken =  PreferenceManager.getDefaultSharedPreferences(context).getBoolean("token", false);
        Boolean gotCode = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("token", false);
        Log.d("check_app", "onCreate: token " + gotToken);
        Log.d("check_app", "onCreate: code " + gotCode);
        if(!gotToken) {
            RequestToken();
        }
        if(!gotCode){
            RequestCode();
        }

        mClassifier = new TFLiteImageClassifier(
                context.getAssets(),
                MODEL_FILE_NAME,
                context.getResources().getStringArray(R.array.emotions));

        mClassificationResult = new LinkedHashMap<>();
    }

    // Function to handle successful new image acquisition
    public void processImageRequestResult(Uri resultImageUri) {
        Bitmap scaledResultImageBitmap = getScaledImageBitmap(resultImageUri);

//        mImageView.setImageBitmap(scaledResultImageBitmap);

        // Clear the result of a previous classification
        mClassificationResult.clear();


        detectFaces(scaledResultImageBitmap);
    }

    public void processImageRequestResult(Bitmap resultImageBitmap) {
        Bitmap scaledImageBitmap = null;

        int scaledHeight;
        int scaledWidth;

        // How many times you need to change the sides of an image
        float scaleFactor;

        // Get larger side and start from exactly the larger side in scaling
        if (resultImageBitmap.getHeight() > resultImageBitmap.getWidth()) {
            scaledHeight = SCALED_IMAGE_BIGGEST_SIZE;
            scaleFactor = scaledHeight / (float) resultImageBitmap.getHeight();
            scaledWidth = (int) (resultImageBitmap.getWidth() * scaleFactor);

        } else {
            scaledWidth = SCALED_IMAGE_BIGGEST_SIZE;
            scaleFactor = scaledWidth / (float) resultImageBitmap.getWidth();
            scaledHeight = (int) (resultImageBitmap.getHeight() * scaleFactor);
        }

        scaledImageBitmap = Bitmap.createScaledBitmap(
                resultImageBitmap,
                scaledWidth,
                scaledHeight,
                true);


        // Clear the result of a previous classification
        mClassificationResult.clear();

        detectFaces(scaledImageBitmap);
    }


    private Bitmap getScaledImageBitmap(Uri imageUri) {
        Bitmap scaledImageBitmap = null;

        try {
            Log.d("TAG", "getScaledImageBitmap: "+ imageUri.toString());
            Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(),
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
                    context.getContentResolver(),
                    scaledImageBitmap,
                    imageUri);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return scaledImageBitmap;
    }
    int noFaceDetectedCount = 0;
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
        FaceDetector detector = FaceDetection.getClient(realTimeOpts);

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
                                        classifyEmotions(faceBitmap);
                                        Toast.makeText(
                                                context,
                                                "face captured",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                        context.camera.close();
                                        context.playerContextButton.setText("playing " + context.emotion + " song");
                                        // Set the image with the face designations
                                        noFaceDetectedCount = 0;

                                        // If no faces are found
                                    } else {
                                        if(noFaceDetectedCount < 6){
                                            context.faceCapture();
//                                            Toast.makeText(
//                                                    context,
//                                                    context.getString(R.string.faceless),
//                                                    Toast.LENGTH_SHORT
//                                            ).show();
                                            noFaceDetectedCount++;
                                        }else{
                                            context.camera.close();
                                            context.playerContextButton.setText("playing " + context.emotion + " song");
                                            Toast.makeText(
                                                    context,
                                                    "cannot detect face, try again.",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                            noFaceDetectedCount = 0;
                                        }
                                    }

                                })
                        .addOnFailureListener(
                                e -> e.printStackTrace());
    }

    public void appendLog(String text)
    {
        File logFile = new File("sdcard/emotion_log.txt");
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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void classifyEmotions(Bitmap imageBitmap) {
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

        String groupName = context.getString(R.string.face);
        mClassificationResult.put(groupName, faceGroup);
        Log.d("testing_app", "classifyEmotions: " + faceGroup);
        appendLog(faceGroup.toString());
        try{
            if(Objects.equals(faceGroup.get(0).first, "neutral")){
                double per = Double.parseDouble(faceGroup.get(0).second.replace("%",""));
                Log.d("testing_app", "persentage : " +per);

                if(per > 75.00){
                    context.playSong(faceGroup.get(0).first);
                    Log.d("testing_app", "choosen emotion : " + faceGroup.get(0).first);
                }else{
                    context.playSong(faceGroup.get(1).first);
                    Log.d("testing_app", "choosen emotion : " + faceGroup.get(1).first);
                }
            }else{
                context.playSong(faceGroup.get(0).first);
                Log.d("testing_app", "choosen emotion : " + faceGroup.get(0).first);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
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
    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "ER_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        return image;
    }

    //authentication

    public static final String CLIENT_ID = "b26c50c6eb134d24a28ab442988ab0bc";
    private static final String REDIRECT_URI = "com.example.musicrecomendation://callback";
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    String mAccessToken;
    String mAccessCode;
    private Call mCall;


    public void RequestCode() {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.CODE);
        AuthorizationClient.openLoginActivity(context, AUTH_CODE_REQUEST_CODE, request);
    }

    public void RequestToken() {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.TOKEN);
        AuthorizationClient.openLoginActivity(context, AUTH_TOKEN_REQUEST_CODE, request);
    }

    private AuthorizationRequest getAuthenticationRequest(AuthorizationResponse.Type type) {
        return new AuthorizationRequest.Builder(CLIENT_ID, type, REDIRECT_URI)
                .setShowDialog(false)
                .setScopes(new String[]{"streaming"})
                .setCampaign("your-campaign-token")
                .build();
    }


    void cancelCall() {
        if (mCall != null) {
            mCall.cancel();
        }
    }

    

}
