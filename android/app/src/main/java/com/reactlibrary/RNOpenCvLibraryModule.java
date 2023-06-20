package com.reactlibrary;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;

import android.util.Base64;

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

  @ReactMethod
  public void checkForBlurryImage(String imageAsBase64, Callback errorCallback, Callback successCallback) {
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inDither = true;
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      // Base64 이미지 문자열을 비트맵 객체로 디코딩
      byte[] decodedString = Base64.decode(imageAsBase64, Base64.DEFAULT);
      Bitmap image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
      // Bitmap image = decodeSampledBitmapFromFile(imageurl, 2000, 2000);

      // 이미지의 특징을 추출하기 위해서 비트맵을 회색조 매트 오브젝트로 변환
      Mat srcMat = new Mat();
      Utils.bitmapToMat(image, srcMat);
      Mat grayMat = new Mat();
      Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY);

      // 결과 비트맵을 생성하고 매트로 변환
      Bitmap destImage = Bitmap.createBitmap(image);
      Mat destMat = new Mat();
      Utils.bitmapToMat(destImage, destMat);

      // 라플라시안 이미지를 계산하여 이미지의 선명도를 측정
      Mat laplacianImage = new Mat();
      destMat.convertTo(laplacianImage, CvType.CV_8UC1);
      Imgproc.Laplacian(grayMat, laplacianImage, CvType.CV_8UC1);

      // 라플라시안 이미지를 8비트 표현으로 변환하고 비트맵을 생성
      Mat laplacianImage8bit = new Mat();
      laplacianImage.convertTo(laplacianImage8bit, CvType.CV_8UC1);
      Bitmap bmp = Bitmap.createBitmap(laplacianImage8bit.cols(), laplacianImage8bit.rows(), Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(laplacianImage8bit, bmp);

      // 비트맵의 픽셀을 반복하여 최대 라플라시안 값을 찾기
      int[] pixels = new int[bmp.getHeight() * bmp.getWidth()];
      bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
      int maxLap = -16777216; // 16m
      for (int pixel : pixels) {
        if (pixel > maxLap)
          maxLap = pixel;
      }

      // 최대 라플라시안 값을 임계값과 비교하여 이미지가 흐릿한지 확인
      int soglia = -8118750; // -6118750;
      if (maxLap <= soglia) {
        System.out.println("is blur image");
      }

      successCallback.invoke(maxLap <= soglia);
    } catch (Exception e) {
      errorCallback.invoke(e.getMessage());
    }
  }
}