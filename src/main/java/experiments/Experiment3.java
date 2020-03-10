package experiments;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import help.Utilities;
import lucene.Index;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * This class creates a per passage feature file for training.
 * @author Shubham Chatterjee
 * @version 10/1/2019
 * I wrote this on the flight to ICTIR!!
 */

public class Experiment3 {
    private HashMap<String, String> featureFileMap = new HashMap<>();
    private HashMap<String, ArrayList<String>> passageRunFileMap;
    private HashMap<String, List<String>> passageEntity = new HashMap<>();
    private HashMap<String, ArrayList<String>> passageQrelMap;
    private HashMap<String, String> pageMap = new HashMap<>();
    private IndexSearcher searcher;

    Experiment3(String indexDir,
                String featureFile,
                String passageRunFile,
                String passageQrels,
                String newFetFile,
                String outlines) {

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir, "id", new EnglishAnalyzer(), new BM25Similarity()).getSearcher();

        System.out.print("Reading feature file...");
        readFeatureFile(featureFile);
        System.out.println("[Done].");

        System.out.print("Reading passage run file...");
        passageRunFileMap = Utilities.getRankings(passageRunFile);
        System.out.println("[Done].");

        System.out.print("Reading passage ground truth file...");
        passageQrelMap = Utilities.getRankings(passageQrels);
        System.out.println("[Done].");

        System.out.print("Getting entities in each passage..");
        getEntities();
        System.out.println("[Done].");

        System.out.print("Reading outlines file....");
        readOutlines(outlines);
        System.out.println("[Done].");

        doTask(newFetFile);

    }

    private void readOutlines(String outlines) {
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(outlines)));
            for(Data.Page page: DeserializeData.iterableAnnotations(bis)) {
                String pageID = page.getPageId();
                String processedPageId = Utilities.process(pageID);
                pageMap.put(processedPageId, pageID);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void doTask(String fetFile) {
        Set<String> querySet = passageRunFileMap.keySet();
        int qid = 1;
        ArrayList<String> fetLines = new ArrayList<>();


        for (String queryID : querySet) {
            HashMap<String, List<String>> paraFeatures = new HashMap<>();
            ArrayList<String> retParaList = passageRunFileMap.get(queryID);
            ArrayList<String> relParaList = passageQrelMap.get(queryID);
            for (String paraID : retParaList) {

                List<String> paraEntityList = passageEntity.get(paraID);
                List<String> featureList = new ArrayList<>();

                for (String entityID : paraEntityList) {
                    //String procssedEntityId = pageMap.get(entityID);
                    String searchKey = queryID + "+" + entityID + "_" + paraID;
                    String featureString = featureFileMap.get(searchKey);
                    if (featureString != null) {
                        featureList.add(featureString);
                    }
                }
                if (!featureList.isEmpty()) {
                    paraFeatures.put(paraID, featureList);
                }
            }
            // average the features
            HashMap<String, String> averageFeatures = average(paraFeatures);
            String prefix = " ", suffix = " ";
            for (String paraID : averageFeatures.keySet()) {
                String fetLine = averageFeatures.get(paraID);
                if (relParaList.contains(paraID)) {
                    prefix = "1" + " " + "qid:" + qid;
                } else {
                    prefix = "0" + " " + "qid:" + qid;
                }
                suffix = "#" + queryID + "_" + paraID;
                fetLine = prefix + " " + fetLine + " " + suffix;
                fetLines.add(fetLine);
            }
            qid ++;
            System.out.println("Done: " + queryID);
        }
        System.out.print("Writing to feature file...");
        Utilities.writeFile(fetLines, fetFile);
        System.out.println("[Done].");

    }

    private HashMap<String, String> average(HashMap<String, List<String>> paraFeatures) {
        Set<String> paraSet = paraFeatures.keySet();
        HashMap<String, String> averageFeatures = new HashMap<>();
        for (String paraID : paraSet) {
            List<String> features = paraFeatures.get(paraID);
            String avgFetString = average(features);
            averageFeatures.put(paraID, avgFetString);
        }
        return averageFeatures;
    }

    @NotNull
    private String average(@NotNull List<String> features) {
        StringBuilder s = new StringBuilder();
        HashMap<Integer, Double> avgFet = new HashMap<>();
        HashMap<Integer, List<Double>> allFetValues = new HashMap<>();
        for (String fet : features) {
            String[] fetValPairs = fet.split(" ");
            for (String fetValPair : fetValPairs) {
                if (fetValPair.contains(":")) {
                    int fetNum = Integer.parseInt(fetValPair.split(":")[0]);
                    double fetVal = Double.parseDouble(fetValPair.split(":")[1]);
                    List<Double> vals;
                    if (allFetValues.containsKey(fetNum)) {
                        vals = allFetValues.get(fetNum);
                    } else {
                        vals = new ArrayList<>();
                    }
                    vals.add(fetVal);
                    allFetValues.put(fetNum, vals);
                }
            }
        }

        for (int fetNum : allFetValues.keySet()) {
            double avgFetVal = averageInList(allFetValues.get(fetNum));
            avgFet.put(fetNum, avgFetVal);
        }

        for (int fetNum : avgFet.keySet()) {
            s.append(fetNum).append(":").append(avgFet.get(fetNum)).append(" ");
        }
        return s.toString().trim();

    }

    @Contract(pure = true)
    private double averageInList(@NotNull List<Double> vals) {
        double avg = 0.0d, sum = 0.0d;
        for (double d : vals) {
            sum += d;
        }
        avg = sum / vals.size();
        return avg;
    }

    private void getEntities() {
        Document document = null;
        Set<String> querySet = passageRunFileMap.keySet();
        for (String queryID : querySet) {
            ArrayList<String> paraList = passageRunFileMap.get(queryID);
            for (String paraID : paraList) {
                if (! passageEntity.containsKey(paraID)) {
                    try {
                        document = Index.Search.searchIndex("id", paraID, searcher);
                    } catch (IOException | ParseException e) {
                        e.printStackTrace();
                    }
                    assert document != null;
                    List<String> paraEntityList = Utilities.getEntities(document);
                    passageEntity.put(paraID, paraEntityList);
                }
            }
        }
    }

    private void readFeatureFile(String featureFile) {
        BufferedReader br = null;
        String line ,info, feature;
        int i;

        try {
            br = new BufferedReader(new FileReader(featureFile));
            while((line = br.readLine()) != null) {
                i = line.indexOf("#");
                feature = line.substring(8,i);
                info = line.substring(i+1);
                String query = info.split("_")[0];
                String paraID = info.split("_")[1];
                String queryID = query.split("\\+")[0];
                String entityID = query.split("\\+")[1];
                String newInfo = queryID + "+" + Utilities.process(entityID) + "_" + paraID;
                featureFileMap.put(newInfo, feature);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(br != null) {
                    br.close();
                } else {
                    System.out.println("Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String featureFile = args[1];
        String passageRunFile = args[2];
        String passageQrelFile = args[3];
        String newFetFile = args[4];
        String outlines = args[5];
        new experiments.Experiment3(indexDir, featureFile, passageRunFile, passageQrelFile, newFetFile, outlines);
    }
}
