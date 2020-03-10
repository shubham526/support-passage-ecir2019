package experiments;

import help.Utilities;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Experiment4 {
    private Map<String, Map<String, Map<String, double[]>>> supportPsgFetFileMap = new HashMap<>();
    private Map<String, Map<String, double[]>> psgFetFileMap = new HashMap<>();
    private HashMap<String, ArrayList<String>> psgQrelMap;
    private ArrayList<String> fetFileStrings = new ArrayList<>();

    public Experiment4(String fetFile, String psgFetFile, String psgQrel) {
        System.out.print("Reading support passage feature file...");
        readFetFile(fetFile);
        System.out.println("[Done].");

        System.out.print("Reading passage ground truth file...");
        psgQrelMap = Utilities.getRankings(psgQrel);
        System.out.println("[Done].");

        System.out.print("Averaging feature vectors across entities....");
        avgVectors();
        System.out.println("[Done].");

        System.out.print("Creating passage feature file...");
        createPsgFetFile(psgQrel, psgFetFile);
        System.out.println("[Done].");

        System.out.println("Passage feature file written to: " + psgFetFile);

    }

    private void createPsgFetFile(String psgQrel, String psgFetFile) {
        int i = 0, rel;
        for (String queryID : psgFetFileMap.keySet()) {
            i++;
            if (psgQrelMap.containsKey(queryID)) {
                ArrayList<String> relParaList = psgQrelMap.get(queryID);
                Map<String, double[]> paraMap = psgFetFileMap.get(queryID);
                for (String paraID : paraMap.keySet()) {
                    if (relParaList.contains(paraID)) {
                        rel = 1;
                    } else {
                        rel = 0;
                    }
                    double[] vector = paraMap.get(paraID);
                    String info = queryID + "_" + paraID;
                    String fetFileString = getFetFileString(rel, i, vector, info);
                    fetFileStrings.add(fetFileString);
                }
            } else {
                System.out.println("No ground truth data for query: " + queryID);
            }
        }
        Utilities.writeFile(fetFileStrings, psgFetFile);
    }

    @NotNull
    private String getFetFileString(int rel, int qid, @NotNull double[] vector, String info) {
        StringBuilder fetLine = new StringBuilder();
        fetLine.append(rel).append(" ").append("qid:").append(qid).append(" ");
        int i = 1;
        for (double d : vector) {
            fetLine.append(i++).append(":").append(d).append(" ");
        }
        fetLine.append("#").append(info);
        return fetLine.toString().trim();
    }

    private void avgVectors() {
        Map<String, double[]> psgMap;
        for (String queryID : supportPsgFetFileMap.keySet()) {
            Map<String, Map<String, double[]>> paraMap = supportPsgFetFileMap.get(queryID);
            for (String paraID : paraMap.keySet()) {
                Map<String, double[]> entityMap = paraMap.get(paraID);
                double[][] vectorMatrix = getVectorMatrix(entityMap);
                double[] avgVector = average(vectorMatrix);
                if (psgFetFileMap.containsKey(queryID)) {
                    psgMap = psgFetFileMap.get(queryID);
                } else {
                    psgMap = new HashMap<>();
                }
                psgMap.put(paraID, avgVector);
                psgFetFileMap.put(queryID, psgMap);
            }
        }
    }

    @NotNull
    @Contract(pure = true)
    private double[] average(@NotNull double[][] vectorMatrix) {
        double[] avgVector = new double[16];
        double sum, avg;
        int rows = vectorMatrix.length, cols = vectorMatrix[0].length;
        for (int i = 0; i < cols; i++) {
            sum = 0.0d;
            for (double[] vector : vectorMatrix) {
                sum += vector[i];
            }
            avg = sum / rows;
            avgVector[i] = avg;
        }
        return avgVector;
    }

    @NotNull
    private double[][] getVectorMatrix(@NotNull Map<String,double[]> entityMap) {
        double[][] vectorMatrix = new double[entityMap.size()][16];
        int i = 0;
        for (String entityID : entityMap.keySet()) {
            double[] vector = entityMap.get(entityID);
            vectorMatrix[i++] = vector;
        }
        return vectorMatrix;
    }

    private void readFetFile(String fetFile) {
        BufferedReader in = null;
        String line;
        Map<String, Map<String, double[]>> paraMap;
        Map<String, double[]> entityMap;
        int i;
        String feature, info;

        try {
            in = new BufferedReader(new FileReader(fetFile));
            while ((line = in.readLine()) != null) {
                line = line.trim();
                i = line.indexOf("#");
                feature = line.substring(0,i);
                info = line.substring(i+1);
                String query = info.split("_")[0];
                String paraID = info.split("_")[1];
                String queryID = query.split("\\+")[0];
                String entityID = query.split("\\+")[1];
                double[] fet = getFeatures(feature);

                if (supportPsgFetFileMap.containsKey(queryID)) {
                    paraMap = supportPsgFetFileMap.get(queryID);
                } else {
                    paraMap = new HashMap<>();
                }

                if (paraMap.containsKey(paraID)) {
                    entityMap = paraMap.get(paraID);
                } else {
                    entityMap = new HashMap<>();
                }

                entityMap.put(entityID, fet);
                paraMap.put(paraID,entityMap);
                supportPsgFetFileMap.put(queryID, paraMap);
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

    @NotNull
    private double[] getFeatures(@NotNull String feature) {
        double[] fet = new double[16];
        int i = 0;
        String[]  fields = feature.split(" ");
        for (int j = 2; j < fields.length; j++) {
            try {
                double val = Double.parseDouble(fields[j].split(":")[1]);
                fet[i++] = val;
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("ArrayIndexOutOfBounds occured for string: " + fields[j]);
                System.exit(1);
            }
        }
        return fet;
    }

    public static void main(@NotNull String[] args) {
        String fetFile = args[0];
        String newFetFile = args[1];
        String psgQrel = args[2];
        new Experiment4(fetFile, newFetFile, psgQrel);
    }
}
