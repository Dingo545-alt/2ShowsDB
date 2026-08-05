import com.mongodb.CursorType;
import com.mongodb.ExplainVerbosity;
import com.mongodb.Function;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Collation;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Hand-written FindIterable<Document> double used instead of a Mockito mock.
 *
 * A plain hand-written implementation avoids the bytecode instrumentation
 * failures of Mockito's inline mock maker on newer JDKs by providing a
 * normal class where only the methods used by FullTextSearchServlet
 * perform real work while the rest remain unsupported.
 */
class FakeFindIterable implements FindIterable<Document> {

    private final List<Document> results;

    FakeFindIterable(List<Document> results) {
        this.results = results;
    }

    @Override
    public <A extends Collection<? super Document>> A into(A target) {
        target.addAll(results);
        return target;
    }

    @Override
    public FindIterable<Document> projection(Bson projection) { return this; }

    @Override
    public FindIterable<Document> limit(int limit) { return this; }

    @Override
    public FindIterable<Document> filter(Bson filter) { return this; }

    @Override
    public FindIterable<Document> skip(int skip) { return this; }

    @Override
    public FindIterable<Document> maxTime(long maxTime, TimeUnit timeUnit) { return this; }

    @Override
    public FindIterable<Document> maxAwaitTime(long maxAwaitTime, TimeUnit timeUnit) { return this; }

    @Override
    public FindIterable<Document> sort(Bson sort) { return this; }

    @Override
    public FindIterable<Document> noCursorTimeout(boolean noCursorTimeout) { return this; }

    @Override
    public FindIterable<Document> oplogReplay(boolean oplogReplay) { return this; }

    @Override
    public FindIterable<Document> partial(boolean partial) { return this; }

    @Override
    public FindIterable<Document> cursorType(CursorType cursorType) { return this; }

    @Override
    public FindIterable<Document> batchSize(int batchSize) { return this; }

    @Override
    public FindIterable<Document> collation(Collation collation) { return this; }

    @Override
    public FindIterable<Document> comment(String comment) { return this; }

    @Override
    public FindIterable<Document> comment(BsonValue comment) { return this; }

    @Override
    public FindIterable<Document> hint(Bson hint) { return this; }

    @Override
    public FindIterable<Document> hintString(String hint) { return this; }

    @Override
    public FindIterable<Document> let(Bson variables) { return this; }

    @Override
    public FindIterable<Document> max(Bson max) { return this; }

    @Override
    public FindIterable<Document> min(Bson min) { return this; }

    @Override
    public FindIterable<Document> returnKey(boolean returnKey) { return this; }

    @Override
    public FindIterable<Document> showRecordId(boolean showRecordId) { return this; }

    @Override
    public FindIterable<Document> allowDiskUse(Boolean allowDiskUse) { return this; }

    @Override
    public Document explain() { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public Document explain(ExplainVerbosity verbosity) { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public <E> E explain(Class<E> explainResultClass) { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public <E> E explain(Class<E> explainResultClass, ExplainVerbosity verbosity) { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public MongoCursor<Document> iterator() { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public MongoCursor<Document> cursor() { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public Document first() { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }

    @Override
    public <U> com.mongodb.client.MongoIterable<U> map(Function<Document, U> mapper) { throw new UnsupportedOperationException("not used by FullTextSearchServlet"); }
}