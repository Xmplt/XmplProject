package me.xmpl.reputation.model;

public enum VoteType {
    LIKE(1),
    DISLIKE(-1);

    private final int pointValue;

    VoteType(int pointValue) {
        this.pointValue = pointValue;
    }

    public int getPointValue() {
        return pointValue;
    }
}