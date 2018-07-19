package mediaengine.fritt.mediaengine;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaEngineClient {
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";
    private static final String TAG = "PCRTCClient";
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    private static final String VIDEO_H264_HIGH_PROFILE_FIELDTRIAL =
            "WebRTC-H264HighProfile/Enabled/";
    private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private static final String AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl";
    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static final int BPS_IN_KBPS = 1000;

    //private static final MediaEngineClient instance = new MediaEngineClient();
    private final ScheduledExecutorService executor;
    //private final MCObserver mcObserver = new MCObserver();
    //private final SDPObserver sdpObserver = new SDPObserver();

    private PeerConnectionFactory mediaConnectionfactory;
    private List<MediaConnectionNode> mediaConnectionNodes;
    PeerConnectionFactory.Options options = null;
    private AudioSource audioSource;
    private VideoSource videoSource;
    private boolean videoCallEnabled;
    private boolean preferIsac;
    private String preferredVideoCodec;
    private boolean videoCapturerStopped;
    private boolean isError;
    private Timer statsTimer;
    private VideoRenderer.Callbacks localRender;
    private List<VideoRenderer.Callbacks> remoteRenders;
    private ClientInterface.SignalingParameters signalingParameters;
    private MediaConstraints pcConstraints;
    private int videoWidth;
    private int videoHeight;
    private int videoFps;
    private MediaConstraints audioConstraints;
    private ParcelFileDescriptor aecDumpFileDescriptor;
    private MediaConstraints sdpMediaConstraints;
    private MediaEngineParameters mediaEngineParameters;
    // Queued remote ICE candidates are consumed only after both local and
    // remote descriptions are set. Similarly local ICE candidates are sent to
    // remote peer after both local and remote description are set.
    private LinkedList<IceCandidateInfo> queuedRemoteCandidates;
    private MediaConnectionEvents events;
    private boolean isInitiator;
    private SessionDescriptionInfo localSdp; // either offer or answer SDP
    private MediaStream mediaStream;
    private VideoCapturer videoCapturer;
    // enableVideo is set to true if video should be rendered and sent.
    private boolean renderVideo;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private RtpSender localVideoSender;
    // enableAudio is set to true if audio should be sent.
    private boolean enableAudio;
    private AudioTrack localAudioTrack;
    private DataChannel dataChannel;
    private boolean dataChannelEnabled;

    public class MediaConnectionNode{
        public PeerConnection peerConnection;
        public String key;
        public boolean isInitiator;
        public MCObserver mcObserver = new MCObserver();
        public SDPObserver sdpObserver = new SDPObserver();
        private SessionDescriptionInfo localSdp; // either offer or answer SDP
        public List<VideoRenderer.Callbacks> remoteRenders;
        private DataChannel dataChannel;
        public MediaConnectionNode() {
            mcObserver.mediaConnectionNode = this;
            sdpObserver.mediaConnectionNode = this;
            peerConnection = null;
            localSdp = null;
            key = null;
            isInitiator = false;
            remoteRenders = null;
        }
    }

    public MediaConnectionNode getPeerConnectionNode(String key) {
        for (int i = 0; i < mediaConnectionNodes.size(); i++) {
            MediaConnectionNode peerConnectionNode = mediaConnectionNodes.get(i);
            if (peerConnectionNode.key.equals(key)) {
                return peerConnectionNode;
            }
        }

        return null;
    }

    public static class MediaEngineParameters{
        public final boolean videoCallEnabled;
        public final boolean audioCallEnabled;
        public final boolean loopback;
        public final boolean tracing;
        public final int videoWidth;
        public final int videoHeight;
        public final int videoFps;
        public final int videoMaxBitrate;
        public final String videoCodec;
        public final boolean videoCodecHwAcceleration;
        public final boolean videoFlexfecEnabled;
        public final int audioStartBitrate;
        public final String audioCodec;
        public final boolean noAudioProcessing;
        public final boolean aecDump;
        public final boolean useOpenSLES;
        public final boolean disableBuiltInAEC;
        public final boolean disableBuiltInAGC;
        public final boolean disableBuiltInNS;
        public final boolean enableLevelControl;
        public final boolean disableWebRtcAGCAndHPF;

        public MediaEngineParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
                                     int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec,
                                     boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, boolean audioCallEnabled,
                                     int audioStartBitrate,
                                     String audioCodec, boolean noAudioProcessing, boolean aecDump, boolean useOpenSLES,
                                     boolean disableBuiltInAEC, boolean disableBuiltInAGC, boolean disableBuiltInNS,
                                     boolean enableLevelControl, boolean disableWebRtcAGCAndHPF
        ) {
            this.videoCallEnabled = videoCallEnabled;
            this.audioCallEnabled = audioCallEnabled;
            this.loopback = loopback;
            this.tracing = tracing;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoMaxBitrate = videoMaxBitrate;
            this.videoCodec = videoCodec;
            this.videoFlexfecEnabled = videoFlexfecEnabled;
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
            this.audioStartBitrate = audioStartBitrate;
            this.audioCodec = audioCodec;
            this.noAudioProcessing = noAudioProcessing;
            this.aecDump = aecDump;
            this.useOpenSLES = useOpenSLES;
            this.disableBuiltInAEC = disableBuiltInAEC;
            this.disableBuiltInAGC = disableBuiltInAGC;
            this.disableBuiltInNS = disableBuiltInNS;
            this.enableLevelControl = enableLevelControl;
            this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
        }
    }

    public MediaEngineClient(ScheduledExecutorService executor) {
        // Executor thread is started once in private ctor and is used for all
        // peer connection API calls to ensure new peer connection factory is
        // created on the same thread as previously destroyed factory.
        this.executor = executor;
        mediaConnectionNodes = new ArrayList<MediaConnectionNode>();
    }

    //public static MediaEngineClient getInstance(){return instance;}

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;
        Logging.d(TAG, "Creating capturer using camera1 API.");
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));

        if (videoCapturer == null) {
            Log.d(TAG,"videoCapture is null");
            return null;
        }
        return videoCapturer;
    }


    public void createMediaConnectionFactory(final Context context,
                                             final MediaEngineParameters mediaEngineParameters,
                                             final MEglBase mEglBase,
                                             final MediaConnectionEvents events,
                                             final SurfaceViewRender localRender){
        this.mediaEngineParameters = mediaEngineParameters;
        this.events = events;
        this.localRender = localRender;
        this.videoCapturer = createVideoCapturer();
        videoCallEnabled = mediaEngineParameters.videoCallEnabled;
        //Rest variables to inital states
        mediaConnectionfactory = null;
        preferIsac = false;
        videoCapturerStopped = false;
        isError = false;
        queuedRemoteCandidates = null;
        localSdp = null;
        mediaStream = null;
        renderVideo = true;
        localVideoTrack = null;
        remoteVideoTrack = null;
        localVideoSender = null;
        enableAudio = mediaEngineParameters.audioCallEnabled;
        localAudioTrack = null;
        statsTimer = new Timer();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                createMediaConnectionFactoryInternal(context);

                createMediaConstraintsInternal();
                if (videoCallEnabled) {
                    Log.d(TAG, "EGLContext: " + mEglBase.getContext());
                    mediaConnectionfactory.setVideoHwAccelerationOptions(mEglBase.getContext(), mEglBase.getContext());
                }

                mediaStream = mediaConnectionfactory.createLocalMediaStream("ARDAMS");
                if (videoCallEnabled) {
                    mediaStream.addTrack(createVideoTrack(videoCapturer));
                }

                mediaStream.addTrack(createAudioTrack());

                if (mediaEngineParameters.aecDump) {
                    try {
                        aecDumpFileDescriptor =
                                ParcelFileDescriptor.open(new File(Environment.getExternalStorageDirectory().getPath()
                                                + File.separator + "Download/audio.aecdump"),
                                        ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                                                | ParcelFileDescriptor.MODE_TRUNCATE);
                        mediaConnectionfactory.startAecDump(aecDumpFileDescriptor.getFd(), -1);
                    } catch (IOException e) {
                        Log.e(TAG, "Can not open aecdump file", e);
                    }
                }
            }
        });
    }

    public void createMediaConnection(final MEglBase mEglBase, final MediaEngineRenderer mediaEngineRenderer, final ClientInterface.SignalingParameters
                                      signalingParameters, final String key){
        if (mediaEngineParameters == null) {
            Log.e(TAG, "Creating peer connection without initializing factory.");
            return;
        }
        MediaConnectionNode mediaConnectionNode = new MediaConnectionNode();
        mediaConnectionNode.isInitiator = signalingParameters.initiator;
        mediaConnectionNode.key = key;
        mediaConnectionNode.remoteRenders = mediaEngineRenderer.remoteRenderers;
        mediaConnectionNodes.add(mediaConnectionNode);
        Log.d(TAG,"CreateMediaConnection");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    //createMediaConstraintsInternal();
                    createPeerConnectionInternal(signalingParameters.initiator,key);
                } catch (Exception e) {
                    reportError("Failed to create peer connection: " + e.getMessage(),null);
                    throw e;
                }
            }
        });
    }

    private void createMediaConstraintsInternal() {
        Log.d(TAG,"Create MediaConstraints");
        // Create peer connection constraints.
        pcConstraints = new MediaConstraints();

        Log.d(TAG,"use DTLS");
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));


        // Check if there is a camera on device and disable video call if not.
        if (videoCapturer == null) {
            Log.w(TAG, "No camera on device. Switch to audio only call.");
            videoCallEnabled = false;
        }
        // Create video constraints if video call is enabled.
        if (videoCallEnabled) {
            videoWidth = mediaEngineParameters.videoWidth;
            videoHeight = mediaEngineParameters.videoHeight;
            videoFps = mediaEngineParameters.videoFps;

            // If video resolution is not specified, default to HD.
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = HD_VIDEO_WIDTH;
                videoHeight = HD_VIDEO_HEIGHT;
            }

            // If fps is not specified, default to 30.
            if (videoFps == 0) {
                videoFps = 30;
            }
            Log.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);
            Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);
        }

        // Create audio constraints.
        audioConstraints = new MediaConstraints();
        // added for audio performance measurements
        if (mediaEngineParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }
        if (mediaEngineParameters.enableLevelControl) {
            Log.d(TAG, "Enabling level control.");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(AUDIO_LEVEL_CONTROL_CONSTRAINT, "true"));
        }
        // Create SDP constraints.
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (videoCallEnabled) {
            sdpMediaConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        } else {
            sdpMediaConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        }
    }

    private void createPeerConnectionInternal(boolean isInitiator,String key) {
        if (mediaConnectionfactory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created");
            return;
        }
        Log.d(TAG, "Create peer connection.");

        Log.d(TAG, "PCConstraints: " + pcConstraints.toString());
        queuedRemoteCandidates = new LinkedList<IceCandidateInfo>();

        List<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);

        PeerConnection peerConnection = mediaConnectionfactory.createPeerConnection
                (rtcConfig, pcConstraints, mediaConnectionNode.mcObserver);

        mediaConnectionNode.peerConnection = peerConnection;
        isInitiator = false;

        mediaConnectionNode.peerConnection.addStream(mediaStream);
        if (videoCallEnabled) {
            findVideoSender(mediaConnectionNode);
        }
        Log.d(TAG, "Peer connection created.");
    }

    public void close() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                closeInternal();
            }
        });
    }

    public void closeNode(final MediaConnectionNode mediaConnectionNode) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                closeOneInternal(mediaConnectionNode);
            }
        });
    }

    private void closeOneInternal(MediaConnectionNode mediaConnectionNode) {
        if (mediaConnectionNode != null) {
            Log.d(TAG, "peer connection dispose closeOneInternal");
            Log.d(TAG, "peerConnection remove local stream");
            mediaConnectionNode.peerConnection.removeStream(mediaStream);
            mediaConnectionNode.peerConnection.dispose();
            mediaConnectionNode.peerConnection = null;
        }
        if (mediaConnectionNodes != null && mediaConnectionNode != null) {
            mediaConnectionNodes.remove(mediaConnectionNode);
        }
        Log.d(TAG, "peerConnectionNodes count:" + mediaConnectionNodes.size());
        events.onMediaConnectionClosed(mediaConnectionNode);
    }

    private void closeInternal() {
        if (mediaConnectionfactory != null && mediaEngineParameters.aecDump) {
            mediaConnectionfactory.stopAecDump();
        }
        Log.d(TAG, "Closing peer connection.");
        statsTimer.cancel();
        /*if (mediaConnection != null) {
            mediaConnection.dispose();
            mediaConnection = null;
        }*/
        if (mediaConnectionNodes != null) {
            for (int i = 0; i < mediaConnectionNodes.size(); i++) {
                MediaConnectionNode peerConnectionNode = mediaConnectionNodes.get(i);
                if (peerConnectionNode != null && peerConnectionNode.dataChannel != null) {
                    peerConnectionNode.dataChannel.dispose();
                    peerConnectionNode.dataChannel = null;
                }
                if (peerConnectionNode.peerConnection != null) {
                    Log.d(TAG, "peer connection dispose closeInternal");
                    peerConnectionNode.peerConnection.removeStream(mediaStream);
                    peerConnectionNode.peerConnection.dispose();
                    peerConnectionNode.peerConnection = null;
                    peerConnectionNode.remoteRenders = null;
                }
            }
            mediaConnectionNodes.clear();
        }
        Log.d(TAG, "Closing audio source.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.d(TAG, "Stopping capture.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturerStopped = true;
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        localRender = null;
        remoteRenders = null;
        Log.d(TAG, "Closing peer connection factory.");
        if (mediaConnectionfactory != null) {
            mediaConnectionfactory.dispose();
            mediaConnectionfactory = null;
        }
        options = null;
        Log.d(TAG, "Closing peer connection done.");
        //PeerConnectionFactory.stopInternalTracingCapture();
        //PeerConnectionFactory.shutdownInternalTracer();
        events = null;
    }

    private void createMediaConnectionFactoryInternal(Context context){
        //PeerConnectionFactory.initializeInternalTracer();
        isError = false;

        //Initialize field trials
        String fieldTrials = "";
        if (mediaEngineParameters.videoFlexfecEnabled) {
            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
            Log.d(TAG, "Enable FlexFEC field trial.");
        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        if (mediaEngineParameters.disableWebRtcAGCAndHPF) {
            fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
            Log.d(TAG, "Disable WebRTC AGC field trial.");
        }
        // Check preferred video codec.
        preferredVideoCodec = VIDEO_CODEC_VP8;
        if (videoCallEnabled && mediaEngineParameters.videoCodec != null) {
            switch (mediaEngineParameters.videoCodec) {
                case VIDEO_CODEC_VP8:
                    preferredVideoCodec = VIDEO_CODEC_VP8;
                    break;
                case VIDEO_CODEC_VP9:
                    preferredVideoCodec = VIDEO_CODEC_VP9;
                    break;
                case VIDEO_CODEC_H264_BASELINE:
                    preferredVideoCodec = VIDEO_CODEC_H264;
                    break;
                case VIDEO_CODEC_H264_HIGH:
                    // TODO(magjed): Strip High from SDP when selecting Baseline instead of using field trial.
                    fieldTrials += VIDEO_H264_HIGH_PROFILE_FIELDTRIAL;
                    preferredVideoCodec = VIDEO_CODEC_H264;
                    break;
                default:
                    preferredVideoCodec = VIDEO_CODEC_VP8;
            }
        }
        Log.d(TAG, "Preferred video codec: " + preferredVideoCodec);
        PeerConnectionFactory.initializeFieldTrials(fieldTrials);
        Log.d(TAG, "Field trials: " + fieldTrials);

        // Check if ISAC is used by default.
        preferIsac = mediaEngineParameters.audioCodec != null
                && mediaEngineParameters.audioCodec.equals(AUDIO_CODEC_ISAC);

        // Create peer connection factory.
        PeerConnectionFactory.initializeAndroidGlobals(
                context, mediaEngineParameters.videoCodecHwAcceleration);
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        mediaConnectionfactory = new PeerConnectionFactory(options);
        Log.d(TAG, "Peer connection factory created.");

    }

    private AudioTrack createAudioTrack() {
        audioSource = mediaConnectionfactory.createAudioSource(audioConstraints);
        localAudioTrack = mediaConnectionfactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(enableAudio);
        return localAudioTrack;
    }

    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        videoSource = mediaConnectionfactory.createVideoSource(capturer);
        capturer.startCapture(videoWidth, videoHeight, videoFps);

        localVideoTrack = mediaConnectionfactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));
        return localVideoTrack;
    }

    private void findVideoSender(MediaConnectionNode mediaConnectionNode) {
        for (RtpSender sender : mediaConnectionNode.peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals(VIDEO_TRACK_TYPE)) {
                    Log.d(TAG, "Found video sender.");
                    localVideoSender = sender;
                }
            }
        }
    }

    private void getStats(final MediaConnectionNode peerConnectionNode) {
        if (peerConnectionNode == null || peerConnectionNode.peerConnection == null || isError) {
            return;
        }
        boolean success = peerConnectionNode.peerConnection.getStats(new StatsObserver() {
            @Override
            public void onComplete(final StatsReport[] reports) {
                events.onMediaConnectionStatsReady((StatesReporter[]) reports, peerConnectionNode);
            }
        }, null);
        if (!success) {
            Log.e(TAG, "getStats() returns false!");
        }
    }

    public void enableStatsEvents(boolean enable, int periodMs, final MediaConnectionNode peerConnectionNode) {
        if (enable) {
            try {
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                getStats(peerConnectionNode);
                            }
                        });
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "Can not schedule statistics timer", e);
            }
        } else {
            statsTimer.cancel();
        }
    }

    public void setAudioEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                enableAudio = enable;
                if (localAudioTrack != null) {
                    localAudioTrack.setEnabled(enableAudio);
                }
            }
        });
    }

    public void setVideoEnabled(final boolean enable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                renderVideo = enable;
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(renderVideo);
                }
                if (remoteVideoTrack != null) {
                    remoteVideoTrack.setEnabled(renderVideo);
                }
            }
        });
    }

    public void createOffer(final String key) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);
                if (mediaConnectionNode != null && mediaConnectionNode.peerConnection != null && !isError) {
                    Log.d(TAG, "PC Create OFFER: " + mediaConnectionNode.key);
                    isInitiator = true;
                    mediaConnectionNode.peerConnection.createOffer(mediaConnectionNode.sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer(final String key) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);
                if (mediaConnectionNode != null && mediaConnectionNode.peerConnection != null && !isError)  {
                    Log.d(TAG, "PC create ANSWER");
                    isInitiator = false;
                    mediaConnectionNode.peerConnection.createAnswer(mediaConnectionNode.sdpObserver, sdpMediaConstraints);
                    Log.d(TAG, "PC create ANSWER over");
                }
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidateInfo candidate,final String key) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);
                if (mediaConnectionNode != null && mediaConnectionNode.peerConnection != null && !isError) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        mediaConnectionNode.peerConnection.addIceCandidate(candidate.ConvertToIC());
                    }
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidateInfo[] candidates,final String key) {
        executor.execute(new Runnable() {
            MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);
            @Override
            public void run() {
                if (mediaConnectionNode != null && mediaConnectionNode.peerConnection != null && !isError){
                    return;
                }
                // Drain the queued remote candidates if there is any so that
                // they are processed in the proper order.
                drainCandidates(mediaConnectionNode);
                IceCandidate[] candidates1 = new IceCandidate[256];
                for(int i = 0; i < candidates.length;i++)
                {
                    candidates1[i] = candidates[i].ConvertToIC();
                }
                mediaConnectionNode.peerConnection.removeIceCandidates(candidates1);
            }
        });
    }

    public void setRemoteDescription(final SessionDescriptionInfo sdp,final String key) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                MediaConnectionNode mediaConnectionNode = getPeerConnectionNode(key);
                Log.d(TAG,"peerconnection key: " + mediaConnectionNode.key);
                /*if (mediaConnectionNode != null && mediaConnectionNode.peerConnection != null && !isError){
                    Log.d(TAG,"Error to return!!!");
                    return;
                }*/
                String sdpDescription = sdp.description;
                if (preferIsac) {
                    sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
                }
                if (videoCallEnabled) {
                    sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
                }
                if (mediaEngineParameters.audioStartBitrate > 0) {
                    sdpDescription = setStartBitrate(
                            AUDIO_CODEC_OPUS, false, sdpDescription, mediaEngineParameters.audioStartBitrate);
                }
                Log.d(TAG, "Set remote SDP.");
                SessionDescription sdpRemote =  sdp.ConvertToSD();
                mediaConnectionNode.peerConnection.setRemoteDescription(mediaConnectionNode.sdpObserver, sdpRemote);
                Log.d(TAG, "Set remote SDP Over.");
            }
        });
    }

    public void stopVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && !videoCapturerStopped) {
                    Log.d(TAG, "Stop video source.");
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                    }
                    videoCapturerStopped = true;
                }
            }
        });
    }

    public void startVideoSource() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (videoCapturer != null && videoCapturerStopped) {
                    Log.d(TAG, "Restart video source.");
                    videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
                    videoCapturerStopped = false;
                }
            }
        });
    }

    public void setVideoMaxBitrate(final Integer maxBitrateKbps,final MediaConnectionNode mediaConnectionNode) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (mediaConnectionNode == null || mediaConnectionNode.peerConnection == null || localVideoSender == null || isError) {
                    return;
                }
                Log.d(TAG, "Requested max video bitrate: " + maxBitrateKbps);
                if (localVideoSender == null) {
                    Log.w(TAG, "Sender is not ready.");
                    return;
                }

                RtpParameters parameters = localVideoSender.getParameters();
                if (parameters.encodings.size() == 0) {
                    Log.w(TAG, "RtpParameters are not ready.");
                    return;
                }

                for (RtpParameters.Encoding encoding : parameters.encodings) {
                    // Null value means no limit.
                    encoding.maxBitrateBps = maxBitrateKbps == null ? null : maxBitrateKbps * BPS_IN_KBPS;
                }
                if (!localVideoSender.setParameters(parameters)) {
                    Log.e(TAG, "RtpSender.setParameters failed.");
                }
                Log.d(TAG, "Configured max video bitrate to: " + maxBitrateKbps);
            }
        });
    }


    private static String setStartBitrate(
            String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet =
                            "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "="
                            + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }

    /** Returns the line number containing "m=audio|video", or -1 if no such line exists. */
    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    private static String movePayloadTypesToFront(List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<String>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<String>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (int i = 0; i < lines.length; ++i) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private void drainCandidates(final MediaConnectionNode mediaConnectionNode) {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Adds " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidateInfo candidate : queuedRemoteCandidates) {
                boolean a = mediaConnectionNode.peerConnection.addIceCandidate(candidate.ConvertToIC());
                Log.d(TAG,"addIceCandidate native:" + a);
            }
            queuedRemoteCandidates = null;
        }
    }

    private void switchCameraInternal() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            if (!videoCallEnabled || isError || videoCapturer == null) {
                Log.e(TAG, "Failed to switch camera. Video: " + videoCallEnabled + ". Error : " + isError);
                return; // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }

    public void switchCamera() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                switchCameraInternal();
            }
        });
    }

    public void changeCaptureFormat(final int width, final int height, final int framerate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                changeCaptureFormatInternal(width, height, framerate);
            }
        });
    }

    private void changeCaptureFormatInternal(int width, int height, int framerate) {
        if (!videoCallEnabled || isError || videoCapturer == null) {
            Log.e(TAG,
                    "Failed to change capture format. Video: " + videoCallEnabled + ". Error : " + isError);
            return;
        }
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        videoSource.adaptOutputFormat(width, height, framerate);
    }

    private void reportError(final String errorMessage, final MediaConnectionNode peerConnectionNode) {
        Log.e(TAG, "Mediaconnection error: " + errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    events.onMediaConnectionError(errorMessage,peerConnectionNode);
                    isError = true;
                }
            }
        });
    }

    /**
     * Observer to get PeerConnection events
     */
    private class MCObserver implements PeerConnection.Observer{
        public MediaConnectionNode mediaConnectionNode;
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    IceCandidateInfo candidateInfo = new IceCandidateInfo(candidate.sdp,candidate.sdpMLineIndex,candidate.sdp);
                    events.onIceCandidate(candidateInfo,mediaConnectionNode);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    IceCandidateInfo[] candidates1 = new IceCandidateInfo[256];
                    for(int i = 0; i < candidates.length;i++)
                    {
                        candidates1[i] = new IceCandidateInfo(candidates[i].sdpMid,candidates[i].sdpMLineIndex,candidates[i].sdp);
                    }
                    events.onIceCandidatesRemoved(candidates1,mediaConnectionNode);
                }
            });
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "IceConnectionState: " + newState);
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected(mediaConnectionNode);
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected(mediaConnectionNode);
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        reportError("ICE connection failed.",mediaConnectionNode);
                    }
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mediaConnectionNode.peerConnection == null || isError) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        reportError("Weird-looking stream: " + stream,mediaConnectionNode);
                        return;
                    }
                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(renderVideo);
                        for (VideoRenderer.Callbacks remoteRender : mediaConnectionNode.remoteRenders) {
                            remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                        }
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            Log.d(TAG, "New Data channel " + dc.label());

            if (!dataChannelEnabled)
                return;

            dc.registerObserver(new DataChannel.Observer() {
                public void onBufferedAmountChange(long previousAmount) {
                    Log.d(TAG, "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "Data channel state changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onMessage(final DataChannel.Buffer buffer) {
                    if (buffer.binary) {
                        Log.d(TAG, "Received binary msg over " + dc);
                        return;
                    }
                    ByteBuffer data = buffer.data;
                    final byte[] bytes = new byte[data.capacity()];
                    data.get(bytes);
                    String strData = new String(bytes);
                    Log.d(TAG, "Got msg: " + strData + " over " + dc);
                }
            });
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }

        @Override
        public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {}
    }
    // Implementation detail: handle offer creation/signaling and answer setting,
    // as well as adding remote ICE candidates once the answer SDP is set.
    private class SDPObserver implements SdpObserver {
        public MediaConnectionNode mediaConnectionNode;
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            if (mediaConnectionNode.localSdp != null) {
                reportError("Multiple SDP create.",mediaConnectionNode);
                return;
            }
            String sdpDescription = origSdp.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            }
            if (videoCallEnabled) {
                sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
            }
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            mediaConnectionNode.localSdp = new SessionDescriptionInfo(sdp.type.canonicalForm(),sdp.description);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mediaConnectionNode.peerConnection != null && !isError) {
                        Log.d(TAG, "Set local SDP from " + sdp.type);
                        mediaConnectionNode.peerConnection.setLocalDescription(mediaConnectionNode.sdpObserver, sdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mediaConnectionNode.peerConnection == null || isError) {
                        return;
                    }
                    if (isInitiator) {
                        // For offering peer connection we first create offer and set
                        // local SDP, then after receiving answer set remote SDP.
                        if (mediaConnectionNode.peerConnection.getRemoteDescription() == null) {
                            // We've just set our local SDP so time to send it.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(mediaConnectionNode.localSdp,mediaConnectionNode);
                        } else {
                            // We've just set remote description, so drain remote
                            // and send local ICE candidates.
                            Log.d(TAG, "Remote SDP set succesfully");
                            drainCandidates(mediaConnectionNode);
                        }
                    } else {
                        // For answering peer connection we set remote SDP and then
                        // create answer and set local SDP.
                        if (mediaConnectionNode.peerConnection.getLocalDescription() != null) {
                            // We've just set our local SDP so time to send it, drain
                            // remote and send local ICE candidates.
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(mediaConnectionNode.localSdp,mediaConnectionNode);
                            drainCandidates(mediaConnectionNode);
                        } else {
                            // We've just set remote SDP - do nothing for now -
                            // answer will be created soon.
                            Log.d(TAG, "Remote SDP set succesfully");
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error,mediaConnectionNode);
        }

        @Override
        public void onSetFailure(final String error) {
            reportError("setSDP error: " + error,mediaConnectionNode);
        }
    }
}

