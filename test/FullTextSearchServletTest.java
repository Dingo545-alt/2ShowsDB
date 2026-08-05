import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoCollection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * tests inject a mock collection via reflection instead of going through
 * the servlet lifecycle (init() would otherwise attempt a real Mongo connection).
 */
@ExtendWith(MockitoExtension.class)
class FullTextSearchServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private ServletContext servletContext;
    @Mock private MongoCollection<Document> movieCollection;

    private StringWriter responseBody;
    private FullTextSearchServlet servlet;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        servlet = new FullTextSearchServlet();
        Field field = FullTextSearchServlet.class.getDeclaredField("movieCollection");
        field.setAccessible(true);
        field.set(servlet, movieCollection);
    }

    @Test
    void blankQueryReturnsEmptyArrayWithoutTouchingMongo() throws Exception {
        when(request.getParameter("q")).thenReturn("   ");

        servlet.doGet(request, response);

        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(0, json.size());
        verifyNoInteractions(movieCollection);
    }

    @Test
    void missingQueryReturnsEmptyArrayWithoutTouchingMongo() throws Exception {
        when(request.getParameter("q")).thenReturn(null);

        servlet.doGet(request, response);

        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(0, json.size());
        verifyNoInteractions(movieCollection);
    }

    @Test
    void returnsMatchingTitlesUpToTenResults() throws Exception {
        when(request.getParameter("q")).thenReturn("good");
        List<Document> docs = List.of(
                new Document("_id", "tt1").append("title", "Good Uncle"),
                new Document("_id", "tt2").append("title", "Ultimate Good")
        );
        when(movieCollection.find(any(Bson.class))).thenReturn(new FakeFindIterable(docs));

        servlet.doGet(request, response);

        verify(response).setStatus(200);
        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(2, json.size());
        assertEquals("tt1", json.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("Good Uncle", json.get(0).getAsJsonObject().get("title").getAsString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void mongoExceptionLeavesStatusUnsetAndReturnsEmptyBody() throws Exception {
        when(request.getParameter("q")).thenReturn("good");
        when(request.getServletContext()).thenReturn(servletContext);
        when(movieCollection.find(any(Bson.class))).thenThrow(new RuntimeException("mongo down"));

        servlet.doGet(request, response);

        // The catch block only logs; it never sets a response status.
        verify(response, never()).setStatus(anyInt());
        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertTrue(json.isEmpty());
    }
}