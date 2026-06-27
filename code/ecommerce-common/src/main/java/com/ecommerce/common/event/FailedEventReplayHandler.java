package com.ecommerce.common.event;

public interface FailedEventReplayHandler {

    String eventType();

    void replay(String eventPayload) throws Exception;
}
