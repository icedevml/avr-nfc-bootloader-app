package pl.icedev.nfcflasher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static pl.icedev.nfcflasher.Util.bytesToHex;
import static pl.icedev.nfcflasher.Util.hexStringToByteArray;

class HexConverter {
    private ByteBuffer prog;
    private short base_addr;
    private short max_addr;

    // bootloader unlock password, keep secret
    private static String BOOTLOADER_PASSWORD_CMD = "37FFFFFFFFFFFFFFFF\n";

    private void readHexProgram(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        prog = ByteBuffer.allocate(128*1024);
        boolean addr_set = false;
        short cur_addr = 0;

        while (true) {
            String line = br.readLine();

            if (line == null) {
                break;
            }

            if (line.charAt(0) != ':') {
                continue;
            }

            byte[] bl = hexStringToByteArray(line.substring(1));

            // [len bytes] [lsb addr] [msb addr] [record type] [data...] [checksum]
            byte length = bl[0];
            short addr = ByteBuffer.wrap(new byte[] {bl[2], bl[1]})
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getShort();
            byte record = bl[3];

            if (record == (byte)0) {
                if (!addr_set) {
                    cur_addr = addr;
                    base_addr = addr;
                    addr_set = true;
                } else if (cur_addr != addr) {
                    throw new RuntimeException("Provided hex file has addressing gaps, which is not supported.");
                }

                for (int i = 0; i < length; i++) {
                    prog.put(bl[i+4]);
                    cur_addr++;
                }
            } else if (record == (byte)1) {
                break;
            }
        }

        prog.rewind();

        max_addr = (short) (cur_addr / 2);
    }

    private int writeBootloaderCommands(OutputStream os) throws IOException {
        int commands = 0;

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
        bw.write(BOOTLOADER_PASSWORD_CMD);
        commands++;

        short cur_addr = base_addr;
        int page_parts = 0;

        while (cur_addr < max_addr || page_parts != 0) {
            if (page_parts == 0) {
                byte[] enc_addr = ByteBuffer.allocate(2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(cur_addr)
                        .array();

                bw.write("AA" + bytesToHex(enc_addr) + "\n");
                commands++;
            }

            byte[] out = new byte[32];
            prog.get(out);

            bw.write("F1" + bytesToHex(new byte[] {(byte)(page_parts * 32)}) + "" + bytesToHex(out) + "\n");
            commands++;
            cur_addr += 16;
            page_parts += 1;

            if (page_parts >= 4) {
                page_parts = 0;
            }
        }

        bw.flush();
        bw.close();

        return commands;
    }

    int convertHexFile(InputStream is, OutputStream os) throws IOException {
        readHexProgram(is);
        return writeBootloaderCommands(os);
    }
}
