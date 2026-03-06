package com.wpanther.document.intake.domain.port.out;

import com.wpanther.document.intake.domain.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.domain.event.StartSagaCommand;

public interface DocumentEventPublisher {
    void publishStartSagaCommand(StartSagaCommand command);
    void publishTraceEvent(DocumentReceivedTraceEvent event);
}
