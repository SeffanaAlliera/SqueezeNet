package com.sasoft.squeezenet;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Tensor;
import org.pytorch.Module;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class CapturedImageActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView textView;
    private Module module;
    private static final String TAG = CapturedImageActivity.class.getSimpleName();

    // Define class labels
    private List<String> classLabels = Arrays.asList("Cat", "Dog");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captured_image);

        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);

        // Get the image file path from the intent
        Intent intent = getIntent();
        String imagePath = intent.getStringExtra("imageFilePath");

        // Load the captured image into the ImageView
        if (imagePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setImageBitmap(bitmap);

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            // Perform image compression in a background thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Convert the Bitmap to a byte array
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                        byte[] byteArray = stream.toByteArray();

                        // Access the Python module
                        Python py = Python.getInstance();
                        PyObject pyObj = py.getModule("squeezenet");

                        // Call the predict method from your Python script
                        PyObject result = pyObj.callAttr("predict", byteArray);
                        String prediction = result.toString();

                        // Update the TextView with the prediction (assuming textView is your TextView)
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(prediction);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Handle errors gracefully
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText("Error: " + e.getMessage());
                            }
                        });
                    }
                }
            }).start();
            /*try {
                // Load the model
                String modelFilePath = assetFilePath("catsdogsmodel.pt");
                module = Module.load(modelFilePath);
                Toast.makeText(CapturedImageActivity.this, "Model Loaded", Toast.LENGTH_SHORT).show();

                // Preprocess the image
                Tensor inputTensor = preprocessImage(imagePath);

                // Perform inference
                performInference(inputTensor);
            } catch (Exception e) {
                Log.e(TAG, "Error loading model", e);
                Toast.makeText(CapturedImageActivity.this, "Model Not Loaded", Toast.LENGTH_SHORT).show();
            }*/
        }
    }
    /*public String assetFilePath(String assetName) throws IOException {
        File file = new File(this.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = this.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
    // Preprocess the input image
    public static Tensor preprocessImage(String imagePath) {
        try {
            // Load the image from file
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

            // Resize the image to 256x256
            Bitmap resizedBitmap = resizeImage(bitmap, 256, 256);

            // Center crop the image to 224x224
            Bitmap croppedBitmap = centerCrop(resizedBitmap, 224, 224);

            // Convert Bitmap to float array
            float[] floatValues = convertBitmapToFloatArray(croppedBitmap);

            // Create tensor from float array
            return Tensor.fromBlob(floatValues, new long[]{1, 3, 224, 224});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Resize the image
    private static Bitmap resizeImage(Bitmap image, int width, int height) {
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    // Center crop the image
    private static Bitmap centerCrop(Bitmap image, int newWidth, int newHeight) {
        int width = image.getWidth();
        int height = image.getHeight();

        int left = (width - newWidth) / 2;
        int top = (height - newHeight) / 2;
        int right = left + newWidth;
        int bottom = top + newHeight;

        return Bitmap.createBitmap(image, left, top, newWidth, newHeight);
    }

    // Convert Bitmap to float array with normalization
    private static float[] convertBitmapToFloatArray(Bitmap bitmap) {
        int[] intValues = new int[224 * 224];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        float[] floatValues = new float[3 * 224 * 224];
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            // Normalize and set values based on mean and standard deviation
            floatValues[i * 3 + 0] = (((val >> 16) & 0xFF) / 255.0f - 0.485f) / 0.229f; // Red channel
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) / 255.0f - 0.456f) / 0.224f;  // Green channel
            floatValues[i * 3 + 2] = ((val & 0xFF) / 255.0f - 0.406f) / 0.225f;          // Blue channel
        }
        return floatValues;
    }

    // Perform inference using the preprocessed image tensor
    private void performInference(Tensor inputTensor) {
        if (module == null) {
            Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Perform inference
            IValue outputTensor = module.forward(IValue.from(inputTensor));

            long inferenceTime = System.currentTimeMillis() - startTime;

            // Process the output tensor
            float[] outputArray = outputTensor.toTensor().getDataAsFloatArray();

            // Find the index with maximum probability and calculate its percentage
            int maxIndex = 0;
            float maxProbability = outputArray[0];
            for (int i = 1; i < outputArray.length; i++) {
                if (outputArray[i] > maxProbability) {
                    maxProbability = outputArray[i];
                    maxIndex = i;
                }
            }

            // Calculate the sum of all probabilities for normalization
            float sumProbabilities = 0.0f;
            for (float probability : outputArray) {
                sumProbabilities += probability;
            }

            // Convert probability to percentage
            float maxProbabilityPercentage = maxProbability / sumProbabilities * 100;

            // Display confidence for all classes
            StringBuilder confidenceText = new StringBuilder();
            for (int i = 0; i < outputArray.length; i++) {
                // Convert probability to percentage
                float probabilityPercentage = outputArray[i] / sumProbabilities * 100;

                // Get the predicted class label (you need to have a list of class labels)
                String predictedLabel = (i < classLabels.size()) ? classLabels.get(i) : "Unknown";

                // Append the confidence for this class to the StringBuilder
                confidenceText.append(predictedLabel).append(": ").append(String.format("%.2f", probabilityPercentage)).append("%\n");
            }

            // Set the result in the TextView
            textView.setText("Predicted class: " + classLabels.get(maxIndex) + ". Confidence: " + String.format("%.2f", maxProbabilityPercentage) + "%. \n" + "Confidence for other classes:\n" + confidenceText.toString() + " Inference time: " + inferenceTime + "ms");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error performing inference", Toast.LENGTH_SHORT).show();
        }
    }*/

}
