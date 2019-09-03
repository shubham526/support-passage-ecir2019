package salience;

import help.EntitySalience;
import help.Utilities;
import lucene.Index;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * This class re-ranks the support passages obtained using method "Entity Context Neighbors" (see paper and appendix)
 * using entity salience scores obtained using SWAT.
 * Method: Score(p | q, e)  = Score(e | q)  * Salience(p | e)
 * where   Score(e | q)     = normalized retrieval score of entity for query (obtained from the entity ranking)
 *         Salience (p | e) = normalized salience score of entity 'e' for passage 'p' (obtained from SWAT).
 * @author Shubham Chatterjee
 * @version 02/25/2019
 */

public class Experiment2 {

    private IndexSearcher searcher;
    private Map<String, Map<String, Map<String, Double>>> supportPsgRunFileMap;
    private HashMap<String,LinkedHashMap<String, Double>> entityRankings;
    private HashMap<String, Map<String, Double>> salientEntityMap;
    private HashMap<String, Map<String, Double>> swatMap;
    private ArrayList<String> runStrings;

    /**
     * Constructor.
     * @param trecCarDir String Path to the support passage directory.
     * @param outputDir String Path to the output directory within the support passage directory.
     * @param dataDir String Path to the data directory within the support passage directory.
     * @param supportPsgRunFile String Name of the passage run file (obtained from ECN) withn the data directory.
     * @param entityRunFile String Name of the entity run file.
     * @param outFile String Name of the output file.
     * @param swatFile String Path to the swat annotation file.
     */

    public Experiment2(String indexDir,
                       String trecCarDir,
                       String outputDir,
                       String dataDir,
                       String supportPsgRunFile,
                       String entityRunFile,
                       String outFile,
                       String swatFile) {

        this.runStrings = new ArrayList<>();
        this.supportPsgRunFileMap = new LinkedHashMap<>();
        this.entityRankings = new LinkedHashMap<>();
        this.supportPsgRunFileMap = new HashMap<>();
        this.swatMap = new HashMap<>();
        this.salientEntityMap = new HashMap<>();

        String supportPsgRunFilePath = trecCarDir + "/" + dataDir + "/" + supportPsgRunFile;
        String entityRunFilePath = trecCarDir + "/" + dataDir + "/" + entityRunFile;
        String outFilePath = trecCarDir + "/" + outputDir + "/" + outFile;

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir).getSearcher();
        System.out.println("[Done].");

        System.out.print("Reading provided passage run file...");
        getRunFileMap(supportPsgRunFilePath, supportPsgRunFileMap);
        System.out.println("[Done].");

        System.out.print("Reading entity rankings...");
        Utilities.getRankings(entityRunFilePath, entityRankings);
        System.out.println("[Done].");

        System.out.print("Reading the SWAT annotations...");
        try {
            this.swatMap = Utilities.readMap(swatFile);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        experiment(outFilePath);

    }

