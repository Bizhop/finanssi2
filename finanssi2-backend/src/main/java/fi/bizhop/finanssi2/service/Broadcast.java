package fi.bizhop.finanssi2.service;

/** A message for the subscribers of a STOMP topic; sent once the change it reports is committed */
public record Broadcast(String topic, Object payload) {}
