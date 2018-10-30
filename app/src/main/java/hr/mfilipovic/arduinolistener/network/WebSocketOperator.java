package hr.mfilipovic.arduinolistener.network;

import okio.ByteString;

public interface WebSocketOperator {
    void sent(boolean send, String payload);

    void opened();

    void received(String message);

    void received(ByteString msgBytes);

    void closing(int code, String reason);

    void closed(int code, String reason);

    void failed(String message);
}
