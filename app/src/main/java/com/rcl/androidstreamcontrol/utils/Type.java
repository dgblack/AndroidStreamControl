package com.rcl.androidstreamcontrol.utils;

public enum Type {
    Unknown(0), Offer(1), Answer(2), Ice(3);
    private int val;

    private Type(int val) {
        this.val = val;
    }

    public int getVal() {
        return val;
    }
}

