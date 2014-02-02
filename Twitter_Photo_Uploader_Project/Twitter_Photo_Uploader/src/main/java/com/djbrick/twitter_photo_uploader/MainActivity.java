package com.djbrick.twitter_photo_uploader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import android.view.Surface;
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

        static String TWITTER_CONSUMER_KEY = "";
        static String TWITTER_CONSUMER_SECRET = "";

        private ProgressDialog mDialog;
        private Camera mCamera;
        private ImageView mPhotoDisplay;
        private Button mTakePicture;
        private Button mRetakeButton;
        private SurfaceView mCameraSurface;
        private Button mUploadButton;
        private Bitmap mCurrentPhoto;
        private MSTwitter mMSTwitter;

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
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }catch (Exception e){

            }
        }



        /**
         * Send tweet using MSTwitter object created in onCreate()
         */
        private void tweet() {
            // assemble data
            String textToTweet = "test";
            String tweetImagePath = MSTwitter.putBitmapInDiskCache(this.getActivity(), mCurrentPhoto);

            // start the tweet
            mMSTwitter.startTweet(textToTweet, tweetImagePath);

        }

        private void handleFinish(String message){
            mDialog.cancel();
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(message).setPositiveButton("ok",new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
            MSTwitter.clearCredentials(getActivity());
            setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);
            startPreview();
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
                    handleFinish(note);
                    break;
                case MSTwitter.MSTWEET_STATUS_FINSIHED_FAILED:
                    note = "Tweet failed:" + message;
                    handleFinish(note);
                    break;
            }

            // add note to results TextView
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm:ss.S");
            String timeS = timeFmt.format(new Date());

            Log.d("Photo Upload","\n[Message received at " + timeS +"]\n" + note);

        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            mPhotoDisplay = (ImageView) rootView.findViewById(R.id.photo);
            mUploadButton = (Button) rootView.findViewById(R.id.upload_picture);
            mRetakeButton = (Button) rootView.findViewById(R.id.take_new_picture);
            openCamera();
            setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);


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
                            "Uploading Photo. Please wait...", true);
                    mDialog.show();
                    tweet();
                }
            });

            mRetakeButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    startPreview();
                    mRetakeButton.setVisibility(View.GONE);
                }
            });


            mCameraSurface = (SurfaceView) rootView.findViewById(R.id.camera_surface);
            SurfaceHolder previewHolder = mCameraSurface.getHolder();
            previewHolder.addCallback(surfaceCallback);

            try {
                mCamera.setPreviewDisplay(previewHolder);
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
            if (mCamera!=null && mCameraSurface.getHolder().getSurface()!=null) {
                try {
                    mCamera.setPreviewDisplay(mCameraSurface.getHolder());
                }
                catch (Throwable t) {
                    Log.e("PreviewDemo-surfaceCallback",
                            "Exception in setPreviewDisplay()", t);
                }

            }
        }


        private void setCameraDisplayOrientation(Activity activity,
                                                       int cameraId, android.hardware.Camera camera) {
            android.hardware.Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = activity.getWindowManager().getDefaultDisplay()
                    .getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(result);
        }

        private void startPreview() {
            mCameraSurface.setVisibility(View.VISIBLE);
            mPhotoDisplay.setVisibility(View.GONE);
            mUploadButton.setVisibility(View.GONE);
            mTakePicture.setVisibility(View.VISIBLE);
            if (mCamera!=null) {
                mCamera.startPreview();
            }
        }

        @Override
        public void onPictureTaken(byte[] bytes, Camera camera) {

            BitmapFactory.Options options = new BitmapFactory.Options();

            mCurrentPhoto = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length,options);

            // photo defaults to landscape rotate it to portrait
            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            mCurrentPhoto = Bitmap.createBitmap(mCurrentPhoto, 0, 0, mCurrentPhoto.getWidth(), mCurrentPhoto.getHeight(), matrix, true);


            mCameraSurface.setVisibility(View.GONE);
            mTakePicture.setVisibility(View.GONE);
            mUploadButton.setVisibility(View.VISIBLE);
            mPhotoDisplay.setVisibility(View.VISIBLE);
            mRetakeButton.setVisibility(View.VISIBLE);
            mPhotoDisplay.setImageBitmap(mCurrentPhoto);
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
