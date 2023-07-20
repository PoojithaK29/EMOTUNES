package com.example.musicrecomendation;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;

// todo: check entered playlist link is valid some how?

public class SetPlaylistActivity extends AppCompatActivity {

    EditText happy;
    EditText sad;
    EditText disgust;
    EditText fear;
    EditText neutral;
    EditText angry;
    EditText surprise;
    ArrayList<EditText> list;
    ArrayList<String> listName;
    String baseUrl = "https://open.spotify.com/playlist/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_set_playlist);
        try {
            this.getSupportActionBar().hide();
        }
        // catch block to handle NullPointerException
        catch (NullPointerException e) {
        }
        happy = findViewById(R.id.happy);
        sad = findViewById(R.id.sad);
        disgust = findViewById(R.id.disgust);
        fear = findViewById(R.id.fear);
        neutral = findViewById(R.id.neutral);
        angry = findViewById(R.id.angry);
        surprise = findViewById(R.id.surprise);
        list = new ArrayList<EditText>(Arrays.asList(happy,sad,disgust,fear,neutral,angry,surprise));
        listName =  new ArrayList<>(Arrays.asList("happy",
                "sad",
                "disgust",
                "fear",
                "neutral",
                "angry",
                "surprise"));
        happy.setText(baseUrl + getDefaultSharedPreferences(this).getString("happy", "2BiQVRb8LV6lbDLy9XIhMk"));
        sad.setText(baseUrl + getDefaultSharedPreferences(this).getString("sad", "3CfQlMNi5zQ6mrDqx56GXT"));
        disgust.setText(baseUrl + getDefaultSharedPreferences(this).getString("disgust", "3qgzMg4m5tvf16PzlPgGa9"));
        fear.setText(baseUrl + getDefaultSharedPreferences(this).getString("fear", "5AM4lgcUAw5sokybXj3ny7"));
        neutral.setText(baseUrl + getDefaultSharedPreferences(this).getString("neutral", "4PFwZ4h1LMAOwdwXqvSYHd"));
        angry.setText(baseUrl + getDefaultSharedPreferences(this).getString("angry", "2SAlj6IpdtsyI7qqU0ZKb2"));
        surprise.setText(baseUrl + getDefaultSharedPreferences(this).getString("surprise", "4o5SxWNsTNLOi9ahHeJF8A"));


    }

    public void onSetPlaylistClicked(View view) {
        for(int i = 0; i < list.size(); i++){
            String URI = list.get(i).getText().toString().replace(baseUrl,"").substring(0,22);
            getDefaultSharedPreferences(this).edit().putString(listName.get(i),URI).apply();
        }

    }
}