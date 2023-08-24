package com.example.aruco;

import static java.text.AttributedCharacterIterator.Attribute.LANGUAGE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Call;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pools;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.CameraSource;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static String TAG = "MainActivity";
    JavaCameraView javaCameraView;
    Mat mRGBA;
    public Mat frame;
    private ImageView capturedImageView;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private CascadeClassifier faceDetector;
    private static final int defaultDict = Aruco.DICT_4X4_100;
    private int dictionary = defaultDict;
    private TextRecognizer recognizer;
    private TextView text;
    private TextView weight;
    Mat rgb;

    Mat gray;
    private Button captureButton;
    private CameraBridgeViewBase mOpenCvCameraView;
    private CascadeClassifier mCascadeClassifier;
    private final Handler handler = new Handler();
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("Hi", "OpenCV loaded successfully");

                    try {
                        // Load the cascade classifier
                        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt2);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt2.xml");
                        FileOutputStream os = new FileOutputStream(cascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mCascadeClassifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
                        if (mCascadeClassifier.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mCascadeClassifier = null;
                        } else {
                            faceDetector = mCascadeClassifier; // Assign the loaded classifier to faceDetector
                            Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());
                        }

                        cascadeDir.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }

    };

    private final ExecutorService executorService;

    {
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        capturedImageView = findViewById(R.id.captured_image_view);

        mOpenCvCameraView.setMaxFrameSize(2592, 1944);
        text = findViewById(R.id.text);
        weight=findViewById(R.id.textWeight);
        captureButton = findViewById(R.id.capture_button);
//        weight = findViewById(R.id.weight);
        initializeCamera();
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Call the captureFrame method when the button is clicked
                if(mRGBA!=null)captureFrame(mRGBA);
            }
        });

    }

    private void captureFrame(Mat frame) {
        // Check if the frame is available
        if (frame != null) {
            extractTextUsingMLKit(frame.clone());
            Mat processedFrame = detectArucoFace(frame);

            // Convert the processed frame to a bitmap for display
            if (processedFrame != null) {
                // Convert the processed frame to a bitmap for display
                Bitmap processedBitmap = Bitmap.createBitmap(processedFrame.cols(), processedFrame.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(processedFrame, processedBitmap);

                // Release the processed frame since it's no longer needed
                processedFrame.release();

                // Display the processed frame in the ImageView
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Set the bitmap in the ImageView
                        capturedImageView.setImageBitmap(processedBitmap);

                    }
                });

            }

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Clear the ImageView
                            capturedImageView.setImageBitmap(null);
                        }
                    });
                }
            }, 5000); // Delay of 50 seconds

            // Release the input frame
            frame.release();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission granted", Toast.LENGTH_LONG).show();
                initializeCamera();
            } else {
                Toast.makeText(this, "Camera Permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        } else {
            mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
            mOpenCvCameraView.enableFpsMeter();
            mOpenCvCameraView.enableView();
        }

    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {
        if (rgb != null) {
            rgb.release();
            rgb = null;
        }
        if (gray != null) {
            gray.release();
            gray = null;
        }
        if (mRGBA != null) {
            mRGBA.release();
            mRGBA = null;
        }
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (javaCameraView != null) {
            javaCameraView.disableView();
            mOpenCvCameraView.disableView();

        }
        // Shut down the ExecutorService
        executorService.shutdown();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (javaCameraView != null) {
            javaCameraView = null;
            mOpenCvCameraView.disableView();
            javaCameraView.disableView();
        }
    }

    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

//    private Mat rotateMat(Mat input, double angle) {
//        Mat rotated = new Mat(input.size(), input.type());
//        Point center = new Point(input.width() / 2.0, input.height() / 2.0);
//        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);
//        Imgproc.warpAffine(input, rotated, rotationMatrix, input.size());
//        return rotated;
//    }


    private void showText(Mat img, String text, Point org, double[] gravity, Scalar color) {
        int font = Core.FONT_HERSHEY_SIMPLEX;
        int scale = 1;
        int thickness = 2;
        int[] baseline = {0};
        Size ts = Imgproc.getTextSize(text, font, scale, thickness, baseline);
        Point pt = new Point(org.x - gravity[0] * ts.width,
                org.y + gravity[1] * ts.height + gravity[2] * baseline[0]);
        Imgproc.putText(img, text, pt, font, scale, color, thickness, Imgproc.LINE_AA);
    }


    private boolean isFrameProcessing = false;

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // code for the back camera
        if (mRGBA != null) {
            mRGBA.release();
        }
        mRGBA=inputFrame.rgba().clone();
//        if(!isFrameProcessing) {
//            isFrameProcessing = true;
//
//            frame=mRGBA.clone();
//            Log.i("onCameraFrame", "mRGBA is not empty");
//            executorService.submit(() -> detectArucoFace(frame));
//        } else {
//            Log.i("onCameraFrame", "mRGBA is empty");
//        }
        return mRGBA;
    }
    class Marker {
        double id;
        Mat corners;

        Marker(double id, Mat corners) {
            this.id = id;
            this.corners = corners;
        }
    }

    private Mat detectArucoFace(Mat mat) {
        Log.i("detectArucoFace", "Entered detectArucoFace");

        if(mat.empty()){
            Log.e("detectArucoFace", "Frame is empty");
            mat.release();
            return null;
        }
        Mat gray = new Mat();
        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY);
        Dictionary dict = Aruco.getPredefinedDictionary(defaultDict);
        DetectorParameters params = DetectorParameters.create();
        Aruco.detectMarkers(gray, dict, corners, ids, params);
        List<Marker> markers = new ArrayList<>();
        for (int i = 0; i < corners.size(); i++) {
            markers.add(new Marker(ids.get(i, 0)[0], corners.get(i)));
        }