    /**
     * Works in parallel using Java 8 parallelStreams.
     * DEFAULT THREAD POOL SIE = NUMBER OF PROCESSORS
     * USE : System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "N") to set the thread pool size
     */
    private void experiment(String outFilePath) {
        //Get the set of queries
        Set<String> querySet = supportPsgRunFileMap.keySet();
        System.out.println(querySet.size());

        // Do in parallel

        querySet.parallelStream().forEach(this::doTask);



        // Create the run file
        System.out.print("Writing to run file....");
        Utilities.writeFile(runStrings, outFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written at: " + outFilePath);
    }

    /**
     * Do the actual work.
     * @param queryID String query
     */
    private void doTask(String queryID) {

        // Get the list of entities for the query
        Map<String, Map<String, Double>> entityToParaMap = supportPsgRunFileMap.get(queryID);
        Set<String> entitySet = entityToParaMap.keySet();
        System.out.println("Query: " + queryID);
        if (entityRankings.containsKey(queryID)) {

            for (String entityID : entitySet) {
                if (entityRankings.get(queryID).containsKey(entityID)) {
                    Map<String, Double> paraToScoreMap = entityToParaMap.get(entityID);
                    Set<String> paraSet = paraToScoreMap.keySet();
                    // This is a Map of paragraphs and their scores (P(p|e))
                    Map<String, Double> paraMap = new HashMap<>();

                    // This is a Map where Key = Query and Value = (paragraph score)
                    Map<String, Map<String, Double>> scoreMap = new HashMap<>();

                    String processedEntityID = Utilities.process(entityID); // Remove enwiki: from the entityID

                    // Get the scores for the paragraphs from the salience scores, that is, P(p|e)
                    getParaScores(paraSet, paraMap, processedEntityID);

                    if (paraMap.size() != 0) {
                        // Convert this paraMap to a distribution
                        Map<String, Double> normalizedParaMap = normalize(paraMap);
                        // Score the paragraphs
                        Map<String, Double> scores = new HashMap<>();
                        scoreParas(queryID, entityID, normalizedParaMap, scores);
                        scoreMap.put(queryID + "+" + entityID, scores);
                        makeRunStrings(scoreMap);
                    }
                } else {
                    System.out.printf("Entity %s not present in entity rankings\n", entityID);
                }
            }
            System.out.println("Done Query: " + queryID);
            System.out.println("=====================================================================================");
        } else {
            System.out.printf("Query %s not present in entity rankings\n", queryID);
        }
    }

    /**
     * Load the run file in memory.
     * This loads the run file in the form of a Map of a Map of a Map.
     * @param runFile String Run file to load.
     * @param queryMap Map Map to load the run file into.
     */

    private void getRunFileMap(String runFile,
                               Map<String, Map<String, Map<String, Double>>> queryMap) {
        BufferedReader in = null;
        String line;
        Map<String, Map<String, Double>> entityMap;
        Map<String, Double> paraMap;
        try {
            in = new BufferedReader(new FileReader(runFile));
            while((line = in.readLine()) != null) {
                String[] fields = line.split(" ");
                String queryID = fields[0].split("\\+")[0];
                String entityID = fields[0].split("\\+")[1];
                String paraID = fields[2];
                double paraScore = Double.parseDouble(fields[4]);
                if (queryMap.containsKey(queryID)) {
                    entityMap = queryMap.get(queryID);
                } else {
                    entityMap = new HashMap<>();
                }
                if (entityMap.containsKey(entityID)) {
                    paraMap = entityMap.get(entityID);
                } else {
                    paraMap = new HashMap<>();
                }
                paraMap.put(paraID,paraScore);
                entityMap.put(entityID,paraMap);
                queryMap.put(queryID,entityMap);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(in != null) {
                    in.close();
                } else {
                    System.out.println("Input Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the P(p|e).
     * @param paraSet Set Set of paragraphs retrieved for the query-entity pair
     * @param paraMap Map Map of paragraph and their scores.
     * @param processedEntityID String EntityID after removing enwiki:
     */

    private void getParaScores(@NotNull Set<String> paraSet,
                               Map<String, Double> paraMap,
                               String processedEntityID) {
        // For every paragraph (support passage) retrieved for the query
        Map<String, Double> saliencyMap = new HashMap<>();
        String paraText = null;
        Document document = null;
        for (String paraID : paraSet) {

            if (swatMap.containsKey(paraID)) {
                saliencyMap = swatMap.get(paraID);
                //System.out.println("Got SWAT annotation from SWAT file.");
            } else if (salientEntityMap.containsKey(paraID)) {
                saliencyMap = salientEntityMap.get(paraID);
                //System.out.println("Got SWAT annotation from cache.");
            } else {
                try {
                    document = Index.Search.searchIndex("id", paraID, searcher);
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
                assert document != null;
                paraText = document.get("text");
                saliencyMap = EntitySalience.getSalientEntities(paraText);
                //System.out.println("Could not find SWAT annotation for paraID: " + paraID);
                //System.out.println("Queried the SWAT API.");
                salientEntityMap.put(paraID, saliencyMap);
            }
            if (saliencyMap == null) {
                continue;
            }
            paraMap.put(paraID, saliencyMap.getOrDefault(processedEntityID, 0.0d));
        }
    }

    /**
     * Score the passages.
     * Method: Score(p | q, e)  = Score(e | q)  * Salience(p | e)
     * @param queryID String QueryID
     * @param entityID String EntityID
     * @param normalizedParaMap Map
     * @param scores Map
     */

    private void scoreParas(String queryID,
                            String entityID,
                            @NotNull Map<String, Double> normalizedParaMap,
                            Map<String, Double> scores) {
        for (String paraID : normalizedParaMap.keySet()) {
            try {
                double prob_entity_given_query = entityRankings.get(queryID).get(entityID);
                double prob_para_given_entity = normalizedParaMap.get(paraID);
                double score = prob_entity_given_query * prob_para_given_entity;
                scores.put(paraID, score);
            } catch (NullPointerException e) {
                System.out.printf("NullPointerException for query %s and entity %s\n", queryID, entityID);
            }
        }
    }

    @NotNull
    private Map<String, Double> normalize(@NotNull Map<String, Double> rankings) {
        Map<String, Double> normRankings = new HashMap<>();
        double sum = 0.0d;
        for (double score : rankings.values()) {
            sum += score;
        }

        for (String s : rankings.keySet()) {
            double normScore = rankings.get(s) / sum;
            normRankings.put(s,normScore);
        }

        return normRankings;
    }

    /**
     * Make the run file strings.
     * @param scoreMap Map
     */

    private void makeRunStrings(@NotNull Map<String, Map<String, Double>> scoreMap) {
        String runFileString;
        for (String query : scoreMap.keySet()) {
            int rank = 1;
            HashMap<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap.get(query));
            for (String paraId : sortedScoreMap.keySet()) {
                double score = sortedScoreMap.get(paraId);
                runFileString = query + " Q0 " + paraId + " " + rank
                        + " " + score + " " + "Exp-2-salience";
                runStrings.add(runFileString);
                rank++;
            }
        }
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */
    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String trecCarDir = args[1];
        String outputDir = args[2];
        String dataDir = args[3];
        String supportPsgRunFile = args[4];
        String entityRunFile = args[5];
        String outFile = args[6];
        String swatFile = args[7];

        new Experiment2(indexDir, trecCarDir, outputDir, dataDir, supportPsgRunFile, entityRunFile, outFile, swatFile);

    }





}

