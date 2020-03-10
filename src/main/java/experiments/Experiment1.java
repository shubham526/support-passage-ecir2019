package experiments;

import help.Utilities;
import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Purpose: This class does the first new experiment for ECIR 2019 full paper.
 * Method : The support passage ranking gives us a ranking of passages for each query-entity pair.
 *          Suppose we have a ranking of support passages for 3 entities. There will be a passage which occurs more
 *          than one ranking. Assign a score to the passage equal to the reciprocal of the rank at which the passage occurs.
 *          The total score of the passage is the sum of these partial scores obtained from each ranking it occurs in.
 * @author Shubham Chatterjee
 * @version 8/19/2019
 */

public class Experiment1 {

    private ArrayList<String> runStrings = new ArrayList<>();
    private LinkedHashMap<String, LinkedHashMap<String, LinkedList<String>>> supportPassageMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Double> passageScoreMap = new LinkedHashMap<>();

    /**
     * Constructor.
     * @param supportPassageRunFilePath String Path to the support passage run file.
     * @param passageRunFilePath String Path to the passage run file
     */

    public Experiment1(String supportPassageRunFilePath, String passageRunFilePath) {

        System.out.print("Reading support passage run file...");
        readSupportPassageRunFile(supportPassageRunFilePath);
        System.out.println("[Done].");

        System.out.print("Re-ranking...");
        rerank(supportPassageMap);
        System.out.println("[Done].");

        // Create the run file
        System.out.print("Writing to run file...");
        Utilities.writeFile(runStrings, passageRunFilePath);
        System.out.println("[Done].");

        System.out.println("Run file written at: " + passageRunFilePath);

    }
    /**
     * Read the support passage run file.
     * This method reads the ranking as a Map of Map of Map.
     * The innermost Map has Key = EntityID, Value = Score(passage|query, entity)
     * The middle Map has Key = paraID, Value = innermost Map
     * The outer map Map has Key = QueryID, Value = middle Map
     * This format helps to marginalize over the entities.
     * @param runFile String The run file to read.
     */

    private void readSupportPassageRunFile(String runFile) {

        BufferedReader in = null;
        String line;
        LinkedHashMap<String, LinkedList<String>> entityToParaMap;
        LinkedList<String> paraList;

        try {
            in = new BufferedReader(new FileReader(runFile));
            while ((line = in.readLine()) != null) {
                String[] fields = line.split(" ");
                String queryID = fields[0].split("\\+")[0];
                String entityID = fields[0].split("\\+")[1];
                String paraID = fields[2];

                if (supportPassageMap.containsKey(queryID)) {
                    entityToParaMap = supportPassageMap.get(queryID);
                } else {
                    entityToParaMap = new LinkedHashMap<>();
                }
                if (entityToParaMap.containsKey(entityID)) {
                    paraList = entityToParaMap.get(entityID);
                } else {
                    paraList = new LinkedList<>();
                }
                paraList.add(paraID);
                entityToParaMap.put(entityID, paraList);
                supportPassageMap.put(queryID, entityToParaMap);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
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
     * Helper method to rerank the passages.
     * @param supportPassageMap Map
     */

    private void rerank(@NotNull LinkedHashMap<String, LinkedHashMap<String, LinkedList<String>>> supportPassageMap) {
        List<String> queryList = new ArrayList<>(supportPassageMap.keySet());
        // Do in parallel
        // queryList.parallelStream().forEach(this::doTask);
        ProgressBar pb = new ProgressBar("Progress", queryList.size());

        for (String queryId : queryList) {
            doTask(queryId);
            pb.step();
        }
        pb.close();

    }

    /**
     * Helper method.
     * @param queryId String
     */

    private void doTask(String queryId) {
        LinkedHashMap<String, LinkedList<String>> entityToPassageMap = supportPassageMap.get(queryId);
        List<String> entityList = new ArrayList<>(entityToPassageMap.keySet());
        double paraScore;

        for (String entityID : entityList) {
            LinkedList<String> paraList = entityToPassageMap.get(entityID);
            for (int i = 0; i < paraList.size(); i++) {
                String paraID = paraList.get(i);
                paraScore = 1.0 / (i + 1);
                if (passageScoreMap.containsKey(paraID)) {
                    paraScore += passageScoreMap.get(paraID);

                }
                passageScoreMap.put(paraID, paraScore);
            }
        }
        makeRunStrings(queryId, passageScoreMap);
        //System.out.println("Done: " + queryId);

    }

    /**
     * Helper method.
     * @param queryId String
     * @param passageScoreMap Map
     */

    private void makeRunStrings(String queryId, LinkedHashMap<String, Double> passageScoreMap) {
        LinkedHashMap<String, Double> sortedPassageScoreMap = Utilities.sortByValueDescending(passageScoreMap);
        int rank = 1;
        String runFileString;
        List<String> paraList = new ArrayList<>(sortedPassageScoreMap.keySet());
        for (String paraID : paraList) {
            String score = String.format("%.2f", sortedPassageScoreMap.get(paraID));
            runFileString = queryId + " Q0 " + paraID + " " + rank++ + " " + score + " " + "re-ranking-exp-1";
            runStrings.add(runFileString);
        }
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */

    public static void main(@NotNull String[] args) {
        String supportPassageRunFilePath = args[0];
        String passageRunFilePath = args[1];
        new Experiment1(supportPassageRunFilePath, passageRunFilePath);
    }
}
