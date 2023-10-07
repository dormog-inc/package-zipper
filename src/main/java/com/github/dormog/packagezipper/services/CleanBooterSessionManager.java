package com.github.dormog.packagezipper.services;

import com.github.dormog.packagezipper.maven.resolver.Booter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Data
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
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
