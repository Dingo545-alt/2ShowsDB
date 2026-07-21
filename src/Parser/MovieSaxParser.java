package Parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;


public class MovieSaxParser extends DefaultHandler implements Runnable {
    private final String xmlFilePath;
    private final BlockingQueue<DataRecords.DataRecord> queue;
    private final int consumerCount;

    // parse state
    private StringBuilder currentValue;
    private boolean insideFilm;
    private boolean insideDirs;
    private boolean insideDir;
    private boolean insideCats;
    private boolean insideYearChild;

    // per-film
    private String       title;
    private String       yearText;
    private String       primaryDirector;
    private String       fallbackDirector;
    private boolean      directorFound;
    private List<String> genreCodes;

    // per-<dir>
    private String currentDirk;
    private String currentDirn;

    private int produced, skipped, parseErrors;

    public MovieSaxParser(String xmlFilePath,
                          BlockingQueue<DataRecords.DataRecord> queue,
                          int consumerCount) {
        this.xmlFilePath   = xmlFilePath;
        this.queue         = queue;
        this.consumerCount = consumerCount;
    }

    @Override
    public void run() {
        System.out.println("[MovieParser] Starting: " + xmlFilePath);
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.newSAXParser().parse(new File(xmlFilePath), this);
        } catch (Exception e) {
            System.err.println("[MovieParser] Fatal: " + e.getMessage());
        } finally {
            emitPoisonPills();
            System.out.printf("[MovieParser] Done. produced=%d skipped=%d errors=%d%n",
                    produced, skipped, parseErrors);
        }
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) {
        currentValue = new StringBuilder();
        switch (qName.toLowerCase()) {
            case "film":
                insideFilm = true; insideDirs = false; insideDir = false;
                insideCats = false; insideYearChild = false; directorFound = false;
                title = null; yearText = null;
                primaryDirector = null; fallbackDirector = null;
                genreCodes = new ArrayList<>();
                break;
            case "dirs": if (insideFilm) insideDirs = true; break;
            case "dir":
                if (insideDirs) { insideDir = true; currentDirk = null; currentDirn = null; }
                break;
            case "cats": if (insideFilm) insideCats = true; break;
            case "released": case "re-released": case "rereleased":
                if (insideFilm) insideYearChild = true;
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (!insideYearChild && currentValue != null)
            currentValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String value = normalise(currentValue);
        switch (qName.toLowerCase()) {

            case "t":
                // Capture title only at film level (not inside dirs, people, etc.)
                if (insideFilm && !insideDirs && !insideCats) title = value;
                break;

            case "year":
                if (insideFilm) yearText = value;
                break;
            case "released": case "re-released": case "rereleased":
                insideYearChild = false;
                break;

            case "dirk": if (insideDir) currentDirk = value; break;
            case "dirn": if (insideDir) currentDirn = value; break;
            case "dir":
                if (insideDir) {
                    if (!directorFound && "R".equalsIgnoreCase(currentDirk)
                            && currentDirn != null) {
                        primaryDirector = currentDirn;
                        directorFound   = true;
                    }
                    if (fallbackDirector == null && currentDirn != null)
                        fallbackDirector = currentDirn;
                    insideDir = false;
                }
                break;
            case "dirs": insideDirs = false; break;

            case "cat":
                if (insideCats && value != null) {
                    String code = value.trim();
                    if (!code.isEmpty()) genreCodes.add(code);
                }
                break;
            case "cats": insideCats = false; break;

            case "film":
                insideFilm = false;

                String dir = (primaryDirector != null) ? primaryDirector : fallbackDirector;
                Integer parsedYear = parseIntSafe(yearText);

                if (title == null) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("movie",
                            "Missing title; cannot insert (genres found: " + genreCodes + ")");
                    break;
                }
                if (parsedYear == null) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("movie",
                            "Skipped: title='" + title + "' has no valid year (got '" + yearText + "')");
                    break;
                }
                if (dir == null || dir.isEmpty()) {
                    skipped++;
                    InconsistencyLogger.INSTANCE.log("movie",
                            "Skipped: title='" + title + "' has no director");
                    break;
                }
                
                enqueue(new DataRecords.MovieRecord(title, parseIntSafe(yearText), dir, genreCodes));
                break;
        }
        currentValue = null;
    }

    @Override
    public void error(SAXParseException e) {
        parseErrors++;
        System.err.printf("[MovieParser] Recoverable error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[movies XML] line %d: %s", e.getLineNumber(), e.getMessage()));
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        parseErrors++;
        System.err.printf("[MovieParser] FATAL error line %d: %s%n",
                e.getLineNumber(), e.getMessage());
        InconsistencyLogger.INSTANCE.log("parse",
                String.format("[movies XML] FATAL line %d: %s", e.getLineNumber(), e.getMessage()));
        throw e;
    }

    private void enqueue(DataRecords.DataRecord r) {
        try { queue.put(r); produced++; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void emitPoisonPills() {
        for (int i = 0; i < consumerCount; i++)
            try { queue.put(DataRecords.MovieRecord.POISON_PILL); }
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
        if (trimmed.length() < 4) return null;

        String first4 = trimmed.substring(0, 4);

        // Must be 4 digits — rejects "19xx", "20XX", "abcd", etc.
        if (!first4.matches("\\d{4}")) return null;

        try {
            return Integer.parseInt(first4);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
