package com.weavechain.zk.spartan.commit;

import com.weavechain.curves.PointFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

@Getter
@AllArgsConstructor
public class DerefsCommitment {

    private final PolyCommitment commOpsVal;

    public void pack(MessageBufferPacker packer) throws IOException {
        commOpsVal.pack(packer);
    }

    public static DerefsCommitment unpack(MessageUnpacker unpacker, PointFactory pointFactory) throws IOException {
        PolyCommitment commOpsVal = PolyCommitment.unpack(unpacker, pointFactory);
        return new DerefsCommitment(commOpsVal);
    }
}
