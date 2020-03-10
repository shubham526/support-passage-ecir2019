package help;

import lucene.Index;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * This class shows the passages in a passage ranking.
 * @author Shubham Chatterjee
 * @version 9/25/2019
 */

public class ShowPassage {
    private IndexSearcher searcher;
    private HashMap<String, ArrayList<String>> runFileMap;

    /**
     * Constructor.
     * @param indexDir String Path to the index directory.
     * @param psgRunFile String Path to the passage run file.
     */
    public ShowPassage(String indexDir, String psgRunFile) {

        System.out.print("Setting up Index for use....");
        this.searcher = new Index.Setup(indexDir).getSearcher();
        System.out.println("[Done].");

        System.out.print("Getting rankings from run file...");
        runFileMap = Utilities.getRankings(psgRunFile);
        System.out.println("Done.");
        show();

    }

    /**
     * Helper method.
     */
    private void show() {
        Set<String> querySet = runFileMap.keySet();
        Document document = null;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        for (String queryID : querySet) {
            System.out.println("QueryID: " + queryID);
            ArrayList<String> paraList = runFileMap.get(queryID);
            for (String paraID : paraList) {
                try {
                    document = Index.Search.searchIndex("id", paraID, searcher);
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
                assert document != null;
                String paraText = document.get("text");
                System.out.println("ParaID: " + paraID);
                System.out.println("Text: ");
                System.out.println(paraText);
                System.out.println("===============================================================================");
                try {
                    br.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Main method.
     * @param args Command line argument.
     */

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String psgRunFile = args[1];
        new ShowPassage(indexDir, psgRunFile);
    }
}
