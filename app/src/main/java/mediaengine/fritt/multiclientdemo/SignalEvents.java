package mediaengine.fritt.multiclientdemo;

import android.util.Log;

import mediaengine.fritt.mediaengine.ClientInterface;
import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.MediaEngineClient;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;

public class SignalEvents implements ClientInterface.SignalingEvents {
    private static String TAG = "SignalEvent";

    private MainActivity activity;
    public SignalEvents(MainActivity activity,String key){
        this.key = key;
        this.activity = activity;
    }
    private String key;

    @Override
    public void onCreateSubscriber() {
        activity.onCreateSubscriber(key);
    }

    @Override
    public void onCreateConnectToServer() {
        Log.d(TAG,"onCreateConnectedToServer");
        //activity.SendAttach(key);
    }

    @Override
    public void onShowFiles(String s) {

    }

    @Override
    public void onConnectedToRoom(ClientInterface.SignalingParameters signalingParameters) {
        Log.d(TAG,"onConnectedToRoom");
        activity.onConnectedToRoom(key,signalingParameters);
    }

    @Override
    public void onConnectedToCall() {

    }

    @Override
    public void onConnectedToAnswer(SessionDescriptionInfo sdp) {
        Log.d(TAG,"onConnectedToAnswer");
        activity.onConnectedToAnswer(key,sdp);
    }

    @Override
    public void onRemoteDescription(SessionDescriptionInfo sessionDescriptionInfo) {
        Log.d(TAG,"onRemoteDescription");
        activity.onRemoteDescription(key,sessionDescriptionInfo);

    }

    @Override
    public void onRemoteIceCandidate(IceCandidateInfo iceCandidateInfo) {
        Log.d(TAG,"onRemoteIceCandidate");


    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidateInfo[] iceCandidateInfos) {

    }

    @Override
    public void onChannelClose() {
        Log.d(TAG,"onChannelClose");
        activity.onChannelClose(key);
    }

    @Override
    public void onChannelError(String s) {

    }
}
