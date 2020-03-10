package help;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import lucene.Index;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * This class does a qualitative analysis of passage rankings derived from support passage rankings.
 * @author Shubham Chatterjee
 * @version 9/25/2019
 */

public class QualitativeAnalysis {
    private HashMap<String, ArrayList<String>> firstRunFileMap;
    private HashMap<String, ArrayList<String>> secondRunFileMap;
    private HashMap<String, ArrayList<String>> entityRunFileMap;
    private HashMap<String, ArrayList<String>> entityQrelMap;
    private ArrayList<String> queryList = new ArrayList<>();
    private IndexSearcher searcher;
    public QualitativeAnalysis(String indexDir,
                               String outlinesFile,
                               String entityRunFile,
                               String entityQrelFile,
                               String runFile1,
                               String runFile2) {
        System.out.print("Setting up Index for use....");
        this.searcher = new Index.Setup(indexDir).getSearcher();
        System.out.println("[Done].");

        System.out.print("Reading first run file...");
        firstRunFileMap = Utilities.getRankings(runFile1);
        System.out.println("[Done].");

        System.out.print("Reading second run file...");
        secondRunFileMap = Utilities.getRankings(runFile2);
        System.out.println("[Done].");

        System.out.print("Reading entity run file...");
        entityRunFileMap = Utilities.getRankings(entityRunFile);
        System.out.println("[Done].");

        System.out.print("Reading entity ground truth file...");
        entityQrelMap = Utilities.getRankings(entityQrelFile);
        System.out.println("[Done].");

        System.out.print("Reading outlines file. Getting query list...");
        getQueryList(outlinesFile);
        System.out.println("[Done].");

        analyze();
    }

    private void getQueryList(String outlinesFile) {
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(outlinesFile)));
            for(Data.Page page: DeserializeData.iterableAnnotations(bis))
                queryList.add(page.getPageId());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void analyze() {
        Document document = null;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println();
        for (String queryID : queryList) {
            System.out.println("-------------------------------");
            System.out.println("QueryID: " + queryID);
            System.out.println("-------------------------------");
            if (    firstRunFileMap.containsKey(queryID)  &&
                    secondRunFileMap.containsKey(queryID) &&
                    entityRunFileMap.containsKey(queryID) &&
                    entityQrelMap.containsKey(queryID)) {

                ArrayList<String> firstRunFileParas = firstRunFileMap.get(queryID);
                ArrayList<String> secondRunFileParas = secondRunFileMap.get(queryID);
                Set<String> retEntitySet = new HashSet<>(Utilities.process(entityRunFileMap.get(queryID)));
                Set<String> relEntitySet = new HashSet<>(Utilities.process(entityQrelMap.get(queryID)));
                // Find the retrieved entities that are also relevant
                retEntitySet.retainAll(relEntitySet);

                for (String paraID : firstRunFileParas) {
                    if (secondRunFileParas.contains(paraID)) {
                        System.out.println("ParaID: " + paraID);
                        int posInFirstRun = firstRunFileParas.indexOf(paraID);
                        int posInSecondRun = secondRunFileParas.indexOf(paraID);
                        try {
                            document = Index.Search.searchIndex("id", paraID, searcher);
                        } catch (IOException | ParseException e) {
                            e.printStackTrace();
                        }
                        assert document != null;
                        String entitiesInPara = document.get("entity");
                        String paraText = document.get("text");
                        Set<String> entitiesInPsgSet = new HashSet<>(
                                Arrays.asList(Utilities.clean(entitiesInPara.split(" "))));
                        System.out.println("ParaText: ");
                        System.out.println(paraText);
                        System.out.println("-------------------------------------------------------------------------");
                        System.out.println("Entities in the passage-->");
                        System.out.println(entitiesInPara);
                        // Find how many relevant entities are in the passage
                        entitiesInPsgSet.retainAll(retEntitySet);
                        System.out.println("Position of passage in first run file: " + posInFirstRun);
                        System.out.println("Position of passage in second run file: " + posInSecondRun);
                        System.out.println("Number of retrieved entities which are relevant: " + retEntitySet.size());
                        System.out.println("Number of retrieved entities which are relevant in the passage: "
                                + entitiesInPsgSet.size());
                        System.out.println(entitiesInPsgSet);
                        System.out.println("=========================================================================");
                        try {
                            br.readLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String outlines = args[1];
        String entityRunFile = args[2];
        String entityQrelFile = args[3];
        String runFile1 = args[4];
        String runFile2 = args[5];

        new QualitativeAnalysis(indexDir, outlines, entityRunFile, entityQrelFile, runFile1, runFile2);

    }

}
