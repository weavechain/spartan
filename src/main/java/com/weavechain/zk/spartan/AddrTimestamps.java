package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class AddrTimestamps {

    private final List<List<Long>> opsAddrLong;

    private final List<DensePolynomial> opsAddr;

    private final List<DensePolynomial> readTs;

    private final DensePolynomial auditTs;

    public List<DensePolynomial> deref(List<Scalar> memVal) {
        List<DensePolynomial> result = new ArrayList<>();
        for (int i = 0; i < opsAddr.size(); i++) {
            result.add(derefMem(opsAddrLong.get(i), memVal));
        }

        return result;
    }

    public DensePolynomial derefMem(List<Long> addr, List<Scalar> memVal) {
        List<Scalar> result = new ArrayList<>();
        for (Long a : addr) {
            result.add(memVal.get(a.intValue()));
        }
        return DensePolynomial.create(result);
    }

    public static AddrTimestamps create(long numCells, long numOps, List<List<Long>> opsAddr, ScalarFactory scalarFactory) {
        List<Long> auditTs = new ArrayList<>(Collections.nCopies((int)numCells, 0L));

        List<DensePolynomial> opsAddrVec = new ArrayList<>();
        List<DensePolynomial> readTsVec = new ArrayList<>();

        for (List<Long> opsAddrInst : opsAddr) {
            List<Long> readTs = new ArrayList<>(Collections.nCopies((int)numOps, 0L));

            for (int i = 0; i < numOps; i++) {
                Long addr = opsAddrInst.get(i);
                if (addr >= numCells) {
                    throw new IllegalArgumentException("Invalid address");
                }

                Long rTs = auditTs.get(addr.intValue());
                readTs.set(i, rTs);

                Long wTs = rTs + 1;
                auditTs.set(addr.intValue(), wTs);
            }

            opsAddrVec.add(DensePolynomial.fromSize(opsAddrInst, scalarFactory));
            readTsVec.add(DensePolynomial.fromSize(readTs, scalarFactory));
        }

        return new AddrTimestamps(
                opsAddr,
                opsAddrVec,
                readTsVec,
                DensePolynomial.fromSize(auditTs, scalarFactory)
        );
    }
}
