package com.tradetracker.portfolio.matching;

public class InsufficientHoldingsException extends RuntimeException {
    public InsufficientHoldingsException(String msg) { super(msg); }
}
