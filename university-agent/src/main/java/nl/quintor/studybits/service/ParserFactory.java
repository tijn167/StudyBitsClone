package nl.quintor.studybits.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ParserFactory {

    public Parser getParser(String system) {
        if (system == null) {
            return null;
        }
        if (system.equalsIgnoreCase("Progress")) {
            return ProgressCreateService();
        }
        if (system.equalsIgnoreCase("Osiris")) {
            log.debug("Osiris parsen creëren");
            return OsirisCreateService();
        }
        return null;

    }

    public OsirisParser OsirisCreateService() {
        return new OsirisParser("OsirisParser");
    }

    public OsirisParser ProgressCreateService() {
        return new OsirisParser("ProgressParser");
    }
}
