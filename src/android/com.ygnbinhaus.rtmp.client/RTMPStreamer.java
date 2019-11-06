package com.ygnbinhaus.rtmp.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import org.apache.cordova.CordovaActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class RTMPStreamer extends CordovaActivity implements ConnectCheckerRtmp {
    // 90, 180, 270 or 0
    private final String[] _orient = new String[]{"90", "180", "270", "0"};
    SurfaceView surfaceView;
    String currentDateAndTime;
    PowerManager.WakeLock mWakeLock;
    private RtmpCamera1 rtmpCameral;
    private Activity activity;
    private File folder = null;
    private String _url = null;
    private String _token = null;
    private ImageButton ic_torch;
    private ImageButton ic_switch_camera;
    private ImageButton ic_broadcast;
    private ImageButton ic_closed;
    private Camera1ApiManager camera1ApiManager;
    private boolean isFlashOn = false;

    private java.util.List<android.hardware.Camera.Size> resolutions;
    private int selectedWidth = 0;
    private int selectedHeight = 0;
    BroadcastReceiver br = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String method = intent.getStringExtra("method");

                if (method != null) {
                    if (method == "stop") _stopStreaming();
                    /*switch (method) {
                        case "stop":
                            _stopStreaming();
                            break;
                    }*/
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this.cordovaInterface.getActivity();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(_getResource("rtsp_rtmp_streamer", "layout"));

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, TAG);
            mWakeLock.acquire();
        }

        Intent intent = getIntent();
        _url = intent.getStringExtra("url");
        _token = intent.getStringExtra("token");

        _UIListener();
        _commentContainer(false);
        _broadcastRCV();
    }

    @Override
    public void onPause() {
        super.onPause();
        _stopStreaming();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activity.unregisterReceiver(br);
        _stopStreaming();

        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    public void onConnectionSuccessRtmp() {
        VideoStream.sendBroadCast(activity, "onConnectionSuccess");
        runOnUiThread(() -> Toast.makeText(RTMPStreamer.this, "Connection success", Toast.LENGTH_SHORT)
                .show());
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        VideoStream.sendBroadCast(activity, "onConnectionFailed");
        runOnUiThread(() -> {
            Toast.makeText(RTMPStreamer.this, "Connection failed. " + reason,
                    Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onDisconnectRtmp() {
        VideoStream.sendBroadCast(activity, "onDisconnect");
        runOnUiThread(() -> {
            Toast.makeText(RTMPStreamer.this, "Disconnected", Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onAuthErrorRtmp() {
        VideoStream.sendBroadCast(activity, "onAuthError");
        runOnUiThread(() -> {
            Toast.makeText(RTMPStreamer.this, "Auth error", Toast.LENGTH_SHORT).show();
            _stopStreaming();
        });
    }

    @Override
    public void onAuthSuccessRtmp() {
        VideoStream.sendBroadCast(activity, "onAuthSuccess");
        runOnUiThread(() -> Toast.makeText(RTMPStreamer.this, "Auth success", Toast.LENGTH_SHORT).show());
    }

    private void _broadcastRCV() {
        IntentFilter filter = new IntentFilter(RTMPClient.BROADCAST_FILTER);
        activity.registerReceiver(br, filter);
    }

    private void _UIListener() {
        surfaceView = findViewById(_getResource("rtmp_rtsp_stream_surfaceView", "id"));
        rtmpCameral = new RtmpCamera1(surfaceView, this);
        camera1ApiManager = new Camera1ApiManager(surfaceView, rtmpCameral);

        ic_torch = findViewById(_getResource("ic_torch", "id"));
        ic_torch.setOnClickListener(v -> _toggleFlash());

        ic_switch_camera = findViewById(_getResource("ic_switch_camera", "id"));
        ic_switch_camera.setOnClickListener(v -> _toggleCameraFace());

        ic_broadcast = findViewById(_getResource("ic_broadcast", "id"));
        ic_broadcast.setOnClickListener(v -> _toggleStreaming());

        ic_closed = findViewById(_getResource("ic_closed", "id"));
        ic_closed.setOnClickListener(v -> _closedActivity());
    }

    private void _toggleStreaming() {
        if (!rtmpCameral.isStreaming()) {
            _startStreaming();
        } else {
            _stopStreaming();
        }
    }

    private void _startStreaming() {
        /*int _w = 0;
        int _h = 0;

        this.resolutions = rtmpCameral.getResolutionsBack();
        for (int i = 0; i < this.resolutions.size(); i++) {
            Log.i(TAG, "RES: H: " + this.resolutions.get(i).height + " W: " + this.resolutions.get(i).width);

            // get the recommended resolution
            if (this.resolutions.get(i).width >= 720 && _w == 0) {
                _w = this.resolutions.get(i).width;
                _h = this.resolutions.get(i).height;
            }
        }

        if (_w == 0 && _h == 0) {
            _w = (this.selectedWidth == 0) ? this.resolutions.get(0).width : this.selectedWidth;
            _h = (this.selectedHeight == 0) ? this.resolutions.get(0).height : this.selectedHeight;
        }

        // video helper
        VideoHelper videoH = new VideoHelper();
        Log.d(TAG, "Res: " + _w + "x" + _h + " bitrate " + videoH.bitrate(_w, _h));
        */
        if (rtmpCameral.prepareAudio() && rtmpCameral.prepareVideo(640, 480, 24, 2500*1024,
                false, 0, 90)) {
            rtmpCameral.startStream(_url+"/"+_token);
            _toggleBtnImgVideo(true);

            VideoStream.sendBroadCast(activity, "onStartStream");
        } else {
            Toast.makeText(activity, "Error preparing stream, This device cant do it.", Toast.LENGTH_SHORT)
                    .show();
            _toggleBtnImgVideo(false);

            JSONObject obj = new JSONObject();
            try {
                obj.put("message", "Error preparing stream, This device cant do it.");
                VideoStream.sendBroadCast(activity, "onError", obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void _stopStreaming() {
        VideoStream.sendBroadCast(activity, "onStopStream");

        if (rtmpCameral.isStreaming()) {

            rtmpCameral.stopStream();
            rtmpCameral.stopPreview();
            _toggleBtnImgVideo(false);

            // close the flash
            if (camera1ApiManager.isLanternEnabled()) {
                camera1ApiManager.disableLantern();

                isFlashOn = false;
                _toggleBtnImgFlash();
            }
        }
    }

    private void _toggleFlash() {
        if (!isFlashOn && !camera1ApiManager.isLanternEnabled()) {
            try {
                camera1ApiManager.enableLantern();
                isFlashOn = true;
            } catch (Exception e) {
                e.printStackTrace();
                isFlashOn = false;
            }
        } else {
            camera1ApiManager.disableLantern();
            isFlashOn = false;
        }

        // changing button/switch image
        _toggleBtnImgFlash();
    }

    private void _toggleBtnImgFlash() {
        String icon = (!isFlashOn) ? "ic_flash_on_white_36dp" : "ic_flash_off_white_36dp";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ic_torch.setImageDrawable(getDrawable(_getResource(icon, "drawable")));
        } else {
            ic_torch.setImageResource(_getResource(icon, "drawable"));
        }
    }

    private void _toggleBtnImgVideo(boolean isStreamingOn) {
        String icon = (!isStreamingOn) ? "ic_videocam_white_36dp" : "ic_videocam_off_white_36dp";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ic_broadcast.setImageDrawable(getDrawable(_getResource(icon, "drawable")));
        } else {
            ic_broadcast.setImageResource(_getResource(icon, "drawable"));
        }
    }

    private void _toggleCameraFace() {
        try {
            rtmpCameral.switchCamera();
        } catch (CameraOpenException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            rtmpCameral.switchCamera();
        }
    }

    private void _closedActivity() {
        this._stopStreaming();
        finish();
    }

    private int _getResource(String name, String type) {
        String package_name = getApplication().getPackageName();
        Resources resources = getApplication().getResources();
        return resources.getIdentifier(name, type, package_name);
    }
}