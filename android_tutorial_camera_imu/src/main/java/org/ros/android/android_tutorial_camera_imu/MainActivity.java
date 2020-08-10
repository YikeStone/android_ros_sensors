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
package org.ros.android.android_tutorial_camera_imu;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.Toast;

import android.util.Log;
import org.ros.address.InetAddressFactory;
import org.ros.android.MasterChooser;
import org.ros.android.RosActivity;
import org.ros.android.view.camera.RosCameraPreviewView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import android.content.SharedPreferences;
import java.util.Random;

/**
 * @author ethan.rublee@gmail.com (Ethan Rublee)
 * @author damonkohler@google.com (Damon Kohler)
 * @author huaibovip@gmail.com (Charles)
 */

public class MainActivity extends RosActivity {

    private int cameraId = 0;
    private RosCameraPreviewView rosCameraPreviewView;
    private NavSatFixPublisher fix_pub;
    private ImuPublisher imu_pub;

    private Switch cameraSwitch;
    private Switch imuSwitch;
    private Switch gpsSwitch;

    private boolean cameraRunning;

    private NodeMainExecutor nodeMainExecutor;
    private LocationManager mLocationManager;
    private SensorManager mSensorManager;
    private static String sID = "";

    private NodeConfiguration nodeConfigurationGPS, nodeConfigurationCamera, nodeConfigurationImu;

    public MainActivity() {
        super("ROS", "Camera & Imu");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        cameraRunning = false;
        cameraSwitch = (Switch) findViewById(R.id.camera_switch);
        imuSwitch = (Switch) findViewById(R.id.imu_switch);
        gpsSwitch = (Switch) findViewById(R.id.gps_switch);

        imuSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked)
                    nodeMainExecutor.execute(imu_pub, nodeConfigurationImu);

                else
                    nodeMainExecutor.shutdownNodeMain(imu_pub);

                Log.v("IMU publisher", ""+isChecked);
            }
        });

        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked)
                    nodeMainExecutor.execute(fix_pub, nodeConfigurationGPS);

                else
                    nodeMainExecutor.shutdownNodeMain(fix_pub);

                Log.v("IMU publisher", ""+isChecked);
            }
        });

        cameraSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked) {
                    rosCameraPreviewView.setCamera(getCamera());
                    nodeMainExecutor.execute(rosCameraPreviewView, nodeConfigurationCamera);
                    cameraRunning = true;
                }
                else {
                    nodeMainExecutor.shutdownNodeMain(rosCameraPreviewView);
                    cameraRunning = false;
                }
                Log.v("IMU publisher", ""+isChecked);
            }
        });
    }

    public void onSwitchCameraButtonClicked(View view) {

        final Toast toast;
                if(!cameraRunning)
                {
                    toast = Toast.makeText(this, "Camera Publisher not running", Toast.LENGTH_SHORT);
                }
    else {
                    int numberOfCameras = Camera.getNumberOfCameras();

                    if (numberOfCameras > 1) {
                        cameraId = (cameraId + 1) % numberOfCameras;
                        rosCameraPreviewView.releaseCamera();
                        rosCameraPreviewView.setCamera(getCamera());
                        toast = Toast.makeText(this, "Switching cameras.", Toast.LENGTH_SHORT);
                    } else {
                        toast = Toast.makeText(this, "No alternative cameras to switch to.", Toast.LENGTH_SHORT);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast.show();
                    }
                });
    }

    @Override @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) //API = 15
    protected void init(NodeMainExecutor nodeMainExecutor) {
        sID = MasterChooser.sID;
        rosCameraPreviewView = new RosCameraPreviewView(sID);
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        this.nodeMainExecutor = nodeMainExecutor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] PERMISSIONS = {"", "", "", ""};
            PERMISSIONS[0] = Manifest.permission.ACCESS_FINE_LOCATION;
            PERMISSIONS[1] = Manifest.permission.CAMERA;
            PERMISSIONS[2] = Manifest.permission.READ_EXTERNAL_STORAGE;
            PERMISSIONS[3] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
        }else {
            initGPS();
            initCamera();
        }
        initImu();

    }

    private void initGPS() {
        this.fix_pub = new NavSatFixPublisher(mLocationManager, sID);
        nodeConfigurationGPS = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfigurationGPS.setMasterUri(getMasterUri());
        nodeConfigurationGPS.setNodeName(fix_pub.getDefaultNodeName());
    }

    private void initCamera() {
        rosCameraPreviewView.setCamera(getCamera());
        nodeConfigurationCamera = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfigurationCamera.setMasterUri(getMasterUri());
        nodeConfigurationCamera.setNodeName(rosCameraPreviewView.getDefaultNodeName());
    }

    private void initImu() {
        nodeConfigurationImu = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfigurationImu.setMasterUri(getMasterUri());
        this.imu_pub = new ImuPublisher(mSensorManager, sID);
        nodeConfigurationImu.setNodeName(imu_pub.getDefaultNodeName());
    }

    private Camera getCamera() {
        Camera cam = Camera.open(cameraId);
        Camera.Parameters camParams = cam.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (camParams.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_INFINITY)) {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            }
        }
        //cam.setParameters(camParams);
        return cam;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                initGPS();
            }
            if (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                initCamera();
            }
        }
    }

    public void onRadioButtonClicked(View view) {

        boolean checked = ((RadioButton) view).isChecked();

        switch(view.getId()) {

            case R.id.res480:
                if (checked)
                    rosCameraPreviewView.setPreviewSize(640, 480);
                    break;

            case R.id.res720:
                if (checked)
                    rosCameraPreviewView.setPreviewSize(1280, 720);
                break;

            case R.id.res1080:
                if (checked)
                    rosCameraPreviewView.setPreviewSize(1920, 1080);
                    break;
        }

        if(cameraRunning){
            rosCameraPreviewView.releaseCamera();
            rosCameraPreviewView.setCamera(getCamera());
        }
    }

}
