
import baseline.PassageBaseline;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;
import salience.Experiment1;
import salience.Experiment2;
import salience.Experiment5;

/**
 * Project runner.
 * @author Shubham Chatterjee
 * @version 9/16/2019
 */
public class ProjectMain {
    public static void main(@NotNull String[] args) {

        if (args.length == 0) {
            help();
            System.exit(-1);
        }
        String command = args[0];
        if (command.equalsIgnoreCase("-h") || command.equalsIgnoreCase("--help")) {
            help();
            System.exit(1);
        }
        String indexDir, trecCarDir, outputDir, dataDir, paraRunFile, entityRunFile, outFile, entityQrel, swatFile,
                supportPsgRunFile, outlinesFilePath, outputFilePath, supportPassageRunFilePath,
                passageRunFilePath, candidatePassageRunFilePath, a, sim;

        Analyzer analyzer;
        Similarity similarity;



        switch(command) {
            case "psg-baseline":
                System.out.println("Making Passage baseline.");
                similarity = null;
                analyzer = null;
                String s1 = null, s2 = null;

                indexDir = args[1];
                outlinesFilePath = args[2];
                outputDir = args[3];
                a = args[4];
                sim = args[5];


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
                            float lambda = Float.parseFloat(args[6]);
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
                outFile = "psg-baseline" + "-" + s1 + "-" + s2 + ".run";
                outputFilePath = outputDir + "/" + outFile;
                new PassageBaseline(indexDir, outlinesFilePath, outputFilePath, analyzer, similarity);
                break;

            case "psg-exp-1":
                System.out.println("Passage Experiment-1.");
                supportPassageRunFilePath = args[1];
                passageRunFilePath = args[2];
                new experiments.Experiment1(supportPassageRunFilePath, passageRunFilePath);
                break;

            case "psg-exp-2":
                System.out.println("Passage Experiment-2.");
                supportPassageRunFilePath = args[1];
                candidatePassageRunFilePath = args[2];
                passageRunFilePath = args[3];
                new experiments.Experiment2(supportPassageRunFilePath, candidatePassageRunFilePath, passageRunFilePath);
                break;

            case "sal-exp-1":
                System.out.println("Salience Experiment-1.");
                indexDir = args[1];
                trecCarDir = args[2];
                outputDir = args[3];
                dataDir = args[4];
                paraRunFile = args[5];
                entityRunFile = args[6];
                outFile = args[7];
                entityQrel = args[8];
                new salience.Experiment1(indexDir, trecCarDir, outputDir, dataDir, paraRunFile, entityRunFile,
                        outFile, entityQrel);
                break;

            case "sal-exp-2":
                System.out.println("Salience Experiment-2.");
                indexDir = args[1];
                trecCarDir = args[2];
                outputDir = args[3];
                dataDir = args[4];
                supportPsgRunFile = args[5];
                entityRunFile = args[6];
                outFile = args[7];
                swatFile = args[8];
                new salience.Experiment2(indexDir, trecCarDir, outputDir, dataDir, supportPsgRunFile, entityRunFile,
                        outFile, swatFile);
                break;

            case "sal-exp-3":
                System.out.println("Salience Experiment-3.");
                indexDir = args[1];
                trecCarDir = args[2];
                outputDir = args[3];
                dataDir = args[4];
                supportPsgRunFile = args[5];
                paraRunFile = args[6];
                entityRunFile = args[7];
                outFile = args[8];
                swatFile = args[9];
                new salience.Experiment3(indexDir, trecCarDir, outputDir, dataDir, supportPsgRunFile, paraRunFile,
                        entityRunFile, outFile, swatFile);
                break;

            case "sal-exp-5":
                System.out.println("Salience Experiment 5");
                entityRunFile = args[1];
                paraRunFile = args[2];
                swatFile = args[3];
                entityQrel = args[4];
                indexDir = args[5];
                outputDir = args[6];

                new salience.Experiment5(entityRunFile, paraRunFile, swatFile, entityQrel, indexDir, outputDir);
                break;

            default: help();

        }
    }
    private static void help() {
        System.out.println("================================================================================");
        System.out.println("This code produces the results from the ECIR 2019 Support Passage Long Paper");
        System.out.println("================================================================================");

        System.out.println("The following options are available:");
        System.out.println("baseline : Produces the passage baseline run. Uses BM25.");
        System.out.println("psg-exp-1: Produces run for passage retrieval experiment-1.");
        System.out.println("psg-exp-2: Produces run for passage retrieval experiment-2.");
        System.out.println("sal-exp-1: Produces run for entity salience experiment-1.");
        System.out.println("sal-exp-2: Produces run for entity salience experiment-2.");
        System.out.println("sal-exp-3: Produces run for entity salience experiment-3.");
        System.exit(-1);

    }
}
