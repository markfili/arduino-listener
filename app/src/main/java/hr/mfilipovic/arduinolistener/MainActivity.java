package hr.mfilipovic.arduinolistener;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaCodec;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import hr.mfilipovic.arduinolistener.network.WebSocketCommunicator;
import hr.mfilipovic.arduinolistener.network.WebSocketOperator;
import okio.ByteString;

public class MainActivity extends AppCompatActivity implements WebSocketOperator {

    private static final String TAG = "MainActivity";
    private static final int BAUD_RATE = 9600;

    UsbManager mUsbManager;
    UsbDevice mUsbDevice;
    UsbDeviceConnection mUsbDeviceConnection;

    int[] videos;

    private MediaSource[] mMediaSources;
    SimpleExoPlayer exoPlayer;
    private File mLogFile;
    private OutputStreamWriter mFileWriter;
    private TextView mLogView;
    private WebSocketCommunicator mCommunicator;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initLogs();
        setupUsb();
        initData();
        initSocket();
        initExo(getApplicationContext());
        handleMessage(5);
    }

    private void initSocket() {
        String url = "ws://172.17.41.209:3001/"; //"wss://echo.websocket.org";
        mCommunicator = new WebSocketCommunicator();
        mCommunicator.operator(this)
                .url(url)
                .build();
    }

    private void initLogs() {
        try {
            Logger logger = new Logger(getApplicationContext());
            mLogFile = logger.createNewLogFile();
            mLogView = findViewById(R.id.log_view);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initData() {
        videos = new int[]{R.raw.a, R.raw.b, R.raw.c, R.raw.d, R.raw.e, R.raw.idle, R.raw.call, R.raw.show};
        mMediaSources = new MediaSource[videos.length];
        for (int i = 0; i < videos.length; i++) {
            try {
                DataSpec dataSpec = new DataSpec(
                        RawResourceDataSource.buildRawResourceUri(videos[i]));

                final RawResourceDataSource rawResourceDataSource = new RawResourceDataSource(getApplicationContext());
                rawResourceDataSource.open(dataSpec);

                DataSource.Factory factory = new DataSource.Factory() {
                    @Override
                    public DataSource createDataSource() {
                        return rawResourceDataSource;
                    }
                };

                MediaSource videoSource = new ExtractorMediaSource.Factory(factory).createMediaSource(rawResourceDataSource.getUri());

                mMediaSources[i] = new LoopingMediaSource(videoSource);
            } catch (RawResourceDataSource.RawResourceDataSourceException e) {
                e.printStackTrace();
            }
        }
    }

    private void initExo(Context context) {
        final PlayerView playerView = findViewById(R.id.exo_player);
        final TextureView textureView = findViewById(R.id.texture_view);
        playerView.setResizeMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Matrix matrix = new Matrix();
                RectF dst = new RectF(0, 0, 1920, 1080);
                RectF screen = new RectF(dst);
                matrix.postRotate(270, screen.centerX(), screen.centerY());
                matrix.mapRect(dst);
                RectF src = new RectF(0, 0, 1080, 1920);
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
                matrix.mapRect(src);
                matrix.setRectToRect(screen, src, Matrix.ScaleToFit.FILL);
                matrix.postRotate(270, screen.centerX(), screen.centerY());
//                matrix.setRotate(270, getResources().getDisplayMetrics().widthPixels / 2, getResources().getDisplayMetrics().heightPixels / 2);
                textureView.setTransform(matrix);
                exoPlayer.setVideoTextureView(textureView);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector());
        playerView.setPlayer(exoPlayer);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        if (mFileWriter != null) {
            try {
                mFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFileWriter = null;
        }
    }

    private void setupUsb() {
        mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
        if (mUsbManager != null) {
            HashMap<String, UsbDevice> devices = mUsbManager.getDeviceList();
            for (Map.Entry<String, UsbDevice> device : devices.entrySet()) {
                UsbDevice usbDevice = device.getValue();
                String deviceName = usbDevice.getDeviceName();
                int productId = usbDevice.getProductId();
                int vendorId = usbDevice.getVendorId();
                Log.i(TAG, "setupUsb: device found\n" + deviceName + " " + productId + " " + vendorId);
                if (productId == 60000 && vendorId == 4292) {
                    Log.i(TAG, "setupUsb: found Croduino!");
                    mUsbDevice = usbDevice;
                    connectToCroduino();
                    break;
                }
            }
            if (mUsbDevice == null) {
                Toast.makeText(getBaseContext(), "Croduino not connected.", Toast.LENGTH_LONG).show();
                Log.i(TAG, "setupUsb: Croduino not connected.");
            }
        }
    }

    private void connectToCroduino() {
        if (mUsbDevice != null) {
            mUsbDeviceConnection = mUsbManager.openDevice(mUsbDevice);
            if (mUsbDeviceConnection != null) {
                UsbSerialDevice usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mUsbDeviceConnection);
                if (usbSerialDevice != null) {
                    if (usbSerialDevice.open()) {
                        usbSerialDevice.setBaudRate(BAUD_RATE);
                        usbSerialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                        usbSerialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                        usbSerialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                        usbSerialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                        usbSerialDevice.read(mUsbReadCallback);
                        Log.i(TAG, "setupUsb: Serial port open.");
                    } else {
                        Log.e(TAG, "setupUsb: Serial port could not be opened, maybe an I/O error or it CDC driver was chosen it does not really fit");
                    }
                } else {
                    Log.e(TAG, "setupUsb: No driver for given device, even generic CDC driver could not be loaded");
                }
            } else {
                Log.i(TAG, "setupUsb: Failed to establish USB connection");
            }
        } else {
            Log.i(TAG, "setupUsb: Usb device not found.");
        }
    }

    private class HandleMessageRunnable implements Runnable {

        private String msgString;

        public void handle(String msg) {
            this.msgString = msg;
        }

        @Override
        public void run() {
            Log.i(TAG, "onReceivedData: " + msgString);
            if (msgString != null) {
                String trimmed = msgString.trim();
                if (!trimmed.equals("")) {
                    if (TextUtils.isDigitsOnly(trimmed)) {
                        int msg = Math.abs(Integer.parseInt(trimmed));
                        handleMessage(msg);
                    }
                }
            }
        }
    }

    private HandleMessageRunnable mMessageRunnable;

    private UsbSerialInterface.UsbReadCallback mUsbReadCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] bytes) {
            String msgString = new String(bytes);
            if (!TextUtils.isEmpty(msgString)) {
                if (mMessageRunnable == null) {
                    mMessageRunnable = new HandleMessageRunnable();
                }
                mMessageRunnable.handle(msgString);
                runOnUiThread(mMessageRunnable);
            }
        }
    };

    int lastMsg;

    private void handleMessage(int msg) {
        if (msg != lastMsg) {
            String date = sdf.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());
            String log = String.format(Locale.getDefault(), "%d,%s\n", msg, date);
            writeToLog(log);
            writeDebug(log);
            sendMessage(msg, date);
            switchExoPlayerSource(msg);
            lastMsg = msg;
        }
    }

    JSONObject message = new JSONObject();

    private void sendMessage(int msg, String date) {
        try {
            message.put("sensor", msg);
            message.put("timestamp", date);
            mCommunicator.send(message.toString());
            message.remove("sensor");
            message.remove("timestamp");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    int rowCount;

    private void writeToLog(String log) {
        try {
            mFileWriter = new OutputStreamWriter(new FileOutputStream(mLogFile, true));
            mFileWriter.write(log);
            mFileWriter.flush();
            mFileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                mFileWriter.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void writeDebug(String log) {
        if (mLogView.getVisibility() == View.VISIBLE) {
            String logViewString = mLogView.getText().toString();
            if (rowCount > mLogView.getMaxLines() - 1) {
                logViewString = "";
                rowCount = 0;
            }
            ++rowCount;
            mLogView.setText(String.format("%s%s", logViewString, log));
        }
    }

    private void switchExoPlayerSource(int msg) {
        if (msg < mMediaSources.length) {
            exoPlayer.prepare(mMediaSources[msg]);
            exoPlayer.setPlayWhenReady(true);
        }
    }

    boolean isPendingRetry;
    Handler mRetryHandler = new Handler();
    Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            mCommunicator.reconnect();
            isPendingRetry = false;
        }
    };

    private void retry() {
        mRetryHandler.postDelayed(retryRunnable, 2000);
    }

    @Override
    public void sent(boolean success, String payload) {
        Log.i(TAG, "sent: " + (success ? "yes " : "no ") + payload);
        if (!success && !isPendingRetry) {
            Log.i(TAG, "retrying connection");
            retry();
            isPendingRetry = true;
        }
    }

    @Override
    public void opened() {
        Log.i(TAG, "opened: Socket open.");
    }

    @Override
    public void received(String message) {
        Log.i(TAG, "received: " + message);
    }

    @Override
    public void received(ByteString msgBytes) {
        Log.i(TAG, "received: " + msgBytes);
    }

    @Override
    public void closing(int code, String reason) {
        Log.i(TAG, "closing: " + code + " " + reason);
    }

    @Override
    public void closed(int code, String reason) {
        Log.i(TAG, "closed: " + code + " " + reason);
    }

    @Override
    public void failed(String message) {
        Log.i(TAG, "failed: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCommunicator.destroy();
    }
}
