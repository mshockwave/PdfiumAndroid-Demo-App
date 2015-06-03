package com.shockwave.pdfiumtest;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void pickFileOnClick(View v){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("file/pdf");
        startActivityForResult(intent, 1);
    }
    @Override
    public void onActivityResult(int reqCode, int result, Intent intent){
        if(reqCode == 1){
            if(result == RESULT_OK){
                Uri data = intent.getData();
                Log.i("Main", "File path: " + data.getPath());


            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
