package pl.icedev.nfcflasher;

import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import static pl.icedev.nfcflasher.Util.bytesToHex;
import static pl.icedev.nfcflasher.Util.hexStringToByteArray;

public class FlasherHostApduService extends HostApduService {

    private final String TAG = "NFCFlasher";
    private final String CMDS_FILE = "prog-commands.txt";

    public static final String ACTION_SEND_INFO = "ACTION_SEND_INFO";
    private int messageCounter = 0;
    private ArrayList<String> cmds;

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        Log.i(TAG, "APDU RECEIVED");
        Log.i(TAG, bytesToHex(apdu));

        if (selectAidApdu(apdu)) {
            Log.i(TAG, "Application selected");
            messageCounter = 0;
            cmds = new ArrayList<>();

            try {
                InputStream is = openFileInput(CMDS_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                while (true) {
                    String line = reader.readLine();

                    if (line == null) {
                        break;
                    } else if (!line.isEmpty()) {
                        cmds.add(line);
                    }
                }
            } catch (IOException e) {
                Context context = getApplicationContext();
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
                Intent metaIntent = new Intent(ACTION_SEND_INFO);
                metaIntent.putExtra("workMode", "error");
                metaIntent.putExtra("errorDescr", "Failed to read commands: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                lbm.sendBroadcast(metaIntent);

                return new byte[] { 0x00 };
            }

            Intent appIntent = new Intent(getApplicationContext(), MainActivity.class);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(appIntent);

            return getWelcomeMessage();
        }
        else if (apdu[1] == (byte)0xC2) {
            Context context = getApplicationContext();
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            Intent metaIntent = new Intent(ACTION_SEND_INFO);

            if (apdu[5] != (byte)0xAF || apdu[6] != 0x00) {
                metaIntent.putExtra("workMode", "error");
                metaIntent.putExtra("errorDescr", "Bootloader indicated error state.");
                lbm.sendBroadcast(metaIntent);
            } else if (messageCounter < cmds.size()) {
                metaIntent.putExtra("workMode", "working");
                metaIntent.putExtra("progressMax", cmds.size());
                metaIntent.putExtra("progressCur", messageCounter);
                lbm.sendBroadcast(metaIntent);

                Log.i(TAG, cmds.get(messageCounter));

                byte[] data = hexStringToByteArray(cmds.get(messageCounter));
                messageCounter++;
                return data;
            } else {
                metaIntent.putExtra("workMode", "done");
                lbm.sendBroadcast(metaIntent);
            }
        }

        return new byte[] { (byte)0x00 };
    }

    private byte[] getWelcomeMessage() {
        return new byte[] { (byte)0xC0, (byte)0xFF, (byte)0xEE };
    }

    private boolean selectAidApdu(byte[] apdu) {
        return apdu.length >= 2 && apdu[0] == (byte)0 && apdu[1] == (byte)0xa4;
    }

    @Override
    public void onDeactivated(int reason) {
        Log.i(TAG, "Deactivated: " + reason);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
