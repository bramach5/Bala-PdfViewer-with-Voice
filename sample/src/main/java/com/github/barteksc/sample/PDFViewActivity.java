/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.sample;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.scroll.ScrollHandle;
import com.shockwave.pdfium.PdfDocument;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.NonConfigurationInstance;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringArrayRes;

import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

@EActivity(R.layout.activity_main)
@OptionsMenu(R.menu.options)
public class PDFViewActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();

    private final static int REQUEST_CODE = 42;

    //public static final String SAMPLE_FILE = "Lesson1.pdf";
    private String SAMPLE_FILE;
    private String myString;
    private String LESSON;
    private TextToSpeech mytos;
    private Context myContext;
    private Handler myHandler;
    private Timer myTimer;
    private TimerTask myTimerTask;
    private Thread myThread;
    private int i;
    private List<Integer> myList = new ArrayList<Integer>();
    private ListIterator<Integer> myListIterator = null;
    private int tempint;
    private int numPages;
    private Cursor listCursor;
    private int totalcount;
    private int strLength;
    private int timeDelay;





    @ViewById
    PDFView pdfView;

    @NonConfigurationInstance
    Uri uri;

    @NonConfigurationInstance
    Integer pageNumber = 0;

    String pdfFileName;
    Handler handler;





    @OptionsItem(R.id.pickFile)
    void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(intent, REQUEST_CODE);
    }

    @AfterViews
    void afterViews() {
        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(SAMPLE_FILE);
        }
        setTitle(pdfFileName);
    }

    private void displayFromAsset(String assetFileName) {

        LESSON = getIntent().getStringExtra("firstKeyName");
        SAMPLE_FILE = LESSON + ".pdf";

        //LESSON = "short_a";
        //SAMPLE_FILE = LESSON + ".pdf";


        pdfFileName = assetFileName;

        pdfView.fromAsset(SAMPLE_FILE)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .enableSwipe(true)
                .swipeHorizontal(false)
                .load();



        myContext = getApplicationContext();

        mytos = new TextToSpeech(myContext,new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mytos.setLanguage(Locale.US);
            }
        });

        DatabaseAccess dbaccess = new DatabaseAccess(myContext);
        dbaccess.createDataBase();
        SQLiteDatabase db = dbaccess.getReadableDatabase();

        String tempword = "PageNumber";
        listCursor = db.query(LESSON, new String[]{"PageNumber", "VoiceOver"}, null, null, null, null, String.format("%s", tempword));
        numPages = listCursor.getCount();

        class MyThread implements Runnable {

            public void run() {

                for (int i=0; i<numPages; i++) {
                    Message message = Message.obtain();
                    pdfView.fromAsset(SAMPLE_FILE)
                            .pages(i)
                            .load();

                    listCursor.moveToPosition(i);
                    myString = listCursor.getString(1);
                    strLength = myString.length();
                    timeDelay = (strLength/4)*500;

                    if (strLength < 3) timeDelay = 1000;
                    else if (timeDelay < 2000) timeDelay = 2000;


                    try { Thread.sleep(100); }
                    catch (InterruptedException e) { e.printStackTrace(); }
                    message.arg1 = i+1;
                    handler.sendMessage(message);
                    try { Thread.sleep(timeDelay); }
                    catch (InterruptedException e) { e.printStackTrace(); }

                }
            }

        }

        Thread thread;
        thread = new Thread(new MyThread());
        thread.start();




        handler= new Handler() {
            public void handleMessage(Message msg) {
                //myString = "SoL Bala Page Number " + String.valueOf(msg.arg1) + " of " + String.valueOf(msg.arg2);
                int pageNumber = msg.arg1;
                //listCursor.moveToPosition(pageNumber);
                //myString = listCursor.getString(1);
                mytos.speak(myString, TextToSpeech.QUEUE_FLUSH, null);
            }
        };

    }

    private void displayFromUri(Uri uri) {
        pdfFileName = getFileName(uri);

        pdfView.fromUri(uri)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .load();
    }

    @OnActivityResult(REQUEST_CODE)
    public void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            displayFromUri(uri);
        }
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format("%s %s / %s", pdfFileName, page + 1, pageCount));
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void loadComplete(int nbPages) {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        Log.e(TAG, "title = " + meta.getTitle());
        Log.e(TAG, "author = " + meta.getAuthor());
        Log.e(TAG, "subject = " + meta.getSubject());
        Log.e(TAG, "keywords = " + meta.getKeywords());
        Log.e(TAG, "creator = " + meta.getCreator());
        Log.e(TAG, "producer = " + meta.getProducer());
        Log.e(TAG, "creationDate = " + meta.getCreationDate());
        Log.e(TAG, "modDate = " + meta.getModDate());

        printBookmarksTree(pdfView.getTableOfContents(), "-");

    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {

            Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));

            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }
}
