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
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements Camera.PictureCallback {

        // Constants

        static String TWITTER_CONSUMER_KEY = "";
        static String TWITTER_CONSUMER_SECRET = “”;

        // Preference Constants
        static String PREFERENCE_NAME = "twitter_oauth";
        static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
        static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
        static final String PREF_KEY_TWITTER_LOGIN = "isTwitterLoggedIn";

        static final String TWITTER_CALLBACK_URL = "oauth://t4jsample";

        // Twitter oauth urls
        static final String URL_TWITTER_AUTH = "auth_url";
        static final String URL_TWITTER_OAUTH_VERIFIER = "oauth_verifier";
        static final String URL_TWITTER_OAUTH_TOKEN = "oauth_token";

        private Camera mCamera;
        private ImageView mPhotoDisplay;
        private Button mTakePicture;
        private SurfaceHolder mPreviewHolder;
        private Button mLoginButton;
        private Button mUploadButton;
        private static SharedPreferences mSharedPreferences;
        private static Twitter mTwitter;
        private static RequestToken mRequestToken;
        private File mCurrentPhoto;
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

        private void loginToTwitter() {
            // Check if already logged in
            if (!isTwitterLoggedInAlready()) {
                ConfigurationBuilder builder = new ConfigurationBuilder();
                builder.setOAuthConsumerKey(TWITTER_CONSUMER_KEY);
                builder.setOAuthConsumerSecret(TWITTER_CONSUMER_SECRET);
                twitter4j.conf.Configuration configuration = builder.build();

                TwitterFactory factory = new TwitterFactory(configuration);
                mTwitter = factory.getInstance();

                try {
                    mRequestToken = mTwitter
                            .getOAuthRequestToken(TWITTER_CALLBACK_URL);
                    this.startActivity(new Intent(Intent.ACTION_VIEW, Uri
                            .parse(mRequestToken.getAuthenticationURL())));
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
        }



        /**
         * Check user already logged in your application using twitter Login flag is
         * fetched from Shared Preferences
         * */
        private boolean isTwitterLoggedInAlready() {
            // return twitter login status from Shared Preferences
            return mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            openCamera();
            mPhotoDisplay = (ImageView) rootView.findViewById(R.id.photo);
            mLoginButton = (Button) rootView.findViewById(R.id.login);
            mUploadButton = (Button) rootView.findViewById(R.id.upload_picture);

            mSharedPreferences = this.getActivity().getApplicationContext().getSharedPreferences(
                    "MyPref", 0);

            mLoginButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    // Call login twitter function
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            loginToTwitter();
                        }
                    }).start();
                }
            });


            mUploadButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    try {
                        new updateTwitterStatus().execute("test");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            if (!isTwitterLoggedInAlready()) {
                final Uri uri = this.getActivity().getIntent().getData();
                if (uri != null && uri.toString().startsWith(TWITTER_CALLBACK_URL)) {
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            // oAuth verifier
                            String verifier = uri
                                    .getQueryParameter(URL_TWITTER_OAUTH_VERIFIER);

                            try {
                                // Get the access token
                                AccessToken accessToken = mTwitter.getOAuthAccessToken(
                                        mRequestToken, verifier);

                                // Shared Preferences
                                SharedPreferences.Editor e = mSharedPreferences.edit();

                                // After getting access token, access token secret
                                // store them in application preferences
                                e.putString(PREF_KEY_OAUTH_TOKEN, accessToken.getToken());
                                e.putString(PREF_KEY_OAUTH_SECRET,
                                        accessToken.getTokenSecret());
                                // Store login status - true
                                e.putBoolean(PREF_KEY_TWITTER_LOGIN, true);
                                e.commit(); // save changes

                                Log.e("Twitter OAuth Token", "> " + accessToken.getToken());

                                // Hide login button
                                mLoginButton.setVisibility(View.GONE);

                            } catch (Exception e) {
                                // Check log for login errors
                                Log.e("Twitter Login Error", "> " + e.getMessage());
                            }
                        }
                    }).start();
                }
            }

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
            File photo=new File(Environment.getExternalStorageDirectory(), "photo.jpg");

            if (photo.exists()) {
                photo.delete();
            }

            try {
                FileOutputStream fos=new FileOutputStream(photo.getPath());

                fos.write(bytes);
                fos.close();
            }
            catch (java.io.IOException e) {
                Log.e("PictureDemo", "Exception in photoCallback", e);
            }

            mCurrentPhoto = photo;
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

        /**
         * Function to update status
         * */
        class updateTwitterStatus extends AsyncTask<String, String, String> {

            /**
             * Before starting background thread Show Progress Dialog
             * */
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                pDialog = new ProgressDialog(getActivity());
                pDialog.setMessage("Updating to twitter...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(false);
                pDialog.show();
            }

            /**
             * getting Places JSON
             * */
            protected String doInBackground(String... args) {
                    Log.d("Tweet Text", "> " + args[0]);
                    String status = args[0];
                    ConfigurationBuilder builder = new ConfigurationBuilder();
                    builder.setOAuthConsumerKey(TWITTER_CONSUMER_KEY);
                    builder.setOAuthConsumerSecret(TWITTER_CONSUMER_SECRET);

                    // Access Token
                    String access_token = mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, "");
                    // Access Token Secret
                    String access_token_secret = mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, "");

                    AccessToken accessToken = new AccessToken(access_token, access_token_secret);
                     mTwitter = new TwitterFactory(builder.build()).getInstance(accessToken);


                        try{
                            StatusUpdate statusUpdate = new StatusUpdate(status);
                            statusUpdate.setMedia(mCurrentPhoto);
                            mTwitter.updateStatus(status);
                        }catch(TwitterException e){
                            Log.d("TAG", "Pic Upload error" + e.getErrorMessage());
                        }
                    return null;
                }


            /**
             * After completing background task Dismiss the progress dialog and show
             * the data in UI Always use runOnUiThread(new Runnable()) to update UI
             * from background thread, otherwise you will get error
             * **/
            protected void onPostExecute(String file_url) {
                // dismiss the dialog after getting all products
                pDialog.dismiss();


            }

        }
    }
}