// Sort the markers based on the x and y coordinates of the top-left corner
        Collections.sort(markers, new Comparator<Marker>() {
            @Override
            public int compare(Marker m1, Marker m2) {
                Point p1 = new Point(m1.corners.get(0, 0));
                Point p2 = new Point(m2.corners.get(0, 0));

                // compare by x-coordinate first
//                int yComparison = Double.compare(p1.y, p2.y);
//                if (yComparison != 0) {
//                    return yComparison;
//                }

                // if x-coordinates are equal, compare by y-coordinate
                return Double.compare(p1.y, p2.y);
            }
        });

// Separate the sorted corners and ids
        List<Mat> sortedCorners = new ArrayList<>();
        Mat sortedIds = new Mat();
        for (Marker marker : markers) {
            sortedCorners.add(marker.corners);
            // Create a new Mat for the id and push it back
            Mat idMat = new Mat(1, 1, CvType.CV_32F);
            idMat.put(0, 0, marker.id);
            sortedIds.push_back(idMat);
        }

        List<Point> imgPoints = new ArrayList<>();
        for(int i=0;i<corners.size();i++){
            Mat c=sortedCorners.get(i);
            for (int j = 0; j < 4; j++) {
                Point p = new Point(c.get(0, j));
                imgPoints.add(p);
            }

        }
        List<Point> refPoints = new ArrayList<>();
