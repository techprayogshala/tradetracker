package com.tradetracker.notification.service;

/** Rendered email ready for dispatch. */
public record EmailContent(String subject, String htmlBody) {}
