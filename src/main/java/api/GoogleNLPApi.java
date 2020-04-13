package api;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.language.v1.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class to query the Google Natural Language Processing API.
 * Wrapper over the Google client library.
 *
 * @author Shubham Chatterjee
 * @version 03/22/2020
 */

public class GoogleNLPApi {
    private LanguageServiceSettings languageServiceSettings;
    private AnalyzeEntitiesResponse response;

    public GoogleNLPApi(String keyFile) {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.fromStream(new FileInputStream(keyFile));
            languageServiceSettings = LanguageServiceSettings
                    .newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Entity> query(String text) {
        List<Entity> entities = new ArrayList<>();
        try (LanguageServiceClient language = LanguageServiceClient.create(languageServiceSettings)) {
            Document doc = Document.newBuilder()
                    .setContent(text)
                    .setType(Document.Type.PLAIN_TEXT)
                    .build();

            AnalyzeEntitiesRequest request = AnalyzeEntitiesRequest.newBuilder()
                    .setDocument(doc)
                    .setEncodingType(EncodingType.UTF16)
                    .build();

            response = language.analyzeEntities(request);
            getEntities(entities);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return entities;
    }

    private void getEntities(List<Entity> entities) {
        List<com.google.cloud.language.v1.Entity> entityList = response.getEntitiesList();
        for (com.google.cloud.language.v1.Entity entity : entityList) {
            String name = entity.getName();
            double salience = entity.getSalience();
            Map<String, String> metadata = entity.getMetadataMap();
            List<com.google.cloud.language.v1.EntityMention> mentions = entity.getMentionsList();
            List<EntityMention> mentionList = new ArrayList<>();
            for (com.google.cloud.language.v1.EntityMention mention : mentions) {
                mentionList.add(new EntityMention(mention.getText().getBeginOffset(), mention.getText().getContent(),
                        mention.getType().toString()));
            }
            entities.add(new Entity(name, salience, metadata, mentionList));
        }
    }

    /**
     * Inner class to represent an entity returned by Google NLP.
     */

    public static class Entity {
        private final String name;
        private final double salience;
        private final Map<String, String> metadata;
        private final List<EntityMention> mentions;

        public Entity(String name, double salience, Map<String, String> metadata, List<EntityMention> mentions) {
            this.name = name;
            this.salience = salience;
            this.metadata = metadata;
            this.mentions = mentions;
        }

        public String getName() {
            return name;
        }

        public double getSalience() {
            return salience;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public List<EntityMention> getMentions() {
            return mentions;
        }

        @Override
        public String toString() {
            return "Entity{" +
                    "name='" + name + '\'' +
                    ", salience=" + salience +
                    ", metadata=" + metadata +
                    ", mentions=" + mentions +
                    '}';
        }
    }

    /**
     * Inner class to represent an entity mention.
     */

    public static class EntityMention {
        private final int beginOffset;
        private final String content;
        private final String type;

        public EntityMention(int beginOffset, String content, String type) {
            this.beginOffset = beginOffset;
            this.content = content;
            this.type = type;
        }

        public int getBeginOffset() {
            return beginOffset;
        }

        public String getContent() {
            return content;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return "EntityMention{" +
                    "beginOffset='" + beginOffset + '\'' +
                    ", content='" + content + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
}
