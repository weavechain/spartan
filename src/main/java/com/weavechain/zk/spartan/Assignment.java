package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Assignment {

    private final List<Scalar> assignment;

    public static Assignment createPadded(Assignment other, int len, ScalarFactory scalarFactory) {
        List<Scalar> s = new ArrayList<>(other.getAssignment());
        for (int i = other.getAssignment().size(); i < len; i++) {
            s.add(scalarFactory.zero());
        }
        return new Assignment(s);
    }
}
