package com.weavechain.zk.spartan.util;

public class Keccak {

    private static final long[] ROUND_CONSTANTS = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808aL,
            0x8000000080008000L, 0x000000000000808bL, 0x0000000080000001L,
            0x8000000080008081L, 0x8000000000008009L, 0x000000000000008aL,
            0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
            0x000000008000808bL, 0x800000000000008bL, 0x8000000000008089L,
            0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
            0x000000000000800aL, 0x800000008000000aL, 0x8000000080008081L,
            0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    private static final int[] PI = { 10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1 };

    private static final int[] RHO = { 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44 };

    public static void f1600(long[] state) {
        if (state.length != 25) {
            throw new IllegalArgumentException("State array must be of length 25");
        }

        for (int round = 0; round < 24; round++) {
            long[] out = new long[5];

            // theta
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    out[x] ^= state[5 * y + x];
                }
            }

            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    long t1 = out[(x + 4) % 5];
                    long t2 = Long.rotateLeft(out[(x + 1) % 5], 1);
                    state[5 * y + x] ^= t1 ^ t2;
                }
            }

            // rho and pi
            long last = state[1];
            for (int x = 0; x < 24; x++) {
                long temp = state[PI[x]];
                state[PI[x]] = Long.rotateLeft(last, RHO[x]);
                last = temp;
            }

            // chi
            for (int yStep = 0; yStep < 5; yStep++) {
                int y = 5 * yStep;

                for (int x = 0; x < 5; x++) {
                    out[x] = state[y + x];
                }

                for (int x = 0; x < 5; x++) {
                    long t1 = ~out[(x + 1) % 5];
                    long t2 = out[(x + 2) % 5];
                    state[y + x] = out[x] ^ (t1 & t2);
                }
            }

            // iota
            state[0] ^= ROUND_CONSTANTS[round];
        }
    }

    public static void main(String[] args) {
        long[] state = new long[25];
        f1600(state);
        for (long l : state) {
            System.out.printf("%016x ", l);
        }
    }
}
