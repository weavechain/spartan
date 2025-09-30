// Copyright 2018 Developers of the Rand project.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// https://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or https://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.

package com.weavechain.zk.spartan.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ChaChaRng {

    private static final int BUF_BLOCKS = 4;
    private static final int BLOCK_WORDS = 16;

    public static class ChaCha {
        private static final int BUFSZ = 64; // Typically, BUFSZ is 64 for ChaCha20

        private static final int[] CONSTANT = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};
        private int[] state;

        public ChaCha(byte[] key, byte[] nonce) {
            if (key.length != 32) {
                throw new IllegalArgumentException("Key must be 32 bytes");
            }
            if (nonce.length != 12) {
                throw new IllegalArgumentException("Nonce must be 12 bytes");
            }
            this.state = initChaCha(key, nonce);
        }

        public void refill4(int drounds, int[] out) {
            refillWideImpl(this.state, drounds, out);
        }

        public void setBlockPos(long value) {
            setStreamParam(this.state, StreamParam.BLOCK, value);
        }

        public long getBlockPos() {
            return getStreamParam(this.state, StreamParam.BLOCK);
        }

        public void setNonce(long value) {
            setStreamParam(this.state, StreamParam.NONCE, value);
        }

        public long getNonce() {
            return getStreamParam(this.state, StreamParam.NONCE);
        }

        public int[] getSeed() {
            return Arrays.copyOfRange(this.state, 4, 12);
        }

        private static int[] initChaCha(byte[] key, byte[] nonce) {
            int[] state = new int[16];
            System.arraycopy(CONSTANT, 0, state, 0, CONSTANT.length);

            for (int i = 0; i < 8; i++) {
                state[4 + i] = bytesToInt(key, i * 4);
            }
            state[12] = 0; // Counter
            for (int i = 0; i < 3; i++) {
                state[13 + i] = bytesToInt(nonce, i * 4);
            }
            return state;
        }

        private static void refillWideImpl(int[] state, int drounds, int[] out) {
            int[] workingState = state.clone();
            for (int i = 0; i < drounds; i += 2) {
                quarterRound(workingState, 0, 4, 8, 12);
                quarterRound(workingState, 1, 5, 9, 13);
                quarterRound(workingState, 2, 6, 10, 14);
                quarterRound(workingState, 3, 7, 11, 15);
                quarterRound(workingState, 0, 5, 10, 15);
                quarterRound(workingState, 1, 6, 11, 12);
                quarterRound(workingState, 2, 7, 8, 13);
                quarterRound(workingState, 3, 4, 9, 14);
            }

            for (int i = 0; i < 16; i++) {
                workingState[i] += state[i];
            }

            for (int i = 0; i < workingState.length; i++) {
                intToBytes(out, workingState[i], i * 4);
            }

            state[12] += 1;
            if (state[12] == 0) {
                state[13] += 1;
            }
        }

        private static void quarterRound(int[] x, int a, int b, int c, int d) {
            x[a] += x[b]; x[d] = rotate(x[d] ^ x[a], 16);
            x[c] += x[d]; x[b] = rotate(x[b] ^ x[c], 12);
            x[a] += x[b]; x[d] = rotate(x[d] ^ x[a], 8);
            x[c] += x[d]; x[b] = rotate(x[b] ^ x[c], 7);
        }

        private static int rotate(int v, int n) {
            return (v << n) | (v >>> (32 - n));
        }

        private static int bytesToInt(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF)) |
                    ((bytes[offset + 1] & 0xFF) << 8) |
                    ((bytes[offset + 2] & 0xFF) << 16) |
                    ((bytes[offset + 3] & 0xFF) << 24);
        }

        private static void intToBytes(int[] output, int value, int offset) {
            output[offset] = value;
        }

        private static void setStreamParam(int[] state, StreamParam param, long value) {
            if (param == StreamParam.BLOCK) {
                state[12] = (int) value;
            } else if (param == StreamParam.NONCE) {
                state[13] = (int) (value & 0xFFFFFFFF);
                state[14] = (int) ((value >> 32) & 0xFFFFFFFF);
            }
        }

        private static long getStreamParam(int[] state, StreamParam param) {
            if (param == StreamParam.BLOCK) {
                return state[12] & 0xFFFFFFFFL;
            } else if (param == StreamParam.NONCE) {
                return ((long) state[14] << 32) | (state[13] & 0xFFFFFFFFL);
            }
            return 0;
        }

        private enum StreamParam {
            BLOCK,
            NONCE
        }
    }

    static class ChaChaCore {
        private ChaCha state;

        ChaChaCore(byte[] seed) {
            this.state = new ChaCha(seed, new byte[12]);
        }

        void generate(int[] results) {
            int[] output = new int[64];
            this.state.refill4(10, output);
            for (int i = 0; i < 64; i++) {
                results[i] = output[i];
            }
        }

        void setBlockPos(long blockPos) {
            this.state.setBlockPos(blockPos);
        }

        long getBlockPos() {
            return this.state.getBlockPos();
        }

        void setNonce(long nonce) {
            this.state.setNonce(nonce);
        }

        long getNonce() {
            return this.state.getNonce();
        }

        int[] getSeed() {
            return this.state.getSeed();
        }
    }

    static class BlockRng {
        private ChaChaCore core;
        private int[] buffer;
        private int index;

        BlockRng(ChaChaCore core) {
            this.core = core;
            this.buffer = new int[64];
            this.index = 64;
        }

        void generateAndSet(int newIndex) {
            this.core.generate(this.buffer);
            this.index = newIndex;
        }

        int nextU32() {
            if (this.index >= 64) {
                this.core.generate(this.buffer);
                this.index = 0;
            }
            return this.buffer[this.index++];
        }

        long nextU64() {
            return (((long) nextU32()) & 0xffffffffL) | (((long) nextU32()) << 32);
        }

        void fillBytes(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.remaining() >= 4) {
                buffer.putInt(nextU32());
            }
            if (buffer.remaining() > 0) {
                byte[] temp = new byte[4];
                ByteBuffer tempBuffer = ByteBuffer.wrap(temp).order(ByteOrder.LITTLE_ENDIAN);
                tempBuffer.putInt(nextU32());
                tempBuffer.flip();
                buffer.put(temp, 0, buffer.remaining());
            }
        }

        boolean tryFillBytes(byte[] bytes) {
            try {
                fillBytes(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private BlockRng rng;

    public ChaChaRng(byte[] seed) {
        this.rng = new BlockRng(new ChaChaCore(seed));
    }

    public int nextU32() {
        return this.rng.nextU32();
    }

    public long nextU64() {
        return this.rng.nextU64();
    }

    public void fillBytes(byte[] bytes) {
        this.rng.fillBytes(bytes);
    }

    boolean tryFillBytes(byte[] bytes) {
        return this.rng.tryFillBytes(bytes);
    }

    long getWordPos() {
        long bufStartBlock = this.rng.core.getBlockPos() - BUF_BLOCKS;
        long bufOffsetWords = this.rng.index;
        long bufOffsetBlocks = bufOffsetWords / BLOCK_WORDS;
        long blockOffsetWords = bufOffsetWords % BLOCK_WORDS;
        long posBlock = bufStartBlock + bufOffsetBlocks;
        return posBlock * BLOCK_WORDS + blockOffsetWords;
    }

    void setWordPos(long wordOffset) {
        long block = wordOffset / BLOCK_WORDS;
        this.rng.core.setBlockPos(block);
        this.rng.generateAndSet((int) (wordOffset % BLOCK_WORDS));
    }

    void setStream(long stream) {
        this.rng.core.setNonce(stream);
        if (this.rng.index != 64) {
            long wp = this.getWordPos();
            this.setWordPos(wp);
        }
    }

    long getStream() {
        return this.rng.core.getNonce();
    }

    int[] getSeed() {
        return this.rng.core.getSeed();
    }

    public static void main(String[] args) {
        try {
            // Initialize the seed with 32 zero bytes
            byte[] seed = new byte[32];
            Arrays.fill(seed, (byte) 0);

            // Create a ChaChaRng instance with the seed
            ChaChaRng rng = new ChaChaRng(seed);

            // Generate 32 random bytes
            byte[] results = new byte[32];
            rng.fillBytes(results);

            // The expected output to be verified against
            byte[] expected = {
                    (byte) 118, (byte) 184, (byte) 224, (byte) 173, (byte) 160, (byte) 241, (byte) 61, (byte) 144,
                    (byte) 64, (byte) 93, (byte) 106, (byte) 229, (byte) 83, (byte) 134, (byte) 189, (byte) 40,
                    (byte) 189, (byte) 210, (byte) 25, (byte) 184, (byte) 160, (byte) 141, (byte) 237, (byte) 26,
                    (byte) 168, (byte) 54, (byte) 239, (byte) 204, (byte) 139, (byte) 119, (byte) 13, (byte) 199
            };

            //Truth.assertThat(results).isEqualTo(expected);

            // Output results for verification
            System.out.println("Results: " + Arrays.toString(results));
            System.out.println("Expected: " + Arrays.toString(expected));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
