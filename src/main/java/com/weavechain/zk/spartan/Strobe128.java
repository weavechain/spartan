package com.weavechain.zk.spartan;

import com.weavechain.zk.spartan.util.Keccak;
import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Strobe128 {

    //Partial Strobe implementation based on Merlin

    private static final int STROBE_R = 166;

    private static final byte FLAG_I = 1;
    private static final byte FLAG_A = 1 << 1;
    private static final byte FLAG_C = 1 << 2;
    private static final byte FLAG_T = 1 << 3;
    private static final byte FLAG_M = 1 << 4;
    private static final byte FLAG_K = 1 << 5;

    private final AlignedKeccakState state = new AlignedKeccakState();

    private int pos;

    private int posBegin;

    private byte curFlags;

    public Strobe128(byte[] protocolLabel) {
        byte[] initialState = new byte[200];
        initialState[0] = 1;
        initialState[1] = (byte) (STROBE_R + 2);
        initialState[2] = 1;
        initialState[3] = 0;
        initialState[4] = 1;
        initialState[5] = 96;
        System.arraycopy("STROBEv1.0.2".getBytes(), 0, initialState, 6, 12);

        long[] newState = bytesToLongs(initialState);
        Keccak.f1600(newState);
        this.state.setState(longsToBytes(newState));

        this.pos = 0;
        this.posBegin = 0;
        this.curFlags = 0;
        metaAd(protocolLabel, false);
    }

    private static long[] bytesToLongs(byte[] state) {
        int len = state.length / Long.BYTES;
        long[] result = new long[len];
        for (int i = 0; i < len; i++) {
            result[i] = bytesToLong(state, i * Long.BYTES);
        }
        return result;
    }

    private static byte[] longsToBytes(long[] state) {
        int len = state.length * Long.BYTES;
        byte[] result = new byte[len];
        for (int i = 0; i < state.length; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(state[i]);
            buffer.flip();

            System.arraycopy(buffer.array(), 0, result, i * Long.BYTES, Long.BYTES);
        }
        return result;
    }

    private static long bytesToLong(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff) |
                (((long) bytes[offset + 1] & 0xff) << 8) |
                (((long) bytes[offset + 2] & 0xff) << 16) |
                (((long) bytes[offset + 3] & 0xff) << 24) |
                (((long) bytes[offset + 4] & 0xff) << 32) |
                (((long) bytes[offset + 5] & 0xff) << 40) |
                (((long) bytes[offset + 6] & 0xff) << 48) |
                (((long) bytes[offset + 7] & 0xff) << 56);
    }

    private void runF() {
        state.xor(pos, (byte)posBegin);
        state.xor(pos + 1, (byte)0x04);
        state.xor(STROBE_R + 1, (byte)0x80);

        long[] newState = bytesToLongs(state.getState());
        Keccak.f1600(newState);
        this.state.setState(longsToBytes(newState));

        pos = 0;
        posBegin = 0;
    }

    private void absorb(byte[] data) {
        for (byte b : data) {
            absorb(b);
        }
    }

    private void absorb(byte b) {
        state.xor(pos, b);
        pos++;

        if (pos == STROBE_R) {
            runF();
        }
    }

    private void overwrite(byte[] data) {
        for (byte b : data) {
            state.set(pos, b);
            pos++;
            if (pos == STROBE_R) {
                runF();
            }
        }
    }

    private void squeeze(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = state.get(pos);
            state.set(pos, (byte)0);
            pos++;
            if (pos == STROBE_R) {
                runF();
            }
        }
    }

    private void beginOp(byte flags, boolean more) {
        if (more) {
            if (curFlags != flags) {
                throw new IllegalArgumentException("Tried to continue op " + Integer.toBinaryString(curFlags) + " but changed flags to " + Integer.toBinaryString(flags));
            }
            return;
        }

        if ((flags & FLAG_T) != 0) {
            throw new IllegalArgumentException("Used unsupported T flag");
        }

        int oldBegin = posBegin;
        posBegin = (pos + 1) & 0xFF;
        curFlags = flags;
        absorb((byte)oldBegin);
        absorb(flags);

        if ((flags & (FLAG_C | FLAG_K)) != 0 && pos != 0) {
            runF();
        }
    }

    public void metaAd(byte[] data, boolean more) {
        beginOp((byte) (FLAG_M | FLAG_A), more);
        absorb(data);
    }

    public void ad(byte[] data, boolean more) {
        beginOp(FLAG_A, more);
        absorb(data);
    }

    public void prf(byte[] data, boolean more) {
        beginOp((byte) (FLAG_I | FLAG_A | FLAG_C), more);
        squeeze(data);
    }

    public void key(byte[] data, boolean more) {
        beginOp((byte) (FLAG_A | FLAG_C), more);
        overwrite(data);
    }

    @Override
    public String toString() {
        return "Strobe128: STATE OMITTED";
    }

    @Getter
    @Setter
    private static class AlignedKeccakState {

        private byte[] state;

        public AlignedKeccakState() {
            this.state = new byte[200];
        }

        public byte get(int idx) {
            return state[idx];
        }

        public void set(int idx, byte val) {
            state[idx] = val;
        }

        public void xor(int idx, byte val) {
            state[idx] ^= val;
        }
    }
}
