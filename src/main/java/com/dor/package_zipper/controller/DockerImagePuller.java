package com.dor.package_zipper.controller;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import com.dor.package_zipper.models.ImageLayer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

@Service
public class DockerImagePuller {

    private String output_path;
    private String[] imgparts;


    public void pullAndConstructImageStructure(List<ImageLayer> layers, String registryUrl) {
        try {
            // Create tmp folder that will hold the image
            String imgdir = output_path + "/tmp_" + imgparts[0].replace('/', '.').replace(':', '@');
            if (Files.exists(Paths.get(imgdir))) {
                FileUtils.deleteDirectory(new File(imgdir));
            }
            Files.createDirectories(Paths.get(imgdir));
            System.out.println("Creating image structure in: " + imgdir);

            // Fetching data from registry and parsing JSON
            URL url = new URL(registryUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // If you need authentication, set headers here:
            // conn.setRequestProperty("HeaderName", "HeaderValue");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> respData = objectMapper.readValue(response.toString(), Map.class);

            // Get manifest for SHA256 ID image
// Example endpoint for fetching content
            String endpointURL = "https://example.com/path/to/config";

// Fetching content from the endpoint URL
            byte[] confrespContent = fetchContent(endpointURL);

// Hypothetically, if the content fetched is a JSON and contains a config property:
            String configContent = new String(confrespContent, StandardCharsets.UTF_8);
            JsonObject jsonContent = new JsonParser().parse(configContent).getAsJsonObject();
            String config = jsonContent.get("config").getAsString(); // assuming the fetched content has a "config" field

            String configFileName = imgdir + "/" + config.substring(7) + ".json";
            Files.write(Paths.get(configFileName), confrespContent);

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("Config", config.substring(7) + ".json");
            contentItem.put("RepoTags", new ArrayList<>(List.of(imgparts[0])));
            contentItem.put("Layers", new ArrayList<String>());
            content.add(contentItem);

            String emptyJson = "{\"created\":\"1970-01-01T00:00:00Z\",\"container_config\":{\"Hostname\":\"\",\"Domainname\":\"\",\"User\":\"\",\"AttachStdin\":false,\"AttachStdout\":false,\"AttachStderr\":false,\"Tty\":false,\"OpenStdin\":false, \"StdinOnce\":false,\"Env\":null,\"Cmd\":null,\"Image\":\"\",\"Volumes\":null,\"WorkingDir\":\"\",\"Entrypoint\":null,\"OnBuild\":null,\"Labels\":null}}";

            String parentid = "";
            for (ImageLayer layer : layers) {
                // Replace this with the logic to download, extract, etc. from the previous version
                String fakeLayerId = generateFakeLayerId(parentid, layer.getDigest());
                ((List<String>) contentItem.get("Layers")).add(fakeLayerId + "/layer.tar");

                String layerdir = imgdir + "/" + fakeLayerId;

                // Handle logic similar to Python code for adding VERSION file
                String versionFileName = layerdir + "/VERSION";
                Files.write(Paths.get(versionFileName), "1.0".getBytes());

                if (layers.get(layers.size() - 1).getDigest().equals(layer.getDigest())) {
                    Map<String, Object> jsonObj = objectMapper.readValue(confrespContent, Map.class);
                    jsonObj.remove("history");
                    jsonObj.remove("rootfs"); // Handle the "rootfS" case for Microsoft
                    jsonObj.put("id", fakeLayerId);
                    if (parentid != null && !parentid.isEmpty()) {
                        jsonObj.put("parent", parentid);
                    }
                    parentid = (String) jsonObj.get("id");

                    String jsonFileName = layerdir + "/json";
                    Files.write(Paths.get(jsonFileName), objectMapper.writeValueAsBytes(jsonObj));
                } else {
                    String jsonFileName = layerdir + "/json";
                    Files.write(Paths.get(jsonFileName), emptyJson.getBytes());
                }
            }

            String manifestFileName = imgdir + "/manifest.json";
            Files.write(Paths.get(manifestFileName), objectMapper.writeValueAsBytes(content));

            // Handle repositories file creation
            Map<String, Map<String, String>> repositoriesContent = new HashMap<>();
            repositoriesContent.put(String.join("/", Arrays.asList(imgparts).subList(0, imgparts.length - 1)),
                    Collections.singletonMap(imgparts[imgparts.length - 1], parentid));
            String repositoriesFileName = imgdir + "/repositories";
            Files.write(Paths.get(repositoriesFileName), objectMapper.writeValueAsBytes(repositoriesContent));

            // Create image tar and clean tmp folder
            String dockerTar = output_path + "/" + imgparts[0].replace('/', '_').replace(':', '@') + ".tar";
            try (TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(new FileOutputStream(dockerTar))) {
                addDirectoryToTar(tarOutputStream, new File(imgdir), "/");
            }

            Files.deleteIfExists(Paths.get(imgdir));
            System.out.println("Docker image pulled: " + dockerTar);

        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private byte[] fetchContent(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        // If you need authentication, set headers here:
        // conn.setRequestProperty("HeaderName", "HeaderValue");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            InputStream is = conn.getInputStream();
            byte[] byteChunk = new byte[4096];
            int n;

            while ((n = is.read(byteChunk)) > 0) {
                baos.write(byteChunk, 0, n);
            }
            return baos.toByteArray();
        }
    }

    private void addDirectoryToTar(TarArchiveOutputStream tarOutputStream, File folder, String parent) throws IOException {
        for (File file : folder.listFiles()) {
            String entryName = parent + file.getName();
            if (file.isDirectory()) {
                addDirectoryToTar(tarOutputStream, file, entryName + "/");
            } else {
                TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
                tarOutputStream.putArchiveEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        tarOutputStream.write(buffer, 0, bytesRead);
                    }
                }
                tarOutputStream.closeArchiveEntry();
            }
        }
    }

    private String generateFakeLayerId(String parentid, String ublob) throws NoSuchAlgorithmException {
        String toHash = parentid + "\n" + ublob + "\n";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedBytes = digest.digest(toHash.getBytes());
        return bytesToHex(hashedBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
