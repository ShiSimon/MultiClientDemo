package mediaengine.fritt.multiclientdemo;

import android.util.Log;

import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.MediaEngineClient;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;
import mediaengine.fritt.mediaengine.StatesReporter;

public class MCEvents implements MediaEngineClient.MediaConnectionEvents {
    private static final String TAG="MCEvents";
    private String key;
    private MainActivity activity;

    public MCEvents(String key,MainActivity activity){
        this.key = key;
        this.activity = activity;
    }
    @Override
    public void onLocalDescription(SessionDescriptionInfo sessionDescriptionInfo) {
        Log.d(TAG,"onLocalDescription");
        activity.onLocalDescription(key,sessionDescriptionInfo);
    }

    @Override
    public void onIceCandidate(IceCandidateInfo iceCandidateInfo) {
        Log.d(TAG,"onIceCandidate");
        activity.onIceCandidate(key,iceCandidateInfo);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidateInfo[] iceCandidateInfos) {

    }

    @Override
    public void onIceConnected() {
        activity.onIceConnected();
    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatesReporter[] statesReporters) {

    }

    @Override
    public void onPeerConnectionError(String s) {

    }
}
