package mediaengine.fritt.mediaengine;

import android.util.Log;

import org.webrtc.VideoRenderer;

public class ProxyRenderer implements myCallbacks {
    public static final String TAG = "ProxyRenderer";
    private VideoRenderer.Callbacks target;

    synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
        //Log.d(TAG,"renderFrame");
        if (target == null) {
            VideoRenderer.renderFrameDone(frame);
            return;
        }

        target.renderFrame(frame);
    }

    synchronized public void setTarget(VideoRenderer.Callbacks target) {
        this.target = target;
    }
}