package com.wpanther.document.intake.application.port.out;

import com.wpanther.document.intake.application.dto.event.DocumentReceivedTraceEvent;
import com.wpanther.document.intake.application.dto.event.StartSagaCommand;

public interface DocumentEventPublisher {
    void publishStartSagaCommand(StartSagaCommand command);
    void publishTraceEvent(DocumentReceivedTraceEvent event);
}