// Define your reference points here
// for example, let's consider a 4x4 square in a 2D plane
        for (int i = 0; i < sortedIds.rows(); i++) {
            int id = (int) sortedIds.get(i, 0)[0];

            if(id==9) {
                refPoints.add(new Point(600, 50));      // top-left
                refPoints.add(new Point(630, 50));      // top-right
                refPoints.add(new Point(630, 80));      // bottom-right
                refPoints.add(new Point(600, 80));      // bottom-left
            }
            if(id==8) {
                refPoints.add(new Point(600, 120));      // top-left
                refPoints.add(new Point(630, 120));      // top-right
                refPoints.add(new Point(630, 150));      // bottom-right
                refPoints.add(new Point(600, 150));      // bottom-left
            }
            if(id==7) {
                refPoints.add(new Point(600, 180));      // top-left
                refPoints.add(new Point(630, 180));      // top-right
                refPoints.add(new Point(630, 210));      // bottom-right
                refPoints.add(new Point(600, 210));      // bottom-left
            }
            if(id==6) {
                refPoints.add(new Point(600, 240));      // top-left
                refPoints.add(new Point(630, 240));      // top-right
                refPoints.add(new Point(630, 270));      // bottom-right
                refPoints.add(new Point(600, 270));      // bottom-left
            }
            if(id==5) {
                refPoints.add(new Point(600, 300));      // top-left
                refPoints.add(new Point(630, 300));      // top-right
                refPoints.add(new Point(630, 330));      // bottom-right
                refPoints.add(new Point(600, 330));      // bottom-left
            }
            if(id==4) {
                refPoints.add(new Point(600, 360));      // top-left
                refPoints.add(new Point(630, 360));      // top-right
                refPoints.add(new Point(630, 390));      // bottom-right
                refPoints.add(new Point(600, 390));      // bottom-left
            }
            if(id==3) {
                refPoints.add(new Point(600, 420));      // top-left
                refPoints.add(new Point(630, 420));      // top-right
                refPoints.add(new Point(630, 450));      // bottom-right
                refPoints.add(new Point(600, 450));      // bottom-left
            }
            if(id==2) {
                refPoints.add(new Point(600, 480));      // top-left
                refPoints.add(new Point(630, 480));      // top-right
                refPoints.add(new Point(630, 510));      // bottom-right
                refPoints.add(new Point(600, 510));      // bottom-left
            }
            if(id==1) {
                refPoints.add(new Point(600, 540));      // top-left
                refPoints.add(new Point(630, 540));      // top-right
                refPoints.add(new Point(630, 570));      // bottom-right
                refPoints.add(new Point(600, 570));      // bottom-left
            }
            if(id==0) {
                refPoints.add(new Point(600, 600));      // top-left
                refPoints.add(new Point(630, 600));      // top-right
                refPoints.add(new Point(630, 630));      // bottom-right
                refPoints.add(new Point(600, 630));      // bottom-left
            }
        }
        if(imgPoints.size()==refPoints.size() && imgPoints.size()!=0){
//            text.setText("ProcessStarted");
            mat = perspectiveCorrection(mat, imgPoints, refPoints);

//            text.setText("Finished");
        }
        corners.forEach(Mat::release);
        corners.clear();
        ids.release();



        gray.release();


//        frame.release();
        isFrameProcessing=false;
        return mat;

    }
    public Mat perspectiveCorrection(Mat src, List<Point> imgPoints, List<Point> refPoints) {
        MatOfPoint2f imgPoint2f = new MatOfPoint2f();
        imgPoint2f.fromList(imgPoints);
        MatOfPoint2f refPoint2f = new MatOfPoint2f();
        refPoint2f.fromList(refPoints);
//        text.setText("Processing");
        Mat homography = Calib3d.findHomography(imgPoint2f, refPoint2f);
        Mat dst = new Mat();
        Imgproc.warpPerspective(src, dst, homography, src.size());

        Mat gray = new Mat();
        Imgproc.cvtColor(dst, gray, Imgproc.COLOR_RGBA2GRAY);

        double scaleFactor = 1.1;
        int minNeighbors = 7;
        int flags = 0;
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(gray, faces, scaleFactor, minNeighbors, flags);
        Rect[] facesArray = faces.toArray();
        for (Rect face : facesArray) {
            Point faceCenter = new Point(face.x + face.width / 2, face.y - 10);
            double length=((double)(630-faceCenter.y));
            Imgproc.line(dst,faceCenter, new Point(0,faceCenter.y) ,new Scalar(0, 255, 0), 2);
            length/=((double) 6);
            length+=90;
            text.setText(String.format("%.2f", length));
        }


        faces.release();
//        text.setText("Processed");

        gray.release();
        return dst;
    }
    private void extractTextUsingMLKit(Mat rgbaImage) {
        // Convert the RGBA image to a Bitmap
        Bitmap image = Bitmap.createBitmap(rgbaImage.cols(), rgbaImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbaImage, image);

        // Create an ML Kit TextRecognizer
        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Create an ML Kit InputImage from the Bitmap
        InputImage inputImage = InputImage.fromBitmap(image, 0);

        // Process the image and extract text
        Task<Text> result = textRecognizer.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text visionText) {
                        // Get the extracted text
                        String extractedText = visionText.getText();
                        // Process the extracted text as needed

                        for (Text.TextBlock block : visionText.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                // Get the concatenated string in each line
                                String lineString = line.getText();
                                boolean ok=true;
                                for(int i=0;i<lineString.length();i++){
                                    char set=lineString.charAt(i);
                                    if((set<'0' || set>'9') &&(set!='.' && set!=',')){
                                        ok=false;
                                    }
                                }
                                if(ok && lineString.length()>0)weight.setText(lineString);
                                // Add the line string to the list
                            }
                        }

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle any errors that occur during text recognition
                        e.printStackTrace();
                    }
                });
    }



}




