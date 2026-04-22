package com.tradetracker.common.exception;

import java.util.UUID;

/** Base class for all TradeTracker domain exceptions. */
public abstract class TradeTrackerException extends RuntimeException {
    protected TradeTrackerException(String message) { super(message); }
    protected TradeTrackerException(String message, Throwable cause) { super(message, cause); }
}
