package mediaengine.fritt.multiclientdemo;


import android.os.Handler;
import android.util.Log;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class HttpWebSocketClient {
    private static final String TAG = "HttpWebSocket";
    private final HttpWebSocketEvents events;
    private final Handler handler;
    private OkHttpClient client;
    private Request request;
    private WebSocket ws;
    private ChannelObserver wsObserver;
    private String wsServerUrl;
    private int port;
    private ChannelState state;
    private final Object closeEventLock = new Object();
    private boolean closeEvent;

    public enum ChannelState{NEW,CONNECTED,CLOSED,ERROR}

    public interface HttpWebSocketEvents{
        void onWebSocketEstablished();
        void onWebSocketMessage(final String message);
        void onWebSocketClose();
        void onWebSocketError(final String description);
    }

    public HttpWebSocketClient(Handler handler,HttpWebSocketEvents events){
        this.handler = handler;
        this.events = events;
        state = ChannelState.NEW;
    }

    public ChannelState getState() {
        return state;
    }

    public void connect(final String wsUrl,final int port){
        checkIfCalledOnValidThread();
        if(state != ChannelState.NEW){
            Log.e(TAG,"WebSocket is already connected");
            return;
        }
        wsServerUrl = wsUrl;
        this.port = port;
        closeEvent = false;
        List<Protocol> protocolList = new LinkedList<Protocol>(){};
        Protocol protocol = Protocol.HTTP_1_1;
        protocolList.add(protocol);

        final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(
                    java.security.cert.X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        } catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }catch (KeyManagementException e){
            e.printStackTrace();
        }

        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
        client = new OkHttpClient.Builder().retryOnConnectionFailure(true)
                .sslSocketFactory(sslContext.getSocketFactory())
                .hostnameVerifier(hostnameVerifier)
                .protocols(protocolList).build();
        wsObserver = new ChannelObserver();
        String url = "ws://" + wsServerUrl + ":" +port;
        url = "wss://10.0.1.116:6661";
        Log.d(TAG,"url = " + url);
        request = new Request.Builder().url(url).header("Sec-WebSocket-Protocol","mms").build();
        Log.d("WebSocket",request.toString());
        Log.d("WebSocket","headers:" + request.headers().toString());
        ws = client.newWebSocket(request,wsObserver);
        client.dispatcher().executorService().shutdown();
    }

    public void disconnect(){
        ws.cancel();
        synchronized (closeEventLock) {
            while (!closeEvent) {
                try {
                    closeEventLock.wait(1000);
                    break;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Wait error: " + e.toString());
                }
            }
        }
    }

    public void send(String message){
        checkIfCalledOnValidThread();
        ws.send(message);
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private class ChannelObserver extends WebSocketListener{
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            Log.d(TAG,"WebSocket Connection opened to "+ wsServerUrl + ":" + port);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    state = ChannelState.CONNECTED;
                    events.onWebSocketEstablished();
                }
            });
        }

        @Override
        public void onMessage(WebSocket webSocket, final String text) {
            super.onMessage(webSocket, text);
            Log.d(TAG,"S->C: " + text);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(state == ChannelState.CONNECTED){
                        events.onWebSocketMessage(text);
                    }
                }
            });
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);

            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: "
                    + state);
            synchronized (closeEventLock) {
                closeEvent = true;
                closeEventLock.notify();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != ChannelState.CLOSED) {
                        state = ChannelState.CLOSED;
                        events.onWebSocketClose();
                    }
                }
            });

        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
        }
    }
}
