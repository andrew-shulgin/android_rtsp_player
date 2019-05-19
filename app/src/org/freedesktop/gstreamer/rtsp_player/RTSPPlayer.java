package org.freedesktop.gstreamer.rtsp_player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import org.freedesktop.gstreamer.GStreamer;

public class RTSPPlayer extends Activity implements SurfaceHolder.Callback {
    private native void nativeInit(String launchCmd);     // Initialize native code, build pipeline, etc

    private native void nativeFinalize(); // Destroy pipeline and shutdown native code

    private native void nativePlay();     // Set pipeline to PLAYING

    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks

    private native void nativeSurfaceInit(Object surface);

    private native void nativeSurfaceFinalize();

    private long native_custom_data;      // Native code will use this to keep private data

    private Uri rtspUri = null;

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        Intent intent = getIntent();
        Uri originalUri = intent.getData();
        Uri.Builder builder = new Uri.Builder();
        if (originalUri == null)
            return;
        assert originalUri.getScheme() != null;
        builder.scheme(originalUri.getScheme().equals("rtsp") ? "rtspt" : originalUri.getScheme())
                .encodedAuthority(originalUri.getAuthority())
                .path(originalUri.getPath())
                .query(originalUri.getQuery());
        rtspUri = builder.build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (rtspUri == null)
            return;
        String launchCmd = String.format(
                "rtspsrc location=%s latency=300 ! decodebin ! videoconvert ! autovideosink", rtspUri
        );
        Log.i("RTSP Player", launchCmd);
        nativeInit(launchCmd);
    }

    @Override
    protected void onPause() {
        nativeFinalize();
        super.onPause();
    }

    protected void onDestroy() {
        nativeFinalize();
        super.onDestroy();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                Log.i("RTSP Player", message);
            }
        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized() {
        nativePlay();
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("rtsp-player");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit(holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize();
    }

}
