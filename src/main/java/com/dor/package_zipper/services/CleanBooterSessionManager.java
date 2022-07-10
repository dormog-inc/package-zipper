package com.dor.package_zipper.services;

import com.dor.package_zipper.maven.resolver.AbstractEventsCrawlerRepositoryListener;
import com.dor.package_zipper.maven.resolver.Booter;
import com.dor.package_zipper.maven.resolver.ConsoleRepositoryListener;
import com.dor.package_zipper.maven.resolver.EventsCrawlerRepositoryListener;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Data
public class CleanBooterSessionManager {
    private final Environment env;
    private DefaultRepositorySystemSession session;
    private RepositorySystem system;
    private Booter booter;

    @PostConstruct
    public void initialize() {
        this.booter = new Booter();
        this.system = Booter.newRepositorySystem();
        this.session = booter.newRepositorySystemSession(system);
    }
}
