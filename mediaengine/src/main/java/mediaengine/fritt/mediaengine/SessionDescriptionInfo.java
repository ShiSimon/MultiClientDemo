package mediaengine.fritt.mediaengine;

import org.webrtc.SessionDescription;

public class SessionDescriptionInfo {
    public final String type;
    public final String description;

    public SessionDescriptionInfo(String type, String description) {
        this.type = type;
        this.description = description;
    }



    public SessionDescription ConvertToSD(){
        SessionDescription.Type infotype = SessionDescription.Type.fromCanonicalForm(type);
        return new SessionDescription(infotype,description);
    }

}