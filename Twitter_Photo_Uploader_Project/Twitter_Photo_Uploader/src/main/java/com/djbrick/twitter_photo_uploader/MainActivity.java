package com.djbrick.twitter_photo_uploader;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        PlaceholderFragment currentFragment = (PlaceholderFragment)getSupportFragmentManager().findFragmentById(R.id.container);
        currentFragment.onFragmentResult(requestCode, resultCode, data);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements Camera.PictureCallback {

        // Constants

        static String TWITTER_CONSUMER_KEY = "waVAu1dHyQBRp16kPs5uLA";
        static String TWITTER_CONSUMER_SECRET = "N4DMN6x9ylA5WLleNtcuXncvhO3kaGPEjXwFA4e9s0";

        private ProgressDialog mDialog;
        private Camera mCamera;
        private ImageView mPhotoDisplay;
        private Button mTakePicture;
        private SurfaceHolder mPreviewHolder;
        private Button mLoginButton;
        private Button mUploadButton;
        private static SharedPreferences mSharedPreferences;
        private static Twitter mTwitter;
        private static RequestToken mRequestToken;
        private Bitmap mCurrentPhoto;
        private MSTwitter mMSTwitter;
        ProgressDialog pDialog;

        public PlaceholderFragment() {
        }

        @Override
        public void onPause(){
            super.onPause();
            try{
                mCamera.release();
            }catch(Exception e){

            }
        }


        protected void onFragmentResult(int requestCode, int resultCode, Intent data){
            mMSTwitter.onCallingActivityResult(requestCode, resultCode, data);
        }

            @Override
        public void onResume(){
            super.onResume();
            openCamera();
        }

        private void openCamera(){
            try{
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            }catch (Exception e){

            }
        }



        /**
         * Send tweet using MSTwitter object created in onCreate()
         */
        private void tweet() {
            // assemble data
            String textToTweet = "testjfdgfd";

             // use MSTwitter function to save image to file because startTweet() takes an image path
            // this is done to avoid passing large image files between intents which is not android best practices
            String tweetImagePath = MSTwitter.putBitmapInDiskCache(this.getActivity(), mCurrentPhoto);

            // start the tweet
            mMSTwitter.startTweet(textToTweet, tweetImagePath);

        }

        private void handleTweetMessage(int event, String message) {

            String note = "";
            switch (event) {
                case MSTwitter.MSTWEET_STATUS_AUTHORIZING:
                    note = "Authorizing app with twitter.com";
                    break;
                case MSTwitter.MSTWEET_STATUS_STARTING:
                    note = "Tweet data send started";
                    break;
                case MSTwitter.MSTWEET_STATUS_FINSIHED_SUCCCESS:
                    note = "Tweet sent successfully";
                    break;
                case MSTwitter.MSTWEET_STATUS_FINSIHED_FAILED:
                    note = "Tweet failed:" + message;
                    break;
            }

            // add note to results TextView
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm:ss.S");
            String timeS = timeFmt.format(new Date());

            mDialog.setMessage("\n[Message received at " + timeS +"]\n" + note);

        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            openCamera();
            mPhotoDisplay = (ImageView) rootView.findViewById(R.id.photo);
            mLoginButton = (Button) rootView.findViewById(R.id.login);
            mUploadButton = (Button) rootView.findViewById(R.id.upload_picture);

            // make a MSTwitter event handler to receive tweet send events
            MSTwitter.MSTwitterResultReceiver myMSTReceiver = new MSTwitter.MSTwitterResultReceiver() {
                @Override
                public void onRecieve(int tweetLifeCycleEvent, String tweetMessage) {
                    handleTweetMessage(tweetLifeCycleEvent, tweetMessage);
                }
            };

            mMSTwitter = new MSTwitter(getActivity(), TWITTER_CONSUMER_KEY, TWITTER_CONSUMER_SECRET, myMSTReceiver);



            mUploadButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    // show  loading dialog
                    mDialog = ProgressDialog.show(getActivity(), "",
                            "Loading. Please wait...", true);
                    tweet();
                }
            });


            SurfaceView cameraSurface = (SurfaceView) rootView.findViewById(R.id.camera_surface);
            mPreviewHolder = cameraSurface.getHolder();
            mPreviewHolder.addCallback(surfaceCallback);

            try {
                mCamera.setPreviewDisplay(cameraSurface.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }


            mTakePicture = (Button) rootView.findViewById(R.id.take_picture);
            mTakePicture.setOnClickListener(new View.OnClickListener(){
                public void onClick(View view){
                    mCamera.takePicture(null, null, null, PlaceholderFragment.this);
                }
            });
            return rootView;
        }

        private void initPreview(int width, int height) {
            if (mCamera!=null && mPreviewHolder.getSurface()!=null) {
                try {
                    mCamera.setPreviewDisplay(mPreviewHolder);
                }
                catch (Throwable t) {
                    Log.e("PreviewDemo-surfaceCallback",
                            "Exception in setPreviewDisplay()", t);
                }

            }
        }



        private void startPreview() {
            if (mCamera!=null) {
                mCamera.startPreview();
            }
        }

        @Override
        public void onPictureTaken(byte[] bytes, Camera camera) {

            BitmapFactory.Options options = new BitmapFactory.Options();

            mCurrentPhoto = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length,options);

            mPhotoDisplay.setImageBitmap(mCurrentPhoto);
            startPreview();
        }

        SurfaceHolder.Callback surfaceCallback=new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                // no-op -- wait until surfaceChanged()
            }

            public void surfaceChanged(SurfaceHolder holder,
                                       int format, int width,
                                       int height) {
                initPreview(width, height);
                startPreview();
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                // no-op
            }
        };

    }
}
