package com.shockwave.pdfiumtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;


public class PdfActivity extends ActionBarActivity {
    private static final String TAG = PdfActivity.class.getName();

    private PdfDocument mPdfDoc;
    private PdfiumCore mPdfCore;
    private int mPageCount = -1;

    private final BlockingQueue<PageEntity> mPagesPool = new LinkedBlockingQueue<>();
    private static final int CACHE_PAGE_NUM = 5;
    private final BlockingQueue<PageEntity> mActivePages = new LinkedBlockingQueue<>();

    private final ExecutorService mPageLoaderExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mRenderExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        ViewPager pdfViewPager = (ViewPager)findViewById(R.id.view_pager_main);
        final PdfPagerAdapter adapter = new PdfPagerAdapter();
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

            //Create cached pages
            int i;
            for(i = 0; i < CACHE_PAGE_NUM; i++){
                mPagesPool.add(new PageEntity(this));
            }

            adapter.notifyDataSetChanged();

        }catch(IOException e){
            Log.e(TAG, "File not exist, Uri: " + fileUri.toString());
            finish();
        }
    }

    private class PageEntity {
        public PhotoView photoView;
        public Bitmap contentBitmap;
        public int pageIndex;
        public PhotoViewAttacher photoAttacher;

        public PageEntity(Context ctx){
            photoView = new PhotoView(ctx);
        }

        public void updateImgBitmap(){
            if(photoView != null){
                photoView.setImageBitmap(contentBitmap);
                if(photoAttacher != null){
                    photoAttacher.update();
                }
            }
        }
    }

    private void loadPageIfNeed(int index){
        if( !(index >= 0 && index < mPageCount) ) return;
        if(!mPdfDoc.hasPage(index)){
            mPdfCore.openPage(mPdfDoc, index);
        }
    }

    private class PdfPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return (mPageCount < 0)? 0 : mPageCount;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position){
            if(mPagesPool.size() <= 0){
                mPagesPool.add(new PageEntity(getApplicationContext()));
            }

            final PageEntity pageEntity = mPagesPool.poll();
            if(pageEntity != null){
                pageEntity.pageIndex = position;
                loadPageIfNeed(position);

                /**
                 * Since PhotoViewAttacher would treat its ImageView as WeakReference
                 * So we have to re-create it every time
                 * **/
                //if(pageEntity.photoAttacher != null) pageEntity.photoAttacher.cleanup();
                pageEntity.photoAttacher = new PhotoViewAttacher(pageEntity.photoView);

                /*Check page bitmap properties*/
                int pageWidth = mPdfCore.getPageWidth(mPdfDoc, position);
                int pageHeight = mPdfCore.getPageHeight(mPdfDoc, position);
                if(pageEntity.contentBitmap == null || pageEntity.contentBitmap.isRecycled()){
                    pageEntity.contentBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);
                }else if( !(pageEntity.contentBitmap.getHeight() == pageHeight &&
                            pageEntity.contentBitmap.getWidth() == pageWidth) &&
                            pageEntity.contentBitmap.getConfig().equals(Bitmap.Config.ARGB_8888) ){
                        pageEntity.contentBitmap.recycle();
                        pageEntity.contentBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);
                }

                mRenderExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        mPdfCore.renderPage(mPdfDoc, pageEntity.contentBitmap, pageEntity.pageIndex);
                        pageEntity.updateImgBitmap();
                    }
                });

                container.addView(pageEntity.photoView, ViewGroup.LayoutParams.MATCH_PARENT/*width*/,
                                                        ViewGroup.LayoutParams.WRAP_CONTENT/*height*/);

                mActivePages.add(pageEntity);

                /*Preload pages*/
                mPageLoaderExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        loadPageIfNeed(position + 1);
                        loadPageIfNeed(position + 2);
                    }
                });
            }

            return pageEntity;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object){
            if(object instanceof PageEntity){
                PageEntity removedPage = (PageEntity)object;
                if(removedPage == mActivePages.peek()){
                    removedPage = mActivePages.poll();
                }else{
                    mActivePages.remove(removedPage);
                }

                mPagesPool.add(removedPage);
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) { return view == object; }
    }

    @Override
    public void onDestroy(){
        if(mPdfDoc != null && mPdfCore != null) mPdfCore.closeDocument(mPdfDoc);

        super.onDestroy();
    }
}
