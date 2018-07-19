package mediaengine.fritt.mediaengine;

public interface MediaConnectionEvents {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescriptionInfo sdp, final MediaEngineClient.MediaConnectionNode mediaConnectionNode);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidateInfo candidate, final MediaEngineClient.MediaConnectionNode mediaConnectionNode);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidateInfo[] candidates, final MediaEngineClient.MediaConnectionNode mediaConnectionNode);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected(final MediaEngineClient.MediaConnectionNode MediaConnectionNode);

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected(final MediaEngineClient.MediaConnectionNode MediaConnectionNode);

    /**
     * Callback fired once Media connection is closed.
     */
    void onMediaConnectionClosed(final MediaEngineClient.MediaConnectionNode MediaConnectionNode);

    /**
     * Callback fired once Media connection statistics is ready.
     */
    void onMediaConnectionStatsReady(final StatesReporter[] reports, final MediaEngineClient.MediaConnectionNode MediaConnectionNode);

    /**
     * Callback fired once Media connection error happened.
     */
    void onMediaConnectionError(final String description, final MediaEngineClient.MediaConnectionNode MediaConnectionNode);
}