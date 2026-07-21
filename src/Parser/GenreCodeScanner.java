package Parser;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.*;


/*
 * RUN THIS BEFORE THE MAIN XML.
 * This is to load every <cat> code in mains234.xml to see how many times each appear,
 * and whether it is already covered by the mapping below
 *
 * After reviewing the output, fill in the values with ???
 */
public class GenreCodeScanner extends DefaultHandler {
    private static final Map<String, String> KNOWN = new LinkedHashMap<>();
    static {
        // --- DRAMA & CONTEMPORARY ---
        KNOWN.put("Dram", "Drama"); KNOWN.put("DRam", "Drama"); KNOWN.put("DRAM", "Drama");
        KNOWN.put("dram", "Drama"); KNOWN.put("Drama", "Drama"); KNOWN.put("Dramd", "Drama");
        KNOWN.put("Dramn", "Drama"); KNOWN.put("Draam", "Drama"); KNOWN.put("DraM", "Drama");
        KNOWN.put("ram", "Drama"); KNOWN.put("Dram>", "Drama"); KNOWN.put("UnDr", "Drama");
        KNOWN.put("Ctxx", "Drama"); KNOWN.put("Ctxxx", "Drama"); KNOWN.put("Ctcxx", "Drama");
        KNOWN.put("txx", "Drama"); KNOWN.put("AvGa", "Drama"); KNOWN.put("Avant Garde", "Drama");
        KNOWN.put("Expm", "Drama"); KNOWN.put("Art Video", "Drama"); KNOWN.put("Allegory", "Drama");
        KNOWN.put("anti-Dram", "Drama"); KNOWN.put("Dram Docu", "Drama");

        // --- COMEDY ---
        KNOWN.put("Comd", "Comedy"); KNOWN.put("comd", "Comedy"); KNOWN.put("Sati", "Comedy");
        KNOWN.put("Comdx", "Comedy"); KNOWN.put("Cond", "Comedy"); KNOWN.put("Camp", "Comedy");
        KNOWN.put("Cult", "Comedy"); KNOWN.put("Comd Noir", "Comedy"); KNOWN.put("Comd West", "Comedy");

        // --- ACTION & ADVENTURE ---
        KNOWN.put("Actn", "Action"); KNOWN.put("actn", "Action"); KNOWN.put("Act", "Action");
        KNOWN.put("Axtn", "Action"); KNOWN.put("Sctn", "Action"); KNOWN.put("Viol", "Action");
        KNOWN.put("Dram.Actn", "Action"); KNOWN.put("Romt Actn", "Action");
        KNOWN.put("Advt", "Adventure"); KNOWN.put("Road", "Adventure"); KNOWN.put("RomtAdvt", "Adventure");

        // --- THRILLER & DISASTER ---
        KNOWN.put("Susp", "Thriller"); KNOWN.put("susp", "Thriller"); KNOWN.put("Psyc", "Thriller");
        KNOWN.put("Psych Dram", "Thriller"); KNOWN.put("Disa", "Thriller"); KNOWN.put("Dist", "Thriller");

        // --- ROMANCE ---
        KNOWN.put("Romt", "Romance"); KNOWN.put("romt", "Romance"); KNOWN.put("Romtx", "Romance");
        KNOWN.put("Ront", "Romance"); KNOWN.put("Romt Comd", "Romance"); KNOWN.put("Romt. Comd", "Romance");
        KNOWN.put("Romt Dram", "Romance");

        // --- FANTASY & SURREAL ---
        KNOWN.put("Fant", "Fantasy"); KNOWN.put("fant", "Fantasy"); KNOWN.put("Surr", "Fantasy");
        KNOWN.put("Surl", "Fantasy"); KNOWN.put("surreal", "Fantasy"); KNOWN.put("Weird", "Fantasy");
        KNOWN.put("Romt Fant", "Fantasy"); KNOWN.put("FantH*", "Fantasy");

        // --- HORROR ---
        KNOWN.put("Horr", "Horror"); KNOWN.put("Hor", "Horror"); KNOWN.put("horr", "Horror");
        KNOWN.put("H", "Horror"); KNOWN.put("H*", "Horror"); KNOWN.put("H**", "Horror");
        KNOWN.put("H0", "Horror"); KNOWN.put("RFP; H*", "Horror");

        // --- DOCUMENTARY ---
        KNOWN.put("Docu", "Documentary"); KNOWN.put("Natu", "Documentary"); KNOWN.put("Ducu", "Documentary");
        KNOWN.put("Dicu", "Documentary"); KNOWN.put("Duco", "Documentary"); KNOWN.put("verite", "Documentary");
        KNOWN.put("CA", "Documentary"); KNOWN.put("TVmini", "Documentary"); KNOWN.put("Docu Dram", "Documentary");

        // --- BIOGRAPHY ---
        KNOWN.put("BioP", "Biography"); KNOWN.put("Biop", "Biography"); KNOWN.put("Bio", "Biography");
        KNOWN.put("BioG", "Biography"); KNOWN.put("BioB", "Biography"); KNOWN.put("BioPP", "Biography");
        KNOWN.put("BioPx", "Biography"); KNOWN.put("BiopP", "Biography");

        // --- SCI-FI & ANIMATION ---
        KNOWN.put("ScFi", "Sci-Fi"); KNOWN.put("SciF", "Sci-Fi"); KNOWN.put("S.F.", "Sci-Fi");
        KNOWN.put("Scfi", "Sci-Fi"); KNOWN.put("SxFi", "Sci-Fi");
        KNOWN.put("Cart", "Animation");

        // --- CRIME & MYSTERY & NOIR ---
        KNOWN.put("CnRb", "Crime"); KNOWN.put("CnR", "Crime"); KNOWN.put("Crim", "Crime");
        KNOWN.put("CmR", "Crime"); KNOWN.put("CnRbb", "Crime");
        KNOWN.put("Myst", "Mystery"); KNOWN.put("myst", "Mystery"); KNOWN.put("Mystp", "Mystery");
        KNOWN.put("Noir", "Film-Noir"); KNOWN.put("noir", "Film-Noir"); KNOWN.put("Noir Comd", "Film-Noir");
        KNOWN.put("Noir Comd Romt", "Film-Noir");

        // --- ADULT ---
        KNOWN.put("Porn", "Adult"); KNOWN.put("porn", "Adult"); KNOWN.put("Porb", "Adult");
        KNOWN.put("Adct", "Adult"); KNOWN.put("Adctx", "Adult"); KNOWN.put("Homo", "Adult");
        KNOWN.put("Kinky", "Adult");

        // --- OTHERS ---
        KNOWN.put("West", "Western"); KNOWN.put("West1", "Western");
        KNOWN.put("Hist", "History"); KNOWN.put("Epic", "History");
        KNOWN.put("Musc", "Musical"); KNOWN.put("Muscl", "Musical"); KNOWN.put("musc", "Musical");
        KNOWN.put("Muusc", "Musical"); KNOWN.put("stage musical", "Musical");
        KNOWN.put("Faml", "Family"); KNOWN.put("Scat", "Short"); KNOWN.put("TV", "Short");
    }

