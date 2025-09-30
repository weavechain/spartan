package com.weavechain.zk.spartan;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Utils {

    public static long nextPow2(long v) {
        return 1L << (v == 0 ? 0 : 64 - Long.numberOfLeadingZeros(v - 1));
    }

    public static long log2(Long v) {
        long l = v == 0 ? 0 : 64 - Long.numberOfLeadingZeros(v - 1);
        if (1L << l == v) {
            return l;
        } else {
            return l - 1;
        }
    }
}
