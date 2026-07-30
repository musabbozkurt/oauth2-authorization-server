package mb.oauth2authorizationserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.TableChangeDescription;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OracleCqnListener implements DatabaseChangeListener {

    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        log.info("Received CQN database event from Oracle: {}", event.getEventType());

        TableChangeDescription[] tableChanges = event.getTableChangeDescription();
        if (tableChanges == null || tableChanges.length == 0) {
            return;
        }

        try {
            log.info("Refreshing schedule jobs after integration table change");
        } catch (Exception e) {
            log.error("Failed to refresh schedule jobs after CQN event. Exceptions: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
