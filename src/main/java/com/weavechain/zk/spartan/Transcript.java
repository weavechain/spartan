package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.commit.R1CSCommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Transcript {

    public static final String MERLIN_PROTOCOL_LABEL = "Merlin v1.0";

    private static final byte[] EMPTY = new byte[0];

    private static final boolean DEBUG = false;

    private final Strobe128 strobe;

    private final ScalarFactory scalarFactory;

    private final PointFactory pointFactory;

    //Strobe128
    public Transcript(byte[] label, ScalarFactory scalarFactory, PointFactory pointFactory) {
        this.scalarFactory = scalarFactory;
        this.pointFactory = pointFactory;

        strobe = new Strobe128(MERLIN_PROTOCOL_LABEL.getBytes(StandardCharsets.UTF_8));
        appendMessage("dom-sep".getBytes(StandardCharsets.UTF_8), label);
    }

    public void appendMessage(byte[] label, byte[] message) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putInt(message.length);

        if (DEBUG) {
            StringBuilder hex = new StringBuilder();
            for (byte b : message) {
                hex.append(String.format("%02X ", b));
            }
            String bufHex = hex.toString().trim();
            System.out.println("> " + new String(label) + " " + bufHex.toString());
        }

        strobe.metaAd(label, false);
        strobe.metaAd(buff.array(), true);
        strobe.ad(message, false);
    }

    public void appendScalar(byte[] label, Scalar value) {
        appendMessage(label, value.toByteArray());
    }

    public void appendLong(byte[] label, Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(value);
        buffer.flip();
        appendMessage(label, buffer.array());
    }

    public void appendPoint(byte[] label, Point value) {
        appendMessage(label, value.toByteArray());
    }

    public void appendScalars(byte[] label, List<Scalar> values) {
        appendMessage(label, "begin_append_vector".getBytes(StandardCharsets.UTF_8));
        for (Scalar s : values) {
            appendScalar(label, s);
        }
        appendMessage(label, "end_append_vector".getBytes(StandardCharsets.UTF_8));
    }

    public void appendCommitment(byte[] label, PolyCommitment comm) {
        appendMessage(label, "poly_commitment_begin".getBytes(StandardCharsets.UTF_8));
        for (Point s : comm.getC()) {
            appendPoint("poly_commitment_share".getBytes(StandardCharsets.UTF_8), s);
        }
        appendMessage(label, "poly_commitment_end".getBytes(StandardCharsets.UTF_8));
    }

    public void appendCommitment(byte[] label, UniPoly poly) {
        appendMessage(label, "uni_poly_begin".getBytes(StandardCharsets.UTF_8));
        for (Scalar s : poly.getCoeffs()) {
            appendScalar("coeff".getBytes(StandardCharsets.UTF_8), s);
        }
        appendMessage(label, "uni_poly_end".getBytes(StandardCharsets.UTF_8));
    }

    public void appendCommitment(byte[] label, DerefsCommitment comm) {
        appendMessage("derefs_commitment".getBytes(StandardCharsets.UTF_8), "begin_derefs_commitment".getBytes(StandardCharsets.UTF_8));
        appendCommitment(label, comm.getCommOpsVal());
        appendMessage("derefs_commitment".getBytes(StandardCharsets.UTF_8), "end_derefs_commitment".getBytes(StandardCharsets.UTF_8));
    }

    public void appendCommitment(byte[] label, R1CSCommitment comm) {
        //appendMessage(label, EMPTY);
        appendLong("num_cons".getBytes(StandardCharsets.UTF_8), comm.getNumCons());
        appendLong("num_vars".getBytes(StandardCharsets.UTF_8), comm.getNumVars());
        appendLong("num_inputs".getBytes(StandardCharsets.UTF_8), comm.getNumInputs());
        appendCommitment("comm".getBytes(StandardCharsets.UTF_8), comm.getComm());
    }

    public void appendCommitment(byte[] label, SparseMatPolyCommitment comm) {
        //appendMessage(label, EMPTY);
        appendLong("batch_size".getBytes(StandardCharsets.UTF_8), comm.getBatchSize());
        appendLong("num_ops".getBytes(StandardCharsets.UTF_8), comm.getNumOps());
        appendLong("num_mem_cells".getBytes(StandardCharsets.UTF_8), comm.getNumMemCells());
        appendCommitment("comm_comb_ops".getBytes(StandardCharsets.UTF_8), comm.getCommCombOps());
        appendCommitment("comm_comb_mem".getBytes(StandardCharsets.UTF_8), comm.getCommCombMem());
    }

    public byte[] challengeBytes(byte[] label, int len) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putInt(len);

        byte[] result = new byte[len];
        strobe.metaAd(label, false);
        strobe.metaAd(buff.array(), true);
        strobe.prf(result, false);

        return result;
    }

    public Scalar challengeScalar(byte[] label) {
        byte[] out = challengeBytes(label, 64);

        if (DEBUG) {
            StringBuilder hex = new StringBuilder();
            for (byte b : out) {
                hex.append(String.format("%02X ", b));
            }
            String bufHex = hex.toString().trim();
            System.out.println(new String(label) + " " + bufHex.toString());
        }

        return scalarFactory.fromBytesModOrderWide(out);
    }

    public List<Scalar> challengeVector(byte[] label, long len) {
        List<Scalar> result = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            result.add(challengeScalar(label));
        }
        return result;
    }
}
