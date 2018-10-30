package hr.mfilipovic.arduinolistener.network;

import android.support.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketCommunicator extends WebSocketListener {

    private WebSocketOperator mOperator;
    private String mUrl;
    private OkHttpClient mClient;
    private WebSocket mWebSocket;
    private Request mRequest;

    public WebSocketCommunicator operator(WebSocketOperator operator) {
        this.mOperator = operator;
        return this;
    }

    public WebSocketCommunicator url(String url) {
        this.mUrl = url;
        return this;
    }

    public void build() {
        this.mClient = new OkHttpClient.Builder().build();
        this.mRequest = new Request.Builder().url(mUrl).build();
        this.mWebSocket = this.mClient.newWebSocket(mRequest, this);
    }

    public void destroy() {
        this.mWebSocket.close(1000, "Normal closure. Goodbye!");
        this.mClient.dispatcher().executorService().shutdown();
        this.mClient = null;
    }

    public void send(String payload) {
        mOperator.sent(this.mWebSocket.send(payload), payload);
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        mOperator.opened();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        mOperator.received(text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        mOperator.received(bytes);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        mOperator.closing(code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        mOperator.closed(code, reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
        mOperator.failed(t.getMessage());
    }

    public void reconnect() {
        this.mWebSocket = this.mClient.newWebSocket(mRequest, this);
    }
}
