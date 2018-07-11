package mediaengine.fritt.multiclientdemo;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mediaengine.fritt.mediaengine.ClientInterface;
import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.IceServerInfo;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

public class WebSocketClient implements ClientInterface,WebSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "WSClient";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private final Handler handler;
    private WebSocketChannelClient wsClient;
    private ConnectionState WSCState;
    //private RoomConnectionParameters connectionParameters;
    //private SignalingParameters params;
    private ConcurrentHashMap MCMmessageMangers;

    private ConcurrentHashMap events;

    public WebSocketClient() {
        events = new ConcurrentHashMap();
        MCMmessageMangers = new ConcurrentHashMap();
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void registerEvent(SignalingEvents event,String key){
        events.put(key,event);
    }


    public void connectToServer(final String url,final int port){
        WSCState = ConnectionState.NEW;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToServerInternal(url,port);
            }
        });
    }

    private void connectToServerInternal(String url,int port){
        wsClient = new WebSocketChannelClient(handler,this);
        Log.d(TAG,"Try to connect server");
        wsClient.connect(url,port);
    }


    public void createMCWebSocketChannel(final String name){
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Try to connect a websocket channel");
                MCMessageManager mcMessageManager = new MCMessageManager();
                MCMessageManager.MessageHandler create = new MCMessageManager.MessageHandler("create");
                mcMessageManager.messageHandlers.add(create);
                mcMessageManager.service_name = name;
                MCMmessageMangers.put(name,mcMessageManager);
                if(WSCState != ConnectionState.CONNECTED){
                    Log.d(TAG,"Try to send message in state: " + WSCState);
                    return;
                }
                JSONObject json = new JSONObject();
                try {
                    json.put("mms","create");
                    json.put("transaction",create.getTransaction());
                    Log.d(TAG, "C->S: " + json.toString());
                    wsClient.send(json.toString());
                }catch (JSONException e){
                    reportError("WebSocket register JSON error: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void stopRecord() {
        Log.d(TAG,"stopRecord");
        handler.post(new Runnable() {
            @Override
            public void run() {
                String key = findRecordkey();
                MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
                MCMessageManager.MessageHandler local_message =
                        new MCMessageManager.MessageHandler("message");
                manager.messageHandlers.add(local_message);
                JSONObject json = new JSONObject();
                try{
                    json.put("mms",local_message.getMms());
                    json.put("transaction",local_message.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("handle_id",manager.handle_id);
                    JSONObject data = new JSONObject();
                    data.put("request","stop");
                    String data_string = data.toString();
                    json.put("body",data);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendAttach error: " + e.getMessage());
                }
            }
        });
    }

    private String findRecordkey(){
        for(Object key:MCMmessageMangers.keySet()){
            MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
            if(manager.service_type.equals("RECORD")){
                return key.toString();
            }
        }
        return null;
    }

    public void sendAttach(final String name, final String opaque_id, final String service_name, final String type){
        Log.d(TAG,"sendAttach :" + service_name);
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(name);
                manager.service_name = service_name;
                manager.opaque_id = opaque_id;
                manager.service_type = type;
                MCMessageManager.MessageHandler handler = new MCMessageManager.MessageHandler("attach");
                manager.messageHandlers.add(handler);
                try{
                    json.put("mms",handler.getMms());
                    json.put("transaction",handler.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("service",service_name);
                    json.put("opaque_id",manager.opaque_id);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendAttach error: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void onWebSocketEstablished() {
        Log.d(TAG,"WebSocket established");
        WSCState = ConnectionState.CONNECTED;
    }

    @Override
    public void onWebSocketMessage(final String msg) {
        try {
            Log.d(TAG,"onWebSocketMessage");
            JSONObject json = new JSONObject(msg);
            if(msg.length() > 0){
                if(json.isNull("session_id")){
                    //Only get in when response create
                    String tran = json.getString("transaction");
                    String name = getEventObject(tran);
                    Log.d(TAG,"name: " + name);
                    if (name == null) {
                        Log.d(TAG, "Error!");
                        return;
                    }
                    JSONObject data = json.getJSONObject("data");
                    Log.d(TAG,"data = " + data.toString());
                    String session_Id = data.getString("id");
                    Log.d(TAG,"session_id = " + session_Id);
                    long test = Long.parseLong(session_Id);
                    MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(name);
                    manager.session_id = test;
                    SignalingEvents event = (SignalingEvents) events.get(name);
                    event.onCreateConnectToServer();
                }else {
                    String session_id = json.getString("session_id");
                    long id = Long.parseLong(session_id);
                    String name = getNameFromid(id);
                    Log.d(TAG,"name = " + name);
                    SignalingEvents event = (SignalingEvents) events.get(name);
                    MCMessageManager manger = (MCMessageManager) MCMmessageMangers.get(name);
                    if(manger.service_type.equals("ECHO")){
                        onMessageEcho(json,event,manger);
                    }else if(manger.service_type.equals("RECORD")){
                        onMessageRecord(json,event,manger);
                    }else if(manger.service_type.equals("PLAY")){
                        onMessagePlay(json,event,manger);
                    }else{
                        Log.d(TAG,"Did not add service");
                    }
                    //Get the right messagemanger
                    /*String session_id = json.getString("session_id");
                    long id = Long.parseLong(session_id);
                    String name = getNameFromid(id);
                    Log.d(TAG,"name = " + name);
                    SignalingEvents event = (SignalingEvents) events.get(name);
                    MCMessageManager manger = (MCMessageManager) MCMmessageMangers.get(name);
                    if(!json.isNull("transaction")){
                        int ret = CheckIfInServerManager(manger,json.getString("transaction"));
                        if(ret != 10000){
                            //Find the message what we have send
                            if(manger.messageHandlers.get(ret).getMms().equals("attach")){
                                JSONObject data = json.getJSONObject("data");
                                String handle_Id = data.getString("id");
                                long tmp = Long.parseLong(handle_Id);
                                manger.handle_id = tmp;
                                if(manger.service_name.equals("service.recordplay"))
                                {
                                    if(name.equals("record-service")){
                                        sendRecord(manger);
                                    }else{
                                        sendListFiles(manger);
                                    }
                                }
                                else if(manger.service_name.equals("service.echo")){
                                    Log.d(TAG,"sendEcho");
                                    sendEcho(manger);
                                }
                            }else if(manger.messageHandlers.get(ret).getMms().equals("message")){
                                Log.d(TAG,"Message");
                                if(manger.service_name.equals("service.echo")){
                                    Log.d(TAG,"echo-service");
                                    if(json.getString("mms").equals("event")){
                                        if(json.isNull("jsep")){
                                            Log.d(TAG,"Try to connect to room");
                                            SignalingParameters params = new SignalingParameters(new LinkedList<IceServerInfo>(), true,
                                                null, "ws://10.0.1.116:6660",null,null,null);
                                            ((SignalingEvents) events.get(name)).onConnectedToRoom(params);
                                        }else{
                                            Log.d(TAG,"Try to add answer");
                                            JSONObject sdpjson = json.getJSONObject("jsep");
                                            //serverConnectionManager.messageHandler.remove(check);
                                            SessionDescriptionInfo sdp = new SessionDescriptionInfo("answer",
                                                    sdpjson.getString("sdp"));
                                            ((SignalingEvents) events.get(name)).onRemoteDescription(sdp);
                                        }
                                    }
                                }
                                else if (manger.service_name.equals("service.recordplay")){
                                    Log.d(TAG,"service.recordplay");
                                    if(json.getString("mms").equals("event")){
                                        if(!json.isNull("jsep")){
                                            JSONObject sdpjson = json.getJSONObject("jsep");
                                            SessionDescriptionInfo sdp = new SessionDescriptionInfo("offer",
                                                    sdpjson.getString("sdp"));
                                            ((SignalingEvents) events.get(name)).onConnectedToAnswer(sdp);
                                        }else{
                                            Log.d(TAG,"Try to connect to room");
                                            SignalingParameters params = new SignalingParameters(new LinkedList<IceServerInfo>(), true,
                                                    null, "ws://10.0.1.116:6660",null,null,null);
                                            ((SignalingEvents) events.get(name)).onConnectedToRoom(params);
                                        }
                                    }else if (json.getString("mms").equals("ack")){
                                        return;
                                    }else{
                                        Log.d(TAG,"list recordplay");
                                        getFileToPlay(manger,json);}
                                }
                            }
                        }else{
                            Log.d(TAG,"Did not send this message: " + manger.session_id);
                        }
                    }else{
                        return;
                    }*/
                }
            }else{
                Log.d(TAG,"Error from server");
            }
        }catch (JSONException e){
            reportError(e.getMessage());
        }
    }

    private void onMessageEcho(final JSONObject json,final SignalingEvents events,MCMessageManager manager){
        if(!json.isNull("transaction")){
            try {
                int ret = CheckIfInServerManager(manager,json.getString("transaction"));
                if(ret != 10000){
                    //response what have sended
                    if(manager.messageHandlers.get(ret).getMms().equals("attach")){
                        //Get handle id after send attach
                        JSONObject data = json.getJSONObject("data");
                        String handle_Id = data.getString("id");
                        long tmp = Long.parseLong(handle_Id);
                        manager.handle_id = tmp;
                        Log.d(TAG,"Apply Echo");
                        sendEcho(manager);
                    }else if(manager.messageHandlers.get(ret).getMms().equals("message") && json.getString("mms").equals("event")){
                        if(json.isNull("jsep")){
                            Log.d(TAG,"Try to init one media engine & offer sdp");
                            SignalingParameters params = new SignalingParameters(new LinkedList<IceServerInfo>(), true,
                                null, "ws://10.0.1.116:6660",null,null,null);
                            events.onConnectedToRoom(params);
                        }else{
                            Log.d(TAG,"Try to set remote description");
                            JSONObject sdpjson = json.getJSONObject("jsep");
                            SessionDescriptionInfo sdp = new SessionDescriptionInfo("answer",
                                    sdpjson.getString("sdp"));
                            events.onRemoteDescription(sdp);
                        }
                    }else if(json.getString("mms").equals("ack")){
                        Log.d(TAG,"Handle ack");
                        return;
                    }
                }else{
                    Log.d(TAG,"Did not have sended " + manager.session_id);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }else{
            return;
        }
    }

    private void onMessageRecord(final JSONObject json,final SignalingEvents events,MCMessageManager manager){
        if(!json.isNull("transaction")){
            try {
                int ret = CheckIfInServerManager(manager,json.getString("transaction"));
                if(ret != 10000){
                    //response what have sended
                    if(manager.messageHandlers.get(ret).getMms().equals("attach")){
                        //Get handle id after send attach
                        JSONObject data = json.getJSONObject("data");
                        String handle_Id = data.getString("id");
                        long tmp = Long.parseLong(handle_Id);
                        manager.handle_id = tmp;
                        Log.d(TAG,"Apply Record");
                        sendRecord(manager);
                    }else if(manager.messageHandlers.get(ret).getMms().equals("message")){
                        if(json.getString("mms").equals("success")){
                            //server is ready to receive sdp offer
                            Log.d(TAG,"Try to init one media engine & offer sdp");
                            SignalingParameters params = new SignalingParameters(new LinkedList<IceServerInfo>(), true,
                                    null, "ws://10.0.1.116:6660",null,null,null);
                            events.onConnectedToRoom(params);
                        }
                        if(json.getString("mms").equals("event") && manager.messageHandlers.get(ret).getMms().equals("message")){
                            if(json.isNull("jsep")){
                                //server receive stop record
                                events.onChannelClose();
                            }else{
                                //server have received offer sdp,try to add answer sdp to media engine
                                Log.d(TAG,"Try to set remote description");
                                JSONObject sdpjson = json.getJSONObject("jsep");
                                SessionDescriptionInfo sdp = new SessionDescriptionInfo("answer",
                                    sdpjson.getString("sdp"));
                                events.onRemoteDescription(sdp);
                            }
                        }
                    }else if(json.getString("mms").equals("ack")){
                        Log.d(TAG,"Handle ack");
                        return;
                    }
                }else{
                    Log.d(TAG,"Did not have sended " + manager.session_id);
                }
            }catch (JSONException e){
                e.printStackTrace();
            }
        }else{
            return;
        }
    }

    private void onMessagePlay(final JSONObject json,final SignalingEvents events,MCMessageManager manager){
        Log.d(TAG,"onMessagePlay");
        if(!json.isNull("transaction")){
            try {
                int ret = CheckIfInServerManager(manager,json.getString("transaction"));
                if(ret != 10000){
                    //response what have sended
                    if(manager.messageHandlers.get(ret).getMms().equals("attach")){
                        //Get handle id after send attach
                        JSONObject data = json.getJSONObject("data");
                        String handle_Id = data.getString("id");
                        long tmp = Long.parseLong(handle_Id);
                        manager.handle_id = tmp;
                        Log.d(TAG,"Apply Play");
                        sendListFiles(manager);
                    }else if(manager.messageHandlers.get(ret).getMms().equals("message")){
                        if(json.getString("mms").equals("success")){
                            Log.d(TAG,"Get File to play");
                            getFileToPlay(manager,json);
                        }else if(json.getString("mms").equals("event") && !json.isNull("jsep")){
                            Log.d(TAG,"Get offer sdp,Try to init one media connection");
                            JSONObject sdpjson = json.getJSONObject("jsep");
                            SessionDescriptionInfo sdp = new SessionDescriptionInfo("offer",
                                    sdpjson.getString("sdp"));
                            events.onConnectedToAnswer(sdp);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else{
            try {
                if(json.getString("mms").equals("hangup")){
                    events.onChannelClose();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendRecord(final MCMessageManager manager){
        handler.post(new Runnable() {
            @Override
            public void run() {
                MCMessageManager.MessageHandler local_message =
                        new MCMessageManager.MessageHandler("message");
                manager.messageHandlers.add(local_message);
                JSONObject json = new JSONObject();
                try{
                    json.put("mms",local_message.getMms());
                    json.put("transaction",local_message.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("handle_id",manager.handle_id);
                    JSONObject data = new JSONObject();
                    data.put("request","configure");
                    data.put("video-bitrate-max",1048576);
                    data.put("video-keyframe-interval",15000);
                    String data_string = data.toString();
                    json.put("body",data);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendAttach error: " + e.getMessage());
                }
            }
        });
    }

    private void getFileToPlay(final MCMessageManager manager,final JSONObject json){
        handler.post(new Runnable() {
            @Override
            public void run() {
                /*String id = null;
                try {
                    JSONObject data = json.getJSONObject("servicedata").getJSONObject("data");
                    JSONArray list = data.getJSONArray("list");
                    for(int i=0;i<list.length();i++){
                        JSONObject obj = list.getJSONObject(0);
                        if(obj.getString("video").equals(true)){
                            id = obj.getString("id");
                            break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }*/
                MCMessageManager.MessageHandler messageHandler = new MCMessageManager.MessageHandler("message");
                manager.messageHandlers.add(messageHandler);
                JSONObject json = new JSONObject();
                try{
                    json.put("mms",messageHandler.getMms());
                    json.put("transaction",messageHandler.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("handle_id",manager.handle_id);
                    JSONObject data = new JSONObject();
                    data.put("request","play");
                    long tmp = Long.parseLong("1804345911226965");
                    data.put("id",tmp);
                    String data_string = data.toString();
                    json.put("body",data);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendMessage error: " + e.getMessage());
                }
            }
        });
    }

    private void sendEcho(final MCMessageManager manager){
        handler.post(new Runnable() {
            @Override
            public void run() {
                MCMessageManager.MessageHandler local_message =
                        new MCMessageManager.MessageHandler("message");
                manager.messageHandlers.add(local_message);
                JSONObject json = new JSONObject();
                try{
                    json.put("mms",local_message.getMms());
                    json.put("transaction",local_message.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("handle_id",manager.handle_id);
                    JSONObject data = new JSONObject();
                    data.put("video",true);
                    data.put("aduio",true);
                    String data_string = data.toString();
                    json.put("body",data);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendAttach error: " + e.getMessage());
                }
            }
        });
    }

    private void sendListFiles(final  MCMessageManager manager){
        handler.post(new Runnable() {
            @Override
            public void run() {
                MCMessageManager.MessageHandler local_message =
                        new MCMessageManager.MessageHandler("message");
                manager.messageHandlers.add(local_message);
                JSONObject json = new JSONObject();
                try{
                    json.put("mms",local_message.getMms());
                    json.put("transaction",local_message.getTransaction());
                    json.put("session_id",manager.session_id);
                    json.put("handle_id",manager.handle_id);
                    JSONObject data = new JSONObject();
                    data.put("request","list");
                    String data_string = data.toString();
                    json.put("body",data);
                    String message = json.toString();
                    Log.d(TAG,"C->S :" + message);
                    wsClient.send(message);
                }catch (JSONException e){
                    reportError("sendAttach error: " + e.getMessage());
                }
            }
        });
    }

    private int CheckIfInServerManager(final MCMessageManager manager,final String transaction){
        for(int i = 0;i < manager.messageHandlers.size();i++){
            if(transaction.equals(manager.messageHandlers.get(i).getTransaction())){
                return i;
            }
        }
        return 10000;
    }

    private String getNameFromid(long id){
        for(Object key:MCMmessageMangers.keySet()){
            MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
            if(manager.session_id == id){
                return key.toString();
            }
        }
        Log.d(TAG,"Cannot find session_id");
        return null;
    }

    private String getEventObject(String tran){
        for(Object key:MCMmessageMangers.keySet()){
            Log.d(TAG,"name: " + key.toString());
            MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
            for(int i = 0; i < manager.messageHandlers.size();i++){
                if(tran.equals(manager.messageHandlers.get(i).getTransaction())){
                    return key.toString();
                }
            }
        }
        return null;
    }

    @Override
    public void onWebSocketClose() {
        Log.d(TAG,"WebSocketClosed");
        WSCState = ConnectionState.CLOSED;
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    @Override
    public void startService(String s) {
        if(s.equals("echo-service")){
            createMCWebSocketChannel("echo-service");
        }else if(s.equals("play-service")){
            createMCWebSocketChannel("play-service");
        }else if(s.equals("record-service")){
            createMCWebSocketChannel(s);
        }
    }

    @Override
    public void sendOfferSdp(final String key, final SessionDescriptionInfo sdp) {
        Log.d(TAG,"key = " + key + "  sendOfferSdp");
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject upjson = new JSONObject();
                MCMessageManager.MessageHandler offer = new MCMessageManager.MessageHandler("message");
                MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
                manager.messageHandlers.add(offer);
                jsonPut(upjson,"mms",offer.getMms());
                jsonPut(upjson,"transaction",offer.getTransaction());
                jsonPut(upjson,"session_id",manager.session_id);
                jsonPut(upjson,"handle_id",manager.handle_id);
                JSONObject body = new JSONObject();
                if(manager.service_type.equals("RECORD")){
                    jsonPut(body,"request","record");
                    jsonPut(body,"name","shitest");
                }else{
                    jsonPut(body,"audio",true);
                    jsonPut(body,"video",true);
                }
                jsonPut(upjson,"body",body);
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "offer");
                jsonPut(upjson,"jsep",json);
                Log.d(TAG,"C->S :" + upjson.toString());
                wsClient.send(upjson.toString());
            }
        });
    }

    @Override
    public void sendAnswerSdp(final String key, final SessionDescriptionInfo sdp) {
        Log.d(TAG,"key = " + key + "  sendAnswerSdp");
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject upjson = new JSONObject();
                MCMessageManager.MessageHandler offer = new MCMessageManager.MessageHandler("message");
                MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
                manager.messageHandlers.add(offer);
                jsonPut(upjson,"mms",offer.getMms());
                jsonPut(upjson,"transaction",offer.getTransaction());
                jsonPut(upjson,"session_id",manager.session_id);
                jsonPut(upjson,"handle_id",manager.handle_id);
                JSONObject body = new JSONObject();
                jsonPut(body,"request","start");
                jsonPut(upjson,"body",body);
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                jsonPut(upjson,"jsep",json);
                Log.d(TAG,"C->S :" + upjson.toString());
                wsClient.send(upjson.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidate(final String key, final IceCandidateInfo candidate) {
        Log.d(TAG,"sendLocalIceCandidate");
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject upjson = new JSONObject();
                MCMessageManager.MessageHandler offer = new MCMessageManager.MessageHandler("trickle");
                MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
                manager.messageHandlers.add(offer);
                jsonPut(upjson,"mms",offer.getMms());
                jsonPut(upjson,"transaction",offer.getTransaction());
                jsonPut(upjson,"session_id",manager.session_id);
                jsonPut(upjson,"handle_id",manager.handle_id);
                JSONObject json = new JSONObject();
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(json, "sdpMid", candidate.sdpMid);
                jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(upjson,"candidate",json);
                Log.d(TAG,"C->S: " + upjson.toString());

                wsClient.send(upjson.toString());

            }
        });
    }

    @Override
    public void sendLocalIceCandidateRemovals(String s, IceCandidateInfo[] iceCandidateInfos) {

    }

    @Override
    public void disconnectChannel(final String s) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectChannelInternal(s);
            }
        });
    }

    private void disconnectChannelInternal(final String key){
        MCMessageManager manager = (MCMessageManager) MCMmessageMangers.get(key);
        MCMessageManager.MessageHandler handler = new MCMessageManager.MessageHandler("destroy");
        manager.messageHandlers.add(handler);
        wsClient.disconnect(true,handler.getTransaction(),manager.session_id);
    }

    public void disconnectWSC(){
        handler.post(new Runnable() {
            @Override
            public void run() {
                wsClient.disconnectWS();
            }
        });
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (WSCState != ConnectionState.ERROR) {
                    WSCState = ConnectionState.ERROR;
                    //events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidateInfo candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidateInfo toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidateInfo(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
}
