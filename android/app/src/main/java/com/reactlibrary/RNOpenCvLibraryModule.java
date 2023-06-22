package com.reactlibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import org.opencv.android.Utils;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
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

  private void edgesFor(Mat image) {
    // 가장자리 감지를 수행: 캐니 가장자리 감지 알고리즘을 그레이스케일 이미지에 적용
    // threshold: 에지가 너무 약하거나 노이즈가 있는 경우 낮춰라. 반대로 에지가 너무 강하거나 중요한 에지가 많이 누락된 경우 높여라
    int threshold = 30;
    Imgproc.Canny(image, image, threshold, threshold * 3);
    Imgproc.dilate(image, image, new Mat(), new Point(-1, -1), 1);
  }

  private Mat processImage(Mat image) {
    // 이미지의 특징을 추출하기 위해서 회색조 매트 오브젝트로 변환
    Mat result = new Mat();
    Imgproc.cvtColor(image, result, Imgproc.COLOR_BGR2GRAY);
    Imgproc.GaussianBlur(result, result, new Size(5, 5), 0);
    // Imgproc.adaptiveThreshold(result, result, 255,
    // Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 7, 5);
    edgesFor(result);
    return result;
  }

  private Mat largestRectangleFor(List<MatOfPoint> contours, Mat image) {
    double maxArea = 0;
    Rect largestRect = null;
    for (MatOfPoint contour : contours) {
      double contourArea = Imgproc.contourArea(contour);
      if (Math.abs(contourArea) < 100) {
        continue;
      }

      MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
      MatOfPoint2f approx = new MatOfPoint2f();
      double epsilon = 0.02 * Imgproc.arcLength(curve, true);
      Imgproc.approxPolyDP(curve, approx, epsilon, true);
      if (approx.total() == 4) {
        double area = Imgproc.contourArea(approx);
        if (area > maxArea) {
          maxArea = area;
          largestRect = Imgproc.boundingRect(new MatOfPoint(approx.toArray()));
        }
      }
    }
    if (largestRect == null) {
      return null;
    }

    Imgproc.rectangle(image, largestRect.tl(), largestRect.br(), new Scalar(0, 255, 0), 2);
    boolean isSizeValidation = largestRect.width > image.width() * 0.5 && largestRect.height > image.height() * 0.5;
    if (!isSizeValidation) {
      return null;
    }

    Mat croppedImage = new Mat(image, largestRect);
    return croppedImage;
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
      Mat processed = processImage(image);

      // RETR_EXTERNAL은 다른 윤곽선 안에 포함된 윤곽선은 무시하고 외부(외부) 윤곽선만 검색(윤곽 검색 모드)
      // CHAIN_APPROX_SIMPLE는 가로, 세로, 대각선 세그먼트를 각각의 끝점으로 압축하고 중간 지점은 버림(윤곽 근사화 방법)
      List<MatOfPoint> contours = new ArrayList<>();
      Mat hierarchy = new Mat();
      Imgproc.findContours(processed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

      Mat croppedMat = largestRectangleFor(contours, image);
      String originImage = matToBase64Image(image);
      String croppedImage = croppedMat == null ? null : matToBase64Image(croppedMat);

      WritableArray array = Arguments.createArray();
      array.pushString(originImage);
      array.pushString(croppedImage);
      successCallback.invoke(array);
    } catch (Exception e) {
      errorCallback.invoke(e.getMessage());
    }
  }

  private double angle(Point pt1, Point pt2, Point pt0) {
    double dx1 = pt1.x - pt0.x;
    double dy1 = pt1.y - pt0.y;
    double dx2 = pt2.x - pt0.x;
    double dy2 = pt2.y - pt0.y;
    return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
  }

  private boolean checkCosine(MatOfPoint2f approx) {
    boolean result = false;
    int verticeCnt = (int) approx.total();
    if (verticeCnt >= 4 && verticeCnt <= 6) {
      List<Double> cos = new ArrayList<>();
      for (int j = 2; j < verticeCnt + 1; j++) {
        cos.add(angle(approx.toArray()[j % verticeCnt], approx.toArray()[j - 2],
            approx.toArray()[j - 1]));
      }
      Collections.sort(cos);
      double mincos = cos.get(0);
      double maxcos = cos.get(cos.size() - 1);

      result = verticeCnt == 4 && mincos >= -0.1 && maxcos <= 0.3;
    }
    return result;
  }

  private List<MatOfPoint> rectanglesFor(List<MatOfPoint> contours, Mat image) {
    List<MatOfPoint> rectangles = new ArrayList<>();

    for (MatOfPoint contour : contours) {
      double contourArea = Imgproc.contourArea(contour);
      if (Math.abs(contourArea) < 100) {
        continue;
      }

      MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
      MatOfPoint2f approx = new MatOfPoint2f();
      double epsilon = 0.02 * Imgproc.arcLength(curve, true);
      Imgproc.approxPolyDP(curve, approx, epsilon, true);

      if (approx.total() == 4) {
        Rect rect = Imgproc.boundingRect(new MatOfPoint(approx.toArray()));
        Imgproc.rectangle(image, rect.tl(), rect.br(), new Scalar(0, 255, 0), 2);
        rectangles.add(contour);
      }
    }

    return rectangles;
  }
}