    // ------- SAX parser ------------------------------------------
    private final Map<String, Integer> codeCounts = new TreeMap<>();
    private StringBuilder currentValue;
    private boolean insideCats;

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) {
        if ("cat".equalsIgnoreCase(qName)) {
            currentValue = new StringBuilder();
        }

        if ("cats".equalsIgnoreCase(qName)) {
            insideCats = true;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (currentValue != null) currentValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if ("cats".equalsIgnoreCase(qName)) {
            insideCats = false;
        } else if ("cat".equalsIgnoreCase(qName) && insideCats && currentValue != null) {
            String raw = currentValue.toString().trim();
            if (!raw.isEmpty()) {
                codeCounts.merge(raw, 1, Integer::sum);
            }
        }
        currentValue = null;
    }

    // ------- Main ------------------------------------------
    public static void main(String[] args) throws Exception{
        String xmlPath = (args.length > 0) ? args[0] : "XmlDataFiles/mains243.xml";

        System.out.println("Scanning: " + xmlPath);
        System.out.println();

        GenreCodeScanner scanner = new GenreCodeScanner();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(new File(xmlPath), scanner);

        Map<String, Integer> counts = scanner.codeCounts;
        List<String> unmapped = new ArrayList<>();

        // ------- Section 1: Full table sorted by frequency ---------------
        System.out.println("=".repeat(62));
        System.out.printf("%-22s  %6s  %s%n", "RAW CODE", "COUNT", "MAPS TO");
        System.out.println("=".repeat(62));

        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    String code    = e.getKey();
                    int    count   = e.getValue();
                    String mapping = KNOWN.getOrDefault(code, "??? UNMAPPED");
                    System.out.printf("%-22s  %6d  %s%n",
                            "'" + code + "'", count, mapping);
                    if (!KNOWN.containsKey(code)) unmapped.add(code);
                });

        System.out.println("=".repeat(62));
        System.out.printf("Distinct codes : %d%n", counts.size());
        System.out.printf("Total entries  : %d%n",
                counts.values().stream().mapToInt(Integer::intValue).sum());
        System.out.printf("Unmapped codes : %d%n", unmapped.size());
        System.out.println();

        // ------- Section 2: SQL for unmapped codes (fill in the ???) ---------------
        if (!unmapped.isEmpty()) {
            System.out.println("-- -------------------------------------------------------");
            System.out.println("-- UNMAPPED CODES: fill in the genre_name values, then");
            System.out.println("-- paste into stored_procedures.sql and run against the DB");
            System.out.println("-- -------------------------------------------------------");
            System.out.println("INSERT IGNORE INTO genre_code_map (code, genre_name) VALUES");
            for (int i = 0; i < unmapped.size(); i++) {
                String code  = unmapped.get(i);
                int    count = counts.get(code);
                String comma = (i < unmapped.size() - 1) ? "," : ";";
                System.out.printf("    ('%s', '???')%s   -- %d occurrence(s)%n",
                        code, comma, count);
            }
            System.out.println();
        }

        // ------- Section 3: SQL for all known mappings ---------------
        System.out.println("-- -------------------------------------------------------");
        System.out.println("-- KNOWN MAPPINGS: paste into stored_procedures.sql");
        System.out.println("-- -------------------------------------------------------");
        System.out.println("INSERT IGNORE INTO genre_code_map (code, genre_name) VALUES");

        // Only include codes that actually appear in this file
        List<String> knownInFile = new ArrayList<>();
        for (String code : KNOWN.keySet()) {
            if (counts.containsKey(code)) knownInFile.add(code);
        }
        for (int i = 0; i < knownInFile.size(); i++) {
            String code  = knownInFile.get(i);
            String name  = KNOWN.get(code);
            String comma = (i < knownInFile.size() - 1) ? "," : ";";
            System.out.printf("    ('%s', '%s')%s%n", code, name, comma);
        }
    }
}
