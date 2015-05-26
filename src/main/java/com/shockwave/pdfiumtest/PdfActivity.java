package com.shockwave.pdfiumtest;

import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PdfActivity extends ActionBarActivity {
    private static final String TAG = PdfActivity.class.getName();

    private PdfiumCore mPdfCore;

    private PdfDocument mPdfDoc = null;
    private FileInputStream mDocFileStream = null;

    private GestureDetector mSlidingDetector;
    private ScaleGestureDetector mZoomingDetector;

    private int mCurrentPageIndex = 0;
    private int mPageCount = 0;

    private SurfaceHolder mPdfSurfaceHolder;
    private boolean isSurfaceCreated = false;

    private final Rect mPageRect = new Rect();
    private final RectF mPageRectF = new RectF();
    private final Rect mScreenRect = new Rect();
    private final Matrix mTransformMatrix = new Matrix();
    private boolean isScaling = false;

    private boolean canFlipPage = true;

    private final ExecutorService mPreLoadPageWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService mRenderPageWorker = Executors.newSingleThreadExecutor();
    private Runnable mRenderRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        mPdfCore = new PdfiumCore(this);

        mSlidingDetector = new GestureDetector(this, new SlidingDetector());
        mZoomingDetector = new ScaleGestureDetector(this, new ZoomingDetector());

        Intent intent = getIntent();
        Uri fileUri;
        if( (fileUri = intent.getData()) == null){
            finish();
            return ;
        }

        mRenderRunnable = new Runnable() {
            @Override
            public void run() {
                loadPageIfNeed(mCurrentPageIndex);

                resetPageFit(mCurrentPageIndex);
                mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                        mPageRect.left, mPageRect.top,
                        mPageRect.width(), mPageRect.height());

                mPreLoadPageWorker.submit(new Runnable() {
                    @Override
                    public void run() {
                        loadPageIfNeed(mCurrentPageIndex + 1);
                        loadPageIfNeed(mCurrentPageIndex + 2);
                    }
                });
            }
        };

        SurfaceView surfaceView = (SurfaceView)findViewById(R.id.view_surface_main);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceCreated = true;
                updateSurface(holder);
                if (mPdfDoc != null) {
                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.w(TAG, "Surface Changed");
                updateSurface(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                isSurfaceCreated = false;
                Log.w(TAG, "Surface Destroy");
            }
        });

        try{
            mDocFileStream = new FileInputStream(fileUri.getPath());

            mPdfDoc = mPdfCore.newDocument(mDocFileStream.getFD());
            Log.d("Main", "Open Document");

            mPageCount = mPdfCore.getPageCount(mPdfDoc);
            Log.d(TAG, "Page Count: " + mPageCount);

        }catch(IOException e){
            e.printStackTrace();
            Log.e("Main", "Data uri: " + fileUri.toString());
        }
    }

    private void loadPageIfNeed(final int pageIndex){
        if( pageIndex >= 0 && pageIndex < mPageCount && !mPdfDoc.hasPage(pageIndex) ){
            Log.d(TAG, "Load page: " + mCurrentPageIndex);
            mPdfCore.openPage(mPdfDoc, pageIndex);
        }
    }

    private void updateSurface(SurfaceHolder holder){
        mPdfSurfaceHolder = holder;
        mScreenRect.set(holder.getSurfaceFrame());
    }

    private void resetPageFit(int pageIndex){
        float pageWidth = mPdfCore.getPageWidth(mPdfDoc, pageIndex);
        float pageHeight = mPdfCore.getPageHeight(mPdfDoc, pageIndex);
        float screenWidth = mPdfSurfaceHolder.getSurfaceFrame().width();
        float screenHeight = mPdfSurfaceHolder.getSurfaceFrame().height();

        if( (pageWidth / pageHeight) < (screenWidth / screenHeight) ){
            //Situation one: fit height
            pageWidth *= (screenHeight / pageHeight);
            pageHeight = screenHeight;

            mPageRect.top = 0;
            mPageRect.left = (int)(screenWidth - pageWidth) / 2;
            mPageRect.right = (int)(mPageRect.left + pageWidth);
            mPageRect.bottom = (int)pageHeight;
        }else{
            //Situation two: fit width
            pageHeight *= (screenWidth / pageWidth);
            pageWidth = screenWidth;

            mPageRect.left = 0;
            mPageRect.top = (int)(screenHeight - pageHeight) / 2;
            mPageRect.bottom = (int)(mPageRect.top + pageHeight);
            mPageRect.right = (int)pageWidth;
        }

        canFlipPage = true;
    }

    private void rectF2Rect(RectF inRectF, Rect outRect){
        outRect.left = (int)inRectF.left;
        outRect.right = (int)inRectF.right;
        outRect.top = (int)inRectF.top;
        outRect.bottom = (int)inRectF.bottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        boolean ret;

        ret = mZoomingDetector.onTouchEvent(event);
        if(!isScaling) ret |= mSlidingDetector.onTouchEvent(event);
        ret |= super.onTouchEvent(event);

        return ret;
    }

    private class SlidingDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
            if(!isSurfaceCreated) return false;
            if(canFlipPage) return false;
            Log.d(TAG, "Drag");

            mTransformMatrix.setTranslate(distanceX * -1, distanceY * -1);
            mPageRectF.set(mPageRect);
            RectF tmpRectF = new RectF(mPageRectF);
            mTransformMatrix.mapRect(tmpRectF);

            if(tmpRectF.left <= mScreenRect.left && tmpRectF.right >= mScreenRect.right ||
                    (tmpRectF.left >= mScreenRect.left && tmpRectF.right <= mScreenRect.right) ){
                mPageRectF.left = tmpRectF.left;
                mPageRectF.right = tmpRectF.right;
            }
            if(tmpRectF.top <= mScreenRect.top && tmpRectF.bottom >= mScreenRect.bottom ||
                    (tmpRectF.top >= mScreenRect.top && tmpRectF.bottom <= mScreenRect.bottom) ){
                mPageRectF.top = tmpRectF.top;
                mPageRectF.bottom = tmpRectF.bottom;
            }

            rectF2Rect(mPageRectF, mPageRect);

            mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                    mPageRect.left, mPageRect.top,
                    mPageRect.width(), mPageRect.height());

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
            if(!isSurfaceCreated) return false;
            if(!canFlipPage) return true;

            if(velocityX < 100f){ //Forward
                if(mCurrentPageIndex < mPageCount - 1){
                    Log.d(TAG, "Flip backward");
                    mCurrentPageIndex++;
                    Log.d(TAG, "Next Index: " + mCurrentPageIndex);

                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }

            if(velocityX > 100f){ //Backward
                Log.d(TAG, "Flip backward");
                if(mCurrentPageIndex > 0){
                    mCurrentPageIndex--;
                    Log.d(TAG, "Next Index: " + mCurrentPageIndex);

                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }

            return true;
        }
    }
    private class ZoomingDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float mAccumulateScale = 1f;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector){
            isScaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector){
            if(!isSurfaceCreated) return false;

            canFlipPage = false;

            mAccumulateScale *= detector.getScaleFactor();
            mAccumulateScale = Math.max(1f, mAccumulateScale);
            float scaleValue = (mAccumulateScale > 1f)? detector.getScaleFactor() : 1f;
            mTransformMatrix.setScale(scaleValue, scaleValue,
                    detector.getFocusX(), detector.getFocusY());
            mPageRectF.set(mPageRect);

            mTransformMatrix.mapRect(mPageRectF);

            rectF2Rect(mPageRectF, mPageRect);

            mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                    mPageRect.left, mPageRect.top,
                    mPageRect.width(), mPageRect.height());

            if(mScreenRect.contains(mPageRect)) canFlipPage = true;

            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector){
            if(mAccumulateScale == 1f && !mScreenRect.contains(mPageRect)){
                resetPageFit(mCurrentPageIndex);

                mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                        mPageRect.left, mPageRect.top,
                        mPageRect.width(), mPageRect.height());
            }

            isScaling = false;
        }
    }

    @Override
    public void onDestroy(){
        try{
            if(mPdfDoc != null && mDocFileStream != null){
                mPdfCore.closeDocument(mPdfDoc);
                Log.d("Main", "Close Document");

                mDocFileStream.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            super.onDestroy();
        }
    }
}
