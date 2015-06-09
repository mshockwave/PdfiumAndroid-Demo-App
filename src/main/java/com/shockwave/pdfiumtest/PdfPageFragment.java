package com.shockwave.pdfiumtest;


import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class PdfPageFragment extends Fragment implements IPageShowListener{
    private static final String TAG = PdfPageFragment.class.getName();

    private PdfiumCore mPdfCore;
    private PdfDocument mPdfDoc;
    private int mPageCount = -1;

    private int mPageIndex = 0;

    private Bitmap mBitmap;

    PhotoView mPhotoView;
    PhotoViewAttacher mPhotoAttacher;

    public static PdfPageFragment newInstance(PdfiumCore pdfCore, PdfDocument pdfDoc, int pageCount, int pageIndex) {
        PdfPageFragment fragment = new PdfPageFragment();
        fragment.setUpPdf(pdfCore, pdfDoc, pageCount, pageIndex);
        Log.d(TAG, "newInstance invoked: " + pageIndex);

        return fragment;
    }

    public PdfPageFragment() {
        // Required empty public constructor
    }

    public void setUpPdf(PdfiumCore core, PdfDocument doc, int count, int index){
        mPdfCore = core;
        mPdfDoc = doc;
        mPageCount = count;
        mPageIndex = index;
    }
    private void loadPageIfNeed(int index){
        if( !(index >= 0 && index < mPageCount) ) return;
        if(!mPdfDoc.hasPage(index)){
            mPdfCore.openPage(mPdfDoc, index);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pdf_page, container, false);
        mPhotoView = (PhotoView)rootView.findViewById(R.id.img_photo_view);
        mPhotoAttacher = new PhotoViewAttacher(mPhotoView);

        return rootView;
    }

    private void renderPage(){
        loadPageIfNeed(mPageIndex);

        (new Thread(){
            @Override
            public void run(){
                int height = mPdfCore.getPageHeight(mPdfDoc, mPageIndex) / 2;
                int width = mPdfCore.getPageWidth(mPdfDoc, mPageIndex) / 2;

                if(mBitmap == null || mBitmap.isRecycled()){
                    mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    mPdfCore.renderPage(mPdfDoc, mBitmap, mPageIndex);
                }else if( !(mBitmap.getHeight() == height &&
                        mBitmap.getWidth() == width &&
                        mBitmap.getConfig().equals(Bitmap.Config.ARGB_8888) &&
                        mBitmap.isMutable()) ){
                    mBitmap.recycle();
                    mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    mPdfCore.renderPage(mPdfDoc, mBitmap, mPageIndex);
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPhotoView.setImageBitmap(mBitmap);
                        mPhotoAttacher.update();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);

        renderPage();
    }
    @Override
    public void onDetach(){
        Log.d(TAG, "Detach");

        super.onDetach();
    }

    @Override
    public void onDestroy(){
        if(mBitmap != null) mBitmap.recycle();

        super.onDestroy();
    }

    @Override
    public void onPageShowed() {

    }

    @Override
    public void onPageInvisible() {

    }
}
