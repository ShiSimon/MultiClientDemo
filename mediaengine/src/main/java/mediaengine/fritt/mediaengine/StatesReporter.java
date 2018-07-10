package mediaengine.fritt.mediaengine;

import org.webrtc.StatsReport;

public class StatesReporter extends StatsReport {
    public StatesReporter(String id, String type, double timestamp, Value[] values) {
        super(id, type, timestamp, values);
    }
}
