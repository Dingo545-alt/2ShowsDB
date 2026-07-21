package Parser;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class InconsistencyLogger {
    // Cap on how many sample entries we keep per category
    private static final int MAX_EXAMPLES_PER_CATEGORY = 200;

    public static final InconsistencyLogger INSTANCE = new InconsistencyLogger();

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<String>> samples = new ConcurrentHashMap<>();

    public void log(String category, String description) {
        // Bump the counter
        counts.computeIfAbsent(category, k -> new AtomicInteger(0))
                .incrementAndGet();

        // Save a sample if we haven't hit the cap yet
        ConcurrentLinkedQueue<String> bucket =
                samples.computeIfAbsent(category, k -> new ConcurrentLinkedQueue<>());
        if (bucket.size() < MAX_EXAMPLES_PER_CATEGORY) {
            bucket.add(description);
        }
    }

    public void writeReport(String outputPath) {
        try (PrintWriter out = new PrintWriter(new FileWriter(outputPath))) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            out.println("================================================================");
            out.println("  XML LOAD INCONSISTENCY REPORT");
            out.println("  Generated: " + timestamp);
            out.println("================================================================");
            out.println();

            if (counts.isEmpty()) {
                out.println("No inconsistencies were recorded. Clean run!");
                return;
            }

            // Summary table at the top
            out.println("SUMMARY");
            out.println("----------------------------------------------------------------");
            int grandTotal = 0;
            for (Map.Entry<String, AtomicInteger> e : counts.entrySet()) {
                int n = e.getValue().get();
                grandTotal += n;
                out.printf("  %-12s : %,d entries%n", e.getKey(), n);
            }
            out.printf("  %-12s : %,d entries%n", "TOTAL", grandTotal);
            out.println();

            // Detailed sections — one per category
            for (Map.Entry<String, AtomicInteger> e : counts.entrySet()) {
                String category = e.getKey();
                int    total    = e.getValue().get();
                ConcurrentLinkedQueue<String> bucket = samples.get(category);
                int sampleCount = (bucket == null) ? 0 : bucket.size();

                out.println("================================================================");
                out.printf ("  CATEGORY: %s   (%,d total, showing first %,d)%n",
                        category.toUpperCase(), total, sampleCount);
                out.println("================================================================");

                if (bucket != null) {
                    for (String s : bucket) {
                        out.println("  " + s);
                    }
                }

                if (total > sampleCount) {
                    out.printf("  ... and %,d more not shown%n", total - sampleCount);
                }
                out.println();
            }

            System.out.printf("[InconsistencyLogger] Report written to: %s (%,d total issues)%n",
                    outputPath, grandTotal);

        } catch (IOException ex) {
            System.err.println("[InconsistencyLogger] Could not write report: " + ex.getMessage());
        }
    }

    // Total count across all categories — useful for summary printing.
    public int totalCount() {
        return counts.values().stream().mapToInt(AtomicInteger::get).sum();
    }


}
