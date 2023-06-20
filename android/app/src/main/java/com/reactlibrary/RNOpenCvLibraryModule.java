package com.reactlibrary;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import org.opencv.android.Utils;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class RNOpenCvLibraryModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public RNOpenCvLibraryModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNOpenCvLibrary";
  }

  private String matToBase64Image(Mat image) {
    // OpenCV 매트 이미지를 비트맵으로 변환
    Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(image, bitmap);
    // 비트맵을 Base64로 인코딩된 문자열로 변환
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
    byte[] byteArray = byteArrayOutputStream.toByteArray();
    String base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT);
    return base64Image;
  }

  private Mat blurGrayFor(Mat image) {
    // 이미지의 특징을 추출하기 위해서 회색조 매트 오브젝트로 변환
    Mat gray = new Mat();
    Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
    Mat blurGray = new Mat();
    Imgproc.blur(gray, blurGray, new Size(3, 3));
    return blurGray;
  }

  private List<MatOfPoint> rectanglesFor(List<MatOfPoint> contours) {
    List<MatOfPoint> rectangles = new ArrayList<>();

    for (MatOfPoint contour : contours) {
      double epsilon = 0.02 * Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
      MatOfPoint2f approx = new MatOfPoint2f();
      Imgproc.approxPolyDP(new MatOfPoint2f(contour.toArray()), approx, epsilon, true);
      if (approx.total() == 4) {
        rectangles.add(contour);
      }
    }

    return rectangles;
  }

  @ReactMethod
  public void checkForRectangle(String imageAsBase64, Callback errorCallback, Callback successCallback) {
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inDither = true;
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      // Base64 이미지 문자열을 비트맵 객체로 디코딩 후, MAT객체로 변환
      byte[] decodedString = Base64.decode(imageAsBase64, Base64.DEFAULT);
      Bitmap sourceBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
      Mat image = new Mat();
      Utils.bitmapToMat(sourceBitmap, image);

      // 이미지의 특징을 추출하기 위해서 비트맵을 회색조 매트 오브젝트로 변환
      Mat blurGray = blurGrayFor(image);

      // 가장자리 감지를 수행: 캐니 가장자리 감지 알고리즘을 그레이스케일 이미지에 적용
      // threshold: 에지가 너무 약하거나 노이즈가 있는 경우 낮춰라. 반대로 에지가 너무 강하거나 중요한 에지가 많이 누락된 경우 높여라
      Mat edges = new Mat();
      int threshold = 50;
      Imgproc.Canny(blurGray, edges, threshold, threshold * 3);

      // RETR_EXTERNAL은 다른 윤곽선 안에 포함된 윤곽선은 무시하고 외부(외부) 윤곽선만 검색(윤곽 검색 모드)
      // CHAIN_APPROX_SIMPLE는 가로, 세로, 대각선 세그먼트를 각각의 끝점으로 압축하고 중간 지점은 버림(윤곽 근사화 방법)
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

      List<MatOfPoint> rectangles = rectanglesFor(contours);

      // 직사각형을 그리기: 원본 이미지에 감지된 직사각형을 그림
      Imgproc.drawContours(image, rectangles, -1, new Scalar(0, 255, 0), 2);

      String base64Image = matToBase64Image(image);

      successCallback.invoke(base64Image);
    } catch (Exception e) {
      errorCallback.invoke(e.getMessage());
    }
  }
}