package api;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class to query DBPedia KB through DBpedia Spotlight.
 * @author Shubham Chatterjee
 * @version 03/10/2020
 */
public class DBPediaApi {
    private final static String SPOTLIGHT_URL = "https://api.dbpedia-spotlight.org/en/annotate";
    private final static String SPARQL_URL = "http://dbpedia.org/sparql";


    /**
     * Inner class to represent a DBPedia Entity.
     */
    public static class Entity {
        private final String uri, surfaceForm, dbpediaEntity, description;
        private final int support, offset;
        private final ArrayList<String> types;
        private final double similarityScore, percentageOfSecondRank;

        public Entity(String uri,
                      String surfaceForm,
                      String dbpediaEntity,
                      String description,
                      int support,
                      int offset,
                      ArrayList<String> types,
                      double similarityScore,
                      double percentageOfSecondRank) {

            this.uri = uri;
            this.surfaceForm = surfaceForm;
            this.dbpediaEntity = dbpediaEntity;
            this.description = description;
            this.support = support;
            this.offset = offset;
            this.types = types;
            this.similarityScore = similarityScore;
            this.percentageOfSecondRank = percentageOfSecondRank;
        }

        public String getUri() {
            return uri;
        }

        public String getSurfaceForm() {
            return surfaceForm;
        }

        public String getDescription() {
            return description;
        }

        public int getSupport() {
            return support;
        }

        public int getOffset() {
            return offset;
        }

        public ArrayList<String> getTypes() {
            return types;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public double getPercentageOfSecondRank() {
            return percentageOfSecondRank;
        }

        public String getDbpediaEntity() {
            return dbpediaEntity;
        }

        @Override
        public String toString() {
            return "Entity{" +
                    "uri='" + uri + '\'' +
                    ", surfaceForm='" + surfaceForm + '\'' +
                    ", dbpediaEntity='" + dbpediaEntity + '\'' +
                    ", support=" + support +
                    ", offset=" + offset +
                    ", types=" + types +
                    ", similarityScore=" + similarityScore +
                    ", percentageOfSecondRank=" + percentageOfSecondRank +
                    '}';
        }
    }

    @NotNull
    public static ArrayList<Entity> query(String data) {
        ArrayList<Entity> entities = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#.####");

        String uri, surfaceForm, dbpediaEntity, description;
        int support, offset;
        ArrayList<String> types;
        double similarityScore, percentageOfSecondRank;

        try {
            Document doc = getDocument(data);
            if (doc.text() != null) {
                JSONObject json = new JSONObject(doc.text());
                if (json.has("Resources")) {
                    JSONArray jsonArray = json.getJSONArray("Resources");

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);

                        uri = jsonObject.has("@URI")
                                ? jsonObject.getString("@URI")
                                : "";

                        dbpediaEntity = uri.substring(uri.lastIndexOf("/") + 1);
                        description = getDBPediaAbstract(dbpediaEntity);

                        support = jsonObject.has("@support")
                                ? jsonObject.getInt("@support")
                                : 0;
                        String t = jsonObject.has("@types")
                                ? jsonObject.getString("@types")
                                : "";
                        types = new ArrayList<>(Arrays.asList(t.split(",")));

                        surfaceForm = jsonObject.has("@surfaceForm")
                                ? jsonObject.getString("@surfaceForm")
                                : "";

                        offset = jsonObject.has("@offset")
                                ? jsonObject.getInt("@offset")
                                : 0;

                        similarityScore = jsonObject.has("@similarityScore")
                                ? jsonObject.getDouble("@similarityScore")
                                : 0.0d;

                        percentageOfSecondRank = jsonObject.has("@percentageOfSecondRank")
                                ? jsonObject.getDouble("@percentageOfSecondRank")
                                : 0.0d;

                        entities.add(new Entity(uri, surfaceForm, dbpediaEntity, description, support, offset, types,
                                Double.parseDouble(df.format(similarityScore)),
                                Double.parseDouble(df.format(percentageOfSecondRank))));
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return entities;
    }

    private static String getDBPediaAbstract(String dbpediaEntity) {
        String entity = "dbpedia:" + dbpediaEntity;

        ParameterizedSparqlString qs = new ParameterizedSparqlString( "" +
                "prefix dbpedia: <http://dbpedia.org/resource/>\n" +
                "prefix dbpedia-owl: <http://dbpedia.org/ontology/>\n" +

                "select ?abstract where {\n" + entity + " "  +
                "dbpedia-owl:abstract ?abstract\n" +
                "filter(langMatches(lang(?abstract),\"en\"))" +
                "}" );
        QueryExecution exec = QueryExecutionFactory.sparqlService( SPARQL_URL, qs.asQuery() );
        ResultSet results = exec.execSelect();

        return results.next().get( "abstract" ).toString();
    }

    /**
     * Helper method to connect to the URL.
     * @param data String The text to annotate.
     * @return Document Jsoup document
     */

    private static Document getDocument(String data) {
        Document doc = null;
        try {
            doc = Jsoup.connect(SPOTLIGHT_URL)
                    .data("text", data)
                    .header("Accept", "application/json")
                    .ignoreContentType(true)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return doc;
    }

    public static void main(String[] args) {
        String data = "First documented in the 13th century, Berlin was the capital of the Kingdom of Prussia (1701–1918), the German Empire (1871–1918), the Weimar Republic (1919–33) and the Third Reich (1933–45). Berlin in the 1920s was the third largest municipality in the world. After World War II, the city became divided into East Berlin -- the capital of East Germany -- and West Berlin, a West German exclave surrounded by the Berlin Wall from 1961–89. Following German reunification in 1990, the city regained its status as the capital of Germany, hosting 147 foreign embassies.";
        ArrayList<Entity> entities = DBPediaApi.query(data);
        for (Entity entity : entities) {
            System.out.println("Entity: " + entity.getDbpediaEntity());
            System.out.println();
            System.out.println(entity.getDescription());
            System.out.println("======================================================================");
        }
    }
}
