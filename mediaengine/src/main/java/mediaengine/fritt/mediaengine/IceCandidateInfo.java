package mediaengine.fritt.mediaengine;

import org.webrtc.IceCandidate;

public class IceCandidateInfo{
    public final String sdpMid;
    public final int sdpMLineIndex;
    public final String sdp;
    public final String serverUrl;

    public IceCandidateInfo(String sdpMid, int sdpMLineIndex, String sdp) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
        this.serverUrl = "";
    }

    private IceCandidateInfo(String sdpMid, int sdpMLineIndex, String sdp, String serverUrl) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
        this.serverUrl = serverUrl;
    }

    public String toString() {
        return this.sdpMid + ":" + this.sdpMLineIndex + ":" + this.sdp + ":" + this.serverUrl;
    }

    public IceCandidate ConvertToIC(){
        return new IceCandidate(sdpMid,sdpMLineIndex,sdp);
    }
}

