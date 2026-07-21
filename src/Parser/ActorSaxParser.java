package Parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.concurrent.BlockingQueue;

public class ActorSaxParser extends DefaultHandler implements Runnable {
    private final String xmlFilePath;
    private final BlockingQueue<DataRecords.DataRecord> queue;
    private final int consumerCount;

    private StringBuilder currentValue;
    private boolean insideActor;
    private String  stageName;
    private Integer birthYear;

    private int produced, skipped, parseErrors;

    public ActorSaxParser(String xmlFilePath,
                          BlockingQueue<DataRecords.DataRecord> queue,
                          int consumerCount) {
        this.xmlFilePath   = xmlFilePath;
        this.queue         = queue;
        this.consumerCount = consumerCount;
    }

    @Override
    public void run() {
        System.out.println("[ActorParser] Starting: " + xmlFilePath);
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.newSAXParser().parse(new File(xmlFilePath), this);
        } catch (Exception e) {
            System.err.println("[ActorParser] Fatal: " + e.getMessage());
        } finally {
            emitPoisonPills();
            System.out.printf("[ActorParser] Done. produced=%d skipped=%d errors=%d%n",
                    produced, skipped, parseErrors);
        }
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) {
        currentValue = new StringBuilder();
        if ("actor".equalsIgnoreCase(qName)) {
            insideActor = true;
            stageName   = null;
            birthYear   = null;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (currentValue != null) currentValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (!insideActor) { currentValue = null; return; }
        String value = normalise(currentValue);

        switch (qName.toLowerCase()) {
            case "stagename": stageName = value;               break;
            case "dob":       birthYear = parseIntSafe(value); break;

            case "actor":
                insideActor = false;
                if (stageName == null || stageName.isEmpty()) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("actor",
                            "Skipped: missing stagename (birth_year was '" + birthYear + "')");
                    break;
                }

                enqueue(new DataRecords.ActorRecord(stageName, birthYear));
                break;
        }
        currentValue = null;
    }

    @Override
    public void error(SAXParseException e) {
        parseErrors++;
        System.err.printf("[ActorParser] Recoverable error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[actors XML] line %d: %s", e.getLineNumber(), e.getMessage()));
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        parseErrors++;
        System.err.printf("[ActorParser] FATAL error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[actors XML] FATAL line %d: %s", e.getLineNumber(), e.getMessage()));
        throw e;
    }

    private void enqueue(DataRecords.DataRecord r) {
        try { queue.put(r); produced++; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void emitPoisonPills() {
        for (int i = 0; i < consumerCount; i++)
            try { queue.put(DataRecords.ActorRecord.POISON_PILL); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String normalise(StringBuilder sb) {
        if (sb == null) return null;
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.length() < 4) {
            InconsistencyLogger.INSTANCE.log("actor", "Unparseable birth year: '" + s + "'");
            return null;
        }

        String first4 = trimmed.substring(0, 4);

        // Must be 4 digits — rejects "19xx", "20XX", "abcd", etc.
        if (!first4.matches("\\d{4}")) {
            InconsistencyLogger.INSTANCE.log("actor", "Unparseable birth year: '" + s + "'");
            return null;
        }


        try {
            return Integer.parseInt(first4);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
