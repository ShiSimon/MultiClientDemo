package mediaengine.fritt.multiclientdemo;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import mediaengine.fritt.mediaengine.AppRTCAudioManager;
import mediaengine.fritt.mediaengine.ClientInterface;
import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.IceServerInfo;
import mediaengine.fritt.mediaengine.MEglBase;
import mediaengine.fritt.mediaengine.MediaEngineClient;
import mediaengine.fritt.mediaengine.MediaEngineRenderer;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;
import mediaengine.fritt.mediaengine.StatesReporter;
import mediaengine.fritt.mediaengine.SurfaceViewRender;

public class MainActivity extends Activity{
    private static final String TAG = "MainActivity";
    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};
    private SurfaceViewRender fullscreenRender;
    private SurfaceViewRender pipRenderer;
    private SurfaceViewRender showRenderer;
    private MediaEngineClient.MediaEngineParameters mediaEngineParameters;

    private AppRTCAudioManager audioManager;

    private Map<String,MediaEngineClient> clientMap;
    private ClientInterface clientInterface;
    private MediaEngineRenderer mediaEngineRenderer;
    private MediaEngineRenderer showEngineRenderer;
    private MEglBase mEglBase;
    private Button start_button;
    private Button play_button;
    private Button record_button;
    private Button stop_button;
    private Button disconnect_button;
    private WebSocketClient wsc;

    private Map<String,SignalEvents> signalingEventsMap;
    private Map<String,MediaEngineClient.MediaConnectionEvents> MCEventsMap;
    private boolean isError;
    private Toast logToast;
    private boolean isSwappedFeeds = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        signalingEventsMap = new HashMap<>();
        clientMap = new HashMap<>();
        MCEventsMap = new HashMap<>();
        pipRenderer = (SurfaceViewRender) findViewById(R.id.pip_video_view);
        fullscreenRender = (SurfaceViewRender) findViewById(R.id.full_screen_render);
        showRenderer = (SurfaceViewRender) findViewById(R.id.show_video_view);
        start_button = (Button) findViewById(R.id.start);
        play_button = (Button) findViewById(R.id.play);
        record_button = (Button) findViewById(R.id.record);
        stop_button = (Button) findViewById(R.id.stop);
        disconnect_button = (Button) findViewById(R.id.hangup);

        mEglBase = new MEglBase();
        pipRenderer.init(mEglBase);
        pipRenderer.setScaleType("SCALE_ASPECT_FIT");

        fullscreenRender.init(mEglBase);
        fullscreenRender.setScaleType("SCALE_ASPECT_FILL");
        pipRenderer.setZOrderMediaOverlay(true);
        pipRenderer.setEnableHardwareScaler(true /* enabled */);
        fullscreenRender.setEnableHardwareScaler(true);

        showRenderer.init(mEglBase);
        showRenderer.setScaleType("SCALE_ASPECT_FIT");
        showRenderer.setZOrderMediaOverlay(true);
        showRenderer.setEnableHardwareScaler(true);


        mediaEngineRenderer = new MediaEngineRenderer(fullscreenRender, pipRenderer);
        mediaEngineRenderer.setRenderer(isSwappedFeeds);

        showEngineRenderer = new MediaEngineRenderer(fullscreenRender, showRenderer);
        showEngineRenderer.setRenderer(isSwappedFeeds);

        // Swap feeds on pip view click.
        pipRenderer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSwappedFeeds(!isSwappedFeeds);
            }
        });

        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCall();
            }
        });
        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        record_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecord();
            }
        });

        play_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPlay();
            }
        });

        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecord();
            }
        });

        disconnect_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnectFromEcho();
            }
        });

        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
                false, 640, 480, 0, 0, "VP8",
                true, false, true,0, "OPUS",
                false, false, false, false, false,
                false, false, false);

        wsc = new WebSocketClient();
        wsc.connectToServer("10.0.1.116",6660);
        clientInterface = wsc;
    }

    private void stopRecord(){
        Log.d(TAG,"Stop Record");
        clientInterface.stopRecord();
    }

    private void startRecord(){
        Log.d(TAG,"startRecord");
        MediaEngineClient client = new MediaEngineClient();
        MediaEngineClient.MediaConnectionEvents mcEvents = new MCEvents("record-service",this);
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(false, false,
                false, 640, 480, 0, 0, "VP8",
                true, false, true,0, "OPUS",
                false, false, false, false, false,
                false, false, false);
        client.createMediaConnectionFactory(getApplicationContext(), mediaEngineParameters, mcEvents);
        Log.d(TAG,"After create　Factory");
        if(audioManager == null){
            audioManager = AppRTCAudioManager.create(getApplicationContext());
            Log.d(TAG, "Starting the audio manager...");
            audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
                // This method will be called each time the number of available audio
                // devices has changed.
                @Override
                public void onAudioDeviceChanged(
                        AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                    onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
                }
            });
        }
        MCEventsMap.put("record-service",mcEvents);
        clientMap.put("record-service",client);
        SignalEvents se = new SignalEvents(this,"record-service");
        wsc.registerEvent(se,"record-service");
        //clientInterface.startService("record-service");
        SendAttach("record-service");
    }

    private void startCall(){
        Log.d(TAG,"startCall");
        MediaEngineClient client = new MediaEngineClient();
        MediaEngineClient.MediaConnectionEvents mcEvents = new MCEvents("echo-service",this);
        client.createMediaConnectionFactory(getApplicationContext(), mediaEngineParameters, mcEvents);
        Log.d(TAG,"After create　Factory");
        if(audioManager == null){
            audioManager = AppRTCAudioManager.create(getApplicationContext());
            Log.d(TAG, "Starting the audio manager...");
            audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
        }
        MCEventsMap.put("echo-service",mcEvents);
        clientMap.put("echo-service",client);
        SignalEvents se = new SignalEvents(this,"echo-service");
        wsc.registerEvent(se,"echo-service");
        SendAttach("echo-service");
    }

    private void startPlay(){
        Log.d(TAG,"startPlay");
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
                false, 640, 480, 0, 0, "VP8",
                true, false, false,0, "OPUS",
                false, false, false, false, false,
                false, false, false);
        MediaEngineClient client = new MediaEngineClient();
        MediaEngineClient.MediaConnectionEvents mEvents = new MCEvents("play-service",this);
        client.createMediaConnectionFactory(getApplicationContext(),mediaEngineParameters,mEvents);
        if(audioManager == null){
            audioManager = AppRTCAudioManager.create(getApplicationContext());
            Log.d(TAG, "Starting the audio manager...");
            audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
                // This method will be called each time the number of available audio
                // devices has changed.
                @Override
                public void onAudioDeviceChanged(
                        AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                    onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
                }
            });
        }
        MCEventsMap.put("play-service",mEvents);
        clientMap.put("play-service",client);
        SignalEvents se = new SignalEvents(this,"play-service");
        wsc.registerEvent(se,"play-service");
        //clientInterface.startService("play-service");
        SendAttach("play-service");
    }

    private void disconnectFromEcho(){
        wsc.sendHangup();
    }

    private void disconnect(){
        mediaEngineRenderer.release();
        showEngineRenderer.release();
        for(Object key:clientMap.keySet()){
            clientInterface.disconnectChannel(key.toString());
            clientMap.get(key).close();
        }
        clientMap.clear();
        wsc.disconnectWSC();
        if (pipRenderer != null) {
            pipRenderer.release();
            pipRenderer = null;
        }
        if (fullscreenRender != null) {
            fullscreenRender.release();
            fullscreenRender = null;
        }
        if(showRenderer != null){
            showRenderer.release();
            showRenderer = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        finish();
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    public void SendAttach(String key){
        if(key.startsWith("echo")){
            wsc.sendAttach(key,"echo-kXEcj2JFjoUF","service.echo","ECHO");
        }else if(key.startsWith("record")){
            wsc.sendAttach(key,"recordplaytest-U2Md9D08sA3L","service.recordplay","RECORD");
        }else if(key.startsWith("play")){
            wsc.sendAttach(key,"recordplaytest-U2Md9D08sA3L","service.recordplay","PLAY");
        }
    }

    public void onConnectedToAnswer(final String key,final SessionDescriptionInfo sdp){
        final ClientInterface.SignalingParameters params = new ClientInterface.SignalingParameters(new LinkedList<IceServerInfo>(), false,
                null, "ws://10.0.1.116:6660",null,null,null);
        Log.d(TAG, "onConnectedToRoomInternal,get response from server");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToAnswerInternal(key,params,sdp);
            }
        });
    }

    private void onConnectedToAnswerInternal(final String key,final ClientInterface.SignalingParameters params,
                                             final SessionDescriptionInfo sdp) {
        Log.d(TAG, "onConnectedToAnswerInternal");

        Log.d(TAG, "Create PC");

        params.offerSdp = sdp;
        clientMap.get(key).createMediaConnection(mEglBase, showEngineRenderer, params);

        clientMap.get(key).setRemoteDescription(sdp);
        Log.d(TAG, "I am receiver");
        logAndToast("Creating ANSWER...");
        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        clientMap.get(key).createAnswer();
        logAndToast("Create ANSWER Over");
    }


    public void onConnectedToRoom(final String key, final ClientInterface.SignalingParameters params){
        Log.d(TAG, "onConnectedToRoomInternal,get response from server");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(key,params);
            }
        });
    }

    private void onConnectedToRoomInternal(final String key,final ClientInterface.SignalingParameters params) {
        Log.d(TAG, "onConnectedToRoomInternal");

        Log.d(TAG, "Create PC");
        clientMap.get(key).createMediaConnection(mEglBase, mediaEngineRenderer, params);


        Log.d(TAG, "I am caller");
        logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
        clientMap.get(key).createOffer();

    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    //disconnectWithErrorMessage(description);
                }
            }
        });
    }

    public void onLocalDescription(final String key,final SessionDescriptionInfo sdp){
        Log.d(TAG, "onLocalDescription");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(key.equals("echo-service") || key.equals("record-service")){
                    clientInterface.sendOfferSdp(key,sdp);
                }else if(key.equals("play-service")){
                    clientInterface.sendAnswerSdp(key,sdp);
                }
                if (mediaEngineParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: " + mediaEngineParameters.videoMaxBitrate);
                    clientMap.get(key).setVideoMaxBitrate(mediaEngineParameters.videoMaxBitrate);
                }
            }
        });
    }

    public void onIceCandidate(final String key,final IceCandidateInfo candidate) {
        Log.d(TAG,"onIceCandidate");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (clientInterface != null) {
                    clientInterface.sendLocalIceCandidate(key,candidate);
                }
            }
        });
    }


    public void onRemoteDescription(final String key,final SessionDescriptionInfo sdp) {
        Log.d(TAG,"onRemoteDescription");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clientMap.get(key).setRemoteDescription(sdp);

                //clientMap.get(key).createAnswer();

                Log.d(TAG, "Set ANSWER over");
            }
        });
    }

    public void onIceConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected");
                //iceConnected = true;
                callConnected();
            }
        });
    }

    public void onChannelClose(final String key){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"close channel: " + key);
                MediaEngineClient client = clientMap.get(key);
                client.close();
                clientMap.remove(key);
                MCEventsMap.remove(key);
                signalingEventsMap.remove(key);
                fullscreenRender.clearImage();
                pipRenderer.clearImage();
                showRenderer.clearImage();
                showRenderer.setZOrderMediaOverlay(false);
                audioManager.stop();
                audioManager = null;
            }
        });
    }

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        Log.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
        this.isSwappedFeeds = isSwappedFeeds;
        mediaEngineRenderer.setRenderer(isSwappedFeeds);
    }


    // Should be called from UI thread
    private void callConnected() {
        Log.i(TAG, "Call connected:");
        // Enable statistics callback.
        //mediaEngineClient.enableStatsEvents(true, 1000);
        setSwappedFeeds(false /* isSwappedFeeds */);
    }



    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        disconnect();
        if(logToast != null){
            logToast.cancel();
        }
        mEglBase.release();
        super.onDestroy();
    }
}
