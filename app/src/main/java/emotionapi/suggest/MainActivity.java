package emotionapi.suggest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.entity.ByteArrayEntity;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.client.utils.URIBuilder;
import cz.msebera.android.httpclient.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.CAMERA;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView; // variable to hold the image view in our activity_main.xml
    private TextView resultText; // variable to hold the text view in our activity_main.xml
    private static final int RESULT_LOAD_IMAGE  = 100;
    private static final int REQUEST_CAMERA_CODE = 300;
    private static final int REQUEST_PERMISSION_CODE = 200;
    public static HashMap<String,Float> emotionColl = new HashMap<>();
    public static String maxEmotion = "neutral";
    public static HashMap<String,String> emotionMap = new HashMap<>();
    static {

        emotionMap.put("anger","calm");
        emotionMap.put("contempt","motivational");
        emotionMap.put("disgust","mesmerizing");
        emotionMap.put("fear","faith");
        emotionMap.put("happiness","happy");
        emotionMap.put("neutral","happy");
        emotionMap.put("sadness","happy");
        emotionMap.put("surprise","happy");

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initiate our image view and text view
        imageView = (ImageView) findViewById(R.id.imageView);
        resultText = (TextView) findViewById(R.id.resultText);

        Button getSuggestionsBtn = (Button) findViewById(R.id.getSuggestions);
        getSuggestionsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i1 = new Intent(getApplicationContext(),Suggestions.class);
                startActivity(i1);
            }
        });
    }

    // when the "GET EMOTION" Button is clicked this function is called
    public void getEmotion(View view) {
        // run the GetEmotionCall class in the background
        GetEmotionCall emotionCall = new GetEmotionCall(imageView);
        emotionCall.execute();
    }

    // when the "GET IMAGE" Button is clicked this function is called
    public void getImage(View view) {
            // check if user has given us permission to access the gallery
            if(checkPermission()) {
                Intent choosePhotoIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(choosePhotoIntent, RESULT_LOAD_IMAGE);
            }
            else {
                requestPermission();
            }
    }

    // This function gets the selected picture from the gallery and shows it on the image view
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // get the photo URI from the gallery, find the file path from URI and send the file path to ConfirmPhoto
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {

            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
            Cursor cursor = getContentResolver().query(selectedImage,filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            // a string variable which will store the path to the image in the gallery
            String picturePath= cursor.getString(columnIndex);
            cursor.close();
            Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
            imageView.setImageBitmap(bitmap);
        }

        if (requestCode == REQUEST_CAMERA_CODE && resultCode == RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(photo);
        }
    }

    // convert image to base 64 so that we can send the image to Emotion API
    public byte[] toBase64(ImageView imgPreview) {
        Bitmap bm = ((BitmapDrawable) imgPreview.getDrawable()).getBitmap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
        return baos.toByteArray();
    }


    // if permission is not given we get permission
    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this,new String[]{READ_EXTERNAL_STORAGE,CAMERA}, REQUEST_PERMISSION_CODE);
    }




    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),READ_EXTERNAL_STORAGE);
        int result2 = ContextCompat.checkSelfPermission(getApplicationContext(),CAMERA);
        return result == PackageManager.PERMISSION_GRANTED && result2 == PackageManager.PERMISSION_GRANTED;
    }

    public void getCameraImage(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_CAMERA_CODE);
        }
    }

    // asynchronous class which makes the api call in the background
    private class GetEmotionCall extends AsyncTask<Void, Void, String> {

        private final ImageView img;

        GetEmotionCall(ImageView img) {
            this.img = img;
        }

        // this function is called before the api call is made
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            resultText.setText("Getting results...");
        }

        // this function is called when the api call is made
        @Override
        protected String doInBackground(Void... params) {
            HttpClient httpclient = HttpClients.createDefault();
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            try {
                URIBuilder builder = new URIBuilder("https://westcentralus.api.cognitive.microsoft.com/face/v1.0/detect?returnFaceAttributes=emotion");

                URI uri = builder.build();
                HttpPost request = new HttpPost(uri);
                request.setHeader("Content-Type", "application/octet-stream");
                // enter you subscription key here
                request.setHeader("Ocp-Apim-Subscription-Key", String.valueOf(R.string.microsoft_face_api));

                // Request body.The parameter of setEntity converts the image to base64
                request.setEntity(new ByteArrayEntity(toBase64(img)));

                // getting a response and assigning it to the string res
                HttpResponse response = httpclient.execute(request);
                HttpEntity entity = response.getEntity();
                String res = EntityUtils.toString(entity);
                //Log.d("hhihhi","ghjvh"+res);
                return res;

            }
            catch (Exception e){
                Log.d("a1",e.getMessage());
                return "null";
            }

        }

        // this function is called when we get a result from the API call
        @Override
        protected void onPostExecute(String result) {
            JSONArray jsonArray = null;
            try {
                Log.d("result","result"+result);
                // convert the string to JSONArray
                jsonArray = new JSONArray(result);

                JSONObject obj = jsonArray.getJSONObject(0);
                JSONObject faceAttr = obj.getJSONObject("faceAttributes");
                JSONObject emotions = faceAttr.getJSONObject("emotion");
                emotionColl.put("anger",Float.parseFloat(emotions.getString("anger")));
                emotionColl.put("contempt",Float.parseFloat(emotions.getString("contempt")));
                emotionColl.put("disgust",Float.parseFloat(emotions.getString("disgust")));
                emotionColl.put("fear",Float.parseFloat(emotions.getString("fear")));
                emotionColl.put("happiness",Float.parseFloat(emotions.getString("happiness")));
                emotionColl.put("neutral",Float.parseFloat(emotions.getString("neutral")));
                emotionColl.put("sadness",Float.parseFloat(emotions.getString("sadness")));
                emotionColl.put("surprise",Float.parseFloat(emotions.getString("surprise")));


                for (Map.Entry<String,Float> m : emotionColl.entrySet()) {
                    emotionColl.put(m.getKey(),m.getValue());
                }
                    maxEmotion = "anger";
                for (Map.Entry<String,Float> m : emotionColl.entrySet()){
                    String emo = m.getKey();
                    Float val = m.getValue();
                    //Log.d("emo",emo);

                    //Log.d("val",val.toString());
                    //Log.d("max",emotionColl.get(maxEmotion).toString());

                    Float f = emotionColl.get(maxEmotion);

                    if(val > f)
                    {
                        maxEmotion = emo;
                    }
                }
                resultText.setText(maxEmotion);

            } catch (JSONException e) {
                Log.d("abc","abc"+e.getMessage());
                resultText.setText("Unrecognizable Picture");
            }
        }


    }
}
