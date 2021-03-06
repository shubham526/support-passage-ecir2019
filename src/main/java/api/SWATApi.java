package api;


import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * This class uses the SWAT API to find salient entities in a text given as input.
 * You can either annotate all entities in the text (salient as well as non-salient) or just the salient entities.
 * The class creates a Map of annotated entities and writes it to disk as a serialized file.
 * @author Shubham Chatterjee
 * @version 3/8/2020
 */

public class SWATApi {
    private final static String URL = "https://swat.d4science.org/salience";
    private final static String TOKEN = "xxxxxx"; // INSERT YOUR KEY HERE

    /**
     * Get the salient entities in the text with their scores.
     * @param text String Text to annotate.
     * @return Map where Key = EntityID and Value = Saliency Score if any salient entities are found, null otherwise.
     */

    @Nullable
    public static Map<String, Double>   getSalientEntities(String text) {
        Map<String, Double> salientEntities = new HashMap<>();
        URL u = getURL();
        assert u != null;
        URLConnection connection = setUpConnection(u);
        doTask(connection, text, salientEntities);
        if (salientEntities.size() != 0) {
            return salientEntities;
        } else {
            return null;
        }
    }

    /**
     * Get all the annotations as returned by SWAT.
     * @param text The text to annotate.
     * @return List of all annotations
     */
    @NotNull
    public static ArrayList<String> getAllEntities(String text) {
        ArrayList<String> annotationList = new ArrayList<>();
        URL u = getURL();
        URLConnection connection = setUpConnection(u);
        doTask(connection, text, annotationList);
        return annotationList;
    }

    /**
     * Do the actual work.
     * @param connection URLConnection
     * @param text String
     * @param annotationList List
     */
    private static void doTask(URLConnection connection,
                               String text,
                               ArrayList<String> annotationList) {
        String jsonInputString = "{\"content\": \"" + text + "\"}";
        write(jsonInputString, connection);
        String res = read(connection);

        if (res.isEmpty()) {
            System.err.println("Server returned no result.");
            return;
        }

        try {
            JSONObject response = new JSONObject(res);
            String status = response.getString("status");
            if ("ok".equals(status)) {
                JSONArray jsonArray = response.getJSONArray("annotations");
                for (int i = 0; i < jsonArray.length(); i++) {
                    // Get each annotation
                    Object ob = jsonArray.get(i);
                    // Convert to JSON
                    JSONObject o = (JSONObject)ob;
                    // Add to list as string
                    annotationList.add(o.toString());
                }
            }
        } catch (JSONException e) {
            System.err.println("ERROR: JSONException");
            e.printStackTrace();
        }
    }

    /**
     * Get the URL for the SWAT API.
     * This method adds the parameter "gcube-token" to the URL.
     * @return URL
     */
    @Nullable
    private static URL getURL() {
        URIBuilder ub = null;
        try {
            ub = new URIBuilder(URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        assert ub != null;
        ub.addParameter("gcube-token", TOKEN);
        String url_new = ub.toString();
        try {
            return new URL (url_new);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Setup the connection to the SWAT API.
     * @param url URL to connect to.
     * @return URLConnection
     */
    @NotNull
    private static URLConnection setUpConnection(@NotNull URL url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert con != null;
            con.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        return con;
    }

    /**
     * Write the data in JSON format to the output stream.
     * @param jsonInputString The text in JSON format.
     * @param connection URLConnection
     */
    private static void write(@NotNull String jsonInputString,
                              @NotNull URLConnection connection) {
        try(OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read the response from the server.
     * @param connection URLConnection
     * @return String The response read
     */
    @NotNull
    private static String read(@NotNull URLConnection connection) {

        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            System.err.println("ERROR: IOException");
            System.err.println("Input Stream not available.");
            return "";
        }

        try {
            if (inputStream.available() == 0) {
                System.err.println("InputStream available but no data to read.");
                return "";
            }
        } catch (IOException e) {
            System.err.println("ERROR: IOException");
            System.err.println("InputStream available but no data to read.");
            return "";
        }


        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        } catch (IOException e) {
            System.err.println("ERROR: IOException while reading from InputStream");
            e.printStackTrace();
        }
        return response.toString();
    }

    /**
     * Do the actual work.
     * @param connection URLConnection
     */
    private static void doTask(URLConnection connection,
                               String text,
                               Map<String, Double> salientEntities) {
        String jsonInputString = "{\"content\": \"" + text + "\"}";

        write(jsonInputString, connection);
        String res = read(connection);

        if (res.isEmpty()) {
            System.err.println("Server returned no result.");
            return;
        }

        try {
            JSONObject response = new JSONObject(res);
            String status = response.getString("status");
            if ("ok".equals(status)) {
                JSONArray jsonObjects = response.getJSONArray("annotations");
                getSalientEntities(jsonObjects, salientEntities);
            }
        } catch (JSONException e) {
            System.err.println("ERROR: JSONException");
            e.printStackTrace();

        }
    }

    /**
     * Get the salient entities in the text.
     * @param jsonArray The array of JSON objects. Entities annotated by SWAT.
     * @param salientEntities The list of salient entities based on "salience_class"
     * @throws JSONException
     */
    private static void  getSalientEntities(@NotNull JSONArray jsonArray,
                                            Map<String, Double> salientEntities) throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) {
            Object ob = jsonArray.get(i);
            JSONObject o = (JSONObject)ob;
            if (o.getDouble("salience_class") == 1.0) {
                String title = o.getString("wiki_title");
                double score = o.getDouble("salience_score");
                salientEntities.put(title.toLowerCase(), score);
            }
        }
    }

    /**
     * Get all salient entities corresponding to the JSONObject.
     * @param jsonObject JSONObject
     * @param salientEntities HasHMap
     */
    public static void getSalientEntities(@NotNull JSONObject jsonObject,
                                          Map<String, Double> salientEntities) {
        try {
            String status = jsonObject.getString("status");
            if ("ok".equals(status)) {
                JSONArray jsonObjects = jsonObject.getJSONArray("annotations");
                getSalientEntities(jsonObjects, salientEntities);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method.
     * Just for checking purposes.
     * @param args command line arguments
     */

    public static void main(String[] args) throws IOException, ClassNotFoundException, JSONException {

        Scanner sc = new Scanner(System.in);
        System.out.println("Enter text to annotate: ");
        String text = sc.nextLine();
        System.out.println("Enter file to store: ");
        String file = sc.nextLine();
        ArrayList<String> list = SWATApi.getAllEntities(text);
        System.out.println("Writing....");
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(file)));
        oos.writeObject(list);
        oos.flush();
        oos.close();
        System.out.println("done");

        System.out.println("reading file: " + file);
        ArrayList<String> listInFile;
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(file)));
        listInFile = (ArrayList<String>) ois.readObject();
        for (String s : listInFile) {
            JSONObject jsonObject = new JSONObject(s);
            //System.out.println(jsonObject);
            System.out.println(jsonObject.getString("wiki_title"));

        }
        // System.out.println(list);

    }
}

