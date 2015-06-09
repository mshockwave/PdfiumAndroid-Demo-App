package com.shockwave.pdfiumtest;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfiumtest.view.ImageViewPager;

import java.io.FileInputStream;
import java.io.IOException;


public class PdfActivity extends ActionBarActivity implements ImageViewPager.OnPageChangeListener{
    private static final String TAG = PdfActivity.class.getName();

    private PdfDocument mPdfDoc;
    private PdfiumCore mPdfCore;
    private int mPageCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        ImageViewPager pdfViewPager = (ImageViewPager)findViewById(R.id.view_pager_main);
        PagerAdapter adapter = new PdfPagerAdapter(getSupportFragmentManager());
        pdfViewPager.setAdapter(adapter);

        Uri fileUri;
        if( (fileUri = getIntent().getData()) == null){
            Log.e(TAG, "No Input file");
            finish();
            return;
        }

        try{
            FileInputStream fileIns = new FileInputStream(fileUri.getPath());

            mPdfCore = new PdfiumCore(this);

            mPdfDoc = mPdfCore.newDocument(fileIns.getFD());
            if(mPdfDoc == null){
                Log.e(TAG, "Open PdfDocument failed");
                finish();
                return;
            }
            mPageCount = mPdfCore.getPageCount(mPdfDoc);

            pdfViewPager.getAdapter().notifyDataSetChanged();

        }catch(IOException e){
            Log.e(TAG, "File not exist, Uri: " + fileUri.toString());
            finish();
        }
    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
    @Override
    public void onPageScrollStateChanged(int state) {}

    private class PdfPagerAdapter extends FragmentPagerAdapter {

        public PdfPagerAdapter(FragmentManager fm) { super(fm); }

        @Override
        public Fragment getItem(int position) {
            return PdfPageFragment.newInstance(mPdfCore, mPdfDoc, mPageCount, position);
        }

        @Override
        public int getCount() { return (mPageCount < 0)? 0 : mPageCount; }
    }

    @Override
    public void onDestroy(){
        if(mPdfDoc != null && mPdfCore != null) mPdfCore.closeDocument(mPdfDoc);

        super.onDestroy();
    }
}
