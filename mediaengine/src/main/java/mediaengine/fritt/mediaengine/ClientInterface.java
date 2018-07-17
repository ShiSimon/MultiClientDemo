package mediaengine.fritt.mediaengine;

import java.util.List;

public interface ClientInterface {

    class RoomConnectionParameters{
        public final String roomUrl;
        public final String roomId;
        public final boolean loopback;
        public final String urlParameters;
        public RoomConnectionParameters(String roomUrl,String roomId,boolean loopback,
                                        String urlParameters){
            this.roomId = roomId;
            this.roomUrl = roomUrl;
            this.loopback = loopback;
            this.urlParameters = urlParameters;
        }
        public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback) {
            this(roomUrl, roomId, loopback, null /* urlParameters */);
        }
    }

    /**
     * Struct holding the signaling parameters of an AppRTC room.
     */
    class SignalingParameters {
        public List<IceServerInfo> iceServers;
        public boolean initiator;
        public String clientId;
        public String wssUrl;
        public String wssPostUrl;
        public SessionDescriptionInfo offerSdp;
        public List<IceCandidateInfo> iceCandidates;

        public SignalingParameters(List<IceServerInfo> iceServers, boolean initiator,
                                   String clientId, String wssUrl, String wssPostUrl, SessionDescriptionInfo offerSdp,
                                   List<IceCandidateInfo> iceCandidates) {
            this.iceServers = iceServers;
            this.initiator = initiator;
            this.clientId = clientId;
            this.wssUrl = wssUrl;
            this.wssPostUrl = wssPostUrl;
            this.offerSdp = offerSdp;
            this.iceCandidates = iceCandidates;
        }
    }

    /**
     * Asynchronously connect to an AppRTC room URL using supplied connection
     * parameters. Once connection is established onConnectedToRoom()
     * callback with room parameters is invoked.
     */
    void startService(final String service_name);

    void stopRecord();

    /**
     * Send offer SDP to the other participant.
     */
    void sendOfferSdp(final String key,final SessionDescriptionInfo sdp);

    /**
     * Send answer SDP to the other participant.
     */
    void sendAnswerSdp(final String key,final SessionDescriptionInfo sdp);

    /**
     * Send Ice candidate to the other participant.
     */
    void sendLocalIceCandidate(final String key,final IceCandidateInfo candidate);

    /**
     * Send removed ICE candidates to the other participant.
     */
    void sendLocalIceCandidateRemovals(final String key,final IceCandidateInfo[] candidates);

    /**
     * Disconnect from room.
     */
    void disconnectChannel(final String key);

    /**
     * Callback interface for messages delivered on signaling channel.
     *
     * <p>Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    interface SignalingEvents {

        void onCreateSubscriber();

        void onCreateConnectToServer();

        void onShowFiles(final String msg);
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        void onConnectedToRoom(final SignalingParameters params);

        void onConnectedToCall();

        void onConnectedToAnswer(final SessionDescriptionInfo sdp);
        /**
         * Callback fired once remote SDP is received.
         */
        void onRemoteDescription(final SessionDescriptionInfo sdp);

        /**
         * Callback fired once remote Ice candidate is received.
         */
        void onRemoteIceCandidate(final IceCandidateInfo candidate);

        /**
         * Callback fired once remote Ice candidate removals are received.
         */
        void onRemoteIceCandidatesRemoved(final IceCandidateInfo[] candidates);

        /**
         * Callback fired once channel is closed.
         */
        void onChannelClose();

        /**
         * Callback fired once channel error happened.
         */
        void onChannelError(final String description);

        void onAllDisconnect();
    }


}
