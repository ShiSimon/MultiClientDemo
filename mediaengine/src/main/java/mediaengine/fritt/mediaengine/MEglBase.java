package mediaengine.fritt.mediaengine;

import org.webrtc.EglBase;

public class MEglBase{
    private EglBase glBase;

    public MEglBase(){
        glBase = EglBase.create();
    }

    public EglBase.Context getContext(){
        return glBase.getEglBaseContext();
    }

    public void release(){glBase.release();}
}
