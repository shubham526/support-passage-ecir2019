package experiments;

import api.WATApi;
import help.PseudoDocument;
import help.Utilities;
import lucene.Index;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static help.Utilities.process;


/**
 * ===================================Experiment-6================================
 * Apply ECN with related entities.
 * Instead of using frequency of co-occurring entities, use relation score.
 * Relation score obtained with WAT.
 * ===============================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/22/2020
 */

public class Experiment6 {

    private final IndexSearcher searcher;
    //HashMap where Key = queryID and Value = list of paragraphs relevant for the queryID
    private final HashMap<String, ArrayList<String>> paraRankings;
    //HashMap where Key = queryID and Value = list of entities relevant for the queryID
    private final HashMap<String,ArrayList<String>> entityRankings;
    private final HashMap<String, ArrayList<String>> entityQrels;
    // ArrayList of run strings
    private final ArrayList<String> runStrings;
    private Map<String, Integer> entityIDMap = new ConcurrentHashMap<>();

    public Experiment6(String indexDir,
                       String mainDir,
                       String outputDir,
                       String dataDir,
                       String passageRunFile,
                       String entityRunFile,
                       String idFile,
                       String outFile,
                       String entityQrelFilePath) {


        String entityRunFilePath = mainDir + "/" + dataDir + "/" + entityRunFile;
        String passageRunFilePath = mainDir + "/" + dataDir + "/" + passageRunFile;
        String idFilePath = mainDir + "/" + dataDir + "/" + idFile;
        String outFilePath = mainDir + "/" + outputDir + "/" + outFile;
        this.runStrings = new ArrayList<>();

        System.out.print("Reading entity rankings...");
        entityRankings = Utilities.getRankings(entityRunFilePath);
        System.out.println("[Done].");

        System.out.print("Reading passage rankings...");
        paraRankings = Utilities.getRankings(passageRunFilePath);
        System.out.println("[Done].");

        System.out.print("Reading entity ground truth...");
        entityQrels = Utilities.getRankings(entityQrelFilePath);
        System.out.println("[Done].");

        System.out.print("Reading id file...");
        try {
            entityIDMap = Utilities.readMap(idFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir).getSearcher();
        System.out.println("[Done].");

        feature(outFilePath);

    }

    /**
     * Method to calculate the feature.
     * Works in parallel using Java 8 parallelStreams.
     * DEFAULT THREAD POOL SIZE = NUMBER OF PROCESSORS
     * USE : System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "N") to set the thread pool size
     */

    private  void feature(String outFilePath) {
        //Get the set of queries
        Set<String> querySet = entityRankings.keySet();

        // Do in parallel
        querySet.parallelStream().forEach(this::doTask);

        // Do in serial
        //querySet.forEach(this::doTask);


        // Create the run file
        System.out.print("Writing to run file.....");
        Utilities.writeFile(runStrings, outFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written at: " + outFilePath);
    }

    /**
     * Helper method.
     * For every query, look at all the entities relevant for the query.
     * For every such entity, create a pseudo-document consisting of passages which contain this entity.
     * For every co-occurring entity in the pseudo-document, if the entity is also relevant for the query,
     * then find the frequency of this entity in the pseudo-document and score the passages using this frequency information.
     *
     * @param queryId String
     */

    private void doTask(String queryId) {
        List<String> pseudoDocEntityList;
        List<PseudoDocument> pseudoDocuments = new ArrayList<>();
        Map<String, Double> relMap = new HashMap<>();

        if (entityRankings.containsKey(queryId) && entityQrels.containsKey(queryId)) {
            ArrayList<String> processedEntityList = process(entityRankings.get(queryId));

            // Get the set of entities retrieved for the query
            Set<String> retEntitySet = new HashSet<>(entityRankings.get(queryId));

            // Get the set of entities relevant for the query
            Set<String> relEntitySet = new HashSet<>(entityQrels.get(queryId));

            // Get the number of retrieved entities which are also relevant
            // Finding support passage for non-relevant entities makes no sense!!

            retEntitySet.retainAll(relEntitySet);

            // Get the list of passages retrieved for the query
            ArrayList<String> paraList = paraRankings.get(queryId);


            // For every entity in this list of relevant entities do
            for (String entityId : retEntitySet) {

                // Create a pseudo-document for the entity
                PseudoDocument d = Utilities.createPseudoDocument(entityId, paraList, searcher);

                // Get the list of entities that co-occur with this entity in the pseudo-document
                if (d != null) {

                    // Add it to the list of pseudo-documents for this entity
                    pseudoDocuments.add(d);

                    // Get the list of co-occurring entities
                    pseudoDocEntityList = d.getEntityList();

                    // For every co-occurring entity do
                    for (String e : pseudoDocEntityList) {

                        // If the entity also occurs in the list of entities relevant for the query then
                        // And the relMap does not already contain this entity


                        ////////////////////////////////////////////////////////////////////////////////////////////////
                        // The second condition is important because pseudoDocEntityList contains multiple occurrences
                        // of the same entity (the original method depended on the frequency of the co-occurring entities).
                        // However, for the purposes of this experiment, we are using the relatedness score between two
                        // entities and hence we don't need multiple occurrences of the same entity. Finding relatedness
                        // of same entity multiple times is going to increase run-time.
                        ////////////////////////////////////////////////////////////////////////////////////////////////

                        if (processedEntityList.contains(e) && !relMap.containsKey(e)) {

                            // Find the relation score of this entity with the given entity and store it
                            relMap.put(e, getRelatedness(entityId, Utilities.unprocess(e)));
                        }
                    }
                }
            }
            // Now score the passages in the pseudo-documents
            scorePassage(queryId, pseudoDocuments, relMap);
            System.out.println("Done query: " + queryId);
        }
    }

    /**
     * Helper method.
     * Returns the relatedness between between two entities.
     * @param e1 String First Entity.
     * @param e2 String Second Entity
     * @return Double Relatedness
     */

    private double getRelatedness(@NotNull String e1, String e2) {

        int id1, id2;
        String s1, s2;

        if (e1.equalsIgnoreCase(e2)) {
            return 1.0d;
        }

        if (entityIDMap.containsKey(e1)) {
            id1 = entityIDMap.get(e1);
        } else {
            s1 = e1.substring(e1.indexOf(":") + 1).replaceAll("%20", "_");
            id1 = WATApi.TitleResolver.getId(s1);
            entityIDMap.put(e1, id1);
        }

        if (entityIDMap.containsKey(e2)) {
            id2 = entityIDMap.get(e2);
        } else {
            s2 = e2.substring(e2.indexOf(":") + 1).replaceAll("%20", "_");
            id2 = WATApi.TitleResolver.getId(s2);
            entityIDMap.put(e2, id2);
            //System.out.println("Queried WAT");
        }

        if (id1 < 0 || id2 < 0) {
            return 0.0d;
        }

        List<WATApi.EntityRelatedness.Pair> pair = WATApi.EntityRelatedness.getRelatedness("mw",id1, id2);
        if (!pair.isEmpty()) {
            return pair.get(0).getRelatedness();
        } else {
            return 0.0d;
        }
    }

    /**
     * Method to score the passages in a pseudo-document corresponding to an entity.
     * For every pseudo-document, get the entity corresponding to this document and the
     * list of documents making up this pseudo-document. For every such document, get a score.
     *
     * @param query   Query ID
     * @param pseudoDocuments List of pseudo-documents
     * @param relMap HashMap where Key = Paragraph ID and Value = Score
     */

    private void scorePassage(String query,
                              @NotNull List<PseudoDocument> pseudoDocuments,
                              Map<String, Double> relMap) {


        // For every pseudo-document do
        for (PseudoDocument d : pseudoDocuments) {

            // Get the entity corresponding to the pseudo-document
            String entityId = d.getEntity();
            Map<String, Double> scoreMap = new HashMap<>();

            // Get the list of documents in the pseudo-document corresponding to the entity
            ArrayList<Document> documents = d.getDocumentList();

            // For every document do
            for (Document doc : documents) {

                // Get the paragraph id of the document
                String paraId = doc.getField("id").stringValue();

                // Get the score of the document
                double score = getParaScore(doc, relMap);

                // Store the paragraph id and score in a HashMap
                scoreMap.put(paraId, score);
            }
            // Make the run file strings for query-entity and document
            makeRunStrings(query, entityId, scoreMap);
        }
    }

    /**
     * Method to find the score of a paragraph.
     * This method looks at all the entities in the paragraph and calculates the score from them.
     * For every entity in the paragraph, if the entity has a score from the entity context pseudo-document,
     * then sum over the entity scores and store the score in a HashMap.
     * @param doc  Document
     * @param relMap HashMap where Key = entity id and Value = score
     * @return Integer
     */

    @Contract("null, _ -> fail")
    private double getParaScore(Document doc, Map<String, Double> relMap) {

        double entityScore, paraScore = 0;
        // Get the entities in the paragraph
        // Make an ArrayList from the String array
        assert doc != null;
        ArrayList<String> pEntList = Utilities.getEntities(doc);
        /* For every entity in the paragraph do */
        for (String e : pEntList) {
            // Lookup this entity in the HashMap of scores for the entities
            // Sum over the scores of the entities to get the score for the passage
            // Store the passage score in the HashMap
            if (relMap.containsKey(e)) {
                entityScore = relMap.get(e);
                paraScore += entityScore;
            }

        }
        return paraScore;
    }

    /**
     * Method to make the run file strings.
     *
     * @param queryId  Query ID
     * @param scoreMap HashMap of the scores for each paragraph
     */

    private void makeRunStrings(String queryId, String entityId, Map<String, Double> scoreMap) {
        LinkedHashMap<String, Double> paraScore = Utilities.sortByValueDescending(scoreMap);
        String runFileString;
        int rank = 1;

        for (String paraId : paraScore.keySet()) {
            double score = paraScore.get(paraId);
            if (score > 0) {
                runFileString = queryId + "+" +entityId + " Q0 " + paraId + " " + rank++
                        + " " + score + " " + "ECNRelEnt";
                runStrings.add(runFileString);
            }

        }
    }

    /**
     * Main method.
     * @param args Command Line arguments
     */

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String mainDir = args[1];
        String outputDir = args[2];
        String dataDir = args[3];
        String paraRunFile = args[4];
        String entityRunFile = args[5];
        String idFile = args[6];
        String outFile = args[7];
        String entityQrel = args[8];

        new Experiment6(indexDir, mainDir, outputDir, dataDir, paraRunFile, entityRunFile, idFile,
                outFile, entityQrel);
    }

}
