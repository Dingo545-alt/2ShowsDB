package Parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.concurrent.BlockingQueue;

public class CastSaxParser extends DefaultHandler implements Runnable {

    private final String xmlFilePath;
    private final BlockingQueue<DataRecords.DataRecord> queue;
    private final int consumerCount;

    private StringBuilder currentValue;
    private boolean insideM;
    private String  movieTitle;
    private String  actorName;

    private int produced, skipped, parseErrors;

    public CastSaxParser(String xmlFilePath,
                         BlockingQueue<DataRecords.DataRecord> queue,
                         int consumerCount) {
        this.xmlFilePath   = xmlFilePath;
        this.queue         = queue;
        this.consumerCount = consumerCount;
    }

    @Override
    public void run() {
        System.out.println("[CastParser] Starting: " + xmlFilePath);
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.newSAXParser().parse(new File(xmlFilePath), this);
        } catch (Exception e) {
            System.err.println("[CastParser] Fatal: " + e.getMessage());
        } finally {
            emitPoisonPills();
            System.out.printf("[CastParser] Done. produced=%d skipped=%d errors=%d%n",
                    produced, skipped, parseErrors);
        }
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) {
        currentValue = new StringBuilder();
        if ("m".equalsIgnoreCase(qName)) {
            insideM    = true;
            movieTitle = null;
            actorName  = null;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (currentValue != null) currentValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (!insideM) { currentValue = null; return; }
        String value = normalise(currentValue);

        switch (qName.toLowerCase()) {
            case "t": movieTitle = value; break;
            case "a": actorName  = value; break;
            case "m":
                insideM = false;
                if (movieTitle == null) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("cast",
                            "Missing movie title (actor was: '" + actorName + "')");
                } else if (actorName == null) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("cast",
                            "Missing actor name (movie was: '" + movieTitle + "')");
                } else if (isPlaceholderName(actorName)) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("cast",
                            "Placeholder actor '" + actorName + "' in movie '" + movieTitle + "'");
                } else {
                    enqueue(new DataRecords.CastRecord(movieTitle, actorName));
                }
        }
        currentValue = null;
    }

    @Override
    public void error(SAXParseException e) {
        parseErrors++;
        System.err.printf("[CastParser] Recoverable error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[casts XML] line %d: %s", e.getLineNumber(), e.getMessage()));

    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        parseErrors++;
        System.err.printf("[CastParser] FATAL error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[casts XML] FATAL line %d: %s", e.getLineNumber(), e.getMessage()));
        throw e;
    }

    private void enqueue(DataRecords.DataRecord r) {
        try { queue.put(r); produced++; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void emitPoisonPills() {
        for (int i = 0; i < consumerCount; i++)
            try { queue.put(DataRecords.CastRecord.POISON_PILL); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String normalise(StringBuilder sb) {
        if (sb == null) return null;
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isPlaceholderName(String name) {
        if (name == null) return true;
        String n = name.trim().toLowerCase();
        if (n.isEmpty()) return true;

        switch (n) {
            case "s a":
            case "sa":
            case "s.a.":
            case "a s":
            case "sa s":
            case "none":
            case "x x":
            case "z x":
                return true;
            default:
                return false;
        }
    }
}
