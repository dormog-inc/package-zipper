package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.exceptions.ArtifactNameIncludesToManyColons;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import com.dor.package_zipper.models.ZipStreamerBody;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@Slf4j
@AllArgsConstructor
public class DockerImageZipperController {
      private final AppConfig appConfig;
      private final DockerClient dockerClient;

      @PostMapping("/zip/stream")
      public ResponseEntity<StreamingResponseBody> StreamDockerImageTar(@RequestBody String image) {
            return null;
      }
}
