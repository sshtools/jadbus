package com.sshtools.jadbus;

import java.util.Objects;

class Pair<A, B> {
    final A first;
    final B second;

    Pair(A _first, B _second) {
        first = _first;
        second = _second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public boolean equals(Object _obj) {
        if (this == _obj) {
            return true;
        }
        if (!(_obj instanceof Pair)) {
            return false;
        }
        Pair<?, ?> other = (Pair<?, ?>) _obj;
        return Objects.equals(first, other.first) && Objects.equals(second, other.second);
    }

}