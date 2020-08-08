/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.view.camera;

import com.google.common.base.Preconditions;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import org.ros.exception.RosRuntimeException;

import java.io.IOException;
import java.util.List;

/**
 * Displays preview frames from the camera.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public class CameraPreviewView {

  private final static double ASPECT_TOLERANCE = 0.1;

  private SurfaceTexture surfaceTexture;
  private Camera camera;
  private Size previewSize;
  private byte[] previewBuffer;
  private RawImageListener rawImageListener;
  private BufferingPreviewCallback bufferingPreviewCallback;

  public CameraPreviewView() {
    surfaceTexture = new SurfaceTexture(10);
    bufferingPreviewCallback = new BufferingPreviewCallback();
  }

  private final class BufferingPreviewCallback implements PreviewCallback {
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
      Preconditions.checkArgument(camera == CameraPreviewView.this.camera);
      Preconditions.checkArgument(data == previewBuffer);
      if (rawImageListener != null) {
        rawImageListener.onNewRawImage(data, previewSize);
      }
      camera.addCallbackBuffer(previewBuffer);
    }
  }

  public void releaseCamera() {
    if (camera == null) {
      return;
    }
    camera.setPreviewCallbackWithBuffer(null);
    camera.stopPreview();
    camera.release();
    camera = null;
  }

  public void setRawImageListener(RawImageListener rawImageListener) {
    this.rawImageListener = rawImageListener;
  }

  public Size getPreviewSize() {
    return previewSize;
  }

  public void setCamera(Camera camera) {
    Preconditions.checkNotNull(camera);
    this.camera = camera;
    setupCameraParameters();
    setupBufferingPreviewCallback();
    camera.startPreview();
    try {
      // This may have no effect if the SurfaceHolder is not yet created.
      camera.setPreviewTexture(surfaceTexture);
    } catch (IOException e) {
      throw new RosRuntimeException(e);
    }
  }

  private void setupCameraParameters() {
    Camera.Parameters parameters = camera.getParameters();
    List<Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
    previewSize = getOptimalPreviewSize(supportedPreviewSizes, 640, 480);
    parameters.setPreviewSize(previewSize.width, previewSize.height);
    parameters.setPreviewFormat(ImageFormat.NV21);
    camera.setParameters(parameters);
  }

  private Size getOptimalPreviewSize(List<Size> sizes, int width, int height) {
    Preconditions.checkNotNull(sizes);
    double targetRatio = (double) width / height;
    double minimumDifference = Double.MAX_VALUE;
    Size optimalSize = null;

    // Try to find a size that matches the aspect ratio and size.
    for (Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
        continue;
      }
      if (Math.abs(size.height - height) < minimumDifference) {
        optimalSize = size;
        minimumDifference = Math.abs(size.height - height);
      }
    }

    // Cannot find one that matches the aspect ratio, ignore the requirement.
    if (optimalSize == null) {
      minimumDifference = Double.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.height - height) < minimumDifference) {
          optimalSize = size;
          minimumDifference = Math.abs(size.height - height);
        }
      }
    }

    Preconditions.checkNotNull(optimalSize);
    return optimalSize;
  }

  private void setupBufferingPreviewCallback() {
    int format = camera.getParameters().getPreviewFormat();
    int bits_per_pixel = ImageFormat.getBitsPerPixel(format);
    previewBuffer = new byte[previewSize.height * previewSize.width * bits_per_pixel / 8];
    camera.addCallbackBuffer(previewBuffer);
    camera.setPreviewCallbackWithBuffer(bufferingPreviewCallback);
  }
}
