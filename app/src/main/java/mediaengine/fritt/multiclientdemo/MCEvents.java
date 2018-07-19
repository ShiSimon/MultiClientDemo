package mediaengine.fritt.multiclientdemo;

import android.util.Log;

import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.MediaConnectionEvents;
import mediaengine.fritt.mediaengine.MediaEngineClient;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;
import mediaengine.fritt.mediaengine.StatesReporter;

public class MCEvents implements MediaConnectionEvents {
    private static final String TAG="MCEvents";
    //private String key;
    private MainActivity activity;

    public MCEvents(MainActivity activity){
        this.activity = activity;
    }

    @Override
    public void onLocalDescription(SessionDescriptionInfo sessionDescriptionInfo, MediaEngineClient.MediaConnectionNode mediaConnectionNode) {
        Log.d(TAG,"onLocalDescription");
        activity.onLocalDescription(sessionDescriptionInfo,mediaConnectionNode);
    }

    @Override
    public void onIceCandidate(IceCandidateInfo iceCandidateInfo, MediaEngineClient.MediaConnectionNode mediaConnectionNode) {
        Log.d(TAG,"onIceCandidate");
        activity.onIceCandidate(iceCandidateInfo,mediaConnectionNode);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidateInfo[] iceCandidateInfos, MediaEngineClient.MediaConnectionNode mediaConnectionNode) {

    }

    @Override
    public void onIceConnected(MediaEngineClient.MediaConnectionNode mediaConnectionNode) {
        activity.onIceConnected(mediaConnectionNode);
    }

    @Override
    public void onIceDisconnected(MediaEngineClient.MediaConnectionNode mediaConnectionNode) {

    }

    @Override
    public void onMediaConnectionClosed(MediaEngineClient.MediaConnectionNode mediaConnectionNode) {

    }

    @Override
    public void onMediaConnectionStatsReady(StatesReporter[] statesReporters, MediaEngineClient.MediaConnectionNode mediaConnectionNode) {

    }

    @Override
    public void onMediaConnectionError(String s, MediaEngineClient.MediaConnectionNode mediaConnectionNode) {

    }
}
