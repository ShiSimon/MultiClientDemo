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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import mediaengine.fritt.mediaengine.AppRTCAudioManager;
import mediaengine.fritt.mediaengine.ClientInterface;
import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.IceServerInfo;
import mediaengine.fritt.mediaengine.MEglBase;
import mediaengine.fritt.mediaengine.MediaConnectionEvents;
import mediaengine.fritt.mediaengine.MediaEngineClient;
import mediaengine.fritt.mediaengine.MediaEngineFactory;
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

    //private Map<String,MediaEngineClient> clientMap;
    private ClientInterface clientInterface;
    private MediaEngineRenderer mediaEngineRenderer;
    private MediaEngineRenderer showEngineRenderer;
    private MEglBase mEglBase;
    private Button create_meet_button;
    private Button join_meet_button;
    private Button leave_meet_button;
    private Button start_button;
    private Button play_button;
    private Button record_button;
    private Button stop_button;
    private Button disconnect_button;
    private WebSocketClient wsc;

    private Map<String,SignalEvents> signalingEventsMap;
    //private Map<String,MediaConnectionEvents> MCEventsMap;
    private MCEvents mcEvent;
    private boolean isError;
    private Toast logToast;
    private boolean isSwappedFeeds = true;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private MediaEngineClient mediaEngineClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));
        setContentView(R.layout.activity_main);

        signalingEventsMap = new HashMap<>();
        //clientMap = new HashMap<>();
        //MCEventsMap = new HashMap<>();
        pipRenderer = (SurfaceViewRender) findViewById(R.id.pip_video_view);
        fullscreenRender = (SurfaceViewRender) findViewById(R.id.full_screen_render);
        showRenderer = (SurfaceViewRender) findViewById(R.id.show_video_view);
        create_meet_button = (Button) findViewById(R.id.conference);
        join_meet_button = (Button) findViewById(R.id.join);
        start_button = (Button) findViewById(R.id.start);
        leave_meet_button = (Button) findViewById(R.id.leave);
        play_button = (Button) findViewById(R.id.play);
        record_button = (Button) findViewById(R.id.record);
        stop_button = (Button) findViewById(R.id.stop);
        disconnect_button = (Button) findViewById(R.id.hangup);

        mEglBase = new MEglBase();
        pipRenderer.init(mEglBase);
        pipRenderer.setScaleType("SCALE_ASPECT_FIT");

        fullscreenRender.init(mEglBase);
        fullscreenRender.setScaleType("SCALE_ASPECT_FIT");
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

        create_meet_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                create_meet();
            }
        });

        join_meet_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                join_meet();
            }
        });

        leave_meet_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                leave_meet();
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
        mediaEngineClient = new MediaEngineClient(executor);
        mcEvent = new MCEvents(this);
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
                false, 640, 480, 0, 0, "VP8",
                true, false, true,0, "OPUS",
                false, false, false, false, false,
                false, false, false);
        mediaEngineClient.createMediaConnectionFactory(getApplicationContext(),mediaEngineParameters,mEglBase,mcEvent,fullscreenRender);

        //MCEventsMap.put("echo-service",mcEvents);
    }

    private void create_meet(){
        /*Log.d(TAG,"create_meet");
        MediaEngineClient client = new MediaEngineClient();
        MediaConnectionEvents mcEvents = new MCEvents("conference-service",this);
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
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
        MCEventsMap.put("conference-service",mcEvents);
        clientMap.put("conference-service",client);
        SignalEvents se = new SignalEvents(this,"conference-service");
        wsc.registerEvent(se,"conference-service");
        SendAttach("conference-service",1);*/
    }

    private void join_meet(){
        /*Log.d(TAG,"join_meet");
        MediaEngineClient client = new MediaEngineClient();
        MediaConnectionEvents mcEvents = new MCEvents("conference-service-pub",this);
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
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
        MCEventsMap.put("conference-service-pub",mcEvents);
        clientMap.put("conference-service-pub",client);
        SignalEvents se = new SignalEvents(this,"conference-service-pub");
        wsc.registerEvent(se,"conference-service-pub");
        SendAttach("conference-service-pub",2);*/
    }

    private void leave_meet(){
        Log.d(TAG,"leave meet");
        wsc.leaveMeet();
    }

    private void stopRecord(){
        Log.d(TAG,"Stop Record");
        clientInterface.stopRecord();
    }

    private void startRecord(){
        /*Log.d(TAG,"startRecord");
        MediaEngineClient client = new MediaEngineClient();
        MediaConnectionEvents mcEvents = new MCEvents("record-service",this);
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
        SendAttach("record-service",0);*/
    }

    private void startCall(){
        Log.d(TAG,"startCall");
        //MediaEngineClient client = new MediaEngineClient();
        //MediaConnectionEvents mcEvents = new MCEvents("echo-service",this);
        //client.createMediaConnectionFactory(getApplicationContext(), mediaEngineParameters, mcEvents);
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
        SignalEvents se = new SignalEvents(this,"echo-service");
        wsc.registerEvent(se,"echo-service");
        SendAttach("echo-service",0);
    }

    private void startPlay(){
        /*Log.d(TAG,"startPlay");
        mediaEngineParameters = new MediaEngineClient.MediaEngineParameters(true, false,
                false, 640, 480, 0, 0, "VP8",
                true, false, false,0, "OPUS",
                false, false, false, false, false,
                false, false, false);
        MediaEngineClient client = new MediaEngineClient();
        MediaConnectionEvents mEvents = new MCEvents("play-service",this);
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
        SendAttach("play-service",0);*/

        Log.d(TAG,"startPlay");
        SignalEvents se = new SignalEvents(this,"play-service");
        wsc.registerEvent(se,"play-service");
        SendAttach("play-service",0);
    }

    private void disconnectFromEcho(){
        wsc.sendHangup();
    }

    public void disconnect(){
        mediaEngineRenderer.release();
        showEngineRenderer.release();
        /*for(Object key:clientMap.keySet()){
            clientInterface.disconnectChannel(key.toString());
            clientMap.get(key).close();
        }
        clientMap.clear();*/
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

    public void SendAttach(String key,int id){
        if(key.startsWith("echo")){
            wsc.sendAttach(key,"echo-kXEcj2JFjoUF","service.echo","ECHO");
        }else if(key.startsWith("record")){
            wsc.sendAttach(key,"recordplaytest-U2Md9D08sA3L","service.recordplay","RECORD");
        }else if(key.startsWith("play")){
            wsc.sendAttach(key,"recordplaytest-U2Md9D08sA3L","service.recordplay","PLAY");
        }else if(key.startsWith("conference")){
            if(key.equals("conference-service-pub")){
                wsc.sendAttach(key,"conferencetest-A63K9WhtAMRb","service.conference","CONFERENCE-PUB");
            }else if(key.equals("conference-service-sub") && id == 2){
                wsc.sendAttach(key,"conferencetest-A63K9WhtAMRb","service.conference","CONFERENCE-SUB");
            }else if(key.equals("conference-service-sub") && id == 1){
                wsc.sendAttach(key,"videoroomtest-9pPNCS0Z4HY2","service.conference","CONFERENCE-SUB");
            }
            else{
                wsc.sendAttach(key,"videoroomtest-9pPNCS0Z4HY2","service.conference","CONFERENCE");
            }
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
        mediaEngineClient.createMediaConnection(mEglBase, showEngineRenderer, params,key);

        mediaEngineClient.setRemoteDescription(sdp,key);
        Log.d(TAG, "I am receiver");
        logAndToast("Creating ANSWER...");
        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        mediaEngineClient.createAnswer(key);
        logAndToast("Create ANSWER Over");
    }

    public void onCreateSubscriber(final String key){
        /*Log.d(TAG,"onCreateSubscriber");
        MediaEngineClient client = new MediaEngineClient();
        MediaConnectionEvents mcEvents = new MCEvents("conference-service-sub",this);
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
        MCEventsMap.put("conference-service-sub",mcEvents);
        clientMap.put("conference-service-sub",client);
        SignalEvents se = new SignalEvents(this,"conference-service-sub");
        wsc.registerEvent(se,"conference-service-sub");
        if(key.equals("conference-service")){
            SendAttach("conference-service-sub",1);
        }else{
            SendAttach("conference-service-sub",2);
        }*/
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
        Log.d(TAG, "onConnectedToRoomInternal,key = " + key);

        Log.d(TAG, "Create PC");
        mediaEngineClient.createMediaConnection(mEglBase,mediaEngineRenderer,params,key);


        Log.d(TAG, "I am caller");
        logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
        mediaEngineClient.createOffer(key);
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

    public void onLocalDescription(final SessionDescriptionInfo sdp, final MediaEngineClient.MediaConnectionNode mediaConnectionNode){
        Log.d(TAG, "onLocalDescription");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String key = mediaConnectionNode.key;
                if(key.equals("echo-service") || key.equals("record-service") || key.equals("conference-service-pub")
                        ||key.equals("conference-service")){
                    clientInterface.sendOfferSdp(key,sdp);
                }else if(key.equals("play-service")||key.equals("conference-service-sub")){
                    clientInterface.sendAnswerSdp(key,sdp);
                }
                if (mediaEngineParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: " + mediaEngineParameters.videoMaxBitrate);
                    mediaEngineClient.setVideoMaxBitrate(mediaEngineParameters.videoMaxBitrate,mediaConnectionNode);
                }
            }
        });
    }

    public void onIceCandidate(final IceCandidateInfo candidate, final MediaEngineClient.MediaConnectionNode mediaConnectionNode) {
        Log.d(TAG,"onIceCandidate");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String key = mediaConnectionNode.key;
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
                mediaEngineClient.setRemoteDescription(sdp,key);

                //clientMap.get(key).createAnswer();

                Log.d(TAG, "Set ANSWER over");
            }
        });
    }

    public void onIceConnected(final MediaEngineClient.MediaConnectionNode mediaConnectionNode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected: " + mediaConnectionNode.key);
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
                if(mediaEngineClient == null){

                }else{
                    MediaEngineClient.MediaConnectionNode mediaConnectionNode = mediaEngineClient.getPeerConnectionNode(key);
                    mediaEngineClient.closeNode(mediaConnectionNode);
                    signalingEventsMap.remove(key);
                    fullscreenRender.clearImage();
                    pipRenderer.clearImage();
                    showRenderer.clearImage();
                    showRenderer.setZOrderMediaOverlay(false);
                }
                //audioManager.stop();
                //audioManager = null;
            }
        });
    }

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        Log.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
        this.isSwappedFeeds = isSwappedFeeds;
        //mediaEngineRenderer.setRenderer(isSwappedFeeds);
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
