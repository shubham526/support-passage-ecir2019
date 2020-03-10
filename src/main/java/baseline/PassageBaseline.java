package baseline;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import help.Utilities;
import lucene.Index;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class creates a baseline for the passage retrieval task.
 * @author Shubham Chatterjee
 * @version 9/6/2019
 */

public class PassageBaseline {
    private IndexSearcher searcher;
    private List<String> tokens = new ArrayList<>();
    private ArrayList<String> runStrings = new ArrayList<>();
    private List<Data.Page> pageList = new ArrayList<>();
    private Analyzer analyzer;

    public PassageBaseline(String indexDir,
                           String outlineFilePath,
                           String outputFilePath,
                           Analyzer analyzer,
                           Similarity similarity) {

        this.analyzer = analyzer;

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir, "text", analyzer, similarity).getSearcher();
        System.out.println("[Done].");

        System.out.print("Getting list of pages from outlines file...");
        getPageListFromPath(outlineFilePath, pageList);
        System.out.println("[Done].");

        System.out.println("Searching index....");
        search();
        System.out.println("[Done].");

        System.out.print("Writing to run file...");
        Utilities.writeFile(runStrings, outputFilePath);
        System.out.print("[Done].");

        System.out.println("Run file written to: " + outputFilePath);



    }

    /**
     * Convert a query along  to a boolean query
     * @param queryStr String  query
     * @return BooleanQuery A boolean query representing the terms in the original query
     * @throws IOException Excption
     */
    private BooleanQuery toQuery(String queryStr) throws IOException {

        TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(queryStr));
        tokenStream.reset();
        tokens.clear();
        while (tokenStream.incrementToken()) {
            final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
            tokens.add(token);
        }
        tokenStream.end();
        tokenStream.close();
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
        for (String token : tokens) {
            booleanQuery.add(new TermQuery(new Term("text", token)), BooleanClause.Occur.SHOULD);
        }
        return booleanQuery.build();
    }

    /**
     * Create a run file
     * Run file string format: $queryId Q0 $paragraphId $rank $score $name
     * @param queryID String ID of the query
     * @param tds TopDocs Top hits for the query
     * @param retDocs ScoreDoc[] Scores of the top hits
     * @throws IOException Exception
     */
    private void createRunFile(String queryID, TopDocs tds, @NotNull ScoreDoc[] retDocs)throws IOException {
        List<String> paraID = new ArrayList<>();
        Document d;
        String runFileString;

        for (int i = 0; i < retDocs.length; i++) {
            d = searcher.doc(retDocs[i].doc);
            String pID = d.getField("id").stringValue();

            runFileString = queryID + " Q0 " + pID + " " + i + " " + tds.scoreDocs[i].score + " " + "Baseline-BM25";
            if(!paraID.contains(pID)) {
                paraID.add(pID);
                runStrings.add(runFileString);
            }
        }
    }

    /**
     * Get the list of pages
     * @param path String Path to the outlines file
     * @param pageList List of pages
     */
    private void getPageListFromPath(String path, List<Data.Page> pageList) {
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(path)));
            for(Data.Page page: DeserializeData.iterableAnnotations(bis))
                pageList.add(page);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Build the query string.
     * @param page Page
     * @param sectionPath SectionPath
     * @return String
     */
    @NotNull
    private String buildSectionQueryStr(@NotNull Data.Page page, @NotNull List<Data.Section> sectionPath) {
        StringBuilder queryStr = new StringBuilder();
        queryStr.append(page.getPageName());
        for (Data.Section section: sectionPath) {
            if(!section.getHeading().contains("/"))
                queryStr.append(" ").append(section.getHeading());
        }
        return queryStr.toString();
    }

    /**
     * Search the page titles as queries
     */
    private void search() {
        try {
            for(Data.Page page : pageList) {
                String qString = buildSectionQueryStr(page, Collections.emptyList());
                BooleanQuery query = toQuery(qString);
                String qID = page.getPageId();
                TopDocs topDocs = Index.Search.searchIndex(query,100);
                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                createRunFile(qID,topDocs, scoreDocs);
                System.out.println("Done page:"+page.getPageName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method.
     * @param args Command line arguments
     */

    public static void main(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;
        String s1 = null, s2 = null;

        String indexDir = args[0];
        String outlineFilePath = args[1];
        String outputDir = args[2];
        String a = args[3];
        String sim = args[4];


        switch (a) {
            case "std" :
                analyzer = new StandardAnalyzer();
                s2 = "std";
                System.out.println("Analyzer: Standard");
                break;
            case "eng":
                analyzer = new EnglishAnalyzer();
                s2 = "eng";
                System.out.println("Analyzer: English");
                break;
            default:
                System.out.println("Wrong choice of analyzer! Exiting.");
                System.exit(1);
        }
        switch (sim) {
            case "BM25" :
            case "bm25":
                System.out.println("Similarity: BM25");
                similarity = new BM25Similarity();
                s1 = "bm25";
                break;
            case "LMJM":
            case "lmjm":
                System.out.println("Similarity: LMJM");
                try {
                    float lambda = Float.parseFloat(args[5]);
                    System.out.println("Lambda = " + lambda);
                    similarity = new LMJelinekMercerSimilarity(lambda);
                    s1 = "lmjm";
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("No lambda value for similarity LM-JM.");
                    System.exit(1);
                }
                break;
            case "LMDS":
            case "lmds":
                System.out.println("Similarity: LMDS");
                similarity = new LMDirichletSimilarity();
                s1 = "lmds";
                break;

            default:
                System.out.println("Wrong choice of similarity! Exiting.");
                System.exit(1);
        }
        String outFile = "psg-baseline" + "-" + s1 + "-" + s2 + ".run";
        String outputFilePath = outputDir + "/" + outFile;
        new PassageBaseline(indexDir, outlineFilePath, outputFilePath, analyzer, similarity);

    }
}
