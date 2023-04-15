package com.example.myhealthypregnancyapp;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.yalantis.ucrop.UCrop;
import org.apache.commons.text.similarity.LevenshteinDistance;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int UCROP_REQUEST_CODE = 1001;

    private AppBarConfiguration appBarConfiguration;

    private TextView textView2;
    private Bitmap croppedBitmap;
    private Uri imageUri;

    private final ActivityResultLauncher<Intent> getContentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startCropActivity(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), result -> {
                if (result) {
                    startCropActivity(imageUri);
                } else {
                    // Handle the case when the image capture fails
                }
            }
    );

    private final List<String> ingredientsToAvoid = Arrays.asList(
            "Retinoids",
            "Retinol",
            "Retinyl palmitate",
            "Retinoic acid",
            "Hydroquinone",
            "Salicylic acid",
            "Benzoyl peroxide",
            "Phthalates",
            "Parabens",
            "Formaldehyde-releasing preservatives",
            "Oxybenzone",
            "Avobenzone",
            "Octinoxate",
            "Rosemary essential oil",
            "Sage essential oil",
            "Juniper essential oil",
            "Differin (adapalene)",
            "Retin-A",
            "Renova (tretinoin)",
            "Retinyl linoleate",
            "Avage (Tazarotene)",
            "Tazorac",
            "Beta hydroxy acid",
            "BHA",
            "Lecithin",
            "Phosphatidylcholine",
            "Soy",
            "Textured vegetable protein (TVP)",
            "Tazarotene"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView2 = findViewById(R.id.textview_first);

        setSupportActionBar(findViewById(R.id.toolbar));
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        FloatingActionButton fabCamera = findViewById(R.id.fabCamera);
        fabCamera.setOnClickListener(view -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            takePictureLauncher.launch(imageUri);
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            getContentLauncher.launch(intent);
        });
    }

    private void startCropActivity(Uri sourceUri) {
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped"));
        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .start(MainActivity.this, UCROP_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCROP_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri croppedUri = UCrop.getOutput(data);
                if (croppedUri != null) {
                    try {
                        croppedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), croppedUri);
                        recognizeText(croppedBitmap, getRotationDegree(croppedUri));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void recognizeText(Bitmap bitmap, int rotationDegree) throws IOException {
        InputImage image = InputImage.fromBitmap(bitmap, rotationDegree);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> result = recognizer.process(image)
                .addOnSuccessListener(result1 -> {
                    String resultText = result1.getText();
                    String[] words = resultText.split("\\s*\\n\\s*|\\s*,\\s*|\\s*\\\\\\s*");
                    List<String> filteredWords = new ArrayList<>();

                    for (String word : words) {
                        if (!word.trim().isEmpty()) {
                            filteredWords.add(word);
                        }
                    }

                    SpannableStringBuilder spannableText = new SpannableStringBuilder();

                    LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

                    for (String word : filteredWords) {
                        boolean containsWord = false;
                        int minDistance = Integer.MAX_VALUE;
                        boolean exactMatch = false;

                        for (String ingredient : ingredientsToAvoid) {
                            String[] ingredientWords = word.split("\\s+");
                            containsWord = false;

                            int distance = levenshteinDistance.apply(word.toLowerCase(), ingredient.toLowerCase());
                            if (distance < minDistance) {
                                minDistance = distance;
                                exactMatch = distance == 1 || distance == 0;
                            }
                            if(exactMatch) {
                                break;
                            }
                            for (String ingredientWord : ingredientWords) {
                                if (ingredient.equalsIgnoreCase(ingredientWord)) {
                                    containsWord = true;
                                    break;
                                }
                            }
                            if(containsWord){
                                break;
                            }
                        }

                        if (exactMatch) {
                            spannableText.append(word);
                            int startPos = spannableText.length() - word.length();
                            int endPos = spannableText.length();
                            spannableText.setSpan(new StyleSpan(Typeface.BOLD), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannableText.setSpan(new CustomClickableSpan(Color.RED), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (minDistance <= 2 || containsWord) { // Adjust this value to change the similarity threshold
                            spannableText.append(word);
                            int startPos = spannableText.length() - word.length();
                            int endPos = spannableText.length();
                            int orangeColor = Color.rgb(255, 165, 0);
                            spannableText.setSpan(new StyleSpan(Typeface.BOLD), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannableText.setSpan(new CustomClickableSpan(orangeColor), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            spannableText.append(word);
                        }
                        spannableText.append("\n");
                    }

                    textView2.setText(spannableText);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Error recognizing text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Task failed with an exception
                });
    }

    public class CustomClickableSpan extends ClickableSpan {
        private final int textColor;

        public CustomClickableSpan(int textColor) {
            this.textColor = textColor;
        }

        @Override
        public void onClick(@NonNull View widget) {
            TextView textView = (TextView) widget;
            Spanned s = (Spanned) textView.getText();
            int start = s.getSpanStart(this);
            int end = s.getSpanEnd(this);
            String word = s.subSequence(start, end).toString();
            openGoogleSearch(word);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(textColor);
        }
    }

    private void openGoogleSearch(String searchText) {
        String url = "https://www.google.com/search?q=" + Uri.encode(searchText + " for pregnant women");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private int getRotationDegree(Uri fileUri) {
        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(fileUri));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rotation;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_exit) {
            finish(); // Close the current activity
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}


