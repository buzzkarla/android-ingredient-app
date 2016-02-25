package com.example.kelvs.parsing;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;


public class SearchRecipeActivity extends AppCompatActivity {


    public final static String EXTRA_MESSAGE = "com.example.kelvs.parsing";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_recipe);
    }


    public void sendKeyWord (View view){

        Intent intent = new Intent(this, ShowIngredientsActivity.class);
        EditText editText = (EditText) findViewById(R.id.inputText);
        String keyword = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, keyword);
        startActivity(intent);
    }
}

