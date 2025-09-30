package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PolyCommitmentBlinds {

    private final List<Scalar> blinds;
}
