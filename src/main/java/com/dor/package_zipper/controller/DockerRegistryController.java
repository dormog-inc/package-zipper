package com.dor.package_zipper.controller;

import com.dor.package_zipper.models.ImageLayer;
import com.dor.package_zipper.models.ImageManifest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;

@Component
@RestController
@RequiredArgsConstructor
public class DockerRegistryController {
    // Declare these at the top
    private final String JSON_MANIFEST_TYPE_BIS = "application/vnd.docker.distribution.manifest.list.v2+json";
    private final String DOCKER_DEFAULT_AUTH_URL = "auth.docker.io/token";
    private final String OUTPUT_PATH = ".";
    private String mode = "debug";  // If this is fixed in Java as well

    private RestTemplate restTemplate = new RestTemplate();
    private final DockerImagePuller dockerImagePuller;

    @PostConstruct
    public void init() {

    }

    @GetMapping("/pull")
    public ResponseEntity<String> pullImage(
            @RequestParam String imagePath,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false, defaultValue = ".") String outputPath) throws JsonProcessingException {

        // Constants
        final String DOCKER_DEFAULT_SERVER_URL = "registry-1.docker.io";
        final String DOCKER_DEFAULT_REPO = "library";
        final String DOCKER_DEFAULT_TAG = "latest";

        // Parse the imagePath similar to Python's sys.argv[1].split('/')
        String[] imgparts = imagePath.split("/");

        String registryUrl = DOCKER_DEFAULT_SERVER_URL;
        String repository = DOCKER_DEFAULT_REPO;
        String img = "";
        String tag = DOCKER_DEFAULT_TAG;

        // Handle the console dimensions
        int consoleRows = 20;
        int consoleColumns = 20;
// Java doesn't have a direct method to get console size like 'stty size', but this will be hardcoded for this example

// Print formatted lines
        System.out.println(String.join("", Collections.nCopies(consoleColumns, "_")));
        System.out.println("\nDocker image :\t\t\t" + img);
        System.out.println("Docker tag :\t\t\t" + tag);
        System.out.println("Repository :\t\t\t" + repository);
        System.out.println("Serveur_URL :\t\t\t" + "https://" + registryUrl);
        System.out.println(String.join("", Collections.nCopies(consoleColumns, "_")));

// Handle image parts like in Python
        if (imgparts.length > 1 && (imgparts[0].contains(".") || imgparts[0].contains(":"))) {
            registryUrl = imgparts[0];
            repository = imgparts[1];

            String[] restParts = String.join("/", Arrays.copyOfRange(imgparts, 2, imgparts.length)).split(":");
            img = restParts[0];
            tag = restParts[1];
        } else {
            if (imgparts.length > 1) {
                String[] restParts = imgparts[1].split(":");
                repository = restParts[0];
                tag = restParts[1];
            } else {
                repository = "library/" + imgparts[0].split(":")[0];
                tag = imgparts[0].split(":")[1];
            }
        }


        // Get registry endpoint
        String registryEndpoint = getEndpointRegistry(registryUrl, repository);

        HttpHeaders headers = new HttpHeaders();

        // Add basic auth if username and password are provided
        if (username != null && password != null) {
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("US-ASCII")));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);
        }

        // Constants
        final String JSON_MANIFEST_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
        final String MANIFEST_TYPES = "application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.oci.image.index.v1+json";

        // Generate the manifest request URL
        String manifestRequest = "https://" + registryUrl + "/v2/" + repository + "/" + img + "/manifests/" + tag;

        // If debugging mode is enabled
        if ("debug".equals(mode)) {
            System.out.println("manifests_request:");
            System.out.println(manifestRequest);
            System.out.println("auth headers:");
            System.out.println(headers);
        }

        ResponseEntity<String> manifestResponse = restTemplate.exchange(
                manifestRequest,
                HttpMethod.GET,
                new HttpEntity<String>(headers),
                String.class
        );

        // If the response status isn't OK, handle the errors
        if (manifestResponse.getStatusCode() != HttpStatus.OK) {
            System.out.println("[-] Cannot fetch manifest for " + imagePath + " [HTTP " + manifestResponse.getStatusCode() + "]");

            // Retry with other manifest types
            HttpHeaders newHeaders = getAuthHead(registryEndpoint, MANIFEST_TYPES, username, password);  // Assuming you have a similar method as the Python one
            String generatedRequest = "https://" + registryUrl + "/v2/" + repository + "/" + img + "/manifests/" + tag;

            if ("debug".equals(mode)) {
                System.out.println("generated_req:");
                System.out.println(generatedRequest);
                System.out.println("auth headers:");
                System.out.println(newHeaders);
            }

            // Attempt to fetch manifest v2
            ResponseEntity<String> newManifestResponse = restTemplate.exchange(
                    generatedRequest,
                    HttpMethod.GET,
                    new HttpEntity<String>(newHeaders),
                    String.class
            );

            if (newManifestResponse.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = new ObjectMapper().readTree(newManifestResponse.getBody());
                JsonNode manifests = rootNode.get("manifests");
                if (manifests != null) {
                    for (JsonNode manifest : manifests) {
                        JsonNode platform = manifest.get("platform");
                        platform.fieldNames().forEachRemaining(fieldName -> {
                            System.out.print(fieldName + ": " + platform.get(fieldName).asText() + ", ");
                        });
                        System.out.println("digest: " + manifest.get("digest").asText());
                    }
                }
            } else if (newManifestResponse.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                System.out.println("Authentication needed !");
                System.exit(1);
            } else {
                System.out.println("Error when getting manifest response status code : " + newManifestResponse.getStatusCode());
                System.exit(1);
            }
        }

        // Parse and get all layers from the manifest
        ObjectMapper objectMapper = new ObjectMapper();
        ImageManifest manifest = objectMapper.readValue(manifestResponse.getBody(), ImageManifest.class);
        List<ImageLayer> layers = manifest.getLayers();
        dockerImagePuller.pullAndConstructImageStructure(layers, registryEndpoint); // Here, use the registryEndpoint or another appropriate URL based on your logic.

        return ResponseEntity.ok("Image pulled successfully");
    }


    public String getEndpointRegistry(String url, String repository) {
        String serverAuthUrl = "";
        ResponseEntity<String> response = restTemplate.getForEntity("https://" + url + "/v2/", String.class);

        if (response.getStatusCodeValue() == 401) {
            HttpHeaders headers = response.getHeaders();
            String wwwAuthenticate = headers.getFirst("WWW-Authenticate");

            Pattern realmPattern = Pattern.compile("realm=\"([^\"]*)\"");
            Matcher matcher = realmPattern.matcher(wwwAuthenticate);
            if (matcher.find()) {
                if ("Sonatype Nexus Repository Manager".equals(matcher.group(1))) {
                    serverAuthUrl = "https://" + url + "/v2/";
                    System.out.println("Nexus OSS repository type");
                } else if (!url.equals(matcher.group(1)) && matcher.group(1).contains("http")) {
                    Pattern servicePattern = Pattern.compile("service=\"([^\"]*)\"");
                    Matcher serviceMatcher = servicePattern.matcher(wwwAuthenticate);
                    if (serviceMatcher.find()) {
                        serverAuthUrl = matcher.group(1) + "?service=" + serviceMatcher.group(1) + "&scope=repository:" + repository + ":pull";
                        System.out.println("Docker Hub repository type");
                    }
                }
            } else {
                serverAuthUrl = "https://" + url + "/v2/";
                System.out.println("failed !");
            }
        }

        return serverAuthUrl;
    }

    public HttpHeaders getAuthHead(String registryEndpoint, String type, String username, String password) {
        HttpHeaders authHead = new HttpHeaders();
        ObjectMapper objectMapper = new ObjectMapper();

        ResponseEntity<String> response;
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            response = restTemplate.getForEntity(registryEndpoint, String.class, username, password);
        } else {
            response = restTemplate.getForEntity(registryEndpoint, String.class);
        }

        if (response.getStatusCodeValue() == 200) {
            try {
                Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {
                });
                String token = (String) responseBody.get("token");
                if (token != null) {
                    authHead.set("Authorization", "Bearer " + token);
                } else {
                    String basicAuth = response.getHeaders().getFirst("Authorization");
                    if (basicAuth != null && basicAuth.startsWith("Basic")) {
                        authHead.set("Authorization", basicAuth.split(" ")[1]);
                    }
                }
                authHead.set("Accept", type);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (response.getStatusCodeValue() == 401) {
            System.out.println("Authentication error!");
            System.exit(1);
        } else {
            System.out.println("Error inside get_auth_head function: " + response.getStatusCodeValue());
        }

        return authHead;
    }


    // This is an approximation of the Python progress bar in Java. It won't behave exactly like the Python version.
    public void progressBar(String ublob, int nbTraits) {
        StringBuilder progressBar = new StringBuilder(ublob.substring(7, 19) + ": Downloading [");
        for (int i = 0; i < nbTraits; i++) {
            progressBar.append(i == nbTraits - 1 ? ">" : "=");
        }
        for (int i = nbTraits; i < 49; i++) {
            progressBar.append(" ");
        }
        progressBar.append("]");
        System.out.print("\r" + progressBar.toString());
    }

}

