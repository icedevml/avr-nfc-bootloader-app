package pl.icedev.nfcflasher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "NFCFlasher";
    private final String CMDS_FILE = "prog-commands.txt";

    private TextView msg;
    private ProgressBar progress;
    private TextView hex_fname;
    private Button select_hex;

    static final int PICK_HEX_FILE = 1;

    BroadcastReceiver apduMessageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FlasherHostApduService.ACTION_SEND_INFO)) {
                Log.i(TAG, "Received intent: " + intent.getStringExtra("meta"));

                String workMode = intent.getStringExtra("workMode");

                if (workMode.equals("error")) {
                    msg.setText("Communication error:\n" + intent.getStringExtra("errorDescr"));
                } else if (workMode.equals("idle")) {
                    msg.setText("Tap and hold phone against the device");
                } else if (workMode.equals("working")) {
                    int progressMax = intent.getIntExtra("progressMax", 0);
                    int progressCur = intent.getIntExtra("progressCur", 0);

                    if (progressMax != 0 && progressCur != 0) {
                        String progressText = Integer.toString(progressCur)
                                + " / " + Integer.toString(progressMax);
                        msg.setText("Working " + progressText);

                        progress.setProgress(progressCur);
                        progress.setMax(progressMax);
                    }
                } else if (workMode.equals("done")) {
                    msg.setText("Programming done");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        msg = findViewById(R.id.msg);
        progress = findViewById(R.id.progress);
        hex_fname = findViewById(R.id.hex_fname);
        select_hex = findViewById(R.id.select_hex);

        select_hex.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                Intent i = Intent.createChooser(intent, "File");
                startActivityForResult(i, PICK_HEX_FILE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_HEX_FILE && resultCode == RESULT_OK && data.getData() != null) {
            Log.i(TAG, "Picked data string: " + data.getDataString());

            String fileName;
            Cursor cursor = getContentResolver().query(data.getData(),
                    null, null, null, null);

            if (cursor != null && cursor.getCount() != 0) {
                deleteFile(CMDS_FILE);

                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                fileName = cursor.getString(columnIndex);

                hex_fname.setText(fileName);
            }

            if (cursor != null) {
                cursor.close();
            }

            try {
                ParcelFileDescriptor pfd = getContentResolver()
                        .openFileDescriptor(data.getData(), "r");

                InputStream is = new FileInputStream(pfd.getFileDescriptor());
                FileOutputStream outputStream;

                try {
                    outputStream = openFileOutput(CMDS_FILE, Context.MODE_PRIVATE);
                    HexConverter hc = new HexConverter();
                    int num_commands = hc.convertHexFile(is, outputStream);
                    outputStream.close();

                    Toast toast = Toast.makeText(this, "Succesfully generated " + Integer.toString(num_commands) + " bootloader commands.", Toast.LENGTH_SHORT);
                    toast.show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast toast = Toast.makeText(this, "Failed to load hex: " + e.getMessage(), Toast.LENGTH_SHORT);
                    toast.show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "File not found.");
                Toast toast = Toast.makeText(this, "IOException occurred when loading hex file.", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(apduMessageBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register local broadcast
        IntentFilter filter = new IntentFilter(FlasherHostApduService.ACTION_SEND_INFO);
        LocalBroadcastManager.getInstance(this).registerReceiver(apduMessageBroadcastReceiver, filter);
    }
}
